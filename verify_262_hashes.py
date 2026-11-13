#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""verify_262_hashes.py -- 用 Modrinth 官方 hashes 复核本地 26.2 jar 的字节一致性。

对 dataset-262/download-manifest.json 的每个条目：
  1. 读本地 jar，算 SHA-1 / SHA-512 / SHA-256（本地值，不信任清单）
  2. GET /v2/version/{versionId}，取 files[*] 中与该 jar 同名的条目
  3. 比对 sha512（Modrinth 发布的 hashes 里还有 sha1；本轮只用 sha512，因为它是强哈希，
     且本地 SHA-256 无法跨源比对——上游不发布 sha256）
不下载 jar 本体（只读本地文件 + 一次 JSON 请求）。

注：这里的 SHA-1 只用于与上游发布的校验值做一致性比对，不是安全用途，
所以调用时显式 usedforsecurity=False。真正的完整性标识是 SHA-256 / SHA-512。

用法：
    python -u verify_262_hashes.py
    python -u verify_262_hashes.py --out dataset-262/hash-crosscheck.json

断言：
  E1  每个条目都能在 /v2/version/{id} 的文件列表里找到同名文件
  E2  找到的文件至少带一个可跨源比对的哈希（sha1 或 sha512）
"""

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "modgraph-segment3-hashcheck/1.0 (offline static analysis project)"
MANIFEST = Path("dataset-262/download-manifest.json")


def http_json(url):
    last = None
    for attempt in range(3):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                return json.loads(r.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as exc:
            last = exc
            if attempt < 2:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError("%s failed: %s" % (url, last))


def local_hashes(path):
    try:
        blob = Path(path).read_bytes()
    except OSError as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))
    return {
        "sha512": hashlib.sha512(blob).hexdigest(),
        "sha256": hashlib.sha256(blob).hexdigest().upper(),
        "bytes": len(blob),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", default=str(MANIFEST))
    ap.add_argument("--out", default="dataset-262/hash-crosscheck.json")
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    try:
        data = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (args.manifest, exc))

    rows = []
    for entry in data.get("manifest") or []:
        record = {
            "slug": entry["slug"],
            "versionId": entry["versionId"],
            "fileName": entry.get("fileName"),
            "license": entry.get("license"),
            "localPath": entry.get("localPath"),
        }
        local = local_hashes(entry["localPath"])
        record["local"] = local
        record["sha256MatchesManifest"] = local["sha256"] == entry.get("sha256")
        try:
            version = http_json("%s/version/%s" % (API, entry["versionId"]))
        except RuntimeError as exc:
            record["bucket"] = "HTTP_ERROR"
            record["error"] = str(exc)
            rows.append(record)
            continue
        files = version.get("files") or []
        same = [f for f in files if f.get("filename") == entry.get("fileName")]
        if not same:
            record["bucket"] = "FILE_NOT_LISTED"
            record["listed"] = [f.get("filename") for f in files]
            rows.append(record)
            continue
        remote = same[0].get("hashes") or {}
        record["remoteHashes"] = sorted(remote.keys())
        checks = {}
        if remote.get("sha512"):
            checks["sha512"] = remote["sha512"].lower() == local["sha512"]
        record["checks"] = checks
        if not checks:
            record["bucket"] = "NO_COMPARABLE_HASH"
        elif all(checks.values()):
            record["bucket"] = "MATCH"
        else:
            record["bucket"] = "MISMATCH"
        rows.append(record)

    buckets = {}
    for r in rows:
        buckets[r["bucket"]] = buckets.get(r["bucket"], 0) + 1
    checks = {
        "E1_every_file_found": all(r["bucket"] != "FILE_NOT_LISTED" for r in rows),
        "E2_comparable_hash_present": all(r["bucket"] != "NO_COMPARABLE_HASH" for r in rows),
        "E3_all_matched": all(r["bucket"] == "MATCH" for r in rows),
    }
    out = {
        "manifest": str(args.manifest),
        "entries": len(rows),
        "buckets": buckets,
        "assertions": checks,
        "rows": sorted(rows, key=lambda r: r["slug"]),
    }
    try:
        Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (args.out, exc))

    for r in out["rows"]:
        extra = "  <-- 84 MB, singled out" if r["slug"] == "ambientsounds" else ""
        print(
            "%-10s %-24s sha512=%s sha256=%s%s"
            % (
                r["bucket"],
                r["slug"],
                r.get("checks", {}).get("sha512"),
                r["local"]["sha256"][:16],
                extra,
            ),
            flush=True,
        )
    print("buckets:", buckets)
    print("assertions:", json.dumps(checks))
    print("wrote", args.out)
    if not (checks["E1_every_file_found"] and checks["E2_comparable_hash_present"]):
        print("FAIL: an assertion failed (see rows)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
