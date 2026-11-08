#!/usr/bin/env python3
"""Cross-check modgraph's naming-convention Mixin targets against modcrawl.

This script treats modcrawl as an external process. It does not copy or import
any modcrawl code. For every JAR under fixtures/jars it runs:

    <modcrawl> mixins --json <jar>

and stores the raw JSON output. It then joins that output with the existing
candidates.jsonl records (by sourceFile basename) and reports:

  * per-record four-way comparison: both / naming-only / modcrawl-only / neither
  * the 10-record "gap" set (OK, non-empty mixinConfigs, empty mixinTargets)
  * filled vs remaining, and a reason bucket for every remaining record

Arithmetic invariants are asserted; anything that cannot be classified makes
the script exit non-zero. No counts are hard-coded.

Usage:
    python verify_mixin_coverage.py --modcrawl C:\\path\\to\\modcrawl.exe

The modcrawl binary may also be supplied through MODCRAWL_EXE or PATH.
"""

from __future__ import annotations

import argparse
import csv
import datetime
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

FOUR_BUCKETS = ("BOTH", "NAMING_ONLY", "MODCRAWL_ONLY", "NEITHER")
REMAINING_BUCKETS = (
    "EMPTY_CONFIG",
    "CONFIG_MISSING",
    "MALFORMED_CONFIG",
    "NESTED_JAR_ONLY",
    "CONFIG_REFERENCES_MISSING_CLASS",
    "CONFIG_CLASS_PRESENT_MODCRAWL_NO_TARGET",
    "UNCLASSIFIED",
)


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def find_modcrawl(cli_value: str | None) -> Path:
    candidates: list[str] = []
    if cli_value:
        candidates.append(cli_value)
    env_value = os.environ.get("MODCRAWL_EXE")
    if env_value:
        candidates.append(env_value)
    on_path = shutil.which("modcrawl")
    if on_path:
        candidates.append(on_path)

    for value in candidates:
        path = Path(value).expanduser().resolve()
        if path.is_file():
            return path
    fail(
        "modcrawl executable not found. Pass --modcrawl <exe>, set MODCRAWL_EXE, "
        "or put modcrawl on PATH."
    )
    raise AssertionError("unreachable")


def load_candidates(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                fail(f"{path}:{line_no}: invalid JSON: {exc}")
            if not isinstance(record, dict):
                fail(f"{path}:{line_no}: candidate record is not an object")
            records.append(record)
    if not records:
        fail(f"{path}: no candidate records")
    return records


def enumerate_jars(root: Path) -> list[Path]:
    if not root.is_dir():
        fail(f"JAR root does not exist: {root}")
    jars = sorted(
        p for p in root.rglob("*") if p.is_file() and p.suffix.lower() == ".jar"
    )
    if not jars:
        fail(f"No .jar files found under {root}")
    return jars


def run_modcrawl(exe: Path, jar: Path, timeout: int) -> tuple[bytes, bytes, int]:
    command = [str(exe), "mixins", "--json", str(jar)]
    try:
        result = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired:
        fail(f"modcrawl timed out after {timeout}s on {jar}")
    return result.stdout, result.stderr, result.returncode


def parse_mixin_entries(raw: bytes, jar: Path) -> list[dict[str, Any]]:
    if not raw.strip():
        fail(f"modcrawl produced empty stdout for {jar}")
    try:
        value = json.loads(raw.decode("utf-8-sig"))
    except UnicodeDecodeError as exc:
        fail(f"modcrawl stdout is not UTF-8 for {jar}: {exc}")
    except json.JSONDecodeError as exc:
        preview = raw.decode("utf-8", "replace")[:500]
        fail(f"modcrawl stdout is not JSON for {jar}: {exc}; preview={preview!r}")
    if not isinstance(value, list):
        fail(f"modcrawl JSON is not an array for {jar}: {type(value).__name__}")
    entries: list[dict[str, Any]] = []
    for item in value:
        if not isinstance(item, dict):
            fail(f"modcrawl JSON entry is not an object for {jar}: {item!r}")
        if not isinstance(item.get("mixin_class"), str) or not isinstance(
            item.get("target"), str
        ):
            fail(f"modcrawl JSON entry lacks string mixin_class/target for {jar}: {item!r}")
        entries.append(item)
    return entries


def read_configs(jar: Path, config_paths: list[str]) -> tuple[list[dict[str, Any]], list[str]]:
    """Return (parsed configs, missing config paths)."""
    configs: list[dict[str, Any]] = []
    missing: list[str] = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for config_path in config_paths:
            if config_path not in names:
                missing.append(config_path)
                continue
            try:
                text = zf.read(config_path).decode("utf-8-sig")
                value = json.loads(text)
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                fail(f"cannot parse mixin config {config_path} in {jar}: {exc}")
            if not isinstance(value, dict):
                fail(f"mixin config {config_path} in {jar} is not a JSON object")
            configs.append(value)
    return configs, missing


def referenced_classes(config: dict[str, Any]) -> list[str]:
    package = config.get("package")
    if not isinstance(package, str) or not package:
        return []
    refs: list[str] = []
    for key in ("mixins", "client", "server"):
        values = config.get(key)
        if isinstance(values, str):
            values = [values]
        if not isinstance(values, list):
            continue
        for value in values:
            if isinstance(value, str) and value:
                refs.append(f"{package.replace('.', '/')}/{value.replace('.', '/')}.class")
    return refs


def class_in_nested_jars(jar: Path, class_paths: set[str]) -> bool:
    with zipfile.ZipFile(jar) as zf:
        nested_names = [n for n in zf.namelist() if n.lower().endswith(".jar")]
        for nested_name in nested_names:
            try:
                data = zf.read(nested_name)
                with zipfile.ZipFile(io.BytesIO(data)) as nested:
                    if class_paths.intersection(nested.namelist()):
                        return True
            except (zipfile.BadZipFile, OSError):
                continue
    return False


def classify_remaining(jar: Path, record: dict[str, Any]) -> dict[str, Any]:
    config_paths = [p for p in (record.get("mixinConfigs") or []) if isinstance(p, str)]
    detail: dict[str, Any] = {
        "reason": "UNCLASSIFIED",
        "configPaths": config_paths,
        "configsFound": [],
        "configsMissing": [],
        "referencedClassCount": 0,
        "topLevelClassHits": 0,
        "nestedJarClassHits": 0,
    }
    if not config_paths:
        detail["reason"] = "EMPTY_CONFIG"
        return detail
    configs, missing = read_configs(jar, config_paths)
    detail["configsFound"] = config_paths[: len(configs)]
    detail["configsMissing"] = missing
    if not configs:
        detail["reason"] = "CONFIG_MISSING"
        return detail
    if missing:
        detail["reason"] = "CONFIG_MISSING"
        return detail
    refs: list[str] = []
    for config in configs:
        refs.extend(referenced_classes(config))
    detail["referencedClassCount"] = len(refs)
    if not refs:
        detail["reason"] = "EMPTY_CONFIG"
        return detail

    with zipfile.ZipFile(jar) as zf:
        top_classes = set(zf.namelist())
    existing_top = [ref for ref in refs if ref in top_classes]
    detail["topLevelClassHits"] = len(existing_top)
    if existing_top:
        detail["reason"] = "CONFIG_CLASS_PRESENT_MODCRAWL_NO_TARGET"
        return detail

    if class_in_nested_jars(jar, set(refs)):
        detail["nestedJarClassHits"] = 1
        detail["reason"] = "NESTED_JAR_ONLY"
    else:
        detail["reason"] = "CONFIG_REFERENCES_MISSING_CLASS"
    return detail


def unique_mod_ids(rows: list[dict[str, Any]]) -> list[str]:
    return sorted({str(row["modId"]) for row in rows if row.get("modId")})


def format_mod_ids(rows: list[dict[str, Any]]) -> str:
    grouped: dict[str, list[str]] = defaultdict(list)
    for row in rows:
        grouped[str(row["modId"])].append(str(row["loaderFamily"]))
    parts = []
    for mod_id in sorted(grouped):
        parts.append(f"{mod_id} ({', '.join(sorted(set(grouped[mod_id])))})")
    return "; ".join(parts)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modcrawl", help="path to modcrawl executable")
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="modgraph repository root (default: script directory)",
    )
    parser.add_argument(
        "--jars-root",
        type=Path,
        help="JAR root (default: <repo-root>/fixtures/jars)",
    )
    parser.add_argument(
        "--candidates",
        type=Path,
        help="candidates.jsonl (default: <repo-root>/candidates.jsonl)",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        help="output directory (default: <repo-root>/mixin-coverage)",
    )
    parser.add_argument(
        "--modcrawl-repo",
        type=Path,
        help="modcrawl source checkout for LICENSE/version notes (default: <repo-root>/../tools/modcrawl)",
    )
    parser.add_argument("--timeout", type=int, default=180, help="per-JAR timeout in seconds")
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    jars_root = (args.jars_root or (repo_root / "fixtures" / "jars")).resolve()
    candidates_path = (args.candidates or (repo_root / "candidates.jsonl")).resolve()
    out_dir = (args.out_dir or (repo_root / "mixin-coverage")).resolve()
    modcrawl_repo = (
        args.modcrawl_repo or (repo_root.parent / "tools" / "modcrawl")
    ).resolve()

    exe = find_modcrawl(args.modcrawl)
    version_result = subprocess.run(
        [str(exe), "--version"], capture_output=True, check=False
    )
    if version_result.returncode != 0:
        fail(f"modcrawl --version failed: {version_result.stderr.decode('utf-8', 'replace')}")
    version = version_result.stdout.decode("utf-8", "replace").strip()
    exe_hash = sha256_file(exe)

    license_first_line = ""
    license_path = modcrawl_repo / "LICENSE"
    if license_path.is_file():
        with license_path.open(encoding="utf-8", errors="replace") as f:
            license_first_line = f.readline().strip()
        if license_first_line != "MIT License":
            fail(
                f"modcrawl LICENSE first line is {license_first_line!r}, expected 'MIT License'"
            )

    jars = enumerate_jars(jars_root)
    records = load_candidates(candidates_path)

    jar_by_name: dict[str, Path] = {}
    for jar in jars:
        if jar.name in jar_by_name:
            fail(f"duplicate JAR basename {jar.name!r}: {jar_by_name[jar.name]} and {jar}")
        jar_by_name[jar.name] = jar
    record_by_name: dict[str, dict[str, Any]] = {}
    for record in records:
        name = Path(str(record.get("sourceFile", ""))).name
        if not name:
            fail(f"candidate record lacks sourceFile: {record!r}")
        if name in record_by_name:
            fail(f"duplicate candidate sourceFile {name!r}")
        record_by_name[name] = record

    missing_records = sorted(set(jar_by_name) - set(record_by_name))
    missing_jars = sorted(set(record_by_name) - set(jar_by_name))
    if missing_records or missing_jars:
        fail(
            "JAR/candidates mismatch: "
            f"jars without candidate={missing_records}; candidates without jar={missing_jars}"
        )

    status_counts = Counter(str(record.get("status")) for record in records)
    allowed_statuses = {"OK", "FAILED"}
    unexpected = set(status_counts) - allowed_statuses
    if unexpected:
        fail(f"unexpected candidate statuses: {sorted(unexpected)}")
    if sum(status_counts.values()) != len(jars):
        fail(
            f"status sum {sum(status_counts.values())} != filesystem JAR count {len(jars)}"
        )

    raw_dir = out_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    invocation_rows: list[dict[str, Any]] = []
    rows: list[dict[str, Any]] = []

    for index, jar in enumerate(jars, 1):
        record = record_by_name[jar.name]
        stdout, stderr, returncode = run_modcrawl(exe, jar, args.timeout)
        raw_path = raw_dir / jar.relative_to(jars_root).with_suffix(".json")
        raw_path.parent.mkdir(parents=True, exist_ok=True)
        raw_path.write_bytes(stdout)
        stderr_text = stderr.decode("utf-8", "replace")
        invocation_rows.append(
            {
                "sourceFile": jar.name,
                "relativePath": str(jar.relative_to(jars_root)).replace("\\", "/"),
                "exitCode": returncode,
                "stderr": stderr_text.replace("\r", "\\r").replace("\n", "\\n"),
                "rawOutput": str(raw_path.relative_to(out_dir)).replace("\\", "/"),
            }
        )
        if returncode != 0:
            fail(
                f"modcrawl exited {returncode} on {jar}; stderr={stderr_text!r}"
            )
        entries = parse_mixin_entries(stdout, jar)

        naming_targets = record.get("mixinTargets") or []
        naming_hit = bool(naming_targets)
        modcrawl_hit = bool(entries)
        if naming_hit and modcrawl_hit:
            classification = "BOTH"
        elif naming_hit:
            classification = "NAMING_ONLY"
        elif modcrawl_hit:
            classification = "MODCRAWL_ONLY"
        else:
            classification = "NEITHER"

        status = str(record.get("status"))
        rows.append(
            {
                "sourceFile": jar.name,
                "relativePath": str(jar.relative_to(jars_root)).replace("\\", "/"),
                "loaderFamily": record.get("loaderFamily", ""),
                "status": status,
                "modId": record.get("modId", ""),
                "version": record.get("version", ""),
                "namingTargetCount": len(naming_targets),
                "modcrawlEntryCount": len(entries),
                "modcrawlUniqueClassCount": len({e["mixin_class"] for e in entries}),
                "modcrawlUniqueTargetCount": len({e["target"] for e in entries}),
                "namingHit": naming_hit,
                "modcrawlHit": modcrawl_hit,
                "classification": classification if status == "OK" else "FAILED_RECORD",
                "gapRecord": False,
                "filledByModcrawl": "",
                "remainingReason": "",
            }
        )
        print(
            f"[{index:3d}/{len(jars)}] {jar.name}: "
            f"naming={len(naming_targets)} modcrawl={len(entries)} {classification if status == 'OK' else 'FAILED_RECORD'}"
        )

    ok_rows = [row for row in rows if row["status"] == "OK"]
    failed_rows = [row for row in rows if row["status"] == "FAILED"]
    if len(ok_rows) + len(failed_rows) != len(jars):
        fail(f"OK {len(ok_rows)} + FAILED {len(failed_rows)} != JARs {len(jars)}")

    four_counts = Counter(row["classification"] for row in ok_rows)
    for bucket in FOUR_BUCKETS:
        four_counts.setdefault(bucket, 0)
    if sum(four_counts.values()) != len(ok_rows):
        fail(f"four-way bucket sum {sum(four_counts.values())} != OK records {len(ok_rows)}")

    gap_rows = [
        row
        for row in ok_rows
        if not row["namingHit"] and row["namingTargetCount"] == 0
        and bool(record_by_name[row["sourceFile"]].get("mixinConfigs"))
    ]
    remaining_details: dict[str, dict[str, Any]] = {}
    for row in gap_rows:
        record = record_by_name[row["sourceFile"]]
        row["gapRecord"] = True
        if row["modcrawlHit"]:
            row["filledByModcrawl"] = "YES"
        else:
            row["filledByModcrawl"] = "NO"
            detail = classify_remaining(jar_by_name[row["sourceFile"]], record)
            row["remainingReason"] = detail["reason"]
            remaining_details[row["sourceFile"]] = detail
    filled_rows = [row for row in gap_rows if row["filledByModcrawl"] == "YES"]
    remaining_rows = [row for row in gap_rows if row["filledByModcrawl"] == "NO"]
    if len(filled_rows) + len(remaining_rows) != len(gap_rows):
        fail(
            f"gap filled {len(filled_rows)} + remaining {len(remaining_rows)} != gap {len(gap_rows)}"
        )

    empty_naming_rows = [row for row in ok_rows if not row["namingHit"]]
    empty_config_rows = [
        row
        for row in empty_naming_rows
        if not bool(record_by_name[row["sourceFile"]].get("mixinConfigs"))
    ]
    if len(empty_naming_rows) != len(gap_rows) + len(empty_config_rows):
        fail(
            f"empty naming targets {len(empty_naming_rows)} != gap {len(gap_rows)} "
            f"+ empty configs {len(empty_config_rows)}"
        )
    neither_rows = [row for row in ok_rows if row["classification"] == "NEITHER"]
    neither_non_gap_rows = [row for row in neither_rows if not row["gapRecord"]]
    modcrawl_only_rows = [
        row for row in ok_rows if row["classification"] == "MODCRAWL_ONLY"
    ]
    modcrawl_only_non_gap_rows = [
        row for row in modcrawl_only_rows if not row["gapRecord"]
    ]
    if len(neither_rows) != len(remaining_rows) + len(neither_non_gap_rows):
        fail(
            f"NEITHER {len(neither_rows)} != gap-remaining {len(remaining_rows)} "
            f"+ non-gap {len(neither_non_gap_rows)}"
        )
    if len(modcrawl_only_rows) != len(filled_rows) + len(modcrawl_only_non_gap_rows):
        fail(
            f"MODCRAWL_ONLY {len(modcrawl_only_rows)} != gap-filled {len(filled_rows)} "
            f"+ non-gap {len(modcrawl_only_non_gap_rows)}"
        )

    remaining_counts = Counter(row["remainingReason"] for row in remaining_rows)
    unknown_buckets = set(remaining_counts) - set(REMAINING_BUCKETS)
    if unknown_buckets:
        fail(f"unknown remaining reason buckets: {sorted(unknown_buckets)}")
    for bucket in REMAINING_BUCKETS:
        remaining_counts.setdefault(bucket, 0)
    if sum(remaining_counts.values()) != len(remaining_rows):
        fail(
            f"remaining reason bucket sum {sum(remaining_counts.values())} != remaining {len(remaining_rows)}"
        )
    if remaining_counts["UNCLASSIFIED"]:
        fail(
            "UNCLASSIFIED remaining records: "
            + ", ".join(row["sourceFile"] for row in remaining_rows if row["remainingReason"] == "UNCLASSIFIED")
        )

    # Top-level partition of the 10 gap records: filled + every remaining reason.
    gap_bucket_counts: dict[str, int] = {"FILLED_BY_MODCRAWL": len(filled_rows)}
    for row in remaining_rows:
        gap_bucket_counts[row["remainingReason"]] = (
            gap_bucket_counts.get(row["remainingReason"], 0) + 1
        )
    if sum(gap_bucket_counts.values()) != len(gap_rows):
        fail(
            f"gap bucket sum {sum(gap_bucket_counts.values())} != gap total {len(gap_rows)}"
        )

    # Write per-record CSV.
    out_dir.mkdir(parents=True, exist_ok=True)
    comparison_path = out_dir / "comparison.csv"
    fieldnames = [
        "sourceFile",
        "relativePath",
        "loaderFamily",
        "status",
        "modId",
        "version",
        "namingTargetCount",
        "modcrawlEntryCount",
        "modcrawlUniqueClassCount",
        "modcrawlUniqueTargetCount",
        "namingHit",
        "modcrawlHit",
        "classification",
        "gapRecord",
        "filledByModcrawl",
        "remainingReason",
    ]
    with comparison_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    invocation_path = out_dir / "modcrawl-invocations.tsv"
    with invocation_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["sourceFile", "relativePath", "exitCode", "stderr", "rawOutput"],
            delimiter="\t",
        )
        writer.writeheader()
        writer.writerows(invocation_rows)

    naming_only_mod_ids = unique_mod_ids([r for r in ok_rows if r["classification"] == "NAMING_ONLY"])
    modcrawl_only_mod_ids = unique_mod_ids([r for r in ok_rows if r["classification"] == "MODCRAWL_ONLY"])
    both_mod_ids = unique_mod_ids([r for r in ok_rows if r["classification"] == "BOTH"])
    neither_mod_ids = unique_mod_ids([r for r in ok_rows if r["classification"] == "NEITHER"])

    summary = {
        "generatedAt": datetime.datetime.now().astimezone().isoformat(),
        "modcrawl": {
            "path": str(exe),
            "version": version,
            "sha256": exe_hash,
            "licenseFirstLine": license_first_line,
            "commandTemplate": f"{exe} mixins --json <jar>",
        },
        "inputs": {
            "repoRoot": str(repo_root),
            "jarsRoot": str(jars_root),
            "candidates": str(candidates_path),
        },
        "counts": {
            "jarsEnumerated": len(jars),
            "candidateRecords": len(records),
            "status": dict(sorted(status_counts.items())),
            "okRecords": len(ok_rows),
            "failedRecords": len(failed_rows),
            "invocationFailures": sum(1 for row in invocation_rows if row["exitCode"] != 0),
        },
        "okFourWay": {
            "total": len(ok_rows),
            "counts": {bucket: four_counts[bucket] for bucket in FOUR_BUCKETS},
            "sum": sum(four_counts.values()),
            "bothModIds": both_mod_ids,
            "namingOnlyModIds": naming_only_mod_ids,
            "modcrawlOnlyModIds": modcrawl_only_mod_ids,
            "neitherModIds": neither_mod_ids,
            "emptyNamingTargets": {
                "total": len(empty_naming_rows),
                "withNonEmptyMixinConfigs": len(gap_rows),
                "withEmptyMixinConfigs": len(empty_config_rows),
            },
            "neitherBreakdown": {
                "gapRemaining": len(remaining_rows),
                "emptyConfigNoModcrawl": len(neither_non_gap_rows),
            },
            "modcrawlOnlyBreakdown": {
                "gapFilled": len(filled_rows),
                "emptyConfigButAnnotated": len(modcrawl_only_non_gap_rows),
            },
            "namingOnlyRecords": [
                {
                    "sourceFile": r["sourceFile"],
                    "modId": r["modId"],
                    "loaderFamily": r["loaderFamily"],
                    "namingTargetCount": r["namingTargetCount"],
                }
                for r in ok_rows
                if r["classification"] == "NAMING_ONLY"
            ],
            "modcrawlOnlyRecords": [
                {
                    "sourceFile": r["sourceFile"],
                    "modId": r["modId"],
                    "loaderFamily": r["loaderFamily"],
                    "modcrawlEntryCount": r["modcrawlEntryCount"],
                }
                for r in ok_rows
                if r["classification"] == "MODCRAWL_ONLY"
            ],
        },
        "gap": {
            "definition": "status=OK, mixinTargets empty, mixinConfigs non-empty",
            "total": len(gap_rows),
            "filled": len(filled_rows),
            "remaining": len(remaining_rows),
            "filledModIds": unique_mod_ids(filled_rows),
            "remainingModIds": unique_mod_ids(remaining_rows),
            "filledRecords": [
                {
                    "sourceFile": r["sourceFile"],
                    "modId": r["modId"],
                    "loaderFamily": r["loaderFamily"],
                    "modcrawlEntryCount": r["modcrawlEntryCount"],
                }
                for r in filled_rows
            ],
            "remainingRecords": [
                {
                    "sourceFile": r["sourceFile"],
                    "modId": r["modId"],
                    "loaderFamily": r["loaderFamily"],
                    "reason": r["remainingReason"],
                    "detail": remaining_details.get(r["sourceFile"], {}),
                }
                for r in remaining_rows
            ],
            "remainingReasonBuckets": {
                bucket: remaining_counts[bucket] for bucket in REMAINING_BUCKETS
            },
            "remainingReasonSum": sum(remaining_counts.values()),
            "gapBuckets": gap_bucket_counts,
            "gapBucketSum": sum(gap_bucket_counts.values()),
        },
        "assertions": {
            "jarsEqualsCandidates": len(jars) == len(records),
            "statusSumEqualsJars": sum(status_counts.values()) == len(jars),
            "fourWaySumEqualsOk": sum(four_counts.values()) == len(ok_rows),
            "gapFilledPlusRemainingEqualsTotal": len(filled_rows) + len(remaining_rows) == len(gap_rows),
            "remainingReasonSumEqualsRemaining": sum(remaining_counts.values()) == len(remaining_rows),
            "gapBucketSumEqualsTotal": sum(gap_bucket_counts.values()) == len(gap_rows),
            "emptyNamingEqualsGapPlusEmptyConfig": len(empty_naming_rows)
            == len(gap_rows) + len(empty_config_rows),
            "neitherBreaksDown": len(neither_rows)
            == len(remaining_rows) + len(neither_non_gap_rows),
            "modcrawlOnlyBreaksDown": len(modcrawl_only_rows)
            == len(filled_rows) + len(modcrawl_only_non_gap_rows),
            "noUnclassifiedRemaining": remaining_counts["UNCLASSIFIED"] == 0,
        },
    }
    if not all(summary["assertions"].values()):
        fail(f"assertion failed: {summary['assertions']}")

    summary_path = out_dir / "summary.json"
    summary_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    # Human-readable report.
    lines: list[str] = []
    lines.append("# Mixin coverage cross-check: naming convention vs modcrawl")
    lines.append("")
    lines.append(f"- generated: {summary['generatedAt']}")
    lines.append(f"- modcrawl: `{version}`")
    lines.append(f"- modcrawl sha256: `{exe_hash}`")
    lines.append(f"- modcrawl LICENSE first line: `{license_first_line or 'not found'}`")
    lines.append(f"- command: `{exe} mixins --json <jar>`")
    lines.append(
        f"- filesystem JARs: **{len(jars)}**; candidates.jsonl records: **{len(records)}**; "
        f"OK: **{len(ok_rows)}**; FAILED: **{len(failed_rows)}**"
    )
    lines.append("")
    lines.append("## 191 OK records: four-way comparison")
    lines.append("")
    lines.append("| bucket | records | unique modIds |")
    lines.append("| --- | ---: | ---: |")
    for bucket in FOUR_BUCKETS:
        lines.append(
            f"| {bucket} | {four_counts[bucket]} | "
            f"{len({'BOTH': both_mod_ids, 'NAMING_ONLY': naming_only_mod_ids, 'MODCRAWL_ONLY': modcrawl_only_mod_ids, 'NEITHER': neither_mod_ids}[bucket])} |"
        )
    lines.append(f"| **sum** | **{sum(four_counts.values())}** | |")
    lines.append("")
    lines.append(
        "Per-bucket unique modIds may overlap: a modId with both Fabric and NeoForge "
        "variants can land in two buckets (for example `sodium`)."
    )
    lines.append("")
    lines.append("## Denominator check")
    lines.append("")
    lines.append(
        f"- OK records with empty naming targets: **{len(empty_naming_rows)}** = "
        f"gap (non-empty `mixinConfigs`) **{len(gap_rows)}** + empty `mixinConfigs` "
        f"**{len(empty_config_rows)}**"
    )
    lines.append(
        f"- NEITHER **{len(neither_rows)}** = gap-remaining **{len(remaining_rows)}** + "
        f"empty-config records with no modcrawl hit **{len(neither_non_gap_rows)}**"
    )
    lines.append(
        f"- MODCRAWL_ONLY **{len(modcrawl_only_rows)}** = gap-filled **{len(filled_rows)}** + "
        f"empty-config records where modcrawl still found `@Mixin` **{len(modcrawl_only_non_gap_rows)}**"
    )
    lines.append("")
    lines.append("### NAMING_ONLY (naming hit, modcrawl hit=0)")
    lines.append("")
    lines.append(format_mod_ids([r for r in ok_rows if r["classification"] == "NAMING_ONLY"]) or "(none)")
    lines.append("")
    lines.append("### MODCRAWL_ONLY (naming hit=0, modcrawl hit)")
    lines.append("")
    lines.append(format_mod_ids([r for r in ok_rows if r["classification"] == "MODCRAWL_ONLY"]) or "(none)")
    lines.append("")
    lines.append("## The gap set (OK, empty mixinTargets, non-empty mixinConfigs)")
    lines.append("")
    lines.append(
        f"- records: **{len(gap_rows)}**; filled by modcrawl: **{len(filled_rows)}**; "
        f"remaining: **{len(remaining_rows)}**"
    )
    lines.append(
        f"- unique modIds: {len(unique_mod_ids(gap_rows))} "
        f"(filled {len(unique_mod_ids(filled_rows))}, remaining {len(unique_mod_ids(remaining_rows))})"
    )
    lines.append("")
    lines.append("| modId | loader | jar | modcrawl entries | status |")
    lines.append("| --- | --- | --- | ---: | --- |")
    for row in gap_rows:
        lines.append(
            f"| {row['modId']} | {row['loaderFamily']} | {row['sourceFile']} | "
            f"{row['modcrawlEntryCount']} | {'filled' if row['filledByModcrawl'] == 'YES' else 'remaining'} |"
        )
    lines.append("")
    lines.append("### Gap top-level partition")
    lines.append("")
    lines.append("| bucket | records |")
    lines.append("| --- | ---: |")
    for bucket in ("FILLED_BY_MODCRAWL", *REMAINING_BUCKETS):
        lines.append(f"| {bucket} | {gap_bucket_counts.get(bucket, 0)} |")
    lines.append(f"| **sum** | **{sum(gap_bucket_counts.values())}** |")
    lines.append("")
    lines.append("## Remaining reason buckets")
    lines.append("")
    lines.append("| bucket | records |")
    lines.append("| --- | ---: |")
    for bucket in REMAINING_BUCKETS:
        lines.append(f"| {bucket} | {remaining_counts[bucket]} |")
    lines.append(f"| **sum** | **{sum(remaining_counts.values())}** |")
    lines.append("")
    lines.append("## Assertions")
    lines.append("")
    for name, passed in summary["assertions"].items():
        lines.append(f"- {name}: {'PASS' if passed else 'FAIL'}")
    lines.append("")
    lines.append(
        "Note: `mixinTargets` is produced by naming-convention inference; "
        "modcrawl reads class-level `@Mixin` annotations. A non-empty list from "
        "either side is a detection, not proof that the target names agree."
    )
    lines.append("")

    report_path = out_dir / "report.md"
    report_path.write_text("\n".join(lines), encoding="utf-8")

    print()
    print(f"JARs: {len(jars)}; OK: {len(ok_rows)}; FAILED: {len(failed_rows)}")
    print(f"four-way: {dict(four_counts)} (sum={sum(four_counts.values())})")
    print(
        f"gap: {len(gap_rows)} = filled {len(filled_rows)} + remaining {len(remaining_rows)}"
    )
    print(f"remaining reasons: {dict(remaining_counts)} (sum={sum(remaining_counts.values())})")
    print(f"report: {report_path}")
    print("ALL ASSERTIONS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
