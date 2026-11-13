#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""aggregate_262_final.py -- 段 5 主智能体汇总：两路归一去重 + 分桶求和断言。

读取（只读）：
  forbric-comparison/mixin-262.json      Lane A：36 个 26.2 jar 的四分类与缺口
  forbric-comparison/baseline-262.json   Lane B：可采模组基线 vs 失败组的字段分布
  dataset-262/forbric/comparison-262.json 段 4 的 47 行分桶
  dataset-262/batch-m0-summary.json     段 2 的 30 个（C1–C6 已在那边断言）
  dataset-262/forbric/batch-m0-summary.json 段 4 的 36 个

只写 dataset-262/aggregate-262-final.json。

断言：
  J1  Lane A 四分类之和 == 36
  J2  Lane A 缺口 = 补上 + 剩余，且原因桶之和 == 剩余
  J3  Lane B 每个字段计数 <= 对应分母（基线 30 / 失败组 39）
  J4  Lane B 的 conflicts 行数 == C(唯一 modId, 2)（两侧）
  J5  段 4 的 47 行分桶之和 == 47，且没有未分桶行
  J6  两路输出是不同文件（没有互踩）
"""

import argparse
import json
import sys
from pathlib import Path


def load(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        return None
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mixin", default="forbric-comparison/mixin-262.json")
    ap.add_argument("--baseline", default="forbric-comparison/baseline-262.json")
    ap.add_argument("--comparison", default="dataset-262/forbric/comparison-262.json")
    ap.add_argument("--out", default="dataset-262/aggregate-262-final.json")
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    a = load(args.mixin)
    b = load(args.baseline)
    comp = load(args.comparison)

    checks = {}
    info = {}

    if a is None:
        raise SystemExit("FAIL: %s missing" % args.mixin)
    four = a.get("fourWay") or {}
    total = sum(v for v in four.values() if isinstance(v, int))
    info["laneA"] = {"fourWay": four, "sum": total, "gap": a.get("gap")}
    checks["J1_fourway_sums_to_36"] = total == 36

    gap = a.get("gap") or {}
    reasons = gap.get("reasonBuckets") or {}
    reason_sum = sum(v for v in reasons.values() if isinstance(v, int))
    filled = gap.get("filledByModcrawl")
    remaining = gap.get("remaining")
    records = gap.get("records")
    gap_ok = (
        isinstance(filled, int)
        and isinstance(remaining, int)
        and isinstance(records, int)
        and filled + remaining == records
        and reason_sum == remaining
    )
    info["laneA_gap"] = {"records": records, "filled": filled, "remaining": remaining, "reasonSum": reason_sum}
    checks["J2_gap_splits"] = bool(gap_ok)

    if b is None:
        info["laneB"] = {"status": "MISSING"}
        checks["J3_fields_within_denominators"] = False
        checks["J4_conflicts_equal_pairs"] = False
    else:
        fields = b.get("fields") or {}
        base_n = (b.get("baselineGroup") or {}).get("records")
        fail_n = (b.get("failureGroup") or {}).get("rows")
        within = True
        for name, pair in fields.items():
            if pair.get("baseline") is not None and base_n is not None and pair["baseline"] > base_n:
                within = False
            if pair.get("failure") is not None and fail_n is not None and pair["failure"] > fail_n:
                within = False
        info["laneB"] = {
            "baselineRecords": base_n,
            "failureRows": fail_n,
            "fields": fields,
            "conflictRowMath": b.get("conflictRowMath"),
        }
        checks["J3_fields_within_denominators"] = within

        math = b.get("conflictRowMath") or {}
        pairs_ok = True
        for side in ("baseline", "failure"):
            side_math = math.get(side) or {}
            u = side_math.get("uniqueModIds")
            rows = side_math.get("conflictRows")
            if isinstance(u, int) and isinstance(rows, int):
                if rows != u * (u - 1) // 2:
                    pairs_ok = False
            else:
                pairs_ok = False
        checks["J4_conflicts_equal_pairs"] = pairs_ok

    if comp is None:
        checks["J5_comparison_buckets_sum"] = False
    else:
        buckets = comp.get("buckets") or {}
        s = sum(v for v in buckets.values() if isinstance(v, int))
        rows = comp.get("rows") or []
        checks["J5_comparison_buckets_sum"] = s == len(rows) and len(rows) == 47
        info["comparison"] = {"buckets": buckets, "rows": len(rows)}

    checks["J6_lanes_are_distinct_files"] = args.mixin != args.baseline

    out = {"laneA": info.get("laneA"), "laneAGap": info.get("laneA_gap"), "laneB": info.get("laneB"),
           "comparison": info.get("comparison"), "assertions": checks,
           "ok": all(checks.values())}
    try:
        Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    print(json.dumps(out["assertions"], indent=2))
    print("laneA fourWay:", four, "sum", total)
    print("gap:", info.get("laneA_gap"))
    if info.get("laneB", {}).get("fields"):
        print("fields:", json.dumps(info["laneB"]["fields"], ensure_ascii=False))
    print("wrote", args.out)
    if not out["ok"]:
        print("FAIL: an aggregation assertion failed")
        return 1
    print("OK: all aggregation assertions hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
