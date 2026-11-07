# BOUNDARIES — 外部对照及其解释边界

状态：v1.0（2026-11-07）。本文件不属于 SPEC 验收标准。它固化两轮外部对照的实测结果，以及必须与这些数字一起传播的解释边界。以下计数全部取自 `PROGRESS.md`（第二十步与第二十一步）；`PROGRESS.md` 变化时，本文件两版必须同步。

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

## 4. 这些数字可以怎么读、不能怎么读

1. **版本错位不是预测失败。** Forbric 清单描述 MC 26.2，modgraph 数据集是 1.21.1。`NO_CANDIDATE` 的意思是“1.21.1 数据集里没有这个身份的记录”——版本/身份错位，不是“modgraph 预测这个模组会失败”，也不是“modgraph 在 26.2 上也会漏掉它”。本轮没有分析 26.2 数据集。
2. **`EXCLUDED` 是支持策略免责声明。** 它不是“该模组坏了”的结论，也不能解释运行时崩溃。
3. **非 `SATISFIED` 的依赖行是 1.21.1 的声明性判定。** 它可能是真缺口、版本区间造成的偏差，或已知误报（见 `PROGRESS.md` 的连字符版本边界）；它不是 Forbric 失败运行时原因的证据。
4. **工具只做声明性准入检查。** 不预测 Mixin transform、类加载、模组加载顺序或运行时行为，也不保证稳定。见 `SPEC.md` §0。
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
