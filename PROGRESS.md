# modgraph 项目进度

> 本文件用自然语言记录项目进展，面向不熟悉 Java 的读者。最后更新：2026-11-01（第十六步：修正 `provides` 优先级，SPEC 升到 v0.10，真实 modId 优先于别名；AMBIGUOUS_ALIAS 58→18，SATISFIED 572→612，MISSING_ID 72 不变；两份 README 的测试徽章改成 96；测试 96 个、失败 0、跳过 0）。

第十六步修正第十五步那条写错的规则：把“别名与真实 modId 重名”也算歧义，等于把 depId 精确命中真实 modId 和只能靠别名命中混为一谈，导致 40 条本来正确的 SATISFIED（depId = `fabric-api`）被打成 AMBIGUOUS_ALIAS。SPEC 两版升到 v0.10，§3.5.5 改成五级优先级、命中即停：真实 modId → 环境 ID → 唯一别名拥有者 → 多拥有者判歧义 → MISSING_ID；真实 modId 是权威身份，别的模组把同一字符串写进 `provides` 也不构成歧义，唯一保留的冲突是别名占用环境 ID。代码只改 `DependencyResolver.targetsOf` 的候选收集顺序。实际跑：SATISFIED 612、MISSING_ID 72、VERSION_MISMATCH 7、BREAKS_VIOLATED 7、AMBIGUOUS_ALIAS 18、UNPARSEABLE 0，逐行 diff 只有 `AMBIGUOUS_ALIAS → SATISFIED` 一种迁移（40 行，全部 `fabric-api`），M0 三个输出文件哈希未变。

第十五步修掉 `provides` 这个语义 bug：Fabric Loader 会把 `provides` 里的 ID 当作“存在的模组”，M0.5 之前只认数据集 modId 与环境 ID，导致 18 条 `fabric` 依赖被判 MISSING_ID，而实际运行时会解析到 `fabric-api`。SPEC.md 与 SPEC.zh-CN.md 同步升到 v0.9：§3.5.5 的存在性改为「数据集 modId ∪ 环境 ID ∪ 任意模组的 provides 别名」，别名解析到拥有者的版本；一个 ID 有多个拥有者时（多个 modId 声明同一别名，或别名与真实 modId / 环境 ID 重名）不挑任何一个，新增第六种 outcome `AMBIGUOUS_ALIAS`，`resolvedVersion` 为空，WARN 列出全部候选。§3.5.6 与 §6.5 的 stdout 格式同步加上 `| AMBIGUOUS_ALIAS N`，§3.5.7 增加第 7 条验收。

真实数据（195 个 jar、716 条依赖）修前 → 修后：MISSING_ID 90 → 72（正好少 18，全部是 `fabric`），AMBIGUOUS_ALIAS 0 → 58，SATISFIED 612 → 572，VERSION_MISMATCH 7 → 7，BREAKS_VIOLATED 7 → 7，UNPARSEABLE 0 → 0。58 条歧义只来自两个 ID：`fabric` 18 条（`fabric-api` 与 `fabric_api` 都声明该别名），`fabric-api` 40 条（`fabric_api` 声明 `provides: ["fabric","fabric-api"]`，与真实 modId `fabric-api` 重名）。因此 SATISFIED 不是 +18 而是 −40：那 40 条本来走真实 modId，现在因为多了一个拥有者被判歧义。这是按用户规则“不许静默、不许任选一个”的直接结果，不是新 bug；如果以后要按加载器家族分流，需要另写规则。

第十四步只改文档，补上第十三步留下的两处收尾。两份 README 第 26 行的测试徽章从 `tests-42 passing` 改为 `tests-88 passing`，MIT 许可徽章没有动。`SPEC.md` 与 `SPEC.zh-CN.md` 的 §6.5 stdout 模板里，`[INFO]  硬依赖总数：412，不可满足：23` 和 `[INFO]  环：1` 两行被删掉——它们既不匹配程序实际输出，也与 §3.5.6 定义的三行格式冲突——替换成 `依赖总数：硬依赖 N | 软依赖 N | 冲突声明 N`、`不可满足：MISSING_ID N | VERSION_MISMATCH N | BREAKS_VIOLATED N`、`无法解析的版本区间：N（实现缺陷，不是模组问题）`，字段名与 §3.5.6 逐字相同；模板上方补了一句「这是格式模板，不是真实数字，真实数字见 README 的实跑输出」，原来模板下方那句只保留「每个数字必须来自实际运行、本文件不得写死任何数量」的要求。中英文两版同步修改，改完 `gradle clean test` 仍是 88 个测试、失败 0、错误 0、跳过 0。

第十三步只改四份文档，没有动 `src/` 下的任何一行。两份 README 现在与仓库事实一致：M0.5 标为已完成（2026-11-01，195 个真实 jar，716 条依赖声明），产物表补上 `dependencies.csv`，新增「依赖可满足性判定」与「已知边界」两节，输出示例里补上环境事实与三行依赖统计；顺手修掉了 `--args`、三步/四步、42 个测试、v0.7、工作记录条数这些与仓库对不上的表述。`SPEC.zh-CN.md` 从 v0.7 升到 v0.8：版本头改成 v0.8，整节补上 §3.5（3.5.1–3.5.7），§6 新增 6.4 `dependencies.csv` 并把 stdout 顺延成 6.5，§7 标题加上「仅 M0」。两份 SPEC 逐节核对过：29 个标题一一对应、表格行数与列表项数全部相同、outcome 枚举与 CSV 表头逐字未译。当步按用户要求一个徽章都没动（测试徽章在第十四步才改成 88），许可证仍是 MIT。

第十二步只做 M0.5，没有启动 Minecraft、没有加载类、没有解析 class 文件、没有引入任何新依赖。SPEC.md 版本头是 v0.8，§3.5 是 M0.5 的规范，§7 十条只约束 M0，两者没有混用。本步顺手修了 CONTRIBUTING.md 三处与仓库事实不符的表述（六种→七种失败分类、测试资源缺失时的口径、./gradlew→gradle），没有改任何生产逻辑。

M0.5 的真实结果：195 个输入、OK 191、FAILED 4 不变；共 716 条依赖声明（硬依赖 531、软依赖 46、冲突声明 139），每条在 `dependencies.csv` 里恰好一行。判定结果：SATISFIED 612、MISSING_ID 90（硬 68 + 软 22）、VERSION_MISMATCH 7、BREAKS_VIOLATED 7、UNPARSEABLE 0。环境事实声明为 minecraft=1.21.1、fabricloader=0.20.1、quilt_loader=0.27.0、neoforge=21.1.262、forge=52.0.0、java=21，启动时打印一行 INFO，没有任何环境 ID 被判成 MISSING_ID。716 行逐行与 candidates.jsonl 重新比对，字段差异 0；10 条人工抽样按源 jar 逐个对照原始元数据，四项（modId / version / declaredRange / outcome）一致 10/10。M0 的三个输出文件哈希未变，HIGH 331、MEDIUM 1170、LOW 725、覆盖率缺口 10/191 全部保持。

第十一步只改文档与测试辅助代码，没有动 `src/main`。README 现在与仓库事实一致：摘要数字来自 195 个真实 jar 的实跑、`conflicts.csv` 已列为产物、冲突预测四档规则和“静态推断，非运行结论”写在正文里。测试侧新增 `DatasetRoot`：`fixtures/jars` 不存在或为空时回退到 `src/test/resources/fixtures`，并在日志里说明当前模式；完整数据集下仍是 42 个测试、失败 0、跳过 0，内置样本下也是 42 个测试、失败 0、跳过 0。

第十步只改文档，没有动任何生产代码和测试。SPEC.md 已确认升到 v0.7：版本头是 v0.7，§2.4 有三条推断规则（后缀式、前缀式、嵌套类按 `$` 分段独立剥除），§7 编号 1-10 无重复，第 4 条改成 7 种 failureCode，第 7 条新增“目标集合中不得残留任何含 `Mixin` 字样的名字”，第 1 条不再写死数量。按新文字逐条核对的结果写在第五节。

第九步只改了一处生产代码：`InvasivenessAnalyzer.addTarget`。含 `$` 的类名先按 `$` 切分，每一段独立剥前缀 `Mixin` 和后缀 `Mixin` / `MixinPlugin` / `Accessor` / `Invoker`，再拼回；不含 `$` 的类名走原来的逻辑，行为一个字没变。sodium 的 4 处错误目标名修好，但目标总数（fabric 65、neoforge 74）、覆盖率缺口（10/191）、HIGH 组数（331）全部没有变化——因为修复只是把两个幽灵目标换成了正确名字，没有新增或减少目标数量。

第八步的结论不是“全部通过”。旧的 §7 第 1 条要求 200 个 jar，实际是 100 + 95 = 195，缺 5 个 NeoForge 样本；这条验收线已在 v0.7 改成“全部真实 jar，数量由文件系统真实列举”，不再写死数字。第 7 条抽检发现的三类命名问题里，嵌套类已在第九步修掉，中缀式与 `AccessXxx` 按用户裁决保留为已知局限。

第七步的核心变化：单个 jar 不管是不可读、不是 zip、JSON 语法错误、TOML 写坏还是缺必填字段，都在同一层被捕获，填 failureCode 和 failureMessage、打一条 ERROR、标记 FAILED，然后继续处理下一个文件。扫描阶段的失败不会再被解析器改写成 NO_METADATA。当前真实数据 195 个输入、OK 191、FAILED 4（全部 NO_METADATA），191 + 4 = 195。测试从 30 个增加到 41 个，失败 0、错误 0、跳过 0。

## 一、当前阶段与目标

当前处于 **M0.5** 阶段（版本区间求值与可满足性判定），规范是 `SPEC.md` v0.8 §3.5。M0.5 只回答一个问题：**“集合里是否存在满足这条声明的目标”**。它不做拓扑排序、不产出安装顺序、不做版本回溯与候选选择、不下载、不建议装哪个版本，也不把结果合并进 `conflicts.csv`。

M0.5 的两套版本语法必须分开实现，因为同名写法在两边的含义相反：Fabric 的裸版本串 `1.21.1` 是**精确匹配**，NeoForge 的无括号 `21.1.219` 是**最低版本**（Maven 软要求）。实现分在 `version/FabricVersionRange` 与 `version/NeoForgeVersionRange` 两个类里，比较规则在 `version/Version`；无括号形式在解析后用 DEBUG 打出采用了哪条约定。

环境 ID（minecraft / fabricloader / quilt_loader / neoforge / forge / java 及其别名）走 `resolve/EnvironmentFacts`，绝不参与“数据集里有没有这个 modId”的判断。这一步不做的话，374 条环境 ID 依赖（其中 minecraft 174 条）会全部报缺失。

当前真实数字（`gradle run` 实跑，不是估算）：

- 依赖声明 716 条：硬依赖 531、软依赖 46、冲突声明 139；`dependencies.csv` 716 行数据加表头。
- 结果：SATISFIED 612、MISSING_ID 90（硬 68 + 软 22）、VERSION_MISMATCH 7、BREAKS_VIOLATED 7、UNPARSEABLE 0。
- 环境事实：`minecraft=1.21.1 | fabricloader=0.20.1 | quilt_loader=0.27.0 | neoforge=21.1.262 | forge=52.0.0 | java=21`，启动时 INFO 打印一行，任何数字都能据此复现。
- 环境 ID 行共 374 条（minecraft 174、fabricloader 80、neoforge 77、java 43），MISSING_ID 0 条。

七个需要用户裁决或至少知情的口径（都写在代码注释里，不藏在数字后面）：

1. `provides` **不参与**解析。SPEC §3.5.5 只承认“数据集里的 modId”和“环境 ID”两种来源，扩展它等于改规格。代价是 18 条 `fabric` 硬依赖被判 MISSING_ID——数据集里的 `fabric-api` 的 `provides` 正是 `["fabric"]`，运行时能提供，但静态口径不认。
2. 同一 `modId` 的 Fabric / NeoForge 两个变体版本不同时（数据集里有 20 个 modId 如此，例如 creativecore 2.13.48 / 2.13.52），按“**任一存在的版本满足区间即 SATISFIED**”判定，`resolvedVersion` 优先写实际匹配的那个版本。理由是 M0 的冲突预测本来就把同 modId 的变体合并处理。
3. `Constraint.versionRanges` 有多条原串时按“任一满足”处理（Fabric 真实写法 `["1.21","1.21.1"]` 是“支持其中任一版本”），`declaredRange` 用 `;` 连接原串，例：`1.21;1.21.1`。
4. `breaks` 的目标不在集合里记 **SATISFIED**，不记 MISSING_ID：没有目标就谈不上冲突。这一条把“不可满足”里的噪声从 190 降到 90，理由写在第 55 条坑里。
5. `+` 之后的构建元数据不参与比较（`0.8.13+mc1.21.1` 等价于 `0.8.13`）。SPEC §3.5.4 只写了按 `.` 和 `-` 切分，没提 `+`；不剥离的话 `13+mc1` 会变成非数字段，`0.116.17+1.21.1` 会被判成小于 `0.116.6+1.21.1`，与事实相反。
6. NeoForge 侧的 `*` 按“不限制版本”处理（真实数据里有 2 条 `versionRange = "*"`，SPEC §3.5.3 的 NeoForge 表没列这一形式，但把它判成 UNPARSEABLE 是误伤）。
7. 空白区间串按“不限制版本”处理（NeoForge 的 `versionRange` 允许缺失或空串）。

已知的两处误报（按 SPEC §3.5.4 字面切分导致的，没有偷偷修正）：

- `sound_physics_remastered → voicechat [1.21-2.4.11,)` 判 VERSION_MISMATCH。`1.21.1-2.6.27` 与 `1.21-2.4.11` 按 `.` 和 `-` 切段后，第 3 段是 `1` 和 `2`，于是被判成更小；按“MC 1.21.1 + voicechat 2.6.27”的真实语义它应当是满足的。
- 同一条依赖的反向声明 `voicechat <1.21-2.4.11` 因此判 BREAKS_VIOLATED，同样是这一个比较引起的。

`UNPARSEABLE` 在真实数据上是 0。`[#compatible_neoforge#)` 确实存在于 entity_texture_features 的 TOML 里，但整行被 `#` 注释掉了，TOML 解析器根本不会读它；该分支由单元测试覆盖（`>=${placeholder}` 与 `${file.jarVersion}` 两种），不是“写了没跑过”。

当前处于 M0 阶段（离线元数据解析）第六步已完成：在已有 Fabric / NeoForge 元数据解析的基础上，给每个成功解析的模组加上了侵入深度评级，读取 mixin 配置并推断目标类，最后对所有成功模组两两比较，产出静态冲突预测。第六步没有启动 Minecraft、没有加载类、没有解析 class 文件、没有引入 ASM 或 Mixin 依赖；只读取 zip 条目名、元数据文本和明文 mixin 配置文件。

M0 的最终口径（SPEC v0.7 §2.4，共三条推断规则）：Mixin 目标推断同时剥后缀 `Mixin / MixinPlugin / Accessor / Invoker` 和前缀 `Mixin`，含 `$` 的嵌套类先按 `$` 切分、每段独立执行前两条再拼回；ubiquitous 阈值从 25% 下调到 10%，当前 116 个唯一模组对应 `ceil(116 × 0.10) = 12`；摘要同时打印阈值和 14 个实际 ubiquitous 目标类。目标覆盖率缺口从 54 个（54/191，28.3%）降到 10 个（10/191，5.2%），满足“缺口不超过 OK 总数 10%”的验收。冲突结果变为 HIGH 331、MEDIUM 1170、LOW 725、NONE 4444；HIGH 占总对数 4.96%，低于 5%，也低于基线 649。

分档条件不再是悬而未决的冲突：用户 m01468 已裁决采纳严格版 all-rare，SPEC v0.6 §2.4 已按此收紧——共享目标全部不是 ubiquitous 才判 HIGH，只要混入一个 ubiquitous 就降 MEDIUM。按旧 v0.5 的 any-rare 字面，前缀剥离后 HIGH 会变成 810（12.1%），反而高于基线并违反 5% 验收线；当前 all-rare 是 331（4.96%）。裁决理由是语义而非数字：any-rare 会把“共享 3 个目标、其中 2 个是 GameRenderer/Minecraft 这种人人都改的类、只有 1 个稀有”的配对也判 HIGH，但实际撞车大概率就在高频类，稀有类只是顺带命中；all-rare 才表达“撞的位置确实不寻常”。代码行为没有回退，只把 `ConflictPredictor` 注释同步成 v0.6 分级表。

当前工作区的真实数据仍然是：fixtures/jars/fabric 100 个 jar，fixtures/jars/neoforge 95 个 jar，合计 195 个。100 个 fabric 全部解析成功（FABRIC: OK=100 FAILED=0）；neoforge 的 95 个中 91 个成功、4 个失败（NEOFORGE: OK=91 FAILED=4），4 个失败全部是 NO_METADATA，文件名和原因在下面列出。合并后输入 195 个，OK 191 个，FAILED 4 个，OK 数加 FAILED 数正好等于 195。代码和测试都没有硬编码这些数字。

4 个失败记录（全部是 NO_METADATA，failureMessage 都提示可能是 jar-in-jar 嵌套）：

1. connector-2.0.0-beta.17+1.21.1-full.jar —— 外层没有 neoforge.mods.toml 或 mods.toml，检测到内层 jar。
2. Essential_1-5-1_neoforge_1-21-1.jar —— 外层没有元数据，检测到内层 jar。
3. kotlinforforge-5.12.0-all-2.jar —— 外层只有 META-INF/ 和 META-INF/jarjar/，真正的 TOML 在内层 jar 里。
4. kotlinforforge-5.12.0-all.jar —— 结构与上一个副本相同。

第六步的评级结果：侵入深度 API 23 个、MIXIN 152 个、CORE 7 个、RENDER 9 个；支持策略 SUPPORTED 175 个、DEGRADED 0 个、EXCLUDED 16 个。这里 DEGRADED 是 0，不是没跑到：SPEC v0.6 仍把 RENDER 和 CORE 的 supportPolicy 统一为 EXCLUDED，M0 阶段没有别的来源会产生 DEGRADED。当前 HACK_CLIENT 为 0，所以 16 个 EXCLUDED 全部来自 RENDER/CORE（9 + 7）。

RENDER 的 9 个（文件名 → modId）：

1. iris-fabric-1.8.14-beta.1+mc1.21.1.jar → iris
2. reeses-sodium-options-fabric-2.2.4+mc1.21.1.jar → reeses-sodium-options
3. sodium-extra-fabric-0.9.5+mc1.21.1.jar → sodium-extra
4. sodium-fabric-0.8.13+mc1.21.1.jar → sodium
5. embeddium-1.0.15+mc1.21.1.jar → embeddium
6. iris-neoforge-1.8.14-beta.1+mc1.21.1.jar → iris
7. reeses-sodium-options-neoforge-2.2.4+mc1.21.1.jar → reeses_sodium_options
8. sodium-extra-neoforge-0.9.5+mc1.21.1.jar → sodium_extra
9. sodium-neoforge-0.8.13+mc1.21.1.jar → sodium

CORE 的 7 个（文件名 → modId）：

1. entity_model_features-3.3.12-1.21-fabric.jar → entity_model_features（信号：条目路径含 `math/asm/`）
2. konkrete_fabric_1.9.9_MC_1.21.jar → konkrete（信号：条目路径含 `minidev/asm/`）
3. Cobblemon-neoforge-1.8.4+1.21.1.jar → cobblemon（信号：条目路径含 `impl/asm/`）
4. entity_model_features-3.3.12-1.21-neoforge.jar → entity_model_features
5. konkrete_neoforge_1.9.9_MC_1.21.jar → konkrete
6. xaerominimap-neoforge-1.21.1-26.6.0.jar → xaerominimap（信号：`META-INF/coremods.json`）
7. xaeroworldmap-neoforge-1.21.1-1.47.0.jar → xaeroworldmap（信号：`META-INF/coremods.json`）

`modType` 的当前分布是：LIBRARY 12 个、SHADER_LOADER 2 个、OPTIMIZER 1 个、NORMAL 176 个、HACK_CLIENT 0 个。sodium 虽然是 OPTIMIZER 名单里的 ID，但它的 provides 非空，按“LIBRARY 优先”的顺序被判成 LIBRARY；这是按 SPEC §2.3 的判定顺序得到的，不是为了让某个数字好看。

Mixin 目标索引：只读 mixin 配置的明文 JSON，从 `mixins` / `client` / `server` 数组里取实现类名，去掉包路径后先剥开头的 `Mixin`，再按 `MixinPlugin` → `Mixin` → `Accessor` → `Invoker` 的顺序剥尾部后缀，得到简单类名；结果写进 `mixinTargets`，并把 `mixinTargetsInferred` 置为 true。当前 191 个 OK 记录全部是 true，4 个 FAILED 是 false 且目标列表为空。输出是简单名，不带包路径；两个不同包下同名的类会被合并，这是 SPEC 明确接受的误报来源。目标推断只基于命名约定，没有读 `@Mixin` 注解、没有读 class 文件。

目标覆盖率修正：v0.5 之前有 54 个 OK 模组的 `mixinConfigs` 非空但推断不出目标（54/191，28.3%），原因之一是大量前缀式类名如 BiomesOPlenty 的 `MixinBloodFluid`、`MixinLiquidNullFluid` 没有被剥前缀，`MixinGameRenderer` 还会和 `GameRenderer` 分裂计数。v0.5 同时剥前缀和后缀后，缺口降到 10 个（10/191，5.2%），满足“不超过 OK 总数 10%”的验收；当前所有 target 中已经没有以 `Mixin` 开头的名字。剩余 10 个是配置本身数组为空或只有无后缀/无前缀的类名，属于命名约定推断的已知漏报边界，不是没跑到。

冲突预测：只对 status=OK 的模组做两两比较；同一 modId 的 Fabric / NeoForge 变体先合并目标并取更深的侵入深度，因此当前 191 条 OK 记录聚合成 116 个唯一 modId，比较 6670 对。结果写入 `conflicts.csv`，并输出 `冲突预测：HIGH 331 组 | MEDIUM 1170 组 | LOW 725 组（静态推断，非运行结论）`。HIGH 表示两个模组共享的目标里没有 ubiquitous 高频类，不表示一定崩溃；NONE 也不表示一定稳定。每一条 HIGH 都打了 WARN，格式是 `潜在冲突 {} × {}，共同目标：{}`。

按 SPEC v0.6 的稀有度规则，当前 `ubiquitous` 阈值是 `ceil(116 × 0.10) = 12` 个唯一模组。实际 Top 10 是 `Minecraft 28 | GameRenderer 25 | Entity 19 | LevelRenderer 18 | MinecraftClient 18 | ClientPacketListener 17 | Gui 16 | LivingEntity 16 | Screen 15 | ClientLevel 13`；实际判为 ubiquitous 的 14 个目标类完整清单是 `Minecraft 28 | GameRenderer 25 | Entity 19 | LevelRenderer 18 | MinecraftClient 18 | ClientPacketListener 17 | Gui 16 | LivingEntity 16 | Screen 15 | ClientLevel 13 | ItemStack 12 | MinecraftServer 12 | Player 12 | ServerLevel 12`。摘要会同时打印阈值和这份清单。HIGH 从基线 649 降到 331，MEDIUM 1170、LOW 725、NONE 4444；331/6670 = 4.96%，低于 5% 验收线，也低于基线 649。这个结果来自前缀式目标推断和 10% 阈值，不是通过改数据或改阈值凑出来的；分档条件已由用户 m01468 裁决为 all-rare，见本节前面第 9、11 段。

当前输出文件：`candidates.jsonl` 195 行（每个输入 jar 一行）；`summary.csv` 195 行数据加表头，表头是 `sourceFile,sha256,loaderFamily,status,modId,version,modType,invasiveness,supportPolicy,failureCode`；`conflicts.csv` 6670 行数据加表头，表头是 `modA,modB,level,sharedTargets`。EXCLUDED 的 16 个模组仍然保留在 summary.csv 里，没有被剔除；测试专门断言了这一点。

本步的测试结果是 30 个测试、0 失败、0 错误、0 跳过。`gradle clean test` 退出码 0；`gradle clean build` 退出码 0（12 秒，10 个任务）；`gradle run` 退出码 0。这些数字都是真实执行结果，不是只看了 BUILD SUCCESSFUL 的缓存状态。30 个测试中 DescriptorAnalysisTest 9 个，其余 21 个。

M0 的定位仍然要反复强调：输出是“能否启动的准入检查”，不是“能否稳定运行的保证”。它能发现依赖缺失、版本区间不相交、循环依赖、显式不兼容声明这类写在元数据里的冲突；它不能发现两个 Mixin 撞同一方法、两个模组替换同一渲染阶段这类只有运行时才暴露的行为性冲突。项目不做“自动兼容”，也不做“自动修复”。第六步的 HIGH 冲突预测同样是静态预警，会漏报也会误报。

已知边界和遗留问题：第七步已经修掉“JarScanner 遇到不可读或非 zip 文件直接抛异常终止”这个从第二步就存在的问题；现在扫描失败同样产一条 FAILED 记录并计入失败数，OK+FAILED 恒等于输入数，Main 里的等式断言保留着，不满足就抛异常。SPEC.md 已确认是 v0.7（版本头、§2.4 三条推断规则、§7 十条验收一致）。FORGE 分支没有真实的 mods.toml 样本，只有代码实现；HACK_CLIENT 当前 0 个，没有真实外挂样本。前缀式 Mixin 类名和嵌套类分段剥除已经按 v0.7 加入；覆盖率缺口 10/191，仍低于 10% 验收线，但这是命名约定推断的漏报边界。第八步抽检发现三类漏推断边界，其中第一类已在第九步修掉：嵌套类现在按 `$` 分段独立剥除，`XxxMixin$YyyMixin` 推成 `Xxx$Yyy`，sodium 两个版本各 2 处共 4 处已修正。仍未修的还有两类：名字里带中缀的 `PlayerMixinFabric` 不被剥除，直接跳过；`AccessXxx` 这种非 `Accessor` 后缀的访问器全部被跳过，controlling 的 NeoForge 版本因此 `mixinTargets` 为空。这两类按用户裁决保持现状：中缀剥除无法判断边界，`Access` 前缀规则可能误伤正常类名。第七步有一个必须如实说明的点：INTERNAL_ERROR 不是“数据分类正确”的样本，它是 `fabric.mod.json` 里的 schemaVersion 数值超出 int 范围时触发的真实运行时异常路径；它能证明这条兜底路径存在且会计入 FAILED，但不能声称“坏数据会被正确归类为 INTERNAL_ERROR”。另外 IO_ERROR 用的是“zip 结构完好、但某个条目的压缩数据损坏”的样本：文件能打开、条目名能列出，读条目内容时才失败，所以它不会被误记成 NOT_A_ZIP。没有执行 git commit，提交仍由用户完成。

## 二、文件树

（列出 src/ 下的全部文件）

```
src/
├── main/
│   ├── java/
│   │   └── dev/
│   │       └── modgraph/
│   │           ├── Main.java
│   │           ├── analysis/
│   │           │   ├── ConflictLevel.java
│   │           │   ├── ConflictPredictor.java
│   │           │   ├── ConflictReport.java
│   │           │   ├── InvasivenessAnalyzer.java
│   │           │   ├── ModConflict.java
│   │           │   └── TargetFrequency.java
│   │           ├── model/
│   │           │   ├── Constraint.java
│   │           │   ├── FailureCode.java
│   │           │   ├── Invasiveness.java
│   │           │   ├── LoaderFamily.java
│   │           │   ├── ModCandidate.java
│   │           │   ├── ModType.java
│   │           │   ├── Ordering.java
│   │           │   ├── Side.java
│   │           │   ├── Status.java
│   │           │   └── SupportPolicy.java
│   │           ├── parse/
│   │           │   ├── FabricMetadataParser.java
│   │           │   └── NeoForgeMetadataParser.java
│   │           ├── report/
│   │           │   └── ReportWriter.java
│   │           ├── resolve/
│   │           │   ├── DepKind.java
│   │           │   ├── DependencyOutcome.java
│   │           │   ├── DependencyReport.java
│   │           │   ├── DependencyResolver.java
│   │           │   ├── DependencyResult.java
│   │           │   └── EnvironmentFacts.java
│   │           ├── scan/
│   │           │   ├── JarScanner.java
│   │           │   └── ScannedJar.java
│   │           └── version/
│   │               ├── FabricVersionRange.java
│   │               ├── NeoForgeVersionRange.java
│   │               ├── Version.java
│   │               ├── VersionParseException.java
│   │               └── VersionRange.java
│   └── resources/
│       └── log4j2.xml
└── test/
    ├── java/
    │   └── dev/
    │       └── modgraph/
    │           ├── StandardStreamPolicyTest.java
    │           ├── analysis/
    │           │   └── DescriptorAnalysisTest.java
    │           ├── parse/
    │           │   ├── FabricMetadataParserTest.java
    │           │   ├── MetadataBomTest.java
    │           │   ├── NeoForgeInvalidEnumTest.java
    │           │   └── NeoForgeMetadataParserTest.java
    │           ├── report/
    │           │   └── DependencyCsvTest.java
    │           ├── resolve/
    │           │   ├── DependencyResolverTest.java
    │           │   └── EnvironmentFactsTest.java
    │           ├── scan/
    │           │   ├── FailurePathTest.java
    │           │   └── JarScannerTest.java
    │           ├── testsupport/
    │           │   └── DatasetRoot.java
    │           └── version/
    │               ├── FabricVersionRangeTest.java
    │               ├── NeoForgeVersionRangeTest.java
    │               └── VersionTest.java
    └── resources/
        └── fixtures/
            ├── fabric-bom.jar
            ├── fail-corrupt-entry.jar
            ├── fail-internal-error.jar
            ├── fail-malformed-json.jar
            ├── fail-malformed-toml.jar
            ├── fail-missing-id.jar
            ├── fail-no-metadata.jar
            ├── fail-not-a-zip.jar
            ├── neoforge-bom.jar
            ├── neoforge-invalid-ordering.jar
            └── neoforge-invalid-side.jar
```

- src/main/java/dev/modgraph/Main.java：程序入口。现在统一扫描 fixtures/jars 下的两个目录，把每个 jar 解析成 ModCandidate，再调用侵入深度分析；最后写出 candidates.jsonl、summary.csv、conflicts.csv、dependencies.csv，并在 INFO 摘要里打印 FABRIC/NEOFORGE 的成功失败数、总输入数、失败码分布（第七步新增，格式如 `NO_METADATA: 4`，没有失败时打印“无”）、四档侵入深度分布、支持策略分布、冲突预测摘要、M0.5 的三行依赖统计和“本结果为准入检查，不构成运行稳定性保证”。它还会断言 OK+FAILED 等于输入数，不满足就记 ERROR 并抛出异常，避免偷偷丢记录。第十二步起，第一行 INFO 是环境事实（`环境事实：minecraft=1.21.1 | ...`），M0.5 的三行位于冲突预测之后、最后一行声明之前。
- src/main/java/dev/modgraph/version/Version.java：版本号与比较规则（SPEC §3.5.4）。按 `.` 和 `-` 切段，数字段按数值比较（先比去零后的长度再比字典序，不转成整数，避免溢出），非数字段按字典序，缺失段补 `0`，数字段恒大于非数字段（因此 `-beta` 低于正式版）。`+` 之后的构建元数据被剥离（如 `0.8.13+mc1.21.1` 等价于 `0.8.13`），`source()` 仍返回原串。含 `${` 或 `#` 的构建期占位符直接判不可解析。通配段 `*` / `x` / `X` 由 `matchesWildcard` 处理，模式以通配段结尾时后续段不再限制。
- src/main/java/dev/modgraph/version/VersionParseException.java：解析失败的运行时异常，带原串（`offending()`），供调用方打 WARN 并归类 UNPARSEABLE。
- src/main/java/dev/modgraph/version/VersionRange.java：已解析区间的接口，只有 `matches(Version)`。
- src/main/java/dev/modgraph/version/FabricVersionRange.java：Fabric / Quilt 语法。支持 `*`、`>=` / `>` / `<=` / `<` / `==` / `=`、`~1.2`（`>=1.2 <1.3`）、`1.21.x` 通配、空格分隔的 AND。真实数据必需的补充：裸版本串是精确匹配（与 NeoForge 无括号形式语义相反）、空串表示不限制、末尾的 `-` 不影响版本（`>=1.21-`）。不认识的谓词（如 `^1.2`）判不可解析，不静默当版本号。
- src/main/java/dev/modgraph/version/NeoForgeVersionRange.java：Maven 区间语法。支持 `[a,)`、`(a,)`、`[a,b)`、`(,b]`、`[a]` 精确、无括号的软要求（`>=`）、区间内空格、`[a,]` 这种上界为空但用 `]` 收尾的写法、以及多区间并集。`*` 与空串表示不限制。`unbracketed()` 让调用方打 DEBUG 说明用了哪条约定。括号缺失、逗号位置错误、端点含通配段或占位符都判不可解析。
- src/main/java/dev/modgraph/resolve/EnvironmentFacts.java：环境事实（SPEC §3.5.2）。六个规范 ID 与三个别名（minecraft_version、fabric-loader、quilt-loader）映射到声明版本；`describe()` 输出启动时打印的一行。版本值集中写在类里的静态初始块，附推导依据，业务代码不引用这些常量，只接收本对象。
- src/main/java/dev/modgraph/resolve/DepKind.java：依赖种类枚举，CSV 取值 required / recommends / breaks。
- src/main/java/dev/modgraph/resolve/DependencyOutcome.java：五种判定的枚举，与 SPEC §3.5.5 一一对应。
- src/main/java/dev/modgraph/resolve/DependencyResult.java：`dependencies.csv` 的一行；`resolvedVersion` 可为空（目标不存在）。
- src/main/java/dev/modgraph/resolve/DependencyReport.java：全部行 + 按种类、按结果的计数。计数只做聚合，不把 UNPARSEABLE 折进 SATISFIED。
- src/main/java/dev/modgraph/resolve/DependencyResolver.java：M0.5 的判定器。只取 status=OK 的记录，按 depends → recommends → breaks 顺序对每条声明产出一行；目标来源只有“数据集里的 modId”和“环境 ID”两种；同一 modId 的多个变体按“任一版本满足即 SATISFIED”；多条区间原串按“任一满足”；breaks 的目标不存在记 SATISFIED；无法解析的区间与无法解析的目标版本都记 UNPARSEABLE 并打 WARN（带原串），绝不当成满足。它不排序、不求解、不改 conflicts.csv。
- src/main/java/dev/modgraph/analysis/ConflictLevel.java：冲突等级枚举，取值 HIGH、MEDIUM、LOW、NONE。
- src/main/java/dev/modgraph/analysis/ModConflict.java：一对模组的冲突结果，记录 modA、modB、等级和共同目标简单名列表；列表在创建时做不可变复制。
- src/main/java/dev/modgraph/analysis/InvasivenessAnalyzer.java：侵入深度分析器。只读 zip 条目名、元数据文本和 mixin 配置；按 RENDER → CORE → MIXIN → API 的顺序命中即停。RENDER 信号包括已知渲染/光影模组 ID、mixin 配置或入口点引用 net.minecraft.client.render；CORE 信号包括 META-INF/coremods.json、metadata transformer、条目路径里的 `/asm/`；MIXIN 信号包括 mixinConfigs 非空或存在 mixins.*.json。命中 RENDER/CORE 时打 WARN 并置 EXCLUDED；所有 EXCLUDED 都打“排除模组…不做兼容性承诺”的 WARN。它还负责 modType 的判定顺序。mixin 目标名推断在 `addTarget`：不含 `$` 的类名先剥前缀 `Mixin` 再剥一个后缀（`MixinPlugin` / `Mixin` / `Accessor` / `Invoker`）；含 `$` 的嵌套类按 `$` 分段，每段独立做同样的前缀+后缀剥除再拼回，因此 `VertexMultiConsumerMixin$DoubleMixin` 得到 `VertexMultiConsumer$Double`。中缀式（`PlayerMixinFabric`）和 `Access` 前缀（`AccessXxx`）按用户裁决不做。
- src/main/java/dev/modgraph/analysis/ConflictPredictor.java：静态冲突预测器。只取 status=OK 的记录，按 modId 合并同一模组的多个加载器变体；先统计每个目标被多少唯一模组命中，10% 及以上为 ubiquitous（当前 116 个唯一模组，阈值 12），再两两比较目标简单名，分 HIGH / MEDIUM / LOW / NONE。按用户 m01468 的裁决采用 all-rare：共享目标全部不是 ubiquitous 才是 HIGH，只要混入一个 ubiquitous 就降 MEDIUM；SPEC v0.6 §2.4 已按此收紧。代码注释、PROGRESS 和交接报告一致，不再待确认。HIGH 逐条打 WARN。它返回 `ConflictReport`（包含全部 pair 包括 NONE、目标频率、模组数、总对数、阈值）。
- src/main/java/dev/modgraph/analysis/ConflictReport.java：冲突预测的汇总结果，保存全部模组对、目标频率列表、唯一模组数、总对数和 ubiquitous 阈值；列表在创建时做不可变复制，数字不能为负。
- src/main/java/dev/modgraph/analysis/TargetFrequency.java：一个目标类及其“被多少个唯一模组命中”的计数，用于 Top 10 输出和 ubiquitous 判定；目标名不能为空，计数必须为正。
- src/main/java/dev/modgraph/model/ModCandidate.java：每个 jar 对应的一条统一记录，24 个字段的名称、顺序、类型严格按 SPEC.md v0.3 第一节；四个必填字段在创建时检查不能为空，可选字段允许留空。第六步之后，成功记录的 mixinTargets 已填入推断出的简单类名，mixinTargetsInferred 为 true；失败记录仍为空/false。
- src/main/java/dev/modgraph/model/Constraint.java：依赖、推荐、破坏、加载顺序这类约束的通用结构，记录目标模组 ID、版本区间列表、是否必需、排序方式和适用侧。按用户确认，它是独立类，不嵌套进 ModCandidate。
- src/main/java/dev/modgraph/model/LoaderFamily.java：加载器家族枚举，取值为 FABRIC、NEOFORGE、FORGE、QUILT、UNKNOWN。
- src/main/java/dev/modgraph/model/Status.java：记录状态枚举，只有 OK 和 FAILED 两个值。
- src/main/java/dev/modgraph/model/Side.java：模组适用侧枚举，取值为 CLIENT、SERVER、BOTH、UNKNOWN。
- src/main/java/dev/modgraph/model/Ordering.java：加载顺序枚举，取值为 NONE、BEFORE、AFTER。
- src/main/java/dev/modgraph/model/ModType.java：模组类型枚举，取值为 LIBRARY、SHADER_LOADER、OPTIMIZER、HACK_CLIENT、NORMAL。
- src/main/java/dev/modgraph/model/Invasiveness.java：侵入深度枚举，按从深到浅为 RENDER、CORE、MIXIN、API。
- src/main/java/dev/modgraph/model/SupportPolicy.java：支持策略枚举，取值为 SUPPORTED、DEGRADED、EXCLUDED。
- src/main/java/dev/modgraph/model/FailureCode.java：七种失败原因的枚举，覆盖不是 zip、没有元数据、JSON 损坏、TOML 损坏、缺少必填字段、读写错误，以及 INTERNAL_ERROR（程序内部异常，用来和“数据有问题”区分开）。
- src/main/java/dev/modgraph/parse/FabricMetadataParser.java：Fabric 元数据解析器。读取 jar 根目录的 fabric.mod.json，先剥离 UTF-8 BOM，再按 SPEC.md §2.1 映射统一字段；识别 quilt.mod.json；把 SPEC 未定义的顶层字段记入 unknownFields 并 WARN；mixinConfigs 只记录 mixins 里的配置路径，不读配置内容（配置内容由第六步的分析器读）；找不到 fabric.mod.json 时在 failureMessage 里提示可能是 jar-in-jar 嵌套；单个 jar 解析失败时填 failureCode、打一条 ERROR、标记 FAILED 并继续。第七步之后它还会检查扫描阶段是否已经把该 jar 判成 FAILED，是就直接原样返回，避免把 NOT_A_ZIP 改写成 NO_METADATA；读条目内容时如果 zip 抛 ZipException（条目数据损坏），归 IO_ERROR，因为文件本身仍是有效 zip；未分类的 RuntimeException 由原来的 MALFORMED_JSON 改为 INTERNAL_ERROR，不再把程序 bug 伪装成数据问题。
- src/main/java/dev/modgraph/parse/NeoForgeMetadataParser.java：NeoForge / Forge 元数据解析器。读取 META-INF/neoforge.mods.toml；找不到时回退 META-INF/mods.toml；有前者记 NEOFORGE，只有后者记 FORGE；先剥离 UTF-8 BOM，再用 night-config 的 TOML 解析器解析；取第一条 [[mods]]；多条目只取第一条并 WARN；依赖按 type、required、mandatory 的先后顺序判定；versionRange 原样保留；mixinConfigs 从顶层 [[mixins]] 的 config 读路径；找不到元数据时记 NO_METADATA 并在消息里提示嵌套；依赖 ordering / side 只认已知值，遇到不认识的字符串记 MALFORMED_TOML 并在 ERROR 里写明字段和值；RuntimeException 兜底归 INTERNAL_ERROR，消息注明“这是程序异常不是数据问题”并带异常类名；失败只打一条 ERROR 并继续。第七步之后它同样先检查扫描阶段的 FAILED 并原样返回，读条目内容时的 ZipException 归 IO_ERROR。
- src/main/java/dev/modgraph/report/ReportWriter.java：输出写入器。用 Gson 的 serializeNulls 写 candidates.jsonl；按 SPEC 表头写 summary.csv；按 modA,modB,level,sharedTargets 写 conflicts.csv；第十二步新增 `writeDependencies`，按 `modId,version,depId,depKind,declaredRange,resolvedVersion,outcome` 写 dependencies.csv，缺失版本写空单元格，含逗号的 Maven 区间按 CSV 规则加引号；CSV 字段做转义；summary.csv 包含全部记录，包括 EXCLUDED 和 FAILED，不做剔除。
- src/main/java/dev/modgraph/scan/ScannedJar.java：扫描结果的临时包装，包含 jar 的文件路径、对应的 ModCandidate，以及这个 jar 内全部条目（entry）的名字；条目名放在 ModCandidate 之外，供侵入深度和 Mixin 目标索引使用。
- src/main/java/dev/modgraph/scan/JarScanner.java：扫描器。递归找出所有 .jar，按路径排序，逐个算 SHA-256，并从 zip 的中央目录读出全部条目名；只读名字，不打开条目内容。第七步之后它对单个文件的失败不再向外抛：读文件算 SHA-256 失败记 IO_ERROR，`new ZipFile` 抛 ZipException 记 NOT_A_ZIP，其他 IOException 记 IO_ERROR，RuntimeException 记 INTERNAL_ERROR；失败文件一样产出一条 FAILED 记录（条目名列表为空），并打一条 ERROR，然后继续下一个文件。只有“目录本身无法遍历”这种没有对应输入文件的情况才会抛 IOException。
- src/main/resources/log4j2.xml：日志配置文件。默认等级 INFO；启动时加 -Dlog.level=debug 可以把等级调到 DEBUG。日志格式包含时间、线程、级别、类名和消息。
- src/test/java/dev/modgraph/testsupport/DatasetRoot.java：第十一步新增的测试数据源选择器。`fixtures/jars` 存在且至少有一个 jar 时用完整数据集，否则回退到 `src/test/resources/fixtures`；`logMode` 每次都会打一行 INFO 说明当前用的是完整数据集还是内置样本，并把根目录和 jar 数写出来。它不会让测试 SKIP，缺数据只切换数据源。
- src/test/java/dev/modgraph/StandardStreamPolicyTest.java：构建时强制执行的静态检查。它用 ArchUnit 扫描 dev.modgraph 包，禁止生产代码出现 System.out、System.err 和 printStackTrace()；一旦出现，gradle build 会失败。
- src/test/java/dev/modgraph/analysis/DescriptorAnalysisTest.java：第六步的 9 个测试，第九步增加到 10 个（新增 `nestedMixinClassNamesAreStrippedSegmentBySegment`，对两个 sodium 记录断言嵌套类被逐段剥除、目标集合里不残留 `Mixin`）。第十一步起支持两种数据源：完整数据集下跑原来的全部分布、冲突和样本断言；内置样本下改为断言该样本的已知构成（只有两个 API 级 BOM 模组、无 mixin 目标、唯一一对冲突落 NONE），不去假装四档都出现过。验证每个 jar 恰好一条记录；OK 记录的分析字段齐全、失败记录为空；四档覆盖全部 OK 且每档非空；RENDER/CORE 全部 EXCLUDED 且有信号；mixinTargets 都是无点简单名且 inferred=true，并用真实的 sodium-neoforge 和 carryon 样本验证嵌套读取和带注释配置；前缀式 `MixinBloodFluid` 会被推断为 `BloodFluid`，且没有 target 以 `Mixin` 开头；mixinConfigs 非空但目标为空的记录不超过 OK 总数 10%；冲突对数等于唯一 modId 的两两组合数、四档都非空，且 HIGH 的共享目标必须全部不是 ubiquitous、MEDIUM 的共享目标必须全是 ubiquitous；HIGH 占比不超过 5%；高频目标 Top 10 按命中数降序，阈值等于 `(modCount + 9) / 10`，且至少有一个 ubiquitous；把三个输出文件写到临时目录，检查行数、表头、EXCLUDED 是否保留、HIGH 行数、JSON 的 inferred 值。无 assume、无 mock。
- src/test/java/dev/modgraph/parse/FabricMetadataParserTest.java：Fabric 解析器的五个测试，分别验证“OK 数加 FAILED 数等于文件系统里 fabric 目录真实 .jar 数”“成功的记录都有 modId、version 和加载器家族”“如果有失败记录，失败记录必须有 failureCode 和 failureMessage”“一个真实的无 fabric.mod.json、但带内层 jar 的样本会得到 NO_METADATA 并在消息里提示嵌套”“mixinTargets 仍为空、mixinTargetsInferred 仍为 false”。
- src/test/java/dev/modgraph/parse/NeoForgeMetadataParserTest.java：NeoForge 解析器的六个测试，分别验证“OK 数加 FAILED 数等于文件系统里 neoforge 目录真实 .jar 数”“成功的记录有 modId、version，加载器家族是 NEOFORGE 或 FORGE”“失败记录是 NO_METADATA、消息里有嵌套提示、modId 为空”“${file.jarVersion} 原样保留”“mixinConfigs 只包含 .json 路径”“mixinTargets 仍为空、mixinTargetsInferred 仍为 false”。
- src/test/java/dev/modgraph/parse/NeoForgeInvalidEnumTest.java：两个测试，分别用真实 fixture 验证非法的 ordering 值和非法的 side 值会让该 jar 变成 FAILED + MALFORMED_TOML；两个测试都挂了一个 Log4j 收集器，断言确实打出了 ERROR，而且消息里包含出错的字段名和值，证明不是静默降级。
- src/test/java/dev/modgraph/parse/MetadataBomTest.java：三个 BOM 测试。第一个直接打开两个真实 fixture jar，断言 fabric.mod.json 和 neoforge.mods.toml 的字节真的以 EF BB BF 开头；第二个用 FabricMetadataParser 解析带 BOM 的 fabric jar，断言 OK、FABRIC、modId 正确；第三个用 NeoForgeMetadataParser 解析带 BOM 的 neoforge jar，断言 OK、NEOFORGE、modId 正确。
- src/test/java/dev/modgraph/scan/JarScannerTest.java：扫描器的四个测试，分别验证“记录数等于文件系统里真实 .jar 数”“每条记录都有必填字段和条目名”“解析字段仍然为空（包括 mixinTargets 和 mixinTargetsInferred）”“记录的条目名和 zip 中央目录完全一致”。第十一步起扫描根目录由 `DatasetRoot.root()` 决定：有完整数据集用 `fixtures/jars`，没有则回退到内置的 11 个样本，两种模式都断言 `OK + FAILED == 文件系统真实列举数`。第七步把第一类断言改成按状态分支：OK 记录必须有 64 位十六进制 sha256 和非空条目名，FAILED 记录必须有 failureCode 和 failureMessage，因为测试资源目录里现在真的有损坏文件。
- src/test/java/dev/modgraph/scan/FailurePathTest.java：第七步新增的 11 个测试。验证每个磁盘上的 jar 恰好一条记录；OK + FAILED 恒等于输入数；失败文件不会中断整批（同目录的 fabric-bom.jar 仍然是 OK）；失败记录带 failureCode / failureMessage 且 modId 为空；扫描失败的 ERROR 只打一条且含 NOT_A_ZIP 和文件名；解析器不会把扫描阶段的 NOT_A_ZIP 改写掉；Fabric 的 6 种失败码和 NeoForge 的 3 种失败码分别由哪个真实 fixture 触发；7 种失败码全部有样本覆盖；损坏条目样本仍然是有效 zip、只是读内容抛 ZipException；INTERNAL_ERROR 来自真实运行时异常路径；条目损坏的 ERROR 只打一条且含 IO_ERROR。没有 assume，没有 mock，没有临时生成文件。
- src/test/java/dev/modgraph/version/VersionTest.java：第十二步新增的 11 个测试，覆盖 SPEC §3.5.4 的每一条：数字段按数值比较（1.10 > 1.9）、缺失尾部段补 0（1.21 == 1.21.0 但 != 1.21.1）、预发布低于正式版（21.0.110-beta < 21.0.110）、非数字段按字典序、构建元数据被忽略（0.8.13+mc1.21.1 == 0.8.13）、超长数字段不溢出、末尾 `-` 不影响版本、通配段匹配（1.21.x 匹配 1.21.1 与 1.21.1.5、不匹配 1.22）、中段通配、占位符与畸形串被拒绝。
- src/test/java/dev/modgraph/version/FabricVersionRangeTest.java：10 个测试，逐条覆盖 §3.5.3 上半表的写法（`*`、五种比较运算、`==`/`=`、裸版本精确匹配、`~1.2`/`~1.21`/`~1.21.1`、`x`/`X` 通配、空格分隔 AND、`~` 与 `<` 组合），另验证真实数据里的 `>=1.21-` 与 `>=1.21- <1.21.2-`、空串为不限制，以及 `^1.2`、`>=1.2.x`、`abc` 判不可解析。
- src/test/java/dev/modgraph/version/NeoForgeVersionRangeTest.java：10 个测试，覆盖 `[a,)`、`(a,)`、`[a,b)`、`(,b]`、`[a]`、无括号软要求（并断言 `unbracketed()` 标志）、区间内空格、`[a,]`、`*` 与空串、Maven 并集、`[21-beta,)` 这类预发布下界，以及 `[#compatible_neoforge#)`、缺闭合括号、逗号位置错误、`(1.21.1)`、`[1.21,1.22,1.23)`、区间后有多余字符全部判不可解析。
- src/test/java/dev/modgraph/resolve/EnvironmentFactsTest.java：4 个测试，验证 §3.5.2 的六个 ID 全部有声明版本、三个别名解析到同一值、普通 modId（sodium、fabric）不被当成环境 ID、`describe()` 列出全部事实。
- src/test/java/dev/modgraph/resolve/DependencyResolverTest.java：9 个测试。第 1 个把 716 条声明按 candidates.jsonl 的顺序重建，与结果逐行比对，确认每条恰好一行、不去重不省略；第 2 个断言 374 条环境 ID 依赖没有一条 MISSING_ID 且都有 resolvedVersion；第 3 个逐行检查五种 outcome 的定义（MISSING_ID 只出现在硬/软依赖且版本为空、BREAKS_VIOLATED 只出现在 breaks、breaks 目标不存在时是 SATISFIED 不是 MISSING_ID）；第 4 个专门验证 breaks 目标不存在的行；第 5、6 个用手工构造的 ModCandidate 验证无法解析的区间与无法解析的目标版本都判 UNPARSEABLE、不会被算成 SATISFIED；第 7 个验证软依赖缺失记 MISSING_ID 且不致命；第 8 个验证两次解析结果完全一致；第 9 个是真实数据里程碑（iris→sodium 的 `0.8.x` 必须满足、e4mc→neoforge 的 `*` 必须满足、indium→sodium 0.5.11 必须 VERSION_MISMATCH、iris→embeddium 的 breaks 必须违反、biomesoplenty→terrablender 通过另一个变体的真实版本满足、sodium 的数组区间写成 `1.21;1.21.1`）。内置样本模式下断言依赖行数为 0（两个 BOM 样本没有声明任何依赖），不用 assume 跳过。
- src/test/java/dev/modgraph/report/DependencyCsvTest.java：2 个测试，验证表头与 SPEC §3.5.6 一致、缺失版本写空单元格、含逗号的 Maven 区间被正确加引号、空结果也写出表头。
- src/test/resources/fixtures/fabric-bom.jar：真实 zip 文件，里面只有一个 fabric.mod.json，文件内容是合法的 Fabric 元数据，但开头有三个 BOM 字节 EF BB BF；用于验证 BOM 不会导致 Fabric 解析失败。
- src/test/resources/fixtures/neoforge-bom.jar：真实 zip 文件，里面只有一个 META-INF/neoforge.mods.toml，文件内容是合法的 NeoForge 元数据，但开头有三个 BOM 字节 EF BB BF；用于验证 BOM 不会导致 TOML 解析失败。
- src/test/resources/fixtures/neoforge-invalid-ordering.jar：真实 zip 文件，META-INF/neoforge.mods.toml 里有一条依赖的 ordering="SIDEWAYS"；用于验证未知 ordering 值会 FAILED + MALFORMED_TOML，而不是静默变成 NONE。
- src/test/resources/fixtures/neoforge-invalid-side.jar：真实 zip 文件，META-INF/neoforge.mods.toml 里有一条依赖的 side="CLIENT_AND_SERVER"；用于验证未知 side 值会 FAILED + MALFORMED_TOML，而不是静默变成 UNKNOWN。
- src/test/resources/fixtures/fail-not-a-zip.jar：不是 zip 的普通文本文件（74 字节），扩展名是 .jar 所以会被扫描器当作输入；触发 NOT_A_ZIP。
- src/test/resources/fixtures/fail-no-metadata.jar：真实 zip，只有一个 assets/sample/readme.txt；同时没有 fabric.mod.json 和 neoforge.mods.toml，两个解析器都触发 NO_METADATA。
- src/test/resources/fixtures/fail-malformed-json.jar：真实 zip，fabric.mod.json 是截断的 JSON（结尾少了一个花括号）；触发 MALFORMED_JSON。
- src/test/resources/fixtures/fail-malformed-toml.jar：真实 zip，META-INF/neoforge.mods.toml 的表头写成 `[[mods]`（少一个方括号），是真正的 TOML 语法错误；触发 MALFORMED_TOML。和已有的两个非法枚举 fixture 区分开：那两个是值非法，这个是语法非法。
- src/test/resources/fixtures/fail-missing-id.jar：真实 zip，fabric.mod.json 合法但缺少必填的 id；触发 MISSING_REQUIRED_FIELD。
- src/test/resources/fixtures/fail-corrupt-entry.jar：真实 zip，中央目录完好、fabric.mod.json 条目存在，但该条目的 deflate 数据首字节被改成 BTYPE=3 的保留块类型；打开 zip 和列条目都正常，读条目内容时抛 ZipException，因此触发 IO_ERROR 而不是 NOT_A_ZIP。
- src/test/resources/fixtures/fail-internal-error.jar：真实 zip，fabric.mod.json 的 schemaVersion 写成 `1e9999999999`；这个数值让 Gson 的数值兜底路径抛 NumberFormatException，触发 INTERNAL_ERROR。它不是“数据被正确归类”的样本，只是真实异常路径的验证样本。

## 三、已完成的工作（按时间顺序）

1. 读了 SPEC.md、README.md、CONTRIBUTING.md，确认 M0 的边界、输出格式、日志等级和工程纪律。
2. 用文件系统真实清点过测试数据；最初是 fabric 194 个、neoforge 10 个、合计 204 个。后来数据集发生重组，第四步补充返工时是 fabric 100 个、neoforge 95 个、合计 195 个。代码和测试都没有硬编码任何一版数字。
3. 处理了本机 Windows 文件权限问题（详见“踩过的坑”），使仓库可以正常读写。
4. 用户手动修好了 wrapper、.gitignore 和垃圾文件；之后确认本机 Gradle 8.10 可用，构建统一用 gradle build。
5. 第一步建了 build.gradle.kts：Java 21、application 插件、SLF4J 2.0.16 + Log4j2 2.24.1、Gson 2.11.0、night-config toml 3.6.7、JUnit 5.11.3、AssertJ 3.26.3；测试用 JUnit Platform 运行。
6. 第一步建了 src/main/resources/log4j2.xml 和 Main.java，Main 最初只打印 “modgraph M0 bootstrap ready”。
7. 静态检查最初想用 Checkstyle，但插件门户网络不通；按用户建议改用 ArchUnit（只从 Maven Central 取依赖），删掉了不再使用的 config/checkstyle/checkstyle.xml。
8. 建了 StandardStreamPolicyTest，并做了负向验证：临时写了一个故意使用 System.out.println 和 printStackTrace() 的探针类，测试确实失败并报出 2 处违规；随后删除探针，重新构建恢复通过。
9. 把 README 的 “## Build” 一节改成 gradle build（因为仓库不再使用 wrapper）。
10. 第一步验收结果：gradle clean build 退出码 0；测试 1 个，通过 1 个，失败 0 个，跳过 0 个；gradle run 输出 “modgraph M0 bootstrap ready”；把日志等级设为 ERROR 时这行 INFO 消失，说明 -Dlog.level 开关确实生效。
11. 第二步按 SPEC.md 第一节和第四节建了 ModCandidate、Constraint、八个枚举、ScannedJar 和 JarScanner；扫描器递归找出全部 .jar、按路径排序、算 SHA-256、只读 zip 条目名；Main 每 jar 一行 DEBUG，结束一行 INFO。
12. 第二步的测试验证了记录数等于文件系统真实 .jar 数、每条记录都有必填字段和条目名、解析字段为空、条目名和 zip 中央目录一致；当时合计 5 个测试，失败 0 个，跳过 0 个。
13. 第三步重新通读了 SPEC.md v0.3，重点看了 §2.4：Mixin 目标索引只从明文 mixin 配置里的实现类名做命名约定推断，冲突预测分档，并明确标注“静态推断，非运行结论”。这些本步只理解，没有写任何索引、比对或输出逻辑。
14. 第三步对 ModCandidate 做了逐字段核对：按 SPEC.md v0.3 §1 的表格，从 sourceFile 到 unknownFields 一共 24 个字段；补上了 mixinTargets（字符串列表）和 mixinTargetsInferred（布尔值），位置严格插在 mixinConfigs 和 nestedJars 之间；JarScanner 给它们传空列表和 false。
15. 第三步确认了已知问题：JarScanner 遇到不可读或非 zip 文件会抛异常终止，违反“单个 jar 失败不影响其余、必须产 FAILED、OK+FAILED==输入数”的约束；按用户要求本步不修，留到第 7 步统一处理。
16. 第四步按 SPEC.md §2.1 写了 FabricMetadataParser：读取根目录 fabric.mod.json，填 modId、version、name、side、depends、recommends、breaks、provides、entrypoints、mixinConfigs、nestedJars；depends 和 breaks 的 required 填 true，recommends 填 false；versionRanges 保持原始字符串，没有做任何区间求值、裁剪或规范化；side 按 environment 映射，缺失时留空。
17. FabricMetadataParser 处理了 fabric.mod.json 里常见的几种写法：mixins 可以是单个字符串、字符串数组，也可以是含 config 字段的对象数组；jars 的每一项是含 file 字段的对象；depends / recommends / breaks 的值可以是单个字符串，也可以是字符串数组。未知顶层字段全部记入 unknownFields 并打 WARN。
18. FabricMetadataParser 的失败处理：没有 fabric.mod.json 记 NO_METADATA；JSON 非法记 MALFORMED_JSON；缺少 schemaVersion、id 或 version，或者 schemaVersion 小于 1，记 MISSING_REQUIRED_FIELD；读取 zip 失败记 IO_ERROR；不是有效 zip 记 NOT_A_ZIP。每种失败都填 failureMessage、打一条 ERROR、标记 FAILED，然后返回记录让主循环继续，解析器自身不向外抛异常。一个 jar 只打一条 ERROR。
19. 第四步最初在旧数据集上得到过 100 个 OK、94 个 FAILED（92 个 NO_METADATA 加 2 个 MALFORMED_JSON，后者是 Debugify 和 EuphoriaPatcher 的旧 fabric 版本）。这份清单是历史结果，当前数据集已经不是那样，不能拿它当后续交付数字。
20. 用户补充了三个真实数据坑后，第四步重新清点当前数据：fixtures/jars/fabric 100 个 jar，全部带合法的 fabric.mod.json，没有 BOM，没有 quilt.mod.json，没有 JSON 损坏；fixtures/jars/neoforge 95 个 jar。当前 Fabric 解析结果是 OK=100、FAILED=0。
21. 在 FabricMetadataParser.readMetadata 里增加了 BOM 处理：按 UTF-8 解码后，如果第一个字符是 U+FEFF，就去掉它再交给 JSON 解析器。这样 BOM 不会导致 JSON 解析失败。
22. 在 FabricMetadataParser 里增加了 NO_METADATA 的嵌套提示：如果条目名里出现 META-INF/jars/、META-INF/jarjar/ 或以 .jar 结尾的内层文件，failureMessage 会写“检测到内层 jar（jar-in-jar 嵌套），本步不扫描嵌套内容”；没有这些条目时也会写“可能是 jar-in-jar 嵌套打包”。failureCode 仍然是 NO_METADATA，没有误报成 MALFORMED_JSON。
23. 更新了 FabricMetadataParserTest：不再要求必须存在失败记录（当前 FAILED=0 时测试仍然有意义，只是失败路径的断言变成“如果有失败，就必须有 failureCode 和 failureMessage”）；新增一个使用真实 neoforge 样本的测试，动态找一个“没有 fabric.mod.json 但带内层 jar”的 jar，断言解析结果是 FAILED、NO_METADATA，并且消息里包含“嵌套”。
24. 第四步补充返工的验收结果：gradle clean test 退出码 0；FabricMetadataParserTest 5 个、JarScannerTest 4 个、StandardStreamPolicyTest 1 个，合计 10 个测试，失败 0 个，错误 0 个，跳过 0 个。随后 gradle run 退出码 0，INFO 摘要为 FABRIC: OK=100 FAILED=0。
25. 第五步先用 Python 的 tomllib 对 95 个 neoforge jar 做只读盘点：91 个含 META-INF/neoforge.mods.toml，0 个只有 META-INF/mods.toml，4 个完全没有元数据；0 个 TOML 语法错误，0 个带 BOM；2 个声明多个 [[mods]]；76 个有顶层 [[mixins]]；[[jarJar]] 当前为 0。盘点还确认了依赖字段的真实变体：type 有 required、optional、incompatible，也有大写的 REQUIRED；有的用 required=true，有的用 mandatory=true，还有两条完全不写类型；versionRange 可能缺失或为空字符串。
26. 按 SPEC.md §2.2 写了 NeoForgeMetadataParser：优先读 META-INF/neoforge.mods.toml，找不到再读 META-INF/mods.toml；前者记 NEOFORGE，只有后者记 FORGE；取第一条 [[mods]]，多条目只取第一条并打 WARN；modId 和 version 是必填字段，缺失或类型错误分别记 MISSING_REQUIRED_FIELD 或 MALFORMED_TOML；version 原样保留，没有替换 ${file.jarVersion}。
27. NeoForgeMetadataParser 的依赖映射：先按 modId 找 dependencies 表，再兼容顶层带引号的整键写法（例如 YACL 的 “dependencies.yet_another_config_lib_v3”）；每条依赖的 modId 必填；versionRange 原样转成字符串列表，缺失时为空列表，不做区间判断；ordering 只认 NONE、BEFORE、AFTER，缺失时仍按 NONE；side 只认 BOTH、CLIENT、SERVER，缺失时仍按 UNKNOWN；但值不是已知字符串时不再静默替换，而是记 MALFORMED_TOML 并打 ERROR；是否必需按 type（required / optional / incompatible，忽略大小写）→ required 布尔 → mandatory 布尔 → 默认 required 的顺序判定。
28. NeoForgeMetadataParser 的其余字段：provides 从第一条 mods 的 provides 列表取；mixinConfigs 从顶层 [[mixins]] 的 config 取，只读路径；nestedJars 从 [[jarJar]] 的 path 或 jar 取；entrypoints 留空；mixinTargets 留空、mixinTargetsInferred 为 false；modType、invasiveness、supportPolicy 留空。license、issueTrackerURL、modproperties、accessTransformers 等 SPEC 未定义的顶层字段进入 unknownFields 并打一条 WARN；modLoader 和 loaderVersion 作为格式基础设施按已知字段处理，不映射。带引号的依赖整键也按已知字段处理，不进入 unknownFields。
29. 对 displayTest 做了保守映射：只有 NORMAL（忽略大小写）映射到 BOTH；其他真实值（None、NONE、IGNORE_ALL_VERSION、MATCH_VERSION、IGNORE_SERVER_VERSION）和缺失都按 UNKNOWN 或留空处理。当前 95 个 jar 里没有 NORMAL，不能凭空把其他值解释成 BOTH。本轮修正未知枚举值时只改了依赖的 ordering / side；displayTest 的这些值是 NeoForge 的真实取值，不是“非法枚举”，所以没有把它们判成 FAILED，否则会把 18 个正常 jar 误伤。
30. 对 mixinConfigs 做了一个明确决定：虽然 SPEC.md §2.2 的表格没有单独列这一行，但 §1 定义了这个字段、§2.3 也要用它，而且真实 NeoForge 元数据里 76 个 jar 有 [[mixins]]。因此按 [[mixins]].config 读取路径，只读名字，不读内容。这个决定已在交接报告中写明，用户已批准保留，SPEC §2.2 表由用户补上。
31. 在 NeoForgeMetadataParser.readMetadata 里增加了 TOML 的 BOM 处理：按 UTF-8 解码后，如果第一个字符是 U+FEFF，就先去掉再交给 TOML 解析器。解析器仍然只捕获单个 jar 的失败，填 failureCode、打一条 ERROR、标记 FAILED，然后继续；没有新增直接向外抛异常的路径。
32. 给 NeoForgeMetadataParser 加了 NO_METADATA 的嵌套提示：找不到 neoforge.mods.toml 和 mods.toml 时，如果条目名里有 META-INF/jars/、META-INF/jarjar/ 或内层 .jar，failureMessage 写“检测到内层 jar（jar-in-jar 嵌套），本步不扫描嵌套内容”；没有内层条目时也会写“可能是 jar-in-jar 嵌套打包”。failureCode 始终是 NO_METADATA，没有报成 MALFORMED_TOML。
33. 更新了 Main：新增 NEOFORGE_ROOT，分别调用 summarizeFabric() 和 summarizeNeoForge()，两个摘要都按 OK 数和 FAILED 数打印；逐 jar 的 DEBUG 行显示文件名和解析出的 modId。Fabric 部分仍然保持原来的 100/0 结果。
34. 在 src/test/resources/fixtures/ 下构造了两个真实 zip 文件作为 BOM fixture：fabric-bom.jar（215 字节）的 fabric.mod.json 以 EF BB BF 开头，neoforge-bom.jar（239 字节）的 META-INF/neoforge.mods.toml 也以 EF BB BF 开头。文件内容是合法元数据，不是 mock，也不是用别的字符模拟 BOM；后续第 7 步也可以继续用。
35. 新增 NeoForgeMetadataParserTest，共 6 个测试：真实计数 OK+FAILED；OK 记录的加载器家族和必填字段；FAILED 记录的 NO_METADATA 和嵌套消息；${file.jarVersion} 原样保留；mixinConfigs 只包含 .json 路径；mixinTargets 为空、mixinTargetsInferred 为 false。新增 MetadataBomTest，共 3 个测试：两个 fixture 的字节真的以 EF BB BF 开头；带 BOM 的 Fabric JSON 解析成功；带 BOM 的 NeoForge TOML 解析成功。两个测试类都没有使用 assume，也没有 mock。
36. 第五步验收：gradle clean test 退出码 0，BUILD SUCCESSFUL，6 个任务执行；JUnit XML 显示 FabricMetadataParserTest 5 个、MetadataBomTest 3 个、NeoForgeMetadataParserTest 6 个、JarScannerTest 4 个、StandardStreamPolicyTest 1 个，合计 19 个测试，失败 0 个，错误 0 个，跳过 0 个。这是第五步当时的数字；本轮新增 NeoForgeInvalidEnumTest 的 2 个测试后，总数是 21 个。
37. 随后 gradle run 退出码 0，BUILD SUCCESSFUL；摘要为 FABRIC: OK=100 FAILED=0，NEOFORGE: OK=91 FAILED=4。neoforge 的 OK+FAILED=95，等于文件系统真实 jar 数。运行日志里的 ERROR 只有这 4 条 NO_METADATA；其余 WARN 是 unknownFields 和多 [[mods]] 提示，不是失败。
38. 第五步的失败清单已经逐个核对：connector、Essential、kotlinforforge 两个副本，全部是 NO_METADATA，全部在 failureMessage 里提示了 jar-in-jar 嵌套；没有 MALFORMED_TOML、MISSING_REQUIRED_FIELD、IO_ERROR 或 NOT_A_ZIP。FORGE 分支因为当前 0 个 mods.toml 样本，只有代码实现，没有真实数据验证。
39. 更新了 PROGRESS.md：写入第五步的当前数据、文件树、NeoForge 解析器行为、BOM fixture、4 个失败记录、mixinConfigs 的决定和 FORGE 无样本的限制。
40. 修正了 .gitignore：原来的 `fixtures/` 会连带忽略 src/test/resources/fixtures/ 下的两个 BOM fixture，导致它们无法被提交；改成 `/fixtures/`，只忽略仓库根目录的数据集，测试资源不再被忽略。没有执行 git add 或 git commit，提交仍由用户完成。
41. 按用户 m00747 的裁决修正未知枚举值：依赖的 ordering 和 side 如果出现不认识的字符串，不再静默变成 NONE / UNKNOWN；解析器抛出一个数据失败，failureCode 记为 MALFORMED_TOML，failureMessage 和 ERROR 日志都写明是哪个字段、哪个值不认识。缺少字段时仍按原来的默认值处理，因为缺失不是“值非法”。
42. 在 FailureCode 里新增 INTERNAL_ERROR。它是第七个失败码，专门表示“程序内部异常”，和 NOT_A_ZIP、NO_METADATA、MALFORMED_JSON、MALFORMED_TOML、MISSING_REQUIRED_FIELD、IO_ERROR 这些“数据问题”分开；用户允许用 IO_ERROR 或新增码，这里选择新增码，因为报表上更容易区分问题来源。
43. 把 RuntimeException 兜底拆开：TOML 语法错误仍然走 ParsingException → MALFORMED_TOML；必填字段缺失仍然走 SPEC §4 的 MISSING_REQUIRED_FIELD；只有剩下的、无法归为数据问题的 RuntimeException 才走 INTERNAL_ERROR。INTERNAL_ERROR 的 failureMessage 固定以“这是程序异常不是数据问题：”开头，并带上异常类名；ERROR 日志也带异常类名和异常对象，让程序 bug 明确暴露。
44. 构造了两个非法枚举值的真实 zip fixture：neoforge-invalid-ordering.jar 里依赖的 ordering="SIDEWAYS"，neoforge-invalid-side.jar 里依赖的 side="CLIENT_AND_SERVER"。它们不是 mock，也不是用字符串假装 TOML；测试实际打开 zip、读取 META-INF/neoforge.mods.toml、交给 NeoForgeMetadataParser 解析。
45. 新增 NeoForgeInvalidEnumTest，两个测试分别验证上面两个 fixture：必须 FAILED + MALFORMED_TOML，failureMessage 必须包含字段名和非法值；测试还挂了一个 Log4j 收集器，断言解析时确实打出了 ERROR，而且 ERROR 文本里包含 MALFORMED_TOML、字段名和非法值。为了在测试里访问 Log4j Core 的 Appender，build.gradle.kts 增加了 testImplementation("org.apache.logging.log4j:log4j-core:2.24.1")；运行时依赖没有变化。
46. 修正验收：gradle clean test 退出码 0，合计 21 个测试、失败 0、错误 0、跳过 0；gradle clean build 退出码 0，BUILD SUCCESSFUL，10 个任务执行；gradle run 退出码 0，FABRIC: OK=100 FAILED=0，NEOFORGE: OK=91 FAILED=4。4 个失败仍然是原来那四个 NO_METADATA，没有新增失败，也没有把四个失败改成 INTERNAL_ERROR。
47. 第六步重新读了 SPEC.md §2.3、§2.4 和 §6，确认四档判定、已知渲染模组清单、modType 顺序、HIGH/MEDIUM/LOW/NONE 规则、conflicts.csv 表头、HIGH 的 WARN 格式和摘要中的“静态推断，非运行结论”。当时 SPEC §2.3 还把 RENDER/CORE 写成 DEGRADED，而用户 m00922 要求 RENDER/CORE 一律 EXCLUDED；本轮按用户指令执行，后来 SPEC v0.4 已把 supportPolicy 表改成 EXCLUDED，代码与 v0.4 一致。
48. 实现了 InvasivenessAnalyzer：只读 zip 条目名、元数据文本和 mixin 配置；按 RENDER → CORE → MIXIN → API 命中即停。RENDER 的已知 ID 清单按 SPEC §2.3；ID 比较时去掉 `-` 和 `_`，避免 `reeses-sodium-options` 和 `reeses_sodium_options` 被当成两个不同的模组。CORE 识别 `META-INF/coremods.json`、metadata 里的 transformer 和条目路径中的 `/asm/`；MIXIN 识别 mixinConfigs 非空或 `mixins.*.json`；其余是 API。信号字符串带 `knownRenderId:`、`entrypoint:`、`mixinConfigRender:`、`coremods:`、`metadata:transformer`、`asmEntry:`、`mixinConfig:`、`mixinEntry:` 前缀，方便追溯。
49. 实现了 mixin 配置读取和目标类推断：先按 candidate.mixinConfigs 在 jar 根目录找配置；找不到时，只在 `META-INF/jars/` 和 `META-INF/jarjar/` 的内层 jar 里定向找该声明配置，不递归扫其他嵌套内容。JSON 用 Gson 的 Strictness.LENIENT，因为 carryon 的配置里有 `//` 注释；先剥 BOM，再取 `mixins` / `client` / `server` 字符串数组；去掉包名后按 MixinPlugin → Mixin → Accessor → Invoker 的顺序剥后缀，得到简单名；mixinTargetsInferred 置为 true。配置缺失记 FAILED + IO_ERROR，JSON 非法记 MALFORMED_JSON，RuntimeException 记 INTERNAL_ERROR；失败只打一条 ERROR。
50. 实现了 ConflictPredictor：只取 status=OK；按 modId 合并同一模组的 Fabric / NeoForge 变体，目标取并集，侵入深度取更深的一档；两两比较，HIGH 是共同目标简单名非空，MEDIUM 是双方都 RENDER/CORE 但无交集，LOW 是一方 RENDER/CORE 另一方 MIXIN，NONE 是其余；HIGH 逐条打 WARN，返回全部 pair。当前 191 条 OK 聚合成 116 个唯一 modId，比较 6670 对。
51. 实现了 ReportWriter：用 Gson serializeNulls 写 candidates.jsonl；summary.csv 按 SPEC 表头写，包含全部 195 条（含 EXCLUDED 和 FAILED）；conflicts.csv 按 modA,modB,level,sharedTargets 写全部 6670 对；CSV 做转义。EXCLUDED 没有被剔除。
52. 重写了 Main：统一扫描 fixtures/jars 下两个目录，解析后调用分析器，写出三个文件，再打摘要。摘要包括 FABRIC、NEOFORGE、输入/OK/FAILED、四档侵入深度、支持策略、冲突预测和最后一行“本结果为准入检查，不构成运行稳定性保证”。Main 还会断言 OK+FAILED 等于输入数，不满足就记 ERROR 并抛出异常。
53. 新增 DescriptorAnalysisTest，开始时是 7 个测试，覆盖记录数、分析字段、四档分布、RENDER/CORE 的 EXCLUDED、mixinTargets 简单名和 inferred=true、sodium 嵌套配置和 carryon 注释配置、冲突分档、三个输出文件的行数/表头/EXCLUDED/HIGH/JSON inferred。没有 assume，也没有 mock；全部用当前 195 个真实 jar。稀有度规则补充后该类增加到 8 个测试。
54. 稀有度规则之前的第六步验收：gradle clean test 退出码 0，28 个测试、失败 0、错误 0、跳过 0；gradle clean build 退出码 0，14 秒，10 个任务；gradle run 退出码 0，6 秒。输出文件 candidates.jsonl 195 行、summary.csv 195 行数据加表头、conflicts.csv 6670 行数据加表头；EXCLUDED 16 个仍在 summary.csv 里。这里的 28 是历史数字，当前是 30。
55. 第六步（v0.4 时）的真实数据结果：RENDER 9、CORE 7、MIXIN 152、API 23；SUPPORTED 175、DEGRADED 0、EXCLUDED 16；HIGH 649、MEDIUM 63、LOW 1015、NONE 4943；v0.5 的前缀/10% 修正后已变为 HIGH 331、MEDIUM 1170、LOW 725、NONE 4444，见第 64 条。日志里有 16 条 RENDER/CORE 侵入 WARN、16 条排除 WARN、649 条 HIGH 冲突 WARN、4 条 NO_METADATA ERROR；这些数字和输出文件一致。
56. 更新了 PROGRESS.md 到第六步；没有执行 git commit，提交仍由用户完成。

57. 按用户 m01288 重读 SPEC.md v0.5 §2.4，升级目标推断：先去掉包路径，再同时剥开头的 `Mixin` 和尾部 `Mixin / MixinPlugin / Accessor / Invoker`；只有真正剥过前后缀的类名才加入 `mixinTargets`，避免把没有命名的普通类误当目标。修改后 BiomesOPlenty 的 `MixinBloodFluid` / `MixinLiquidNullFluid` 分别变成 `BloodFluid` / `LiquidNullFluid`，当前目标列表里不再有以 `Mixin` 开头的名字。

58. 目标覆盖率缺口从 54 个（54/191，28.3%）降到 10 个（10/191，5.24%），满足“缺口不超过 OK 总数 10%”的验收。剩余 10 个是 PresenceFootsteps、Searchables 的 fabric/neoforge 两个变体、VisualWorkbench 的两个变体、coroutil、supermartijn642configlib、Controlling、ForgeConfigAPIPort、embeddium；它们的配置数组为空或类名没有可剥的前后缀，属于命名约定推断的已知边界。

59. 把 ubiquitous 阈值从 25% 下调到 10%，`ConflictPredictor` 的公式改为 `(mods.size() + 9) / 10`，当前 116 个唯一模组得到 12。`Main` 新增两行：`ubiquitous 阈值：12（模组总数 116 × 10%，向上取整）` 和 `ubiquitous 目标类（14 个）：Minecraft 28 | GameRenderer 25 | Entity 19 | LevelRenderer 18 | MinecraftClient 18 | ClientPacketListener 17 | Gui 16 | LivingEntity 16 | Screen 15 | ClientLevel 13 | ItemStack 12 | MinecraftServer 12 | Player 12 | ServerLevel 12`。

60. 分档条件已由用户 m01468 裁决：采纳 all-rare。旧 v0.5 any-rare 字面得到 HIGH 810、MEDIUM 691、LOW 725、NONE 4444（12.14%，超过 5% 且高于基线 649）；当前 all-rare 得到 HIGH 331、MEDIUM 1170、LOW 725、NONE 4444（4.96%）。裁决理由是语义：共享目标里大部分是高频类、只有 1 个稀有，实际撞车大概率在高频类，判 HIGH 是噪声；all-rare 才表示撞的位置确实不寻常。SPEC v0.6 §2.4 已按此收紧，代码行为没有回退。

61. 前 10 组 HIGH（all-rare 版本的 conflicts.csv 顺序）：badoptimizations × nochatreports（ToastComponent）；badoptimizations × entityculling（BlockEntityRenderDispatcher;ClientWorld;WorldRenderer）；badoptimizations × fallingleaves（ParticleManager）；badoptimizations × malilib（WorldRenderer）；badoptimizations × xaeroworldmap（ClientWorld）；betterf3 × emi（Keyboard）；betterf3 × languagereload（ClientChunkManager;ClientChunkMap;Keyboard）；betterf3 × libipn（Keyboard）；betterf3 × malilib（Keyboard）；betterf3 × owo（Keyboard）。

62. 更新 DescriptorAnalysisTest：该类现在 9 个测试，新增“前缀式 Mixin 被正确剥离、没有 target 以 Mixin 开头”“覆盖率缺口不超过 10%”两个断言；HIGH 断言改为共享目标全部不是 ubiquitous，MEDIUM 断言为共享目标全是 ubiquitous，HIGH 比例上限改为 5%；阈值公式改为 `(modCount + 9) / 10`，并断言至少有一个 ubiquitous；Top 10、三个输出文件的行数/表头/EXCLUDED/HIGH 行数继续保留。

63. 验收结果：`gradle clean test` BUILD SUCCESSFUL 11 秒，30 个测试、失败 0、错误 0、跳过 0；其中 DescriptorAnalysisTest 9、FabricMetadataParserTest 5、MetadataBomTest 3、NeoForgeInvalidEnumTest 2、NeoForgeMetadataParserTest 6、JarScannerTest 4、StandardStreamPolicyTest 1。`gradle clean build` 退出码 0，12 秒，10 个任务；`gradle run` 退出码 0，输出 candidates.jsonl 195 行、summary.csv 196 行（含表头）、conflicts.csv 6671 行（含表头），EXCLUDED 16 条仍保留。

64. 当前真实分布：195 个输入、191 个 OK、4 个 FAILED；RENDER 9、CORE 7、MIXIN 152、API 23；SUPPORTED 175、DEGRADED 0、EXCLUDED 16；冲突 HIGH 331、MEDIUM 1170、LOW 725、NONE 4444。4 个 FAILED 仍是 connector、Essential、kotlinforforge-all-2、kotlinforforge-all，全部 NO_METADATA。本轮没有执行 git commit；提交仍由用户完成。

65. 按用户 m01468 的要求，把 `ConflictPredictor` 的分档注释同步为 SPEC v0.6 §2.4 的 all-rare 表（HIGH 全部非 ubiquitous、MEDIUM 全 ubiquitous 或双方 deep 无交集、LOW 一方 deep 一方 MIXIN、NONE 其余），这是注释修改，没有改变任何判定分支或输出数字。同步时发现本地 `SPEC.md` 版本头仍是 v0.3、§2.4 仍是 any-rare 文字；没有自行修改需求文件，已在交接报告中说明，等用户同步。

66. 第七步开工前重新校验 `SPEC.md`：第一次读到版本头仍是 `版本：v0.3`、§2.4 仍是 any-rare，按用户“不是 v0.6 就停下来”的要求停工并报告；用户确认已更新后重读，版本头是 `版本：v0.6`、§2.4 是 all-rare（共享目标全部不是 ubiquitous 才判 HIGH），与代码行为一致，才继续。

67. 修掉从第二步遗留的失败路径问题：`JarScanner` 不再让单个文件的异常冒出去。具体是把它拆成“目录遍历 + 逐文件扫描”两层，`scan(root)` 只在目录本身无法遍历时抛 IOException；每个文件走 `scanOne`，读文件算 SHA-256 失败记 IO_ERROR，`new ZipFile` 抛 ZipException 记 NOT_A_ZIP，其他 IOException 记 IO_ERROR，RuntimeException 记 INTERNAL_ERROR。每种失败都填 failureCode / failureMessage、打一条 ERROR、产出一条 FAILED 记录并继续下一个文件。这满足 SPEC §4 的“不得跳过任何输入文件”和 OK+FAILED 恒等于输入数。

68. 处理了两个解析器与扫描阶段的边界：Fabric 和 NeoForge 解析器进入 `parse` 后先看扫描结果是不是已经 FAILED，是就原样返回，否则一个非 zip 文件会先被 Scanner 正确记成 NOT_A_ZIP，又被解析器改写成像模像样的 NO_METADATA，用户看到的原因就和实际不符。同时把“文件是有效 zip、但读条目内容时抛 ZipException”的情况单独归为 IO_ERROR（NOT_A_ZIP 意思是文件不是 zip，这里是 zip 但数据坏了，两者不能混），做法是在两个解析器的 `readMetadata` 里捕获 ZipException 并转成 IO_ERROR。

69. 顺手修正了一个同类问题：Fabric 解析器原来把所有未分类的 RuntimeException 归成 MALFORMED_JSON，等于把程序 bug 说成“这个 jar 的 JSON 坏了”。现在和 NeoForge 一致，归 INTERNAL_ERROR，消息仍以“这是程序异常不是数据问题：”开头并带异常类名，且一个 jar 只打一条 ERROR（内部失败走不重复打日志的构造分支）。

70. 构造了 7 个真实 zip 文件作为失败 fixture，全部放在 src/test/resources/fixtures/ 下，不是 mock，也不是用字符串假装 zip：`fail-not-a-zip.jar`（普通文本）、`fail-no-metadata.jar`（只有 assets 条目）、`fail-malformed-json.jar`（截断 JSON）、`fail-malformed-toml.jar`（表头写成 `[[mods]` 的真语法错误）、`fail-missing-id.jar`（缺 id）、`fail-corrupt-entry.jar`（条目 deflate 数据首字节改成保留块类型 BTYPE=3）、`fail-internal-error.jar`（schemaVersion 为 1e9999999999）。原有的 fabric-bom、neoforge-bom、neoforge-invalid-ordering、neoforge-invalid-side 四个 fixture 全部保留，一个没删。

71. 新增 FailurePathTest，11 个测试：每个磁盘 jar 恰好一条记录；OK + FAILED 恒等于输入数；损坏文件不中断整批；失败记录字段齐全；扫描失败的 ERROR 恰好一条且含 NOT_A_ZIP；解析器不改写扫描失败；Fabric 侧 6 种码、NeoForge 侧 3 种码逐一对上 fixture；7 种码全部有样本；损坏条目样本仍是有效 zip；INTERNAL_ERROR 来自真实异常路径；条目损坏只有一条 ERROR。同时把 JarScannerTest 的“每条记录都是 OK”断言改成按状态分支，避免在测试资源目录里加入损坏文件后测试自相矛盾。

72. 在 Main 的摘要里新增一行失败码分布（`失败分类：NO_METADATA: 4`，没有失败时是“无”），这样 `gradle run` 里能直接看到每种失败码各多少个，不用去翻 CSV。这一行是 SPEC §6.4 要求的失败分类统计，不是新增功能。

73. 第七步验收：`gradle clean test` BUILD SUCCESSFUL，41 个测试、失败 0、错误 0、跳过 0；分布是 DescriptorAnalysisTest 9、FailurePathTest 11、FabricMetadataParserTest 5、MetadataBomTest 3、NeoForgeInvalidEnumTest 2、NeoForgeMetadataParserTest 6、JarScannerTest 4、StandardStreamPolicyTest 1。`gradle build` 退出码 0。`gradle run` 退出码 0，真实数据仍是 195 个输入、OK 191、FAILED 4，`191 + 4 = 195`，失败分类全部是 NO_METADATA。

74. 用户任务三里手动放在 fixtures/jars/fabric 的三个损坏文件（`__broken_notazip.jar`、`__broken_badjson.jar`、`__broken_noid.jar`）当前**未找到**；`Get-ChildItem fixtures/jars/fabric -Filter "__broken*"` 没有任何结果，fabric 目录仍是 100 个 jar、OK=100 FAILED=0。按用户要求没有自己造数据放进 fixtures/jars，所以真实数据里的 FAILED 仍是 4 个（都来自 neoforge 的 NO_METADATA），三个损坏样本的失败码只在 src/test/resources/fixtures 的测试里验证过。

75. 第八步开工前修了一个测试脆弱性：用户临时放入三个损坏 jar 后，`JarScannerTest.parsingFieldsAreStillEmpty()` 在断言 `failureCode` 为空时失败，因为该方法对**全部**记录断言“解析字段为空”，而 FAILED 记录的 failureCode / failureMessage 必然非空。这是测试依赖输入数据干净的缺陷，不是生产代码缺陷。改法：`parsingFieldsAreStillEmpty` 先过滤出 `status == OK` 的记录并断言列表非空，再只对它们断言字段为空；`scanProducesOneCandidatePerJarFileOnDisk` 增加 `status` 只能是 OK/FAILED 与 `OK + FAILED == 文件系统真实 jar 数` 两条断言。没有用 assume、没有跳过、没有缩小扫描范围，扫描根目录仍是 fixtures/jars。

76. 任务一（§7 第 2 条，元数据一致性）：用固定随机种子 20261031 抽出 5 个 Fabric + 5 个 NeoForge jar，解包读原始 fabric.mod.json / neoforge.mods.toml，与 summary.csv（modId、version）和 candidates.jsonl（depends、recommends、breaks）逐条比对。modId 10/10 一致、version 10/10 一致、depends 10/10 一致（Fabric 的 depends 在契约里对应 required=true，NeoForge 的 optional 对应 recommends，这是字段映射不是值变化）。summary.csv 没有 depends 列，所以 depends 只能用 candidates.jsonl 核对，报告中已注明。

77. 任务二（§7 第 5 条，侵入深度与 modType）：抽了 sodium、iris、embeddium、konkrete、entity_model_features、xaerominimap（Fabric）、xaerominimap（NeoForge）、architectury、fabric-api、badoptimizations 共 10 个模组，逐个列出 invasiveness、modType、命中的 invasivenessSignals 和判定理由。9 个符合预期；sodium 的 `modType` 是 LIBRARY 而不是 OPTIMIZER，原因是它声明了 `provides: ["indium"]`，而代码按 SPEC 表格行序先判 LIBRARY。这是规则优先级问题，不是解析错误，但语义上 sodium 更像 OPTIMIZER，标注为“不确定是否是期望行为”。

78. 任务三（§7 第 7 条，Mixin 目标索引）：抽 badoptimizations、carryon、controlling、visuality、dynamic_fps 五个模组逐类对照，另加 architectury 和 sodium 两个补充样本。前缀式（`MixinXxx`）、后缀式（`XxxMixin`/`XxxAccessor`/`XxxInvoker`）剥除都正确，重复类名会去重（badoptimizations 的 GameRenderer 出现两次只留一个，dynamic_fps 三个配置合并成 10 个目标），`mixinTargetsInferred` 在 191 条 OK 记录上全部为 true（符合 SPEC“M0 恒为 true”）。发现三类问题：sodium 的两个嵌套 Mixin 类推断错误；carryon 的 `PlayerMixinFabric` 中缀命名未被识别（但 Player 已由另一个配置覆盖，目标未缺）；controlling 的 5 个 `AccessXxx` 类全部被跳过，NeoForge 版本 `mixinTargets` 为空。

79. 任务四（§7 九条验收标准）：第 1 条未达成（195 个 jar，要求 200；NeoForge 95 个，要求 100）；第 2、3、5、6、8、9 条达成；第 4 条文字写“6 种 failureCode”，实现是 7 种（第七步按用户指示验证了 INTERNAL_ERROR），按第七步口径算达成但 SPEC 文字未同步；第 7 条抽检后判定为“部分达成”，因为 sodium 有 2 处错误目标名。第 8 条的 HIGH 是 331/6670 = 4.96%，低于 5% 上限，也低于未启用稀有度规则的基线 649/6670 = 9.73%，Top 10 高频目标已打印。

80. 第八步没有改任何生产代码（src/main 零改动），只改了 JarScannerTest 的两个测试方法；临时核对脚本放在仓库外并在完成后删除，没有把任何自动生成的数据放进 fixtures/jars。

81. 第九步修掉缺陷一（嵌套类 Mixin 后缀剥除）。改法：`InvasivenessAnalyzer.addTarget` 先去掉包名；类名含 `$` 就按 `$` 切分成段，每段调用新增的 `stripAffixes` 独立剥前缀 `Mixin` 和后缀 `Mixin` / `MixinPlugin` / `Accessor` / `Invoker`，只要任一段被剥过就把拼回的整名加入目标集合；不含 `$` 的类名完全走原来的路径（先剥前缀再剥后缀），行为不变。后缀匹配仍按 `MixinPlugin` → `Mixin` → `Accessor` → `Invoker` 的顺序，每段只剥一个后缀。

82. 修复前 sodium 的 fabric 与 neoforge 两个版本各有 2 个错误目标：`VertexMultiConsumerMixin$Double`、`VertexMultiConsumerMixin$Multiple`（来自配置里的 `core.render.immediate.consumer.VertexMultiConsumerMixin$DoubleMixin` 和 `$MultipleMixin`）。修复后两个版本都变成 `VertexMultiConsumer$Double`、`VertexMultiConsumer$Multiple`，`mixinTargets` 里不再有任何含 `Mixin` 的字样，也不再残留 `VertexMultiConsumerMixin`。目标集合里没有单独的 `VertexMultiConsumer` 裸名——按规则拼回的是 `VertexMultiConsumer$Double` 这种形式，用户已明确接受这个结果。

83. 修复没有改变任何统计数字：sodium 的 target 数仍是 fabric 65、neoforge 74（换名不换数量）；覆盖率缺口仍是 10/191（5.24%），缺口名单未变；冲突预测仍是 HIGH 331、MEDIUM 1170、LOW 725、总对数 6670，HIGH 占比 4.96%，Top 10 高频目标也未变。原因：这两个幽灵目标只被 sodium 自己引用，改成真名后没有和其他模组产生新的共享。

84. 缺陷二（中缀式 `PlayerMixinFabric`）和缺陷三（`AccessXxx` 前缀，controlling 的 5 个类）按用户要求**不修**，只作为已知局限记录，理由也按用户给的原话：中缀剥除无法判断边界，强行剥会引入误报；`AccessXxx` 前缀与 `Accessor` 后缀是不同命名习惯，SPEC 只定义了后缀，贸然加前缀规则可能误伤以 `Access` 开头的正常类名。因此 controlling 的 NeoForge 版本 `mixinTargets` 仍为空，仍计入那 10 个覆盖率缺口。

85. 第九步新增 1 个回归测试 `DescriptorAnalysisTest.nestedMixinClassNamesAreStrippedSegmentBySegment`：对两个 sodium 记录断言必须包含 `VertexMultiConsumer$Double` / `VertexMultiConsumer$Multiple`、必须不包含修复前的两个旧名字、且目标集合里不允许出现任何含 `Mixin` 的字样。这条测试用的是仓库里已有的真实 sodium jar，没有新建 fixture，没有 mock。第九步没有动分级规则、冲突阈值、`Top 10` 逻辑，也没有动 `src/main` 的其他文件。

86. 第十步 README 收尾，新增三节：`What this does / does not do`（声明性冲突能预测、行为性冲突不能预测、输出定位，外加一段“M0 只做元数据解析与描述符级预测，版本区间求值和拓扑排序属 M0.5/M1，别把目标当现状”的边界说明）、`Known limitations`（命名推断不是注解、中缀式与 `AccessXxx` 未处理、RENDER/CORE 一律 EXCLUDED、jar-in-jar 判 NO_METADATA、FORGE/HACK_CLIENT 无真实样本）、`Dataset`（fixtures 在 .gitignore、需要自备、四个脚本的采集流程、不写死数量）。同时修正 README 里与仓库事实不符的地方：快速开始引用的 `collect_fixtures.py` 并不存在，改成指向 Dataset 的四个真实脚本；`六种失败分类` 改成七种；架构图里的 `InvasivenessScanner` / `Writer` 改成真实类名 `InvasivenessAnalyzer` / `ReportWriter`；数据集合规里“以校验和校验”改成“逐个确认能打开且含元数据，sha256 记录在 manifest 里”。

87. 第十步核对 PROGRESS 五部分完整：一、当前阶段与目标；二、文件树；三、已完成的工作（85 条）；四、踩过的坑（52 条）；五、下一步建议。用户点名的七类坑逐一确认在册：BOM（坑 2、6）、jar-in-jar（坑 3）、Sinytra Connector 的 fart 发现（坑 4）、ubiquitous 阈值 25%→10%（坑 36、59）、any-rare→all-rare（坑 38、60）、嵌套类残留（坑 49、50）、SPEC 版本未同步那次（坑 41、条目 66）。第五节原有的重复段落（两段“下一步等用户指示”）已在本次定稿中合并。

88. 第十步最终自检：`gradle clean test` BUILD SUCCESSFUL（44 秒），42 个测试、失败 0、错误 0、跳过 0；分布是 DescriptorAnalysisTest 10、FailurePathTest 11、FabricMetadataParserTest 5、MetadataBomTest 3、NeoForgeInvalidEnumTest 2、NeoForgeMetadataParserTest 6、JarScannerTest 4、StandardStreamPolicyTest 1。按 SPEC v0.7 §7 十条逐条核对：第 1 条实际 195 个 jar（100 + 95），全部产出记录，OK 191 + FAILED 4 = 195；第 2 条 10/10；第 3 条有测试显式断言；第 4 条 7 种码全部有真实 fixture；第 5 条 10 个模组按规则判定，sodium 的 modType 有 1 处语义争议；第 6 条 ArchUnit 通过；第 7 条 5 个模组抽检、inferred 191/191 true、摘要字样存在、全域 0 个含 `Mixin` 的残留目标；第 8 条 4 条真实 FAILED 记录 + 11 个失败路径测试；第 9 条 HIGH 331/6670 = 4.96%；第 10 条缺口 10/191 = 5.24%。

89. 第十一步 README 定稿。先用 `gradle run` 抓真实输出替换「输出产物」里整段假示例（旧值 200/193/7/172/28/0 和两行 M0 根本没有的“硬依赖总数”“环”全部删掉），换成当前 195 个 jar 的实跑结果：FABRIC 100/0、NEOFORGE 91/4、输入 195/OK 191/FAILED 4、失败分类 NO_METADATA 4、侵入深度 API 23/MIXIN 152/CORE 7/RENDER 9、支持策略 175/0/16、Top 10、阈值 12、HIGH 331/MEDIUM 1170/LOW 725。`conflicts.csv` 补进产物表，并新增「冲突预测」小节说明聚合方式、ubiquitous 阈值、四档条件，以及“静态推断，非运行结论”这条必须保留的声明。「当前状态」里 M0 改成“已完成（2026-10-31，195 个真实 jar）”。

90. 第十一步新增 `Seeking collaborator` 一节（放在「贡献」之前），写明 M0 已完成的四项事实、能提供的规格/数据流程/工程纪律/决策记录，以及 M2 需要的 JVM 底层能力（类加载、字节码、Mixin/ASM、崩溃栈归因）和“我不会 Java，这部分补不上”，并明确这不是求人写代码而是找能做技术判断的合作者。

91. 第十一步解决 clone 即红：新增测试辅助类 `dev.modgraph.testsupport.DatasetRoot`，`fixtures/jars` 不存在或其中没有 jar 时回退到 `src/test/resources/fixtures`（11 个内置样本）；`JarScannerTest`、`FabricMetadataParserTest`、`NeoForgeMetadataParserTest`、`DescriptorAnalysisTest` 四个依赖数据集的类都改用这个入口，并在 `@BeforeAll` 打一行 INFO 说明当前用的是完整数据集还是内置样本。没有用 assume，跳过数仍为 0；两种模式都断言 `OK + FAILED == 文件系统真实列举数`。实测：有完整数据集时 195 个 jar、42 个测试全绿；把 `fixtures` 临时移走后 11 个内置样本、42 个测试同样全绿、失败 0、跳过 0。

92. 第十一步全文一致性核对，修掉的事实错误：README 里“M0 产出依赖图”改成“依赖声明（不做求解与拓扑排序）”；“M0 只有三层”改成“四步”；“外挂类与 OptiFine EXCLUDED”补上 RENDER/CORE 也一律 EXCLUDED；工程纪律第 3 条“测试资源缺失时构建必须失败”改成“回退内置样本，连内置样本都缺失才失败”；数据集合规里“本仓库不包含任何模组 jar”改成“不包含任何真实模组 jar，仓库内有 11 个自制测试样本”。此前已修过的 `collect_fixtures.py`、六种/七种、`InvasivenessScanner`/`Writer`、校验和表述不再重复列出。

93. 第十二步先修 CONTRIBUTING.md 三处与仓库事实不符的表述：第 49 行“六种失败分类”改成七种（第七步已加入 INTERNAL_ERROR）；第 42-43 行“测试资源缺失时构建必须失败”改成“回退到 src/test/resources/fixtures 的内置样本，内置样本也缺失才允许失败”，与 README、DatasetRoot 的实际行为一致；第 115 行 `./gradlew build` 改成 `gradle build`（仓库没有 wrapper）。这三处只改文档，没有动生产代码。

94. 第十二步实现版本比较 `version/Version`：按 `.` 和 `-` 切段；数字段先去除前导零、再按“长度 → 字典序”比较，因此不会因整数溢出出错；非数字段按字典序；缺失段补 `0`；数字段恒大于非数字段，所以 `-beta` 排在同版本正式版之前。`+` 之后的构建元数据在比较前被剥离，但 `source()` 保留原串，CSV 里写的仍是 `0.8.13+mc1.21.1` 这种真实值。含 `${` 或 `#` 的构建期占位符判不可解析。

95. 第十二步实现两套区间语法：`FabricVersionRange` 与 `NeoForgeVersionRange`。Fabric 侧覆盖 `*`、`>=`/`>`/`<=`/`<`/`==`/`=`、`~`、通配段、空格 AND；NeoForge 侧覆盖 Maven 方括号/圆括号区间、精确区间、无括号软要求、区间内空格、上界为空的 `[a,]`、以及并集。裸版本串在 Fabric 是精确匹配、在 NeoForge 是“最低版本”，两套语法没有合并实现；无括号形式解析后用 DEBUG 打出“按 Maven 软要求约定解释为 >=”。

96. 第十二步实现 `resolve/EnvironmentFacts`：声明 minecraft=1.21.1、fabricloader=0.20.1、quilt_loader=0.27.0、neoforge=21.1.262、forge=52.0.0、java=21，并支持 minecraft_version / fabric-loader / quilt-loader 三个别名。声明依据写在类注释里：minecraft 是数据集目标版本，fabricloader 与 neoforge 取数据集内所有同类依赖要求的**下界最大值**，java 取 SPEC §8 的 21，quilt_loader / forge 当前没有数据引用、只为满足“§3.5.2 的 ID 绝不能被判 MISSING_ID”而声明。启动时 INFO 打印一行，业务代码不引用常量，只接收对象。

97. 第十二步实现 `resolve/DependencyResolver`：只取 OK 记录，按 depends → recommends → breaks 逐条产出结果，716 条声明对应 716 行，不去重不省略。目标只从“数据集 modId”和“环境 ID”两处解析。同一 modId 的多个变体按“任一版本满足即 SATISFIED”；多条区间原串按“任一满足”；`breaks` 目标不存在记 SATISFIED；区间或目标版本无法解析时记 UNPARSEABLE 并打 WARN（带原串）。

98. 第十二步在 `ReportWriter` 增加 `writeDependencies`，在 `Main` 增加环境事实打印、依赖判定调用与 §3.5.6 要求的三行统计；`dependencies.csv` 表头为 `modId,version,depId,depKind,declaredRange,resolvedVersion,outcome`，缺失版本写空单元格。`.gitignore` 加入 `dependencies.csv`，与其他三个产物一致，不提交生成物。

99. 第十二步验收数字（`gradle run` 实跑）：716 条依赖，硬 531 / 软 46 / 冲突 139；SATISFIED 612、MISSING_ID 90（硬 68 + 软 22）、VERSION_MISMATCH 7、BREAKS_VIOLATED 7、UNPARSEABLE 0。环境 ID 行 374 条全部满足、MISSING_ID 0 条。M0 三个输出文件在改动前后的 SHA-256 完全相同（conflicts.csv `E83E2326…E8D7`、summary.csv `318E418A…E453`、candidates.jsonl `85905594…0A0`），HIGH 331 / MEDIUM 1170 / LOW 725 与覆盖率缺口 10/191 未变。

100. 第十二步测试：`gradle clean test` 88 个测试、失败 0、错误 0、跳过 0；`gradle clean build` 退出码 0。新增 VersionTest 11、FabricVersionRangeTest 10、NeoForgeVersionRangeTest 10、EnvironmentFactsTest 4、DependencyResolverTest 9、DependencyCsvTest 2，原有 42 个全部保留。没有使用 assume、没有 mock 文件读取、没有临时生成 jar。

101. 第十二步人工对照：把 candidates.jsonl 按顺序重建成 716 条期望行，与 dependencies.csv 逐行比对，`modId`/`version`/`depId`/`depKind`/`declaredRange` 字段差异 0；再抽 10 条（iris→sodium、sodium→minecraft 数组、e4mc→neoforge `*`、biomesoplenty→terrablender、indium→sodium、iris(neoforge)→minecraft、jei(neoforge)→minecraft、iris→embeddium breaks、sodium(neoforge)→embeddium breaks、farmersdelight→crafttweaker 软依赖），按每条对应的源 jar 读取原始 fabric.mod.json / neoforge.mods.toml 对照，四项一致 10/10。完整对照表在交接报告里，不在本文件重复贴。

102. 第十二步没有执行 git commit；`CONTRIBUTING.md`、`Main.java`、`ReportWriter.java`、`.gitignore` 修改与 `resolve/`、`version/`、三个新测试包、`dependencies.csv` 均在等待用户提交。README.md 当时仍写着“M0.5 Not started”，本步按用户指令只更新 PROGRESS.md，没有改 README，留给下一轮定稿。

103. 第十三步只改文档，生产代码与测试零改动。两份 README 同步 M0.5：状态表 M0.5 改为已完成（2026-11-01，195 个真实 jar，716 条依赖声明）；产物表补 `dependencies.csv`（表头 `modId,version,depId,depKind,declaredRange,resolvedVersion,outcome`）；新增「依赖可满足性判定 / Dependency satisfiability (M0.5)」小节，写明六个环境事实、五种 outcome 与真实数字（531 / 46 / 139；612 / 90 / 7 / 7 / 0）；新增「已知边界 / Known limitations」小节，含 M0 六条与 M0.5 三条（MISSING_ID 随数据集变化、`provides` 不参与、voicechat 连字符版本两处误报）。同时修掉 README 与仓库事实不符的表述：`gradle run --args="fixtures/jars"` 改为 `gradle run`（程序不接收参数、扫描根目录固定）、「三步」改「四步」、「42 个测试」改「88」、「SPEC v0.7」改「v0.8」、「92 条工作记录 + 54 条坑」改「103 + 62」、架构图补 `DependencyResolver` 与 `dependencies.csv`、输出示例补环境事实与三行依赖统计、删掉「M0.5 不出硬依赖/不可满足数字」的旧说明。SPEC.zh-CN.md 从 v0.7 升到 v0.8：版本头改为 v0.8，整节补上 §3.5（含 3.5.1–3.5.7），§6 新增 6.4 `dependencies.csv` 并把 stdout 顺延为 6.5，§7 标题改为「验收标准（仅 M0）」并加一句「M0.5 有自己的验收线，见 §3.5.7」。结构与英文版逐项核对：29 个二级/三级标题一一对应，各节表格行数与列表项数全部一致，outcome 枚举、CSV 表头、字段名逐字未译。按用户要求当步未改任何徽章，许可证仍为 MIT。本步没有执行 git commit。

104. 第十四步只改文档，`src/` 零改动。补上第十三步留下的两处收尾：两份 README 第 26 行的测试徽章从 `tests-42 passing` 改为 `tests-88 passing`（MIT 许可徽章未动）；`SPEC.md` 与 `SPEC.zh-CN.md` 的 §6.5 stdout 模板删掉 `硬依赖总数：412，不可满足：23` 和 `环：1` 两行，替换成 §3.5.6 定义的三行（`依赖总数` / `不可满足` / `无法解析的版本区间`，字段名逐字一致），并在模板上方写明「这是格式模板，不是真实数字，真实数字见 README 的实跑输出」，原来模板下方那句只保留「每个数字必须来自实际运行、不得写死数量」的要求。改完 `gradle clean test` 仍是 88 个测试、失败 0、错误 0、跳过 0。没有执行 git commit。

105. 第十五步修 `provides` 语义 bug（生产代码 + 文档）。SPEC 两版升到 v0.9：§3.5.5 存在性改为「数据集 `modId` ∪ 环境 ID ∪ 任意 OK 记录的 `provides` 别名」，别名解析到拥有者的版本；新增第六种 outcome `AMBIGUOUS_ALIAS`，覆盖两类歧义（同一别名被两个及以上 modId 声明；别名与真实 modId 或环境 ID 重名），`resolvedVersion` 为空，WARN 打出全部候选并注明来源（`modId` / `provides` / `environment`）。§3.5.2 的 MISSING 定义、§3.5.6 与 §6.5 的 stdout、§3.5.7 都同步改；新增验收第 7 条。代码侧改 `DependencyOutcome`（加枚举值）、`DependencyResolver`（`indexProviders` 建别名索引、`targetsOf` 收集拥有者、多拥有者走 AMBIGUOUS 分支）、`Main`（摘要行加字段）。

106. 第十五步测试：新增 8 个用例（别名解析到拥有者版本、按拥有者版本判 VERSION_MISMATCH、两个 modId 声明同一别名即便有一个满足也判歧义、别名与真实 modId 重名、别名与环境 ID 重名、自己 provide 自己不算第二个拥有者、breaks 命中别名、真实数据里别名行不得为 MISSING_ID），`DependencyCsvTest` 增加一行 AMBIGUOUS_ALIAS 的 CSV 断言，`breaksOnAbsentTargetIsSatisfiedNotMissing` 改成区分“目标不存在的 SATISFIED”和“歧义的 null 版本”。`gradle clean test` 96 个测试、失败 0、错误 0、跳过 0；把 `fixtures` 临时移走后内置样本模式同样 96/0/0/0。真实数据跑出 MISSING_ID 72、AMBIGUOUS_ALIAS 58、SATISFIED 572、VERSION_MISMATCH 7、BREAKS_VIOLATED 7、UNPARSEABLE 0；M0 三个输出文件 SHA-256 与基线一致（HIGH 331 / MEDIUM 1170 / LOW 725 未变）。逐行对比 716 行，迁移只有两种：18 条 `fabric` MISSING_ID→AMBIGUOUS_ALIAS，40 条 `fabric-api` SATISFIED→AMBIGUOUS_ALIAS，其余 658 行完全没动。

107. 第十五步文档同步：两份 README 的 M0.5 小节改成六种 outcome、更新全部数字（572 / 72 / 7 / 7 / 58 / 0）、输出示例加 `AMBIGUOUS_ALIAS` 字段、Known limitations 里 `provides` 那条改成“已支持，歧义时记 AMBIGUOUS_ALIAS”、SPEC 版本引用 v0.8→v0.9、测试数 88→96、工作记录条数 104→105。徽章按用户本轮指令未改动，因此 shields 里的 `tests-88` 与正文 96 暂时不一致，已在交接报告中列出待裁决。M0 的 195 / 191 / 4 / HIGH 331 / 6670 / 缺口 10/191 一个都没动，许可证仍是 MIT。没有执行 git commit。

108. 第十六步修正 provides 优先级（用户承认第十五步写的规则有误）。SPEC 两版升到 v0.10：§3.5.5 改成五级优先级（真实 modId → 环境 ID → 唯一别名拥有者 → 多拥有者判歧义 → MISSING_ID），并写明“真实 modId 是权威身份，别的模组把同一字符串写进 provides 也不构成歧义”；唯一保留的冲突是别名占用环境 ID。§3.5.7 加第 8 条验收（真实 modId 不得被判歧义）。代码只改 `DependencyResolver.targetsOf`：真实 modId 命中即返回、别名与环境不再并列入候选；环境 ID 与别名重名时仍把两者都交给上层判歧义。测试把 `aliasCollidingWithARealModIdIsAmbiguous` 改成 `realModIdWinsOverAnAliasWithTheSameName`，另加一条“多个模组抢同一个真实 modId 也照样优先取真实 modId”。实际跑：SATISFIED 612、MISSING_ID 72、VERSION_MISMATCH 7、BREAKS_VIOLATED 7、AMBIGUOUS_ALIAS 18、UNPARSEABLE 0，合计 716；逐行 diff 只有 `AMBIGUOUS_ALIAS → SATISFIED` 一种迁移，40 行，全部是 `fabric-api`，它们的 `resolvedVersion` 也从空恢复成 `0.116.17+1.21.1`。M0 三个输出文件 SHA-256 与基线一致。两份 README 的徽章 88→96（按用户本轮指令），M0.5 数字同步，voicechat 那条改成“保守方向、只高估冲突、影响面 2/716”。

## 四、踩过的坑

1. **数据集在步骤之间发生过重组，旧数字不能再用。** 第二步和第三步时 fixtures/jars/fabric 是 194 个、neoforge 是 10 个；第四步补充返工时重新清点，fabric 变成 100 个、neoforge 变成 95 个，合计 195 个。上一轮报告里的 100 OK / 94 FAILED 和 92 个 NO_METADATA 清单已经过期。为什么必须重新清点：用户明确禁止硬编码数量，而且测试要断言记录数等于文件系统真实 .jar 数；如果照着旧数字写，测试会和当前数据对不上。当前代码和测试都只信文件系统。
2. **坑一：UTF-8 BOM 会让正常的元数据文件看起来损坏。** 部分模组在 JSON 或 TOML 最前面放 EF BB BF 三个字节；按普通 UTF-8 解码后，字符串开头会多一个 U+FEFF。如果直接交给严格解析器，可能被当成非法字符，导致一个本来完全正常的模组被误判。Fabric 和 NeoForge 两个解析器现在都会先剥掉开头的 U+FEFF。第四步时当前 100 个 fabric jar 里没有真实 BOM 样本，所以第五步专门构造了两个真实 zip fixture，分别覆盖 Fabric JSON 和 NeoForge TOML；测试直接读原始字节确认是 EF BB BF，不是拿别的字符模拟。这样 BOM 路径不再只是“代码写了但没跑过”。
3. **坑二：jar-in-jar 嵌套会让外层 jar 看起来没有元数据。** KotlinForForge 这类 mod 把真正的 mods.toml 放在内层 jar 里，外层只有 META-INF/ 和 META-INF/jarjar/ 下的若干 jar。当前 fixture 里 kotlinforforge-5.12.0-all.jar 和 kotlinforforge-5.12.0-all-2.jar 都是这个结构；外层没有 neoforge.mods.toml，真正的 neoforge.mods.toml 在 META-INF/jarjar/thedarkcolour.kffmod-5.12.0.jar 里。本步的 NeoForge 解析器不做嵌套扫描，只把外层记为 NO_METADATA，并在 failureMessage 里说明可能是嵌套打包；不能笼统报成 MALFORMED_TOML，也不能为了“看起来成功”去读内层 jar。connector 和 Essential 这两个外层没有元数据的 jar 也走同一条 NO_METADATA 路径。
4. **坑三：Sinytra Connector 靠运行时类重映射，不是元数据转换。** connector-2.0.0-beta.17+1.21.1-full.jar 的条目里有 reloc/net/minecraftforge/fart/（ForgeAutoRenamingTool）和两个内层 jar：META-INF/jarjar/org.sinytra.connector-2.0.0-beta.17+1.21.1-mod.jar 与 META-INF/jarjar/runtime-1.0.0+1.21.1.jar。这说明它的核心手段是运行时把 Fabric 模组的类重映射到 Forge/NeoForge 环境，而不是把 fabric.mod.json 静态转换成 mods.toml。这个观察对本项目定位很重要：modgraph 走的是静态预警，只根据 jar 里能读到的元数据和条目名预测声明性冲突，不做运行时重映射，也不承诺让不兼容的模组真的跑起来；两者是不同层面的方案。这一段只记录，没有因此改动任何代码。
5. **已知问题（第七步已修）：不可读或非 zip 的 jar 曾让 JarScanner 抛异常终止。** 第二步到第六步期间，扫描器遇到这种情况会直接抛异常，不产出 FAILED 记录，也不继续处理其余 jar，违反 SPEC.md §4（单个 jar 失败不能影响其余 jar，必须计入失败数，OK + FAILED 必须等于输入数）。当时 195 个 fixture 都是有效 zip，所以没有触发，但这是靠数据“侥幸没出事”，不是代码正确。第七步把它修成逐文件 try/catch：任何异常都变成一条 FAILED 记录加一条 ERROR，扫描继续。需要记住的教训是——只跑有效数据永远测不出这条路径，必须专门构造损坏 fixture。
6. **TOML 的 BOM 和 JSON 的 BOM 一样会藏起来。** 当前 95 个 neoforge 文件本身没有 BOM，但用户明确要求 TOML 也要处理；如果只在 Fabric 解析器里剥 BOM，NeoForge 的带 BOM 文件仍然会被 TOML 解析器当成非法开头。现在两个解析器都做了同样处理，并且用真实 fixture 覆盖。
7. **mixinConfigs 在 §2.2 的表格里没有单独一行。** 这不是漏读：§1 定义了字段，§2.3 依赖它，真实数据里 76 个 jar 有 [[mixins]]。本步按 [[mixins]].config 读取路径，只读路径不读内容。用户已批准保留这个映射，SPEC §2.2 表由用户补上；如果以后改主意，只需要去掉这一行映射。
8. **FORGE 分支没有真实样本。** 当前 neoforge 目录 95 个 jar 中，91 个含 neoforge.mods.toml，0 个只有 mods.toml。代码按“优先 neoforge.mods.toml，回退 mods.toml”的顺序实现了 FORGE 分支，但没有真实文件能验证“只有 mods.toml 时记 FORGE”和“examplemod 不做规范化”。不能把 91 个 NEOFORGE 的结果说成 FORGE 也被真实数据验证了；等用户提供 Forge jar 再补。
9. **多 [[mods]] 只取第一条会丢信息，但这是用户要求。** forgified-fabric-api 和 voicechat 两个 jar 各有 2 条；解析器只取第一条并打 WARN。两个 kotlinforforge 副本的 modId 相同，本步照实处理两条记录，没有去重；去重属于后续的重复 ID 警告，不是解析器该做的事。
10. **依赖字段的写法非常杂。** 真实数据里 type 有 required、optional、incompatible，也有大写 REQUIRED；有的用 required=true，有的用 mandatory=true，还有两条完全不写类型；versionRange 可能缺失或为空字符串；YACL 还把整个依赖键写成带引号的 “dependencies.yet_another_config_lib_v3”。解析器按 type → required → mandatory → 默认 required 的顺序判定，并对带引号的整键做了兼容；versionRange 原样保留，缺失时为空列表。不能因为某个字段缺失就判整个 jar 失败。
11. **displayTest 没有 NORMAL 样本，不能猜。** 实测值是 None、IGNORE_ALL_VERSION、NONE、MATCH_VERSION、IGNORE_SERVER_VERSION；SPEC.md 只规定 NORMAL 映射到 BOTH。解析器只把 NORMAL 映射成 BOTH，其他值和缺失都按 UNKNOWN 或留空处理；没有为了“看起来完整”把 None 强行解释成 BOTH 或 CLIENT。
12. **mixinTargetsInferred 的占位值曾经是 false。** 在第六步之前，这个字段在 §1 里类型是 boolean，不能像字符串那样留 null；当时没有实现任何推断逻辑，所以先用 false 占位，表示“还没有推断过”。第六步实现命名推断后，所有 OK 记录都改成了 true；失败记录仍是 false。这个变化不是随便改的，是 SPEC §2.4 明确要求的。
13. **Constraint 保持独立类，不改成嵌套内部类。** 用户已经确认这个决定。理由：SPEC.md 把 Constraint 作为独立的结构来描述，没有要求它必须嵌套；独立类更方便后续 Fabric/NeoForge 解析器共用，也不会让 ModCandidate 文件变得过长。字段仍然是五个：targetId、versionRanges、required、ordering、side。
14. **验证细节：gradle test 可能因 UP-TO-DATE 不重跑。** 如果只看 gradle test 的 BUILD SUCCESSFUL，可能是缓存结果；要确认测试真的执行，应该看 gradle clean test，或者检查 build/test-results 下的 XML。这不是代码问题，是 Gradle 的增量构建行为。第六步报告的数字来自 gradle clean test 后的 XML。
15. **Gson 默认宽松模式会放过损坏的 JSON。** 旧数据集里那 2 个损坏的 fabric.mod.json 在 description 里含未转义的控制字符；Python 的 json 直接报错，Gson 如果不设严格模式，可能把它当合法字符串接受，结果就会错记成 OK。Fabric 解析器仍然明确设置 Strictness.STRICT，让非法 JSON 按 MALFORMED_JSON 失败。当前 100 个 fabric jar 的 JSON 都是合法的，这个严格设置没有造成误伤；但它不是“可以放松”的理由。
16. **unknownFields 会很多，这是 SPEC 要求，不是噪声。** 在 Fabric 里，description、authors、license、contact、icon、custom、accessWidener 等字段很常见；在 NeoForge 里，license、issueTrackerURL、modproperties、accessTransformers、logoFile 等也很常见。SPEC.md 没有为它们定义统一字段，所以它们都属于“本规格未定义的顶层字段”，必须进 unknownFields 并打 WARN；不能因为“看起来像常用字段”就静默丢掉。每个 jar 只打一条 WARN，列出该 jar 的全部未知字段。当前运行会产生很多条 WARN，这是正常的，不是失败。
17. **当前 NEOFORGE 有 4 个 FAILED，必须逐个列名，不能笼统说“少数失败”。** 这 4 个都是 NO_METADATA，名字和原因已写在第一节和交接报告里；没有一个 MALFORMED_TOML。FABRIC 仍然是 0 失败。neoforge 的 95 个 jar 全部进入了解析器，4 个失败被计入失败数，没有被跳过或丢弃；第六步已经做了全项目 195 个 jar 的合并统计。
18. **environment 缺失时 side 留空，没有替它填 BOTH。** SPEC.md §2.1 只给了星号、client、server 三种显式映射，没有说缺失时按默认值处理。本步选择留空（null），表示“元数据没有声明”，不把运行时的默认值当成元数据事实。后续如果产品上需要把缺失当成 BOTH，应作为明确规则写进 SPEC 再改。
19. **versionRanges 只做搬运，不做任何求值。** 依赖里的版本区间字符串从 JSON 或 TOML 读出来是什么就存什么，包括空格、操作符、数组顺序和占位符；没有 trim、没有排序、没有尝试判断区间是否相交。SPEC.md §1 明确说区间求值属于 M0.5，本阶段禁止自己发明比较逻辑。混入对象的 mixins 写法也只取 config 路径，不读 mixin 配置文件内容。
20. **真实数据仍然没有 TOML 语法损坏、缺必填字段、IO 错误、非 zip 的样本；这些现在由专门的 fixture 覆盖，但不等于真实数据回归。** fixtures/jars 下的 195 个文件都是有效 zip、元数据都合法，所以真实数据只跑得出 NO_METADATA 这一种失败码。第七步按 SPEC 构造了 7 个真实损坏 fixture，让 7 种失败码都有真实文件触发；但必须区分“fixture 覆盖”和“真实数据回归”——fixture 是我们自己造的边界样本，真实数据里没有这些形态。不能因为测试全绿就声称真实世界的坏 jar 都已经被验证过。
21. **未知枚举值不能静默变成默认值，但也不能一律塞进 unknownFields。** ordering 和 side 是依赖对象里的枚举字段，值非法说明这行依赖不可信，继续拿 NONE / UNKNOWN 去参与后续判断会把错误藏起来。本轮选择直接判 MALFORMED_TOML，让这个 jar 变成 FAILED，并在 ERROR 里写明字段名和非法值。SPEC.md §2.2 没有明确写“非法枚举值怎么办”，所以这个决定记录在这里；如果以后认为应该保留记录、只把非法值放进 unknownFields，需要先改 SPEC 和测试预期，不能两边不一致。
22. **RuntimeException 兜底会把程序 bug 伪装成数据问题。** 之前所有未分类的 RuntimeException 都归 MALFORMED_TOML，报表上看起来像“这个 jar 的 TOML 坏了”，实际上是代码自己的空指针或状态错误。本轮新增 INTERNAL_ERROR：TOML 语法错误仍归 MALFORMED_TOML，必填字段缺失仍归 MISSING_REQUIRED_FIELD，只有无法归为数据问题的 RuntimeException 才归 INTERNAL_ERROR；failureMessage 以“这是程序异常不是数据问题：”开头并带异常类名，ERROR 日志也带异常类名和异常对象。这样用户看 failureCode 就能区分问题来源。
23. **用户说“必填字段缺失→MALFORMED_TOML”，但本轮仍保留 MISSING_REQUIRED_FIELD。** 原因是 SPEC.md §4 明确给必填字段缺失单独一个失败码，而且第五步用户要求“每种失败按 §4 填 failureCode”；如果把它改成 MALFORMED_TOML，会丢掉“缺字段”和“TOML 写坏”的区分。本轮只把“非法枚举值”和“TOML 语法错误”归 MALFORMED_TOML，必填字段缺失保持原码。这个取舍需要在交接报告里说明；如果用户要求按本轮括号里的字面要求统一，再改。
24. **displayTest 的非 NORMAL 值不是“非法枚举”，没有跟着本轮一起判失败。** 依赖的 side 是我们自己的 Side 枚举，非法值可以判 MALFORMED_TOML；displayTest 是 NeoForge 的真实字段，实测有 None、NONE、IGNORE_ALL_VERSION、MATCH_VERSION、IGNORE_SERVER_VERSION 等正常取值，SPEC.md 只规定 NORMAL 映射 BOTH。把它们判失败会误伤当前 18 个正常 jar，所以仍然按 UNKNOWN 或留空处理。这个区别要写清楚，避免以后把“未知枚举”理解成“所有没见过的字符串都失败”。
25. **测试里捕获日志需要把 log4j-core 放进测试编译类路径。** 第一次写 NeoForgeInvalidEnumTest 时直接 import Log4j 的 AbstractAppender，编译失败，因为 build.gradle.kts 里 log4j-core 只是 runtimeOnly；补了 testImplementation("org.apache.logging.log4j:log4j-core:2.24.1") 后才通过。另 AbstractAppender 的构造器仍有 deprecation 编译警告，但不影响测试结果；这不是运行失败，也没有用 assume 或跳过测试来绕过。
26. **supportPolicy 口径已按 SPEC v0.4 统一为 RENDER/CORE → EXCLUDED。** 代码当前与 v0.4 一致，16 个深度侵入全部是 EXCLUDED，DEGRADED 在 M0 阶段没有来源、恒为 0。这不是“没跑到 DEGRADED”；如果以后有人看到 DEGRADED 0，不要把它解释成数据缺失或漏统计，它是规则本身的结果。
27. **已知渲染 ID 的写法不统一，必须归一化。** Fabric 的 reeses-sodium-options 和 NeoForge 的 reeses_sodium_options 是同一个模组，sodium-extra 和 sodium_extra 也是；如果直接字符串比较，会漏判一半。分析器在比较前去掉 `-` 和 `_`，所以两个加载器变体都能命中。这个归一化只用于已知 ID 清单匹配，不改写 ModCandidate 里原样的 modId。
28. **sodium-neoforge 的 mixin 配置藏在嵌套 jar 里。** 外层 jar 没有 `sodium-common.mixins.json` / `sodium-neoforge.mixins.json`，它们只在 `META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar` 内。如果只读外层条目，会漏掉 sodium 的目标索引；所以分析器只对“已经声明的 config 路径”做定向嵌套查找，不递归扫描其他内层 jar，也不读 class。这样既拿到真实配置，又没有把嵌套扫描扩大成无边界行为。
29. **carryon 的 mixin 配置带 `//` 注释，严格 JSON 解析会失败。** 当前配置里有 `//"refmap"...` 这样的注释；如果用 Strictness.STRICT，会把一个正常模组的配置判成 MALFORMED_JSON。这里用 LENIENT 只是为了接受这种真实写法，不代表所有损坏 JSON 都可以放过；Fabric 元数据仍然用 STRICT。配置缺失或确实无法解析时，仍然按失败处理，不是静默跳过。
30. **CORE 的“ASM 相关类路径”在 SPEC 里没有给精确模式。** 当前没有 `org/objectweb/asm/` 或 `org/spongepowered/asm/` 这样的标准 ASM 库路径；本步采用“条目路径含 `/asm/`”作为 L1 近似，命中 entity_model_features 的 `math/asm/`、konkrete 的 `minidev/asm/`、Cobblemon 的 `impl/asm/`。embeddium 也含 `impl/asm/`，但它同时命中 RENDER，按命中即停归 RENDER。这个阈值比标准 ASM 库宽，可能会把名字里带 asm 但并非字节码库的包算进 CORE；信号里保留了具体条目路径，人工可以复核。SPEC 需要的话应补一个更精确的模式。
31. **冲突预测按唯一 modId 聚合，不是按 191 条记录直接两两比。** 同一 modId 的 Fabric/NeoForge 变体会被合并，目标取并集，侵入深度取更深的一档；否则 sodium 会和自己的另一个加载器变体配成一对，产生无意义的“自冲突”。当前 191 条 OK 聚合成 116 个唯一 modId，6670 对。重复 modId 的正式警告留给后续依赖图步骤，本步只保证不自己和自己比。
32. **冲突目标是简单名，误报和漏报都是设计内。** 只去掉包名、按后缀剥类名，两个不同包下同名的类会被合并；目标类名和 mixin 类名无关时又推断不出。因此 HIGH 649 组只是“可能撞同一处注入”，不是运行结论；NONE 也不代表稳定。输出和摘要都必须保留“静态推断，非运行结论”，不能把 HIGH 说成“确定冲突”。
33. **EXCLUDED 不能从统计里剔除。** 用户明确说被排除的模组正是最重要的输出；summary.csv 必须包含它们，测试也断言了 195 行数据。当前 EXCLUDED 16 个全部是 RENDER/CORE，SUPPORTED 175 个，DEGRADED 0；如果把它们从 summary.csv 删掉，行数会变成 179，和输入数对不上。
34. **HIGH 冲突的 WARN 很多，是预期行为。** 649 组 HIGH 会逐条打 WARN，日志会很长；这不是错误，也不应该为了“日志好看”把 WARN 降成 DEBUG 或只打前几条。SPEC 要求每检出一组 HIGH 打一条 WARN，格式固定；本步照做。
35. **mixinTargetsInferred 为 true 只表示“用了命名约定推断”，不表示推断正确。** 当前 191 个 OK 全部是 true；这个布尔值不能用来判断目标列表是否非空——API 模组也可能没有目标，但仍然是“已完成推断”。不要把 true 误读成“有目标”或“推断可靠”。
36. **阈值不能靠感觉定，25% 和 10% 是两个完全不同的世界。** 25% 时阈值是 29，最高目标只有 20 个，规则完全空转，HIGH 仍是 649；10% 时阈值是 12，有 14 个目标越过线，HIGH 才真正降到 331。这个对比说明：如果只看到“规则实现了、测试绿了”就宣布成功，会漏掉“规则在当前数据上什么都没做”。以后改阈值必须同时报告实际 ubiquitous 清单和 HIGH 前后对比，不能只报一个阈值数字。
37. **只剥后缀会漏掉大量前缀式命名。** 修正前 54 个有 mixinConfigs 的 OK 模组推断不出目标；BiomesOPlenty 的 `MixinBloodFluid`、`MixinLiquidNullFluid` 是前缀式，`MixinGameRenderer` 会作为独立目标留下，和 `GameRenderer` 分开计数，既压低覆盖率又压低目标频率。v0.5 按用户要求同时剥前缀和后缀后，缺口降到 10 个（5.24%）；但只剥真正带前后缀的名字，不能把普通类名也剥成空字符串。
38. **分档文字与验收线打架，最后由用户 m01468 裁决。** 旧 v0.5 的 any-rare 字面结果是 HIGH 810（12.14%），违反 5% 验收线；all-rare 严格版是 331（4.96%）。用户裁决采纳 all-rare，理由是语义而不是数字：any-rare 会把“共享目标里大部分是高频类、只有 1 个稀有”的配对也判 HIGH，但实际撞车大概率就在高频类，稀有类只是顺带命中，属于噪声；all-rare 才表达“撞的位置确实不寻常”。SPEC v0.6 §2.4 已按此收紧，`ConflictPredictor` 注释已同步，不再待确认。

39. **覆盖率 10% 是验收线，不是“修完了”。** 修正后仍有 10 个 OK 模组的目标列表为空：PresenceFootsteps、Searchables(fabric/neoforge)、VisualWorkbench(fabric/neoforge)、coroutil、supermartijn642configlib、Controlling、ForgeConfigAPIPort、embeddium。它们不是被跳过：配置确实被读了，只是数组为空或类名没有可剥的前后缀。测试把“缺口 ≤ 10%”写成硬断言，但没有把空列表硬凑成目标；剩余边界如实保留。

40. **Top 10 和 ubiquitous 都按唯一 modId 聚合，不按 191 条记录重复计数。** 同一个模组的 Fabric/NeoForge 两个变体会合并目标，否则 Minecraft 这类目标会被同一模组重复计数，阈值也会失真。当前 191 条 OK 记录聚合成 116 个唯一 modId，阈值 12 就是按 116 算的；这是冲突预测和 Top 10 的共同口径，不能一条按记录数、一条按模组数。

41. **需求文件可能落后于消息里的裁决。** 用户 m01468 说 SPEC 已升到 v0.6 并把规则改严，但同步注释时实际读到的本地 `SPEC.md` 文件头仍是 `版本：v0.3`，§2.4 的分档表仍是旧 any-rare。处理原则：不擅自修改需求文件，代码行为按最新明确裁决；但必须在 PROGRESS 和交接报告里显式写明这个差异，避免下一位读者误以为代码在违背 SPEC。第七步开工时再次确认过：用户更新后文件头已是 v0.6、§2.4 已是 all-rare，差异已消除。

42. **“失败不计数”比“失败”更危险。** 第二步到第六步的扫描器一旦遇到非 zip 就抛异常，整批处理会停在那个文件，后面的 jar 一个记录都没有；如果生产环境里恰好有一个坏文件排在前面，用户看到的结果可能只有十几个 jar，而不是 195 个。这种时候 OK 和 FAILED 都“看起来正常”，因为根本没跑到。所以现在 Main 里保留 `OK + FAILED == 输入数` 的硬断言并在不满足时抛异常——等式不成立说明有记录被吞了，不能靠“日志里没报错”判断没事。

43. **扫描失败和解析失败必须分层，否则 failureCode 会互相覆盖。** 一个非 zip 文件在扫描阶段已经被判成 NOT_A_ZIP，如果解析器照常往下跑，它会找不到 fabric.mod.json，于是写回 NO_METADATA，用户看到的原因就是错的。两个解析器进入 `parse` 后先检查 `candidate().status() == FAILED` 并原样返回，就是为了让“第一层已经判定失败”的事实不被第二层改写。测试专门断言了这一点。

44. **NOT_A_ZIP 和 IO_ERROR 的区别是“文件坏”还是“数据坏”。** 文件不是 zip，`new ZipFile` 直接抛 ZipException，这是 NOT_A_ZIP；文件是 zip、条目名都能列出来，但读某个条目内容时 inflate 失败，这是 IO_ERROR。如果两者都归 NOT_A_ZIP，报表会把“压缩包被下载截断”和“里面某段数据坏了”混为一谈。`fail-corrupt-entry.jar` 就是把 deflate 数据首字节改成保留块类型 BTYPE=3 构造的：zip 结构完好，只有读条目内容时才失败。做这个样本前先实测过 Java 的行为——空文件、随机文本、截断 zip 在打开阶段都抛 ZipException，只会得到 NOT_A_ZIP，拿不到 IO_ERROR。

45. **INTERNAL_ERROR 只能用真实异常路径验证，不能声称是数据触发的。** 七种码里前六种是数据问题，第七种是程序问题。用一个 schemaVersion 写成 `1e9999999999` 的 fixture，让 Gson 的数值兜底路径抛 NumberFormatException，走的是真实的 `catch (RuntimeException)` 分支，不是 mock、也不是反射硬造异常。但它说明的是“这条兜底路径存在且会计入 FAILED 并告警”，不是“坏数据应该被归成 INTERNAL_ERROR”。如果哪天看到 INTERNAL_ERROR 出现在真实数据里，正确的反应是去修代码，而不是把它当成一种正常的数据分类。

46. **扫描失败时 SHA-256 可能算不出来。** ModCandidate 的 sha256 是必填字段不能为 null。如果文件连读都读不出来（权限、设备错误），就没有哈希可填，此时填空字符串并在 failureMessage 里写明“读取文件计算 SHA-256 失败”，不编造一个假哈希，也不因为拿不到哈希就把这条记录丢掉。NOT_A_ZIP 这种“能读但不能当 zip 打开”的情况哈希是算得出来的，仍然填真实值。

47. **“对全部记录断言字段为空”是一条隐式假设：输入全都成功。** 第七步之后扫描器会产出 FAILED 记录，failureCode / failureMessage 必然非空，旧测试 `parsingFieldsAreStillEmpty` 就必然红。测试应该断言“OK 记录的解析字段为空”，而不是“所有记录的解析字段为空”；同时必须有另一条断言保证 FAILED 记录本身字段齐全，两者合起来才不会漏。修的时候不能用 assumeTrue 跳过，也不能只扫一个子目录来回避坏文件，那样等于把真实场景排除在测试之外。

48. **fixtures/ 在 .gitignore 里，别人 clone 下来根本没有这个目录。** 这意味着所有依赖 fixtures/jars 的测试，本质上是“本机有私有样本时才有效”。断言必须只依赖“文件系统真实列举出来的数量”和“OK + FAILED 等于这个数量”，不能依赖固定数字 195，也不能假设每条记录都成功。第八步把 JarScannerTest 改成这种形态后，输入目录里有没有坏文件都不影响通过。

49. **命名约定推断有过三类系统性漏报，测试全绿也发现不了。** 一是嵌套类 `XxxMixin$YyyMixin`：旧逻辑先对整名剥后缀得到 `XxxMixin$Yyy`，前缀规则就不再匹配，目标名残留 `Mixin`，真正的 `Xxx` 反而缺失（sodium 各版本 2 处，导致 `VertexMultiConsumer` 完全不在索引里）；第九步改成按 `$` 分段独立剥除后已修正。二是中缀命名 `PlayerMixinFabric`：既不以 Mixin 开头也不以 Mixin 结尾，被直接跳过，**未修**。三是 `AccessXxx` 前缀式访问器：只认 `Accessor` 后缀，controlling 的 5 个访问器全部丢弃，NeoForge 版本 `mixinTargets` 为空，**未修**。这三类都不是“测试没写”，而是“规则没覆盖”，只能靠人工解包抽检发现。

50. **嵌套类的正确切法是“先分段、再逐段剥”，不是“先剥一整名”。** 旧代码对 `VertexMultiConsumerMixin$DoubleMixin` 先剥掉结尾的 `Mixin`，得到 `VertexMultiConsumerMixin$Double`，此时整名不再以 `Mixin` 开头，前缀规则失效。改成按 `$` 切分后，`VertexMultiConsumerMixin` 这一段能独立剥出 `VertexMultiConsumer`，`DoubleMixin` 这一段能独立剥出 `Double`，拼回就是 `VertexMultiConsumer$Double`。顺序反过来（先整名剥前缀）同样会坏：`MixinFoo$BarMixin` 会被剥成 `Foo$BarMixin`，第二段的后缀再也处理不到。所以两段逻辑必须分开：含 `$` 走分段，不含 `$` 走原来的整名逻辑，不能混在一起。

51. **修名字不等于修数字。** 第九步把两个幽灵目标名换成正确名字后，target 总数、覆盖率缺口、HIGH 组数一个都没变。原因是修复前这两个错误名字也照样进了目标集合（只是名字错），它们只被 sodium 自己引用，改成真名后没有和其他模组新增共享。所以如果只看“HIGH 还是 331”就判断修复没生效，是错的；判断依据必须是目标集合里的实际字符串。反过来也提醒：覆盖率缺口这个指标对“名字错但非空”的缺陷完全不敏感，光靠 10% 阈值永远发现不了这类问题。

52. **中缀式和 `AccessXxx` 是故意不修的，不是漏掉。** 用户裁决：中缀 `PlayerMixinFabric` 无法判断 `Mixin` 后面哪一段是目标、哪一段是加载器后缀（Fabric / Neo 等），强行剥会误报；`AccessXxx` 前缀与 `Accessor` 后缀是两种命名习惯，SPEC 只定义了后缀，加前缀规则可能误伤 `AccessControl`、`AccessWidener` 这类正常类名。这两条要留在已知局限里，等 SPEC 明确定义后再改，不能因为“看起来能修”就顺手改。

53. **README 里的命令和数字会随实现漂移，收尾时必须逐条核对。** 第十步发现 README 的“快速开始”让用户跑 `python3 collect_fixtures.py`，但仓库里从来没有这个文件，真实脚本是 `build_download_list.py` / `fix_downloads.py` / `organize_jars.py` / `verify_downloads.py` 四个；同时“六种失败分类”在实现已经是七种之后还留在路线图和工程纪律里，架构图里的 `InvasivenessScanner` / `Writer` 也不是真实类名。这类错误不会让任何测试变红，只能靠人工把文档里的每条命令、每个类名、每个数字和仓库对一遍。写文档时引用脚本要先 `Get-ChildItem *.py` 确认存在，引用数量要先读文件系统或输出文件确认，不能凭记忆写。

54. **“clone 即红”不能靠跳过解决，也不该靠假装断言解决。** 第十一步实测：把 `fixtures` 目录移走后，四个测试类在 `@BeforeAll` 就 error（JarScannerTest、FabricMetadataParserTest、NeoForgeMetadataParserTest、DescriptorAnalysisTest），不是只有 JarScannerTest。做法是加一个 `DatasetRoot` 统一切换数据源，并在日志里写明当前模式；完整数据集存在时原来的全部分布/冲突/样本断言照旧，内置样本下改成断言这份样本的已知构成（两个 API 级 BOM 模组、无 mixin 目标、唯一一对冲突是 NONE），而不是让测试空转。关键是不能写 `if (集合为空) return;` 这种假通过——那和 assume 跳过没有区别，只是把跳过藏进了方法体。两种模式都仍然断言 `OK + FAILED == 文件系统真实列举数`，这是唯一不能退让的不变量。

55. **`breaks` 的目标不存在不能记 MISSING_ID，否则“不可满足”这个数字会失去意义。** 第一版严格按 §3.5.5 的字面（“ID 既不在数据集里也不是环境提供”）给所有目标缺失的行记 MISSING_ID，结果 139 条冲突声明里有 100 条被算进“不可满足”，总数 190。但 `breaks` 声明一个没装的模组恰恰是正常情况——没有目标就没有冲突。改成“`breaks` 目标不存在 → SATISFIED”后，MISSING_ID 从 190 降到 90（硬 68 + 软 22），这个数字才能用来判断准入。用户口径“breaks 命中记 BREAKS_VIOLATED”也是同一读法。

56. **`+` 之后的构建元数据不剥离，会把版本比反。** 第一次跑出 7 条 VERSION_MISMATCH 里有一条 `cobblemon → fabric-api >=0.116.6+1.21.1`，数据集里是 `0.116.17+1.21.1`。按 3.5.4 只按 `.` 和 `-` 切段时，第 3 段是 `17+1` 与 `6+1`，都是非数字段，字典序下 `17+1` < `6+1`，于是 0.116.17 被判成小于 0.116.6。同理会把 `0.8.x` 通配匹配搞坏。在 `Version.parse` 里剥离 `+` 之后，这类误判消失，CSV 里的 `resolvedVersion` 仍然写原串。SPEC 没提 `+`，这一条是实现补的，写在这里备查。

57. **NeoForge 的 `versionRange = "*"` 不是非法语法。** SPEC §3.5.3 的 NeoForge 表没列 `*`，第一版按“Maven 端点不能含通配段”判 UNPARSEABLE，把 e4mc 的一条硬依赖判成不可解析——那是工具误伤，不是数据缺陷。现在 `*` 与空串都表示不限制版本，真实数据里 e4mc→neoforge 因此变成 SATISFIED。

58. **裸版本串在 Fabric 是精确匹配，不是“最低版本”。** 真实数据里有 35 条裸写法（`1.21.1`、`21`、`1.21`），SPEC §3.5.3 的 Fabric 表没有单列这一形式。若判不可解析，就是 35 条工具缺陷；若当成 `>=`，`"minecraft": "1.21"` 会在 1.21.1 上被错判为满足。最终按 Fabric 加载器的真实语义处理：裸串 = 精确匹配（1.21 == 1.21.0，但 != 1.21.1）。这也正是两套语法必须分开实现的原因：同一个裸串在 NeoForge 侧是 `>=`。

59. **连字符复合版本仍然有 2 处误报，没有偷偷修正。** voicechat 的版本写法是 `1.21.1-2.6.27`（MC 版本 + 模组版本），依赖声明写 `[1.21-2.4.11,)`。按 §3.5.4 字面切段后第 3 段是 `1` 与 `2`，于是判成更小：一条 `depends` 被判 VERSION_MISMATCH，同一条的反向 `breaks` 被判 BREAKS_VIOLATED。按真实语义两条都应当是满足/不冲突。修它需要给“MC 前缀 + 模组版本”这类复合版本单独定规则，属于改 SPEC，不在本步自行决定；当前 7 条 VERSION_MISMATCH 里 1 条、7 条 BREAKS_VIOLATED 里 1 条来自这里。

60. **`provides` 不参与解析，18 条 `fabric` 会被判缺失。** 数据集里 `fabric-api` 的 `provides` 就是 `["fabric"]`，运行时 Fabric 会把它当作提供者；但 SPEC §3.5.5 只承认“数据集里的 modId”和“环境 ID”两种来源，把 `provides` 加进来等于改规格。本步按字面实现，并在报告里点名这 18 条。另有 27 条 `fabric-*` 子模块 ID（`fabric-resource-loader-v0` 等）不在任何 `provides` 列表里，缺失是事实。是否引入能力 ID 解析需要用户裁决。

61. **目标版本是占位符时，不能一遇到就判 UNPARSEABLE。** 第一版发现 jade / patchouli / terrablender / appleskin 的 NeoForge 变体版本是 `${file.jarVersion}`，只要有这个变体就把整行判成不可解析；但同一 modId 的 Fabric 变体有真实版本（terrablender 4.1.0.8），足以证明满足。改成“先看有没有任一版本能匹配 → 满足；都不匹配且存在无法解析的版本 → UNPARSEABLE；都不是才判 VERSION_MISMATCH”，UNPARSEABLE 从 3 降到 0，同时保留了“无法比较时不谎报不符”的原则。

62. **“M0 数字没变”不能靠测试全绿来证明。** 测试只断言结构不变量，数字漂移照样全绿。本步的做法是改动前先跑一次 `gradle run`，把 candidates.jsonl / summary.csv / conflicts.csv 复制到仓库外并记录 SHA-256；改完再跑，三个哈希逐一比对相同，才敢写“M0 未被改动”。同理，716 行与 candidates.jsonl 的逐行比对也是独立脚本做的，不是“相信 CSV 写对了”。

63. **支持 `provides` 之后，受影响的不只有那 18 条“假缺失”。** 修前预计 MISSING_ID 90→72，实际正是 72；但 SATISFIED 不是 +18 而是 612→572，少了 40。原因是数据集里 `forgified-fabric-api`（NeoForge 版，modId 为 `fabric_api`，版本是 `${file.jarVersion}`）声明了 `provides: ["fabric","fabric-api"]`，于是 `fabric-api` 这个 ID 同时有真实 modId 和一个别名拥有者，按“不许任选一个”的规则整批 40 行被判 `AMBIGUOUS_ALIAS`。这暴露了一个更一般的问题：**一个规则改动的影响面必须靠逐行 diff 才能看清，不能只算预期的那一类**。同样值得记住的是，这两条歧义行的两个拥有者其实是同一个上游项目（Fabric API 本体 + Sinytra 移植），冲突来自数据集把两个加载器混在一起；要消除它需要“按加载器家族分流”这条新规则，不能靠调阈值。

64. **新 outcome 一旦进入枚举，所有 switch 和“缺失才为 null”的断言都要重新审一遍。** 加 `AMBIGUOUS_ALIAS` 后，`everyOutcomeRespectsItsDefinition` 的 switch 少了分支（编译不报错，因为语句 switch 不要求穷尽），`breaksOnAbsentTargetIsSatisfiedNotMissing` 直接红了——它把“`resolvedVersion` 为 null 的 breaks 行”等同于“目标不存在”。修法是改成显式区分两种情况：目标不存在 → SATISFIED；多拥有者 → AMBIGUOUS_ALIAS 且版本为空。教训是：**“null 版本”不再唯一对应“目标不存在”**，任何拿 null 当判据的断言都必须重新表达。

65. **“谁拥有这个 ID”必须定义优先级，不能只定义成“拥有者集合”。** 第十五步的规则把“别名与真实 modId 重名”也算歧义，等于把 depId 精确命中真实 modId 和只能靠别名命中当成同一件事；真实语义里前者是权威身份、后者是替补，结果 40 条本来正确的 SATISFIED（depId = `fabric-api`）被打成 AMBIGUOUS_ALIAS。第十六步改成五级优先级、命中即停，这 40 条全部恢复。教训有两条：一是**来源之间只要有主次，规则就必须写出次序**，否则实现只能靠猜；二是**“不许任选”只应约束同一优先级里的并列候选**，把不同优先级也塞进同一个集合，等于用“防错”制造新错。当时逐行 diff 已经把 40 条的迁移摊开写进报告了（+58 歧义里 40 条来自真实 modId 冲突），只是规则本身错了，数据层面的证据是对的——所以“数字变化必须逐条解释”这条纪律值得一直保留。

## 五、下一步建议

M0 已按 SPEC v0.7 定稿（v0.8 / v0.9 / v0.10 都没有改动 M0 的十条），M0.5 已按 SPEC v0.10 §3.5 完成，中英文 SPEC 现在都是 v0.10。完整数据集（195 个 jar）下 `gradle clean test` 是 96 个测试、失败 0、错误 0、跳过 0；`gradle clean build` 退出码 0；`gradle run` 产出四个文件，依赖判定 716 行。把 `fixtures` 临时移走后（内置 11 个样本）测试依然全部通过、跳过仍为 0，两种模式在日志里明确打印当前数据源。

本步结束后仍待用户裁决或确认的两件事：

1. 是否按加载器家族分流解析。当前 18 条 `AMBIGUOUS_ALIAS` 全部来自 `fabric` 这一个别名，根因是数据集同时包含 Fabric API 本体与 Sinytra 的 forgified 移植（两个 modId 都声明 `fabric`）。按家族分流能消掉这 18 条，但那是新规则，需要先写进 SPEC。
2. 连字符复合版本（`1.21.1-2.6.27`）的判定规则。当前按 §3.5.4 字面切段，产生 1 条假 VERSION_MISMATCH 与 1 条假 BREAKS_VIOLATED；要修就得在 SPEC 里给这类版本单独定义规则。这属于保守方向的误报（只高估冲突、不会漏报真问题），影响面 2/716。

环境事实的取值不是待裁决项，只是提醒：当前 fabricloader=0.20.1、neoforge=21.1.262 取的是数据集内下界最大值；如果换成更旧的实际环境版本，VERSION_MISMATCH 会如实增加，这不是 bug。

M0.5 之后（M1）才适合做：依赖图与拓扑排序、循环依赖检测、重复 modId 的警告与仲裁。这些 SPEC 尚未要求，现在不要提前实现，也不要预留接口。

M0 的 §7 十条最终状态：第 1、2、3、4、6、7、8、9、10 条达成；第 5 条标注 1 处语义争议——sodium 的 `modType` 判成 `LIBRARY`（因为它声明了 `provides: ["indium"]`，按 SPEC 表格行序先判 LIBRARY），按规则是对的，但按模组实际身份更像 `OPTIMIZER`。是否调整判定顺序由用户决定，本步没有改。

M0 的已知边界（同时写进 README 的 Known limitations）：

- 命名约定推断不是读 `@Mixin` 注解，会漏报也会误报；`mixinTargets` 只能当静态线索
- 中缀式（`PlayerMixinFabric`）和 `AccessXxx` 前缀（controlling 5 个类）未处理，属已知漏报
- `RENDER` / `CORE` 模组一律 `EXCLUDED`，不做兼容性承诺
- jar-in-jar 嵌套打包判 `NO_METADATA`（connector、Essential、kotlinforforge×2）
- `FORGE` 分支没有真实样本，`HACK_CLIENT` 为 0，这两条逻辑没经过真实数据验证
- `INTERNAL_ERROR` 的 fixture 验证的是兜底路径存在，不是“坏数据应归为 INTERNAL_ERROR”

M0.5 自身的已知边界：

- `provides` 已支持（v0.9 引入、v0.10 修正优先级），真实 modId 优先；只有一个 ID 被两个及以上模组声明时才是 `AMBIGUOUS_ALIAS`，当前 18 条，全部是 `fabric`（Fabric API 本体与 Sinytra 移植都声明了它）
- 连字符复合版本的 2 条误报方向保守：只高估冲突，不会漏报真问题
- 连字符复合版本（`1.21.1-2.6.27`）按字面切段，产生 1 条假 VERSION_MISMATCH 与 1 条假 BREAKS_VIOLATED
- UNPARSEABLE 在真实数据上为 0，`#compatible_neoforge#` 只出现在被注释掉的 TOML 行里；该分支由单元测试覆盖
- 同一 modId 的多个版本按“任一满足”判定，这是 M0 冲突预测合并变体的延续，不是版本选择
- 环境事实是**声明**而非观测：`quilt_loader` 与 `forge` 当前没有任何数据引用

没有执行 git commit，提交仍由用户完成；不要自动兼容、不要自动修复、不要引入 Minecraft / Fabric / Forge / NeoForge / Mixin / ASM 依赖。
