#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix_downloads.py —— 按【原始文件名】认文件，自动归位并改名。

不看 sha256，只看文件名。因为每个模组在 Modrinth 上的原始文件名是唯一的，
而下载工具加的前缀、后缀、编号都只是"噪音"。

匹配规则：扫描到的文件名只要【结尾】是清单里的目标文件名，就算匹配。
所以无论是 sodium__sodium-fabric-1.21.1.jar 还是 xxx(1).sodium-fabric-1.21.1.jar
都能正确识别。

用法：
    python fix_downloads.py --search "D:\\迅雷下载"
    python fix_downloads.py --search "D:\\迅雷下载" --search "D:\\Downloads"
    python fix_downloads.py                      # 默认扫仓库目录

跑完执行 python verify_downloads.py 确认。
"""

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path


def is_valid_zip(p):
    """能不能当 zip 打开——用来判断文件是否下载完整。"""
    try:
        with zipfile.ZipFile(p) as z:
            return z.testzip() is None
    except Exception:
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".", help="仓库根目录")
    ap.add_argument("--search", action="append", default=[],
                    help="要扫描的目录，可重复指定；默认扫仓库根目录")
    ap.add_argument("--copy", action="store_true", help="复制而非移动")
    args = ap.parse_args()

    root = Path(args.root).resolve()
    mf = root / "fixtures" / "manifest.json"

    if not mf.exists():
        print("[ERROR] 找不到 fixtures/manifest.json")
        print("[ERROR] 先运行：python build_download_list.py --count 100 --game-version 1.21.1")
        sys.exit(1)

    entries = json.loads(mf.read_text(encoding="utf-8"))
    print(f"[INFO] 清单条目：{len(entries)} 个")

    # 目标文件名 -> entry
    want = {}
    for e in entries:
        want[Path(e["rel_path"]).name] = e

    # 已经就位的排除掉
    done = 0
    for name, e in list(want.items()):
        p = root / e["rel_path"]
        if p.exists():
            want.pop(name)
            done += 1
    if done:
        print(f"[INFO] 已有 {done} 个位置正确，跳过")

    search_dirs = [Path(d).resolve() for d in args.search] or [root]

    candidates = []
    for d in search_dirs:
        if not d.exists():
            print(f"[WARN] 目录不存在，跳过：{d}")
            continue
        candidates.extend(p for p in d.rglob("*.jar") if p.is_file())
    print(f"[INFO] 扫描到 {len(candidates)} 个 jar 文件")

    # 按文件名结尾匹配
    placed, skipped_broken = 0, []
    for cand in candidates:
        cname = cand.name
        hit = None
        for target in want:
            if cname == target or cname.endswith(target) or cname.endswith("__" + target):
                hit = target
                break
        if not hit:
            continue

        e = want[hit]
        dst = root / e["rel_path"]

        # 下载不完整的文件不放进去，单独报告
        if not is_valid_zip(cand):
            skipped_broken.append((e, str(cand)))
            continue

        try:
            dst.parent.mkdir(parents=True, exist_ok=True)
            if dst.exists():
                dst.unlink()
            if args.copy:
                shutil.copy2(cand, dst)
            else:
                shutil.move(str(cand), str(dst))
            want.pop(hit)
            placed += 1
        except Exception as ex:
            print(f"[ERROR] 归位失败 {cand.name} -> {dst} : {ex}")

    print()
    print(f"[INFO] 本次归位：{placed} 个")

    # 统计实际到位数量
    for loader in ("fabric", "neoforge"):
        d = root / "fixtures" / "jars" / loader
        n = len(list(d.glob("*.jar"))) if d.exists() else 0
        print(f"[INFO]   fixtures/jars/{loader}/ 现有 {n} 个")

    if skipped_broken:
        print()
        print("=== 文件损坏（下载不完整，不是有效的 zip，需重新下载）===")
        for e, path in skipped_broken:
            print(f"  {Path(e['rel_path']).name}")
            print(f"    当前在：{path}")
            print(f"    重新下载：{e['url']}")

    if want:
        print()
        print(f"=== 仍缺失 {len(want)} 个 ===")
        for name, e in list(want.values()) and [(k, v) for k, v in want.items()]:
            print(f"  {name}  ({e['loader']})")
            print(f"    {e['url']}")

    print()
    print("[INFO] 现在运行 python verify_downloads.py 确认")
    sys.exit(0 if not want and not skipped_broken else 1)


if __name__ == "__main__":
    main()
