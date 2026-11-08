# Forbric failure-list vs modgraph metadata

- generated: 2026-11-07T20:43:02.171862+08:00
- failure file: `D:\modgraph\modgraph\forbric-comparison\MOD_TEST_FAILURES.md`
- failure SHA-256: `96ae0bc7e3dfbaed6f76abcddc104fbf79f1db973048e479490c9bedec0e3cfb`
- candidates: `D:\modgraph\modgraph\candidates.jsonl`
- candidate records: **195**
- lists parsed: `35` 35 rows, `12` 12 rows (different samples; never merged)

## Original first 20 lines

```markdown
# Mod 兼容测试：不成功的 mod（待修）

测试日期：2026-09-26 至 2026-09-27
加载器：Forbric main `11ca1ffa`，用 `forbric-kernel-installer` 装进 Mac 官方目录 `~/Library/Application Support/minecraft`（版本 `26.2-forbric`）
下面的历史测试保留当时结果；后续修复及验证单独记录，不回写原始统计。

## 130 个纯 Fabric mod 复测：只在 Forbric 失败的从 7 个降到 0 个（服务端，2026-10-11）

还是 2026-10-03 那 165 个 jar，原生一侧的结果直接沿用（原生启动器的 SHA-256 一样）。Forbric 一侧换成 main `6d1dd0f2` 加上下面这条修复后的内核（SHA-256 `ce609c8a…`），合并基底用这棵树自己的 `tools/dev.py prepare --no-assets` 现建。证据在 `forbric-kernel/run/compat/reports/2026-10-11-pure-fabric-server/`。**仍然只测了服务端。**

| | 2026-10-03 | 2026-10-11 |
|---|---|---|
| 单独跑（各带依赖，200 tick） | 119 两边都过 · 7 只在 Forbric 失败 · 4 两边都失败 | **126 两边都过 · 0 只在 Forbric 失败** · 4 两边都失败 · 0 只在原生失败 |
| 原生能单独跑的 126 个一起（159 jar，1200 tick） | 原生 Done，Forbric 被策略拒绝 | **两边都跑满** |
| 全部 130 个一起（165 jar） | 两边都起不来 | 两边都起不来，原因还是那 4 个两边都失败的 mod |

- 当时那 7 个都已在 2026-10-05 修好，现在全部两边都过。
- 复测途中发现一个新的回退：在 main `6d1dd0f2` 上，naturalist 只在 Forbric 失败（10-03 时两边都过）。原因是合并工具的一次改动让方块物品不再和方块关联，Fabric 初始化时 `Blocks.MELON.asItem()` 拿到的是空气。已修：让 MinecraftForge 物品注册表自己维护的方块→物品映射直接就是游戏读取的那一张，所有注册路径在注册当下就关联好。不认任何 mod 名字。
- 那 4 个两边都失败的仍是 mod 自己的问题。

```

## List `35`: 2026-09-27 main 上不成功的 mod（35 个）

- rows: **35**; unique Forbric ids: **32**
- located in candidates: rows **10** / unique ids **8**; no candidate: rows **25** / unique ids **24**

### Primary buckets

| bucket | rows |
| --- | ---: |
| NO_CANDIDATE | 25 |
| AMBIGUOUS_CANDIDATE | 0 |
| LOADER_MISMATCH | 1 |
| STATUS_FAILED | 0 |
| EXCLUDED_RENDER_CORE | 4 |
| DEPENDENCY_SIGNAL | 5 |
| MIXIN_SIGNAL_ONLY | 0 |
| MATCHED_NO_SIGNAL | 0 |
| UNCLASSIFIED | 0 |
| **sum** | **35** |

### Non-exclusive field counts

| field | rows |
| --- | ---: |
| candidateLocated | 10 |
| statusFailed | 0 |
| supportPolicyExcluded | 4 |
| dependenciesNonSatisfied | 10 |
| mixinTargetsNonEmpty | 7 |
| conflictsHighMedium | 6 |
| nestedJarsNonEmpty | 1 |
| dependsNonEmpty | 10 |
| loaderMatch | 9 |
| loaderMismatch | 1 |
| versionExactMatch | 0 |

### Located rows

| mod | loader | state | candidate modId | candidate loader | bucket | dependency outcomes | conflict levels |
| --- | --- | --- | --- | --- | --- | --- | --- |
| biomes-o-plenty | Fabric | 崩溃 | biomesoplenty | FABRIC | DEPENDENCY_SIGNAL | AMBIGUOUS_ALIAS=1;MISSING_ID=2;SATISFIED=6 | LOW=12;NONE=103 |
| iris | NeoForge | 崩溃 | iris | NEOFORGE | EXCLUDED_RENDER_CORE | BREAKS_VIOLATED=2;SATISFIED=14;VERSION_MISMATCH=1 | HIGH=12;LOW=46;MEDIUM=47;NONE=10 |
| lambdynamiclights | NeoForge | 崩溃 | lambdynlights | FABRIC | LOADER_MISMATCH | MISSING_ID=1 | NONE=115 |
| player-animation-library | NeoForge | 崩溃 | playeranimator | NEOFORGE | DEPENDENCY_SIGNAL | MISSING_ID=1;SATISFIED=3 | HIGH=11;LOW=5;MEDIUM=17;NONE=82 |
| sodium | NeoForge | 崩溃 | sodium | NEOFORGE | EXCLUDED_RENDER_CORE | BREAKS_VIOLATED=2;MISSING_ID=5;SATISFIED=32 | HIGH=16;LOW=37;MEDIUM=52;NONE=10 |
| sodium | NeoForge | 崩溃 | sodium | NEOFORGE | EXCLUDED_RENDER_CORE | BREAKS_VIOLATED=2;MISSING_ID=5;SATISFIED=32 | HIGH=16;LOW=37;MEDIUM=52;NONE=10 |
| sodium-extra | NeoForge | 崩溃 | sodium_extra | NEOFORGE | EXCLUDED_RENDER_CORE | MISSING_ID=1;SATISFIED=2 | HIGH=4;LOW=46;MEDIUM=55;NONE=10 |
| fancymenu | NeoForge | 卡在加载画面 | fancymenu | NEOFORGE | DEPENDENCY_SIGNAL | AMBIGUOUS_ALIAS=1;MISSING_ID=5;SATISFIED=15 | HIGH=13;LOW=1;MEDIUM=54;NONE=47 |
| fzzy-config | NeoForge | 能进世界，mod 加载失败 | fzzy_config | NEOFORGE | DEPENDENCY_SIGNAL | AMBIGUOUS_ALIAS=1;MISSING_ID=1;SATISFIED=6 | NONE=115 |
| fzzy-config | NeoForge | 能进世界，mod 加载失败 | fzzy_config | NEOFORGE | DEPENDENCY_SIGNAL | AMBIGUOUS_ALIAS=1;MISSING_ID=1;SATISFIED=6 | NONE=115 |

### Unmatched (no candidate)

- `alexs-mobs-continued` — alexs-mobs-continued (NeoForge)
- `better-combat` — better-combat (NeoForge)
- `itemglintrelight` — itemglintrelight (Fabric)
- `mcrpvp` — mcrpvp (Fabric)
- `particle-core` — particle-core (NeoForge)
- `particledrawing` — particledrawing (NeoForge)
- `sodium-extra-information` — sodium-extra-information (NeoForge)
- `ukulib` — ukulib (Fabric)
- `animated-loading-overlay` — animated-loading-overlay (Fabric)
- `bclib` — bclib (Fabric)
- `bclib-neoforge` — bclib-neoforge (NeoForge)
- `drippy-loading-screen` — drippy-loading-screen (NeoForge)
- `bclib` — bclib (Fabric)
- `easy-magic` — easy-magic (NeoForge)
- `entitycrosshair` — entitycrosshair (Fabric)
- `expanded-crossbow-enchantings` — expanded-crossbow-enchantings (Forge)
- `mythicquests` — mythicquests (Fabric)
- `oneconfig` — oneconfig (Fabric)
- `auto-eat` — auto-eat (NeoForge)
- `emotesounds` — emotesounds (Fabric)
- `macebot` — macebot (Fabric)
- `pigeon-chat` — pigeon-chat (Fabric)
- `simpleguiapi` — simpleguiapi (NeoForge)
- `the-elementals` — the-elementals (Fabric)
- `time-weather-changer` — time-weather-changer (Fabric)

## List `12`: 2026-10-01 仍未严格成功的 12 个主体

- rows: **12**; unique Forbric ids: **12**
- located in candidates: rows **0** / unique ids **0**; no candidate: rows **12** / unique ids **12**

### Primary buckets

| bucket | rows |
| --- | ---: |
| NO_CANDIDATE | 12 |
| AMBIGUOUS_CANDIDATE | 0 |
| LOADER_MISMATCH | 0 |
| STATUS_FAILED | 0 |
| EXCLUDED_RENDER_CORE | 0 |
| DEPENDENCY_SIGNAL | 0 |
| MIXIN_SIGNAL_ONLY | 0 |
| MATCHED_NO_SIGNAL | 0 |
| UNCLASSIFIED | 0 |
| **sum** | **12** |

### Non-exclusive field counts

| field | rows |
| --- | ---: |
| candidateLocated | 0 |
| statusFailed | 0 |
| supportPolicyExcluded | 0 |
| dependenciesNonSatisfied | 0 |
| mixinTargetsNonEmpty | 0 |
| conflictsHighMedium | 0 |
| nestedJarsNonEmpty | 0 |
| dependsNonEmpty | 0 |
| loaderMatch | 0 |
| loaderMismatch | 0 |
| versionExactMatch | 0 |

### Located rows

(none)

### Unmatched (no candidate)

- `clientcarts` — ClientCarts
- `crashassistant` — Crash Assistant
- `leavesbegone` — Leaves Be Gone
- `myconnectionmychoice` — MyConnectionMyChoice
- `c2me` — C2ME
- `extendeddrawerspolymerpatch` — Extended Drawers Polymer Patch
- `lplm` — LPLM
- `nofog` — NoFog
- `opticore` — OptiCore
- `playercollars` — PlayerCollars
- `soundvisualizer` — Sound Visualizer
- `tuanzisservermod` — Tuanzi’s Server Mod

## Assertions

- failureFileExists: PASS
- sha256Recorded: PASS
- list35BucketsEqualRows: PASS
- list12BucketsEqualRows: PASS
- list35LocatedPlusUnmatchedEqualRows: PASS
- list12LocatedPlusUnmatchedEqualRows: PASS
- noUnclassifiedList35: PASS
- noUnclassifiedList12: PASS
- listsAreDisjoint: PASS

## Reading notes

- The Markdown file is a historical log with several snapshots. The two named tables above are parsed separately; they are different samples and must not be added together.
- The 2026-10-11 section reports 4 both-failure mods but does not name them. They are not guessed or counted.
- The 2026-10-03 section says 10 of the 12 still fail (C2ME and LPLM improved), but does not list all 10 names; this script does not construct that list.
- A primary bucket only says an existing static field can locate or flag the row. It is not proof that the field explains the Forbric runtime failure.
- Each selected candidate is a 1.21.1 record; the Forbric rows are 26.2. Version exact matches are therefore 0 for the located rows.
- `dependencies.csv` and `conflicts.csv` are keyed by modId, not by loader variant; Fabric/NeoForge variants of the same modId can merge in those counters.
