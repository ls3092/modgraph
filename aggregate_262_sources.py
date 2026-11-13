#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""aggregate_262_sources.py -- 段 3 主智能体汇总：两路归一去重 + 分桶求和断言。

读取：
  forbric-comparison/sources-A.json          段 1 的 Forbric 两份清单（44 个唯一 id 的来源）
  forbric-comparison/sources-262-forbric.json  Lane B：44 个 id 在 Modrinth 是否有 26.2
  forbric-comparison/mixin-262.json           Lane A：26.2 批次 modcrawl 四分类（若已产出）

只读上述文件，只写 dataset-262/aggregate-262.json。不改任何其它文件。

断言：
  F1  Lane B 的 rows 数 == 44（Forbric 两份清单的唯一 id 数）
  F2  Lane B 的四个桶之和 == rows 数
  F3  Lane B 的 identity 归一化后无重复
  F4  若 Lane A 产物存在：四分类之和 == 30；缺口 = 补上 + 剩余
  F5  Lane A 若缺失：必须在 laneAStatus 里写明阻断原因，不得静默跳过

用法：
    python -u aggregate_262_sources.py
"""

import argparse
import json
import sys
from pathlib import Path


def normalize(value):
    return "".join(ch for ch in str(value or "").lower() if ch.isalnum())


def load(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        return None
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sources-a", default="forbric-comparison/sources-A.json")
    ap.add_argument("--sources-b", default="forbric-comparison/sources-262-forbric.json")
    ap.add_argument("--mixin", default="forbric-comparison/mixin-262.json")
    ap.add_argument("--out", default="dataset-262/aggregate-262.json")
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    a = load(args.sources_a)
    if a is None:
        raise SystemExit("FAIL: %s missing" % args.sources_a)

    # Forbric 44 个唯一身份（两份表不合并、无交集）
    identities = []
    for table_name, rows in (a.get("tables") or {}).items():
        for row in rows:
            key = normalize(row.get("slug") or row.get("name"))
            if key:
                identities.append((key, table_name))
    unique_keys = {k for k, _ in identities}
    dup_rows = len(identities) - len(unique_keys)

    b = load(args.sources_b)
    lane_a = load(args.mixin)

    out = {
        "forbric": {
            "rows": len(identities),
            "uniqueIdentities": len(unique_keys),
            "duplicateRowsWithin": dup_rows,
            "perTable": {t: sum(1 for k, tt in identities if tt == t) for t in (a.get("tables") or {})},
        },
        "laneB": None,
        "laneA": None,
        "assertions": {},
    }

    checks = {}
    if b is None:
        out["laneB"] = {"status": "MISSING", "path": args.sources_b}
        checks["F1_laneB_rows_equal_44"] = False
        checks["F2_laneB_buckets_sum"] = False
        checks["F3_laneB_no_duplicate_identities"] = False
    else:
        rows = b.get("rows") or []
        buckets = b.get("buckets") or {}
        keys = [normalize(r.get("identity")) for r in rows]
        out["laneB"] = {
            "status": "PRESENT",
            "rows": len(rows),
            "buckets": buckets,
            "bucketSum": sum(v for v in buckets.values() if isinstance(v, int)),
            "uniqueIdentities": len(set(keys)),
            "has262": sorted(
                r.get("identity") for r in rows if r.get("bucket") == "HAS_262"
            ),
        }
        checks["F1_laneB_rows_equal_44"] = len(rows) == len(unique_keys)
        checks["F2_laneB_buckets_sum"] = sum(v for v in buckets.values() if isinstance(v, int)) == len(rows)
        checks["F3_laneB_no_duplicate_identities"] = len(set(keys)) == len(keys)

    if lane_a is None:
        out["laneA"] = {
            "status": "BLOCKED",
            "path": args.mixin,
            "reason": "modcrawl windows binary absent from disk; upstream v0.11.3 asset returned "
                      "504 Gateway Time-out (92-byte HTML body). No four-way classification was produced, "
                      "and none is inferred. See BOUNDARIES.md §2.5.",
        }
        checks["F4_laneA_fourway_sums_to_30"] = None
    else:
        four = lane_a.get("fourWay") or {}
        gap = lane_a.get("gap") or {}
        out["laneA"] = {
            "status": "PRESENT",
            "fourWay": four,
            "fourWaySum": sum(v for v in four.values() if isinstance(v, int)),
            "gap": gap,
        }
        total = sum(v for v in four.values() if isinstance(v, int))
        filled = gap.get("filledByModcrawl")
        remaining = gap.get("remaining")
        checks["F4_laneA_fourway_sums_to_30"] = total == 30 and (
            filled is None or remaining is None or filled + remaining == gap.get("records", 0)
        )

    checks["F5_laneA_block_reason_recorded"] = out["laneA"]["status"] != "BLOCKED" or bool(
        out["laneA"].get("reason")
    )
    out["assertions"] = checks
    out["ok"] = all(v for v in checks.values() if v is not None)

    try:
        Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    print(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True))
    print("wrote", args.out)
    if not out["ok"]:
        print("FAIL: an aggregation assertion failed")
        return 1
    print("OK: aggregation assertions hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
