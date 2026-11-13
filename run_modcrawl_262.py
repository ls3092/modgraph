#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""run_modcrawl_262.py -- 用验真过的 modcrawl exe 对 26.2 jar 跑 mixin 对照原料。

只做两件事：校验 exe 的 SHA-256、逐个 jar 跑 `modcrawl mixins --json`，把原始
JSON 落到 dataset-262/<batch>/modcrawl-262/raw/，并写 invocations.tsv。
不做分类（分类是子智能体的事），不下载任何东西。

用法：
    python -u run_modcrawl_262.py --batch forbric
    python -u run_modcrawl_262.py --batch forbric --exe "%TEMP%\\opencode\\modcrawl-bin\\modcrawl.exe"

断言：
  K1  exe 的 SHA-256 必须等于 EXPECTED
  K2  每个 jar 恰好产出一个 raw 文件
"""

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

EXPECTED = "A1F65355951F81753A10B8D4B9B61AA9EEF2B65A761AD1E1569E8007509CAF56"


def sha256_of(path):
    try:
        blob = Path(path).read_bytes()
    except OSError as exc:
        raise SystemExit("FAIL: cannot read %s: %s" % (path, exc))
    return hashlib.sha256(blob).hexdigest().upper()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--batch", default="forbric")
    ap.add_argument("--exe", default=r"%TEMP%\opencode\modcrawl-bin\modcrawl.exe")
    ap.add_argument("--expected", default=EXPECTED)
    try:
        args = ap.parse_args()
    except SystemExit as exc:
        print("bad arguments: %s" % exc)
        return 2

    import os

    exe = Path(os.path.expandvars(args.exe))
    if not exe.exists():
        raise SystemExit("FAIL: exe not found: %s" % exe)
    digest = sha256_of(str(exe))
    print("exe:", exe)
    print("sha256:", digest)
    if digest != args.expected.upper():
        raise SystemExit("FAIL: K1 hash mismatch (expected %s)" % args.expected)

    base = Path("dataset-262") / args.batch
    raw_dir = base / "modcrawl-262" / "raw"
    try:
        raw_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise SystemExit("FAIL: cannot create %s: %s" % (raw_dir, exc))

    jars = sorted(p for p in (base / "fixtures" / "jars").rglob("*.jar"))
    print("jars:", len(jars))
    lines = ["jar\tloader_dir\texit_code\tsha256\traw_file"]
    produced = 0
    for jar in jars:
        try:
            proc = subprocess.run(
                [str(exe), "mixins", "--json", str(jar)],
                capture_output=True,
                text=True,
                timeout=120,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            raise SystemExit("FAIL: cannot run modcrawl on %s: %s" % (jar, exc))
        raw_path = raw_dir / (jar.stem + ".json")
        try:
            raw_path.write_text(proc.stdout, encoding="utf-8")
        except OSError as exc:
            raise SystemExit("FAIL: cannot write %s: %s" % (raw_path, exc))
        produced += 1
        lines.append(
            "\t".join([jar.name, jar.parent.name, str(proc.returncode), sha256_of(str(jar)), str(raw_path).replace("\\", "/")])
        )
        if proc.returncode != 0:
            print("non-zero exit:", jar.name, proc.returncode)

    try:
        (base / "modcrawl-262" / "invocations.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    except OSError as exc:
        raise SystemExit("FAIL: cannot write invocations: %s" % exc)

    checks = {"K1_exe_hash": digest == args.expected.upper(), "K2_one_raw_per_jar": produced == len(jars)}
    print("checks:", json.dumps(checks), "produced:", produced, "of", len(jars))
    if not all(checks.values()):
        print("FAIL")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
