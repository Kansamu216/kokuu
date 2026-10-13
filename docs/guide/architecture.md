# 架构

## 一句话

**插件 → 只依赖 `core.api` → 只依赖 `core.platform` → 加载器（NeoForge）。**

## 分层

```
                    ┌────────────────────────────────┐
                    │      浏览器 / 服主              │
                    └───────────────┬────────────────┘
                                    │ HTTP
                    ┌───────────────▼────────────────┐
                    │  VoidkitHub（Go 单二进制，多服） │
                    └───────┬───────────┬────────────┘
                            │           │
                    ┌───────▼──┐   ┌────▼────────┐
                    │ 游戏服 A  │   │ 游戏服 B ... │  ← 各装 voidkit_core + 所需插件
                    └───────┬──┘   └────┬────────┘
                            │           │
              ┌─────────────▼───────────▼─────────────┐
              │            voidkit_core（内核）        │
              │   core.api（契约层 · 稳定）             │
              │   core.platform（适配层 · 按加载器重写） │
              │   net.minecraft.* / net.neoforged.*    │
              └───────────────────────────────────────┘
```

- **`core.api`（契约层，稳定）**：跨平台跨版本一行不改。插件只用它，
  **绝不 import 加载器类**。
- **`core.platform`（适配层，唯一大改点）**：收口「每个平台都不一样」的东西 ——
  事件总线、假玩家判定、`@Mod` 入口、游戏目录、物品组件。
- **关键认知**：`net.minecraft.*` 是 Mojang 原版类，NeoForge / Forge / Fabric
  **同包同名**，**不用抽象**；只有 `net.neoforged.*` / `net.fabricmc.*`
  （加载器 API）要收口。

> **移植 = 只重写 `platform/xxx/`，`core.api` 与全部插件零改动。**

## 内核不认识任何业务概念

内核**不认识**「家」「传送点」「权限组」「称号」「经济」。它只提供这几样：

- 插件契约与生命周期（`Feature`）
- 跨插件服务注册表（`VoidkitServices`）
- 数据存储、文本/颜色解析、变量系统、实体与传送工具
- 一个纯数据的 HTTP 路由登记表（`ApiRouter`）—— **内核里没有一行 HTTP 代码**

由此得到：

- **少一个插件不会让整套崩掉** —— 每个服务都有保守的兜底实现。
- **插件之间零编译依赖** —— 通过 `VoidkitServices` 通信，卸载任意插件不影响其它插件编译。
- **可以按需分配** —— 只想要基础指令就装 `core` + `essentials`。

## core 内部结构

```
core/src/main/kotlin/org/voidkit/core/
├── api/          契约：Feature、VoidkitServices、ApiRouter、VoidkitServer、
│                 ServerLifecycleListener、PermissionProvider、EconomyProvider、
│                 VEvents（平台无关事件）、VItems（物品访问）…
├── platform/     适配层：Platform 接口 + Platforms 单例 + neoforge/ 实现
├── config/       配置：VoidkitConfig、ConfigComments、ConfigEditor、LegacyConfig
├── core/         PluginRegistry（插件系统唯一入口）、CoreServices
├── data/         DataStore、PlayerProfile、GlobalData、JoinLog、IpRecord
├── entity/       EntityTools
├── event/        ServerLifecycle
├── feature/      CoreFeature、PlayerTrackingFeature
├── placeholder/  PlaceholderRegistry（%变量%）
├── teleport/     Teleports
└── util/         Broadcast、CommentedJson、ConfigFiles、Msg、Placeholders、Text
```

## 关键设计决策

1. **低耦合（最高原则）**：每个组件只依赖一个 core，**core + 任意组件都能单独运行**。
2. **构造期零 IO**：配置读取一律推迟到 `ServerAboutToStartEvent` ——
   几百个 mod 并行加载时，构造期做一次文件 IO 会直接拖长启动关键路径。
3. **内核无 HTTP**：各模块只往 `ApiRouter`（纯数据登记表）挂路由，由 web 插件消费。
4. **零 mixin**：全部走事件监听 —— 这是整合包兼容性的关键纪律。
5. **平台层收口两类差异**：加载器差异（NeoForge ↔ Fabric）+ 版本差异
   （1.20.1 的 NBT vs 1.21.1 的 DataComponent）。
6. **审计的线程模型**：主线程只做 O(1) 入队 → 唯一写线程批量事务落库（WAL），
   队列满自动丢弃不卡 TPS；查询走独立线程池 + 各自读连接，与写入并行。

## 大型整合包兼容性

主要目标环境是**几百个 mod 的重度整合包**（已在 165 mod 的 SkyExplorer 实测）。为此：

- **零 mixin** —— 不与整合包里已有的 mixin mod 争抢类改写
- **mod 构造期零 IO** —— 避免拖长启动关键路径
- **不使用连字符作为 modId** —— NeoForge 校验 `^[a-z][a-z0-9_]{1,63}$`
- **不做侵入性写入** —— 例如 MOTD 默认**不会**改写 `server.properties`
- **周期性任务默认保守**，全部可配可关

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.1 |
| 加载器 | NeoForge 21.1.x（MC 1.21.1） |
| 构建 | Gradle 8 + JDK 21 |
| 审计存储 | 嵌入式 SQLite（jarJar） |
| 管理平台 | Go 单二进制 |
