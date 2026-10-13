# 版本与加载器

Voidkit 是**多加载器 + 多版本**的套件：同一个仓库、同一套代码，按**版本线分支**维护。

```
KokuuWork/voidkit
├── main              1.21.1 · NeoForge      ← 主线
├── mc/1.20.1         1.20.1 · Forge + NeoForge（同一份 jar）
└── …                 后续按版本线继续加分支
```

> **不按加载器分仓库。** 早期曾打算给 Fabric 单开一个仓库，后来统一改成
> **一个仓库 + 版本线分支** —— 代码只有一份，差异集中在 `core.platform` 适配层，
> 分仓会让「同一处 bug 要改三遍」，得不偿失。

## 支持矩阵

| Minecraft | NeoForge | Forge | Fabric |
|---|---|---|---|
| **1.21.1** | ✅ 已发布（主线 `main`） | — | ⏳ 规划中 |
| **1.20.1** | ✅ 同一份 jar | ✅ 开发中（`mc/1.20.1`） | ⏳ 规划中 |
| 1.20.4 | 构建配置就绪，未验证 | — | ⏳ 规划中 |

**1.20.1 为什么 Forge 与 NeoForge 共用一份 jar？**

NeoForge 在 2023 年从 Forge 分裂，**1.20.1 时期的构建与 Forge 几乎完全相同**
（沿用 Forge 的版本号 47.1.x，API 包名也都是 `net.minecraftforge.*`），
且 1.20.1 上绝大多数 Forge mod 可以直接在 NeoForge 上加载。
所以 1.20.1 只编译一份，两边都能用。（1.20.2 起两者彻底分家。）

## 怎么选

| 你的服务器 | 装哪个 |
|---|---|
| 1.21.1 NeoForge | `main` 分支的产物 |
| 1.20.1（Forge 或 NeoForge） | `mc/1.20.1` 分支的产物 |
| Fabric | 尚在规划中，见 [移植说明](../dev/porting.md) |

产物文件名里带版本线与加载器，**一眼能看出属于哪条线**：

```
voidkit_core-1.0.1+mc1.21.1-neoforge.jar
voidkit_core-1.0.1+mc1.20.1-forge.jar
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

- 每个版本线一个分支：`mc/<Minecraft 版本>`（加载器不用进分支名 —— 1.20.1 那份 jar 两边通用）
- **`main` 永远是最新主线**，不要合回低于它的版本线
- 修 bug 时优先修 `main`，再 forward/back-port 到其它版本线
- `core.api` 的改动必须**同时满足所有版本线**，否则说明抽象漏了

> 各版本线的施工单与进度：仓库根的 `PORTING-1.20.1.md`、`MIGRATION.md`、`ROADMAP.md`。
