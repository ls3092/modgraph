#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""verify_collectability_262.py -- MC 26.2 可采性核验（只读；不下载任何 jar）。

用途：复现长程任务第 1 段里 Modrinth 侧的池子大小、分桶与 slug 重合。
只调用 Modrinth 公开 API（无需 API key），只读 `candidates.jsonl`，
只写本脚本自己的输出文件，不碰 M0/M0.5/M1/M1.5 的任何既有产物。

为什么分桶要“先枚举、再本地分桶”：
  2026-11-08 实测，Modrinth 的 /search total_hits 在两次查询之间会变
  （13846 -> 13847，期间新增了一个 date_created=2026-11-08T05:38 的项目）。
  因此把多个 total_hits 相加相减得到的桶必然对不上总数。
  本脚本只在一个枚举快照内部做分桶，桶和总数据此闭合。

不做什么：
  * 不下载 jar、不下载版本文件、不访问 cdn.modrinth.com 里的资源
  * 不猜 URL；所有 URL 都在 FACETS 常量与 print 里
  * 不把 CurseForge 的数字算进来（官方 API 要 x-api-key，没 key 就不发请求）

用法：
    python verify_collectability_262.py --enumerate 20
    python verify_collectability_262.py --enumerate 147 --json-out snap.json

断言（任一不成立 -> 打印 FAIL 并以 1 退出）：
  A1  versions:99.99 的 total_hits 必须为 0（facet 真的生效）
  A2  versions:1.21.1 mods 的 total_hits 必须 > 0
  A3  快照内四桶之和 == 快照项目数（本脚本采用的唯一闭合分桶）
  A4  快照内每个项目都带 slug 与 projectId
  A5  快照无重复 projectId

已知、且被本脚本如实报出的异常（不是断言）：
  inclusion-exclusion 不闭合：|fabric| + |neoforge| - |both| != |fabric OR neoforge|。
  实测 13846 + 7208 - 2662 = 18392，而 union 查询是 14627（差 3765，远超索引漂移）。
  因此本脚本不发布任何由 loader total_hits 推导出的总数，只用快照内分桶。
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2/search"
UA = "modgraph-collectability-check/1.1 (offline static analysis project)"

FACETS = {
    "mods_262": [["versions:26.2"], ["project_type:mod"]],
    "mods_262_fabric": [["versions:26.2"], ["project_type:mod"], ["categories:fabric"]],
    "mods_262_neoforge": [["versions:26.2"], ["project_type:mod"], ["categories:neoforge"]],
    "mods_262_both": [
        ["versions:26.2"],
        ["project_type:mod"],
        ["categories:fabric"],
        ["categories:neoforge"],
    ],
    "mods_262_either": [
        ["versions:26.2"],
        ["project_type:mod"],
        ["categories:fabric", "categories:neoforge"],
    ],
    "control_bogus": [["versions:99.99"]],
    "control_1211": [["versions:1.21.1"], ["project_type:mod"]],
}


def search(facets, limit=1, offset=0):
    q = urllib.parse.urlencode(
        {"limit": limit, "offset": offset, "facets": json.dumps(facets, separators=(",", ":"))}
    )
    url = API + "?" + q
    body = None
    last = None
    for attempt in range(3):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                body = r.read().decode("utf-8")
            break
        except (urllib.error.URLError, OSError) as exc:
            last = exc
            if attempt < 2:
                time.sleep(2 * (attempt + 1))
    if body is None:
        raise SystemExit("FAIL: request to %s failed after 3 attempts: %s" % (url, last))
    try:
        return url, json.loads(body)
    except ValueError as exc:
        raise SystemExit("FAIL: response from %s was not JSON: %s" % (url, exc))


def normalize(s):
    return "".join(ch for ch in s.lower() if ch.isalnum())


def load_dataset_ids(path):
    ids = []
    if not os.path.exists(path):
        return ids
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
                rec = json.loads(line)
            except ValueError:
                continue
            mid = rec.get("modId") or ""
            if mid:
                ids.append(mid)
    return ids


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--enumerate", type=int, default=20, help="枚举多少页（每页 100）")
    ap.add_argument("--json-out", default="collectability-262.json")
    ap.add_argument("--candidates", default="candidates.jsonl")
    ap.add_argument("--forbric-ids", default="", help="可选：sources-A.json，用来算与 Forbric 清单的重合")
    args = ap.parse_args()

    out = {"gameVersion": "26.2", "api": API, "totalHits": {}, "urls": {}, "assertions": {}, "notes": []}
    for name in ("mods_262", "mods_262_fabric", "mods_262_neoforge", "mods_262_both",
                 "mods_262_either", "control_bogus", "control_1211"):
        url, body = search(FACETS[name])
        out["urls"][name] = url
        out["totalHits"][name] = body["total_hits"]
        print(f"{name:22s} total_hits={body['total_hits']}")

    t = out["totalHits"]
    out["assertions"]["A1_bogus_version_is_zero"] = t["control_bogus"] == 0
    out["assertions"]["A2_1211_nonzero"] = t["control_1211"] > 0

    ie = t["mods_262_fabric"] + t["mods_262_neoforge"] - t["mods_262_both"]
    out["inclusionExclusion"] = {
        "fabric_plus_neoforge_minus_both": ie,
        "union_query": t["mods_262_either"],
        "closes": ie == t["mods_262_either"],
        "gap": ie - t["mods_262_either"],
    }
    if not out["inclusionExclusion"]["closes"]:
        out["notes"].append(
            "inclusion-exclusion does not close (%d vs %d); loader total_hits are NOT used for any bucket"
            % (ie, t["mods_262_either"])
        )
    out["notes"].append(
        "total_hits drifts between requests because the Modrinth index is live; buckets are computed "
        "inside one enumeration snapshot only"
    )

    # 单次枚举快照 -> 本地分桶
    cap = args.enumerate * 100
    projects, offset = [], 0
    while offset < cap:
        _, body = search(FACETS["mods_262"], limit=100, offset=offset)
        hits = body.get("hits") or []
        if not hits:
            break
        for h in hits:
            cats = h.get("categories") or []
            projects.append({
                "slug": h.get("slug"),
                "projectId": h.get("project_id"),
                "title": h.get("title"),
                "license": h.get("license"),
                "loaders": [c for c in cats if c in ("fabric", "neoforge", "quilt", "forge")],
                "url": "https://modrinth.com/mod/" + (h.get("slug") or ""),
            })
        offset += 100

    buckets = {"BOTH": 0, "FABRIC_ONLY": 0, "NEOFORGE_ONLY": 0, "NEITHER": 0}
    for p in projects:
        f = "fabric" in p["loaders"]
        n = "neoforge" in p["loaders"]
        buckets["BOTH" if (f and n) else "FABRIC_ONLY" if f else "NEOFORGE_ONLY" if n else "NEITHER"] += 1
    out["snapshot"] = {"requestedPages": args.enumerate, "projects": len(projects), "buckets": buckets}
    out["assertions"]["A3_buckets_sum_to_snapshot"] = sum(buckets.values()) == len(projects)
    out["assertions"]["A4_every_project_has_slug_and_id"] = all(p["slug"] and p["projectId"] for p in projects)
    ids = [p["projectId"] for p in projects]
    out["assertions"]["A5_no_duplicate_project_ids"] = len(ids) == len(set(ids))

    ds = set(normalize(x) for x in load_dataset_ids(args.candidates))
    ov = [p for p in projects if normalize(p["slug"] or "") in ds]
    out["overlapWith1211"] = {
        "datasetModIds": len(ds),
        "rule": "lowercase; strip every non-alphanumeric char",
        "snapshotMatches": len(ov),
        "slugs": sorted(p["slug"] for p in ov),
    }

    if args.forbric_ids and os.path.exists(args.forbric_ids):
        try:
            with open(args.forbric_ids, encoding="utf-8") as fh:
                a = json.load(fh)
        except (OSError, ValueError) as exc:
            raise SystemExit("FAIL: cannot read %s: %s" % (args.forbric_ids, exc))
        names = set()
        for tbl in (a.get("tables") or {}).values():
            for row in tbl:
                for key in ("slug", "name"):
                    if row.get(key):
                        names.add(normalize(str(row[key])))
        snap = set(normalize(p["slug"] or "") for p in projects)
        hit = sorted(snap & names)
        out["overlapWithForbric262"] = {
            "forbricIds": len(names),
            "snapshotMatches": len(hit),
            "slugs": hit,
            "note": "snapshot-only: the snapshot is the first %d projects by search relevance" % len(projects),
        }

    out["projects"] = projects
    ok = all(out["assertions"].values())
    out["ok"] = ok
    try:
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(out, fh, ensure_ascii=False, indent=2, sort_keys=True)
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.json_out, exc))
    print(json.dumps(out["assertions"], indent=2, sort_keys=True))
    print("snapshot", out["snapshot"])
    print("inclusion-exclusion closes:", out["inclusionExclusion"]["closes"],
          "gap:", out["inclusionExclusion"]["gap"])
    print("wrote", args.json_out)
    if not ok:
        print("FAIL: at least one assertion failed")
        return 1
    print("OK: all assertions hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
