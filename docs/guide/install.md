# 安装与部署

## 环境要求

| 项 | 要求 |
|---|---|
| 服务端 | NeoForge **1.21.1**（21.1.x） |
| Java | **JDK 21**（Minecraft 1.21.1 强制要求） |
| 客户端 | **原版即可**，无需安装任何 mod |

## 模块与 modId

Voidkit 由 **1 个内核 + 7 个插件**组成，每个都是独立 jar，可单独安装、卸载、发布。

| 目录 | modId | 职责 | 是否必需 |
|---|---|---|---|
| `core/` | `voidkit_core` | 内核：插件契约、服务注册表、数据存储、文本/变量、多语言 | **必需** |
| `essentials/` | `voidkit_essentials` | 家 / 传送点 / TPA / 出生点 / 管理工具 / 命令简写 | 可选 |
| `display/` | `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 | 可选 |
| `perm/` | `voidkit_perm` | 权限（LuckPerms 风格组树） | 可选 |
| `title/` | `voidkit_title` | 称号 + 游戏内 GUI | 可选 |
| `economy/` | `voidkit_economy` | 经济（余额 / 转账 / 排行 / 商店 / 回收） | 可选 |
| `utility/` | `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测 | 可选 |
| `web/` | `voidkit_web` | 管理 API 桥（**唯一会监听端口的**） | 可选 |

还有两个**独立仓库、独立发布**的组件：

| 仓库 | modId | 职责 |
|---|---|---|
| [`KokuuWork/voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector) | `voidkit_inspector` | 操作审计（COI 风格） |
| [`KokuuWork/voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss) | `voidkit_guard` | 反作弊（客户端 Agent + 服务端校验） |

两者都依赖 `voidkit_core` 作为前置。

## jar 粒度 ≠ 功能粒度

**每个功能都是一个独立的 `Feature`**，可以在 `config.json` 的 `features` 段、
`/vk features` 清单、或面板「模块」页里**逐个开关**。

所以把「公告」和「菜单」放进 `display`、把「命令简写」放进 `essentials`，
**不损失任何灵活性** —— jar 粒度只决定"发布与安装的打包单位"，不决定"功能能不能单独关"。

## 少装一个会不会崩？

**不会。** 内核只认接口，并给每个服务准备了保守的兜底实现：

| 不装 | 后果 |
|---|---|
| `voidkit_perm` | 权限判定退化成「只认原版 OP」，其余功能照常 |
| `voidkit_economy` | 扣款恒返回失败，表现为「暂时不能购买」而不是报错 |
| `voidkit_web` | 服务器不监听任何端口，一行 HTTP 都不跑 |
| `voidkit_essentials` | 家上限退化成保守默认值（3 / 32） |
| 任何 display 类插件 | 对应的展示功能不生效，其它功能不受影响 |

## 升级与数据目录

- 配置文件与数据都在**服务端根目录**的 `voidkit/` 下，升级 jar 不会动它们。
- 旧版把所有字段塞在 `core/config.json` 里的服务器，**升级时会自动搬迁**
  （见源码 `LegacyConfig`）。
- 卸载某个插件时，内核不会留下一堆没人读的死字段（配置按插件分家）。

> ⚠️ **别把 `voidkit/` 放进 `world/` 里**。默认就是放根目录，这是刻意的：
> 删存档重开时，玩家档案、IP 记录、审计数据不该跟着一起没。

## 下一步

- [配置文件](./config.md)
- [命令速查](./commands.md)
- [架构](./architecture.md)
