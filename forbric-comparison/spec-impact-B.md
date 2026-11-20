# 分域后旧边界措辞影响面（B 路）

`producedBy: impact_b`

## 1. 范围与扫描口径

本报告只读以下 6 个文件，不读取 `src/`、A 路当前草稿或 Forbric / Kilt 源码：

| 文件 | 行数 | 本次快照 SHA-256 |
|---|---:|---|
| `AGENTS.md` | 95 | `73F3F53138EB521EEF1EF3AA80B427DC49EB651D545B3B6224B52F43A5DFDC08` |
| `BOUNDARIES.md` | 157 | `1749D3269C834E7C1A7F1CCDBCA5E50809DB6EC58DBF013338F794F25BA640FA` |
| `BOUNDARIES.zh-CN.md` | 157 | `0E7FBC3E738DA1108CB1E275F4BB81A02AFA4244F6D77D0B3385CB684166C804` |
| `README.md` | 539 | `D407C1D1D52E410406972648BF52FABF00364291098D1294B7079DA8015AB1E4` |
| `README.zh-CN.md` | 458 | `3765AC04BD6DD02B835D9F6FE865DE462CA2CCAF209D67E332F28FCF6B39389E` |
| `PROGRESS.md` | 810 | `BBDEA2C34D18F066372CBE6E7E244DC86765818B1FD57EC274C05594FE03D967` |

扫描目标不是只做字面 `grep`。除用户指定的五组说法外，还把与它们直接同义、或会被“静态 / 加载器实验”分域改变真值的近义句列入：

- `H`：不启动 / 不能启动 Minecraft；
- `L`：不加载类，或不能加载任何模组；
- `P`：不解析 / 不读取 class 内容或字节码；
- `M`：不处理 / 不执行 / 不预测 Mixin transform，或不读 `@Mixin` 注解；
- `B`：不改写字节码。

同一行或同一跨行句子只记一个“命中行”主项，但可带多个标签。跨行原句引用时只把换行还原成空格，不改词、不改标点。

结论边界：

- `MUST_QUALIFY`：分域后需要加“静态分析模式下”或等价限定；
- `KEEP`：已经明确限定在 M0 / M0.5 / 特定脚本 / 历史步骤，或所描述的两轮离线对照本身；
- `INVALID`：即使用静态分析模式限定也不能保留的原文。

## 2. 命中清单

### 2.1 `AGENTS.md`

#### B-001 — `MUST_QUALIFY` — `H,L,P,M,B` — `AGENTS.md:17`

原句：

> 不启动 Minecraft、不加载类、不解析 class 文件、不处理 Mixin、不改写字节码。

分域后：这是无模式限定的全局禁令。若新增“加载器实验模式”，必须在条目上明确写成“静态分析模式下……”，否则同一份常驻约束会同时许可和禁止加载器实验。

### 2.2 `README.md`

#### B-002 — `KEEP` — `P` — `README.md:73-75`

原句：

> On top of that, M0 additionally produces an **invasiveness rating**: without parsing any bytecode, it uses the descriptors and metadata inside the zip to identify which mods operate at the render-pipeline level, the bytecode level, or are pure API consumers — and warns about conflict risk *before* launch.

分域后：主语是 M0，保留；不能外推成整个双域项目的所有模式都不读字节码。

#### B-003 — `MUST_QUALIFY` — `H,L,B` — `README.md:101-102`

原句：

> `modgraph` **is not currently a mod loader**. It does not start Minecraft, does not load classes, does not rewrite bytecode.

分域后：这是项目级身份与行为陈述。静态模式仍可保留前半句，但加载器实验模式启用后，必须加“static analysis mode”限定；否则会与新运行域直接冲突。

#### B-004 — `KEEP` — `H,L` — `README.md:133`

原句：

> ⚠️ **M0 / M0.5: offline static analysis. It cannot load any mod and cannot start Minecraft.**

分域后：已明确限定为 M0 / M0.5，保持；它不是整个项目的永久禁令。

#### B-005 — `KEEP` — `P` — `README.md:326`

原句：

> InvasivenessAnalyzer invasiveness rating + mixin target inference (descriptor level only, no bytecode)

分域后：这是当前 M0 分析流程图中的组件描述，保持。

#### B-006 — `MUST_QUALIFY` — `P` — `README.md:358`

原句：

> Scanning is **descriptor-level (L1) only**: it reads zip entry names and metadata text, never class content.

分域后：句子没有显式写 M0，容易被读成整个项目的扫描器永不读 class。应加“static analysis mode”或 M0 限定。

#### B-007 — `KEEP` — `M` — `README.md:399-400`

原句：

> Mixin target inference follows naming conventions; it does not read the `@Mixin` annotation, so it both misses targets and produces false ones — `mixinTargets` is a static hint, not a fact

分域后：这是 M0 已知局限，保持；不能改成加载器实验模式的运行断言。

### 2.3 `README.zh-CN.md`

#### B-008 — `KEEP` — `P` — `README.zh-CN.md:70`

原句：

> 在此之上，M0 额外产出一项**侵入深度评级**：在不解析字节码的前提下，依据 zip 内的描述符与元数据，识别哪些模组属于渲染管线级、字节码级或纯 API 级改造，并在启动前给出冲突风险预警。

分域后：主语是 M0，保持。

#### B-009 — `MUST_QUALIFY` — `H,L,B` — `README.zh-CN.md:95`

原句：

> `modgraph` **当前不是模组加载器**。它不启动 Minecraft，不加载类，不改写字节码。

分域后：同 B-003。项目级“当前不是”在加载器实验模式启用后会歧义，必须加运行域限定。

#### B-010 — `KEEP` — `H,L` — `README.zh-CN.md:121`

原句：

> ⚠️ **M0 / M0.5：离线静态分析。不能加载任何模组，不能启动 Minecraft。**

分域后：已限定为 M0 / M0.5，保持。

#### B-011 — `KEEP` — `P` — `README.zh-CN.md:287`

原句：

> InvasivenessAnalyzer 侵入深度评级 + Mixin 目标推断（仅描述符级，不解析字节码）

分域后：当前 M0 分析流程描述，保持。

#### B-012 — `MUST_QUALIFY` — `P` — `README.zh-CN.md:317`

原句：

> 扫描仅限**描述符级**：读取 zip 内文件名与元数据文本，不解析 class 内容。class 常量池级扫描属于后续阶段。

分域后：同 B-006，需要模式限定。

#### B-013 — `KEEP` — `M` — `README.zh-CN.md:346`

原句：

> Mixin 目标推断只按命名约定，不读 `@Mixin` 注解，既会漏报也会误报，`mixinTargets` 只能当静态线索

分域后：M0 已知局限，保持。

### 2.4 `BOUNDARIES.md`

#### B-014 — `KEEP` — `L` — `BOUNDARIES.md:14`

原句：

> Both are static, offline comparisons. Neither is a runtime test. Neither produces an install order, a compatibility verdict, or a prediction of class loading.

分域后：主语是两个既有外部对照，保持；不能外推到将来的加载器实验。

#### B-015 — `MUST_QUALIFY` — `L,M` — `BOUNDARIES.md:104`

原句：

> The tool does only declarative admission checks. It does not predict Mixin transforms, class loading, mod loading order, or runtime behavior, and it does not guarantee stability.

分域后：`The tool does only...` 是无模式限定的总称；加入加载器实验域后必须限定为静态分析工具 / 静态模式。

### 2.5 `BOUNDARIES.zh-CN.md`

#### B-016 — `KEEP` — `L` — `BOUNDARIES.zh-CN.md:14`

原句：

> 两轮都是离线静态对照，不是运行时测试；都不产出安装顺序、兼容性结论或类加载预测。

分域后：主语是两轮既有对照，保持。

#### B-017 — `MUST_QUALIFY` — `L,M` — `BOUNDARIES.zh-CN.md:104`

原句：

> 工具只做声明性准入检查。不预测 Mixin transform、类加载、模组加载顺序或运行时行为，也不保证稳定。

分域后：同 B-015，需要静态模式限定。

### 2.6 `PROGRESS.md`

#### B-018 — `KEEP` — `H` — `PROGRESS.md:5`

原句摘录（同一行内连续原文）：

> 本轮没有修改 SPEC、`src/`、六个既有产物、徽章、M0–M1.5 数字、两版 README、两版 BOUNDARIES、LICENSE 或 AGENTS，没有下载 jar、没有启动 Minecraft、没有跑 `gradle test`，没有 commit / push / PR，也没有写实现代码。

分域后：这是第三十一步的历史执行记录，保持。

#### B-019 — `KEEP` — `H,L,P,M,B` — `PROGRESS.md:7`

原句摘录（同一行内连续原文）：

> B 逐字定位 `AGENTS.md:17` 的“不启动 Minecraft、不加载类、不解析 class 文件、不处理 Mixin、不改写字节码”、`SPEC.md:15` 的英文对应句

分域后：历史记录中的逐字引用，保持；原文本身一旦改分域，新的 §0 变更应由新步骤记录，不回写历史。

#### B-020 — `KEEP` — `P` — `PROGRESS.md:65`

原句摘录（同一行内连续原文）：

> 脚本只读 `candidates.jsonl`、mixin 配置 JSON 与 jar 条目清单，不解析 class 文件、不写任何已有产物

分域后：只描述 `verify_mixin_coverage.py`，保持。

#### B-021 — `KEEP` — `H,L,P` — `PROGRESS.md:123`

原句：

> 第十二步只做 M0.5，没有启动 Minecraft、没有加载类、没有解析 class 文件、没有引入任何新依赖。

分域后：该步历史范围，保持。

#### B-022 — `KEEP` — `H,L,P` — `PROGRESS.md:172`

原句：

> 第六步没有启动 Minecraft、没有加载类、没有解析 class 文件、没有引入 ASM 或 Mixin 依赖；只读取 zip 条目名、元数据文本和明文 mixin 配置文件。

分域后：该步历史范围，保持。

#### B-023 — `KEEP` — `P` — `PROGRESS.md:213`

原句：

> 目标推断只基于命名约定，没有读 `@Mixin` 注解、没有读 class 文件。

分域后：M0 特定推断实现的历史描述，保持。

#### B-024 — `KEEP` — `P` — `PROGRESS.md:603`

原句摘录（同一行内连续原文）：

> 脚本只读 candidates / 配置 / jar 条目，不解析 class，不写已有产物

分域后：特定脚本行为，保持。

#### B-025 — `KEEP` — `P` — `PROGRESS.md:666`

原句摘录（同一行内连续原文）：

> 所以分析器只对“已经声明的 config 路径”做定向嵌套查找，不递归扫描其他内层 jar，也不读 class。

分域后：当前 M0 分析器的历史边界，保持。

#### B-026 — `KEEP` — `H` — `PROGRESS.md:756`

原句：

> 该命令只重新定位两版 SPEC 的 §0 / §5 边界，本轮没有执行。**待裁决：是否改 `SPEC.md` §0 并同步 `SPEC.zh-CN.md`；用户单独点头前不写替代条款、不改 AGENTS、不进入 M2、不实现 ClassLoader、不启动 Minecraft。**

分域后：这是第三十一步当时待裁决状态的记录，保持；第三十二步及之后的用户裁决不能回写覆盖这一步。

## 3. 影响面闭合

### 3.1 文件与行数

- 扫描文件：`6`。
- 有命中的文件：`6 / 6`。
- 命中行主项：

| 文件 | 命中行主项 |
|---|---:|
| `AGENTS.md` | 1 |
| `README.md` | 6 |
| `README.zh-CN.md` | 6 |
| `BOUNDARIES.md` | 2 |
| `BOUNDARIES.zh-CN.md` | 2 |
| `PROGRESS.md` | 9 |
| **合计** | **26** |

### 3.2 处置分桶

| 处置 | 数量 | 命中项 |
|---|---:|---|
| `MUST_QUALIFY` | 7 | B-001、B-003、B-006、B-009、B-012、B-015、B-017 |
| `KEEP` | 19 | B-002、B-004、B-005、B-007、B-008、B-010、B-011、B-013、B-014、B-016、B-018、B-019、B-020、B-021、B-022、B-023、B-024、B-025、B-026 |
| `INVALID` | 0 | 无 |
| **合计** | **26** | **7 + 19 + 0 = 26** |

`INVALID = 0` 只表示在当前“草稿尚未生效”的时点，原文都还能通过“静态模式”限定或按历史范围保留；它不表示这些原文分域后可以不改。若 B-001、B-003、B-006、B-009、B-012、B-015、B-017 在加载器实验模式生效后仍保持全局措辞，就会与新运行域冲突。

### 3.3 标签计数

| 标签 | 次数 |
|---|---:|
| `H` 不启动 / 不能启动 Minecraft | 10 |
| `L` 不加载类 / 不能加载任何模组 | 12 |
| `P` 不解析 / 不读取 class 或字节码 | 14 |
| `M` 不处理 / transform Mixin 或读取 `@Mixin` | 6 |
| `B` 不改写字节码 | 4 |
| **标签总次数** | **46** |

注意：`46` 是同一命中行可能有多个标签后的标签次数，不是文件数、行数或独立结论数；不能与 `26` 相加。

## 4. 完整依赖清单的可追踪性问题

### 4.1 用户指定待补项

**TR-01（待补）**：在 `AGENTS.md` 与 `SPEC.md` §0 / §5 这组规范文件中，完整依赖禁令在 `AGENTS.md:18` 有原文：

> 不引入 Minecraft / Fabric Loader / Forge / NeoForge / Mixin / ASM 依赖。

本次只读复核固定 `SPEC.md` §0 为第 13–47 行、§5 为第 919–948 行。该范围内没有这句的逐字版本，也没有完整的同类清单。§0 中相邻的原文是：

> - Any ClassLoader, JVM agent, or bytecode manipulation (ASM / Mixin / Tiny Remapper)
> - Any Minecraft launch, download or patching logic

这两条与 `AGENTS.md:18` 相关，但不构成完整的六项依赖清单。若 §0 草稿要成为可审计的运行域基础，应决定在哪里正式引用或复述该清单。

### 4.2 必须纠正的绝对措辞

如果把“完整依赖清单仅见于 `AGENTS.md:18`”理解为“全仓库文档中只在 `AGENTS.md:18` 出现”，该判断与实文件不自洽。原文如下，本报告不替用户选哪一个作为权威来源：

`README.md:342-343`：

> **Does not depend on**: Minecraft, Fabric Loader, Forge, NeoForge, Mixin, ASM.
> This stage is pure offline text parsing; introducing any of the above is a violation.

`README.zh-CN.md:303-304`：

> **不依赖**：Minecraft、Fabric Loader、Forge、NeoForge、Mixin、ASM。
> 本阶段是纯离线文本解析，任何上述依赖的引入都视为违规。

`PROGRESS.md:7` 还逐字引用了 AGENTS 的完整中文清单。此外：

- `README.md:514`：`No Minecraft / loader-related dependency was introduced`
- `README.zh-CN.md:438`：`未引入 Minecraft / 加载器相关依赖`
- `README.zh-CN.md:449`：`本项目的 M0 阶段不依赖其中任何一项。`

因此更准确的可追踪性项是：

**TR-02（待补，口径更正）**：完整六项清单至少在 `AGENTS.md:18`、`README.md:342`、`README.zh-CN.md:303` 出现；`SPEC.md` §0 / §5 没有逐字落位。若分域允许加载器实验引用原先禁止的依赖，AGENTS 与两版 README 都需要未来的双版 / 单版同步方案；本报告不决定谁优先，也不改任何文件。

## 5. 交接报告

### 改了哪些文件

- 新增：`forbric-comparison/spec-impact-B.md`
- 修改：无
- 删除：无
- 未改 `SPEC.md`、`SPEC.zh-CN.md`、`src/`、两版 README、两版 BOUNDARIES、`AGENTS.md`、`PROGRESS.md`、`LICENSE`、既有产物或徽章。

### 测试数字

没跑。原因是本路只做 Markdown 只读扫描；没有执行 `gradle test`，也没有启动 Minecraft、运行项目、下载或触碰 jar。

### 关键数字

- 扫描文件 `6` 个；六份输入快照文件行数见 §1，哈希见 §1。
- 命中行主项 `26`；处置分桶 `MUST_QUALIFY 7 / KEEP 19 / INVALID 0`，闭合为 `7 + 19 + 0 = 26`。
- 标签次数 `46`：`H 10 / L 12 / P 14 / M 6 / B 4`。
- `SPEC.md` §0 / §5 中完整依赖清单的逐字命中为 `0`。
- 本轮没有实现产物数字、没有基线重算，三路已有报告哈希未变化。

### 违反哪条禁令

无。未启动 Minecraft、未加载类、未解析 class、未处理 Mixin、未改写字节码；未引入 MC / 加载器依赖；未输出安装 / 加载 / 拓扑顺序；未自动兼容、修复或选择版本；未改 Forbric / Kilt；未改 LICENSE 或既有产物；未执行 git 写操作。

### 未验证/已知局限

- 行号固定在本报告开头所列六份文件快照；主智能体随后更新 `PROGRESS.md` 时，该文件后续行号会平移。
- “全部引用”采用 §1 的显式语义扩展；未把一般性的“不能预测运行时冲突”全部算入五组边界原句，只列入会直接受新运行域影响的相邻行。
- “完整依赖清单仅见 AGENTS”按仓库文档核对后不成立；TR-02 已贴原文，必须由主智能体在总报告和后续裁决中保留这处修正。
- 未生成或评价 A 路草稿，未修改任何规范。
