# SPEC.zh-CN.md — M0–M1.5：离线模组元数据归一化、侵入深度评级、依赖可满足性判定、环检测与报告聚合字段

<p align="center">
  <a href="./SPEC.md">English</a> · <b>简体中文</b>
</p>

版本：v1.1
状态：草稿待人工确认
上游启发：Forbric v0.3.0（Ray-T-r/Minecraft-Forbric-mod-loader，Apache-2.0）

---

## 0. 本项目当前不是模组加载器

M0 **不启动 Minecraft、不加载类、不处理 Mixin、不改写字节码**。
它只做一件事：给定一个模组 jar 文件，读出它的元数据与结构特征，产出一条结构化的 `ModCandidate` 记录。

M1（§3.6）在这些记录之上构建依赖图并检测环。它同样不启动 Minecraft、不加载类、不读 class 文件，
**不输出安装顺序**（§3.6.5）。

M1.5（§3.7）在已有记录之上补两个聚合报告字段——`unsatisfiedRequired` 与 `duplicateIds`。它同样不加载任何东西，
不选版本、不做兼容、不做修复，并让 M0 / M0.5 / M1 的每个产物保持逐字节不变。

### 定位（必须准确理解）

M0 的输出是**「能否启动的准入检查」**，不是**「能否稳定运行的保证」**。

- M0 **能**预测声明性冲突：依赖缺失、版本区间不相交、显式不兼容声明
  （环检测**不属于** M0 或 M0.5，它由 M1 规定，见 §3.6）
- M0 **不能**预测行为性冲突：两个 Mixin 撞同一方法、两个模组替换同一渲染阶段、注册表生命周期顺序导致 NPE

行为性冲突只能在运行时暴露。**把 M0 宣传成"能否稳定运行的保证"属于误导。**

明确不在范围内（AI 不得提前实现，也不得"顺手加上"）：

- 任何 ClassLoader、JVM agent、字节码操作（ASM / Mixin / Tiny Remapper）
- 任何 Minecraft 启动、下载、打补丁逻辑
- 任何跨加载器互操作（事件桥、物品/流体/能量）
- 任何 GUI、配置文件、安装器
- 任何"自动兼容"或"自动修复"能力
- 任何以安装顺序、加载顺序或拓扑序为形式的输出——§3.6.5 明令禁止

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
- `report.unsatisfiedRequired`：本次运行能够证明不满足的**硬依赖**声明——**构成与排除项由 §3.7.2（M1.5）定死**，
  M0、M0.5、M1 都不产出
- `report.cycles`：环（硬依赖构成）——**M0 与 M0.5 都不产出，由 M1 规定，见 §3.6**
- `report.duplicateIds`：单个宇宙内同一 `modId` 有多于一条 `status=OK` 记录，按版本相同 / 版本不同分开计数——
  **定义见 §3.7.3（M1.5）**，M0、M0.5、M1 都不产出

**M0 不求解版本区间，不做拓扑排序。**

> 本节是内存契约：这些字段一个都不写入文件。M1（§3.6）只覆盖 `nodes`、`edges` 与 `report.cycles`，
> 且它的环图只使用硬依赖——`recommends` 与 `breaks` 在这里登记，在那边永远不成为边。
> `report.unsatisfiedRequired` 与 `report.duplicateIds` **不属于** M1，在 M1 里保持未实现状态；
> M1 不得实现它们，也不得"顺手加上"。唯一产出它们的是 M1.5（§3.7），且是运行级聚合字段。
> M1 同样不求解版本区间，排序只用于判定是否有环，且永不输出顺序（§3.6.5）。

---

## 3.5 版本区间求值与可满足性判定（M0.5）

M0.5 对 M0 只登记、不求值的版本区间做求值。它对每条依赖只回答一个问题：
**「本集合里有没有模组满足这条声明的要求」**。

### 3.5.1 范围边界（不得越界）

M0.5 **要做**：

- 解析并求值两个加载器家族的版本区间字符串
- 把每条依赖声明与**实际存在**的模组做比对
- 报告被命中的显式 `breaks` 声明

M0.5 **不做**：

- 拓扑排序，或产出安装/加载顺序
- 构建或"求解"依赖图（不做版本回溯，不做候选选择）
- 下载任何东西，或建议装哪个版本
- 把结果合并进 `conflicts.csv`（M0 的数字必须保持可复现；M0.5 写独立文件）

### 3.5.2 环境提供的 ID（必须处理，否则几乎所有模组都会报 MISSING）

加载器与平台 ID **不是数据集里的模组**。它们由运行环境提供，必须按声明的环境事实解析，
**永远不得**报成 `MISSING_ID`。

| ID | 含义 | 解析方式 |
|---|---|---|
| `minecraft` / `minecraft_version` | 游戏版本 | 本次运行声明的环境事实 |
| `fabricloader` / `fabric-loader` | Fabric Loader 版本 | 环境事实 |
| `quilt_loader` / `quilt-loader` | Quilt Loader 版本 | 环境事实 |
| `neoforge` / `forge` | 加载器版本 | 环境事实 |
| `java` | JDK 版本 | 环境事实 |

规则：

- 这些 ID 按声明的环境版本解析；数据集里没有它们是正常情况
- 其余任何既不在数据集里、也不由环境提供、也没有出现在任何 `provides` 列表里的 ID（见 §3.5.5）
  **就是** `MISSING_ID`
- 环境事实必须在启动时以 INFO 打印一次，使数字可审计
- `recommends` 指向不存在的 ID 记 WARN，永不记失败（与 M0 一致）

---

### 3.5.3 版本区间语法（两套互不兼容的语法，必须分开解析）

**Fabric / Quilt**（`fabric.mod.json` 的约束字符串）：

| 形式 | 含义 |
|---|---|
| `*` | 任意版本 |
| `>=1.2.3` / `>1.2.3` / `<=1.2.3` / `<1.2.3` / `==1.2.3` | 比较 |
| `~1.2` | 波浪号：`>=1.2, <1.3` |
| `1.21.x` | 该位置的 `x`（或 `X` / `*`）段按通配匹配 |
| `>=1.2 <1.5` | 空格分隔的约束，**全部**成立（AND） |

**NeoForge / Forge**（`mods.toml` 的 `versionRange`，Maven 区间语法）：

| 形式 | 含义 |
|---|---|
| `[21.1.219,)` | `>= 21.1.219` |
| `(21.1.219,)` | `> 21.1.219` |
| `[1.21,1.22)` | `>= 1.21` 且 `< 1.22` |
| `(,1.22]` | `<= 1.22` |
| `[1.21.1]` | 恰好 `1.21.1` |
| `21.1.219`（无括号） | 下界含等的 `>= 21.1.219`（Maven 软要求约定） |

无括号的 NeoForge 值必须打 DEBUG 记录采用了哪条约定，因为这是两套语法唯一明确分歧的形式。

### 3.5.4 版本比较规则

- 按 `.` 和 `-` 切成段，逐段比较
- 数字段按数值比较；非数字段按字典序比较
- 缺失的尾部段按 `0` 计（比较意义上 `1.21` == `1.21.0`）
- 预发布段（`-beta`、`-rc1`）**低于**同版本号的正式版
- 通配段匹配该位置的任意值
- 比较基于字符串、确定且可复现：**不使用任何"semver 库"，不引入外部依赖**

### 3.5.5 存在性判定与每条依赖的 outcome（每条恰好一个）

一个 ID 按**以下顺序解析，命中即停**：

| # | 条件 | 解析方式 |
|---|---|---|
| 1 | 该 ID 是某条 `status=OK` 记录的 `modId` | 用该模组的实际版本；`provides` 别名**不参与** |
| 2 | 该 ID 是环境提供的 ID（§3.5.2） | 用声明的环境事实 |
| 3 | 该 ID 只被一个 `modId` 声明在 `provides` 里 | 用该拥有者的实际版本 |
| 4 | 该 ID 被两个及以上 `modId` 声明在 `provides` 里 | `AMBIGUOUS_ALIAS`，不选任何一个拥有者 |
| 5 | 以上都不是 | `MISSING_ID` |

**第 1 条优先级最高：真实 `modId` 是权威身份，别的模组把它写进 `provides` 也不构成歧义。** 别名只是
“这个 ID 不是任何模组”时的替补；`fabric-api` 这个模组永远优先于被当作别名的 `fabric-api`。

只有一种情况仍然算冲突：别名与**环境提供的 ID** 重名。如果某个模组声明了 `provides: ["minecraft"]`
（或 `java`、`fabricloader`、`neoforge` 等），那么对该环境 ID 的依赖判 `AMBIGUOUS_ALIAS`，环境事实与声明
该别名的模组都写进 WARN。环境不优先于声明了同一 ID 的模组，因为这种重名本身就是加载错误。

`provides` 里的条目是声明它的模组的别名，Fabric Loader 会把这些 ID 当作存在的模组（见 `fabric.mod.json`
文档）。拥有者按 `modId` 去重：模组在自己的 `provides` 里写上自己的 `modId` 仍只算一个拥有者。

| Outcome | 条件 |
|---|---|
| `SATISFIED` | 该 ID 经第 1、2 或 3 条解析成功，且解析到的版本在区间内。对 `breaks` 声明：目标不存在（第 5 条），或解析成功且版本**不在**区间内（即没有冲突） |
| `MISSING_ID` | 该 ID 落到第 5 条 |
| `VERSION_MISMATCH` | 该 ID 经第 1、2 或 3 条解析成功，但版本不满足区间 |
| `BREAKS_VIOLATED` | `breaks` 声明，经第 1–3 条解析成功且版本在区间内 |
| `AMBIGUOUS_ALIAS` | 该 ID 命中第 4 条，或是被模组同时声明为 `provides` 别名的环境 ID；不选任何一个，`resolvedVersion` 为 `null`；所有候选按 WARN 打出，并注明各自是怎么被找到的（`modId` / `provides` / 环境） |
| `UNPARSEABLE` | 区间字符串无法解析 |

**为什么 `AMBIGUOUS_ALIAS` 要新增第六种 outcome，而不是复用现有五种**：记 `MISSING_ID` 与事实不符（ID 确实存在）；
记 `SATISFIED` 等于静默挑了其中一个候选，正是本阶段禁止的行为；`VERSION_MISMATCH` / `UNPARSEABLE` 描述的是
版本字符串，不是身份。歧义 ID 与其他三种一样会阻断准入判断，所以它的计数与它们打印在同一行 `不可满足` 上。

**`UNPARSEABLE` 绝不能被静默当成满足，也绝不能被静默丢弃。** 它必须被计数、以 WARN 打出原串、
并出现在摘要里。无法解析的区间是本工具的缺陷，不是模组的缺陷。

---

### 3.5.6 输出

新增文件 **`dependencies.csv`**（每条依赖声明一行，含表头）：

```
modId,version,depId,depKind,declaredRange,resolvedVersion,outcome
```

- `depKind`：`required` / `recommends` / `breaks`
- `resolvedVersion`：实际找到的版本，不存在或存在歧义时为 `null`
- 每条依赖声明**恰好出现一次**——不去重、不省略

stdout（INFO 级）必须打印：

```
依赖总数：硬依赖 N | 软依赖 N | 冲突声明 N
不可满足：MISSING_ID N | VERSION_MISMATCH N | BREAKS_VIOLATED N | AMBIGUOUS_ALIAS N
无法解析的版本区间：N（实现缺陷，不是模组问题）
```

### 3.5.7 M0.5 验收标准

1. 每个 `status=OK` 记录的全部依赖声明，在 `dependencies.csv` 里恰好出现一次
2. `UNPARSEABLE` 数量被显式打印，且**不**被并入 `SATISFIED`
3. 任何环境提供的 ID（§3.5.2）都不得被报成 `MISSING_ID`
4. 人工抽 10 条依赖对照原始元数据文件：`modId` / `version` / `declaredRange` / `outcome`
   必须一致，100%
5. `gradle clean test` 通过，**跳过数 0**；测试不得使用 `assumeTrue`
6. M0 自己的数字（HIGH 331 / 6670、覆盖率缺口 10/191）必须保持不变——M0.5 只新增一个文件，
   不改写 M0 的结果
7. `provides` 别名解析到拥有者的版本；一个 ID 被两个及以上模组声明时记 `AMBIGUOUS_ALIAS`，
   计数被打印，并以 WARN 列出全部候选
8. 依赖的 ID 本身是真实 `modId` 时，即使别的模组把同一字符串声明为 `provides`，也必须按第 1 条解析，
   永远不得判成 `AMBIGUOUS_ALIAS`（优先级规则的回归保护）

---

## 3.6 依赖图、拓扑排序与环检测（M1）

M1 构建的图，M0 只登记过、M0.5 只逐条求值过。它只回答一个问题：
**「这一组模组的硬依赖集合里有没有环？如果有，是哪些模组？」**

### 3.6.1 范围边界（不得越界）

M1 **要做**：

- 按加载器宇宙各建一张有向图（§3.6.2），输入只取 `status=OK` 的记录
- 把每一条硬依赖声明归入恰好一个桶（§3.6.3）
- 用拓扑排序判定是否有环，用强连通分量定位环组（§3.6.4）
- 写出 `cycles.csv` 与 §3.6.6 的三行 INFO

M1 **不做**：

- 启动 Minecraft、加载类、读 class 文件、处理 Mixin 或字节码、下载任何东西
- 重新求值版本区间、重新解析 ID，或重新判定 §3.5.5 的六种 outcome 中的任何一种
- 改写 `candidates.jsonl`、`summary.csv`、`conflicts.csv` 或 `dependencies.csv`
- 产出安装顺序、加载顺序或任何拓扑序——硬禁令见 §3.6.5
- 实现 §3 的 `unsatisfiedRequired` 或 `duplicateIds`；两者保持未实现，不属于 M1

> `dependencies.csv` 是 M0.5 的产物，内容逐字节保留。M1 **不**把它当作边的来源：一行里有 `modId` +
> `version` + `declaredRange`，但**没有**声明该行的 `loaderFamily`，因此一行无法被归属到某个宇宙。
> M1 的边取自 `ModCandidate` 记录，那里有 `loaderFamily`。

### 3.6.2 加载器宇宙（本节存在的原因）

数据集把 Fabric 与 NeoForge 的 jar 放在同一个目录里，但一台机器只跑一个加载器家族。混成一张图会造出任何机器
都不存在的环。真实数据把这件事讲得很清楚：191 条 `status=OK` 记录聚合成 116 个唯一 `modId`，其中
**71 个同时有 Fabric 与 NeoForge 两个变体**。按 `modId` 合并它们，等于把两套从不共存的声明合并成一套。

规则：**每个加载器宇宙一张图，边永不跨宇宙。**

| 宇宙 | 包含哪些记录 | 规则 |
|---|---|---|
| `FABRIC` | `loaderFamily = FABRIC` 或 `QUILT` | `QUILT` 并入，因为 §2.1 只在同时存在 `fabric.mod.json` 时才判 `QUILT`，即该 jar 带有 Fabric 可读的元数据 |
| `FORGE` | `loaderFamily = FORGE` | 故意**不**并入 `NEOFORGE`：那是兼容性断言，§0 禁止跨加载器断言 |
| `NEOFORGE` | `loaderFamily = NEOFORGE` | — |
| —（排除） | `loaderFamily = UNKNOWN` | 永不成为节点；排除在建图之外并计数（§3.6.6） |

以下后果全部是有意为之：

- 同一 `modId` 出现在两个宇宙里就是**两个不同的节点**，各自只携带本家族的声明
- 依赖目标只在另一个宇宙里有节点时，**不产生边**，记为 `OTHER_UNIVERSE`
- 只有把 Fabric 与 NeoForge 变体混在一起才存在的"环"，因此是**结构上不可能**，而不是事后过滤掉的

> **这是对当前数据集的事实陈述，不是"已验证有效"的声明。** `QUILT` 有 0 条记录（全部并入 `FABRIC`），
> `FORGE` 有 0 条记录，`OTHER_UNIVERSE` 桶同样是 0——数据集里没有任何一条硬依赖的目标是只存在于另一个宇宙的
> `modId`。只有两个宇宙非空，因此本节这条跨加载器分流规则**完全没有被真实数据触发过**：它只由 §3.6.7 第 1、2 条
> 的构造记录覆盖，除此之外没有任何证据。这里永远不许写成"跨加载器规则已验证有效"；唯一能说的已验证事实是
> "在一个分流不会产生可见差异的数据集上，FABRIC 与 NEOFORGE 被成功分开"。

### 3.6.3 节点与边（什么进图，什么不进）

**节点**：每个 `(宇宙, modId)` 一个。该宇宙内该 `modId` 的全部 `status=OK` 记录合并成一个节点，边集取并集。
M1 不选版本——真实数据集里有 4 个 `modId` 在同一家族内各有两条记录。

**边**：当且仅当该宇宙内 `A` 的某条记录声明了一条**硬依赖**，且其 `targetId` 恰好等于同一宇宙内节点 `B` 的
`modId` 时，存在 `A → B`。

硬依赖指的是**从 `depends` 读出的** `Constraint`。不得用 `required` 标志当判据：§2.1 与 §2.2 给 `breaks`
也设了 `required=true`。

| 声明 | 是否进图 | 理由 |
|---|---|---|
| `depends` | 进，条件是目标是同宇宙的节点 | 唯一断言"必须存在"的声明 |
| `recommends` | **永不** | 软依赖缺失是正常情况（§3.5.2）；两个模组互相推荐不是缺陷，建成边会凭空造出环 |
| `breaks` | **永不** | `breaks` 的含义是"不得共存"，与依赖相反；建成边等于伪造一条反向边并凭空造环 |
| `ordering`（`BEFORE` / `AFTER`） | 不使用 | M1 不输出顺序（§3.6.5）；排序提示不得夹带进边里 |

**身份判定就是 §3.5.5 的第 1 条，按宇宙收窄：真实 `modId` 是权威。** 第 2–5 条永不产生边：

| 桶（每条硬依赖恰好一个） | 条件 | 边 |
|---|---|---|
| `EDGE` | `targetId` 是同宇宙的节点 | 有 |
| `ENVIRONMENT` | `targetId` 是环境提供的 ID（§3.5.2） | 无——环境 ID 不是模组，永不成为节点 |
| `OTHER_UNIVERSE` | `targetId` 是节点，但只在另一个宇宙 | 无 |
| `ALIAS_ONLY` | `targetId` 不是节点，但被本宇宙某个 `provides` 声明 | 无——别名是替补身份，永不成为边；这一条同时覆盖全部 `AMBIGUOUS_ALIAS` 声明，M1 也不得挑一个拥有者 |
| `ABSENT` | 以上都不是 | 无 |

`EDGE + ENVIRONMENT + OTHER_UNIVERSE + ALIAS_ONLY + ABSENT == 硬依赖声明数` 必须在运行时断言，
与 `OK + FAILED == 输入数` 同等性质。落在任何桶之外的声明是程序缺陷，必须让本次运行失败，
不得跳过，不得算成边。

**`EDGE` 数的是声明，图数的是去重后的边。** 一个节点有多条记录（或一条记录把同一目标声明两次）时，会产生
重复的 `(源节点, 目标节点)` 对。桶等式按声明计数，§3.6.6 的边数按去重后的对计数。两者都需要，回答的是不同
问题，且**任何一个都永远不得大于 `EDGE` 声明数**。

> 当前数据集，由 `verify_m1_counts.py` 只读 `candidates.jsonl` 实测：`FABRIC` 98 个节点、`NEOFORGE` 89 个节点、
> `FORGE` 0 个节点；531 条硬依赖归桶为 `EDGE` 89（`FABRIC` 69、`NEOFORGE` 20）、`ENVIRONMENT` 374、
> `OTHER_UNIVERSE` 0、`ALIAS_ONLY` 18、`ABSENT` 50，合计 531。`FABRIC` 的 `continuity` 节点含两条都声明
> `fabric-api` 的记录，因此 69 条 `EDGE` 声明去重后是 68 条边，整张图共 68 + 20 = 88 条边。第十七步预演里的
> 85 条 `FABRIC` 边，是 68 条去重边加上 17 条"别名指向拥有者"的边得到的——它把 `ALIAS_ONLY` 声明当成了边，
> 而上面这张桶表正是禁止这件事的。

> **已知后果，写明而不是藏起来**：仅靠别名满足的依赖（`fabric`，拥有者是 `fabric-api`）不建模。
> 这个误差的方向是漏报——只有经过别名才可达的环会被漏掉，永远不会被凭空发明。§3.5.5 本来就拒绝把别名
> 当作权威身份，M1 保留这个拒绝。

### 3.6.4 环检测与规范化表示

**是否有环**用拓扑排序（Kahn）判定：反复移除入度为 0 的节点；当且仅当所有节点都被移除时图无环。
**移除顺序永不输出、永不记日志**（§3.6.5）。

**环组**就是强连通分量：节点数 ≥ 2 的分量，或带自环的单节点分量，各算一个环组。
环组里的每个成员都至少位于一个有向环上。

**一致性不变量**：拓扑排序能够完成 ⇔ 环组数为 0。两者不一致即为程序缺陷，必须让本次运行失败。

**规范化表示**——同一份输入每次运行必须产出逐字节相同的 `cycles.csv`：

- 环组写成它的**成员集合**，永不写成路径
- `members` 按 `modId` 的 **ASCII 码点升序**排序，因此第一个成员永远是字典序最小的那个——
  既没有起点，也没有方向需要规范化
- 行按 `universe` 排序（ASCII 升序：`FABRIC` < `FORGE` < `NEOFORGE`），再按 `members` 字符串（ASCII 升序）
- 一行里不得增加任何其他列

> **为什么用成员集合而不是环路径。** 只有当一个环组里恰好有一个基本环时，"从最小成员出发往前走"才是规范的。
> 一个强连通分量里可能有多个基本环，挑其中一个需要额外一条任意规则——那正是本节要消除的"两次运行不可比对"。
> 枚举全部基本环是指数级的，其顺序同样不规范。成员集合是唯一不变的表示，而且它回答的正是被问的问题：
> *哪些模组在环里*。
>
> 因此一行**不**声明"存在一个包含全部成员的环"，它声明的是：每个成员都在某个环上。

### 3.6.5 硬禁令：不得输出安装顺序、加载顺序或拓扑序

M1 计算拓扑排序只为一个目的：判定图是否有环。**其结果绝不能离开计算过程。**

以下内容在任何地方都被禁止——`cycles.csv`、stdout、包括 `DEBUG` 与 `TRACE` 在内的任何日志级别、
任何报告对象的任何字段，以及任何由本次运行生成的文档：

- 以安装、加载、解析或"按这个顺序做"的形式给出的 modId 序列
- 拓扑序、部分拓扑序，或其中任何一段前缀
- "入度为 0 的节点优先"清单、分层结构、依赖深度
- 任何暗示应按某个次序安装或加载模组的措辞

**违反即交付作废。**

> 理由：一旦发布顺序，工具就等于承诺"按这个顺序装就能成功"，而本项目做不出这个保证（§0）。
> 环报告只说明"这些模组在这套集合里不可能同时被满足"，它不说明任何排序。
> 如果有人要安装顺序，答案是拒绝，依据就是本节。

### 3.6.6 输出

新增文件 **`cycles.csv`**，含表头，不写占位行（当前数据集实测 0 个环组，因此产出的文件只有表头——
这是真实测量结果，不是占位行）：

```
universe,memberCount,members
FABRIC,2,"sodium;someaddon"
```

- 每个环组一行；`members` 用 `;` 连接，与 `conflicts.csv` 同一约定
- 没有环组时，文件只有表头
- 不重复 `dependencies.csv` 的任何数据，且 `dependencies.csv` 的 M0.5 内容逐字节保留
- `dependencies.csv` 与 `cycles.csv` 回答的是不同问题、口径不同，**绝不可互相加总**：`AMBIGUOUS_ALIAS`
  是在整个数据集上判定的（§3.5.5），`ALIAS_ONLY` 是在单个宇宙内判定的。同一条声明可以同时属于两者，
  两者互不矛盾。

stdout（INFO），位置在 §3.5.6 的三行之后、§6.6 那行固定结语之前：

```
[INFO]  依赖图：FABRIC 节点 N，边 N | FORGE 节点 N，边 N | NEOFORGE 节点 N，边 N | 未归入宇宙的 OK 记录 N
[INFO]  未进图的硬依赖：环境提供 N | 跨加载器宇宙 N | 仅别名 N | 目标不存在 N
[INFO]  依赖环：N 组
```

三个宇宙必须全部打印，为空也要打印，这样"0"就永远不会和"没算"混淆。
`边` 是该宇宙里去重后的 `(源节点, 目标节点)` 对数（§3.6.3），**不是** `EDGE` 桶的声明数；
只要某个节点的多条记录声明了同一个目标，两者就会不同。
各桶计数是所有宇宙的合计；§3.6.3 的按宇宙等式仍必须断言。

每个环组打一条 **ERROR**，格式 `依赖环 {}：{}`——第一格是宇宙，第二格是成员以 ` | ` 连接。
环组为 0 时不打任何 ERROR。

> 上面是**格式模板**，不是真实数字，与 §6.6 的口径一致。

### 3.6.7 M1 验收标准

以下十条只约束 **M1**。它们独立于 §7（M0 十条）与 §3.5.7（M0.5 八条）；三组不得混用，
通过其中一组永不意味着通过另一组。

1. **加载器宇宙**：构造一对记录，`A`（`FABRIC`）依赖 `B`，`B`（`NEOFORGE`）依赖 `A`，必须产出 **0** 个环组
   和两个节点（每宇宙一个）。这是针对跨加载器假环的首要回归保护。
2. **节点身份**：同一宇宙内两条 `modId` 相同的 `status=OK` 记录产出**一个**节点，边取并集；
   同一 `modId` 出现在两个宇宙产出**两个**节点。
3. **声明种类**：`recommends` 与 `breaks` 永不建边。构造 `A` 推荐 `B`、`B` 依赖 `A` 的集合，环组为 0；
   `A` breaks `B` 时同样为 0。
4. **不静默丢弃**：对每个宇宙，`EDGE + ENVIRONMENT + OTHER_UNIVERSE + ALIAS_ONLY + ABSENT == 硬依赖声明数`
   必须在运行时断言，并在测试里再断言一次；四个无边的桶都要打印，永不合并成一个数字。
5. **环检测**：2 环、3 环、自环（`A` 依赖 `A`）与无环图四组构造数据都必须判定正确；
   一个都不许跳过。
6. **一致性**："拓扑排序能够完成"与"环组数为 0"必须双向一致，在真实数据集与构造数据上都要断言。
7. **规范化输出**：`members` 必须正好是该环组按 ASCII 升序连接的结果，行的排序符合 §3.6.4 的要求，
   且同一份输入连续跑两次产出逐字节相同的 `cycles.csv`。
8. **任何地方都没有顺序**：报告对象不暴露任何有序节点序列；`cycles.csv` 只有 §3.6.6 的三列；
   任何 stdout 或日志行都不含安装顺序、加载顺序或拓扑序。审阅产出文件与日志是验收的一部分，不是形式。
9. **M0 与 M0.5 未被改动**：`candidates.jsonl`、`summary.csv`、`conflicts.csv`、`dependencies.csv` 的
   SHA-256 在 M1 前后必须完全相同；§7 与 §3.5.7 的基线（195 / 191 / 4、HIGH 331、6670 对、
   覆盖率缺口 10/191，以及六项 outcome 计数 612 / 72 / 7 / 7 / 18 / 0）必须保持不变。两者都记录在
   `PROGRESS.md` 里。
10. **构建与日志**：`gradle clean test` 通过，**跳过 0**，且不使用 `assumeTrue` / `assumeFalse`；
    每个环组恰好打一条 §3.6.6 格式的 ERROR，环组为 0 时不打；摘要里每个数字都来自实际运行，
    不写死任何 jar 数量。

---

## 3.7 报告字段：`unsatisfiedRequired` 与 `duplicateIds`（M1.5）

M1.5 只给 §3 的报告对象补两个**聚合**字段。两者都不是"每条 `ModCandidate` 一条"的字段：每次运行各一个值，
只从记录与 `dependencies.csv` 的行推导。M1.5 不改任何图，也不改任何已有产物。

### 3.7.1 范围边界（不得越界）

M1.5 **不做**：

- 给 `ModCandidate` 或任何逐记录产物（`candidates.jsonl`、`summary.csv`）加字段；
- 改动 `dependencies.csv`、`conflicts.csv`、§3.6 的图或 `cycles.csv`；
- 在重复项之间仲裁、选版本、删记录、推荐保留哪一份（§0）；
- 把"工具无法判定"写成"模组有问题"，反过来也不行。

M1.5 **做**：

- 每次运行计算一次 `report.unsatisfiedRequired`（§3.7.2）与 `report.duplicateIds`（§3.7.3）；
- 写出 `duplicates.csv`（§6.7）并打印三行 stdout（§3.7.4）。

### 3.7.2 `unsatisfiedRequired`：构成

`unsatisfiedRequired` 数的是本次运行**能够证明不满足的硬依赖声明**。它只由 `dependencies.csv` 每一行的
`(depKind, outcome)` 推导，不重新求值任何东西。

| 行 | 计入 | 理由 |
| --- | :---: | --- |
| `required` + `MISSING_ID` | **算** | 目标 ID 在整个集合里不存在，硬依赖缺失 |
| `required` + `VERSION_MISMATCH` | **算** | 目标存在，但现有版本没有满足声明区间 |
| `required` + `AMBIGUOUS_ALIAS` | 不算，单独列出 | 我们拒绝在多个别名拥有者之间替模组选一个。这是**工具的限制，不是模组的缺陷**，绝不能折进"不满足" |
| `breaks` + `BREAKS_VIOLATED` | 不算 | 冲突命中不是依赖不满足；算进去等于同一行用两个名字报两次 |
| `recommends` + `MISSING_ID` | 不算 | §3.5.2：软依赖缺失是正常配置 |
| `recommends` + `VERSION_MISMATCH` | 不算 | 软声明没有准入效力。它会被单独打印，所以不是隐藏 |
| 任何 `UNPARSEABLE` | 不算 | §3.5.7：区间解析失败是本项目的实现缺陷，永远不能算到模组头上 |

定义：

```
unsatisfiedRequired = required/MISSING_ID + required/VERSION_MISMATCH
```

当前数据集上是 `50 + 6 = 56`。两个相邻的数字不得与它混淆：

- **57** = `50 + 7`，把按种类过滤的 `50` 与未过滤的 `7` 相加。第 7 条 `VERSION_MISMATCH` 是
  `recommends sound_physics_remastered -> voicechat`，既是软依赖，又是 §3.5.4 已知的连字符复合版本误报，
  既不 required、也不是真失配。上面的定义按 `depKind` 过滤，所以它进不来。
- **7 + 18 + 7**（全部"坏"结果）也不是本字段：它把冲突、工具限制与实现缺陷混在一起。

未计入的类别永远和数字一起打印、各自带计数，这样"没算"就不会被读成"没测"（§3.7.4）。

### 3.7.3 `duplicateIds`：定义

**重复组** = 单个宇宙内同一 `modId` 有**两条及以上 `status=OK` 记录**。

- **只在宇宙内判定。** 同一 `modId` 同时出现在 `FABRIC` 与 `NEOFORGE` **不算重复**：§3.6.2 已把它们当作两个
  永不同时存在的节点。当前数据集有 71 个这样的双变体 `modId`，全部排除；测试会断言它们一个都不出现在
  `duplicates.csv` 里。
- **宇宙按 §2.1 判定，不看文件名。** 数据集里有 10 个 jar **同时**带 `fabric.mod.json` 与
  `META-INF/neoforge.mods.toml`，按 §2.1 一律判 `FABRIC`。所以一个文件名带 `neoforge` 的 jar 可以合法地落进
  `FABRIC` 的重复组，这在现行规则下是正确结果。这是数据集里混合 jar 的已知属性，不是仲裁，也不改变 §2.1。
- **版本关系。** 每个组二选一：
  - `SAME_VERSION`：组内所有记录的 `version` 原串完全相同；
  - `DIFFERENT_VERSION`：至少两个不同的原串。
  比较是**原串精确相等**，不是 §3.5.4 的比较规则：§3.5.4 为了区间匹配忽略 `+` 之后的构建元数据，但
  `3.0.0+1.21` 与 `3.0.0+1.21.neoforge` 是两个不同的构建，必须按"版本不同"报告。
- **对数。** 一个组有 `n` 条记录就有 `n * (n - 1) / 2` 对；本次运行的总对数是各组之和。组数、对数、
  版本相同的对数、版本不同的对数分别计数。
- **只报告。** 不仲裁、不选版本、不删除、不推荐保留哪一份（§0）。
- **图不动。** M1 的图本来就把这些记录合并成一个节点（§3.6.3）。`duplicateIds` 不增加节点、不增加边：
  本次运行仍然是 187 节点、88 边、环组 0，`FABRIC` 98 / `FORGE` 0 / `NEOFORGE` 89 原样不变。

### 3.7.4 输出

`duplicates.csv`（§6.7）每次运行都写，含表头；没有重复组时只有表头。行先按宇宙名、再按 `modId` 排序，
都是 ASCII 升序，因此同一份输入的两次运行逐字节相同。

三行 stdout 位于 §3.6.6 的三行之后、固定结语之前：

```
[INFO]  不满足的硬依赖：56（硬 MISSING_ID 50 + 硬 VERSION_MISMATCH 6）
[INFO]  未计入 unsatisfiedRequired：AMBIGUOUS_ALIAS 18（工具无法判定，非模组缺陷）| BREAKS_VIOLATED 7（冲突命中，非依赖不满足）| 软 MISSING_ID 22 | 软 VERSION_MISMATCH 1 | UNPARSEABLE 0（实现缺陷）
[INFO]  重复 modId：4 组 / 4 对 | 版本相同 2 对 | 版本不同 2 对
```

数字随数据集变化；标签、未计入项的顺序与括号里的理由不得变化。

### 3.7.5 M1.5 验收标准

以下七条只约束 **M1.5**。它们独立于 §7、§3.5.7 与 §3.6.7；四组不得混用。

1. **聚合字段而非逐记录字段**：不给 `ModCandidate` 加字段，不给任何已有产物加列；每个值每次运行只算一次。
2. **构成**：真实数据集上的运行值等于 §3.7.2 的定义，且每个未计入类别都由构造行覆盖（仅有别名、breaks、
   软缺失、软失配、不可解析）。
3. **重复项**：构造数据覆盖版本相同组与版本不同组；跨宇宙的一对（`FABRIC` X + `NEOFORGE` X）**不得**被报告；
   真实数据集上测试断言每个被报告的组都在单个宇宙内，且没有任何双变体 `modId` 出现。
4. **不仲裁**：`duplicates.csv` 没有推荐列、没有"保留"列、没有被选中的版本。
5. **图未变**：187 节点 / 88 边 / 环组 0，以及 `FABRIC` 98 / `FORGE` 0 / `NEOFORGE` 89，不因 M1.5 改变。
6. **四个产物未变**：`candidates.jsonl`、`summary.csv`、`conflicts.csv`、`dependencies.csv` 的 SHA-256 与
   `PROGRESS.md` 记录的基线完全相同。
7. **可复现**：同一份输入连续跑两次产出逐字节相同的 `duplicates.csv`；`gradle clean test` 通过、**跳过 0**、
   不使用 `assumeTrue` / `assumeFalse`。

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

### 6.4 `dependencies.csv`（M0.5）

表头：`modId,version,depId,depKind,declaredRange,resolvedVersion,outcome`；规则见 §3.5。
每条依赖声明一行，不去重。

### 6.5 `cycles.csv`（M1）

表头：`universe,memberCount,members`；规则见 §3.6。
每个环组一行，没有环组时只有表头。

### 6.6 stdout（INFO 级）

> 下面是**格式模板**，不是真实数字，真实数字见 README 的实跑输出。每一个数字都必须来自实际运行，本文件不得写死任何数量。

```
[INFO]  输入 jar：200
[INFO]  解析成功：193 (96.5%)
[INFO]  解析失败：7
[INFO]    NOT_A_ZIP: 2 | NO_METADATA: 3 | MALFORMED_JSON: 1 | IO_ERROR: 1
[INFO]  FABRIC: 98 | NEOFORGE: 95 | UNKNOWN: 0
[INFO]  侵入深度：API 41 | MIXIN 131 | CORE 12 | RENDER 16
[INFO]  支持策略：SUPPORTED 172 | DEGRADED 28 | EXCLUDED 0
[INFO]  冲突预测：HIGH 3 组 | MEDIUM 7 组 | LOW 12 组（静态推断，非运行结论）
[INFO]  依赖总数：硬依赖 N | 软依赖 N | 冲突声明 N
[INFO]  不可满足：MISSING_ID N | VERSION_MISMATCH N | BREAKS_VIOLATED N | AMBIGUOUS_ALIAS N
[INFO]  无法解析的版本区间：N（实现缺陷，不是模组问题）
[INFO]  依赖图：FABRIC 节点 N，边 N | FORGE 节点 N，边 N | NEOFORGE 节点 N，边 N | 未归入宇宙的 OK 记录 N
[INFO]  未进图的硬依赖：环境提供 N | 跨加载器宇宙 N | 仅别名 N | 目标不存在 N
[INFO]  依赖环：N 组
[INFO]  不满足的硬依赖：N（硬 MISSING_ID N + 硬 VERSION_MISMATCH N）
[INFO]  未计入 unsatisfiedRequired：AMBIGUOUS_ALIAS N（工具无法判定，非模组缺陷）| BREAKS_VIOLATED N（冲突命中，非依赖不满足）| 软 MISSING_ID N | 软 VERSION_MISMATCH N | UNPARSEABLE N（实现缺陷）
[INFO]  重复 modId：N 组 / N 对 | 版本相同 N 对 | 版本不同 N 对
```

最后必须输出一行固定提示：

```
[INFO]  本结果为准入检查，不构成运行稳定性保证
```

**验收不变量（必须显式断言）**：`成功数 + 失败数 == 输入数`。
此断言失败即视为构建失败。

### 6.7 `duplicates.csv`（M1.5）

表头：`universe,modId,recordCount,pairCount,relation,versions`；规则见 §3.7.3。

```
universe,modId,recordCount,pairCount,relation,versions
FABRIC,continuity,2,1,DIFFERENT_VERSION,3.0.0+1.21;3.0.0+1.21.neoforge
FABRIC,puzzle,2,1,SAME_VERSION,2.3.0
NEOFORGE,create,2,1,SAME_VERSION,6.0.10
NEOFORGE,jei,2,1,DIFFERENT_VERSION,19.64.0.455;19.70.0.467
```

每个重复组一行；`versions` 是去重后的版本原串，按 ASCII 升序、用 `;` 连接；转义规则与 `conflicts.csv` 相同；
没有重复组时只有表头。上面这段就是当前数据集的真实输出。没有"被选中的版本"列、没有推荐列、没有"保留"列，
也不许加（§3.7.3）。

---

## 7. 验收标准（仅 M0）

以下十条只约束 **M0**。M0.5 有自己的验收线，见 §3.5.7；M1 的验收线见 §3.6.7；M1.5 的验收线见 §3.7.5。四组不得混用，通过其中一组永不意味着通过另一组。

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
