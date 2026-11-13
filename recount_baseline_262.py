#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""recount_baseline_262.py -- 按“唯一 modId”重算六项，供对账用（只读）。

存在的理由：早先 crosscheck 里三项（mixinTargetsNonEmpty / supportPolicyExcluded /
statusFailed）其实是按**记录**数的，而另两项按**唯一 modId** 数；失败组 36 条记录
和 35 个唯一 modId 不等，两个分母混在一张表里会误导读者的数。本脚本统一成唯一
modId，并把 record 口径一并打印，便于对照。
"""

import collections
import csv
import json
import sys
from pathlib import Path


def jsonl(path):
    out = []
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: %s" % exc)
    for line in text.splitlines():
        if line.strip():
            try:
                out.append(json.loads(line))
            except ValueError as exc:
                raise SystemExit("FAIL: bad JSON line in %s: %s" % (path, exc))
    return out


def rows_csv(path):
    try:
        with open(path, encoding="utf-8", newline="") as fh:
            return list(csv.DictReader(fh))
    except OSError as exc:
        raise SystemExit("FAIL: %s" % exc)


def compute(cands, deps, conflicts, loader_map=None):
    ok = [c for c in cands if c.get("status") == "OK"]
    by_mod = collections.OrderedDict()
    for c in ok:
        by_mod.setdefault(c.get("modId"), []).append(c)
    bad_dep = {d["modId"] for d in deps if d.get("outcome") not in (None, "SATISFIED")}
    hi = set()
    for x in conflicts:
        if x.get("level") in ("HIGH", "MEDIUM"):
            hi.add(x.get("modA"))
            hi.add(x.get("modB"))
    mismatch_nonnull = 0
    null_label = 0
    if loader_map is not None:
        for mod_id, recs in by_mod.items():
            label = loader_map.get(mod_id)
            if not label:
                null_label += 1
                continue
            if any(r.get("loaderFamily") != label for r in recs):
                mismatch_nonnull += 1
    return {
        "records": len(ok),
        "uniqueModIds": len(by_mod),
        "dependenciesNonSatisfied": len(set(by_mod) & bad_dep),
        "mixinTargetsNonEmpty_modId": sum(1 for m, rs in by_mod.items() if any(r.get("mixinTargets") for r in rs)),
        "mixinTargetsNonEmpty_record": sum(1 for c in ok if c.get("mixinTargets")),
        "conflictsHighMedium": len(set(by_mod) & hi),
        "supportPolicyExcluded_modId": sum(
            1 for m, rs in by_mod.items() if any(r.get("supportPolicy") == "EXCLUDED" for r in rs)
        ),
        "supportPolicyExcluded_record": sum(1 for c in ok if c.get("supportPolicy") == "EXCLUDED"),
        "statusFailed_modId": sum(1 for m, rs in by_mod.items() if any(r.get("status") == "FAILED" for r in rs)),
        "statusFailed_record": sum(1 for c in ok if c.get("status") == "FAILED"),
        "loaderMismatch_modId_nonnull": mismatch_nonnull if loader_map is not None else None,
        "loaderNullLabel_modId": null_label if loader_map is not None else None,
        "conflictRows": len(conflicts),
        "pairsFromUnique": len(by_mod) * (len(by_mod) - 1) // 2,
    }


def main():
    try:
        comp = json.loads(Path("dataset-262/forbric/comparison-262.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: %s" % exc)
    located = [r for r in comp["rows"] if r.get("bucket") == "LOCATED"]
    loader_map = {}
    for r in located:
        if r.get("modId"):
            loader_map[r["modId"]] = r.get("forbricLoader")
    located_ids = set(loader_map)

    base = compute(
        jsonl("dataset-262/candidates.jsonl"),
        rows_csv("dataset-262/dependencies.csv"),
        rows_csv("dataset-262/conflicts.csv"),
    )
    fail_all = compute(
        jsonl("dataset-262/forbric/candidates.jsonl"),
        rows_csv("dataset-262/forbric/dependencies.csv"),
        rows_csv("dataset-262/forbric/conflicts.csv"),
        loader_map=loader_map,
    )

    out = {
        "baseline": base,
        "failure": fail_all,
        "failureRowsLocated": len(located),
        "note": "唯一 modId 口径用于表格；record 口径保留供核对。",
    }
    print(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
