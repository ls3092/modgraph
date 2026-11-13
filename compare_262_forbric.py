#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""compare_262_forbric.py -- 段 4 主链第 3 步：把 Forbric 两份具名表对到 26.2 批次。

口径（写死，不随数据变）：
  * 两份表分开标注日期；**不合并**；所有合计一律写“两份清单合计”。
  * 2026-10-11 的复测“已修到 0”不参与任何失败标签，本脚本根本不读那段。
  * 每一行必须落进且只落进一个桶；桶和必须等于两份表合计行数（断言 H1）。

桶定义（行级）：
  LOCATED            该身份在本轮采到的 26.2 批次里找到候选记录
  NO_262             该身份在 Modrinth 有项目但没有 26.2 版本
  NOT_FOUND          该身份在 Modrinth 无法保守匹配到项目
  DOWNLOAD_FAILED    该身份有 26.2 版本但本轮下载失败
  NOT_IN_BATCH       该身份有 26.2 版本但不在本轮 --limit 样本内

字段信号（仅对 LOCATED 行统计，非互斥、不相加成总数）：
  statusFailed / supportPolicyExcluded / dependenciesNonSatisfied /
  mixinTargetsNonEmpty / conflictsHighMedium / loaderMismatch

用法：
    python -u compare_262_forbric.py
"""

import argparse
import csv
import json
import sys
from pathlib import Path

BUCKETS = ("LOCATED", "NO_262", "NOT_FOUND", "DOWNLOAD_FAILED", "NOT_IN_BATCH")


def normalize(value):
    return "".join(ch for ch in str(value or "").lower() if ch.isalnum())


def load_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))


def load_jsonl(path):
    out = []
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            out.append(json.loads(line))
        except ValueError as exc:
            raise SystemExit("FAIL: bad JSON line in %s: %s" % (path, exc))
    return out


def load_csv(path):
    try:
        with open(path, encoding="utf-8", newline="") as fh:
            return list(csv.DictReader(fh))
    except OSError:
        return []


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sources-a", default="forbric-comparison/sources-A.json")
    ap.add_argument("--lane-b", default="forbric-comparison/sources-262-forbric.json")
    ap.add_argument("--manifest", default="dataset-262/forbric/download-manifest.json")
    ap.add_argument("--m0-dir", default="dataset-262/forbric")
    ap.add_argument("--out", default="dataset-262/forbric/comparison-262.json")
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    a = load_json(args.sources_a)
    b = load_json(args.lane_b)
    manifest = load_json(args.manifest)
    candidates = load_jsonl(str(Path(args.m0_dir) / "candidates.jsonl"))
    deps = load_csv(str(Path(args.m0_dir) / "dependencies.csv"))
    conflicts = load_csv(str(Path(args.m0_dir) / "conflicts.csv"))

    by_bucket = {}
    for row in b.get("rows") or []:
        by_bucket[normalize(row.get("identity"))] = row.get("bucket")

    by_file = {}
    by_identity = {}
    for entry in manifest.get("rows") or []:
        if entry.get("bucket") != "DOWNLOADED":
            continue
        by_file[str(entry.get("fileName") or "")] = entry
        by_identity[normalize(entry.get("identity"))] = entry

    cand_by_file = {str(c.get("sourceFile") or ""): c for c in candidates}

    rows = []
    per_table = {}
    for table_name, table_rows in (a.get("tables") or {}).items():
        per_table[table_name] = len(table_rows)
        for r in table_rows:
            ident = r.get("slug") or r.get("name")
            key = normalize(ident)
            row = {
                "table": table_name,
                "identity": ident,
                "normalized": key,
                "forbricVersion": r.get("version"),
                "forbricLoader": r.get("loader"),
                "bucket": None,
                "modId": None,
                "sourceFile": None,
                "signals": {},
                "signalCount": 0,
            }
            lane = by_bucket.get(key)
            if lane == "NO_262":
                row["bucket"] = "NO_262"
            elif lane == "NOT_FOUND":
                row["bucket"] = "NOT_FOUND"
            elif lane == "HTTP_ERROR":
                row["bucket"] = "DOWNLOAD_FAILED"
            else:
                entry = by_identity.get(key)
                if entry is None:
                    for fname, e in by_file.items():
                        if normalize(fname).startswith(key):
                            entry = e
                            break
                if entry is None:
                    row["bucket"] = "NOT_IN_BATCH"
                else:
                    cand = cand_by_file.get(str(entry.get("fileName")))
                    if cand is None:
                        row["bucket"] = "DOWNLOAD_FAILED"
                    else:
                        row["bucket"] = "LOCATED"
                        row["modId"] = cand.get("modId")
                        row["sourceFile"] = cand.get("sourceFile")
                        sig = {
                            "statusFailed": cand.get("status") == "FAILED",
                            "supportPolicyExcluded": cand.get("supportPolicy") == "EXCLUDED",
                            "mixinTargetsNonEmpty": bool(cand.get("mixinTargets")),
                            "dependenciesNonSatisfied": any(
                                d.get("modId") == cand.get("modId") and d.get("outcome") != "SATISFIED"
                                for d in deps
                            ),
                            "conflictsHighMedium": any(
                                (x.get("modA") == cand.get("modId") or x.get("modB") == cand.get("modId"))
                                and x.get("level") in ("HIGH", "MEDIUM")
                                for x in conflicts
                            ),
                            "loaderMismatch": normalize(cand.get("loaderFamily")) != normalize(row["forbricLoader"]),
                        }
                        row["signals"] = sig
                        row["signalCount"] = sum(1 for v in sig.values() if v)
            rows.append(row)

    counts = {bkt: sum(1 for r in rows if r["bucket"] == bkt) for bkt in BUCKETS}
    located = [r for r in rows if r["bucket"] == "LOCATED"]
    field_totals = {
        k: sum(1 for r in located if r["signals"].get(k))
        for k in (
            "statusFailed",
            "supportPolicyExcluded",
            "dependenciesNonSatisfied",
            "mixinTargetsNonEmpty",
            "conflictsHighMedium",
            "loaderMismatch",
        )
    }
    zero_signal = sorted(r["modId"] or r["identity"] for r in located if r["signalCount"] == 0)
    special = sorted(r["identity"] for r in rows if r["bucket"] in ("NO_262", "NOT_FOUND"))

    checks = {
        "H1_buckets_sum_to_rows": sum(counts.values()) == len(rows),
        "H2_rows_equal_two_tables": len(rows) == sum(per_table.values()),
        "H3_located_have_modid": all(r["modId"] for r in located),
        "H4_no_row_unbucketed": all(r["bucket"] in BUCKETS for r in rows),
    }
    out = {
        "sampleRows": len(rows),
        "perTable": per_table,
        "tablesMerged": False,
        "note": "两份表不合并；任何合计都是“两份清单合计”，不含 2026-10-11 复测。",
        "buckets": counts,
        "locatedFields": field_totals,
        "locatedZeroSignal": zero_signal,
        "no262AndNotFound": special,
        "assertions": checks,
        "rows": sorted(rows, key=lambda r: (r["table"], r["normalized"])),
    }
    try:
        Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    print("rows:", len(rows), "perTable:", per_table)
    print("buckets:", counts, "sum:", sum(counts.values()))
    print("fields:", json.dumps(field_totals))
    print("zero-signal:", len(zero_signal), zero_signal)
    print("no262/not_found:", special)
    print("assertions:", json.dumps(checks))
    print("wrote", args.out)
    if not all(checks.values()):
        print("FAIL: an assertion failed")
        return 1
    print("OK: assertions hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
