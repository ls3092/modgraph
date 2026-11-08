#!/usr/bin/env python3
"""Compare Forbric's named failure lists with modgraph's existing metadata.

Reads a local snapshot of Forbric's MOD_TEST_FAILURES.md and three existing
modgraph artifacts (candidates.jsonl, dependencies.csv, conflicts.csv). It does
not fetch anything, does not run Minecraft, and does not modify existing
artifacts.

The Markdown file is a historical log, not one flat list. This script parses
the two explicit named tables:

  * 2026-09-27 "main 上不成功的 mod（35 个）"  (35 linked rows)
  * 2026-10-01 "仍未严格成功的 12 个主体"      (12 plain rows)

They are different samples and are never merged. The 2026-10-11 "4 both-fail"
entries are not named in the document, so they are not guessed or included.

Each row gets exactly one primary bucket. The bucket sum must equal the parsed
row total; an unclassified row aborts the script.

Usage:
    python verify_forbric_failures.py
    python verify_forbric_failures.py --failures path/to/MOD_TEST_FAILURES.md
"""

from __future__ import annotations

import argparse
import csv
import datetime
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

PRIMARY_BUCKETS = (
    "NO_CANDIDATE",
    "AMBIGUOUS_CANDIDATE",
    "LOADER_MISMATCH",
    "STATUS_FAILED",
    "EXCLUDED_RENDER_CORE",
    "DEPENDENCY_SIGNAL",
    "MIXIN_SIGNAL_ONLY",
    "MATCHED_NO_SIGNAL",
    "UNCLASSIFIED",
)

# Modrinth slug / display name to modgraph modId, only where the names differ.
ALIASES = {
    "lambdynamiclights": ("lambdynlights",),
    "playeranimationlibrary": ("playeranimator",),
    "alexsmobscontinued": ("alexsmobs",),
    "bclibneoforge": ("bclib",),
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def norm(value: Any) -> str:
    return re.sub(r"[^a-z0-9]", "", str(value or "").lower())


def slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower().replace("’", "").replace("'", ""))


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                fail(f"{path}:{line_no}: invalid JSON: {exc}")
            if not isinstance(value, dict):
                fail(f"{path}:{line_no}: candidate is not an object")
            records.append(value)
    return records


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def find_heading(lines: list[str], marker: str) -> tuple[int, int]:
    matches = [
        i for i, line in enumerate(lines) if line.startswith("### ") and marker in line
    ]
    if len(matches) != 1:
        fail(f"expected exactly one '### ...{marker}...' heading, found {len(matches)}")
    start = matches[0]
    end = next(
        (i for i in range(start + 1, len(lines)) if lines[i].startswith("#")),
        len(lines),
    )
    return start, end


def split_cells(line: str) -> list[str]:
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def parse_linked_list(lines: list[str], marker: str) -> list[dict[str, str]]:
    start, end = find_heading(lines, marker)
    rows: list[dict[str, str]] = []
    for line_no in range(start + 1, end):
        line = lines[line_no].strip()
        if not (line.startswith("|") and line.endswith("|")):
            continue
        cells = split_cells(line)
        if not cells or not cells[0].startswith("["):
            continue
        match = re.search(r"\[([^\]]+)\]\(([^)]+)\)", cells[0])
        if not match:
            fail(f"line {line_no + 1}: cannot parse linked mod cell: {cells[0]!r}")
        if len(cells) != 7:
            fail(f"line {line_no + 1}: expected 7 cells, got {len(cells)}: {cells!r}")
        name, url = match.group(1), match.group(2)
        rows.append(
            {
                "forbricName": name,
                "forbricSlug": url.rstrip("/").rsplit("/", 1)[-1],
                "batch": cells[1],
                "source": cells[2],
                "loader": cells[3],
                "forbricVersion": cells[4],
                "state": cells[5],
                "detail": cells[6],
            }
        )
    if not rows:
        fail(f"no linked rows parsed for heading containing {marker!r}")
    return rows


def parse_plain_list(lines: list[str], marker: str) -> list[dict[str, str]]:
    start, end = find_heading(lines, marker)
    rows: list[dict[str, str]] = []
    for line_no in range(start + 1, end):
        line = lines[line_no].strip()
        if not (line.startswith("|") and line.endswith("|")):
            continue
        cells = split_cells(line)
        if not cells or cells[0] in ("主体", "mod"):
            continue
        if set(cells[0]) <= set("-: "):
            continue
        if len(cells) != 3:
            fail(f"line {line_no + 1}: expected 3 cells, got {len(cells)}: {cells!r}")
        rows.append(
            {
                "forbricName": cells[0],
                "forbricSlug": slugify(cells[0]),
                "batch": "",
                "source": "",
                "loader": "",
                "forbricVersion": "",
                "state": cells[1],
                "detail": cells[2],
            }
        )
    if not rows:
        fail(f"no plain rows parsed for heading containing {marker!r}")
    return rows


def candidate_index(records: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    index: dict[str, list[dict[str, Any]]] = defaultdict(list)
    seen_by_key: dict[str, set[str]] = defaultdict(set)
    for record in records:
        source = str(record.get("sourceFile", ""))
        keys = {
            norm(record.get("modId")),
            norm(record.get("name")),
            norm(Path(source).stem),
        }
        for key in keys:
            if key and source not in seen_by_key[key]:
                seen_by_key[key].add(source)
                index[key].append(record)
    for key in index:
        index[key].sort(key=lambda r: str(r.get("sourceFile", "")))
    return index


def find_candidates(index: dict[str, list[dict[str, Any]]], row: dict[str, str]) -> list[dict[str, Any]]:
    keys = [norm(row["forbricName"]), norm(row["forbricSlug"])]
    for key in list(keys):
        keys.extend(ALIASES.get(key, ()))
    hits: dict[str, dict[str, Any]] = {}
    for key in keys:
        for record in index.get(key, []):
            hits[str(record.get("sourceFile", ""))] = record
    return sorted(hits.values(), key=lambda r: str(r.get("sourceFile", "")))


def match_row(
    row: dict[str, str],
    index: dict[str, list[dict[str, Any]]],
    dep_by_mod: dict[str, list[dict[str, str]]],
    conf_by_mod: dict[str, list[dict[str, str]]],
) -> dict[str, Any]:
    hits = find_candidates(index, row)
    selected: dict[str, Any] | None = None
    loader_match = False
    ambiguous = 0
    if hits:
        loader = row["loader"].lower()
        same_loader = [
            record
            for record in hits
            if str(record.get("loaderFamily", "")).lower() == loader
        ]
        if same_loader:
            ambiguous = len(same_loader)
            selected = same_loader[0]
            loader_match = True
        else:
            selected = hits[0]
            loader_match = False

    field_flags = {
        "statusFailed": False,
        "supportPolicyExcluded": False,
        "mixinTargetsNonEmpty": False,
        "nestedJarsNonEmpty": False,
        "dependsNonEmpty": False,
        "dependenciesNonSatisfied": False,
        "conflictsHighMedium": False,
        "versionExactMatch": False,
    }
    dep_outcomes: Counter[str] = Counter()
    conflict_levels: Counter[str] = Counter()
    candidate = {
        "candidateModId": "",
        "candidateName": "",
        "candidateLoader": "",
        "candidateVersion": "",
        "candidateSourceFile": "",
    }

    if selected:
        mod_id = str(selected.get("modId", ""))
        candidate = {
            "candidateModId": mod_id,
            "candidateName": str(selected.get("name", "")),
            "candidateLoader": str(selected.get("loaderFamily", "")),
            "candidateVersion": str(selected.get("version", "")),
            "candidateSourceFile": str(selected.get("sourceFile", "")),
        }
        dep_outcomes = Counter(
            str(r.get("outcome", "")) for r in dep_by_mod.get(mod_id, [])
        )
        conflict_levels = Counter(
            str(r.get("level", "")) for r in conf_by_mod.get(mod_id, [])
        )
        field_flags = {
            "statusFailed": str(selected.get("status", "")) == "FAILED",
            "supportPolicyExcluded": str(selected.get("supportPolicy", "")) == "EXCLUDED",
            "mixinTargetsNonEmpty": bool(selected.get("mixinTargets"))
            or bool(selected.get("mixinConfigs")),
            "nestedJarsNonEmpty": bool(selected.get("nestedJars")),
            "dependsNonEmpty": bool(selected.get("depends")),
            "dependenciesNonSatisfied": any(
                outcome != "SATISFIED" for outcome in dep_outcomes
            ),
            "conflictsHighMedium": (
                conflict_levels.get("HIGH", 0) + conflict_levels.get("MEDIUM", 0) > 0
            ),
            "versionExactMatch": str(selected.get("version", "")) == row["forbricVersion"],
        }

    if not selected:
        bucket = "NO_CANDIDATE"
    elif ambiguous > 1:
        bucket = "AMBIGUOUS_CANDIDATE"
    elif not loader_match:
        bucket = "LOADER_MISMATCH"
    elif field_flags["statusFailed"]:
        bucket = "STATUS_FAILED"
    elif field_flags["supportPolicyExcluded"]:
        bucket = "EXCLUDED_RENDER_CORE"
    elif field_flags["dependenciesNonSatisfied"]:
        bucket = "DEPENDENCY_SIGNAL"
    elif field_flags["mixinTargetsNonEmpty"]:
        bucket = "MIXIN_SIGNAL_ONLY"
    else:
        bucket = "MATCHED_NO_SIGNAL"

    return {
        **row,
        **candidate,
        "loaderMatch": loader_match,
        "sameLoaderCandidates": ambiguous,
        **field_flags,
        "dependencyOutcomes": ";".join(
            f"{key}={value}" for key, value in sorted(dep_outcomes.items())
        ),
        "conflictLevels": ";".join(
            f"{key}={value}" for key, value in sorted(conflict_levels.items())
        ),
        "primaryBucket": bucket,
    }


def summarize_list(
    key: str,
    title: str,
    rows: list[dict[str, Any]],
) -> dict[str, Any]:
    bucket_counts = Counter(row["primaryBucket"] for row in rows)
    for bucket in PRIMARY_BUCKETS:
        bucket_counts.setdefault(bucket, 0)
    unknown = set(bucket_counts) - set(PRIMARY_BUCKETS)
    if unknown:
        fail(f"list {key}: unknown primary buckets: {sorted(unknown)}")
    if sum(bucket_counts.values()) != len(rows):
        fail(
            f"list {key}: bucket sum {sum(bucket_counts.values())} != rows {len(rows)}"
        )
    if bucket_counts["UNCLASSIFIED"]:
        fail(
            f"list {key}: unclassified rows: "
            + ", ".join(row["forbricName"] for row in rows if row["primaryBucket"] == "UNCLASSIFIED")
        )

    matched = [row for row in rows if row["primaryBucket"] != "NO_CANDIDATE"]
    unmatched = [row for row in rows if row["primaryBucket"] == "NO_CANDIDATE"]
    field_counts = {
        "candidateLocated": len(matched),
        "statusFailed": sum(1 for row in rows if row["statusFailed"]),
        "supportPolicyExcluded": sum(
            1 for row in rows if row["supportPolicyExcluded"]
        ),
        "dependenciesNonSatisfied": sum(
            1 for row in rows if row["dependenciesNonSatisfied"]
        ),
        "mixinTargetsNonEmpty": sum(
            1 for row in rows if row["mixinTargetsNonEmpty"]
        ),
        "conflictsHighMedium": sum(
            1 for row in rows if row["conflictsHighMedium"]
        ),
        "nestedJarsNonEmpty": sum(
            1 for row in rows if row["nestedJarsNonEmpty"]
        ),
        "dependsNonEmpty": sum(1 for row in rows if row["dependsNonEmpty"]),
        "loaderMatch": sum(1 for row in rows if row["loaderMatch"]),
        "loaderMismatch": sum(
            1 for row in rows if row["primaryBucket"] == "LOADER_MISMATCH"
        ),
        "versionExactMatch": sum(
            1 for row in rows if row["versionExactMatch"]
        ),
    }
    return {
        "key": key,
        "title": title,
        "total": len(rows),
        "uniqueForbricIds": len({row["forbricSlug"] for row in rows}),
        "primaryBuckets": {bucket: bucket_counts[bucket] for bucket in PRIMARY_BUCKETS},
        "primaryBucketSum": sum(bucket_counts.values()),
        "matchedRows": len(matched),
        "uniqueMatchedForbricIds": len({row["forbricSlug"] for row in matched}),
        "noCandidateRows": len(unmatched),
        "uniqueNoCandidateForbricIds": len({row["forbricSlug"] for row in unmatched}),
        "fieldCounts": field_counts,
        "matchedRowsDetail": [
            {
                "forbricName": row["forbricName"],
                "forbricSlug": row["forbricSlug"],
                "loader": row["loader"],
                "forbricVersion": row["forbricVersion"],
                "state": row["state"],
                "candidateModId": row["candidateModId"],
                "candidateLoader": row["candidateLoader"],
                "candidateVersion": row["candidateVersion"],
                "primaryBucket": row["primaryBucket"],
                "dependencyOutcomes": row["dependencyOutcomes"],
                "conflictLevels": row["conflictLevels"],
            }
            for row in rows
            if row["primaryBucket"] != "NO_CANDIDATE"
        ],
        "unmatched": [
            {
                "forbricName": row["forbricName"],
                "forbricSlug": row["forbricSlug"],
                "loader": row["loader"],
                "forbricVersion": row["forbricVersion"],
                "state": row["state"],
            }
            for row in unmatched
        ],
    }


def write_report(
    path: Path,
    failure_path: Path,
    failure_sha: str,
    failures_first_20: list[str],
    summary: dict[str, Any],
) -> None:
    lines: list[str] = []
    lines.append("# Forbric failure-list vs modgraph metadata")
    lines.append("")
    lines.append(f"- generated: {summary['generatedAt']}")
    lines.append(f"- failure file: `{failure_path}`")
    lines.append(f"- failure SHA-256: `{failure_sha}`")
    lines.append(f"- candidates: `{summary['inputs']['candidates']}`")
    lines.append(f"- candidate records: **{summary['candidateRecords']}**")
    lines.append(
        "- lists parsed: "
        + ", ".join(
            f"`{key}` {value['total']} rows"
            for key, value in summary["lists"].items()
        )
        + " (different samples; never merged)"
    )
    lines.append("")
    lines.append("## Original first 20 lines")
    lines.append("")
    lines.append("```markdown")
    lines.extend(failures_first_20)
    lines.append("```")
    lines.append("")
    for key, value in summary["lists"].items():
        lines.append(f"## List `{key}`: {value['title']}")
        lines.append("")
        lines.append(
            f"- rows: **{value['total']}**; unique Forbric ids: **{value['uniqueForbricIds']}**"
        )
        lines.append(
            f"- located in candidates: rows **{value['matchedRows']}** / unique ids "
            f"**{value['uniqueMatchedForbricIds']}**; no candidate: rows "
            f"**{value['noCandidateRows']}** / unique ids "
            f"**{value['uniqueNoCandidateForbricIds']}**"
        )
        lines.append("")
        lines.append("### Primary buckets")
        lines.append("")
        lines.append("| bucket | rows |")
        lines.append("| --- | ---: |")
        for bucket in PRIMARY_BUCKETS:
            lines.append(f"| {bucket} | {value['primaryBuckets'][bucket]} |")
        lines.append(f"| **sum** | **{value['primaryBucketSum']}** |")
        lines.append("")
        lines.append("### Non-exclusive field counts")
        lines.append("")
        lines.append("| field | rows |")
        lines.append("| --- | ---: |")
        for field, count in value["fieldCounts"].items():
            lines.append(f"| {field} | {count} |")
        lines.append("")
        lines.append("### Located rows")
        lines.append("")
        if value["matchedRowsDetail"]:
            lines.append(
                "| mod | loader | state | candidate modId | candidate loader | bucket | dependency outcomes | conflict levels |"
            )
            lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
            for row in value["matchedRowsDetail"]:
                lines.append(
                    f"| {row['forbricName']} | {row['loader']} | {row['state']} | "
                    f"{row['candidateModId']} | {row['candidateLoader']} | {row['primaryBucket']} | "
                    f"{row['dependencyOutcomes']} | {row['conflictLevels']} |"
                )
        else:
            lines.append("(none)")
        lines.append("")
        lines.append("### Unmatched (no candidate)")
        lines.append("")
        if value["unmatched"]:
            for row in value["unmatched"]:
                lines.append(
                    f"- `{row['forbricSlug']}` — {row['forbricName']}"
                    + (f" ({row['loader']})" if row["loader"] else "")
                )
        else:
            lines.append("(none)")
        lines.append("")
    lines.append("## Assertions")
    lines.append("")
    for name, passed in summary["assertions"].items():
        lines.append(f"- {name}: {'PASS' if passed else 'FAIL'}")
    lines.append("")
    lines.append("## Reading notes")
    lines.append("")
    lines.append(
        "- The Markdown file is a historical log with several snapshots. The two "
        "named tables above are parsed separately; they are different samples and "
        "must not be added together."
    )
    lines.append(
        "- The 2026-10-11 section reports 4 both-failure mods but does not name them. "
        "They are not guessed or counted."
    )
    lines.append(
        "- The 2026-10-03 section says 10 of the 12 still fail (C2ME and LPLM improved), "
        "but does not list all 10 names; this script does not construct that list."
    )
    lines.append(
        "- A primary bucket only says an existing static field can locate or flag "
        "the row. It is not proof that the field explains the Forbric runtime failure."
    )
    lines.append(
        "- Each selected candidate is a 1.21.1 record; the Forbric rows are 26.2. "
        "Version exact matches are therefore 0 for the located rows."
    )
    lines.append(
        "- `dependencies.csv` and `conflicts.csv` are keyed by modId, not by loader "
        "variant; Fabric/NeoForge variants of the same modId can merge in those counters."
    )
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="modgraph repository root (default: script directory)",
    )
    parser.add_argument(
        "--failures",
        type=Path,
        help="local snapshot of MOD_TEST_FAILURES.md (default: <repo-root>/forbric-comparison/MOD_TEST_FAILURES.md)",
    )
    parser.add_argument("--candidates", type=Path)
    parser.add_argument("--dependencies", type=Path)
    parser.add_argument("--conflicts", type=Path)
    parser.add_argument("--out-dir", type=Path)
    parser.add_argument(
        "--expected-sha256",
        help="optional expected SHA-256 of the failure file; mismatch aborts",
    )
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    failure_path = (
        args.failures
        or (repo_root / "forbric-comparison" / "MOD_TEST_FAILURES.md")
    ).resolve()
    candidates_path = (args.candidates or (repo_root / "candidates.jsonl")).resolve()
    dependencies_path = (args.dependencies or (repo_root / "dependencies.csv")).resolve()
    conflicts_path = (args.conflicts or (repo_root / "conflicts.csv")).resolve()
    out_dir = (args.out_dir or (repo_root / "forbric-comparison")).resolve()

    if not failure_path.is_file():
        fail(f"failure file not found: {failure_path}")
    actual_sha = sha256_file(failure_path)
    if args.expected_sha256 and actual_sha.lower() != args.expected_sha256.lower():
        fail(
            f"failure file SHA-256 mismatch: expected {args.expected_sha256}, got {actual_sha}"
        )

    text = failure_path.read_text(encoding="utf-8-sig")
    lines = text.splitlines()
    failures_first_20 = lines[:20]

    list_35 = parse_linked_list(lines, "35")
    list_12 = parse_plain_list(lines, "12")
    if {row["forbricSlug"] for row in list_35} & {
        row["forbricSlug"] for row in list_12
    }:
        fail("the two parsed lists unexpectedly share a Forbric id; refusing to merge them")

    candidates = load_jsonl(candidates_path)
    dependencies = load_csv(dependencies_path)
    conflicts = load_csv(conflicts_path)
    index = candidate_index(candidates)

    dep_by_mod: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in dependencies:
        dep_by_mod[str(row.get("modId", ""))].append(row)
    conf_by_mod: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in conflicts:
        conf_by_mod[str(row.get("modA", ""))].append(row)
        conf_by_mod[str(row.get("modB", ""))].append(row)

    rows_35 = [match_row(row, index, dep_by_mod, conf_by_mod) for row in list_35]
    rows_12 = [match_row(row, index, dep_by_mod, conf_by_mod) for row in list_12]

    summary = {
        "generatedAt": datetime.datetime.now().astimezone().isoformat(),
        "inputs": {
            "repoRoot": str(repo_root),
            "failures": str(failure_path),
            "failureSha256": actual_sha,
            "candidates": str(candidates_path),
            "dependencies": str(dependencies_path),
            "conflicts": str(conflicts_path),
        },
        "candidateRecords": len(candidates),
        "lists": {
            "35": summarize_list(
                "35",
                "2026-09-27 main 上不成功的 mod（35 个）",
                rows_35,
            ),
            "12": summarize_list(
                "12",
                "2026-10-01 仍未严格成功的 12 个主体",
                rows_12,
            ),
        },
        "assertions": {},
    }

    assertions = {
        "failureFileExists": failure_path.is_file(),
        "sha256Recorded": len(actual_sha) == 64,
        "list35BucketsEqualRows": summary["lists"]["35"]["primaryBucketSum"]
        == summary["lists"]["35"]["total"],
        "list12BucketsEqualRows": summary["lists"]["12"]["primaryBucketSum"]
        == summary["lists"]["12"]["total"],
        "list35LocatedPlusUnmatchedEqualRows": summary["lists"]["35"]["matchedRows"]
        + summary["lists"]["35"]["noCandidateRows"]
        == summary["lists"]["35"]["total"],
        "list12LocatedPlusUnmatchedEqualRows": summary["lists"]["12"]["matchedRows"]
        + summary["lists"]["12"]["noCandidateRows"]
        == summary["lists"]["12"]["total"],
        "noUnclassifiedList35": summary["lists"]["35"]["primaryBuckets"][
            "UNCLASSIFIED"
        ]
        == 0,
        "noUnclassifiedList12": summary["lists"]["12"]["primaryBuckets"][
            "UNCLASSIFIED"
        ]
        == 0,
        "listsAreDisjoint": not (
            {row["forbricSlug"] for row in list_35}
            & {row["forbricSlug"] for row in list_12}
        ),
    }
    summary["assertions"] = assertions
    if not all(assertions.values()):
        fail(f"assertion failed: {assertions}")

    out_dir.mkdir(parents=True, exist_ok=True)
    comparison_path = out_dir / "comparison.csv"
    fieldnames = [
        "list",
        "forbricName",
        "forbricSlug",
        "loader",
        "forbricVersion",
        "state",
        "detail",
        "candidateModId",
        "candidateName",
        "candidateLoader",
        "candidateVersion",
        "candidateSourceFile",
        "loaderMatch",
        "sameLoaderCandidates",
        "statusFailed",
        "supportPolicyExcluded",
        "mixinTargetsNonEmpty",
        "nestedJarsNonEmpty",
        "dependsNonEmpty",
        "dependenciesNonSatisfied",
        "conflictsHighMedium",
        "versionExactMatch",
        "dependencyOutcomes",
        "conflictLevels",
        "primaryBucket",
        "batch",
        "source",
    ]
    with comparison_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for key, rows in (("35", rows_35), ("12", rows_12)):
            for row in rows:
                out = {k: row.get(k, "") for k in fieldnames if k != "list"}
                out["list"] = key
                writer.writerow(out)

    summary_path = out_dir / "summary.json"
    summary_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    report_path = out_dir / "report.md"
    write_report(report_path, failure_path, actual_sha, failures_first_20, summary)

    print(f"failure SHA-256: {actual_sha}")
    print(f"candidates: {len(candidates)}")
    for key, value in summary["lists"].items():
        print(
            f"list {key}: total={value['total']} matched={value['matchedRows']} "
            f"noCandidate={value['noCandidateRows']} buckets={value['primaryBuckets']}"
        )
    print(f"report: {report_path}")
    print("ALL ASSERTIONS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
