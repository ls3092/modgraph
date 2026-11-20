# M2 第一刀 · 子任务 C：构建与回滚方案（只读预检）

> 本轮为只读预检。唯一落盘文件即本文档；未写代码、未编译、未改任何 json、未联网、未启动任何 java/PCL 实例。所有路径、字段、数值均来自实际只读核验，未编造。

---

## ① Gradle / src 现状

**构建文件（实际为 Kotlin DSL，不是 Groovy `build.gradle`）**

| 项 | 实读结果 |
|---|---|
| 构建脚本 | `D:\modgraph\modgraph\build.gradle.kts`（仓库内**不存在** `build.gradle`） |
| 设置脚本 | `D:\modgraph\modgraph\settings.gradle.kts`，唯一内容：`rootProject.name = "modgraph"` |
| 插件 | `plugins { java; application }`（`build.gradle.kts:1-4`） |
| 主类配置 | `application { mainClass = "dev.modgraph.Main" }`（`build.gradle.kts:12-14`） |
| Java 版本 | `java.toolchain.languageVersion = JavaLanguageVersion.of(21)`（`build.gradle.kts:6-10`）→ **Java 21** |
| 仓库 | `mavenCentral()`（`build.gradle.kts:16-18`） |
| 依赖 | slf4j-api 2.0.16；log4j-slf4j2-impl / log4j-core 2.24.1（runtimeOnly）；gson 2.11.0；night-config toml 3.6.7；测试 JUnit 5.11.3 / assertj 3.26.3 / archunit 1.5.1（`build.gradle.kts:20-34`） |
| 测试 | `tasks.test { useJUnitPlatform() }`（`build.gradle.kts:36-38`） |
| Gradle wrapper | **无** `gradlew` / `gradlew.bat`，`gradle/` 目录无 wrapper properties（空目录） |

**src 现状（实读 `find` 统计）**

- Java 源码**存在**。
- `src/main/java/…`：**46** 个 `.java`，包根 `dev.modgraph`，子包含 `analysis / graph / model / parse / report / resolve / scan / version`，入口 `dev/modgraph/Main.java`。
- `src/test/java/…`：**26** 个 `.java`。
- 资源：`src/main/resources/log4j2.xml`；`src/test/resources/fixtures/` 下 **11** 个测试用 jar。
- 源码根路径：Gradle `java` 插件默认约定 —— 主源码 `D:\modgraph\modgraph\src\main\java`，测试源码 `src\test\java`，主资源 `src\main\resources`。build.gradle.kts 未自定义 sourceSets。
- **特别注意**：存在空目录树 `D:\modgraph\modgraph\src\m2\java\dev\modgraph\m2\`（仅目录，**0 个 .java 文件**）。这是预留目录，当前无源码；任何 M2 入口类的包名/位置都不能从该空目录推断。

**Java 版本口径澄清（重要，与主链表述对齐）**
任务描述提到"AGENTS.md 里 java 21"。实读 `D:\modgraph\modgraph\AGENTS.md`：其中**没有**任何 Java 版本号行（唯一相关行是第 7 行"维护者不写 Java，只核对数字与结论"）。Java 21 的真实来源是 `build.gradle.kts:8`。本轮如实记录这一出处差异，不把 21 归到 AGENTS.md。

---

## ② `gradle jar` 可行性 与 jar 输出路径预期

**可行性（只读判断，未执行编译）**

- `gradle` 在 PATH：`/c/Gradle/gradle-8.10/bin/gradle` → Gradle **8.10** 可用。
- `java` 在 PATH：`/c/Program Files/Microsoft/jdk-21.0.8.9-hotspot/bin/java` → Microsoft JDK **21.0.8.9**，满足 toolchain 的 Java 21 约束。
- `plugins { java }` 提供标准 `jar` task；脚本没有禁用或重命名该 task。
- 无 wrapper，因此命令用系统 `gradle jar`（在 `D:\modgraph\modgraph` 下执行），而非 `./gradlew jar`。
- 结论：**`gradle jar` 可行**（构建侧 JDK 21 齐备）。

**输出路径预期**

- 预期：`D:\modgraph\modgraph\build\libs\modgraph.jar`
- 依据：`rootProject.name = "modgraph"` 决定 archiveBaseName=`modgraph`；脚本未设置 `version`、未配置 `archiveFileName`/`jar.archiveVersion`，故无版本后缀。
- 实证：该产物**已存在**于磁盘 —— `D:\modgraph\modgraph\build\libs\modgraph.jar`，大小 **126003** 字节，时间戳 **Nov 1 18:52**（与推断名一致，作为旁证）。

**与 library 方案相关的两个事实（如实记录，不猜规则）**

1. 现有 `build/tmp/jar/MANIFEST.MF` 内容仅 `Manifest-Version: 1.0` —— **没有 Main-Class 属性**。即 `gradle jar` 产出的是普通 thin jar；作为被反射加载的库，Manifest 无 Main-Class 并不直接妨碍，但入口不来自 Manifest。
2. jar 仅 126003 字节，**未打包** slf4j / gson / night-config / log4j 等运行依赖（它们在 build.gradle.kts 中是普通 `implementation`/`runtimeOnly`）。这些类在 Fabric 启动器 classpath 中是否可用、是否会触发 `NoClassDefFoundError`，本轮无法由配置确定 → 列为开放点 U2，交主链实跑记录。

---

## ③ library 模板原文（与子任务 A 同源只读取样）

**样本文档（磁盘实际路径）**
`D:\Minecraft\.minecraft\versions\fabric-loader-0.20.1-26.2\fabric-loader-0.20.1-26.2.json`
（显示名 `fabric-loader-0.20.1-26.2`；文件为单行 minified JSON，2841 字节；`mainClass = net.fabricmc.loader.impl.launch.knot.KnotClient`，`inheritsFrom = 26.2`）

**逐字摘出的第一条 library 完整原文**（未经改写、字段顺序原样）：

```json
{"name":"org.ow2.asm:asm:9.10.1","url":"https://maven.fabricmc.net/","md5":"9cb72080438acf27f2607002e1444a7b","sha1":"ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236","sha256":"ed825d10ab1399c8c0cb669e688cf0c8c82629b4c8399b58352b68e92ca10fcb","sha512":"8db6efa37d4d569bf2b95d90931b8064f87ed928d20bbf8d8e7fa6182e9a11d32e6a2cc034a3d173a38ee70ce2ea9f20f52f9d8730d9401d2bc6060674c18928","size":126151}
```

**字段模板（字段名与顺序严格照抄，不自造）**
`name` → `url` → `md5` → `sha1` → `sha256` → `sha512` → `size`（共 7 个字段）

**同源一致性提示（供主链做 A/C 断言）**
该 profile 共有 **7** 条 library：
- 6 条为上述完整 7 字段（含 `org.ow2.asm:asm:9.10.1` 等）；
- 第 7 条 `net.fabricmc:fabric-loader:0.20.1` 只有 `name`、`url` 两个字段（无 md5/sha1/sha256/sha512/size）。

本子任务**明确选用第一条（完整 7 字段）作为字段模板**，因为方案要求 sha1/size 等由主链编译后填真实值。A 子任务若选 `fabric-loader` 那条 2 字段样本，则 A/C 字段集合会不同 —— 主链断言时应先统一取样条目。样本文档路径与取值两者完全相同，可据此核对同源。

---

## ④ 新 jar 坐标与 library 字段方案（仅依据上述模板）

**建议 Maven 坐标**
- group：`dev.modgraph`
- artifact：`modgraph`
- version：`<M2_VERSION>`（占位；由主链确定，例如 `0.1.0-m2`，本轮不编造版本号）
- `name` 字段值：`dev.modgraph:modgraph:<M2_VERSION>`（严格 `group:artifact:version`，与模板 name 格式一致）

**本地 jar 落点路径（按已观测到的 libraries 坐标目录布局）**
- 仓库内构建产物：`D:\modgraph\modgraph\build\libs\modgraph.jar`
- 放入 `.minecraft/libraries` 后的相对路径：`dev/modgraph/modgraph/<M2_VERSION>/modgraph-<M2_VERSION>.jar`
- 绝对路径：`D:\Minecraft\.minecraft\libraries\dev\modgraph\modgraph\<M2_VERSION>\modgraph-<M2_VERSION>.jar`
- 布局依据（只读实测）：`D:\Minecraft\.minecraft\libraries\org\ow2\asm\asm\9.10.1\asm-9.10.1.jar`、`…\net\fabricmc\fabric-loader\0.20.1\fabric-loader-0.20.1.jar` —— 即 group 的 `.` 转 `/` + artifact + version + `<artifact>-<version>.jar`。
- 文件名 `<artifact>-<version>.jar` 为占位写法，由主链按实际 <M2_VERSION> 落地；本轮不创建目录、不复制文件。

**library 条目应填字段（字段名/顺序严格照模板；值用占位说明）**

| 顺序 | 字段 | 填写值（占位） |
|---|---|---|
| 1 | `name` | `dev.modgraph:modgraph:<M2_VERSION>` |
| 2 | `url` | `<待主链决定 — 见下方 U1；不可由本模板推出>` |
| 3 | `md5` | `<主链编译后计算真实值>` |
| 4 | `sha1` | `<主链编译后计算真实值>` |
| 5 | `sha256` | `<主链编译后计算真实值>` |
| 6 | `sha512` | `<主链编译后计算真实值>` |
| 7 | `size` | `<主链编译后填入真实字节数（十进制整数）>` |

**`url` 字段在模板里的写法，以及指向本地 jar 时的处理（不猜启动器 classpath 规则）**
- 模板写法：全部 7 条 library 的 `url` 都是仓库根地址 `https://maven.fabricmc.net/`，**以 `/` 结尾**，是 Maven 仓库 base URL，**不是**某个 jar 的直接下载 URL。样例中没有任何一条 `url` 指向本地文件或 `file://` 路径。
- 指向本地 jar 时应如何处理：**本模板无法推出**。能确定的只有两点事实：(a) 物理 jar 按坐标放到 `.minecraft/libraries` 对应目录；(b) 模板的 `url` 形态是 http(s) 仓库根 + 尾斜杠。至于"本地 jar 场景下 `url` 该填什么""启动器是凭坐标直接采用已存在的本地文件，还是在校验失败时按 `url` 重新下载""能否写 `file://` 或留空"——这些属于启动器 classpath/解析规则，**样本文档没有给出任何证据，本轮不猜**。
- 开放点 **U1（请主链如实记录，不要编造）**：`url` 取值与本地 jar 的解析行为未经证实，需主链实跑后记录真实表现（是否必须从 `url` 下载、是否接受本地文件、hash/size 不匹配时的行为）。
- 开放点 **U2**：见 ② 第 2 点 —— thin jar 的运行依赖是否在 Fabric classpath 中可用，未经证实。

---

## ⑤ 备份 / rollback 步骤

**对象文件（下称 `<id>.json`）**
`D:\Minecraft\.minecraft\versions\fabric-loader-0.20.1-26.2\fabric-loader-0.20.1-26.2.json`
备份文件（下称 `.bak-pre-m2`）：同目录下 `fabric-loader-0.20.1-26.2.json.bak-pre-m2`

**当前状态（只读核实）**
该实例目录下**目前不存在**任何 `.bak*` 文件（已 `ls` 确认）。同目录另有一个 0 字节的 `.abnormal` 标记文件，存在但本轮不解释、不处理。

**步骤（方案描述，本轮均未执行）**

1. **备份原文件**：在任何改动之前，把 `<id>.json` 原样复制为 `<id>.json.bak-pre-m2`（同一目录），确保两者字节完全一致。`.bak-pre-m2` 一经建立即视为官方 Fabric 的黄金副本，后续不再改动它。
2. **修改 `<id>.json`**：
   - 把 `mainClass` 从 `net.fabricmc.loader.impl.launch.knot.KnotClient` 改为 M2 入口类（类名/包名由主链定，本轮不编造）；
   - 在 `libraries` 数组**追加** ④ 中的 library 条目；
   - 保留 `id` / `inheritsFrom = "26.2"` / `arguments` 其余内容不变；不改动父版本 `26.2.json`。
3. **出问题回滚**：把 `<id>.json.bak-pre-m2` 覆盖回 `<id>.json`（即恢复官方 Fabric 的 `mainClass = net.fabricmc.loader.impl.launch.knot.KnotClient` 与原始 `libraries` 数组），随后在 PCL 中重启该实例/重启启动器使其重新读取 json。结果即退回官方 Fabric。
4. **边界**：只允许改这一份 `<id>.json`；不动 `versions` 下任何其他实例或文件；回滚用备份覆盖是当前方案里唯一的"写"，且仅发生在主链实际执行阶段。

一句话 rollback：**把 `.bak-pre-m2` 覆盖回 `<id>.json`，重启实例，即回到官方 Fabric。**

---

## ⑥ 反射转调 `net.fabricmc.loader.impl.launch.knot.KnotClient.main` 的风险清单（只列风险，本轮不解决）

1. **Knot 类的类加载环境 / 初始化假设**：当前 `mainClass` 直接就是 `KnotClient`，意味着 Fabric 启动器按其标准链路初始化（含 `arguments.jvm` 中的 `-XX:+UnlockDiagnosticVMOptions`、`-XX:-UseBASE64Intrinsics`、`-DFabricMcEmu= net.minecraft.client.main.Main `）。若改从另一入口类用反射调用 `KnotClient.main`，Knot 的静态初始化、自定义 ClassLoader、launch wrapper 是否已就位/是否可重入均未知；反射调用假设了这些环境已正确建立，未经验证。
2. **Java 版本差（26.2 需 Java 25 vs 构建侧 Java 21）**：父版本 `D:\Minecraft\.minecraft\versions\26.2\26.2.json` 的 `javaVersion = {"component":"java-runtime-epsilon","majorVersion":25}`；安装日志原文记录 `26.2 requires Java 25 ... No Java 25 found on this machine. NOT installing a JRE`。而构建 toolchain 与 PATH 上的 JDK 都是 **21**。风险：运行时可能根本无法在 Java 21 下启动 26.2，反射入口无法绕过这一硬性版本门槛。（并见 ① 的出处澄清：Java 21 来自 build.gradle.kts，不在 AGENTS.md。）
3. **`main` 方法签名与 `Class.forName` 字符串反射的脆弱性**：反射依赖精确的二进制类名字符串与精确的 `main(String[])` 签名。类名拼写错误、包重定位、签名不符都会在运行时抛 `ClassNotFoundException` / `NoSuchMethodException`，且编译期无法发现；字符串硬编码使重构/版本升级易断。
4. **thin jar 运行依赖缺失（U2）**：`modgraph.jar` 未打包 slf4j/gson/night-config/log4j；若 M2 入口代码在启动器 classpath 中触达这些类而它们不在场，会 `NoClassDefFoundError`。
5. **无 Main-Class 的 Manifest**：jar 的 Manifest 只有 `Manifest-Version: 1.0`，入口不来自 Manifest；反射方案需另找入口类，而 `src/m2/...` 当前为空目录（0 个 .java），入口类名本轮无证据可定。
6. **`url` / 本地 jar 解析不确定（U1）**：见 ④；若启动器不接受本地坐标或 hash/size 不符时从 `url` 重下，方案可能失效，须主链实跑确认。

合计 **6 条**风险（含任务明确要求的 3 条 + 3 条实测补充）。

---

## ⑦ 违反约束情况

**无。** 本轮仅做只读核验（Bash 只读列举/统计、Read、Grep、只读 Python 解析与打印），未写代码、未编译、未修改任何 json 或仓库文件、未联网下载 MC 文件、未使用 Computer Use、未写第二块功能、未用命令行 java 冒充 PCL 点启动、未改动 `versions` 下任何实例；唯一写入是本文档 `m2-build-C.md`。

---

## 简短回复主智能体

- **jar 输出路径预期**：`D:\modgraph\modgraph\build\libs\modgraph.jar`（已存在同名产物 126003 字节；Gradle 8.10 + JDK 21，`gradle jar` 可行；无 wrapper）。
- **模板字段集合**：`name, url, md5, sha1, sha256, sha512, size`（7 字段，取自 `org.ow2.asm:asm:9.10.1` 那条；注意 profile 第 7 条 `fabric-loader` 仅 `name,url`，A/C 取样需统一）。
- **rollback 一句话**：把 `fabric-loader-0.20.1-26.2.json.bak-pre-m2` 覆盖回 `fabric-loader-0.20.1-26.2.json`，重启实例，即退回官方 Fabric。
- **风险条数**：6 条（含要求的 Knot 初始化假设、Java 25 vs 21、反射字符串脆弱性，另加 thin jar 依赖、无 Main-Class/空 m2 入口、url 解析不确定）。
- **待主链处理的开放点**：U1（`url` 与本地 jar 解析行为，勿猜）、U2（运行依赖是否在 Fabric classpath 可用），以及 ① 中 Java 21 出处澄清（来自 build.gradle.kts 而非 AGENTS.md）。
