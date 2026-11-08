#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""prepare_262_dataset.py -- 把已下载的 26.2 jar 放进 M0 的扫描目录并校验哈希。

M0 的扫描根目录在 Main 里是硬编码的 `fixtures/jars/fabric` 与 `fixtures/jars/neoforge`
（相对 CWD）。为了让新批次产物落在 dataset-262/ 下，这里把 jar **移动**（不是复制）
到 `dataset-262/fixtures/jars/<loader>/`，这样在 CWD=dataset-262 跑 M0 即可，既不碰
仓库根的 fixtures/jars，也不碰仓库根那六个既有产物。

用法：
    python prepare_262_dataset.py

断言：
  D1  每个清单条目的 jar 都存在，且移动后 SHA-256 与清单记录一致
  D2  落位后的 jar 数 == 清单条目数
  D3  每个 jar 都落在 fabric 或 neoforge 某一侧，没有 UNKNOWN
"""

import hashlib
import json
import shutil
import sys
from pathlib import Path

BASE = Path("dataset-262")
MANIFEST = BASE / "download-manifest.json"


def sha256_of(path):
    try:
        blob = Path(path).read_bytes()
    except OSError as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))
    return hashlib.sha256(blob).hexdigest().upper()


def main():
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (MANIFEST, exc))

    manifest = data.get("manifest") or []
    if not manifest:
        raise SystemExit("FAIL: manifest is empty; run fetch_262_batch.py --download N first")

    placed, problems = [], []
    for entry in manifest:
        src = Path(entry["localPath"])
        loader = entry.get("loaderDetected")
        if not src.exists():
            problems.append((entry["slug"], "missing %s" % src))
            continue
        if loader not in ("fabric", "neoforge"):
            problems.append((entry["slug"], "unknown loader: %r" % loader))
            continue
        dest_dir = BASE / "fixtures" / "jars" / loader
        try:
            dest_dir.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            raise SystemExit("FAIL: cannot create %s: %s" % (dest_dir, exc))
        dest = dest_dir / src.name
        before = sha256_of(src)
        if before != entry["sha256"]:
            problems.append((entry["slug"], "sha256 changed before move"))
            continue
        try:
            shutil.move(str(src), str(dest))
        except (OSError, shutil.Error) as exc:
            problems.append((entry["slug"], "move failed: %s" % exc))
            continue
        after = sha256_of(dest)
        if after != before:
            problems.append((entry["slug"], "sha256 changed after move"))
            continue
        entry["localPath"] = str(dest).replace("\\", "/")
        entry["placedForM0"] = True
        placed.append(entry)

    counts = {"fabric": 0, "neoforge": 0}
    for e in placed:
        counts[e["loaderDetected"]] += 1

    data["placement"] = {
        "placed": len(placed),
        "problems": len(problems),
        "byLoader": counts,
        "note": "jars were moved (not copied) into dataset-262/fixtures/jars/<loader>/ for the M0 run",
    }
    checks = {
        "D1_hashes_stable": not any("sha256" in p[1] for p in problems),
        "D2_placed_equals_manifest": len(placed) == len(manifest),
        "D3_every_jar_has_loader": all(e["loaderDetected"] in ("fabric", "neoforge") for e in placed),
    }
    data["assertions"] = checks
    try:
        MANIFEST.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write %s: %s" % (MANIFEST, exc))

    print("placed", len(placed), "of", len(manifest), counts)
    for slug, why in problems:
        print("PROBLEM", slug, why)
    print(json.dumps(checks, indent=2))
    if not all(checks.values()):
        print("FAIL: a placement assertion failed")
        return 1
    print("OK: all placement assertions hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
