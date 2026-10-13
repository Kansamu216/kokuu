# 跨平台与跨版本移植

Voidkit 的目标是支持 **NeoForge + Fabric，1.20.X – 1.21.X**。
这套能力靠内核的 `core.platform` **适配层**实现。

## 核心认知

**`net.minecraft.*` 是 Mojang 原版类，NeoForge / Forge / Fabric 三方同包同名**，
**不需要抽象**。

真正要收口的只有两类：

| 类别 | 例子 |
|---|---|
| **加载器差异** | `net.neoforged.*` / `net.minecraftforge.*` / `net.fabricmc.*` |
| **版本差异** | 1.20.1 的 NBT vs 1.21.1 的 DataComponent（ItemStack 组件系统大改） |

## 分层

```
插件（业务代码）
   │  只用 core.api，绝不 import 加载器类
   ▼
core.api        契约层 · 稳定 · 跨平台跨版本一行不改
   ▼
core.platform   适配层 · 唯一大改点 · 按加载器/版本重写
   ▼
net.minecraft.* / net.neoforged.*
```

**移植 = 只重写 `platform/xxx/`，`core.api` 与全部插件零改动。**

## 适配层收口了什么

`core.platform` 把「每个平台都不一样」的东西収成一组接口：

| 接口 / 能力 | 收口的问题 |
|---|---|
| `Platforms.current` | 当前平台的实现单例 |
| `gameDir()` | 游戏根目录（各加载器获取方式不同） |
| `registerLifecycle(ServerLifecycleListener)` | 生命周期事件（`ServerAboutToStart` / 命令注册 / 启停） |
| **VEvent**（`VPlayerJoin` / `VQuit` / `VDeath` / `VChat` / `VRightClickItem` …） | 平台无关的事件抽象，业务代码看不到 `@SubscribeEvent` 的原生事件类 |
| **VItems**（`ItemAccessor`） | 物品自定义名 / Lore / 组件访问（抹平 NBT ↔ DataComponent） |
| `@Mod` 入口 | 加载器入口注解的差异 |

业务 `Feature` 只需覆写平台无关的回调：

```kotlin
override fun onPlayerJoin(player: ServerPlayer) { ... }
override fun onPlayerQuit(player: ServerPlayer) { ... }
```

而不用关心底层是哪套事件总线。

## 移植清单（大致）

| 目标 | 要改的 |
|---|---|
| **Fabric** | 新增 `platform/fabric/`（一个 `@Mod` 入口 + 各接口实现）；业务代码零改动 |
| **1.20.x** | 新增/调整 `platform/neoforge-1.20/`，主要是 `VItems` 的 NBT ↔ DataComponent 实现 |
| **Forge** | 暂缓（优先级低于 Fabric） |

## 当前状态

- `core.platform` 适配层已落地：假玩家判定、游戏目录、生命周期事件已收口。
- **事件抽象（VEvent）** 与 **物品抽象（VItems）** 正在推进 ——
  这是从「NeoForge 单平台」到「多平台多版本」的关键一跃。

## 反作弊的移植

[VoidkitGuard](../anticheat/) 同样遵循这套思路：`common` / `server` / `client`
是平台无关的，唯一碰 `net.neoforged.*` 的是 `platform/neoforge/`。
换加载器 = 新增一个 `platform/fabric/` 目录。

> 具体进度与剩余工作量见仓库根的 `ROADMAP.md`。
