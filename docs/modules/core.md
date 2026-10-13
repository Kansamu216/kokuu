# 内核（voidkit_core）

```
mods/voidkit_core-1.0.1+mc1.21.1-neoforge.jar
```

**每个服务器都必须装它。** 它是套件唯一的「必需件」，其余插件全部依赖它。

## 它做什么

内核**不认识**任何业务概念（家、传送点、权限组、称号、经济）。它只提供基础设施：

| 能力 | 说明 |
|---|---|
| **插件契约与生命周期** | `Feature` —— 每个功能单元的开关、命令、API 路由、生命周期回调 |
| **跨插件服务注册表** | `VoidkitServices` —— 插件间唯一的通信方式 |
| **数据存储** | 玩家档案（`PlayerProfile`）、全局数据、上下线日志、IP 记录 |
| **文本与变量** | 颜色解析、`%变量%` 系统（`PlaceholderRegistry`） |
| **多语言** | 中文原文为 key 的词表，缺词条自动回退中文 |
| **平台适配层** | `core.platform` —— 收口加载器/版本差异，为移植铺路 |
| **API 登记表** | `ApiRouter` —— 纯数据登记，**内核里没有一行 HTTP 代码** |

## 内置功能

| Feature id | 说明 | 默认 |
|---|---|---|
| `core` | 内核本体（命令 `/vk`、配置、数据落盘） | 开 |
| `player-tracking` | 玩家上下线记录 + IP 历史 | 开 |

## 命令

| 命令 | 说明 |
|---|---|
| `/vk reload` | 重新读取全部配置 |
| `/vk save` | 立即落盘 |
| `/vk token` | 查看管理 API 令牌 |
| `/vk status` | 运行状态 |
| `/vk features` | 列出功能模块清单与开关状态（开关改配置文件或面板） |
| `/vk api` | 管理 API（`remote-api` 模块）监听状态 |
| `/vk placeholders` | 列出全部变量 |

> 主命令是**简写 `/vk`**；全名 **`/voidkit`** 等价（同一棵命令树），脚本里想写清楚就用全名。

## 配置

`voidkit/core/config.json`：

| 字段 | 说明 |
|---|---|
| `language` | `zh_cn` / `en_us` |
| `features` | 模块开关表 |
| `worldNames` | 维度显示名（支持颜色码）—— 见 [配置文件](../guide/config.md#worldnames-给世界起中文名) |
| `recordIps` | 是否记录 IP |
| `maxJoinLogEntries` | 上下线日志条数上限 |
| `autoSaveSeconds` | 兜底自动落盘间隔 |

## 数据目录

```
voidkit/
└── core/
    ├── config.json
    └── data/
        ├── players/    玩家档案（家的位置、余额外的档案信息、当前称号等）
        ├── logs/       上下线日志（JSONL，滚动）
        └── ...
```

## 为什么只有它打包 Kotlin 运行时

内核用 [jarJar](https://github.com/MinecraftForge/jarjar) 把 Kotlin 标准库嵌进自己的 jar，
其余插件共享它。所以插件本体都只有几十 KB，装一堆也不会让 `mods/` 目录膨胀。

## 管理员权限

`voidkit.admin` —— 管理类命令（`/vk` 的管理子命令、管理工具等）的总节点。
没装/关掉权限模块（在 `voidkit_essentials` 里）时，**原版 OP** 自动拥有。
