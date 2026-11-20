# BOUNDARIES — 外部对照及其解释边界

状态：v1.1（2026-11-08）。本文件不属于 SPEC 验收标准。它固化两轮外部对照的实测结果，以及必须与这些数字一起传播的解释边界。以下计数取自 `PROGRESS.md`（第二十步与第二十一步），§7 另取第二十六步的 26.2 批次；`PROGRESS.md` 变化时，本文件两版必须同步。

变更记录：v1.1 新增 §7（loader 家族覆盖，带 1.21.1 基线限定与 26.2 首次真实触发）与 §8（数据处理），并在 §2.5 增加一条。

## 1. 为什么有这个文件

两轮外部对照只用来量化已知缺口，不用来证明工具有效：

- `modcrawl` Mixin 覆盖率对照：命名推断漏掉了多少 Mixin 目标；
- Forbric `MOD_TEST_FAILURES.md` 对照：外部失败清单里有多少条能在 1.21.1 数据集中定位，以及它们带什么静态信号。

两轮都是离线静态对照，不是运行时测试；都不产出安装顺序、兼容性结论或类加载预测。

## 2. modcrawl Mixin 覆盖率对照

### 2.1 设置

- 输入：195 个 jar、195 条候选记录、191 条 OK、4 条 FAILED（`191 + 4 = 195`）。
- 四分类只覆盖 191 条 OK 记录。
- 工具：上游 `modcrawl` v0.11.3 预编译 Windows 二进制，不是本地编译（本机没有 `cargo` / `rustc`）。本地 fork 的 `src/cmd/mixins.rs` 与上游 v0.11.3 内容一致，仅 CRLF/LF 差异。二进制 SHA-256 记在 `PROGRESS.md`（第二十步）。
- 命令形态：`modcrawl.exe mixins --json <jar>`。只扫传入 jar 的顶层，不递归嵌套 jar。

### 2.2 四分类结果

| 桶 | 记录数 | 唯一 modId 数 |
|---|---:|---:|
| BOTH | 157 | 100 |
| NAMING_ONLY | 1 | 1（`sodium-neoforge`） |
| MODCRAWL_ONLY | 6 | 5 |
| NEITHER | 27 | 21 |
| **合计** | **191** | — |

唯一 modId 列跨桶会重叠（例如 sodium 的 Fabric 记录在 BOTH、NeoForge 记录在 NAMING_ONLY），不能相加。

### 2.3 原来的 10 条缺口

- 空 `mixinTargets` 的 OK 记录 33 条；其中 `mixinConfigs` 非空的 10 条构成缺口，其余 23 条配置本身为空。
- 缺口是 10 条记录 / 8 个唯一 modId。`modcrawl` 补上 5 条 / 4 个唯一 modId；剩余 5 条 / 4 个唯一 modId。
- 剩余 5 条全部是 `EMPTY_CONFIG`：配置存在但未枚举任何 mixin 类，`modcrawl` 顶层也读到 0 条 `@Mixin`。它们不是“命名规则漏掉目标”的证据——没有可推断的目标。
- `NEITHER` 桶正好是这 5 条加上 22 条空配置且 `modcrawl` 也未命中的记录；`MODCRAWL_ONLY` 桶是补上的 5 条加上 1 条空配置但被 `modcrawl` 读到的记录（`modelfix`）。

### 2.4 嵌套 jar 的边界

`NAMING_ONLY` 只有 1 条，`sodium-neoforge`：命名推断 74 个目标，`modcrawl` 顶层 0 个。把嵌套 jar `META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar` 抽出来单独跑 `modcrawl`，得到 99 条 / 72 个唯一目标。差异来自 `modcrawl` 不递归嵌套 jar，不是 sodium 没有 mixin。因此“`modcrawl` 顶层没读到”不能当作“该 jar 没有 mixin”的证据。

### 2.5 未验证

- 两侧目标名是否一致：命名侧是无点简单名，`modcrawl` 返回内部名（可能是混淆名）；本轮没有做交集。
- 运行时行为；`modcrawl` 对嵌套 jar 的覆盖；4 条 FAILED 记录（也跑了、均返回 0 条，但不在 191 条分母内）。
- 这一对照的 26.2 重跑**未执行**：`PROGRESS.md` 第二十步记录的 v0.11.3 Windows 二进制已不在盘上（`D:\modgraph\tools\modcrawl` 是源码检出，没有 `.exe`），从记录的 release URL 重取返回 `504 Gateway Time-out`（92 字节 HTML 错误页）。因此没有 26.2 的四分类数字，也没有用 1.21.1 的数字外推。

## 3. Forbric 失败清单对照

### 3.1 来源与快照规则

- 来源：`https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader` → `MOD_TEST_FAILURES.md`，30327 字节，SHA-256 `96AE0BC7E3DFBAED6F76ABCDDC104FBF79F1DB973048E479490C9BEDEC0E3CFB`。
- 文件是多快照历史记录，不是一个清单。只解析两个具名表：
  - 2026-09-27 表：35 条 / 32 个唯一 Forbric id；
  - 2026-10-01 表：12 条 / 12 个唯一 Forbric id。
- 两表不合并。脚本断言 `listsAreDisjoint = true`：没有任何 Forbric id 同时出现在两表。
- 其余快照不猜入：2026-10-11 的 4 条“两边都失败”未列名；2026-10-03 说 12 条里 10 条仍失败，但没有逐条列名。

### 3.2 35 条表

- 能定位候选：10 条 / 8 个唯一 id；无候选：25 条 / 24 个唯一 id。
- 主桶：`NO_CANDIDATE` 25 + `LOADER_MISMATCH` 1（`lambdynamiclights`）+ `EXCLUDED_RENDER_CORE` 4（`iris`、`sodium` ×2、`sodium-extra`）+ `DEPENDENCY_SIGNAL` 5（`biomes-o-plenty`、`player-animation-library`、`fancymenu`、`fzzy-config` ×2）= 35。其余桶均为 0。
- 10 条已定位行的字段计数不互斥，不能相加：`dependencies.csv` 非 SATISFIED 10；`depends` 非空 10；版本精确相同 0（全部跨版本）；`mixinTargets` 非空 7；`conflicts.csv` HIGH/MEDIUM 6；`supportPolicy=EXCLUDED` 4；`nestedJars` 非空 1；`status=FAILED` 0；加载器匹配 9 / 不匹配 1。

### 3.3 12 条表

- 能定位候选：0；无候选：12 条 / 12 个唯一 id。`NO_CANDIDATE` 12 = 12。其余桶均为 0。

### 3.4 同两张表对 26.2 批次的结果（一句量化结论）

对 36 个已采 26.2 jar 的批次，两张表（47 行 / 44 个唯一 id，不合并）落桶为 **LOCATED 39 / `NO_262` 2 / `NOT_FOUND` 3 / `NOT_IN_BATCH` 3 / 下载失败 0 = 47 行**，即 **47 行中有 39 行（36 个唯一身份）在 26.2 批次里能定位到候选**；这 39 行内的信号为：非 `SATISFIED` 依赖 36、`mixinTargets` 非空 33、HIGH/MEDIUM 冲突 27、loader 不匹配 20、`supportPolicy=EXCLUDED` 4、`status=FAILED` 0、完全无信号 0 行（字段计数非互斥，不能相加）。不在批次内的 5 个身份就是 2 个 `NO_262` 与 3 个 `NOT_FOUND`，名单见 `PROGRESS.md` 第二十八步；2026-10-11 的复测（“已修到 0”）不参与任何标签。

### 3.5 26.2 批次能说什么、不能说什么

本节早前一版写的是 `mixinTargets` = 31（把按记录的数值写成按唯一 modId）、loader 不匹配 36/36（把 `FABRIC` 与 `Fabric` 大小写敏感地比）。两处都是错的；下面的数字由 `recount_baseline_262.py` 按唯一 modId 重算，修正记在 `PROGRESS.md` 第二十九步。

- **规模。** 两张表合起来 47 行 / 44 个唯一 id；其中 39 行（36 个唯一身份）在 26.2 批次里有候选，36 个下载的 jar 全部跑过 M0（36 OK / 0 FAILED）。
- **字段分布并排**（按唯一 modId 计数，非互斥，不能相加）：

| 字段 | 可采模组基线（30 个） | Forbric 两份清单合计（39 行中 35 个唯一 modId） |
|---|---:|---:|
| 非 `SATISFIED` 依赖 | 26 | 32 |
| `mixinTargets` 非空 | 23 | 30 |
| HIGH/MEDIUM 冲突 | 17 | 24 |
| `supportPolicy=EXCLUDED` | 1 | 3 |
| `status=FAILED` | 0 | 0 |

- **这些信号在普通可采模组里同样普遍**（26/30、23/30、17/30）。本轮不能证明其中任何一项对运行时失败有区分力。“零信号 0 条”是一个行计数，不是命中率、也不是成功率，不能从中得出预测结论。
- **loader 不匹配从证据中剔除。** 39 条已定位行里 31 条带 Forbric 的 loader 标签、8 条没有；31 条已标注行中 17 条与分类器的 `loaderFamily` 一致、**14 条不一致**（大小写无关比较）。Forbric 标的是失败构建的 loader，而本批按**文件级 `loaders`** 选文件，所以这是标注差异而不是失败信号，因此不上表。
- **26.2 的 Mixin 四分类（modcrawl，本地）：** BOTH 29 / `NAMING_ONLY` 2 / `MODCRAWL_ONLY` 1 / `NEITHER` 4 = 36 条记录；唯一 modId 为 28 / 2 / 1 / 4（不能相加）。唯一那条命名缺口（`entitycrosshair`）被 modcrawl 补上 2 个目标，所以 1 补上 1、剩 0。
- **断言覆盖。** C4 只验了 `summary.csv` 行数；现在新增 C6，验 `conflicts.csv` 行数与“唯一 modId 的两两组合”一致（两批：30 → 435、35 → 595）。这就是为什么 36 条记录正确的值是 595 而不是 C(36,2) = 630。

## 4. 这些数字可以怎么读、不能怎么读

1. **版本错位不是预测失败。** Forbric 清单描述 MC 26.2，modgraph 数据集是 1.21.1。`NO_CANDIDATE` 的意思是“1.21.1 数据集里没有这个身份的记录”——版本/身份错位，不是“modgraph 预测这个模组会失败”，也不是“modgraph 在 26.2 上也会漏掉它”。本轮没有分析 26.2 数据集。
2. **`EXCLUDED` 是支持策略免责声明。** 它不是“该模组坏了”的结论，也不能解释运行时崩溃。
3. **非 `SATISFIED` 的依赖行是 1.21.1 的声明性判定。** 它可能是真缺口、版本区间造成的偏差，或已知误报（见 `PROGRESS.md` 的连字符版本边界）；它不是 Forbric 失败运行时原因的证据。
4. **在 `STATIC_ANALYSIS` 模式下，工具只做声明性准入检查。** 不预测 Mixin transform、类加载、模组加载顺序或运行时行为，也不保证稳定。见 `SPEC.md` §0。
5. **`modcrawl` 对照也不是正确性证明。** 它只说明在这个工具/版本下，命名规则没有产出哪些目标，并带上下文的嵌套 jar 与命名边界。
6. **0 表示“在此口径下为 0”。** 12 条表里的 `NO_CANDIDATE` 12 表示 12 条中没有 1 条能在 1.21.1 数据集定位；不表示 0 个失败、0 个 mixin 或“已验证有效”。

## 5. 措辞要求

- 禁用：“已验证有效”“预测成功”“覆盖率提升”“证明”“根因”。
- 改用：“静态对照”“在此口径下”“未验证”“不能证明”“与基线一致/不一致”。
- 引用计数时必须同时给出分母与定义（记录数 vs 唯一 id 数；行 vs 对；声明数 vs 去重边数）。

## 6. 复现

- `python verify_mixin_coverage.py --modcrawl <modcrawl.exe 路径>` —— 对主产物只读，写入 `mixin-coverage/`。
- `python verify_forbric_failures.py --expected-sha256 96AE0BC7E3DFBAED6F76ABCDDC104FBF79F1DB973048E479490C9BEDEC0E3CFB` —— 对主产物只读，写入 `forbric-comparison/`。
- 两个脚本断言不成立都会以非 0 退出。工具二进制、来源 Markdown 与对照产物文件的完整哈希在 `PROGRESS.md` 第二十步 / 第二十一步。

## 7. loader 家族覆盖：1.21.1 基线 vs 26.2 首次真实触发

- **在 1.21.1 基线下**，`QUILT` = 0、`FORGE` = 0、`OTHER_UNIVERSE` = 0 条记录。因此 §3.6.2 这两条分支只由构造测试覆盖，从未被真实数据命中。任何“FORGE / QUILT 为 0”的说法都必须带这个限定：它描述的是 1.21.1 数据集，不是工具本身。
- **在 26.2 批次（30 个 jar）中**，`loaderFamily` 为 FABRIC 19 / NEOFORGE 10 / FORGE 1——FORGE 分支首次被真实数据命中。
- 这条记录是 `carryon` 2.11.0，文件 `carryon-forge-26.2-2.11.0.jar`。它按文件名被放在了 NeoForge 一侧，但该 jar 内没有 `META-INF/neoforge.mods.toml`，只有 `META-INF/mods.toml`，因此按 §2.2 的回退规则判为 `FORGE`。`META-INF/mods.toml` 摘录（省略了展示字段与 `ordering` / `side` 行；完整原文见 `PROGRESS.md` 第二十七步）：

```toml
modLoader="javafml"
loaderVersion="[65,)"
license="GNU LGPLv3"
issueTrackerURL="https://github.com/Tschipp/CarryOn/issues"

[[mods]]
    modId="carryon"
    version="2.11.0"
    displayName="Carry On"

authors="Tschipp, PurpliciousCow"

[[dependencies.carryon]]
    modId="forge"
    mandatory=true
    versionRange="[65,)"

[[dependencies.carryon]]
    modId="minecraft"
    mandatory=true
    versionRange="[26.2, 27)"
```

- 这**不能**说明 FORGE 分支在一般意义上是对的：只有一条记录、一个版本、一个 loader 家族。`QUILT` 在两个数据集里都仍未被真实数据触发。
- **选文件规则（v1.1 新增）：** 采集必须按**文件级 `loaders`** 选文件，不能按项目名、也不能只取项目的 primary 文件。第二十六步取的是每个项目 `26.2` 版本的 primary 文件；`carryon` 的 primary 文件正是 **Forge 构建**（`carryon-forge-26.2-2.11.0.jar`）。所以 FORGE 分支被触发是我们选错了变体，**不是分类器误判**：那个 jar 确实只有 `META-INF/mods.toml`，§2.2 的回退规则因此正确地给出 `FORGE`；同一 mod 的 NeoForge 构建会被判 NEOFORGE。第二十八步已按文件级规则采集（归一化规则在 `dataset-262/forbric/normalization.md`，选文件逻辑在 `fetch_262_forbric.py`）。

## 8. 数据处理与再分发

- 模组 jar **永不入库、永不再分发**。它们只存在 `dataset-262/` 下，该目录已列入 `.gitignore`；任何被跟踪的文件里不包含任何 jar 字节。
- 对照清单每个 jar 只保留三类派生事实：下载 **URL**、**哈希**（本地实测 SHA-256；来源发布的 SHA-512），以及**原样引用的许可标识**（SPDX id 或 `LicenseRef-*`）。许可标识是引用，不是授权分析：本仓库不主张任何 jar 可依该标识再分发。
- 第二十六步记录：30 个本地 jar 的 SHA-512 全部与来源发布值一致，每个本地 SHA-256 也与清单条目一致。一旦出现哈希不一致，整批应废弃而不是部分使用。
