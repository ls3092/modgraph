#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""crosscheck_baseline_262.py -- 主智能体对 Lane B 的独立复算（只读）。

两组、同一口径：
  基线组 = dataset-262/ 的 30 个 26.2 模组（无运行时标签）
  失败组 = dataset-262/forbric/ 中，段 4 判为 LOCATED 的 39 行所对应的 modId

六项字段（按 modId，非互斥）：
  dependenciesNonSatisfied / mixinTargetsNonEmpty / conflictsHighMedium /
  loaderMismatch / supportPolicyExcluded / statusFailed

只写 dataset-262/crosscheck-baseline-262.json，用于与 Lane B 的 baseline-262.json 对账。
"""

import argparse
import csv
import json
import sys
from pathlib import Path


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
    except OSError as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))


def stats(base_dir, cands, deps, conflicts, only_modids=None, loader_map=None):
    ok = [c for c in cands if c.get("status") == "OK"]
    if only_modids is not None:
        ok = [c for c in ok if c.get("modId") in only_modids]
    modids = {c.get("modId") for c in ok if c.get("modId")}
    bad_dep = {d["modId"] for d in deps if d.get("outcome") not in (None, "SATISFIED")}
    hi = set()
    for x in conflicts:
        if x.get("level") in ("HIGH", "MEDIUM"):
            hi.add(x.get("modA"))
            hi.add(x.get("modB"))
    empty_targets = [c for c in ok if not (c.get("mixinTargets") or [])]
    gap = [c for c in empty_targets if (c.get("mixinConfigs") or [])]
    return {
        "records": len(ok),
        "uniqueModIds": len(modids),
        "dependenciesNonSatisfied": len(modids & bad_dep),
        "mixinTargetsNonEmpty": sum(1 for c in ok if c.get("mixinTargets")),
        "conflictsHighMedium": len(modids & hi),
        "supportPolicyExcluded": sum(1 for c in ok if c.get("supportPolicy") == "EXCLUDED"),
        "statusFailed": sum(1 for c in ok if c.get("status") == "FAILED"),
        "emptyMixinTargets": len(empty_targets),
        "coverageGap": len(gap),
        "conflictRows": len(conflicts),
        "pairsFromUnique": len(modids) * (len(modids) - 1) // 2,
        "loaderMismatch": (
            sum(1 for c in ok if c.get("loaderFamily") != loader_map.get(c.get("modId"))) if loader_map else None
        ),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="dataset-262/crosscheck-baseline-262.json")
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    base = {"c": load_jsonl("dataset-262/candidates.jsonl"),
            "d": load_csv("dataset-262/dependencies.csv"),
            "x": load_csv("dataset-262/conflicts.csv")}
    fail = {"c": load_jsonl("dataset-262/forbric/candidates.jsonl"),
            "d": load_csv("dataset-262/forbric/dependencies.csv"),
            "x": load_csv("dataset-262/forbric/conflicts.csv")}

    try:
        comp = json.loads(Path("dataset-262/forbric/comparison-262.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read comparison-262.json: %s" % exc)
    located = [r for r in comp.get("rows") or [] if r.get("bucket") == "LOCATED" and r.get("modId")]
    located_ids = {r["modId"] for r in located}
    loader_map = {r["modId"]: r.get("forbricLoader") for r in located}

    b = stats("dataset-262", base["c"], base["d"], base["x"])
    f = stats("dataset-262/forbric", fail["c"], fail["d"], fail["x"], only_modids=located_ids, loader_map=loader_map)

    checks = {
        "L1_baseline_denominator_30": b["records"] == 30,
        "L2_failure_denominator_39_rows": len(located) == 39,
        "L3_conflict_rows_equal_pairs": b["conflictRows"] == b["pairsFromUnique"] and f["conflictRows"] == f["pairsFromUnique"],
        "L4_counts_within_denominators": all(
            v <= b["records"] for k, v in b.items() if k in ("dependenciesNonSatisfied", "mixinTargetsNonEmpty", "conflictsHighMedium", "supportPolicyExcluded", "statusFailed")
        ) and all(
            v <= f["records"] for k, v in f.items() if k in ("dependenciesNonSatisfied", "mixinTargetsNonEmpty", "conflictsHighMedium", "supportPolicyExcluded", "statusFailed")
        ),
    }
    out = {"baseline": b, "failure": f, "failureRows": len(located), "assertions": checks, "ok": all(checks.values())}
    try:
        Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    print(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True))
    print("wrote", args.out)
    if not out["ok"]:
        print("FAIL")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
