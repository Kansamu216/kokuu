<div align="center">

# Voidkit

**NeoForge 1.21.1 纯服务端模组套件**

用服务端 mod 实现 Bukkit / Essentials / CMI 那一类通用服务器功能，
外加**独立发布的反作弊与操作审计组件**，以及一个轻量管理平台。

玩家用**原版客户端**即可进服，不需要安装任何东西。

[![Release](https://img.shields.io/github/v/release/KokuuWork/voidkit?label=release)](https://github.com/KokuuWork/voidkit/releases)
[![License](https://img.shields.io/github/license/KokuuWork/voidkit)](./LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3eaf7c)](https://neoforged.net/)
[![Docs](https://img.shields.io/badge/docs-kokuuwork.github.io-3eaf7c)](https://kokuuwork.github.io/voidkit/)

**[📖 完整文档](https://kokuuwork.github.io/voidkit/)** ·
[快速开始](https://kokuuwork.github.io/voidkit/guide/) ·
[命令速查](https://kokuuwork.github.io/voidkit/guide/commands.html) ·
[模块总览](https://kokuuwork.github.io/voidkit/modules/)

</div>

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

## 生态

| 仓库 | 内容 | 依赖 |
|---|---|---|
| **`voidkit`**（本仓库） | 内核 + 7 插件 + Go 管理平台 | — |
| [`voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector) | **操作审计**（COI 风格） | `voidkit_core` |
| [`voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss) | **反作弊**（客户端 Agent + 服务端校验） | `voidkit_core` |

## 模块

```
内核 + 7 个插件，每个都是独立 jar，可单独安装、单独卸载、单独发布
```

| 模块 | modId | 职责 | 必需 |
|---|---|---|---|
| `core/` | `voidkit_core` | **内核**：插件契约、服务注册表、数据、文本/变量、多语言 | ✅ |
| `essentials/` | `voidkit_essentials` | 家 / 传送点 / TPA / 出生点 / 管理工具 / 命令简写 | 可选 |
| `display/` | `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 | 可选 |
| `perm/` | `voidkit_perm` | 权限（LuckPerms 风格组树） | 可选 |
| `title/` | `voidkit_title` | 称号 + 游戏内 GUI | 可选 |
| `economy/` | `voidkit_economy` | 经济 / 商店 / 物品回收 | 可选 |
| `utility/` | `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测 | 可选 |
| `web/` | `voidkit_web` | 管理 API 桥（**唯一监听端口的**） | 可选 |
| `hub/` | —（Go 程序） | **管理平台**：多服聚合的后台面板 | 可选 |
| [`inspector`](https://github.com/KokuuWork/voidkit-inspector) | `voidkit_inspector` | **审计**（COI 风格） | 独立发布 |
| [`guard`](https://github.com/KokuuWork/voidkit-guard-oss) | `voidkit_guard` | **反作弊** | 独立发布 |

> 只有 `voidkit_core` 打包了 Kotlin 运行时，其余插件共享它，插件本体都只有几十 KB。

**少装任何一个都不会崩**：内核只认接口，并给每个服务准备了保守的兜底实现。
例如不装 `voidkit_perm`，权限判定退化成「只认原版 OP」，其余功能照常。

**jar 粒度 ≠ 功能粒度**：每个功能都是独立可关的 `Feature`，
可以在配置的 `features` 段、`/vk features`、或面板「模块」页逐个开关。

## 快速开始

需要 **JDK 21**（Minecraft 1.21.1 的要求）。

```bash
git clone https://github.com/KokuuWork/voidkit.git
cd voidkit
./build.sh build --continue          # 产物在各模块 build/libs/ 下
```

把 jar 丢进服务端 `mods/` 目录，**最少装 `voidkit_core`**，其余按需。
也可以直接从 [Releases](https://github.com/KokuuWork/voidkit/releases) 下载。

首次启动会在**服务端根目录**生成 `voidkit/` 配置目录，**所有配置文件都带中文注释**，
打开即可改。完整说明见 [安装部署](https://kokuuwork.github.io/voidkit/guide/install.html)。

## 常用命令

| 命令 | 说明 |
|---|---|
| `/vk` | 内核管理：`reload` `save` `token` `status` `features` `api` |
| `/sethome` `/home` `/delhome` `/homes` | 家 |
| `/setwarp` `/warp` `/warps` `/delwarp` | 公共传送点 |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | 玩家互传 |
| `/fly` `/god` `/heal` `/feed` `/vanish` `/hat` `/enderchest` `/invsee` | 管理工具 |
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
| [模块](https://kokuuwork.github.io/voidkit/modules/) | 内核与 7 个插件逐个详解 |
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
