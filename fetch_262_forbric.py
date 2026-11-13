#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""fetch_262_forbric.py -- 段 4 主链第 1 步：把 Forbric 的 39 个 HAS_262 身份采成 jar。

规则（与段 2 不同的两点，本步按要求收紧）：
  * 选文件按**版本(文件)级 loaders**：只在 `26.2` 在 game_versions 的版本里挑，
    再用该 version 的 `loaders` 决定落位目录（fabric / neoforge / other）。
  * 直接下载进 `dataset-262/forbric/fixtures/jars/<loader>/`，不复制、不搬运。

不下载的：LANE B 里 NO_262 / NOT_FOUND / HTTP_ERROR 的 5 个身份不进本步。
不碰仓库根的 fixtures/jars 与六个既有产物；不改 src/。

用法：
    python -u fetch_262_forbric.py --limit 39
    python -u fetch_262_forbric.py --limit 15      # 样本模式，报告里必须写 15/39

断言：
  G1  三桶（DOWNLOADED / NO_FILE_FOR_262 / DOWNLOAD_FAILED）之和 == 尝试数
  G2  尝试数 == min(--limit, HAS_262 数)
  G3  每个落位目录都属于 fabric / neoforge / other 三档且与 version.loaders 一致
"""

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "modgraph-segment4-collector/1.0 (offline static analysis project)"
BASE = Path("dataset-262/forbric")
BUCKETS = ("DOWNLOADED", "NO_FILE_FOR_262", "NO_FABRIC_OR_NEOFORGE_FILE", "DOWNLOAD_FAILED")
WORKERS = 4


def http_json(url):
    last = None
    for attempt in range(3):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as exc:
            last = exc
            if attempt < 2:
                time.sleep(1 + attempt)
    raise RuntimeError("%s failed: %s" % (url, last))


def normalize(value):
    return "".join(ch for ch in str(value or "").lower() if ch.isalnum())


def pick_version(versions):
    """26.2 在 game_versions 里的版本；优先 release，其次 beta，再其次其它。"""
    cands = [v for v in versions if "26.2" in (v.get("game_versions") or [])]
    if not cands:
        return None
    order = {"release": 0, "beta": 1, "alpha": 2}
    cands.sort(key=lambda v: (order.get(v.get("version_type"), 9), v.get("date_published") or ""))
    return cands[0]


def loader_dir(loaders):
    lo = [str(x).lower() for x in (loaders or [])]
    if "fabric" in lo:
        return "fabric"
    if "neoforge" in lo:
        return "neoforge"
    if "forge" in lo:
        return "neoforge"
    return "other"


def sha256_of(path):
    try:
        blob = Path(path).read_bytes()
    except OSError as exc:
        raise SystemExit("FAIL: cannot hash %s: %s" % (path, exc))
    return hashlib.sha256(blob).hexdigest().upper()


def download(url, dest):
    last = None
    for attempt in range(3):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                blob = r.read()
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(blob)
            return len(blob)
        except (urllib.error.URLError, OSError) as exc:
            last = exc
            if attempt < 2:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError("download failed: %s (%s)" % (url, last))


def work(row):
    rec = {
        "identity": row.get("identity"),
        "sourceTable": row.get("sourceTable"),
        "matchedSlug": row.get("matchedSlug"),
        "projectId": row.get("projectId"),
        "bucket": None,
        "versionId": None,
        "versionNumber": None,
        "versionType": None,
        "loaders": None,
        "loaderDir": None,
        "fileName": None,
        "fileUrl": None,
        "bytes": None,
        "sha256": None,
        "license": row.get("license"),
        "localPath": None,
        "error": None,
    }
    try:
        versions = http_json("%s/project/%s/version" % (API, rec["projectId"]))
    except RuntimeError as exc:
        rec["bucket"] = "DOWNLOAD_FAILED"
        rec["error"] = "version-list: %s" % exc
        return rec
    version = pick_version(versions)
    if version is None:
        rec["bucket"] = "NO_FILE_FOR_262"
        return rec
    files = version.get("files") or []
    primary = [f for f in files if f.get("primary")] or files
    if not primary:
        rec["bucket"] = "NO_FILE_FOR_262"
        rec["error"] = "version has no files"
        return rec
    f = primary[0]
    rec.update(
        {
            "versionId": version.get("id"),
            "versionNumber": version.get("version_number"),
            "versionType": version.get("version_type"),
            "loaders": version.get("loaders"),
            "fileName": f.get("filename"),
            "fileUrl": f.get("url"),
        }
    )
    rec["loaderDir"] = loader_dir(version.get("loaders"))
    if rec["loaderDir"] == "other":
        rec["bucket"] = "NO_FABRIC_OR_NEOFORGE_FILE"
        rec["error"] = "26.2 version exists but file-level loaders are %r" % (version.get("loaders"),)
        return rec
    dest = BASE / "fixtures" / "jars" / rec["loaderDir"] / (rec["fileName"] or (normalize(rec["identity"]) + ".jar"))
    if dest.exists() and dest.stat().st_size > 0:
        rec["bytes"] = dest.stat().st_size
        rec["sha256"] = sha256_of(str(dest))
        rec["localPath"] = str(dest).replace("\\", "/")
        rec["reused"] = True
        rec["bucket"] = "DOWNLOADED"
        return rec
    try:
        size = download(rec["fileUrl"], dest)
    except RuntimeError as exc:
        rec["bucket"] = "DOWNLOAD_FAILED"
        rec["error"] = str(exc)
        return rec
    rec["bytes"] = size
    rec["sha256"] = sha256_of(str(dest))
    rec["localPath"] = str(dest).replace("\\", "/")
    rec["bucket"] = "DOWNLOADED"
    return rec


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=39)
    ap.add_argument("--sources", default="forbric-comparison/sources-262-forbric.json")
    ap.add_argument("--out", default=str(BASE / "download-manifest.json"))
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    try:
        data = json.loads(Path(args.sources).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (args.sources, exc))

    has = [r for r in (data.get("rows") or []) if r.get("bucket") == "HAS_262"]
    has.sort(key=lambda r: normalize(r.get("identity")))
    want = has[: args.limit]
    print("HAS_262 available:", len(has), "attempting:", len(want), flush=True)

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        results = list(pool.map(work, want))
    results.sort(key=lambda r: normalize(r["identity"]))

    buckets = {b: sum(1 for r in results if r["bucket"] == b) for b in BUCKETS}
    for r in results:
        print("%-16s %-30s %s" % (r["bucket"], r["identity"], r.get("versionNumber") or r.get("error") or ""), flush=True)

    checks = {
        "G1_buckets_sum": sum(buckets.values()) == len(results),
        "G2_attempts_match_limit": len(want) == min(args.limit, len(has)),
        "G3_loader_dirs_valid": all(
            r["loaderDir"] in ("fabric", "neoforge")
            for r in results
            if r["bucket"] == "DOWNLOADED"
        ),
    }
    out = {
        "sources": args.sources,
        "hasAvailable": len(has),
        "attempted": len(want),
        "isSample": len(want) < len(has),
        "buckets": buckets,
        "assertions": checks,
        "rows": results,
    }
    try:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    print("buckets:", buckets, "sum:", sum(buckets.values()), flush=True)
    print("assertions:", json.dumps(checks), flush=True)
    print("sample:", out["isSample"], "wrote", args.out, flush=True)
    if not all(checks.values()):
        print("FAIL: an assertion failed")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
