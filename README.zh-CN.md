<p align="center">
  <a href="./README.md">English</a> · <b>简体中文</b>
</p>

<div align="center">

<h1 align="center">modgraph</h1>

<p align="center">
  <b>面向 Fabric 与 NeoForge 的统一模组元数据与依赖仲裁层</b>
  <br/>
  <sub>不是兼容层 · 不重建 API · 不在任何一方之上翻译另一方</sub>
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge" alt="license"/></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/></a>
  <a href="https://gradle.org/"><img src="https://img.shields.io/badge/build-gradle-orange?style=for-the-badge&logo=gradle&logoColor=white" alt="build"/></a>
  <a href="./SPEC.zh-CN.md"><img src="https://img.shields.io/badge/status-M0%20completed-brightgreen?style=for-the-badge" alt="M0 completed"/></a>
  <a href="https://www.minecraft.net/"><img src="https://img.shields.io/badge/Minecraft-1.21.1-52a447?style=for-the-badge" alt="Minecraft"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/real%20jars-195-blue?style=flat-square" alt="jars"/>
  <img src="https://img.shields.io/badge/metadata%20accuracy-100%25-brightgreen?style=flat-square" alt="accuracy"/>
  <img src="https://img.shields.io/badge/tests-42%20passing%20%C2%B7%200%20skipped-brightgreen?style=flat-square" alt="tests"/>
  <img src="https://img.shields.io/badge/HIGH%20conflicts-331%20%C2%B7%204.96%25-orange?style=flat-square" alt="conflicts"/>
</p>

> ⚠️ **这个项目的下一阶段需要 JVM 底层能力，而作者不会 Java。**
> 详见 [Seeking collaborator](#seeking-collaborator)。

</div>

---

## 目录

- [这是什么](#这是什么)
- [它不是什么](#它不是什么)
- [为什么又要做一个](#为什么又要做一个)
- [当前状态](#当前状态)
- [快速开始](#快速开始)
- [输出产物](#输出产物)
- [架构](#架构)
- [工程纪律](#工程纪律)
- [数据集合规](#数据集合规)
- [Seeking collaborator](#seeking-collaborator)
- [贡献](#贡献)
- [许可证](#许可证)
- [致谢](#致谢)

---

## 这是什么

Minecraft Java 版有两大互不兼容的模组生态：**Fabric** 与 **NeoForge / Forge**。
它们各有自己的元数据格式（`fabric.mod.json` / `neoforge.mods.toml`）、依赖语义、版本区间写法和加载期行为。

`modgraph` 的目标是把双方**元数据层**统一到一套模型里，然后在这套模型之上做依赖求解与冲突仲裁——
让"这个模组组合能不能一起装、为什么不能"这个问题，第一次有一个**确定、可复现、可验证**的答案。

统一模型的核心类型是 `ModCandidate`（见 [`SPEC.zh-CN.md`](./SPEC.zh-CN.md) §1）。
它把 Fabric 与 NeoForge 的元数据差异抹平成一个中立结构，同时**保留全部原始字段**，不做有损转换。

在此之上，M0 额外产出一项**侵入深度评级**：在不解析字节码的前提下，依据 zip 内的描述符与元数据，识别哪些模组属于渲染管线级、字节码级或纯 API 级改造，并在启动前给出冲突风险预警。

### 定位

`modgraph` 的输出是**「能否启动的准入检查」，不是「能否稳定运行的保证」**。

- 能预测：依赖缺失、版本区间不相交、循环依赖、显式不兼容声明
- 不能预测：两个 Mixin 撞同一方法、两个模组替换同一渲染阶段

行为性冲突只能在运行时暴露。本项目不做"自动兼容"或"自动修复"的承诺。

---

## 它不是什么

这一点比"它是什么"更重要，因为市面上已有项目的定位都不一样：

| 项目 | 做法 | 定位 |
| --- | --- | --- |
| **Sinytra Connector** | 以 NeoForge 为宿主，加载时重映射 Fabric 模组 | 宿主 + 翻译层 |
| **Kilt** | 以 Fabric 为宿主，内嵌 NeoForge API 并用 500+ mixin 复刻 Forge 补丁 | 宿主 + 翻译层 |
| **Patchwork** | 把 Forge API 重写成一组 Fabric 模组 | API 重建，已停更 |
| **Forbric** | 自研加载器，声称不是翻译层 | 启发来源，见下 |
| **modgraph** | 统一元数据与依赖仲裁 | 元数据层统一 |

`modgraph` **当前不是模组加载器**。它不启动 Minecraft，不加载类，不改写字节码。

关于"是不是翻译层"这个问题，我们的立场是坦诚的：
**统一的边界止于元数据与依赖；再往下（事件、注册表、能量/流体/物品互操作、对同一处 MC 代码的补丁冲突）本质上躲不掉桥接。**
区别不在于"有没有翻译"，而在于**桥的边界是否被诚实地划清楚、冲突是否由统一的仲裁器裁决，而不是静默地二选一**。

---

## 为什么又要做一个

受 [Forbric](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader) v0.3.0 启发，但技术判断与之不同。

Forbric 的代码几乎完全由 AI 生成，且缺少充分的人工审查——社区已有公开分析指出这一点。由此产生的工程质量隐患包括但不限于：

- 用 `assumeTrue` 使测试在特定条件下静默跳过，导致测试通过率失真
- 为提升"可运行模组数量"，把本应必选的 API 降级为可选
- 崩溃被捕获后降级为警告继续执行
- Mixin 输出侧缺乏测试覆盖
- 门禁与金丝雀测试未接入 CI

本项目的应对方式不是"写得更好一点"，而是**把上述每一条写成可执行的工程纪律**，见 [工程纪律](#工程纪律)。

---

## 当前状态

> ⚠️ **M0：离线元数据解析器。不能加载任何模组，不能启动 Minecraft。**

| 阶段 | 内容 | 状态 |
| --- | :---: | :---: |
| **M0** | 离线解析模组 jar 元数据，产出统一 `ModCandidate` 与依赖声明 | ✅ 已完成（2026-10-31，195 个真实 jar） |
| M0.5 | 版本区间求值与语义化版本规则对齐 | 未开始 |
| M1 | 跨加载器依赖求解与冲突仲裁器 | 未开始 |
| M2 | 统一引导与 transform 编排 | ⚠️ 需要 JVM 底层能力，作者不会 Java |
| M3 | 单版本上稳定加载 20 + 20 个真实模组 | 未开始 |

**在 M3 之前的任何版本都不应用于实际游戏。**

---

## 快速开始

### 前置要求

- JDK 21
- Python 3.9+（仅用于采集测试数据集）
- Gradle 8.x（**本机安装**，本仓库不含 wrapper）

### 1. 采集真实模组数据集（可选）

本项目**不使用合成数据**。测试输入是从 [Modrinth](https://modrinth.com) 公开 API 下载的真实模组 jar。

采集分三步，脚本都在仓库根目录：

```bash
python build_download_list.py                        # 生成待下载清单（download_list.txt）
python fix_downloads.py                              # 修正下载失败/残缺的文件
python organize_jars.py --search "fixtures/jars"     # 按 jar 内元数据自动归位
python verify_downloads.py                           # 逐个确认能打开且含元数据
```

不准备数据集也能跑测试：缺少 `fixtures/jars` 时，测试会自动回退到 `src/test/resources/fixtures/` 下的 11 个内置样本。

### 2. 构建

```bash
gradle build
```

### 3. 运行解析器

```bash
gradle run --args="fixtures/jars"
```

---

## 输出产物

| 文件 | 说明 |
| --- | --- |
| `candidates.jsonl` | 每行一个 JSON 对象，对应一个输入 jar，顺序与输入一致 |
| `summary.csv` | 表头 `sourceFile,sha256,loaderFamily,status,modId,version,modType,invasiveness,supportPolicy,failureCode` |
| `conflicts.csv` | 表头 `modA,modB,level,sharedTargets`，启动前冲突预测，含全部模组对（含 `NONE`） |
| stdout | INFO 级摘要 |

以下是当前数据集（195 个 jar）的一次**实际运行**输出，数字不是常量：

```
[INFO] FABRIC: OK=100 FAILED=0
[INFO] NEOFORGE: OK=91 FAILED=4
[INFO] 输入 jar：195 | OK：191 | FAILED：4
[INFO] 失败分类：NO_METADATA: 4
[INFO] 侵入深度：API 23 | MIXIN 152 | CORE 7 | RENDER 9
[INFO] 支持策略：SUPPORTED 175 | DEGRADED 0 | EXCLUDED 16
[INFO] 高频目标类：Minecraft 28 | GameRenderer 25 | Entity 19 | LevelRenderer 18 | MinecraftClient 18 | ClientPacketListener 17 | Gui 16 | LivingEntity 16 | Screen 15 | ClientLevel 13
[INFO] ubiquitous 阈值：12（模组总数 116 × 10%，向上取整）
[INFO] 冲突预测：HIGH 331 组 | MEDIUM 1170 组 | LOW 725 组（静态推断，非运行结论）
[INFO] 本结果为准入检查，不构成运行稳定性保证
```

> M0 不做版本区间求值与依赖求解，因此没有「硬依赖总数」「不可满足」「环」这些数字——它们属于 M0.5 / M1。

### 核心不变量

```
OK 数 + FAILED 数 == 输入文件数
```

**恒等**。此断言失败即构建失败。
任何情况下都不会出现「输入 N、输出少于 N」而无解释的情况——失败必须被计数、被分类、被呈现。

---

## 架构

处理链路只有四步，刻意保持简单：

```
JarScanner           遍历目录，计算 SHA-256，每个输入恰好产出一条记录
   │
   ├── FabricMetadataParser     读 fabric.mod.json / quilt.mod.json
   │
   └── NeoForgeMetadataParser   读 neoforge.mods.toml / mods.toml
   │
   ▼
InvasivenessAnalyzer 侵入深度评级 + Mixin 目标推断（仅描述符级，不解析字节码）
   │
   ▼
ConflictPredictor    按 modId 合并加载器变体，两两比对目标，分 HIGH/MEDIUM/LOW/NONE
   │
   ▼
ReportWriter         candidates.jsonl / summary.csv / conflicts.csv / INFO 摘要
```

统一数据契约是 `ModCandidate`，见 [`SPEC.zh-CN.md`](./SPEC.zh-CN.md) §1。

**依赖**：Gson（JSON）、night-config（TOML）、SLF4J + Log4j2（日志）、JUnit 5 + AssertJ（测试）。

**不依赖**：Minecraft、Fabric Loader、Forge、NeoForge、Mixin、ASM。
本阶段是纯离线文本解析，任何上述依赖的引入都视为违规。

### 侵入深度评级

Fabric 模组期待「原版 MC + Mixin 注入」，NeoForge 模组期待「MC 已被 Forge 打过补丁」——两者基线假设不同。元数据层无法预测运行时冲突，但可以在启动前识别深度侵入的模组。

| 档位 | 含义 |
| --- | --- |
| `RENDER` | 触碰渲染管线，冲突风险最高 |
| `CORE` | 直接操作字节码 |
| `MIXIN` | 运行时注入行为 |
| `API` | 仅使用 API 契约 |

扫描仅限**描述符级**：读取 zip 内文件名与元数据文本，不解析 class 内容。class 常量池级扫描属于后续阶段。

`RENDER` 与 `CORE` 两档、以及外挂类模组与 OptiFine，一律标记为 `EXCLUDED`，**不做任何兼容性承诺**——这是产品决策，不是技术失败：M0 只能预测声明性冲突，深度侵入模组的行为性冲突超出静态观测能力。

它们**不会被从统计中剔除**，全部保留在 `summary.csv` 里——被排除的模组正是最重要的输出。

---

## 工程纪律

以下每一条都是**构建时强制**的，不是风格建议。
它们直接对应本项目要纠正的上游问题。

1. **不得空 catch。** 捕获异常必须完成三件事：填充 `failureCode` 与 `failureMessage`、以 `ERROR` 记录、标记该条为 `FAILED`。
2. **不得把失败降级为警告。** 单个 jar 失败不影响其余 jar 是允许的，但失败必须被计数、分类、呈现。
3. **不得用 `assumeTrue` / `assumeFalse`。** 条件不满足时测试必须 FAIL，不能 SKIP。测试跳过数恒为 0；缺少完整数据集时回退到内置样本，连内置样本都缺失才失败。
4. **不得 mock 掉真实文件读取。** 七种失败分类（`NO_METADATA` / `MALFORMED_JSON` / `MALFORMED_TOML` / `MISSING_REQUIRED_FIELD` / `IO_ERROR` / `NOT_A_ZIP` / `INTERNAL_ERROR`）每一种都由真实构造的损坏 jar 触发。
5. **不得用 `System.out.println` / `printStackTrace`。** 全部走 SLF4J 参数化日志，构建时静态检查强制。
6. **不得跳过任何输入文件。**
7. **不得提前实现 SPEC 未定义的功能，不得预留"扩展性"接口。**

日志等级规范见 [`SPEC.zh-CN.md`](./SPEC.zh-CN.md) §5。

---

## 数据集合规

测试数据集通过 Modrinth 公开 API 下载，仅用于**离线测试与兼容性研究**：

- 本仓库**不包含任何真实模组 jar**，采集产物不入库（仅 `src/test/resources/fixtures/` 下有 11 个自制测试样本）
- 下载内容逐个确认能打开且含元数据，损坏文件自动剔除
- 模组的版权与许可归各自作者所有，本项目不对任何模组内容主张权利
- Minecraft 本体为 Mojang 专有财产，本项目不下载、不修改、不分发任何 Minecraft 文件

---

## Seeking collaborator

> ⚠️ **这个项目的下一阶段需要 JVM 底层能力，而作者不会 Java。**

这不是"求人帮忙写代码"，是找一个**能做技术判断的合作者**——规格、数据、验证体系已经就位。

**已经做到哪一步**

- M0 完成：195 个真实 jar 全量解析，记录数恒等于输入数（`OK 191 + FAILED 4 = 195`）
- 元数据人工抽检：`modId` / `version` / `depends` 三项 **10/10 一致**，47 条依赖声明 0 条不一致
- [`SPEC.zh-CN.md`](./SPEC.zh-CN.md) v0.7 十条验收全部达成
- 42 个测试，失败 0、错误 0、**跳过 0**
- 启动前冲突预警：HIGH 331 组（4.96%）

**我能提供什么**

- 完整规格：[`SPEC.zh-CN.md`](./SPEC.zh-CN.md) v0.7——字段、判定信号、分级规则、输出格式、验收标准都写死了，不靠口头约定
- 真实数据集采集流程：从 Modrinth 公开 API 生成清单、手动下载、按 jar 内元数据自动归位、逐个校验，全程脚本化
- 一套强制的工程纪律：禁止空 catch、禁止 `assumeTrue`、禁止把失败降级为警告、测试跳过数必须为 0
- 全部决策记录：[`PROGRESS.md`](./PROGRESS.md)，92 条工作记录 + 54 条踩过的坑，每一次数字变化与判断失误都在册

**我需要什么**

M2（统一引导与 transform 编排）需要 JVM 底层能力：类加载机制、字节码结构、Mixin / ASM 的工作原理、从崩溃栈反推注入点。**这部分我补不上**，也不打算用 AI 生成一堆"看起来能跑"的代码糊弄过去。

缺的是一个能回答"Mixin 注入冲突到底怎么判定""class 文件里哪个常量池条目能证明目标类"这类问题的人，而不是一个执行工单的人。

如果你愿意参与，请先读 [`CONTRIBUTING.md`](./CONTRIBUTING.md) 和 [`SPEC.zh-CN.md`](./SPEC.zh-CN.md)，然后开一个 issue 说明你想负责的部分与判断依据。

---

## 贡献

欢迎贡献，但请先读 [工程纪律](#工程纪律) 与 [贡献指南](./CONTRIBUTING.md)——本项目对"能跑就行"的容忍度是零。

提交 PR 前请确认：

- [ ] `gradle build` 通过
- [ ] 测试跳过数为 **0**
- [ ] 新增功能已在 `SPEC.md` 中定义
- [ ] 未引入 Minecraft / 加载器相关依赖
- [ ] 未使用 `assumeTrue` / `assumeFalse`
- [ ] 无空 catch 块，无 `System.out.println`
- [ ] `OK + FAILED == 输入数` 依然成立

---

## 许可证

本项目采用 [Apache License 2.0](./LICENSE)。

请注意上下游项目的许可差异：Fabric Loader 为 Apache-2.0，Forge / NeoForge 为 LGPL-2.1，Sinytra Connector 为 LGPL-3.0，Kilt 为 LGPL-2.1。本项目的 M0 阶段不依赖其中任何一项。

---

## 致谢

- [**Forbric**](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader) — 验证了"统一加载器"这一需求的真实性，也用自身的教训界定了本项目的工程纪律。Apache-2.0。
- [**Sinytra Connector**](https://github.com/Sinytra/Connector) 与 [**Kilt**](https://github.com/KiltMC/Kilt) — 提供了翻译层路线的完整经验与已知边界。
- [**Modrinth**](https://modrinth.com) — 开放 API 使真实数据集采集成为可能。
- 所有被本数据集收录的模组作者。
