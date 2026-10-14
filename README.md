<div align="center">

<img src=".github/assets/banner.png" alt="Voidkit" width="100%">

# Voidkit

**支持 NeoForge · Forge · Fabric 的高版本服务器模组套件**

用服务端 mod 实现 Bukkit / Essentials / CMI 那一类通用服务器功能，
外加**独立发布的反作弊与操作审计组件**，以及一个轻量管理平台。

玩家用**原版客户端**即可进服，不需要安装任何东西。

[![Release](https://img.shields.io/github/v/release/KokuuWork/voidkit?include_prereleases&label=release)](https://github.com/KokuuWork/voidkit/releases)
[![Status](https://img.shields.io/badge/status-beta-4f46e5)](#-测试版说明)
[![License](https://img.shields.io/github/license/KokuuWork/voidkit)](./LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.x%20~%201.21.x-4f46e5)](https://kokuuwork.github.io/voidkit/guide/versions.html)
[![Loaders](https://img.shields.io/badge/loaders-NeoForge%20%C2%B7%20Forge%20%C2%B7%20Fabric-4f46e5)](https://kokuuwork.github.io/voidkit/guide/versions.html)
[![Docs](https://img.shields.io/badge/docs-kokuuwork.github.io-4f46e5)](https://kokuuwork.github.io/voidkit/)

**[📖 完整文档](https://kokuuwork.github.io/voidkit/)** ·
[快速开始](https://kokuuwork.github.io/voidkit/guide/) ·
[命令速查](https://kokuuwork.github.io/voidkit/guide/commands.html) ·
[权限节点](https://kokuuwork.github.io/voidkit/guide/permissions.html) ·
[版本与加载器](https://kokuuwork.github.io/voidkit/guide/versions.html) ·
[模块总览](https://kokuuwork.github.io/voidkit/modules/)

</div>

---

## 🚧 测试版说明

> **目前所有 release 都是测试版本（beta），尚未发布正式版。**
>
> 功能多数未经系统实测，**不建议用于生产环境** —— 请在小众测试服试用并反馈问题。
> 版本号从 `0.1.0-beta.1`（beta0.1）起算，修复随版本持续推进，请始终以最新 Release 为准。

---

## 这是什么

Voidkit 面向**几百个 mod 的大型科技整合包**（已在 165 mod 的 SkyExplorer 实测）。
目标不是堆功能，而是**「装上就能用、用着舒服、不会因为几百个 mod 的环境而出问题」**。

它解决三件事：

- **基础服务器功能** —— 家 / 传送 / TPA / 记分牌 / 聊天栏 / 清扫 / 经济 / 权限 / 称号，
  Bukkit 生态那一套，在 NeoForge 上用不了，这里补齐。
- **反作弊** —— 客户端 Agent + 服务端校验：哈希锁、中途复检、蜜罐协议、静默取证。
- **查破坏与回滚** —— CoreProtect 风格的审计，**专为科技包设计**：
  自动化产线不污染日志，真正的那次破坏才查得出来。

## 版本与加载器

**一个仓库 + 版本线分支**，同一套代码支撑多加载器、多版本：

| Minecraft | NeoForge | Forge | Fabric |
|---|---|---|---|
| **1.21.1** | ✅ 主线 `main` | ✅ 已发布（`mc/1.21.1-forge`，需 Forge 52.x） | ✅ 已发布（`feat/fabric`） |
| **1.20.1** | ✅ 同一份 jar | ✅ 同一份 jar（`mc/1.20.1`） | ⏳ 规划中 |

> 1.20.1 的 Forge 与 NeoForge **共用一份 jar** —— NeoForge 1.20.1 就是 Forge 47.1.x 的分支、
> 且以 modId `forge` 注册，API 包名完全相同（`net.minecraftforge.*`）。已真机实测两种加载器
> 均能加载；Release 里按加载器各给一个命名包（**内容完全相同，取一份即可**）。详见
> [版本与加载器](https://kokuuwork.github.io/voidkit/guide/versions.html)。

## 生态

| 仓库 | 内容 | 依赖 |
|---|---|---|
| **`voidkit`**（本仓库） | 内核 + 4 插件 jar（共 5 jar）+ Go 管理平台 | — |
| [`voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector) | **操作审计**（COI 风格） | `voidkit_core` |
| [`voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss) | **反作弊**（客户端 Agent + 服务端校验） | `voidkit_core` |

## 模块

```
5 个 jar：1 个内核 + 4 个插件 jar，每个可单独安装、单独卸载、单独发布
```

| jar | modId | 职责 | 必需 |
|---|---|---|---|
| `core/` | `voidkit_core` | **内核**：插件契约、服务注册表、数据、文本/变量、多语言 | ✅ |
| `essentials/` | `voidkit_essentials` | 家 / 传送点 / TPA / 出生点 / 管理工具 / 命令简写 **+ 权限 + 经济** | 可选 |
| `display/` | `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 **+ 称号** | 可选 |
| `utility/` | `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测 | 可选 |
| `web/` | `voidkit_web` | 管理 API 桥（**唯一监听端口的**） | 可选 |
| `hub/` | —（Go 程序） | **管理平台**：多服聚合的后台面板 | 可选 |
| [`inspector`](https://github.com/KokuuWork/voidkit-inspector) | `voidkit_inspector` | **审计**（COI 风格） | 独立发布 |
| [`guard`](https://github.com/KokuuWork/voidkit-guard-oss) | `voidkit_guard` | **反作弊** | 独立发布 |

> **为什么是 5 个 jar？** 2026-10-13 起把原来的 8 个 jar 做了「一 jar 一 modId」的**真源码合并**
> （决策与实施见 [`MIGRATION.md`](./MIGRATION.md)）：`perm` + `economy` 的源码并入 `voidkit_essentials`，
> `title` 的源码并入 `voidkit_display`。因此 **`voidkit_perm` / `voidkit_economy` / `voidkit_title`
> 三个 modId 不再存在**，没有（也不会有）单独的 jar —— 要这些功能就装对应的 `voidkit_essentials` /
> `voidkit_display`。功能粒度、权限节点、数据目录（`voidkit/perm/`、`voidkit/economy/`、
> `voidkit/title/`）全部不变，仍可逐个 `features` 开关。

> 只有 `voidkit_core` 打包了 Kotlin 运行时，其余插件共享它，插件本体都只有几十 KB。

**少装任何一个都不会崩**：内核只认接口，并给每个服务准备了保守的兜底实现。
例如不装 `voidkit_essentials`（或把其中的权限模块关掉），权限判定退化成「只认原版 OP」，其余功能照常。

**jar 粒度 ≠ 功能粒度**：每个功能都是独立可关的 `Feature`，
可以在配置的 `features` 段或面板「模块」页逐个开关，用 `/vk features` 查看当前清单与开关状态。

## 快速开始

需要 **JDK 21**（Minecraft 1.21.1 的要求）。

```bash
git clone https://github.com/KokuuWork/voidkit.git
cd voidkit
./build.sh build --continue          # 产物在各模块 build/libs/ 下
```

把 jar 丢进服务端 `mods/` 目录，**最少装 `voidkit_core`**，其余按需。

不想自己构建？从 [Releases](https://github.com/KokuuWork/voidkit/releases) 下载（**均为测试版**）——
每个版本线一个整版 zip（1.21.1 · NeoForge / 1.21.1 · Forge / 1.21.1 · Fabric / 1.20.1 · Forge / 1.20.1 · NeoForge），
管理平台 VoidkitHub 的 3 平台预编译二进制也在同一个 Release 里。

首次启动会在**服务端根目录**生成 `voidkit/` 配置目录，**所有配置文件都带中文注释**，
打开即可改。完整说明见 [安装部署](https://kokuuwork.github.io/voidkit/guide/install.html)。

## 常用命令

| 命令 | 说明 |
|---|---|
| `/vk` | 内核管理：`reload` `save` `token` `status` `features` `api` |
| `/sethome` `/home` `/delhome` `/homes` | 家 |
| `/setwarp` `/warp` `/warps` `/delwarp` | 公共传送点 |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | 玩家互传 |
| `/fly` `/god` `/heal` `/feed` `/vanish` `/invsee` | 管理工具 |
| `/hat` `/enderchest` `/workbench` | 玩家自助（默认开放） |
| `/day` `/night` `/sun` `/rain` `/gr` `/diff` `/gm` | 原版命令简写 |
| `/clearlag` `/trash` `/perf` | 清扫 / 垃圾桶 / 性能 |
| `/announce` `/menu` | 公告 / 菜单 |
| `/perm` `/title` | 权限 / 称号 |
| `/balance` `/pay` `/baltop` `/sell` `/shop` | 经济 |
| `/inspect`（`/coi`） | 操作审计 |
| `/guard` `/gban` `/gscreen` `/greport` | 反作弊 |

完整命令与权限节点见 [命令速查](https://kokuuwork.github.io/voidkit/guide/commands.html)。

## 为什么适合大型整合包

- **零 mixin** —— 不与整合包里已有的 mixin mod 争抢类改写
- **构造期零 IO** —— 配置读取推迟到服务端启动阶段，不拖长启动关键路径
- **不做侵入性写入** —— 例如 MOTD 默认**不会**改写 `server.properties`
- **命令简写逐条容错** —— 名字被别的 mod 占了只跳过这一条，其余照常注册
- **周期任务默认保守**，全部可配可关

## 文档

完整文档站：**<https://kokuuwork.github.io/voidkit/>**

| 章节 | 内容 |
|---|---|
| [快速开始](https://kokuuwork.github.io/voidkit/guide/) | 构建、安装、配置、命令、文本语法、架构、FAQ |
| [模块](https://kokuuwork.github.io/voidkit/modules/) | 内核与各模块逐个详解 |
| [反作弊](https://kokuuwork.github.io/voidkit/anticheat/) | VoidkitGuard |
| [审计](https://kokuuwork.github.io/voidkit/audit/) | VoidkitInspector |
| [管理平台](https://kokuuwork.github.io/voidkit/panel/) | VoidkitHub |
| [开发](https://kokuuwork.github.io/voidkit/dev/) | 写插件、跨平台移植 |

## 开发与验证

```bash
./build.sh build --continue     # 构建全部模块（内置 JDK 21 与国内镜像）
tools/smoke-test.sh             # 端到端冒烟测试（起服 + 打 API 断言）
```

**铁律**：低耦合（每个组件只依赖 core）、插件间零编译依赖、构造期零 IO、零 mixin。
详见 [开发指南](https://kokuuwork.github.io/voidkit/dev/) 与仓库根的
[`ARCHITECTURE.md`](./ARCHITECTURE.md)、[`ROADMAP.md`](./ROADMAP.md)。

## 许可证

[MIT](./LICENSE)
