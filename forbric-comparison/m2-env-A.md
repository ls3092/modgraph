# M2 第一刀 · 只读预检子任务 A（环境与模板）

> 本节为只读核验报告。任何统计均标注实测口径（写了什么、分母是什么、哪些没验证）。
> 共享约束统一引用仓库 `AGENTS.md`，不在本文件重复转述。

---

## ① 实例实际文件夹名与 id

| 项 | 实测值 | 来源 |
| --- | --- | --- |
| 实际文件夹名 | `fabric-loader-0.20.1-26.2` | `Get-ChildItem 'D:\Minecraft\.minecraft\versions' -Directory` |
| json 文件名 | `fabric-loader-0.20.1-26.2.json` | 该目录内文件清单 |
| json 内 `id` | `fabric-loader-0.20.1-26.2` | 原文 `"id":"fabric-loader-0.20.1-26.2"` |
| 父实例文件夹名 | `26.2` | 同上目录清单 |
| 父 json 文件名 | `26.2.json` | `versions\26.2\` 内文件清单 |

- 目标文件夹名与给定显示名**完全一致**，未重命名。
- 同目录内 `fabric-loader-0.20.1-26.2.jar` 实测长度为 **0 字节**（占位文件）；父 `26.2.jar` 实测长度为 **39193383 字节**。仅记录，未做任何处理。
- 口径：文件夹清单一共返回 **13 个目录（分母=13）**，全部见第⑧节。

## ② library 模板原文（逐字）

取自 `D:\Minecraft\.minecraft\versions\fabric-loader-0.20.1-26.2\fabric-loader-0.20.1-26.2.json` 的 `libraries` 数组**第 1 条**（字段顺序、拼写、大小写均按原文，未改字段、未补字段）：

```json
{"name":"org.ow2.asm:asm:9.10.1","url":"https://maven.fabricmc.net/","md5":"9cb72080438acf27f2607002e1444a7b","sha1":"ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236","sha256":"ed825d10ab1399c8c0cb669e688cf0c8c82629b4c8399b58352b68e92ca10fcb","sha512":"8db6efa37d4d569bf2b95d90931b8064f87ed928d20bbf8d8e7fa6182e9a11d32e6a2cc034a3d173a38ee70ce2ea9f20f52f9d8730d9401d2bc6060674c18928","size":126151}
```

- 模板字段集合（7 个，按出现顺序）：`name`, `url`, `md5`, `sha1`, `sha256`, `sha512`, `size`。
- 口径：只抽取 `libraries` 的第 1 条作为模板，**未统计该数组总条目数**，**未推断启动器 classpath 规则**。
- 原文该文件为单行压缩 JSON，无缩进；上述代码块即原始字节片段，不存在可保留的缩进场。

## ③ inheritsFrom 原文行

```
"inheritsFrom":"26.2"
```

## ④ mainClass 原文行（子实例，即 fabric-loader json）

```
"mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient"
```

- 父 json `26.2.json` 中另有（仅作对照，非本节要求的“当前 mainClass”）：
  `mainClass": "net.minecraft.client.main.Main`（父 json 字段值前后带空格，为原文格式）。

## ⑤ JDK 列表与 Java 版本核查结论

实测发现的 java.exe 与版本字符串（分母：在 7 个限定根目录下递归搜到 **2 个** java.exe；未做全盘扫描，见“未验证”）：

| # | 路径 | 版本字符串原文（首行） |
| --- | --- | --- |
| 1 | `C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin\java.exe` | `openjdk version "21.0.8" 2025-07-15 LTS` |
| 2 | `C:\Users\Administrator\AppData\Roaming\.minecraft\runtime\java-runtime-delta\bin\java.exe` | `openjdk version "21.0.7" 2025-04-15 LTS` |

- PATH 中的 java：`C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin\java.exe`；`JAVA_HOME` 环境变量为空（实测）。
- 26.2 所需 Java 版本，取自父 json `D:\Minecraft\.minecraft\versions\26.2\26.2.json` 的 `javaVersion` 原文：

```json
javaVersion": {"component": "java-runtime-epsilon", "majorVersion": 25}
```

即 **majorVersion = 25、component = java-runtime-epsilon**。

**核查结论（不自洽，停下贴原文）：**
26.2 要求 Java 25，而本机定向搜索仅发现 2 个 java.exe、均为 Java 21（21.0.8 / 21.0.7），**未发现 Java 25 / java-runtime-epsilon**；PCL 的 `LaunchArgumentJavaUser`（见第⑦节）配置的也正是这 2 个 Java 21 路径。需求量与本机实有量不一致。按共享约束，不自行挑一个“看起来对”的 JRE，不安装 JRE，停在此处并如实报告。

- 未验证：未全盘扫描 `C:\`；仅在 `C:\Program Files\Java`、`C:\Program Files\Eclipse Adoptium`、`C:\Program Files\Amazon Corretto`、`C:\Program Files\Zulu`、`C:\Program Files\Microsoft`、`C:\Users\Administrator\AppData\Roaming\.minecraft\runtime`、`D:\Minecraft\PCL` 这 7 个限定根目录内递归查找 java.exe。未检查注册表/环境变量中未落到上述目录的 JDK。
- 保护说明：读取版本用 `$ErrorActionPreference='Continue'` + `try/catch` 捕获 stderr，未因 `java -version` 写 stderr 而中断。

## ⑥ gradle --version 原文与是否 8.10

可执行文件实测位置：`C:\Gradle\gradle-8.10\bin\gradle.bat`（`Get-Command gradle` 命中，位于 PATH）。

`& 'C:\Gradle\gradle-8.10\bin\gradle.bat' --version` 输出原文：

```
------------------------------------------------------------
Gradle 8.10
------------------------------------------------------------

Build time:    2024-08-14 11:07:45 UTC
Revision:      fef2edbed8af1022cefaf44d4c0514c5f89d7b78

Kotlin:        1.9.24
Groovy:        3.0.22
Ant:           Apache Ant(TM) version 1.10.14 compiled on August 16 2023
Launcher JVM:  21.0.8 (Microsoft 21.0.8+9-LTS)
Daemon JVM:    C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot (no JDK specified, using current Java home)
OS:            Windows 11 10.0 amd64
```

- 是否 8.10：**是**，横幅原文为 `Gradle 8.10`。
- 未下载发行版、未生成 wrapper。

## ⑦ PCL 隔离配置原文

来源：`C:\Users\Administrator\AppData\Roaming\PCLCE\config.v1.json` 与 `C:\Users\Administrator\AppData\Roaming\PCLPRO\config.v1.json`（均只读）。行号为实测 `Get-Content` 索引（1-based）。原文中的 `\uXXXX`、`\\` 为文件内真实字面量，逐字保留。

PCLCE `config.v1.json` 第 8 行（LaunchFolders）：

```
  "LaunchFolders": ".minecraft\u003ED:\\Minecraft\\.minecraft\\|.minecraft\u003ED:\\Minecraft\\\u65B0\u5EFA\u538B\u7F29\\.minecraft\\|\u5B98\u65B9\u542F\u52A8\u5668\u6587\u4EF6\u5939\u003EC:\\Users\\Administrator\\AppData\\Roaming\\.minecraft\\|.minecraft\u003EC:\\Users\\Administrator\\Desktop\\.minecraft\\",
```

PCLCE `config.v1.json` 第 4 行（Java 配置）：

```
  "LaunchArgumentJavaUser": "[{\u0022Path\u0022:\u0022C:\\\\Program Files\\\\Microsoft\\\\jdk-21.0.8.9-hotspot\\\\bin\\\\java.exe\u0022,\u0022IsEnable\u0022:true},{\u0022Path\u0022:\u0022C:\\\\Users\\\\Administrator\\\\AppData\\\\Roaming\\\\.minecraft\\\\runtime\\\\java-runtime-delta\\\\bin\\\\java.exe\u0022,\u0022IsEnable\u0022:true}]",
```

PCLPRO `config.v1.json` 第 7 行（LaunchFolders）：

```
  "LaunchFolders": ".minecraft\u003ED:\\Minecraft\\.minecraft\\",
```

PCLPRO `config.v1.json` 第 8 行（Java 配置）：

```
  "LaunchArgumentJavaUser": "[{\u0022Path\u0022:\u0022C:\\\\Program Files\\\\Microsoft\\\\jdk-21.0.8.9-hotspot\\\\bin\\\\java.exe\u0022,\u0022IsEnable\u0022:true},{\u0022Path\u0022:\u0022C:\\\\Users\\\\Administrator\\\\AppData\\\\Roaming\\\\.minecraft\\\\runtime\\\\java-runtime-delta\\\\bin\\\\java.exe\u0022,\u0022IsEnable\u0022:true}]",
```

- 两份配置的 Java 路径一致：均为 ⑤ 节中的两个 Java 21（jdk-21.0.8.9-hotspot、java-runtime-delta）。
- 未修改 PCL 全局设置，未改动上述任何配置。

## ⑧ 其他实例清单（只列名字）

`D:\Minecraft\.minecraft\versions` 下实例文件夹共 **13 个（分母=13，实测全部）**：

1. `1.20.1-Forge_47.4.22`
2. `1.21.11-Fabric`
3. `1.21.11-Fabric 0.19.5`
4. `1.21.8 Fabric 0.18.4`
5. `1.21.8-Fabric 0.18.4`
6. `1111`
7. `26.2`（父实例）
8. `fabric-loader-0.20.1-26.2`（本任务目标）
9. `Fabulously Optimized`
10. `xiaohe66-lotus-1.21.8`
11. `xiaohe66-lotus-17.2-1.21.11`
12. `Ô­°æ`（PowerShell 输出即为此乱码，按原样记录；不重命名、不猜测其含义）
13. `自然之需YSM兼容整合`

- 除目标与父实例外，其余文件夹**仅列名字，未读取内容、未做任何修改**。

## ⑨ 违反约束情况

**无。**

说明：
- 全程只做只读核验；唯一落盘文件为本报告 `m2-env-A.md`，未写入 `src/`、`build.gradle`、任何 json、`PROGRESS.md`、`AGENTS.md` 等。
- 未使用 Computer Use；`java -version` / `gradle --version` 仅为版本字符串只读查询，未启动任何 MC 实例，未以命令行 java 冒充“PCL 点启动”。
- 未联网下载 MC 文件；未安装 JRE；未改动 PCL 全局设置；未改动 versions 下其他实例；未下载 Gradle 发行版、未生成 wrapper。
- 包名、路径、行号、字段、版本号均来自本次实测；未编造。发现 Java 版本不自洽时按约束停下并贴原文，未自行择一。
