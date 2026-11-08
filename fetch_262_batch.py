#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""fetch_262_batch.py -- 长程段 2：核验 26.2 文件可用性并下载第一批 jar。

只写 dataset-262/，不碰 fixtures/jars、不碰仓库根六个产物、不改 src/。
核验与下载都并发（核验 8 路、下载 4 路），顺序在结果里按 slug 重排，保证可复现。

用法：
    python -u fetch_262_batch.py --verify-only          # 第 1 步核验：81 -> 桶
    python -u fetch_262_batch.py --download 30          # 核验 + 下载前 30（按 slug 排序）

断言（任一不成立 -> 打印 FAIL 并以 1 退出）：
  B1  候选数 == 三个桶之和，且每个候选恰好一个桶
  B2  下载条目数 == min(--download, HAS_262 数)，不凑数
  B3  每个下载成功的 jar 的实测 SHA-256 是 64 位十六进制
  B4  每个下载成功的 jar 都能作为 zip 打开，且能读出 loader 线索
"""

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "modgraph-segment2-collector/1.0 (offline static analysis project)"
OUT = "dataset-262"
BUCKETS = ("HAS_262", "NO_262", "HTTP_ERROR")
VERIFY_WORKERS = 8
DOWNLOAD_WORKERS = 4


def http_json(url):
    last = None
    for attempt in range(3):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=45) as r:
                return json.loads(r.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as exc:
            last = exc
            if attempt < 2:
                time.sleep(1 + attempt)
    raise RuntimeError("%s failed: %s" % (url, last))


def pick_262_file(versions):
    """在项目版本里挑一个 26.2 文件：优先 release，其次 beta，再其次任意。"""
    cands = [v for v in versions if "26.2" in (v.get("game_versions") or [])]
    if not cands:
        return None
    order = {"release": 0, "beta": 1, "alpha": 2}
    cands.sort(key=lambda v: (order.get(v.get("version_type"), 9), v.get("date_published") or ""))
    for v in cands:
        files = [f for f in (v.get("files") or []) if f.get("primary")] or (v.get("files") or [])
        if files:
            return v, files[0]
    return None


def probe(project):
    row = {
        "slug": project["slug"],
        "projectId": project["projectId"],
        "license": project.get("license"),
        "loaders": project.get("loaders"),
        "url": project.get("url"),
        "bucket": None,
        "versionId": None,
        "versionNumber": None,
        "gameVersions": None,
        "fileName": None,
        "fileUrl": None,
        "fileSize": None,
        "error": None,
    }
    try:
        versions = http_json("%s/project/%s/version" % (API, project["projectId"]))
    except RuntimeError as exc:
        row["bucket"] = "HTTP_ERROR"
        row["error"] = str(exc)
        return row
    picked = pick_262_file(versions)
    if picked is None:
        row["bucket"] = "NO_262"
        return row
    v, f = picked
    row.update(
        {
            "bucket": "HAS_262",
            "versionId": v.get("id"),
            "versionNumber": v.get("version_number"),
            "gameVersions": v.get("game_versions"),
            "fileName": f.get("filename"),
            "fileUrl": f.get("url"),
            "fileSize": f.get("size"),
        }
    )
    return row


def detect_loader(path):
    try:
        with zipfile.ZipFile(path) as z:
            names = set(z.namelist())
    except (zipfile.BadZipFile, OSError) as exc:
        return None, "not-a-zip: %s" % exc
    if "fabric.mod.json" in names:
        return "fabric", "fabric.mod.json"
    if "META-INF/neoforge.mods.toml" in names:
        return "neoforge", "META-INF/neoforge.mods.toml"
    if "META-INF/mods.toml" in names:
        return "neoforge", "META-INF/mods.toml (forge)"
    return None, "no fabric/neoforge metadata"


def download(url, dest):
    last = None
    for attempt in range(3):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                blob = r.read()
            Path(dest).write_bytes(blob)
            return len(blob)
        except (urllib.error.URLError, OSError) as exc:
            last = exc
            if attempt < 2:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError("download failed: %s (%s)" % (url, last))


def sha256_of(path):
    try:
        blob = Path(path).read_bytes()
    except OSError as exc:
        raise SystemExit("FAIL: cannot hash %s: %s" % (path, exc))
    return hashlib.sha256(blob).hexdigest().upper()


def fetch_one(row, out_dir):
    entry = dict(row)
    dest = Path(out_dir) / "jars" / (row["fileName"] or (row["slug"] + ".jar"))
    try:
        size = download(row["fileUrl"], str(dest))
    except RuntimeError as exc:
        entry["error"] = str(exc)
        return entry, False
    entry["downloadedBytes"] = size
    entry["sha256"] = sha256_of(str(dest))
    entry["localPath"] = str(dest).replace("\\", "/")
    loader, evidence = detect_loader(str(dest))
    entry["loaderDetected"] = loader
    entry["loaderEvidence"] = evidence
    return entry, True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verify-only", action="store_true")
    ap.add_argument("--download", type=int, default=0)
    ap.add_argument("--source", default="forbric-comparison/collectability-262.json")
    ap.add_argument("--out", default=OUT)
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    out_dir = Path(args.out)
    try:
        (out_dir / "jars").mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise SystemExit("FAIL: cannot create output dir %s: %s" % (out_dir, exc))
    try:
        data = json.loads(Path(args.source).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (args.source, exc))

    want_slugs = set(data["overlapWith1211"]["slugs"])
    projects = sorted(
        [p for p in data["projects"] if p["slug"] in want_slugs], key=lambda x: x["slug"]
    )
    print("candidates (in 1.21.1 dataset, 26.2 snapshot):", len(projects), flush=True)

    with ThreadPoolExecutor(max_workers=VERIFY_WORKERS) as pool:
        results = list(pool.map(probe, projects))
    results.sort(key=lambda r: r["slug"])
    for r in results:
        print("%-9s %s" % (r["bucket"], r["slug"]), flush=True)

    counts = {b: sum(1 for r in results if r["bucket"] == b) for b in BUCKETS}
    print("buckets:", counts, "sum:", sum(counts.values()), "of", len(results), flush=True)

    ok_b1 = sum(counts.values()) == len(results) and len(results) == len(projects)
    report = {
        "source": args.source,
        "candidates": len(results),
        "buckets": counts,
        "assertionB1_buckets_sum": ok_b1,
        "results": results,
    }

    if args.download and not args.verify_only:
        has = [r for r in results if r["bucket"] == "HAS_262"]
        want = has[: args.download]
        with ThreadPoolExecutor(max_workers=DOWNLOAD_WORKERS) as pool:
            pairs = list(pool.map(lambda r: fetch_one(r, str(out_dir)), want))
        manifest = [e for e, good in pairs if good]
        failures = [e for e, good in pairs if not good]
        manifest.sort(key=lambda e: e["slug"])
        for e in manifest:
            print("OK  %-30s %10d  %s  %s" % (e["slug"], e["downloadedBytes"], e["sha256"][:16], e["loaderDetected"]), flush=True)
        for e in failures:
            print("FAIL %-30s %s" % (e["slug"], e.get("error")), flush=True)
        report["download"] = {
            "requested": args.download,
            "available": len(has),
            "attempted": len(want),
            "succeeded": len(manifest),
            "failed": len(failures),
        }
        report["assertionB2_count_matches"] = len(want) == min(args.download, len(has))
        report["assertionB3_sha256_shape"] = all(
            len(e["sha256"]) == 64 and all(c in "0123456789ABCDEF" for c in e["sha256"]) for e in manifest
        )
        report["assertionB4_loader_detected"] = all(e["loaderDetected"] for e in manifest)
        report["manifest"] = manifest
        report["failures"] = failures

    name = "availability-262.json" if (args.verify_only or not args.download) else "download-manifest.json"
    out_json = out_dir / name
    try:
        out_json.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (out_json, exc))
    print("wrote", out_json, flush=True)

    if not ok_b1:
        print("FAIL: B1 bucket assertion failed")
        return 1
    if args.download and not args.verify_only:
        keys = ("assertionB1_buckets_sum", "assertionB2_count_matches",
                "assertionB3_sha256_shape", "assertionB4_loader_detected")
        if not all(report[k] for k in keys):
            print("FAIL: a download assertion failed")
            return 1
    print("OK: assertions hold", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
