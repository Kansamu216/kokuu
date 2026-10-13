# display（展示层）

```
mods/voidkit_display-1.2.1.jar
```

服务器**门面**：MOTD、TAB 玩家列表、侧边栏记分牌、聊天栏格式、公告、菜单。
全部由配置文件驱动，改完 `/voidkit reload` 生效。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `motd` | 服务器列表 MOTD（可配多套轮换） | 开 |
| `tab` | TAB 顶栏 / 底栏 | 开 |
| `scoreboard` | 侧边栏记分牌 | 开 |
| `chat-format` | 聊天栏格式、悬停浮层、点击动作 | 开 |
| `announce` | 公告与定时轮播 | 开 |
| `menu` | 多级菜单（钟表菜单） | 开 |

## MOTD

`voidkit/display/motd.json`。支持多套 + 轮换策略：

```jsonc
{
  "enabled": true,
  "rotation": "random",          // fixed / order / random
  "variants": [
    { "lines": ["&a&l我的服务器", "&7欢迎回来"], "weight": 1 }
  ],
  "writeServerProperties": false // 默认不改写 server.properties
}
```

| 字段 | 说明 |
|---|---|
| `rotation` | `fixed` 固定第一套 / `order` 顺序轮换 / `random` 按 `weight` 加权随机 |
| `writeServerProperties` | 是否把当前 MOTD 也写回 `server.properties`（**默认 false**） |
| `variants[].lines` | 最多两行（原版客户端限制）；MOTD **不支持点击事件** |

## TAB

`voidkit/display/tab.json`：

| 字段 | 说明 |
|---|---|
| `header` / `footer` | 顶栏 / 底栏，可多行，支持颜色码、`%变量%`、`[文字](动作)` 可点击 |
| `updateSeconds` | 刷新间隔 |
| `useGroupPrefix` | 是否把权限组前缀显示到玩家名上（按组统一，用原版队伍机制） |

示例底栏放一排按钮：

```jsonc
"footer": ["&8[&a回城&8](run:/spawn \"&7点一下回到出生点\")  &8[&b主菜单&8](run:/menu)"]
```

## 记分牌

`voidkit/display/scoreboard.json`。可配**多套看板**，按权限 + 优先级选择：

```jsonc
"boards": [
  {
    "id": "default",
    "title": "&b&lVOIDKIT",
    "lines": [
      "&7&m────────",
      "&f玩家 &a%player%",
      "&f在线 &a%online%&7/&a%max%",
      "&f延迟 &a%ping%ms",
      "&fTPS &a%tps%",
      "&7&m────────"
    ],
    "permission": "",     // 留空 = 所有玩家可见
    "priority": 0
  }
]
```

- 每个玩家的侧边栏是**独立**的，所以 `%ping%` 这类变量互不干扰。
- 最多 15 行（原版限制）；空行要写 `""`，留空会被客户端折叠。
- ⚠️ 记分牌的行**不支持点击事件**。

## 聊天栏

`voidkit/display/chat.json` 的核心是 `format` 一行：

```jsonc
"format": "&8[&6%group%&8]&7[&aLv%level%&7] %title%&b{name} &8» &f{message}"
```

| 字段 | 说明 |
|---|---|
| `format` | 整条消息模板（`{name}` `{message}` 是专用 token） |
| `colorEnabled` / `colorPermission` | 谁能在发言里打 `&` 颜色码 |
| `placeholderPermission` | 谁能在聊天里用 `%变量%` |
| `blockMuted` | 被禁言玩家发言是否直接拦下 |
| `nameHover` | 鼠标悬停玩家名的浮层（每项一行，支持颜色与变量） |
| `nameClick` | 点击玩家名执行的动作，如 `run:/tpa {name}` |

> 名字的悬停浮层里**做不到显示玩家皮肤头像** —— 原版协议里浮层只能是文字。

## 公告

`voidkit/display/announce.json` —— 配置公告框线、通道与定时轮播。
用 `/announce auto on|off` 开关轮播，`/announce list` 查看配置。

## 菜单

`voidkit/display/menu.json` —— Spigot「钟表菜单」那一类多级菜单：
每个菜单定义物品图标与点击动作，可以嵌套子菜单。用 `/menu` 打开。

## 命令

| 命令 | 权限节点 |
|---|---|
| `/announce <内容>` / `/announce auto on\|off` / `/announce list` | `voidkit.announce` |
| `/menu [名字]` | `voidkit.menu.use` |
| `/menu list` / `/menu open <玩家> <菜单>` | `voidkit.menu` |

## 权限节点

| 节点 | 说明 |
|---|---|
| `voidkit.announce` | 发布 / 管理公告 |
| `voidkit.menu` | 管理菜单（list / open 他人） |
| `voidkit.menu.use` | 使用菜单 |
| `voidkit.chat.color` | 允许在聊天里用颜色码 |
| `voidkit.chat.placeholder` | 允许在聊天里用 `%变量%` |
