#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_download_list.py — 只生成下载地址清单，不下载任何文件。

用法：
    python build_download_list.py --count 100 --game-version 1.21.1

产物（仓库根目录）：
    download_list.md         人类可读，含编号、模组名、加载器、大小、直链，可打印可分享
    download_list.txt        纯直链，一行一个，可喂给迅雷 / IDM / aria2 批量下载
    fixtures/manifest.json   每个模组的来源、版本、sha256（用于下载后校验）

拿到清单后你手动下载（浏览器 / 迅雷 / IDM / 网盘离线均可），
下载完按 download_list.md 里标注的目标路径放好，然后运行：

    python verify_downloads.py

它会逐文件校验 sha256，告诉你哪些 OK、哪些损坏、哪些还缺。
校验通过就可以开始让 AI 干活了。

只依赖标准库，无需 pip install。
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "modgraph-download-list-builder/0.1 (offline test data collection)"


def api_get(path, params=None, retries=3):
    url = API + path
    if params:
        from urllib.parse import urlencode
        url += "?" + urlencode(params, doseq=True)
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 ** attempt)
    print(f"[WARN] API 请求失败，跳过：{url} -> {last}", file=sys.stderr)
    return None


def search_projects(loader, game_version, wanted):
    """按 loader + 版本搜索项目，按下载量排序。"""
    facets = [
        ["categories:" + loader],
        ["versions:" + game_version],
        ["project_type:mod"],
    ]
    out, offset = [], 0
    while len(out) < wanted:
        batch = api_get("/search", {
            "facets": json.dumps(facets),
            "index": "downloads",
            "limit": 100,
            "offset": offset,
        })
        if not batch or not batch.get("hits"):
            break
        out.extend(batch["hits"])
        offset += 100
        if offset >= batch.get("total_hits", 0):
            break
        time.sleep(0.3)
    return out[:wanted]


def pick_version_file(project_slug, loader, game_version):
    """挑一个匹配目标 loader 与版本的、稳定的发布文件。"""
    versions = api_get(f"/project/{project_slug}/version")
    if not versions:
        return None
    for v in versions:
        if game_version not in (v.get("game_versions") or []):
            continue
        if loader not in (v.get("loaders") or []):
            continue
        if v.get("version_type") not in ("release", "beta"):
            continue
        files = v.get("files") or []
        primary = next((f for f in files if f.get("primary")), files[0] if files else None)
        if primary and primary.get("url"):
            return {
                "version_number": v.get("version_number"),
                "file_name": primary.get("filename"),
                "url": primary.get("url"),
                "hashes": primary.get("hashes", {}),
                "size": primary.get("size"),
            }
    return None


def human(size):
    if not size:
        return "未知"
    mb = size / (1024 * 1024)
    if mb < 1:
        return f"{size / 1024:.0f} KB"
    return f"{mb:.1f} MB"


def safe_name(slug):
    return "".join(c if c.isalnum() or c in "-_" else "_" for c in slug)


def build(loader, game_version, count, root):
    print(f"[INFO] 搜索 {loader} / {game_version}，目标 {count} 个…")
    projects = search_projects(loader, game_version, count * 2)
    print(f"[INFO] 搜索到 {len(projects)} 个候选项目")

    entries, ok = [], 0
    for proj in projects:
        if ok >= count:
            break
        slug = proj.get("slug")
        vf = pick_version_file(slug, loader, game_version)
        if not vf:
            continue
        fname = f"{safe_name(slug)}__{vf['file_name']}"
        entries.append({
            "index": 0,  # 稍后统一编号
            "slug": slug,
            "title": proj.get("title"),
            "project_id": proj.get("project_id"),
            "loader": loader,
            "version": vf["version_number"],
            "rel_path": f"fixtures/jars/{loader}/{fname}",
            "file_name": fname,
            "url": vf["url"],
            "sha256": (vf.get("hashes") or {}).get("sha256"),
            "size": vf.get("size"),
        })
        ok += 1
        print(f"[INFO]  ({ok}/{count}) {slug} {vf['version_number']}")
        time.sleep(0.2)
    return entries


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=100, help="每种 loader 采集多少个")
    ap.add_argument("--game-version", default="1.21.1")
    ap.add_argument("--root", default=".", help="仓库根目录")
    args = ap.parse_args()

    root = Path(args.root).resolve()
    all_entries = []
    for loader in ("fabric", "neoforge"):
        all_entries.extend(build(loader, args.game_version, args.count, root))

    for i, e in enumerate(all_entries, 1):
        e["index"] = i

    total_size = sum(e["size"] or 0 for e in all_entries)

    # ---- download_list.md ----
    md = root / "download_list.md"
    lines = [
        "# 模组下载清单",
        "",
        f"- 游戏版本：**{args.game_version}**",
        f"- 目标数量：每种加载器 {args.count} 个",
        f"- 实际列出：**{len(all_entries)}** 个",
        f"- 合计大小：约 **{human(total_size)}**",
        "",
        "## 怎么下载",
        "",
        "1. 用浏览器 / 迅雷 / IDM / 网盘离线下载，任选一种",
        "2. 按下方「保存到」的路径放置文件（**文件名必须完全一致**）",
        "3. 全部下完后运行：`python verify_downloads.py`",
        "4. 校验通过就可以开始让 AI 干活了",
        "",
        "目录下的 `download_list.txt` 是纯直链版本，一行一个，可以直接喂给下载工具批量导入。",
        "",
        "## 清单",
        "",
        "| # | 加载器 | 模组 | 版本 | 大小 | 保存到 | 直链 |",
        "|---:|---|---|---|---|---:|---|",
    ]
    for e in all_entries:
        url_cell = f"[点击下载]({e['url']})"
        lines.append(
            f"| {e['index']} | {e['loader']} | {e['slug']} | {e['version']} "
            f"| {human(e['size'])} | `{e['rel_path']}` | {url_cell} |"
        )
    md.write_text("\n".join(lines) + "\n", encoding="utf-8")

    # ---- download_list.txt ----
    txt = root / "download_list.txt"
    txt.write_text("\n".join(e["url"] for e in all_entries) + "\n", encoding="utf-8")

    # ---- fixtures/manifest.json ----
    mf = root / "fixtures" / "manifest.json"
    mf.parent.mkdir(parents=True, exist_ok=True)
    mf.write_text(json.dumps(all_entries, indent=2, ensure_ascii=False), encoding="utf-8")

    print()
    print(f"[INFO] 完成，共 {len(all_entries)} 个模组，合计约 {human(total_size)}")
    print(f"[INFO] 人类可读清单：{md}")
    print(f"[INFO] 纯直链清单：  {txt}")
    print(f"[INFO] 校验数据：    {mf}")
    print()
    print("[INFO] 接下来：按 download_list.md 手动下载，然后运行 python verify_downloads.py")


if __name__ == "__main__":
    main()
