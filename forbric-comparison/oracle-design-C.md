# 同一批模组 × 三个实现的对照口径（C 路）

producedBy: lane_c

状态：方案草案，未运行、未产数、未实现。本文只定义未来离线、静态元数据对照如何冻结输入、记录结果、计算分母和写断言；不构成运行时、Mixin transform、类加载或兼容性结论。

## 1. 范围与不做什么

对照对象固定为三个实现：

| 标识 | 含义 | 作用 |
|---|---|---|
| `FORBRIC_ORIGINAL` | Forbric 原版外部实现 | 外部基线，只读 |
| `REPLACEMENT_PRE` | 替换前实现 | 与替换后做同口径前后对照 |
| `REPLACEMENT_POST` | 替换后实现 | 与替换前做同口径前后对照 |

三个实现回答同一批冻结 jar 的同一组静态问题：发现了什么模组、解析出哪些规范化元数据、每条依赖声明落在哪个结果桶。原版 Forbric 的输出只是基线，不能自动充当真值；三者都必须与独立冻结的 oracle 比较。

本轮和后续执行都应满足：

- 不启动 Minecraft，不加载类，不解析 class 文件，不处理 Mixin，不改写字节码；
- 不引入 Minecraft / Fabric Loader / Forge / NeoForge / Mixin / ASM 依赖；
- 不输出安装、加载或拓扑顺序；
- 不下载 jar；输入只来自已获准、已冻结的本地文件；
- jar 不入库、不分发，对照文件只留路径引用、哈希和派生结果；
- 如某实现只能通过启动 Minecraft 或加载类才能产出字段，该字段记 `BOUNDARY_BLOCKED`，不得用其他实现的结果代填。

## 2. 批次冻结：先证明三次输入逐字节相同

### 2.1 评测单位

主评测单位是 **jar 物理文件**，不是项目名，也不是合并后的 modId。定义：

```text
artifact_key = SHA-256(jar bytes)
```

- 每个 `artifact_key` 在冻结核心里恰好出现一次；
- 同一路径别名、多个下载 URL 或同一 modId 的 Fabric / NeoForge 变体不能把主分母变大或变小；
- 若同一字节出现在两个路径，保留一个评测单位，并记录所有路径别名；
- “唯一 modId”只允许作为另列的报告层级，不能替换 jar 级主分母；
- 依赖行、冲突对等后续指标各自使用自己的分母，不能把 jar 数、depId 数和对数相加。

### 2.2 `cohort-manifest` 必填字段

| 字段 | 定义 |
|---|---|
| `cohort_id` | 由规范、manifest 内容和 oracle revision 派生的稳定标识 |
| `manifest_order` | 冻结、随机化后不再变化的正整数 |
| `artifact_key` | jar 字节的 SHA-256 |
| `byte_length` | jar 字节数 |
| `source_identity` | 项目/发布标识；原样保存，不据此重选文件 |
| `file_level_loader` | 来源声明的文件级 loader；不靠文件名猜 |
| `target_game_version` | 三个实现共同的对照目标版本 |
| `environment_facts` | minecraft、loader、java 等声明事实的完整冻结快照 |
| `content_license_raw` | 来源许可标识原值；只作引用，不作授权判断 |
| `source_evidence_ref` | 来源记录或清单中的证据定位 |

同时在批次头冻结：

- 三个实现的源码仓库 URL、commit SHA、构建产物 SHA-256；
- adapter、normalizer、runner 的 SHA-256 与版本；
- JDK、OS、locale、时区、工作目录布局和环境变量白名单；
- 每个实现的命令模板哈希；
- oracle revision、schema revision、canonicalization revision；
- 随机化的执行顺序 seed 与实际顺序哈希。

### 2.3 开工前硬断言

```text
A0  cohort_id 在本次对照中唯一
A1  count(manifest rows) == count(unique artifact_key)
A2  对每个 artifact_key：本地实际 SHA-256 == manifest SHA-256
A3  对每个 artifact_key：本地 byte_length == manifest byte_length
A4  manifest_order 是恰好 1..N 的排列
A5  三个实现读的是同一个只读 cohort 目录
A6  target_game_version、environment_facts、normalizer revision 三者对三实现相同
A7  任一 A0–A6 失败 => 整批无效，停止，不保留部分结果
```

哈希不符时整批废弃，不能只跳过坏文件；否则分母会变化。输入目录必须对执行器只读，运行前后各重算一次 manifest 哈希。

## 3. 每条记录统一 schema

### 3.1 运行级记录 `implementation-runs`

| 字段 | 说明 |
|---|---|
| `run_id` | 一次冷启动的唯一 ID |
| `implementation_id` | 三个固定标识之一 |
| `implementation_revision` | commit / release / build 指纹 |
| `cohort_id` | 输入批次 |
| `command_hash` | 实际命令模板哈希 |
| `normalizer_id` | 采用的规范化投影版本 |
| `started_at`, `ended_at` | 时间；只作审计，不参与正确性 |
| `process_exit_code` | 进程退出码 |
| `run_outcome` | `COMPLETED` / `CRASHED` / `TIMEOUT` / `NOT_ATTEMPTED` / `BOUNDARY_BLOCKED` |
| `stdout_sha256`, `stderr_sha256` | 原始日志哈希 |
| `raw_output_ref`, `raw_output_sha256` | 原始输出位置与哈希 |
| `normalized_output_sha256` | 规范化输出哈希 |

进程退出码 0 只表示进程完成；是否产出可解析结果、是否支持该字段、是否语义正确，都是另外的轴。

### 3.2 每个 jar 的答案 `item-results`

| 字段组 | 字段 |
|---|---|
| 关联 | `run_id`, `artifact_key`, `manifest_order` |
| 能力 | `capability_state`: `SUPPORTED` / `UNSUPPORTED` / `UNKNOWN` |
| 技术状态 | `execution_outcome`: `COMPLETED` / `CRASHED` / `TIMEOUT` / `NOT_ATTEMPTED` / `BOUNDARY_BLOCKED` |
| 输出状态 | `output_state`: `PARSEABLE` / `MALFORMED` / `ABSENT` |
| 发现 | `discovery_answer`: `FOUND` / `REJECTED` / `NO_ANSWER` / `UNSUPPORTED` |
| 身份 | `mod_id_raw`, `mod_id_normalized`, `mod_version_raw`, `mod_version_normalized` |
| 元数据 | `metadata_format`, `metadata_state`, 规范化必填字段的答案 |
| 依赖摘要 | `expected_dep_count`, `emitted_dep_count`, `extra_dep_count` |
| 证据 | `evidence_ref`；只指向冻结输入中的元数据位置，不复制 jar |
| 结果 | `oracle_item_id`, `semantic_result`: `MATCH` / `MISMATCH` / `UNASSESSED` |
| 原因 | `reason_code`，来自预先冻结的枚举 |
| 辅助 | `elapsed_ms`；只作性能观察，不影响正确性 |

`UNKNOWN` 能力不得靠实际输出反推成 `SUPPORTED`。投影 adapter 必须在看到任何实现结果前冻结。

### 3.3 每条依赖声明 `dependency-results`

依赖不能只做模组级布尔值。每条预期声明和实际声明都要有稳定键：

```text
decl_key = (
  artifact_key,
  metadata_source,
  mod_identity,
  dep_kind,
  dep_id_raw,
  dep_id_normalized,
  constraint_raw,
  constraint_normalized,
  occurrence_rank
)
```

`occurrence_rank` 只用于区分完全相同的重复声明；先按上述字段稳定排序，再从 1 编号。比较的是多重集，不依赖实现输出顺序。

| 字段 | 说明 |
|---|---|
| `dep_row_id` | 结果行自身 ID |
| `artifact_key` | 所属 jar |
| `dep_kind` | `depends` / `recommends` / `breaks` / 其他冻结枚举 |
| `dep_id_raw` | 原始标识 |
| `dep_id_normalized` | 规范化标识 |
| `constraint_raw` | 原始版本约束 |
| `constraint_normalized` | 规范化版本约束 |
| `mandatory` | 原声明是否必选 |
| `resolution_status` | `SATISFIED` / `MISSING_ID` / `VERSION_MISMATCH` / `AMBIGUOUS_ALIAS` / `BREAKS_VIOLATED` / `UNPARSEABLE` / `UNSUPPORTED` |
| `evidence_ref` | 元数据证据定位 |
| `semantic_result` | `MATCH` / `MISMATCH` / `UNASSESSED` |

规范化只允许执行预先冻结、可审查的等价变换。若规范化规则改变，必须生成新 revision 并重跑三者；不能对某一个实现单独放宽。

## 4. “成功”必须拆成四种，不能合成一个漂亮数字

### 4.1 技术完成

**技术完成**只表示执行器完成了规定的进程调用，且输出能被冻结的 adapter 解析。它不表示发现、元数据或依赖判定正确。

```text
TECHNICAL_COMPLETION = execution_outcome == COMPLETED
                       and output_state == PARSEABLE
```

### 4.2 领域回答

**领域回答**表示实现明确给出了该 jar 在该字段上的答案，包括明确的 `REJECTED` 或 `UNSUPPORTED` 原因码。空输出、异常退出、仅打印日志都不能算领域回答。

```text
ANSWERED = capability_state == SUPPORTED
           and output_state == PARSEABLE
           and answer is one of the frozen answer enums
```

### 4.3 语义正确

**语义正确**要求规范化答案与预先冻结的 oracle 完全一致。对 jar 级端到端指标，至少包括：

1. 发现答案是 `FOUND` 还是 `REJECTED`；
2. `mod_id`、`version`、`loader_family`、要求的元数据状态全部一致；
3. 预期依赖多重集与实际依赖多重集一致；
4. 每条对应依赖的 `resolution_status` 一致；
5. 没有额外或缺失的声明。

```text
SEMANTIC_MATCH(item) =
  discovery_match
  and metadata_fields_match
  and dependency_multiset_match
  and every_dependency_outcome_match
```

只比较字段子集时，指标名必须写“某字段静态一致”，不能升级成“该实现成功”。

### 4.4 批次成功

只有在一个明确分母上，所有条目都 `SEMANTIC_MATCH`，才可写：

```text
批次静态语义全一致 = 该分母上的 SEMANTIC_MATCH 数 == 该分母总数
```

仍需同时写实现版本、cohort、oracle revision、分母，以及“不涵盖运行时/类加载”的限定。

### 4.5 不支持、不可评估、失败

| 状态 | 定义 | 能否算失败 |
|---|---|---|
| `UNSUPPORTED` | 该实现预先声明不提供此字段/此能力 | 不是语义错误，也不是成功；单独记能力缺口 |
| `NOT_EVALUABLE` | oracle 缺失、证据不足或裁决未完成 | 不是实现失败；不得强行标对/错 |
| `BOUNDARY_BLOCKED` | 产出答案必须启动 MC、加载类或越过本轮硬边界 | 不是实现失败；该对照在此闸门下不可完成 |
| `NO_PARSEABLE_OUTPUT` | 技术上有退出，但没有可解析答案 | 技术/输出失败 |
| `TOOL_FAILURE` | 崩溃、包装错误或不可恢复异常 | 技术失败 |
| `TIMEOUT` | 超过冻结时限 | 技术失败，保留 timeout 独立桶 |
| `MISMATCH` | 有可评估答案，但与 oracle 不一致 | 语义失败 |

“进程运行成功”“匹配原版”“三实现一致”都不能替代上表的分类。

## 5. Oracle 的独立性与冻结

### 5.1 谁是 oracle

oracle 是由冻结规则与人工核验得到的**预期静态答案**，不是任何被测实现的输出。原版 Forbric 只能作为基线；即便它早于替换实现，也不能因为“原版”就拥有真值地位。

每个 oracle item 至少包含：

- `oracle_item_id`、`artifact_key`、oracle revision；
- 期望发现结果；
- 期望 `mod_id` / `version` / `loader_family` / 元数据状态；
- 期望拒绝时的原因枚举；
- 期望依赖声明多重集和每条 `resolution_status`；
- 证据位置、规则版本；
- 裁决状态 `FINAL` / `AMBIGUOUS`；
- 两名独立核验者的标识与分歧处理记录。

### 5.2 防泄漏步骤

1. 冻结 cohort manifest 和被测实现版本；
2. 先从元数据证据和预先写定的规范生成候选 oracle；
3. 两名核验者在不看三实现输出的条件下独立标注；
4. 不一致项由第三方按已冻结规则裁决，或保留 `AMBIGUOUS`；
5. 写入 oracle 文件并计算 SHA-256；
6. 此后才运行三个实现；
7. 运行开始后 oracle 只读；任何修订都生成新 revision 和全新 cohort/run，不能原地补标签；
8. 被测输出、原版 Forbric 输出均不得用于补 oracle。

如果没有独立 oracle，只报告“在已回答交集上的两两一致性”，不得报告语义正确率、成功率或优劣结论。

## 6. 状态机与主分桶

每个 jar × 实现最终恰好落一个 `measurement_state`。先保留各技术轴供审计，再按以下优先级派生互斥终态：

```text
INPUT_CURATION_ERROR      批次冻结或哈希断言失败；应在评测前终止
BOUNDARY_BLOCKED          必须越界才能回答
NOT_ATTEMPTED             执行计划未执行
TIMEOUT                   超过冻结时限
TOOL_FAILURE              崩溃、包装器错误、不可恢复异常
NO_PARSEABLE_OUTPUT       完成但没有 adapter 可解析答案
UNSUPPORTED               实现预先声明不提供该能力
ORACLE_MISSING            oracle 缺失或仍未裁决
CORRECT                   有最终 oracle 且静态语义一致
INCORRECT                 有最终 oracle 且静态语义不一致
```

`INPUT_CURATION_ERROR > 0` 时整批不得进入评分；因此正式结果中它的数量应为 0。原始技术轴即使被终态吸收也必须留在明细文件里。

主分桶恒等式：

```text
N = CORRECT
  + INCORRECT
  + UNSUPPORTED
  + NO_PARSEABLE_OUTPUT
  + TOOL_FAILURE
  + TIMEOUT
  + NOT_ATTEMPTED
  + BOUNDARY_BLOCKED
  + ORACLE_MISSING

且 INPUT_CURATION_ERROR == 0
```

其中：

```text
N_eval = N - ORACLE_MISSING
N_answered = CORRECT + INCORRECT
N_common_3 = 三个实现均有 FINAL oracle 且均有可解析答案的 jar 数
```

这些数不能彼此相加成“总成功”：`N`、`N_eval`、`N_answered` 是三个不同分母。

## 7. 分子、分母和公式

记实现为 `i ∈ {O, P, Q}`，分别对应原版、替换前、替换后。

### 7.1 单实现

| 指标 | 公式 | 说明 |
|---|---|---|
| 技术完成率 | `TECH_COMPLETED_i / N` | 缺失、超时和越界都留在分母 |
| 可解析输出率 | `PARSEABLE_i / ATTEMPTED_i` | `ATTEMPTED_i = N - NOT_ATTEMPTED_i - BOUNDARY_BLOCKED_i` |
| 能力回答率 | `ANSWERED_i / N_eval` | 防止“少答所以少错”被藏掉 |
| 已回答语义准确率 | `CORRECT_i / N_answered_i` | 分母只含明确回答，不能单独代表整批 |
| 端到端静态正确率 | `CORRECT_i / N_eval` | 未回答算未达正确，但必须另列原因桶 |
| Oracle 覆盖率 | `N_eval / N` | 说明真值覆盖，不叫“提升率” |

### 7.2 依赖行

定义 `D_expected` 为所有 `FINAL` oracle 项中冻结的预期依赖声明数；无依赖的 jar 仍以 0 行进入 jar 级判定。

对每个实现：

```text
D_expected = D_EXACT + D_WRONG_OUTCOME + D_MISSING + D_UNAVAILABLE

D_EXACT           键和 resolution_status 均一致
D_WRONG_OUTCOME   键存在，但 resolution_status 不一致
D_MISSING         有预期键，实现未给出
D_UNAVAILABLE     该 jar 无已回答输出，无法按行比较

D_extra           实现给出、oracle 不存在的声明；单独计数，不混入分母
```

`D_expected` 与 `D_extra` 不是同一个分母。jar 级依赖完全一致要求：

```text
D_WRONG_OUTCOME == 0 and D_MISSING == 0 and D_extra == 0
```

### 7.3 两两静态一致性

对每对实现 `(i,j)`：

```text
N_common_ij = 两者都有 FINAL oracle 且都有可解析规范化答案的 jar 数
AGREE_ij + DISAGREE_ij == N_common_ij
```

比较的是规范化后的完整语义答案，不是日志文本或字节；原始输出哈希另外保存。答案缺失不能被悄悄从分母移除，应另记：

```text
N_eval == N_common_ij + N_absent_in_at_least_one_ij
```

原版 Forbric 与替换实现“一致”只表示双方答案相同，不表示双方正确。

### 7.4 替换前后

在 `N_common_3` 上做 2×2 转移表：

|  | `POST=CORRECT` | `POST=INCORRECT` |
|---|---:|---:|
| `PRE=CORRECT` | `BOTH_CORRECT` | `PRE_ONLY_CORRECT` |
| `PRE=INCORRECT` | `POST_ONLY_CORRECT` | `BOTH_INCORRECT` |

断言：

```text
BOTH_CORRECT + PRE_ONLY_CORRECT
  + POST_ONLY_CORRECT + BOTH_INCORRECT == N_common_3
```

另外在同一个 `N_common_3` 上比较规范化答案是否变化：

```text
OUTPUT_UNCHANGED + OUTPUT_CHANGED == N_common_3
```

“变化”不是“修复”；只有 oracle 判定的转移才说明方向。`POST_ONLY_CORRECT` 也不能解释变化原因。

### 7.5 三个实现的全部分支

可另记 8 个三实现正确性模式：

```text
O/P/Q ∈ {C,I} 的 2^3 组合
sum(eight_patterns) == N_common_3
```

该表只描述已满足 oracle 和回答条件的共同子集，不外推整批。

## 8. 断言写法

正式运行器应在写任何汇总前执行并保存机器可读的 `assertions.json`：

```text
A1  N == 0 or count(unique artifact_key in manifest) == N
A2  every(local_size == manifest_bytes)
A3  every(local_sha256 == manifest_sha256)
A4  input_snapshot_sha256_before == input_snapshot_sha256_after
A5  every(implementation run uses same cohort_id and environment digest)

E1  for each implementation:
      sum(measurement_state buckets) == N
E2  for each implementation:
      N_eval == CORRECT + INCORRECT + UNSUPPORTED
                + NO_PARSEABLE_OUTPUT + TOOL_FAILURE + TIMEOUT
                + NOT_ATTEMPTED + BOUNDARY_BLOCKED
E3  for each implementation:
      N_answered == CORRECT + INCORRECT
E4  ORACLE_MISSING == N - N_eval

B1  D_expected == D_EXACT + D_WRONG_OUTCOME + D_MISSING + D_UNAVAILABLE
B2  D_extra >= 0 and D_extra is reported separately
B3  for every jar-level "dependency exact" claim:
      D_WRONG_OUTCOME == 0 and D_MISSING == 0 and D_extra == 0

P1  for each pair (i,j):
      AGREE_ij + DISAGREE_ij == N_common_ij
P2  for each pair (i,j):
      N_common_ij + N_absent_in_at_least_one_ij == N_eval
P3  BOTH_CORRECT + PRE_ONLY_CORRECT
      + POST_ONLY_CORRECT + BOTH_INCORRECT == N_common_3
P4  OUTPUT_UNCHANGED + OUTPUT_CHANGED == N_common_3

R1  任一 E/B/P 断言失败 => 汇总标为 INVALID，不发布成功率或胜者
R2  任一数字附 implementation revision、cohort_id、oracle revision、分母名称
```

所有断言都只能证明“记录桶在这种冻结定义下闭合”，不能单独证明 oracle 正确或实现正确。

## 9. 异常、重跑和稳定性

- 每个实现先执行一次完整冷启动；所有条目按冻结顺序跑。
- 不因结果不好看而换 jar、换顺序、换环境或改 normalizer。
- 超时、崩溃、恶意输出和不可读输入全部计数；不能吞成 WARN，也不能静默跳过。
- 允许对同一冻结命令做一次预先声明的复跑；所有 attempt 都保留，不能只留较好的一次。
- 两次复跑的原始输出哈希、规范化输出哈希和 `measurement_state` 都分别记录。
- 三者任一不一致，记 `NONDETERMINISTIC`，不选择其中一次当最终结果；主汇总必须显式计数。
- 若 adapter/normalizer 自身失败，先把该失败记在包装层，不得误记成被测实现语义错误。
- 若 jar 哈希在两次运行之间变化，整批作废；不做局部修补。
- 若原版 Forbric 无法在不启动 MC/不加载类的前提下回答某字段，标 `BOUNDARY_BLOCKED`；不得把“未运行”写成 `0`。

复跑稳定性分母为已实际 attempt 的 jar，未 attempt 的单独计数：

```text
attempted_for_stability
  = DETERMINISTIC
  + NONDETERMINISTIC
  + RERUN_TECHNICAL_FAILURE
```

## 10. 哪些结果不能叫“预测成功”

以下都不构成“预测成功”：

- 进程退出码为 0；
- 工具扫描或解析完成；
- 输出了某个 modId；
- 与某个历史数字相比缺口变少；
- 与原版 Forbric 一致；
- 替换前、替换后、原版三者一致；
- 在 oracle 上全部一致，但只覆盖静态声明性元数据；
- `UNSUPPORTED`、`EXCLUDED`、`NO_CANDIDATE` 或缺失项为 0；
- 某个非 `SATISFIED` 依赖行与外部失败清单重叠；
- 一次重跑通过或输出哈希稳定。

可以写：

- “在此 cohort、此 oracle revision 和此定义下，静态语义一致 `x/N_eval`”；
- “技术完成 `x/N`，失败 `y/N`，未评估 `z/N`”；
- “依赖声明精确一致 `x/D_expected`，额外声明 `e` 单独报告”；
- “与基线一致/不一致 `x/N_common`”；
- “未验证运行时行为、Mixin transform、类加载和兼容性”。

不得使用 AGENTS.md 明令禁用的“已验证有效”“预测成功”“覆盖率提升”“证明”“根因”。也不得把静态 oracle 一致写成“能启动”或“不会冲突”。

## 11. 推荐结果文件边界

未来实现时建议只生成以下派生文件，均写在新目录且不覆盖既有产物：

| 文件 | 内容 |
|---|---|
| `cohort-manifest.json` | N、jar 哈希、环境与顺序冻结 |
| `implementation-lock.json` | 三实现 commit/build/normalizer 指纹 |
| `oracle-items.jsonl` | 冻结的 jar 级预期答案 |
| `oracle-dependencies.jsonl` | 冻结的依赖行预期答案 |
| `run-results.jsonl` | 每 run × jar 的技术与原始终态 |
| `item-results.jsonl` | 规范化 jar 级答案与语义比较 |
| `dependency-results.jsonl` | 规范化依赖多重集比较 |
| `comparison-summary.json` | 分母、桶、两两与前后转移 |
| `assertions.json` | A/E/B/P/R 断言逐条结果与失败原文 |

文件名与 schema revision 必须在运行前固定；任何重算另起 revision，不覆盖旧结果。

## 12. 未验证与已知局限

- 本文件没有选择真实 cohort、没有定义具体 oracle 标签、没有跑三个实现，因此没有任何成功率、准确率或变化量可报。
- 未确定三个实现是否都能在不启动 Minecraft、不加载类的前提下提供相同字段。
- 未确定“原版 Forbric”可用的离线入口；若必须越界，按 `BOUNDARY_BLOCKED` 处理。
- 未确定规范化规则是否需要按加载器族分开；一旦冻结，所有实现必须使用同一版本。
- 人工 oracle 可能出错；双人核验和争议保留只能降低、不能消除标注误差。
- 静态一致不涵盖运行时失败、Mixin transform、类加载、模组间 Behavioral 冲突或加载顺序。

## 13. 交接报告

### 改了哪些文件

- 新增：`forbric-comparison/oracle-design-C.md`
- 修改：无
- 删除：无
- `src/`：未改

### 测试数字

测试数 / 失败 / 错误 / 跳过：没跑，原因：本轮只写对照方案，未改代码，也不要求跑数；没有执行 `gradle test`。

### 关键数字

本轮实测数字：无。`N`、`N_eval`、`N_answered`、`N_common_3`、`D_expected` 等均为待未来运行的符号定义，不冒充实测值。没有计算基线对比；除新增本文件外，既有产物哈希未变化。

### 违反哪条禁令

无。未启动 Minecraft、未加载类、未触碰 jar、未引入 MC/加载器依赖、未输出顺序、未下载、未改代码或受保护文档、未跑测试、未 commit/push/PR。

### 未验证/已知局限

三实现能力、真实 oracle、实际分母和运行稳定性均未验证；所有这些必须在单独批准的执行闸门后由实际输出填写。

### 下一步第一条命令

待主智能体汇总后裁决；本轮不执行任何对照命令。
