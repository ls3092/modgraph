#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
organize_jars.py —— 不依赖任何清单，直接从 jar 内部读元数据，自动分类归位。

原理：每个 Minecraft 模组 jar 内部都自带元数据文件：
    fabric.mod.json                  -> 这是 Fabric 模组
    META-INF/neoforge.mods.toml      -> 这是 NeoForge 模组
    META-INF/mods.toml               -> 这是 Forge 模组
    quilt.mod.json                   -> 这是 Quilt 模组

脚本打开每个 jar，读它的元数据，判断它属于哪个加载器，然后移动到：
    fixtures/jars/fabric/      Fabric 与 Quilt 模组
    fixtures/jars/neoforge/    NeoForge 与 Forge 模组

文件名保持下载时的原样，不做任何改名。
跟 manifest.json 完全无关，跟文件名前缀后缀完全无关。

用法：
    python organize_jars.py --search "D:\\迅雷下载"
    python organize_jars.py --search "D:\\迅雷下载" --search "D:\\Downloads"
    python organize_jars.py --search "D:\\"          # 全盘扫，慢但最全

跑完执行 python verify_downloads.py 确认。
"""

import argparse
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

META_CANDIDATES = [
    "fabric.mod.json",
    "quilt.mod.json",
    "META-INF/neoforge.mods.toml",
    "META-INF/mods.toml",
]


def _parse_meta_bytes(raw, cand):
    """把元数据文本解析成 (family, modId, version)。"""
    if cand.endswith(".json"):
        # utf-8-sig 会自动去掉 BOM；以前用 errors="replace" 会把 BOM 变成非法字符，
        # 导致 json.loads 抛异常，文件被误判为"无法识别"（Debugify / EuphoriaPatcher 就栽在这）
        obj = json.loads(raw)
        fam = "quilt" if cand.startswith("quilt") else "fabric"
        return fam, obj.get("id"), obj.get("version")
    fam = "neoforge" if "neoforge" in cand else "forge"
    mid = ver = None
    m = re.search(r'modId\s*=\s*"([^"]+)"', raw)
    if m:
        mid = m.group(1)
    m = re.search(r'^\s*version\s*=\s*"([^"]+)"', raw, re.M)
    if m:
        ver = m.group(1)
    return fam, mid, ver


def _scan_names(z, prefix=""):
    """在 zip 内查找元数据，找不到返回 None。"""
    names = set(z.namelist())
    for cand in META_CANDIDATES:
        if cand not in names:
            continue
        try:
            raw = z.read(cand).decode("utf-8-sig", errors="replace")
        except Exception:
            continue
        try:
            fam, mid, ver = _parse_meta_bytes(raw, cand)
        except Exception as ex:
            print(f"[WARN] 元数据解析失败 {prefix}{cand}: {ex}")
            continue
        return fam, mid, ver, prefix + cand
    return None


def read_meta(p, depth=1):
    """打开 jar 读元数据，返回 (family, modId, version, meta_path)。

    先查表层；找不到再查 jar-in-jar 嵌套 jar（kotlinforforge、connector、
    Essential 这类把元数据包在内部 jar 里的）。
    """
    try:
        with zipfile.ZipFile(p) as z:
            hit = _scan_names(z)
            if hit:
                return hit
            if depth > 0:
                for n in z.namelist():
                    if not n.endswith(".jar"):
                        continue
                    try:
                        data = z.read(n)
                    except Exception:
                        continue
                    try:
                        with zipfile.ZipFile(__import__("io").BytesIO(data)) as z2:
                            hit2 = _scan_names(z2, prefix=n + "!/")
                    except Exception:
                        continue
                    if hit2:
                        return hit2
    except zipfile.BadZipFile:
        return None, None, None, None
    except Exception as ex:
        print(f"[WARN] 读取失败 {p.name}: {ex}")
        return None, None, None, None
    return None, None, None, None


def diagnose(p, max_show=25):
    """打不开思路：列出 jar 内部到底有什么，帮我们定位元数据藏在哪。"""
    try:
        with zipfile.ZipFile(p) as z:
            names = z.namelist()
    except Exception as ex:
        return [f"      无法打开：{ex}"]

    out = []
    # 顶层条目（目录层）
    tops = sorted({n.split("/")[0] + ("/" if "/" in n else "") for n in names})
    out.append(f"      顶层：{', '.join(tops[:12])}"
               + (" …" if len(tops) > 12 else ""))

    # 任何看起来像元数据的文件
    suspects = [n for n in names
                if n.endswith(".toml")
                or n.endswith("mod.json")
                or n.endswith(".mods.json")
                or "MANIFEST" in n.upper()]
    if suspects:
        out.append(f"      疑似元数据：{', '.join(suspects[:8])}")
    else:
        out.append("      没有任何 .toml / mod.json / MANIFEST")

    # 嵌套 jar（jar-in-jar 是元数据藏起来的最常见原因）
    nested = [n for n in names if n.endswith(".jar")]
    if nested:
        out.append(f"      ** 嵌套 jar {len(nested)} 个：{', '.join(nested[:5])}"
                   + (" …" if len(nested) > 5 else ""))
        out.append("         → 元数据很可能在这些嵌套 jar 里面")

    if len(names) <= max_show:
        out.append(f"      全部条目（{len(names)}）：{', '.join(names)}")
    return out


def dest_dir_for(family):
    if family in ("fabric", "quilt"):
        return "fabric"
    if family in ("neoforge", "forge"):
        return "neoforge"
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".", help="仓库根目录")
    ap.add_argument("--search", action="append", default=[],
                    help="要扫描的目录，可重复指定")
    ap.add_argument("--copy", action="store_true", help="复制而非移动")
    ap.add_argument("--dry-run", action="store_true",
                    help="只报告打算做什么，不真的移动")
    ap.add_argument("--no-quarantine", action="store_true",
                    help="不隔离损坏/无法识别的文件，留在原处")
    args = ap.parse_args()

    root = Path(args.root).resolve()
    quarantine = root / "fixtures" / "quarantine"

    if not args.search:
        # 默认扫描仓库目录和用户下载目录
        cands = [root, Path.home() / "Downloads", Path.home() / "Desktop"]
        args.search = [str(c) for c in cands if c.exists()]
        print(f"[INFO] 未指定 --search，默认扫描：{args.search}")

    search_dirs = [Path(d).resolve() for d in args.search]

    # 收集候选 jar。
    # 注意：不再一律跳过目标目录里的文件——放错位置的 jar 正是要被重新分类的对象。
    candidates = []
    for d in search_dirs:
        if not d.exists():
            print(f"[WARN] 目录不存在，跳过：{d}")
            continue
        for p in d.rglob("*.jar"):
            if not p.is_file():
                continue
            # 跳过隔离区，避免重复处理
            try:
                if p.is_relative_to(quarantine):
                    continue
            except AttributeError:
                if str(p).startswith(str(quarantine)):
                    continue
            candidates.append(p)

    # 去重（同一文件被多个 --search 目录覆盖）
    seen = set()
    uniq = []
    for p in candidates:
        rp = str(p.resolve())
        if rp not in seen:
            seen.add(rp)
            uniq.append(p)
    candidates = uniq

    print(f"[INFO] 扫描到 {len(candidates)} 个 jar（不含已归位的）")
    print("[INFO] 开始读取每个 jar 的内部元数据…")

    placed = {"fabric": 0, "neoforge": 0}
    unknown, broken = [], []
    manifest = []

    for i, p in enumerate(candidates, 1):
        fam, mid, ver, meta = read_meta(p)

        # 无法识别：区分"坏文件"和"能打开但没元数据"
        if fam is None:
            try:
                with zipfile.ZipFile(p):
                    pass
                # 先诊断（此时文件还在原位），再隔离
                diag = diagnose(p)
                unknown.append((p, "能打开但没有已知元数据文件", diag))
            except Exception:
                broken.append((p, "不是有效的 zip（下载不完整）"))
                diag = []
            # 隔离：坏的/认不出的不该混在数据集里
            if not args.no_quarantine:
                q_dir = quarantine / ("broken" if p in [b[0] for b in broken] else "unknown")
                q_dir.mkdir(parents=True, exist_ok=True)
                q_dst = q_dir / p.name
                n = 2
                while q_dst.exists():
                    q_dst = q_dir / f"{p.stem}-{n}{p.suffix}"
                    n += 1
                if not args.dry_run:
                    try:
                        shutil.move(str(p), str(q_dst))
                    except Exception as ex:
                        print(f"[ERROR] 隔离失败 {p.name}: {ex}")
                else:
                    print(f"[DRY]  隔离 {p.name} -> fixtures/quarantine/")
            continue

        sub = dest_dir_for(fam)
        if sub is None:
            unknown.append((p, f"未知加载器 {fam}"))
            continue

        dst_dir = root / "fixtures" / "jars" / sub

        # 已经在正确的目录里 —— 不用动
        try:
            already_ok = p.parent.resolve() == dst_dir.resolve()
        except Exception:
            already_ok = False
        if already_ok:
            manifest.append({
                "sourceFile": p.name,
                "loader": fam,
                "modId": mid,
                "version": ver,
                "metaFile": meta,
                "rel_path": f"fixtures/jars/{sub}/{p.name}",
            })
            continue

        dst_dir.mkdir(parents=True, exist_ok=True)
        dst = dst_dir / p.name

        # 重名处理
        n = 2
        while dst.exists():
            dst = dst_dir / f"{p.stem}-{n}{p.suffix}"
            n += 1

        moved_from = p.parent.name
        try:
            if args.dry_run:
                print(f"[DRY]  {moved_from}/{p.name} -> fixtures/jars/{sub}/{dst.name}")
            elif args.copy:
                shutil.copy2(p, dst)
            else:
                shutil.move(str(p), str(dst))
            placed[sub] += 1
            manifest.append({
                "sourceFile": dst.name,
                "loader": fam,
                "modId": mid,
                "version": ver,
                "metaFile": meta,
                "rel_path": f"fixtures/jars/{sub}/{dst.name}",
            })
        except Exception as ex:
            print(f"[ERROR] 移动失败 {p.name}: {ex}")

        if i % 50 == 0:
            done = placed["fabric"] + placed["neoforge"]
            print(f"[INFO]   已处理 {i}/{len(candidates)}，归位 {done} 个")

    # 统计目标目录现状
    print()
    print("[INFO] ===== 归位结果 =====")
    for sub in ("fabric", "neoforge"):
        d = root / "fixtures" / "jars" / sub
        n = len(list(d.glob("*.jar"))) if d.exists() else 0
        print(f"[INFO]   fixtures/jars/{sub}/ : 本次移动 {placed[sub]} 个，现有共 {n} 个")

    if broken:
        print()
        print(f"=== 损坏文件 {len(broken)} 个（下载不完整，不是有效 zip）===")
        for p, why in broken[:15]:
            print(f"  {p.name}  <- {p.parent}")
        if len(broken) > 15:
            print(f"  ……还有 {len(broken) - 15} 个")

    if unknown:
        print()
        print(f"=== 无法识别 {len(unknown)} 个（能打开但找不到模组元数据）===")
        for item in unknown[:15]:
            p, why = item[0], item[1]
            diag = item[2] if len(item) > 2 else []
            print(f"  {p.name}  ({why})")
            for line in diag:
                print(line)
        if len(unknown) > 15:
            print(f"  ……还有 {len(unknown) - 15} 个")
        print("  说明：可能是资源包/数据包，或者元数据藏在嵌套 jar 里，见上面的内部结构")

    # 重写 manifest，供后续步骤参考
    mf = root / "fixtures" / "manifest.json"
    mf.parent.mkdir(parents=True, exist_ok=True)
    mf.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print()
    print(f"[INFO] 已重建 fixtures/manifest.json（{len(manifest)} 条）")
    print("[INFO] 现在运行 python verify_downloads.py 确认")


if __name__ == "__main__":
    main()
