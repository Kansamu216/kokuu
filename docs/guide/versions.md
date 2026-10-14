# 版本与加载器

Voidkit 是**多加载器 + 多版本**的套件：同一个仓库、同一套代码，按**版本线分支**维护。

```
KokuuWork/voidkit
├── main                1.21.1 · NeoForge 21.1.x   ← 主线
├── mc/1.21.1-forge     1.21.1 · Forge 52.x        （与 NeoForge 是两套加载器，需独立一份）
├── feat/fabric         1.21.1 · Fabric            （需一并装 Fabric API）
└── mc/1.20.1           1.20.1 · Forge + NeoForge  （同一份 jar，两个加载器通用）
```

> ✅ 四条线**均已真机起服验证**：各 23/23 模块注册、管理 API 全部 200、无致命错误。

> **不按加载器分仓库。** 早期曾打算给 Fabric 单开一个仓库，后来统一改成
> **一个仓库 + 版本线分支** —— 代码只有一份，差异集中在 `core.platform` 适配层，
> 分仓会让「同一处 bug 要改三遍」，得不偿失。

## 支持矩阵

| Minecraft | NeoForge | Forge | Fabric |
|---|---|---|---|
| **1.21.1** | ✅ 已发布（主线 `main`） | ✅ 已发布（`mc/1.21.1-forge`，需 Forge 52.x） | ✅ 已发布（`feat/fabric`，需 Fabric API） |
| **1.20.1** | ✅ 同一份 jar | ✅ 同一份 jar（`mc/1.20.1`） | ⏳ 规划中 |
| 1.20.4 | 构建配置就绪，未验证 | — | ⏳ 规划中 |

**1.20.1 为什么 Forge 与 NeoForge 共用一份 jar？**

NeoForge 在 2023 年从 Forge 分裂，**1.20.1 时期的构建与 Forge 几乎完全相同**：
沿用 Forge 的版本号（47.1.x）、相同的 API 包名（`net.minecraftforge.*`）、相同的
`mods.toml` 格式与 `javafml` 加载器。更关键的一点是 —— NeoForge 1.20.1 自身
**以 `forge` 这个 modId 注册**（`displayName` 才是 "NeoForge"，版本 47.1.106 等），
所以声明依赖 `modId = "forge"`（区间 `[47,)`）的 Forge mod，在两个加载器上都能解析。
1.20.2 起两者彻底分家（包名改 `net.neoforged.*`、清单改 `neoforge.mods.toml`），
同一份 jar 不再通用。

> ✅ **已实测**：同一份 `mc/1.20.1` 发布包分别在 **Forge 47.4.20** 与
> **NeoForge 1.20.1（47.1.106）** 真机服务端上加载成功、23 个模块全部注册、
> 管理 API 全部返回 200（含历史上最易因映射差异出错的接口）。

## 怎么选

| 你的服务器 | 装哪个 |
|---|---|
| 1.21.1 NeoForge | `main` 分支的产物 |
| 1.20.1（Forge 或 NeoForge） | `mc/1.20.1` 分支的产物 |
| 1.21.1 Forge 52.x | `mc/1.21.1-forge` 分支的产物 |
| 1.21.1 Fabric（需 Fabric API） | `feat/fabric` 分支的产物 |

产物文件名里带版本线与加载器，**一眼能看出属于哪条线**：

```
voidkit_core-0.1.0-beta.2+mc1.21.1-neoforge.jar
voidkit_core-0.1.0-beta.2+mc1.20.1-forge.jar
```

不想自己构建？[Releases](https://github.com/KokuuWork/voidkit/releases) 里每个版本线各有一个整版 zip。

## 为什么能做到「代码只有一份」

套件的分层把「跨平台」收口到了极小的范围：

```
插件业务代码（跨版本/跨加载器基本不改）
   ↓ 只用 core.api
core.api       契约层：Feature / VoidkitServices / VEvents / VItem / …
   ↓
core.platform  适配层：Platform 接口 + platforms/{neoforge,forge,fabric}
   ↓
net.minecraft.*（各平台同包同名）/ 加载器 API（要收口）
```

**关键认知**：`net.minecraft.*` 是 Mojang 原版类，各加载器**同包同名，不用抽象**；
真正要收口的只有加载器 API（`net.neoforged.*` / `net.minecraftforge.*` / `net.fabricmc.*`）
和版本差异（例如 1.20.1 的 NBT vs 1.21.1 的 DataComponent）。

详见 [跨平台与跨版本移植](../dev/porting.md)。

## 分支纪律

- 每个版本线一个分支：`mc/<Minecraft 版本>`（1.20.1 那份 jar 两边通用，故加载器不进分支名；
  1.21.1 的 Forge 与 NeoForge **是两套加载器、各需一份**，所以用 `mc/1.21.1-forge` 区分）
- 各线的构建栈可以不同：1.21.1 Forge 线用 **ForgeGradle 7 + Gradle 9.3.1 + Kotlin 2.4.21**，
  其余线是 ModDevGradle + Gradle 8.12.1 + Kotlin 2.1.0（详见 `PORTING-1.21.1-FORGE.md`）
- **`main` 永远是最新主线**，不要合回低于它的版本线
- 修 bug 时优先修 `main`，再 forward/back-port 到其它版本线
- `core.api` 的改动必须**同时满足所有版本线**，否则说明抽象漏了

> 各版本线的施工单与进度：仓库根的 `PORTING-1.20.1.md`、`MIGRATION.md`、`ROADMAP.md`。
