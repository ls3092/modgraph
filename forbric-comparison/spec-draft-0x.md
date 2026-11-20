<!-- producedBy: draft_a -->
<!-- DRAFT ONLY: this file does not amend SPEC.md and is ineffective until separately approved. -->

### 0.1 Execution-domain boundary: static analysis and loader experiment (DRAFT)

**Status:** This is a proposed insertion under §0, not an effective specification clause. Activating this draft requires separate user approval and synchronization into both language versions. It grants no implementation, download, or launch permission.

Each run and report must declare exactly one `operating_mode`: `STATIC_ANALYSIS` or `LOADER_EXPERIMENT`. The modes are mutually exclusive and may not share one denominator or be combined in one result set. Switching modes requires a new run, a new frozen cohort, and new immutable evidence; an existing result may not be relabelled.

#### 1. Static analysis mode

`STATIC_ANALYSIS` is the default mode and retains the current project boundary. It reads only the permitted metadata and structural evidence, reports static results, and makes no runtime claim. The following protected source groups are reproduced verbatim; the blockquote marker is presentation only and is not part of the protected text.

Source `AGENTS.md:17`:
> - 不启动 Minecraft、不加载类、不解析 class 文件、不处理 Mixin、不改写字节码。

Source `SPEC.md:15`:
> M0 **does not start Minecraft, does not load classes, does not process mixins, does not rewrite bytecode**.

Source `SPEC.md:19-20`:
> M1 (§3.6) builds a dependency graph on top of those records and detects cycles. It does not start Minecraft
> either, does not load classes, does not read class files, and **does not output an install order** (§3.6.5).

Source `AGENTS.md:18`:
> - 不引入 Minecraft / Fabric Loader / Forge / NeoForge / Mixin / ASM 依赖。

The four groups above must remain byte-for-byte unchanged when this section is activated. A different capability or dependency requirement belongs to a separately approved clause; it may not be inferred from the label “loader experiment”.

#### 2. Loader experiment mode

`LOADER_EXPERIMENT` is a queued mode for one frozen cohort in one controlled environment. It is not enabled by this draft, and its evidence is separate from every static-analysis result.

- The cohort is frozen before execution by `cohort_id`, ordered artifact identities, and artifact SHA-256 values.
- The environment is controlled by an implementation lock, JDK and loader facts, command digest, locale, timezone, and a recorded environment digest.
- Jars remain outside the repository and are neither committed nor redistributed; inputs are read-only for the run.
- No installation order, load order, topological order, layering, or “install this first” recommendation may appear in output at any log level, report field, or document.
- Automatic compatibility, automatic repair, automatic version choice, and candidate recommendation remain prohibited.
- Long-running runtime-error accounting and its relationship to §5's “at most one ERROR per parse attempt” rule remain pending user decision; no loader result may be called complete before that decision.

Activating this §0.1 draft does not by itself authorize Minecraft launch, class loading, Mixin processing, bytecode transformation, or new dependencies. A controlled Minecraft launch requires a further explicit approval. Until then, any result that requires that launch is `BOUNDARY_BLOCKED`.

#### 3. Evidence and failure accounting

Both modes use a frozen denominator fixed before execution. Injects, skips, crashes, timeouts, unsupported capabilities, unparseable output, missing oracle labels, and boundary blocks remain visible as separate states. A single artifact failure must not be changed to WARN, omitted, or silently continued.

For static parsing, the existing §5 one-jar / one-parse-attempt ERROR rule remains in force. Runtime-event identity, deduplication, retention, and long-term aggregation for `LOADER_EXPERIMENT` are pending the accounting decision above. Until that decision, they must not be merged into the static parse-error count or reported as successful omissions.

#### 4. Comparison metrics

Accuracy uses only `N_answered` as its denominator:
`accuracy = CORRECT / N_answered`, where `N_answered = CORRECT + INCORRECT`.

`CORRECT` and `INCORRECT` exhaust the accuracy denominator. `UNSUPPORTED`, `NO_PARSEABLE_OUTPUT`, `TOOL_FAILURE`, `TIMEOUT`, `NOT_ATTEMPTED`, `BOUNDARY_BLOCKED`, and `ORACLE_MISSING` are separate buckets and must remain visible; none may be swallowed into either accuracy class.

- The 4-bucket correctness decomposition is `BOTH_CORRECT + PRE_ONLY_CORRECT + POST_ONLY_CORRECT + BOTH_INCORRECT = N_common_3`.
- The 8-pattern decomposition counts the eight correctness patterns across original Forbric, replacement-pre, and replacement-post; its eight cells also sum to `N_common_3`.
- The 4-bucket and 8-pattern decompositions are two orthogonal partitions of the same frozen intersection. They must not be added together, mixed into one total, or presented as independent sample sets.
- The original Forbric result is an external baseline, not an oracle. Agreement with it is not semantic correctness.

#### 5. Reporting boundary

Every rate must name its numerator, denominator, cohort, implementation revision, and oracle revision. Static agreement must not be described as a runtime guarantee. Missing evidence, unsupported fields, and blocked runtime requirements must remain explicit rather than being converted to zero.

