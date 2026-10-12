# Voidkit 系统架构

> 面向评审/接手者的一份完整说明：项目是什么、怎么组织的、现在什么状态、下一步怎么改。

## 一、项目是什么

**Voidkit** 是一套 NeoForge **1.21.1 纯服务端**模组套件，面向**几百个 mod 的大型科技整合包**
（实测于 SkyExplorer，165 mod）。

一句话：**「智能 AI 基础模组」—— 零配置、开箱即用、符合直觉、不出问题**。核心卖点不是堆功能，
而是「装上就能用、用着舒服、不会因为几百个 mod 的环境而出问题」。

产品气质对标 Apple：极简克制、丝滑动效、直觉交互、不用看说明书。

## 二、仓库与模块

两个仓库：

| 仓库 | 内容 | 状态 |
|---|---|---|
| `KokuuWork/voidkit` | 内核 + 7 插件（8 jar）+ Go 管理平台 | v1.2.0 |
| `KokuuWork/voidkit-inspector` | 操作审计（COI），依赖 core 前置 | v1.2.0 |

`voidkit` 的 8 个 jar（每个可独立安装/卸载/发布）：

| 目录 | modId | 职责 |
|---|---|---|
| `core/` | `voidkit_core` | **内核**：插件契约、服务注册表、数据、文本、变量、多语言（唯一带 Kotlin 运行时） |
| `essentials/` | `voidkit_essentials` | 传送 / 家 / 传送点 / TPA / 管理工具 + 命令简写 |
| `display/` | `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 + 公告 / 菜单 |
| `perm/` | `voidkit_perm` | 权限（LuckPerms 风格组树；**可选**，不装退化为只认 OP） |
| `title/` | `voidkit_title` | 称号 + 游戏内 GUI（含玩家自定义称号） |
| `economy/` | `voidkit_economy` | 经济（余额 / 转账 / 排行 + 估值表 `/sell`；**可选**） |
| `utility/` | `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测（TPS/MSPT/区块热点） |
| `web/` | `voidkit_web` | 管理 API 桥（**唯一会监听端口的**，配合 Go hub） |

> **jar 粒度 ≠ 功能粒度**：每个功能都是独立可关的 `Feature`（配置文件 `features` 段 + `/voidkit features` + 面板）。

## 三、分层架构（核心）

```
                    ┌────────────────────────────────┐
                    │      浏览器 / 服主              │
                    └───────────────┬────────────────┘
                                    │ HTTP / WebSocket
                    ┌───────────────▼────────────────┐
                    │  KokuuHub（Go 单二进制，多服中心）│
                    │  /hub/proxy/{id}/api/... 透传   │
                    │  + 令牌托管 + 账号              │
                    └───────┬───────────┬────────────┘
                            │ pull      │ pull
                    ┌───────▼──┐   ┌────▼────────┐
                    │ 游戏服 A  │   │ 游戏服 B ... │  ← 各装 voidkit_core + 所需插件
                    └───────┬──┘   └────┬────────┘
                            │           │
              ┌─────────────▼───────────▼─────────────┐
              │            voidkit_core（内核）        │
              │                                       │
              │   ┌─────────────────────────────┐     │
              │   │ core.api（契约层 · 稳定）      │     │
              │   │ Feature / VoidkitServices   │     │
              │   │ ApiRouter / VoidkitServer   │     │
              │   │ ServerLifecycleListener     │     │
              │   │ EconomyProvider 等接口       │     │
              │   └──────────────┬──────────────┘     │
              │   ┌──────────────▼──────────────┐     │
              │   │ core.platform（适配层 · 唯一  │     │
              │   │ 按加载器/版本重写）           │     │
              │   │ Platform 接口 + NeoForge实现 │     │
              │   └──────────────┬──────────────┘     │
              │   ┌──────────────▼──────────────┐     │
              │   │ net.minecraft.*（三平台同名） │     │
              │   │ net.neoforged.*（加载器 API）│     │
              │   └─────────────────────────────┘     │
              └───────────────────────────────────────┘
```

**依赖方向（铁律）**：插件 → 只依赖 `core.api` → 只依赖 `core.platform` → 加载器。

- **`core.api`（契约层，稳定）**：跨平台跨版本一行不改。插件只用它，**绝不 import 加载器类**。
- **`core.platform`（适配层，唯一大改点）**：收口「每个平台都不一样」的东西——事件总线、假玩家判定、
  `@Mod` 入口、游戏目录。
- **关键认知**：`net.minecraft.*` 是 Mojang 原版类，NeoForge/Forge/Fabric **同包同名**，**不用抽象**；
  只有 `net.neoforged.*` / `net.minecraftforge.*` / `net.fabricmc.*`（加载器 API）要收口。

**移植 = 只重写 `platform/xxx/`，`core.api` 与全部插件零改动。**

## 四、core 内部结构

```
core/src/main/kotlin/org/voidkit/core/
├── api/          契约：Feature、VoidkitServices、ApiRouter、VoidkitServer、
│                 ServerLifecycleListener、PermissionProvider、EconomyProvider、
│                 HomeLimits、ConfigSnapshots、ServerEndpoint、PlayerViews、VoidkitLang
├── platform/     适配层：Platform 接口 + Platforms 单例 + neoforge/ 实现
├── config/       配置：VoidkitConfig、ConfigComments、ConfigEditor、LegacyConfig
├── core/         PluginRegistry（插件系统唯一入口）、CoreServices
├── data/         DataStore、PlayerProfile、GlobalData、JoinLog、IpRecord、StoredLocation
├── entity/       EntityTools
├── event/        ServerLifecycle（实现 ServerLifecycleListener，驱动生命周期）
├── feature/      CoreFeature、PlayerTrackingFeature
├── placeholder/  PlaceholderRegistry（%变量%）
├── teleport/     Teleports
└── util/         Broadcast、CommentedJson、ConfigFiles、Icons、Msg、Placeholders、Text
```

## 五、关键设计决策

1. **低耦合（最高原则）**：每个组件只依赖一个 core，**core + 任意组件都能单独运行**。
   插件之间零编译依赖（已验证 0 处跨插件 import），跨插件通信只走 `VoidkitServices`。
2. **构造期零 IO**：配置读取一律推迟到 `ServerAboutToStartEvent`——几百个 mod 并行加载时，
   构造期做一次文件 IO 会直接拖长启动关键路径。
3. **内核无 HTTP**：各模块只往 `ApiRouter`（纯数据登记表）挂路由，由 web 插件消费。
4. **零 mixin**：全部走事件监听，不碰 mixin（这是整合包兼容性的关键纪律）。
5. **平台层收口两类差异**：① 加载器差异（NeoForge↔Fabric）；② 版本差异
   （1.20.1 的 NBT vs 1.21.1 的 DataComponent，ItemStack 组件系统大改）。
6. **审计的线程模型（inspector）**：主线程只做 O(1) 入队 → 唯一写线程批量事务落库（WAL），
   队列满自动丢弃不卡 TPS；查询走独立线程池 + 各自读连接，与写入并行。

## 六、技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | Kotlin 2.1 | 用户明确拥抱新语言（不选 Java） |
| 加载器 | NeoForge 21.1.x（MC 1.21.1） | 目标版本 1.20.x-1.21.x |
| 构建 | Gradle 8 + JDK 21 | MC 1.21.1 强制 JDK 21 |
| 审计存储 | 嵌入式 SQLite（jarJar） | 零配置单文件，一写多读匹配线程模型 |
| 管理平台 | Go 单二进制 | 轻量、全平台（含 Linux） |

## 七、运行时数据目录

```
<服务端根>/voidkit/            ← 放根目录不放 world/，删存档不丢玩家数据
├── core/config.json + core/data/{players,logs}
├── display/{motd,tab,scoreboard,chat,announce,menu}.json
├── perm/{groups,users,settings}.json
├── title/titles.json
├── economy/{accounts,values,config}.json
└── essentials/commands.json 等
```

（审计模块的数据在独立仓：`config.json + inspector.db`）

## 八、当前状态（v1.2.0）

**已完成**：
- 内核 + 7 插件全部可用，v1.2.0 已发布
- `core.platform` 层落地：假玩家判定、游戏目录、生命周期事件已收口
- 称号自定义、经济估值表、审计箱子 GUI 已实现
- 审计模块独立成仓并发布

**真机验证过的**：smoke-test（起服 + 23 模块注册 + 24 项 API 断言 + 配置落盘）通过。
**未验证的**：运行时交互（GUI 点击、`/sell` 交易、称号设置）—— 需真人进服。

## 九、改进方向（怎么改）

按优先级：

| 优先级 | 事项 | 类型 | 说明 |
|---|---|---|---|
| **P0** | 真人进服验证交互 | 验证 | GUI 点击/交易/称号，smoke-test 覆盖不到 |
| **P0** | #5 数据去重 | 技术债 | `PlayerProfile.group` 与 `perm/users.json` 重复，面板组名会过期 |
| **P1** | #1 事件抽象（VEvent） | 架构 | 把 Feature 子类的 `@SubscribeEvent` 收口，解锁 Fabric 移植 |
| **P1** | #1 版本抽象（VItem/VNbt） | 架构 | 收口 1.20.1 NBT vs 1.21.1 DataComponent |
| **P1** | #6 单元测试 | 工程 | 当前只有端到端 smoke-test，无单测 |
| **P2** | #8 智能回滚 | 功能 | 分类感知 + 预览 + 撤销（审计核心卖点） |
| **P2** | #11 技术栈现代化 + CI | 工程 | Detekt/Ktlint/CI 自动发布/Prometheus 指标 |
| **P2** | #15 后台可视化 | 功能 | 审计聚合图/时间线 |
| **P3** | #2 商店 GUI | 功能 | 经济估值表已有，商店界面未做 |
| **P3** | #7 性能深化 / #9 火焰日志 | 功能 | 区块耗时归因 / 火焰蔓延日志 |

**最大的架构债**：跨平台还差「事件抽象」和「版本抽象」两步——这是从「NeoForge 单平台」
到「NeoForge/Fabric + 多版本」的关键一跃，也是工作量最大、风险最高的部分。

## 十、接手须知

- 构建：`./build.sh build`（内置 JDK21 + 国内镜像，直接跑）
- 端到端验证：`tools/smoke-test.sh`（起服 + 打 API）
- 铁律见第五节，改代码前先读 `core/api/Feature.kt` 和 `core/core/PluginRegistry.kt`
- 完整决策过程见 `.workbuddy/memory/`（每日日志 + MEMORY.md）
