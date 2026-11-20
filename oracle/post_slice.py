"""Minimal static discovery/metadata slice for REPLACEMENT_POST.

Only zip entry names and the known metadata text files are read. Class entries
are never opened or parsed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import platform
import sys
import tomllib
import zipfile
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping


SCHEMA_REVISION = 1
RUNNER_ID = "oracle.post_slice.v1"
ADAPTER_ID = "oracle/post_slice.py@1"
NORMALIZER_ID = "oracle/schema.py@1"
FABRIC_PATH = "fabric.mod.json"
NEOFORGE_PATH = "META-INF/neoforge.mods.toml"
FORGE_PATH = "META-INF/mods.toml"
METADATA_ENTRIES = (FABRIC_PATH, NEOFORGE_PATH, FORGE_PATH)


class SliceError(ValueError):
    """Raised when source layout or conversion cannot be represented safely."""


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_sha256(value: object) -> str:
    return sha256_bytes(
        json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    )


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_bytes())
    except OSError as exc:
        raise SliceError(f"cannot read {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise SliceError(f"invalid JSON in {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise SliceError(f"{path}: top-level JSON must be an object")
    return value


def local_paths_by_sha256(repo_root: Path) -> dict[str, list[Path]]:
    index: dict[str, list[Path]] = defaultdict(list)
    sources: tuple[tuple[Path, str, bool], ...] = (
        (repo_root / "dataset-262/download-manifest.json", "manifest", False),
        (
            repo_root / "dataset-262/forbric/download-manifest.json",
            "rows",
            True,
        ),
    )
    for path, key, downloaded_only in sources:
        document = _load_json(path)
        rows = document.get(key)
        if not isinstance(rows, list):
            raise SliceError(f"{path}: {key!r} must be an array")
        for row in rows:
            if not isinstance(row, dict):
                raise SliceError(f"{path}: {key!r} entry must be an object")
            if downloaded_only and row.get("bucket") != "DOWNLOADED":
                continue
            sha256 = row.get("sha256")
            local_path = row.get("localPath")
            if not isinstance(sha256, str) or not isinstance(local_path, str):
                continue
            candidate = (repo_root / local_path).resolve()
            try:
                candidate.relative_to(repo_root.resolve())
            except ValueError as exc:
                raise SliceError(f"local path escapes repository: {candidate}") from exc
            if candidate not in index[sha256.lower()]:
                index[sha256.lower()].append(candidate)
    return index


def _fabric_declarations(metadata: Mapping[str, Any]) -> list[tuple[str, str, object, object, object, object]]:
    rows: list[tuple[str, str, object, object, object, object]] = []
    for kind, required in (("depends", True), ("recommends", False), ("breaks", False)):
        section = metadata.get(kind, {})
        if section is None:
            continue
        if not isinstance(section, dict):
            raise SliceError(f"fabric.mod.json: {kind} must be an object")
        for target, constraints in section.items():
            if not isinstance(target, str) or not target:
                raise SliceError(f"fabric.mod.json: invalid {kind} target {target!r}")
            rows.append((kind, target, constraints, required, "NONE", "BOTH"))
    return rows


def _forge_declarations(
    metadata: Mapping[str, Any],
) -> list[tuple[str, str, object, object, object, object]]:
    dependencies = metadata.get("dependencies", {})
    if dependencies is None:
        return []
    if not isinstance(dependencies, dict):
        raise SliceError("mods metadata: dependencies must be a table")

    rows: list[tuple[str, str, object, object, object, object]] = []
    for owner_id, entries in dependencies.items():
        if not isinstance(entries, list):
            continue
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            target = entry.get("modId", owner_id)
            if not isinstance(target, str) or not target:
                raise SliceError("mods metadata: dependency has no modId")
            raw_type = str(entry.get("type", "required")).lower()
            kind = {
                "required": "depends",
                "optional": "recommends",
                "incompatible": "breaks",
            }.get(raw_type, "other")
            required = kind == "depends"
            rows.append(
                (
                    kind,
                    target,
                    entry.get("versionRange"),
                    required,
                    entry.get("ordering"),
                    entry.get("side"),
                )
            )
    return rows


def _base_item(
    *,
    artifact_key: str,
    file_name: str,
    manifest_order: int,
) -> dict[str, Any]:
    return {
        "artifactKey": artifact_key,
        "sourceFileName": file_name,
        "manifestOrder": manifest_order,
        "capabilityState": "UNKNOWN",
        "executionOutcome": "NOT_ATTEMPTED",
        "outputState": "ABSENT",
        "discoveryAnswer": "NO_ANSWER",
        "modId": None,
        "version": None,
        "loaderFamily": None,
        "expectedDependencyKeys": [],
        "actualDependencyKeys": [],
        "dependencies": [],
        "evidenceRefs": [],
        "reasonCode": "NOT_PROCESSED",
        "oracleItemId": None,
        "semanticResult": "UNASSESSED",
        "measurementState": "ORACLE_MISSING",
        "rawOutputSha256": None,
        "normalizedOutputSha256": None,
    }


def scan_artifact(
    artifact_key: str,
    file_name: str,
    manifest_order: int,
    path: Path | None,
) -> dict[str, Any]:
    item = _base_item(
        artifact_key=artifact_key,
        file_name=file_name,
        manifest_order=manifest_order,
    )
    if path is None or not path.is_file():
        item.update(
            {
                "executionOutcome": "BOUNDARY_BLOCKED",
                "reasonCode": "LOCAL_ARTIFACT_MISSING",
                "measurementState": "BOUNDARY_BLOCKED",
            }
        )
        item["normalizedOutputSha256"] = canonical_sha256(item)
        return item

    actual_sha256 = _sha256_file(path)
    if actual_sha256 != artifact_key:
        item.update(
            {
                "executionOutcome": "BOUNDARY_BLOCKED",
                "reasonCode": "LOCAL_ARTIFACT_HASH_MISMATCH",
                "measurementState": "BOUNDARY_BLOCKED",
            }
        )
        item["normalizedOutputSha256"] = canonical_sha256(item)
        return item

    try:
        with zipfile.ZipFile(path, "r") as archive:
            names = set(archive.namelist())
            metadata_name = next((name for name in METADATA_ENTRIES if name in names), None)
            if metadata_name is None:
                raw_evidence = json.dumps(
                    [name for name in METADATA_ENTRIES if name in names],
                    sort_keys=True,
                ).encode("utf-8")
                item.update(
                    {
                        "capabilityState": "UNSUPPORTED",
                        "executionOutcome": "COMPLETED",
                        "discoveryAnswer": "REJECTED",
                        "reasonCode": "NO_SUPPORTED_METADATA",
                        "measurementState": "UNSUPPORTED",
                        "rawOutputSha256": sha256_bytes(raw_evidence),
                    }
                )
                item["normalizedOutputSha256"] = canonical_sha256(item)
                return item

            raw_metadata = archive.read(metadata_name)
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        item.update(
            {
                "executionOutcome": "CRASHED",
                "reasonCode": f"ZIP_READ_ERROR:{type(exc).__name__}",
                "measurementState": "NO_PARSEABLE_OUTPUT",
                "rawOutputSha256": sha256_bytes(str(exc).encode("utf-8", "replace")),
            }
        )
        item["normalizedOutputSha256"] = canonical_sha256(item)
        return item

    raw_output_sha = sha256_bytes(metadata_name.encode() + b"\0" + raw_metadata)
    item.update(
        {
            "capabilityState": "SUPPORTED",
            "executionOutcome": "COMPLETED",
            "discoveryAnswer": "FOUND",
            "evidenceRefs": [f"zip:{metadata_name}"],
            "reasonCode": "METADATA_PARSED",
            "measurementState": "ORACLE_MISSING",
            "rawOutputSha256": raw_output_sha,
        }
    )

    try:
        if metadata_name == FABRIC_PATH:
            metadata = json.loads(raw_metadata)
            if not isinstance(metadata, dict):
                raise SliceError("fabric.mod.json must be an object")
            item["loaderFamily"] = "FABRIC"
            item["modId"] = metadata.get("id")
            item["version"] = metadata.get("version")
            declarations = _fabric_declarations(metadata)
        else:
            metadata = tomllib.loads(raw_metadata.decode("utf-8-sig"))
            mods = metadata.get("mods")
            first_mod = mods[0] if isinstance(mods, list) and mods else {}
            if not isinstance(first_mod, dict):
                raise SliceError("mods metadata: first mod must be an object")
            item["loaderFamily"] = "NEOFORGE" if metadata_name == NEOFORGE_PATH else "FORGE"
            item["modId"] = first_mod.get("modId")
            item["version"] = first_mod.get("version")
            declarations = _forge_declarations(metadata)
    except (UnicodeDecodeError, json.JSONDecodeError, tomllib.TOMLDecodeError, SliceError) as exc:
        item.update(
            {
                "outputState": "MALFORMED",
                "reasonCode": f"METADATA_PARSE_ERROR:{type(exc).__name__}",
                "measurementState": "NO_PARSEABLE_OUTPUT",
            }
        )
        item["normalizedOutputSha256"] = canonical_sha256(item)
        return item

    item["outputState"] = "PARSEABLE"
    if not isinstance(item["modId"], str) or not item["modId"]:
        item.update(
            {
                "outputState": "MALFORMED",
                "reasonCode": "MISSING_MOD_ID",
                "measurementState": "NO_PARSEABLE_OUTPUT",
            }
        )
        item["normalizedOutputSha256"] = canonical_sha256(item)
        return item
    if not isinstance(item["version"], str) or not item["version"]:
        item.update(
            {
                "outputState": "MALFORMED",
                "reasonCode": "MISSING_MOD_VERSION",
                "measurementState": "NO_PARSEABLE_OUTPUT",
            }
        )
        item["normalizedOutputSha256"] = canonical_sha256(item)
        return item

    base = [
        [
            artifact_key,
            metadata_name,
            item["modId"],
            kind,
            target,
            constraints,
            required,
            ordering,
            side,
        ]
        for kind, target, constraints, required, ordering, side in declarations
    ]
    base.sort(key=lambda value: json.dumps(value, sort_keys=True, ensure_ascii=False))
    seen: dict[str, int] = defaultdict(int)
    keys: list[str] = []
    for parts in base:
        signature = json.dumps(parts, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
        seen[signature] += 1
        payload = parts + [seen[signature]]
        keys.append(
            json.dumps(
                payload,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )
    item["actualDependencyKeys"] = keys
    item["normalizedOutputSha256"] = canonical_sha256(item)
    return item


def _path_for_hash(index: Mapping[str, list[Path]], sha256: str) -> Path | None:
    candidates = sorted(index.get(sha256, []), key=lambda path: path.as_posix())
    return candidates[0] if candidates else None


def build_artifact(repo_root: Path) -> tuple[dict[str, Any], dict[str, int]]:
    cohort_path = repo_root / "oracle/cohort.json"
    cohort = _load_json(cohort_path)
    artifacts = cohort.get("artifacts")
    if not isinstance(artifacts, list):
        raise SliceError(f"{cohort_path}: artifacts must be an array")
    cohort_id = cohort.get("cohortId")
    if not isinstance(cohort_id, str) or not cohort_id:
        raise SliceError(f"{cohort_path}: cohortId is missing")

    local_index = local_paths_by_sha256(repo_root)
    items: list[dict[str, Any]] = []
    for order, artifact in enumerate(artifacts, start=1):
        if not isinstance(artifact, dict):
            raise SliceError("cohort artifact must be an object")
        artifact_key = artifact.get("sha256")
        file_name = artifact.get("fileName")
        if not isinstance(artifact_key, str) or not isinstance(file_name, str):
            raise SliceError("cohort artifact identity is incomplete")
        items.append(
            scan_artifact(
                artifact_key=artifact_key,
                file_name=file_name,
                manifest_order=order,
                path=_path_for_hash(local_index, artifact_key),
            )
        )

    script_sha256 = sha256_bytes(Path(__file__).read_bytes())
    implementation = {
        "implementationId": "REPLACEMENT_POST",
        "sourceRevision": script_sha256,
        "buildSha256": None,
        "runnerId": RUNNER_ID,
        "adapterId": ADAPTER_ID,
        "normalizerId": NORMALIZER_ID,
    }
    started = datetime.now(timezone.utc).isoformat()
    command_identity = {
        "runner": RUNNER_ID,
        "sourceRevision": script_sha256,
        "cohortId": cohort_id,
    }
    boundary_blocked = sum(
        item["measurementState"] == "BOUNDARY_BLOCKED" for item in items
    )
    run = {
        "runId": "replacement-post-" + canonical_sha256(command_identity)[:16],
        "cohortId": cohort_id,
        "implementation": implementation,
        "commandSha256": canonical_sha256(command_identity),
        "environmentDigest": canonical_sha256(
            {
                "python": sys.version,
                "platform": platform.platform(),
                "operating_mode": "STATIC_ANALYSIS",
            }
        ),
        "startedAt": started,
        "endedAt": datetime.now(timezone.utc).isoformat(),
        "processExitCode": None,
        "runOutcome": "BOUNDARY_BLOCKED" if boundary_blocked else "COMPLETED",
        "stdoutSha256": None,
        "stderrSha256": None,
        "rawOutputSha256": None,
        "normalizedOutputSha256": None,
    }
    output = {
        "schemaRevision": SCHEMA_REVISION,
        "cohortId": cohort_id,
        "operatingMode": "STATIC_ANALYSIS",
        "implementation": implementation,
        "run": run,
        "items": items,
    }
    summary = {
        "total": len(items),
        "parsed": sum(item["outputState"] == "PARSEABLE" for item in items),
        "malformed": sum(item["outputState"] == "MALFORMED" for item in items),
        "absent": sum(item["outputState"] == "ABSENT" for item in items),
        "boundary_blocked": boundary_blocked,
        "dependency_keys": sum(len(item["actualDependencyKeys"]) for item in items),
    }
    return output, summary


def write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().with_name("replacement-post.json"),
    )
    args = parser.parse_args(argv)

    try:
        artifact, summary = build_artifact(args.repo_root.resolve())
        write_json(args.output.resolve(), artifact)
    except SliceError as exc:
        print(f"POST_STATUS=FAILED\nRAW_FAILURE={exc}")
        return 1

    print("POST_STATUS=FAILED" if summary["boundary_blocked"] else "POST_STATUS=PASSED")
    print(
        f"POST_ITEMS={summary['total']} "
        f"PARSEABLE={summary['parsed']} "
        f"MALFORMED={summary['malformed']} "
        f"ABSENT={summary['absent']} "
        f"BOUNDARY_BLOCKED={summary['boundary_blocked']} "
        f"DEPENDENCY_KEYS={summary['dependency_keys']}"
    )
    print(f"OUTPUT={args.output}")
    return 1 if summary["boundary_blocked"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
