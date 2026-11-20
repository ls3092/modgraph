"""Project existing candidates.jsonl records into the shared oracle schema."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping


SCHEMA_REVISION = 1
READER_ID = "existing-candidates-jsonl"
ADAPTER_ID = "oracle/normalize_candidates.py@1"
NORMALIZER_ID = "oracle/schema.py@1"
UNRECORDED = "NOT_RECORDED"


class NormalizationError(ValueError):
    """Raised when an existing candidate record cannot be normalized safely."""


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_sha256(value: object) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return sha256_bytes(encoded)


def dependency_keys(
    artifact_key: str,
    loader_family: str | None,
    mod_id: str | None,
    candidate: Mapping[str, Any],
) -> list[str]:
    declarations: list[tuple[str, list[Any], Any, Any]] = []
    for kind in ("depends", "recommends", "breaks"):
        value = candidate.get(kind)
        if value is None:
            continue
        if not isinstance(value, list):
            raise NormalizationError(f"{kind} must be a list")
        for constraint in value:
            if not isinstance(constraint, dict):
                raise NormalizationError(f"{kind} entry must be an object")
            target = constraint.get("targetId")
            if not isinstance(target, str) or not target:
                raise NormalizationError(f"{kind} entry has no targetId")
            ranges = constraint.get("versionRanges")
            if ranges is None:
                ranges = []
            if not isinstance(ranges, list):
                raise NormalizationError(f"{kind} versionRanges must be a list")
            declarations.append((kind, ranges, constraint.get("required"), constraint))

    base_parts = [
        [
            artifact_key,
            loader_family or "",
            mod_id or "",
            kind,
            str(constraint["targetId"]),
            ranges,
            constraint.get("required"),
            constraint.get("ordering"),
            constraint.get("side"),
        ]
        for kind, ranges, _required, constraint in declarations
    ]
    base_parts.sort(key=lambda value: json.dumps(value, sort_keys=True, ensure_ascii=False))

    seen: dict[str, int] = defaultdict(int)
    keys: list[str] = []
    for parts in base_parts:
        base = json.dumps(parts, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
        seen[base] += 1
        key_payload = parts + [seen[base]]
        keys.append(
            json.dumps(
                key_payload,
                sort_keys=True,
                ensure_ascii=False,
                separators=(",", ":"),
            )
        )
    return keys


def normalize_record(
    candidate: Mapping[str, Any],
    *,
    line_number: int,
    raw_line: bytes,
) -> dict[str, Any]:
    artifact_key = candidate.get("sha256")
    if not isinstance(artifact_key, str) or not artifact_key:
        raise NormalizationError(f"line {line_number}: missing sha256")
    artifact_key = artifact_key.lower()
    file_name = candidate.get("sourceFile")
    if not isinstance(file_name, str) or not file_name:
        raise NormalizationError(f"line {line_number}: missing sourceFile")

    status = candidate.get("status")
    if status not in ("OK", "FAILED"):
        raise NormalizationError(f"line {line_number}: unsupported status {status!r}")
    mod_id = candidate.get("modId")
    version = candidate.get("version")
    loader = candidate.get("loaderFamily")
    actual_keys = dependency_keys(artifact_key, loader, mod_id, candidate)
    failure_code = candidate.get("failureCode")
    reason = "LEGACY_STATUS_OK" if status == "OK" else f"LEGACY_FAILED_{failure_code or 'UNKNOWN'}"

    item: dict[str, Any] = {
        "artifactKey": artifact_key,
        "sourceFileName": file_name,
        "manifestOrder": line_number,
        "capabilityState": "SUPPORTED" if status == "OK" else "UNKNOWN",
        "executionOutcome": "COMPLETED",
        "outputState": "PARSEABLE",
        "discoveryAnswer": "FOUND" if status == "OK" else "REJECTED",
        "modId": mod_id if isinstance(mod_id, str) else None,
        "version": version if isinstance(version, str) else None,
        "loaderFamily": loader if isinstance(loader, str) else None,
        "expectedDependencyKeys": [],
        "actualDependencyKeys": actual_keys,
        # candidates.jsonl contains declarations, not resolved dependency outcomes.
        "dependencies": [],
        "evidenceRefs": [f"candidates.jsonl:{line_number}"],
        "reasonCode": reason,
        "oracleItemId": None,
        "semanticResult": "UNASSESSED",
        "measurementState": "ORACLE_MISSING",
        "rawOutputSha256": sha256_bytes(raw_line),
        "normalizedOutputSha256": None,
    }
    item["normalizedOutputSha256"] = canonical_sha256(item)
    return item


def read_candidates(path: Path) -> tuple[list[dict[str, Any]], bytes]:
    try:
        raw = path.read_bytes()
    except OSError as exc:
        raise NormalizationError(f"cannot read {path}: {exc}") from exc
    items: list[dict[str, Any]] = []
    for line_number, line in enumerate(raw.splitlines(), start=1):
        if not line.strip():
            continue
        try:
            candidate = json.loads(line)
        except json.JSONDecodeError as exc:
            raise NormalizationError(f"{path}:{line_number}: invalid JSON: {exc}") from exc
        if not isinstance(candidate, dict):
            raise NormalizationError(f"{path}:{line_number}: record must be an object")
        items.append(
            normalize_record(candidate, line_number=line_number, raw_line=line)
        )
    return items, raw


def build_artifact(candidates_path: Path) -> dict[str, Any]:
    items, raw_candidates = read_candidates(candidates_path)
    candidates_sha256 = sha256_bytes(raw_candidates)
    cohort_payload = [item["artifactKey"] for item in items]
    implementation = {
        "implementationId": "REPLACEMENT_PRE",
        "sourceRevision": candidates_sha256,
        "buildSha256": None,
        "runnerId": READER_ID,
        "adapterId": ADAPTER_ID,
        "normalizerId": NORMALIZER_ID,
    }
    command_sha256 = canonical_sha256(
        {
            "runner": READER_ID,
            "adapter": ADAPTER_ID,
            "normalizer": NORMALIZER_ID,
            "sourceSha256": candidates_sha256,
        }
    )
    environment_sha256 = canonical_sha256(
        {
            "environment": UNRECORDED,
            "source": "existing static candidates.jsonl",
        }
    )
    run = {
        "runId": "legacy-candidates-" + candidates_sha256[:16],
        "cohortId": canonical_sha256(cohort_payload),
        "implementation": implementation,
        "commandSha256": command_sha256,
        "environmentDigest": environment_sha256,
        "startedAt": UNRECORDED,
        "endedAt": UNRECORDED,
        "processExitCode": None,
        "runOutcome": "COMPLETED",
        "stdoutSha256": None,
        "stderrSha256": None,
        "rawOutputSha256": candidates_sha256,
        "normalizedOutputSha256": None,
    }
    return {
        "schemaRevision": SCHEMA_REVISION,
        "cohortId": run["cohortId"],
        "operatingMode": "STATIC_ANALYSIS",
        "implementation": implementation,
        "run": run,
        "items": items,
    }


def write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def summarize(items: Iterable[Mapping[str, Any]]) -> tuple[int, int, int, int]:
    rows = list(items)
    ok = sum(row["discoveryAnswer"] == "FOUND" for row in rows)
    failed = sum(row["discoveryAnswer"] == "REJECTED" for row in rows)
    dependencies = sum(len(row["actualDependencyKeys"]) for row in rows)
    return len(rows), ok, failed, dependencies


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--candidates",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "candidates.jsonl",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().with_name("replacement-pre.json"),
    )
    args = parser.parse_args(argv)

    try:
        artifact = build_artifact(args.candidates.resolve())
        write_json(args.output.resolve(), artifact)
    except NormalizationError as exc:
        print(f"PRE_STATUS=FAILED\nRAW_FAILURE={exc}")
        return 1

    total, ok, failed, dependencies = summarize(artifact["items"])
    assert total == ok + failed
    print("PRE_STATUS=PASSED")
    print(
        f"PRE_ITEMS={total} FOUND={ok} REJECTED={failed} "
        f"DEPENDENCY_KEYS={dependencies}"
    )
    print(f"OUTPUT={args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
