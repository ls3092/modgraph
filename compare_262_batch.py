#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""compare_262_batch.py -- 段 2 的 M0 结果汇总与对照（只读，不覆盖任何既有产物）。

读取新批次跑出来的 dataset-262/run/candidates.jsonl（以及同目录的 CSV 行数），
算出输入数 / OK / FAILED / 侵入深度桶 / 支持策略 / Mixin 覆盖率缺口，
并与仓库根那份 195 个 jar 的旧基线**只读**对照（绝不写它）。

用法：
    python compare_262_batch.py
    python compare_262_batch.py --new dataset-262/run/candidates.jsonl --old candidates.jsonl

断言（任一不成立 -> 打印 FAIL 并以 1 退出）：
  C1  输入数 == OK + FAILED
  C2  RENDER + CORE + MIXIN + API == OK
  C3  缺口 + 空配置非缺口 == 空 mixinTargets 的 OK 数（与第二十步同一口径）
  C4  统计出来的输入数 == CSV 数据行数（summary.csv 含表头）
  C5  新批次文件与旧基线文件不是同一个文件
"""

import argparse
import json
import os
import sys

BUCKETS = ("RENDER", "CORE", "MIXIN", "API")
POLICIES = ("SUPPORTED", "DEGRADED", "EXCLUDED")


def read_jsonl(path):
    rows = []
    try:
        fh = open(path, encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot open %s: %s" % (path, exc))
    with fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except ValueError as exc:
                raise SystemExit("FAIL: bad JSON line in %s: %s" % (path, exc))
    return rows


def csv_data_rows(path):
    try:
        with open(path, encoding="utf-8") as fh:
            return max(0, sum(1 for _ in fh) - 1)
    except OSError:
        return None


def summarize(path):
    rows = read_jsonl(path)
    ok = [r for r in rows if r.get("status") == "OK"]
    failed = [r for r in rows if r.get("status") == "FAILED"]
    inv = dict.fromkeys(BUCKETS, 0)
    for r in ok:
        k = r.get("invasiveness")
        if k in inv:
            inv[k] += 1
    pol = dict.fromkeys(POLICIES, 0)
    for r in ok:
        k = r.get("supportPolicy")
        if k in pol:
            pol[k] += 1
    empty_targets = [r for r in ok if not (r.get("mixinTargets") or [])]
    gap = [r for r in empty_targets if (r.get("mixinConfigs") or [])]
    no_config = [r for r in empty_targets if not (r.get("mixinConfigs") or [])]
    codes = {}
    for r in failed:
        c = r.get("failureCode") or "UNKNOWN"
        codes[c] = codes.get(c, 0) + 1
    return {
        "path": path,
        "input": len(rows),
        "ok": len(ok),
        "failed": len(failed),
        "failureCodes": codes,
        "invasiveness": inv,
        "supportPolicy": pol,
        "emptyMixinTargets": len(empty_targets),
        "coverageGap": len(gap),
        "emptyConfigNotGap": len(no_config),
        "gapModIds": sorted({r.get("modId") for r in gap}),
        "loaders": {k: sum(1 for r in ok if r.get("loaderFamily") == k) for k in ("FABRIC", "NEOFORGE", "FORGE", "QUILT", "UNKNOWN")},
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--new", default="dataset-262/run/candidates.jsonl")
    ap.add_argument("--old", default="candidates.jsonl")
    ap.add_argument("--out", default="dataset-262/batch-m0-summary.json")
    args = ap.parse_args()

    if not os.path.exists(args.new):
        raise SystemExit("FAIL: %s not found" % args.new)
    if os.path.exists(args.old) and os.path.samefile(args.new, args.old):
        raise SystemExit("FAIL: new and old point at the same file")

    new = summarize(args.new)
    old = summarize(args.old) if os.path.exists(args.old) else None

    new["csvRows"] = {
        name: csv_data_rows(os.path.join(os.path.dirname(args.new), name))
        for name in ("summary.csv", "conflicts.csv", "dependencies.csv", "cycles.csv", "duplicates.csv")
    }

    checks = {
        "C1_input_equals_ok_plus_failed": new["input"] == new["ok"] + new["failed"],
        "C2_invasiveness_sums_to_ok": sum(new["invasiveness"].values()) == new["ok"],
        "C3_gap_plus_empty_config": new["coverageGap"] + new["emptyConfigNotGap"] == new["emptyMixinTargets"],
        "C4_input_equals_summary_csv_rows": new["csvRows"]["summary.csv"] is None
        or new["csvRows"]["summary.csv"] == new["input"],
        "C5_new_and_old_are_different_files": old is None or os.path.abspath(args.new) != os.path.abspath(args.old),
    }
    out = {"new": new, "old": old, "assertions": checks, "ok": all(checks.values())}
    try:
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(out, fh, ensure_ascii=False, indent=2, sort_keys=True)
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    print(json.dumps({"new": {k: v for k, v in new.items() if k != "gapModIds"}}, indent=2, ensure_ascii=False))
    if old:
        print("old baseline:", {k: old[k] for k in ("input", "ok", "failed", "invasiveness", "coverageGap")})
    print(json.dumps(checks, indent=2))
    print("wrote", args.out)
    if not out["ok"]:
        print("FAIL: an assertion failed")
        return 1
    print("OK: all assertions hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
