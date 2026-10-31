<p align="right">
  <a href="./SPEC.md">English</a> · <b>简体中文</b>
</p>

# SPEC.zh-CN.md — M0：离线模组元数据归一化、依赖图与侵入深度评级

版本：v0.7
状态：草稿待人工确认
上游启发：Forbric v0.3.0（Ray-T-r/Minecraft-Forbric-mod-loader，Apache-2.0）

---

## 0. 本项目当前不是模组加载器

M0 **不启动 Minecraft、不加载类、不处理 Mixin、不改写字节码**。
它只做一件事：给定一个模组 jar 文件，读出它的元数据与结构特征，产出一条结构化的 `ModCandidate` 记录。

### 定位（必须准确理解）

M0 的输出是**「能否启动的准入检查」**，不是**「能否稳定运行的保证」**。

- M0 **能**预测声明性冲突：依赖缺失、版本区间不相交、循环依赖、显式不兼容声明
- M0 **不能**预测行为性冲突：两个 Mixin 撞同一方法、两个模组替换同一渲染阶段、注册表生命周期顺序导致 NPE

行为性冲突只能在运行时暴露。**把 M0 宣传成"能否稳定运行的保证"属于误导。**

明确不在范围内（AI 不得提前实现，也不得"顺手加上"）：

- 任何 ClassLoader、JVM agent、字节码操作（ASM / Mixin / Tiny Remapper）
- 任何 Minecraft 启动、下载、打补丁逻辑
- 任何跨加载器互操作（事件桥、物品/流体/能量）
- 任何 GUI、配置文件、安装器
- 任何"自动兼容"或"自动修复"能力

---

## 1. 统一数据契约：ModCandidate

每个 jar 产出**恰好一条**记录。解析失败也必须产出一条 `status = "FAILED"` 的记录，**不得跳过、不得省略、不得返回 null**。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sourceFile` | string | 是 | jar 文件名（不含路径） |
| `sha256` | string | 是 | 整个 jar 文件的 SHA-256，小写十六进制 |
| `loaderFamily` | enum | 是 | `FABRIC` / `NEOFORGE` / `FORGE` / `QUILT` / `UNKNOWN` |
| `status` | enum | 是 | `OK` / `FAILED` |
| `modId` | string | 否 | 模组 ID。`FAILED` 时为 `null` |
| `version` | string | 否 | 原始版本字符串，原样保留不做规范化 |
| `name` | string | 否 | 展示名 |
| `side` | enum | 否 | `CLIENT` / `SERVER` / `BOTH` / `UNKNOWN` |
| `depends` | array\<Constraint\> | 否 | 硬依赖 |
| `recommends` | array\<Constraint\> | 否 | 软依赖（缺失时警告但继续） |
| `breaks` | array\<Constraint\> | 否 | 冲突声明 |
| `provides` | array\<string\> | 否 | 该模组对外提供的能力/ID |
| `entrypoints` | array\<string\> | 否 | 入口点 key 列表（Fabric）；Forge 系留空数组 |
| `mixinConfigs` | array\<string\> | 否 | jar 内 mixin 配置文件路径（只读出路径名，不解析内容） |
| `mixinTargets` | array\<string\> | 否 | 该模组声明要注入的目标类，形如 `net.minecraft.client.render.GameRenderer`，见 §2.4 |
| `mixinTargetsInferred` | boolean | 否 | `mixinTargets` 是否由命名约定推断而来（M0 恒为 `true`），见 §2.4 |
| `nestedJars` | array\<string\> | 否 | 内嵌 jar 路径（Fabric `jars`；Forge `jarJar`） |
| `modType` | enum | 否 | 模组类型，判定规则见 §2.3 |
| `invasiveness` | enum | 否 | 侵入深度评级，判定规则见 §2.3 |
| `invasivenessSignals` | array\<string\> | 否 | 触发评级的具体信号，见 §2.3 |
| `supportPolicy` | enum | 否 | `SUPPORTED` / `DEGRADED` / `EXCLUDED`，见 §2.3 |
| `failureCode` | enum | 否 | `status=FAILED` 时必填，见 §4 |
| `failureMessage` | string | 否 | 人类可读原因 |
| `unknownFields` | array\<string\> | 否 | 元数据中出现但本规格未定义的顶层字段名 |

### Constraint 结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `targetId` | string | 依赖目标的模组 ID 或能力 ID |
| `versionRanges` | array\<string\> | **原始**版本区间字符串，原样保留 |
| `required` | boolean | 硬依赖为 `true` |
| `ordering` | enum | `NONE` / `BEFORE` / `AFTER` |
| `side` | enum | `CLIENT` / `SERVER` / `BOTH` / `UNKNOWN` |

> 版本区间的**字符串**在 M0 不做求值，只做原样保留与格式登记。
> 区间求值属于 M0.5，本阶段禁止 AI 自行发明比较逻辑。

---

## 2. 元数据来源映射

### 2.1 Fabric / Quilt：`fabric.mod.json`（jar 根目录）

| 统一字段 | 来源 |
|---|---|
| `modId` | `id` |
| `version` | `version` |
| `name` | `name` |
| `side` | `environment`（`*`→`BOTH`，`client`→`CLIENT`，`server`→`SERVER`） |
| `depends` | `depends`，`required=true` |
| `recommends` | `recommends`，`required=false` |
| `breaks` | `breaks`，`required=true` |
| `provides` | `provides` |
| `entrypoints` | `entrypoints` 的全部 key |
| `mixinConfigs` | `mixins`（数组） |
| `nestedJars` | `jars` 中每项 `file` |

`loaderFamily` 判定：存在 `fabric.mod.json` 且 `schemaVersion >= 1` → `FABRIC`。
若 jar 内另有 `quilt.mod.json` → `QUILT`。

### 2.2 NeoForge / Forge：`META-INF/neoforge.mods.toml` 或 `META-INF/mods.toml`

| 统一字段 | 来源 |
|---|---|
| `modId` | `[[mods]]` 中**第一条**的 `modId` |
| `version` | 该条 `version`（可能为 `${file.jarVersion}`，原样保留，不算失败） |
| `name` | `displayName` |
| `side` | `displayTest`（`NORMAL` 视为 `BOTH`） |
| `depends` | `[[dependencies.<modId>]]` 中 `type="required"` |
| `recommends` | `type="optional"` |
| `breaks` | `type="incompatible"` |
| `provides` | `[[mods]]` 的 `provides`（NeoForge） |
| `nestedJars` | `[[jarJar]]`（NeoForge） |
| `mixinConfigs` | `[[mixins]]` 的 `config` 字段（只读路径名，不解析内容） |

`loaderFamily` 判定：存在 `META-INF/neoforge.mods.toml` → `NEOFORGE`；
仅有 `META-INF/mods.toml` → `FORGE`。

> **`displayTest` 的合法取值**：`NORMAL`、`IGNORE_ALL_VERSION`、`MATCH_VERSION`、`IGNORE_SERVER_VERSION`。
> 不在此集合内的值记 `UNKNOWN` 并打 WARN，**不判 `FAILED`**——误判会伤及正常模组。
> 依赖对象的 `ordering` / `side` 不属于此列，未知值必须判 `MALFORMED_TOML`（见 §4）。

> **多 `[[mods]]` 条目**：只取第一条，并以 WARN 记录。

---

### 2.3 侵入深度评级（本项目的差异化能力）

**背景**：Fabric 与 NeoForge 模组建立在不同的基线假设上——Fabric 模组期待「原版 MC + Mixin 注入」，NeoForge 模组期待「MC 已被 Forge 打过补丁」。元数据层无法预测两者运行时是否冲突，但**可以在启动前识别深度侵入的模组并预警**。

**扫描范围限定为 L1（描述符级）**：只读取 zip 内的**文件名与元数据文本**，不解析 class 文件内容、不加载类。
任何 class 常量池扫描（L2）属于后续阶段，M0 禁止实现。

#### 判定信号

按以下顺序判定，**命中即停止**（取最高档）：

| 档位 | 信号（满足任一） | 含义 |
|---|---|---|
| `RENDER` | mixin 配置或入口点中引用 `net.minecraft.client.render` 下的类；模组名/ID 命中已知渲染类清单（见下） | 触碰渲染管线，冲突风险最高 |
| `CORE` | 存在 `META-INF/coremods.json`；`mods.toml` 中存在 transformer 条目；jar 内存在 ASM 相关类路径 | 直接操作字节码 |
| `MIXIN` | 存在任一 `mixins.*.json` 配置或 `mixinConfigs` 非空 | 运行时注入行为 |
| `API` | 无以上信号 | 仅使用 API 契约 |

**已知渲染/光影类模组清单**（硬编码常量，允许后续扩展）：
`sodium`、`iris`、`oculus`、`optifine`、`rubidium`、`embeddium`、`sodiumextra`、`reeses-sodium-options`、`dynamiclights`、`lambdynamiclights`

#### `modType` 判定

| 值 | 判定 |
|---|---|
| `LIBRARY` | `provides` 非空，或 ID 命中已知前置库清单（`fabric-api`、`fabric`、`architectury`、`neoforge`、`forge`、`kotlinforforge`、`fabric-language-kotlin`） |
| `SHADER_LOADER` | ID 命中 `iris` / `oculus` / `optifine` |
| `OPTIMIZER` | ID 命中 `sodium` / `rubidium` / `embeddium` |
| `HACK_CLIENT` | ID 命中已知外挂清单（`meteor-client`、`wurst`、`impact`、`future`、`liquidbounce`、`athena`、`rusherhack`） |
| `NORMAL` | 其余 |

#### `supportPolicy` 判定

| 值 | 条件 |
|---|---|
| `EXCLUDED` | `modType = HACK_CLIENT`、ID 为 `optifine`、**或 `invasiveness` 为 `RENDER` / `CORE`** |
| `DEGRADED` | M0 阶段**无来源**，恒为 0；保留该值供后续阶段使用 |
| `SUPPORTED` | 其余 |

**这三条是产品决策，不是技术失败。** 被标记 `EXCLUDED` 的模组不做任何兼容性承诺。

> **为什么 `RENDER` / `CORE` 是 `EXCLUDED` 而不是 `DEGRADED`**：
> M0 只能预测**声明性**冲突，无法预测**行为性**冲突。
> 深度侵入渲染管线或直接操作字节码的模组，其行为性冲突完全超出本阶段的观测能力，
> 因此必须按"不做承诺"处理，而不是"降级支持"。
> 这与 §2.4 冲突预测的定位一致：只预警，不保证。

#### 日志要求

- 命中 `RENDER` / `CORE` 时打 **WARN**，格式：`侵入深度 {} 于 {}，信号：{}`
- 命中 `EXCLUDED` 时打 **WARN**，格式：`排除模组 {}（{}），不做兼容性承诺`
- 不得以任何理由将上述降级为 DEBUG 或静默

---

### 2.4 Mixin 目标索引与静态冲突预测（本项目的第二项差异化能力）

#### 为什么需要它

依赖冲突（§3）只是"谁需要谁"。真正让模组组合崩掉的是**行为冲突**：两个模组要往同一个类的同一个方法里注入代码。

传统做法是运行时崩溃后人工累积黑名单（Sinytra Connector 的 incompatible 列表就是这么来的）。
本项目要做的是**启动前预测**：不启动游戏，只靠静态信息，指出"这两个模组可能撞在一起"。

#### M0 阶段能拿到什么（诚实边界）

Fabric 的 mixin 配置文件是**明文 JSON**，形如：

```json
{
  "package": "me.jellysquid.mods.sodium.mixin",
  "mixins": ["GameRendererMixin", "LevelRendererMixin"],
  "client": ["RenderSystemMixin"]
}
```

它给出了 **mixin 实现类的名字**，但**不直接给出目标类名**——
目标类写在 mixin 实现类的 `@Mixin(GameRenderer.class)` 注解里，而读注解需要解析 class 文件（属 L2，本阶段禁止）。

因此 M0 采用**命名约定推断**：

| mixin 实现类名（去掉包名） | 推断目标类 |
|---|---|
| `GameRendererMixin` | `GameRenderer` |
| `LevelRendererMixin` | `LevelRenderer` |
| `MinecraftMixin` | `Minecraft` |
| `MixinGameRenderer` | `GameRenderer` |
| `MixinBloodFluid` | `BloodFluid` |

推断规则**三条都要做**（真实数据同时存在这几种风格）：

1. **后缀式**：去掉尾部的 `Mixin` / `MixinPlugin` / `Accessor` / `Invoker`
2. **前缀式**：去掉开头的 `Mixin`
3. **嵌套类**：含 `$` 的类名，先按 `$` 切分，**对每一段独立执行上面两条**，再拼回

| mixin 实现类名 | 推断目标类 |
|---|---|
| `VertexMultiConsumerMixin$DoubleMixin` | `VertexMultiConsumer$Double` |
| `VertexMultiConsumerMixin$MultipleMixin` | `VertexMultiConsumer$Multiple` |

> 只做后缀式是不够的。实测 191 个 OK 记录中有 **54 个**写了 `mixinConfigs` 却推断不出任何目标，
> 原因就是大量真实配置使用前缀式命名（BiomesOPlenty 的 `MixinBloodFluid`、`MixinLiquidNullFluid`）。
> 更糟的是 `MixinGameRenderer` 会作为独立目标留下，与 `GameRenderer` **分裂计数**，
> 直接压低目标命中数，导致稀有度统计失真。
>
> **只做前后缀、不处理嵌套类会漏掉整个目标**。实测 sodium 的
> `VertexMultiConsumerMixin$DoubleMixin` 被推断成 `VertexMultiConsumerMixin$Double`（残留 `Mixin`），
> 而这两条是 sodium 中唯一指向 `VertexMultiConsumer` 的 Mixin——
> 真正的目标类完全没有进入索引，冲突预测会直接漏掉它。
> 修复后目标名变为 `VertexMultiConsumer$Double`，HIGH 总数未变（错误名原本也在集合里），
> **判断依据必须是目标集合的实际字符串，不是数字**。

**局限必须明确标注**：

- 推断结果只到**简单类名**（如 `GameRenderer`），不带完整包路径
- 存在漏报（目标类名与 mixin 类名无关时推断不出）与误报（两个不同包下同名的类）
- 因此 `mixinTargetsInferred` 在 M0 阶段**恒为 `true`**，输出中必须显式提示这是推断值

> 精确的目标类需要读取 class 文件注解中的类型描述符（L2）。
> 该能力属后续阶段，届时须重新评估"不得引入 ASM"这条禁令，并由人工确认后再开放。
> **M0 阶段严禁以任何理由实现 L2。**

#### 冲突预测规则

对全部 `status=OK` 的模组，先按**唯一 `modId` 聚合**（同一模组的 Fabric / NeoForge 变体合并为一个，
避免自己与自己比较），再两两比对 `mixinTargets`。

##### 第一步：计算目标稀有度（真实数据倒逼出来的必要修正）

`GameRenderer`、`Player`、`Minecraft`、`LivingEntity` 这类类被**大量模组共同注入**——
它们相同并不说明任何问题。首轮实测：116 个模组、6670 对中，朴素"目标类同名即 HIGH"
产出 **649 组 HIGH（约 10%）**，其中绝大多数由少数几个高频目标类造成。**这个信噪比不可用。**

因此必须先统计每个目标类被多少个模组命中：

| 概念 | 定义 |
|---|---|
| `targetModCount` | 命中该目标类的模组数 |
| `UBIQUITOUS_THRESHOLD` | **10%**，即 `targetModCount ≥ ceil(模组总数 × 0.10)` 的目标类视为 **ubiquitous（人人都改）** |

> **25% 是错的，已下调到 10%。** 首轮按 25% 计算时阈值 = 29，而实测最高目标
> `Minecraft` 只有 20、`GameRenderer` 只有 14，**没有任何目标达标**，
> 稀有度规则空转，HIGH 仍是 649 组没有下降。
>
> 根因是目标分布**极度长尾**：116 个模组散落在数百个不同目标类上，
> 头部类被 12% 的模组命中就已经是"人人都改"了。25% 这个比例对这种分布不适用。
>
> 按 10%（116 × 0.10 = 11.6，取整 12）计算，实测头部目标
> `Minecraft 20`、`GameRenderer 14`、`Entity 13`、`Gui 13`、`LivingEntity 12`
> 会被正确标记为 ubiquitous，稀有度规则才真正生效。

##### 第二步：分级

| 等级 | 条件 | 含义 |
|---|---|---|
| `HIGH` | 共享目标**全部不是** ubiquitous（且至少共享 1 个） | 撞在不寻常的地方，真正值得看 |
| `MEDIUM` | 共享目标中**至少有 1 个** ubiquitous | 撞在人人都在改的类上，很可能是噪声 |
| `LOW` | 仅一方为 `RENDER`/`CORE`，另一方为 `MIXIN` | 风险较低 |
| `NONE` | 其余 | — |

> **为什么是 all-rare 而不是 any-rare**（实测倒逼出来的语义修正）：
>
> 按字面 any-rare（"共享目标中至少 1 个非 ubiquitous 即 HIGH"），实测 HIGH **810 组（12.14%）**，
> 反而**高于**未启用稀有度规则的基线 649——规则形同虚设。
>
> 语义上也站不住：两个模组共享 3 个目标，其中 2 个是 `GameRenderer`、`Minecraft`
> 这类人人都在改的类、只有 1 个是稀有类，它们**实际撞车的地方大概率就是那些高频类**，
> 稀有类只是顺带命中。判 HIGH 是噪声。
>
> all-rare 才表达"它们撞的位置确实不寻常"。实测对比：
>
> | | HIGH | 占比 | 前 10 组内容 |
> |---|---:|---:|---|
> | 无稀有度规则（基线） | 649 | 9.7% | 全是 `GameRenderer` / `Player` |
> | any-rare | 810 | 12.1% | 仍以高频类为主 |
> | **all-rare（采纳）** | **331** | **4.96%** | `WorldRenderer`、`ToastComponent`、`ParticleManager` 等具体目标 |

##### 必须输出稀有度统计

`conflicts.csv` 之外，摘要必须额外打印**命中数最高的 10 个目标类**，形如：

```
[INFO]  高频目标类：GameRenderer 42 | Player 38 | Minecraft 31 | LivingEntity 27 …
```

没有这一行就无法判断 HIGH 是否可信——**高频目标类决定了这份报告的噪声上限**。

**这不是结论，是预警。** 输出中必须明确：静态预测会漏报也会误报，HIGH 不代表一定崩，NONE 不代表一定稳。

#### 输出

冲突预测结果写入独立的 `conflicts.csv`：

```
modA,modB,level,sharedTargets
sodium,iris,HIGH,"GameRenderer;LevelRenderer"
```

并在 stdout 摘要中输出一行：

```
[INFO]  冲突预测：HIGH 3 组 | MEDIUM 7 组 | LOW 12 组（静态推断，非运行结论）
```

#### 日志要求

- 每检出一组 `HIGH` 打 **WARN**，格式：`潜在冲突 {} × {}，共同目标：{}`
- 摘要中必须出现"静态推断，非运行结论"字样，不得省略
- 不得以任何理由将上述降级为 DEBUG 或静默

---

## 3. 依赖图（M0 第二阶段，本阶段仅登记不求解）

M0 第二阶段输入全部 `ModCandidate`，输出：

- `nodes`：全部 `status=OK` 的 `modId`
- `edges`：`depends` / `recommends` / `breaks` 三类有向边
- `report.unsatisfiedRequired`：目标 ID 在全部节点中不存在的硬依赖
- `report.cycles`：环（硬依赖构成）
- `report.duplicateIds`：同一 `modId` 出现多个不同 `version`

**M0 不求解版本区间，不做拓扑排序。**

---

## 4. 失败分类（`failureCode`）

| code | 含义 | 处理 |
|---|---|---|
| `NOT_A_ZIP` | 文件不是有效 zip | 该 jar 记 FAILED，继续处理其余 jar |
| `NO_METADATA` | zip 中找不到任何已知元数据文件 | FAILED，继续 |
| `MALFORMED_JSON` | JSON 语法错误 | FAILED，继续 |
| `MALFORMED_TOML` | TOML 语法错误 | FAILED，继续 |
| `MISSING_REQUIRED_FIELD` | 缺少 `id` / `modId` / `version` 等必需字段 | FAILED，继续 |
| `IO_ERROR` | 读取异常 | FAILED，继续 |
| `INTERNAL_ERROR` | 程序层面的意外异常（不是数据问题） | FAILED，继续；`failureMessage` 必须写明这是程序异常不是数据问题，并带异常类名 |

**关键约束**：单个 jar 失败**不影响其余 jar**，但**必须计入最终报表的失败计数**。
任何情况下不得出现"总输入 200，输出记录 187"而无解释的情况。
报表必须恒满足：`OK 数 + FAILED 数 == 输入文件数`。

---

## 5. 日志等级规范（强制）

使用 SLF4J 门面 + Log4j2 实现。等级从高到低：`ERROR` / `WARN` / `INFO` / `DEBUG` / `TRACE`。

| 等级 | 使用场景 | 示例 |
|---|---|---|
| `ERROR` | 单个 jar 解析失败；依赖图出现不可满足的硬依赖；出现环 | `解析失败 {}: {}` |
| `WARN` | 结果可用但可能不完整：未知字段、多 mod jar、软依赖缺失、深度侵入命中、排除模组 | `侵入深度 {} 于 {}，信号：{}` |
| `INFO` | 阶段级摘要，每个阶段最多一行 | `扫描 {} 个 jar：OK {}，FAILED {}` |
| `DEBUG` | 单个 jar 的解析过程 | `解析 {} → modId={}` |
| `TRACE` | 字段级细节，默认关闭 | `读取 depends 条目 {}` |

### 硬性规则（AI 违反即打回）

1. **禁止用 `try/catch` 把异常吞成 WARN 或静默继续。** 捕获异常必须：填充 `failureCode` + `failureMessage` → 以 `ERROR` 记录 → 该条目标记 FAILED。不允许"catch 后什么都不做"。
2. **禁止把 ERROR 降级为 WARN 以让进程跑完。** 进程继续是因为"其余 jar 独立"，不是因为"这个错误不重要"。
3. **禁止 `printStackTrace()` 和 `System.out.println()`。** 全部走 SLF4J。
4. **日志必须参数化**：`log.warn("侵入深度 {} 于 {}", level, file)`，禁止字符串拼接。
5. **禁止记录完整 jar 二进制或大段元数据原文。**
6. **默认输出等级为 `INFO`**；`DEBUG` 通过 JVM 参数 `-Dlog.level=debug` 开启。
7. **侵入深度命中 RENDER / CORE 与 EXCLUDED 判定必须以 WARN 输出，不得降级。**
8. 一个 jar 的失败**最多产生一条 ERROR**，不得因重试而重复刷屏。

---

## 6. 输出格式

### 6.1 `candidates.jsonl`

每行一个 JSON 对象，对应一个输入 jar，**顺序与输入一致**。

### 6.2 `summary.csv`

表头：`sourceFile,sha256,loaderFamily,status,modId,version,modType,invasiveness,supportPolicy,failureCode`

### 6.3 `conflicts.csv`

表头：`modA,modB,level,sharedTargets`，规则见 §2.4。

### 6.4 stdout（INFO 级）

> 下面是**格式模板**，不是真实数字。每一个数字都必须来自实际运行，本文件不得写死任何数量。

```
[INFO]  输入 jar：200
[INFO]  解析成功：193 (96.5%)
[INFO]  解析失败：7
[INFO]    NOT_A_ZIP: 2 | NO_METADATA: 3 | MALFORMED_JSON: 1 | IO_ERROR: 1
[INFO]  FABRIC: 98 | NEOFORGE: 95 | UNKNOWN: 0
[INFO]  侵入深度：API 41 | MIXIN 131 | CORE 12 | RENDER 16
[INFO]  支持策略：SUPPORTED 172 | DEGRADED 28 | EXCLUDED 0
[INFO]  冲突预测：HIGH 3 组 | MEDIUM 7 组 | LOW 12 组（静态推断，非运行结论）
[INFO]  硬依赖总数：412，不可满足：23
[INFO]  环：1
```

最后必须输出一行固定提示：

```
[INFO]  本结果为准入检查，不构成运行稳定性保证
```

**验收不变量（必须显式断言）**：`成功数 + 失败数 == 输入数`。
此断言失败即视为构建失败。

---

## 7. 验收标准

1. `fixtures/jars/` 下**全部**真实 jar 均产出记录，无遗漏。**数量必须由文件系统真实列举得出，本文件不得写死任何数字**（历史上曾误写"200 个"，实际数据集为 195 个）。
2. 人工抽取 10 个 jar 解包核对，`modId` / `version` / `depends` 三项与原始元数据一致率 100%。
3. `OK + FAILED == 输入数` 恒成立，测试显式断言。
4. 单元测试覆盖全部 **7 种** `failureCode`（`NO_METADATA`、`MALFORMED_JSON`、`MALFORMED_TOML`、`MISSING_REQUIRED_FIELD`、`IO_ERROR`、`NOT_A_ZIP`、`INTERNAL_ERROR`），**每个测试必须先构造一个确实会触发该失败的 fixture jar 并断言其失败**。禁止用 mock 绕过真实文件读取。`INTERNAL_ERROR` 无法靠数据触发，允许验证其兜底路径存在，但必须在 `PROGRESS.md` 说明它不是数据触发的。
5. 侵入深度评级：人工抽取 10 个已知类型的模组（含至少 1 个渲染类、1 个前置库）核对 `invasiveness` 与 `modType` 判定正确。
6. 无 `System.out.println`、无 `printStackTrace`、无空 catch 块——构建时以静态检查强制（Checkstyle 或 ArchUnit）。
7. Mixin 目标索引：人工抽取 5 个含 mixin 的模组，解包核对 `mixinTargets` 是否与其 mixin 配置文件中的类名一致；`mixinTargetsInferred` 必须为 `true`，且摘要中必须出现"静态推断，非运行结论"字样。**目标集合中不得残留任何含 `Mixin` 字样的名字**。
8. 遇到不可读或非 zip 的输入文件**不得抛异常终止**，必须产出 `status=FAILED` 的记录并计入失败数（见 §4 关键约束）。
9. 冲突预测信噪比：摘要必须打印高频目标类 Top 10；`HIGH` 组数不得超过总对数的 **5%**，且**必须低于**未启用稀有度规则时的基线（649/6670 = 9.7%）。若 HIGH 数与基线持平，即判定稀有度规则未生效，必须复查阈值与目标推断是否漏了前缀式类名。
10. 目标推断覆盖率：`mixinConfigs` 非空但 `mixinTargets` 为空的记录数不得超过 OK 总数的 **10%**。首轮只剥后缀时该缺口为 54/191（28%），加上前缀剥离后应显著下降。

---

## 8. 技术栈（不得替换）

- Java 21
- Gradle 8.x（Kotlin DSL 或 Groovy 均可）
- SLF4J 2.x + Log4j2 2.x
- JSON：`com.google.code.gson:gson`
- TOML：`com.electronwill.night-config:toml`
- 测试：JUnit 5 + AssertJ
- **禁止**引入 Minecraft、Fabric Loader、Forge、NeoForge、Mixin、ASM 相关依赖

> 侵入深度 L1 扫描只需要 zip entry 名与元数据文本，不需要任何字节码库。
> 任何 class 文件解析（L2）在本阶段禁止实现。
