# 模块总览

Voidkit 由 **1 个内核 + 4 个插件 jar（共 5 个 jar）**组成，另有 **2 个独立发布的组件**
（反作弊、审计）和 **1 个管理平台**。

> 2026-10-13 起把原来的 8 个 jar 做了「一 jar 一 modId」的**真源码合并**：`perm` + `economy`
> 的源码并入 `voidkit_essentials`，`title` 的源码并入 `voidkit_display`。所以下面 `perm` /
> `title` / `economy` 三页依然保留（feature、权限节点、配置文件都没变），但它们**不再是独立
> jar / modId，也不需要单独下载** —— 装对应的 `voidkit_essentials` / `voidkit_display` 即可。

## 一览

| 模块 | 所在 jar / modId | 职责 | 独立仓库 |
|---|---|---|---|
| [内核](./core.md) | `voidkit_core` | 插件契约、服务注册表、数据、文本/变量、多语言 | — |
| [essentials](./essentials.md) | `voidkit_essentials` | 家 / 传送 / TPA / 出生点 / 管理工具 / 命令简写 | — |
| [display](./display.md) | `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 | — |
| [perm](./perm.md) | 并入 `voidkit_essentials` | 权限（LuckPerms 风格组树） | — |
| [title](./title.md) | 并入 `voidkit_display` | 称号 + 游戏内 GUI | — |
| [economy](./economy.md) | 并入 `voidkit_essentials` | 经济、商店、物品回收 | — |
| [utility](./utility.md) | `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测 | — |
| [web](./web.md) | `voidkit_web` | 管理 API 桥 | — |
| [反作弊](../anticheat/) | `voidkit_guard` | 客户端 Agent + 服务端校验 | [voidkit-guard-oss](https://github.com/KokuuWork/voidkit-guard-oss) |
| [审计](../audit/) | `voidkit_inspector` | 操作审计（COI 风格） | [voidkit-inspector](https://github.com/KokuuWork/voidkit-inspector) |
| [管理平台](../panel/) | —（Go 程序） | 多服聚合的后台面板 | 本仓库 `hub/` |

## jar 粒度 ≠ 功能粒度

**每个功能都是一个独立的 `Feature`**，可以在 `config.json` 的 `features` 段、
`/vk features` 清单、或面板「模块」页里**逐个开关**。

| 模块 | 内含的功能（`features` id） |
|---|---|
| `core` | `core` `player-tracking` |
| `essentials` | `homes` `warps` `spawn` `back` `tpa` `teleport` `admin-tools` `info` `command-alias` |
| `display` | `motd` `tab` `scoreboard` `chat-format` `announce` `menu` |
| `perm` | `permissions` |
| `title` | `titles` |
| `economy` | `economy` |
| `utility` | `clearlag` `perf` |
| `web` | `remote-api` |

> 例：只想要「记分牌」不想要「聊天栏」，装 `voidkit_display` 后把 `chat-format`
> 设为 `false` 即可，不用卸载整个 jar。
