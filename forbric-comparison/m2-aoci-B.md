# M2 第一刀 只读预检子任务 B（AOCI-CODE 规范）

- 性质：只读核验。唯一落盘文件即本报告 `m2-aoci-B.md`，未写任何其他文件。
- 工具：经 ToolSearch 加载 `mcp__aoci__aoci_rules`、`mcp__aoci__aoci_overview`、`mcp__aoci__aoci_search` schema，用 DeferExecuteTool 调用；三者均正常返回，无报错、无不可用。
- 本次调用：`aoci_rules` ×1；`aoci_overview`（check_only=true, cognition_state_version='cognition-state/v2'）×1；`aoci_search` ×7（逐词、scope=all）。

---

## ① aoci_rules 中禁令 / 授权边界相关要点原文（逐字）

来源：`aoci_rules` 返回正文，逐字摘录。

**权威边界（开头段）：**
> 权威边界：本合同只规定会话级原则。当前Guide承载当前Plan的执行顺序和安全停点；Schema、Spec与Validator承载机器结构和判据。Prompt只约束模型语义生成。Description、Help、AGENTS与文档不得覆盖机器事实。机器数值、词表与集合只由当前编译版本的internal/machinecontract提供。Fresh Bootstrap中，Host模型创作当前Plan与Guide要求的语义产物；AOCI绝不填写origin=host_model、制造Receipt或生成语义。内部批次不是用户决策；批准、安全、漂移、CAS与Recovery边界继续具有权威。

**第 4 条（语义生成来源限制）：**
> 4. 认知采用"模型生成、模型读取"：生成或修订Header时调查仓库事实；生成或更新Entry时先读当前Header，再读目标源码、测试、配置及必要关联证据。路径、AST、符号列表、扫描、正则、模板或规则引擎只能辅助定位、校验和写入，不能直接生成、预填、拼接或改写标签、F/R/A/S及Curation裁决。

**第 5 条（写入边界）：**
> 5. AOCI受管理资产写入边界：用户只限制业务文件范围时，收尾可以更新aoci.txt与Baseline；用户明确禁止修改aoci.txt、.aoci、元数据或任何额外文件时，不得写入，并如实报告剩余不一致。

**第 6 条（只读任务）：**
> 6. 只读任务不需要调用维护工具。正在运行的版本是cognition_receipt.mcp_service_version；二进制是.mcp.json里的command，不一定在PATH上。

**第 9 条（安全信号不可绕过）：**
> 9. 返回 repair_required 且包含 findings 时，只修复命中的候选并重提同一完整批次。stopped只结束该次尝试：检查failed_step、error 和 recovery；可证明零写入则重新Plan，Intent可证明则Resume，策略要求Rollback则完成后重新Plan；只有证据不足、第三方字节冲突、有界重新Plan仍不收敛、需要审批或外部动作或命中其他安全边界时才停止任务。不得绕过冲突、审批、权限、人工裁决、CAS和安全信号。

**第 10 条（不得猜写）：**
> 10. 语义裁决证据不足时不得猜写。只在当前布局和工具返回状态支持时使用 aoci_report；它不替代Curation、孤儿裁决或故障恢复。维护后再改动受管理对象，维护结果即失效。

**第 11 条（机器阈值）：**
> cognition_refresh_threshold: 30
> overview_delivery.chunk_tokens: 7000
> overview_delivery.chunk_tokens_min: 4000
> overview_delivery.chunk_tokens_max: 24000

---

## ② 当前生效索引 machine facts 原文（check_only=true, cognition-state/v2）

来源：`aoci_overview` 返回 JSON，关键字段逐字摘录。

**请求模式 / 交付：**
- `"request_mode":"check_only"`
- `"delivery_mode":"checkpoint"`
- `"full_text_included":false`
- `"reason":"explicit_check_only"`
- `"server_delivery_complete":true`
- `"host_delivery_status":"host_delivery_incomplete"`

**assessment 关键字段：**
- `"state":"invalid"`
- `"recall":"none"`
- `"refresh_status":"refresh_deferred_until_stable"`
- `"refresh_reasons":[]`
- `"initial_cognition":true`
- `"stable_checkpoint":false`
- `"semantic_change_count":8`
- `"semantic_change_threshold":30`
- `"semantic_change_paths_sha256":"a2dadc76ca6038442e36186c5aaa34697aec332efc0bc7e11528386152d55447"`
- `"semantic_stale":1`
- `"actionable_missing":7`
- `"pending_curation":0`
- `"format_only":0`
- `"line_ending_only":0`
- `"curation_excluded":0`
- `"technical_skipped":0`
- `"orphan":0`
- `"unbaselined":7`
- `"governance_blocker_count":15`
- `"governance_aligned":false`
- `"scope_change_required":false`
- `"whole_index_tokens":9040`
- `"budget_mode":"enforce"`
- `"budget_status":"healthy"`
- `"budget_violation_count":0`
- `"next_action":"先把当前最小工作单元稳定下来；修改和检查仍不稳定时，不要读取旧完整Overview，也不要维护索引。"`

**cognition_receipt 关键字段：**
- `"cognition_scope":"all"`
- `"cognition_state":"invalid"`
- `"effective_scope":"all"`
- `"layout_mode":"volumes-v1"`
- `"mcp_service_version":"0.2.0-rc2"`
- `"runtime_repository_root":"D:\\modgraph"`
- `"scope_object_count":204`
- `"model_full_cognition_reliable":false`
- `"model_scope_cognition_reliable":false`
- `"state_owner":"host_model"`
- `"version":2`
- `"delivered_volumes":[{"id":"meta","kind":"meta","path":"aoci.meta.txt","asset_state":"present","sha256":"5ccd06dd2198435423141174e4f3eaed7e7645644070e0b5cc2e20dbc889172a","object_count":0}]`
- `"delivered_volumes":[{"id":"code","kind":"code","path":"aoci.code.txt","asset_state":"present","sha256":"a2cbfea8592d3a503775c140124428215925f985830da527b2445f94b50e06e5","object_count":204}]`

**cognition_state_v2（原文）：**
```json
{"version":"cognition-state/v2","model_cognition_level":1,"model_cognition_level_state":"index_loaded","delivery_verified":false,"model_cognition_usable":false,"model_cognition_usability_status":"not_established","strict_attestation_verified":false,"strict_attestation_status":"not_provided","strict_attestation_failure_reasons":["not_provided"],"governance_aligned":false,"current_system_cognition_reliable":false,"protocol_completed":true}
```

**delivery_plan（含 entry_count 原文）：**
```json
{"scope":"all","scope_identity":"411daa27c998ae38da73e4e48007d0fb83a12b5900f98932ba46f9535d9322c1","entry_count":204,"estimated_tokens":9040,"body_utf8_bytes":28187,"chunk_tokens":7000,"chunk_count":2}
```

> 小结（数字均来自上方原文）：entry_count = **204**；语义变化计数 semantic_change_count = **8**（阈值 30，semantic_stale=1）；governance_aligned = **false**（blocker 15）；cognition_state = **invalid**；model_cognition_level_state = **index_loaded**；current_system_cognition_reliable = **false**。

---

## ③ 逐词命中数表（aoci_search，scope=all，逐词单独调用）

| 关键词 | 命中数 | 原文证据 |
|---|---|---|
| LOADER_EXPERIMENT | 0 | `检索结果(0 条):` / `无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识` |
| STATIC_ANALYSIS | 0 | `检索结果(0 条):` / `无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识` |
| 双运行域 | 0 | `检索结果(0 条):` / `无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识` |
| 授权 | 0 | `检索结果(0 条):` / `无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识` |
| PCL | 0 | `检索结果(0 条):` / `无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识` |
| src | 1 | `检索结果(1 条):` / `[volume_id=code object_ref=code:.pi/quiet-tools/outputs/2026-11-08T05-07-46-454Z-read-call_9f9ed060e33e411bb781913d-3038c48e49.txt] 2026-11-08T05-07-46-454Z-read-call_9f9ed060e33e411bb781913d-3038c48e49.txt[XS1M]: F:项目进度笔记的文件树一节快照 \| R:- \| A:列出 src 下全部 Java 文件与目录结构 \| S:-` |
| version.json | 0 | `检索结果(0 条):` / `无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识` |

说明：唯一命中 `src` 的条目是一条「项目进度笔记的文件树一节快照」，其 A 字段为「列出 src 下全部 Java 文件与目录结构」，**不涉及任何写 src 的授权或 §0.1 条款**；不构成授权依据。每次检索均附带相同提示：`cognition: refresh_status=refresh_ready_for_overview generation=0; next: call no-argument aoci_overview and follow next_cursor until completed=true`。

---

## ④ AUTH_GAP 原文

检索不到旧 §0.1 / LOADER_EXPERIMENT / 写 src 的授权（LOADER_EXPERIMENT=0、授权=0、src 的唯一命中与授权无关，STATIC_ANALYSIS / 双运行域 / PCL / version.json 均为 0 条），按规则不凭记忆补写旧 §0.1 条款。逐字记录 AOCI 返回的 0 条原文：

> 检索结果(0 条):
> 无匹配条目: 可直接读源码,或用 aoci_report 登记缺失的知识

即：**AUTH_GAP — 当前生效索引（aoci.code.txt，entry_count=204）中不存在旧 §0.1 / LOADER_EXPERIMENT / 写 src 的授权条款；检索结果(0 条): 无匹配条目。**

---

## ⑤ 结论

1. **AOCI 可用**：`aoci_rules`、`aoci_overview`（check_only）、`aoci_search` 均正常返回，无报错、无不可用。当前生效索引为 volumes-v1 布局，`entry_count=204`（code 卷 204 条、meta 卷 0 条），语义变化计数 8（阈值 30），cognition_state=invalid、governance_aligned=false、current_system_cognition_reliable=false，索引处于 `refresh_deferred_until_stable`（未对齐、不可靠）。
2. **无授权条款**：AOCI 当前生效索引中检索不到旧 §0.1 / LOADER_EXPERIMENT / 写 src 的授权（见④ AUTH_GAP）。因此不引用、不补写任何旧 §0.1 条款。
3. **本轮授权来源**：既然 AOCI 可用但无相应授权条款，本轮 M2 第一刀的执行依据为「**用户本轮直接授权 M2 第一刀**」+「**AOCI 当前生效禁令**」（见①），不把授权扩大到 M2 后续阶段或第二块功能。
4. **范围收束**：不联网下载 MC 文件；不使用 Computer Use；不写第二块功能；除本报告外不修改任何仓库文件；不进行索引维护（只读任务，`aoci_rules` 第 6 条）。

---

## ⑥ 违反约束情况

无。

（本子任务全程只读；未写任何仓库文件，仅落盘本报告 `m2-aoci-B.md`；未联网、未使用 Computer Use、未写第二块功能、未凭记忆补写旧 §0.1 条款。）
