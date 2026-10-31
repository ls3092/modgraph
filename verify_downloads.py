#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_downloads.py —— 确认 fixtures/jars 下的模组都已就位且完整。

不依赖任何清单。只做两件事：
1. 数 fixtures/jars/fabric/ 和 fixtures/jars/neoforge/ 里各有多少 jar
2. 逐个确认它们是能打开的、且内部含模组元数据的有效 jar

用法：
    python verify_downloads.py
"""

import json
import sys
import zipfile
from pathlib import Path

META_CANDIDATES = [
    "fabric.mod.json",
    "quilt.mod.json",
    "META-INF/neoforge.mods.toml",
    "META-INF/mods.toml",
]


def inspect(p):
    """返回 (是否完整, 是否有元数据, 元数据文件名)"""
    try:
        with zipfile.ZipFile(p) as z:
            names = set(z.namelist())
            if z.testzip() is not None:
                return False, False, None
            for c in META_CANDIDATES:
                if c in names:
                    return True, True, c
            return True, False, None
    except Exception:
        return False, False, None


def main():
    root = Path(".").resolve()

    total_ok = 0
    no_meta = []
    corrupt = []

    print()
    print("[INFO] ===== 数据集现状 =====")
    for sub in ("fabric", "neoforge"):
        d = root / "fixtures" / "jars" / sub
        if not d.exists():
            print(f"[INFO]   fixtures/jars/{sub}/ : 目录不存在")
            continue
        jars = sorted(d.glob("*.jar"))
        ok = 0
        for p in jars:
            intact, has_meta, meta = inspect(p)
            if intact and has_meta:
                ok += 1
            elif intact:
                no_meta.append((sub, p))
            else:
                corrupt.append((sub, p))
        total_ok += ok
        print(f"[INFO]   fixtures/jars/{sub}/ : {len(jars)} 个 jar，其中 {ok} 个可用")

    print()
    print(f"[INFO] 可用模组合计：{total_ok} 个")

    if corrupt:
        print()
        print(f"=== 损坏 {len(corrupt)} 个（不是有效 zip，需重新下载）===")
        for sub, p in corrupt[:15]:
            print(f"  [{sub}] {p.name}")
        if len(corrupt) > 15:
            print(f"  ……还有 {len(corrupt) - 15} 个")

    if no_meta:
        print()
        print(f"=== 无元数据 {len(no_meta)} 个（能打开但不是模组，如资源包）===")
        for sub, p in no_meta[:10]:
            print(f"  [{sub}] {p.name}")
        if len(no_meta) > 10:
            print(f"  ……还有 {len(no_meta) - 10} 个")
        print("  建议：把这些移出 fixtures/jars，它们不是模组")

    print()
    if total_ok >= 40 and not corrupt:
        print("[INFO] 数据集可用，可以开始让 AI 干活了。")
        sys.exit(0)

    if total_ok < 40:
        print("[WARN] 可用数量偏少。建议至少每边 40 个，否则 AI 的统计结果不可信。")
        print("[WARN] 先运行 organize_jars.py --search \"你的下载目录\" 归位更多文件。")

    sys.exit(0 if not corrupt else 1)


if __name__ == "__main__":
    main()
