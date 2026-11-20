# M2 第一刀 · 回滚说明

## 改了什么
- 目标实例：`D:\Minecraft\.minecraft\versions\fabric-loader-0.20.1-26.2`（id 同名）
- 修改文件：`fabric-loader-0.20.1-26.2.json`
  - `mainClass`：`net.fabricmc.loader.impl.launch.knot.KnotClient` → `dev.modgraph.m2.M2Entry`
  - `libraries` 末尾追加 1 条：`dev.modgraph:modgraph:0.1.0-m2`
- 依赖 jar：`D:\Minecraft\.minecraft\libraries\dev\modgraph\modgraph\0.1.0-m2\modgraph-0.1.0-m2.jar`

## 回滚（退回官方 Fabric）
1. 关闭 PCL 中该实例的启动（若正在运行）。
2. 把同目录下的 `fabric-loader-0.20.1-26.2.json.bak-pre-m2` 覆盖回 `fabric-loader-0.20.1-26.2.json`。
3. 覆盖后 `mainClass` 恢复为 `net.fabricmc.loader.impl.launch.knot.KnotClient`，`libraries` 恢复为原始 7 条。
4. 重新在 PCL 选该实例启动，即回到官方 Fabric 行为。
- 说明：`.bak-pre-m2` 是原 json 的字节级黄金副本（SHA-256 `ad6913df9e38bd979181ad2b0f3ecdcf83c1dcafa2e2ba00762539992878808a`），本轮之后不再改动。
- libraries 下新增的坐标目录可留可删；只要 json 恢复原状，该 jar 不在 classpath 上，不生效。
