<!-- producedBy: draft_a -->
<!-- DRAFT ONLY: this file does not amend SPEC.zh-CN.md and is ineffective until separately approved. -->

### 0.1 运行域边界：静态分析与加载器实验（草稿）

**状态：** 这是拟插入 §0 的草稿，不是已生效的规范条款。启用本草案需用户单独批准，并同步写入两个语言版本；本草案不授予实现、下载或启动权限。

每次运行和报告必须恰好声明一个 `operating_mode`：`STATIC_ANALYSIS` 或 `LOADER_EXPERIMENT`。两个模式互斥，不得共用同一分母，不得把两种模式合入同一结果集。切换模式必须产生新的运行、新的冻结 cohort 和新的不可变证据；既有结果不得重新贴标签。

#### 1. 静态分析模式

`STATIC_ANALYSIS` 是默认模式，保留当前项目边界。它只读取获准的元数据与结构证据，报告静态结果，不作运行时结论。以下四组受保护原文逐字保留；引用标记只是 Markdown 展示，不属于受保护正文。

来源 `AGENTS.md:17`：
> - 不启动 Minecraft、不加载类、不解析 class 文件、不处理 Mixin、不改写字节码。

来源 `SPEC.md:15`：
> M0 **does not start Minecraft, does not load classes, does not process mixins, does not rewrite bytecode**.

来源 `SPEC.md:19-20`：
> M1 (§3.6) builds a dependency graph on top of those records and detects cycles. It does not start Minecraft
> either, does not load classes, does not read class files, and **does not output an install order** (§3.6.5).

来源 `AGENTS.md:18`：
> - 不引入 Minecraft / Fabric Loader / Forge / NeoForge / Mixin / ASM 依赖。

本节生效时，以上四组原文必须逐字节保持不变。不同能力或依赖要求只能写入另经批准的条款，不得由“加载器实验”标签推导出来。

#### 2. 加载器实验模式

`LOADER_EXPERIMENT` 是排队中的模式，仅适用于受控环境中的一个冻结 cohort。本草案不会启用该模式，其证据与所有静态分析结果相互独立。

- cohort 在执行前按 `cohort_id`、有序 artifact 身份和 artifact SHA-256 冻结。
- 受控环境由实现锁、JDK 与加载器事实、命令摘要、地区、时区和已记录的环境摘要固定。
- jar 不进入仓库、不提交、不再分发；运行期间输入只读。
- 任何日志级别、报告字段或文档都不得输出安装顺序、加载顺序、拓扑顺序、分层或“先装这个”建议。
- 自动兼容、自动修复、自动版本选择和候选推荐仍然禁止。
- 长期运行时错误如何记账、如何与 §5 的“每次解析尝试至多一条 ERROR”规则分账，仍待用户裁决；该裁决前不得把加载器结果称为完整。

启用本 §0.1 草案本身不授权启动 Minecraft、加载类、处理 Mixin、改写字节码或增加依赖。受控启动 Minecraft 必须另行明确批准；在此之前，任何依赖该启动才能得到的结果都记为 `BOUNDARY_BLOCKED`。

#### 3. 证据与失败记账

两个模式都先冻结分母，再执行。注入、跳过、崩溃、超时、能力不支持、输出不可解析、oracle 标签缺失和边界阻断都必须作为独立状态保留。单条 artifact 的失败不得降成 WARN、省略或静默继续。

静态解析继续遵守 §5 现有的“单个 jar / 每次解析尝试至多一条 ERROR”规则。`LOADER_EXPERIMENT` 的运行时事件身份、去重、保留期和长期聚合仍需等待上述分账裁决；裁决前不得并入静态解析错误计数，也不得作为成功的省略项报告。

#### 4. 对照指标

准确率只使用 `N_answered` 作分母：
`accuracy = CORRECT / N_answered`，其中 `N_answered = CORRECT + INCORRECT`。

`CORRECT` 与 `INCORRECT` 恰好吃尽准确率分母。`UNSUPPORTED`、`NO_PARSEABLE_OUTPUT`、`TOOL_FAILURE`、`TIMEOUT`、`NOT_ATTEMPTED`、`BOUNDARY_BLOCKED` 与 `ORACLE_MISSING` 各自单列，必须保持可见；不得吞入任一准确率类别。

- 4 桶正确性分解为 `BOTH_CORRECT + PRE_ONLY_CORRECT + POST_ONLY_CORRECT + BOTH_INCORRECT = N_common_3`。
- 8 模式分解统计原版 Forbric、替换前与替换后的八种正确性模式；八个格子之和同样等于 `N_common_3`。
- 4 桶分解与 8 模式分解是同一个冻结交集上的两套正交切分，不得相加、混成一个总数，也不得当成独立样本集。
- 原版 Forbric 结果是外部基线，不是 oracle。与它一致不等于语义正确。

#### 5. 报告边界

每个率都必须写明分子、分母、cohort、实现 revision 和 oracle revision。静态一致不得写成运行时保证。证据缺失、字段不支持与运行时边界阻断必须明确保留，不得改写成零。

