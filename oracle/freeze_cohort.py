"""Freeze the two 26.2 artifact manifests into one SHA-256 cohort.

The script reads manifest JSON only. It never opens or hashes a jar body.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping


SCHEMA_REVISION = 1
OPERATING_MODE = "STATIC_ANALYSIS"
BATCH2_RELATIVE = Path("dataset-262/download-manifest.json")
BATCH4_RELATIVE = Path("dataset-262/forbric/download-manifest.json")
EXPECTED_BATCH2 = 30
EXPECTED_BATCH4 = 36
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SOURCE_BATCH_ORDER = {"segment_2": 0, "segment_4": 1}
CONFLICT_FIELDS = ("fileUrl", "fileName", "spdx", "loader")


class CohortError(ValueError):
    """Raised when a manifest cannot produce a conflict-free cohort."""


@dataclass(frozen=True)
class SourceEntry:
    sha256: str
    file_url: str
    spdx: str
    loader: str
    source_batch: str
    file_name: str

    def field_values(self) -> dict[str, str]:
        return {
            "fileUrl": self.file_url,
            "fileName": self.file_name,
            "spdx": self.spdx,
            "loader": self.loader,
        }


def _required_string(row: Mapping[str, Any], key: str, source: str) -> str:
    value = row.get(key)
    if not isinstance(value, str) or not value:
        raise CohortError(f"{source}: required string {key!r} is missing or empty")
    return value


def _normalize_sha256(value: object, source: str) -> str:
    if not isinstance(value, str):
        raise CohortError(f"{source}: sha256 is not a string: {value!r}")
    normalized = value.lower()
    if SHA256_RE.fullmatch(normalized) is None:
        raise CohortError(f"{source}: sha256 is not 64 lowercase-compatible hex: {value!r}")
    return normalized


def _load_json(path: Path) -> tuple[dict[str, Any], str]:
    try:
        raw = path.read_bytes()
    except OSError as exc:
        raise CohortError(f"cannot read {path}: {exc}") from exc
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise CohortError(f"invalid JSON in {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise CohortError(f"{path}: top-level JSON must be an object")
    return value, hashlib.sha256(raw).hexdigest()


def _rows(document: Mapping[str, Any], key: str, path: Path) -> list[Mapping[str, Any]]:
    value = document.get(key)
    if not isinstance(value, list):
        raise CohortError(f"{path}: {key!r} must be an array")
    if not all(isinstance(row, dict) for row in value):
        raise CohortError(f"{path}: every {key!r} entry must be an object")
    return value


def read_batch2(path: Path) -> list[SourceEntry]:
    document, _ = _load_json(path)
    rows = _rows(document, "manifest", path)
    entries: list[SourceEntry] = []
    for index, row in enumerate(rows):
        source = f"{path}:manifest[{index}]"
        entries.append(
            SourceEntry(
                sha256=_normalize_sha256(row.get("sha256"), source),
                file_url=_required_string(row, "fileUrl", source),
                spdx=_required_string(row, "license", source),
                loader=_required_string(row, "loaderDetected", source).lower(),
                source_batch="segment_2",
                file_name=_required_string(row, "fileName", source),
            )
        )
    return entries


def read_batch4(path: Path) -> list[SourceEntry]:
    document, _ = _load_json(path)
    rows = _rows(document, "rows", path)
    downloaded = [row for row in rows if row.get("bucket") == "DOWNLOADED"]
    entries: list[SourceEntry] = []
    for index, row in enumerate(downloaded):
        source = f"{path}:DOWNLOADED[{index}]"
        entries.append(
            SourceEntry(
                sha256=_normalize_sha256(row.get("sha256"), source),
                file_url=_required_string(row, "fileUrl", source),
                spdx=_required_string(row, "license", source),
                loader=_required_string(row, "loaderDir", source).lower(),
                source_batch="segment_4",
                file_name=_required_string(row, "fileName", source),
            )
        )
    return entries


def _duplicate_details(
    entries: Iterable[SourceEntry],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    groups: dict[str, list[SourceEntry]] = defaultdict(list)
    for entry in entries:
        groups[entry.sha256].append(entry)

    conflicts: list[dict[str, Any]] = []
    within_batch_duplicates: list[dict[str, Any]] = []
    for sha256, group in sorted(groups.items()):
        for source_batch in SOURCE_BATCH_ORDER:
            count = sum(entry.source_batch == source_batch for entry in group)
            if count > 1:
                within_batch_duplicates.append(
                    {"sha256": sha256, "sourceBatch": source_batch, "count": count}
                )

        differing_fields: dict[str, list[str]] = {}
        for field in CONFLICT_FIELDS:
            values = sorted({entry.field_values()[field] for entry in group})
            if len(values) > 1:
                differing_fields[field] = values
        if differing_fields:
            conflicts.append({"sha256": sha256, "differingFields": differing_fields})
    return conflicts, within_batch_duplicates


def _merge_entries(entries: Iterable[SourceEntry]) -> list[dict[str, Any]]:
    groups: dict[str, list[SourceEntry]] = defaultdict(list)
    for entry in entries:
        groups[entry.sha256].append(entry)

    artifacts: list[dict[str, Any]] = []
    for sha256, group in sorted(groups.items()):
        first = group[0]
        artifacts.append(
            {
                "sha256": sha256,
                "fileUrl": first.file_url,
                "spdx": first.spdx,
                "loader": first.loader,
                "sourceBatches": sorted(
                    {entry.source_batch for entry in group},
                    key=SOURCE_BATCH_ORDER.__getitem__,
                ),
                "fileName": first.file_name,
            }
        )
    return artifacts


def _canonical_sha256(value: object) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _relative_posix(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def build_cohort(repo_root: Path) -> dict[str, Any]:
    batch2_path = repo_root / BATCH2_RELATIVE
    batch4_path = repo_root / BATCH4_RELATIVE
    batch2_document, batch2_manifest_sha256 = _load_json(batch2_path)
    batch4_document, batch4_manifest_sha256 = _load_json(batch4_path)

    # Re-read through the dedicated parsers so field validation is identical to
    # the command path, while retaining the byte-level manifest digests above.
    del batch2_document, batch4_document
    batch2 = read_batch2(batch2_path)
    batch4 = read_batch4(batch4_path)

    set2 = {entry.sha256 for entry in batch2}
    set4 = {entry.sha256 for entry in batch4}
    overlap = len(set2 & set4)
    union_entries = _merge_entries((*batch2, *batch4))
    union = len(union_entries)
    conflicts, within_batch_duplicates = _duplicate_details((*batch2, *batch4))

    assertions = {
        "batch2CountIs30": len(batch2) == EXPECTED_BATCH2,
        "batch4DownloadedCountIs36": len(batch4) == EXPECTED_BATCH4,
        "noWithinBatchDuplicateSha256": not within_batch_duplicates,
        "unionFormula": union == EXPECTED_BATCH2 + EXPECTED_BATCH4 - overlap,
        "everySha256Is64Hex": all(
            isinstance(item["sha256"], str)
            and SHA256_RE.fullmatch(item["sha256"]) is not None
            for item in union_entries
        ),
        "noSha256MetadataConflicts": not conflicts,
    }
    if not all(assertions.values()):
        failure = {
            "assertions": assertions,
            "sha256Conflicts": conflicts,
            "withinBatchDuplicates": within_batch_duplicates,
        }
        raise CohortError(
            "cohort assertions failed:\n"
            + json.dumps(failure, ensure_ascii=False, indent=2)
        )

    identity_payload = {
        "schemaRevision": SCHEMA_REVISION,
        "operatingMode": OPERATING_MODE,
        "manifestInputs": [
            {
                "path": BATCH2_RELATIVE.as_posix(),
                "sha256": batch2_manifest_sha256,
            },
            {
                "path": BATCH4_RELATIVE.as_posix(),
                "sha256": batch4_manifest_sha256,
            },
        ],
        "artifacts": union_entries,
    }
    return {
        "schemaRevision": SCHEMA_REVISION,
        "cohortId": _canonical_sha256(identity_payload),
        "operatingMode": OPERATING_MODE,
        "targetGameVersion": "26.2",
        "manifestInputs": identity_payload["manifestInputs"],
        "counts": {
            "segment_2": len(batch2),
            "segment_4": len(batch4),
            "overlap": overlap,
            "union": union,
            "sha256Conflicts": len(conflicts),
            "withinBatchDuplicates": len(within_batch_duplicates),
        },
        "assertions": assertions,
        "artifacts": union_entries,
    }


def write_cohort(payload: Mapping[str, Any], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(output)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="repository root (default: parent of oracle/)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().with_name("cohort.json"),
        help="output cohort JSON (default: oracle/cohort.json)",
    )
    args = parser.parse_args(argv)

    try:
        payload = build_cohort(args.repo_root.resolve())
        write_cohort(payload, args.output.resolve())
    except CohortError as exc:
        print(f"COHORT_STATUS=FAILED\nRAW_FAILURE={exc}", file=sys.stderr)
        return 1

    counts = payload["counts"]
    checks = payload["assertions"]
    print("COHORT_STATUS=PASSED")
    print(
        f"BATCH2={counts['segment_2']} "
        f"BATCH4={counts['segment_4']} "
        f"OVERLAP={counts['overlap']} "
        f"UNION={counts['union']} "
        f"SHA256_CONFLICTS={counts['sha256Conflicts']}"
    )
    print(f"ASSERTIONS_PASSED={sum(checks.values())}/{len(checks)}")
    print(f"OUTPUT={_relative_posix(args.output, args.repo_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
