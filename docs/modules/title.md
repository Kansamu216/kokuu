# title（称号）

> 📦 **已包含在 `voidkit_display` 里** —— 装
> `mods/voidkit_display-0.1.0-beta.1+mc1.21.1-neoforge.jar` 即获得本模块，
> **不需要**（也没有）单独的 `voidkit_title` jar。本页保留，因为称号的 feature、
> 权限节点与配置都没变；数据目录也仍是 `voidkit/title/`（见下）。

给玩家佩戴的**称号**，带游戏内 GUI 选择界面，并支持玩家自定义称号。
称号可以显示在聊天栏（`%title%`）里。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `titles` | 称号系统 + GUI | 开 |

## 设计要点

「谁**拥有**哪个称号」**不存在配置里** —— 直接复用[权限系统](./perm.md)的节点，
避免两份名单对不上。

| 概念 | 存在哪 |
|---|---|
| 有哪些称号、长什么样 | `titles.json` 的 `titles` |
| 谁有资格佩戴 | 权限节点（称号的 `permission` 字段） |
| 谁**当前戴着**哪个 | 玩家档案（`/title set` 写入） |

给某个玩家发称号：

```
/perm user <玩家> add voidkit.title.vip
```

## 配置

`voidkit/title/titles.json`：

```jsonc
{
  "titles": {
    "newbie": { "display": "&7[萌新]", "description": "刚上路", "permission": "", "weight": 0, "icon": "minecraft:wooden_sword" },
    "vip":    { "display": "&6[VIP]",  "description": "VIP 专属", "permission": "voidkit.title.vip", "weight": 10, "icon": "minecraft:gold_ingot" },
    "builder":{ "display": "&b[建筑师]", "permission": "voidkit.title.builder", "weight": 20, "icon": "minecraft:bricks" },
    "admin":  { "display": "&c[管理团队]", "permission": "voidkit.title.admin", "weight": 100, "icon": "minecraft:command_block" }
  },
  "custom": {
    "enabled": true,
    "permission": "voidkit.title.custom",
    "maxLength": 12,
    "allowColors": false,
    "colorCode": "&f"
  }
}
```

| 称号字段 | 说明 |
|---|---|
| `display` | 显示文本，支持 `&` 颜色码 |
| `description` | 悬停在 GUI 图标上的说明 |
| `permission` | 拥有条件；**留空 = 人人可用** |
| `weight` | 排序权重，越大越靠前 |
| `icon` | GUI 图标（物品 id） |

### 自定义称号

让玩家用 `/title custom <文本>` 自己写称号。防滥用：

| `custom` 字段 | 说明 |
|---|---|
| `enabled` | 是否启用 |
| `permission` | 需要的权限节点 |
| `maxLength` | 显示宽度上限（中文/全角/emoji 算 2 格） |
| `allowedPattern` | 纯文本允许的字符正则（去掉颜色码后校验） |
| `allowColors` | 是否允许玩家带颜色码（**默认禁止**，防止伪装管理员颜色） |
| `colorCode` | 展示时统一套的颜色码 |

## 命令

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/title` | 打开称号 GUI | `voidkit.command.title` | 🟢 |
| `/title list` | 列出全部称号 | 同上 | 🟢 |
| `/title set <id>` | 佩戴称号（还需**拥有**该称号） | 同上 | 🟢 |
| `/title clear` | 清空自己的称号 | 同上 | 🟢 |
| `/title custom <文本>` | 自定义称号 | `voidkit.title.custom` | 🔴 |

> 称号的**拥有条件**由 `titles.json` 里每个称号自己的 `permission` 字段决定
> （留空 = 人人可用），不在命令这一层。

## 权限节点

| 节点 | 说明 | 默认 |
|---|---|---|
| `voidkit.command.title` | 打开 GUI / 佩戴 / 查看称号 | 🟢 公开 |
| `voidkit.title.custom` | 使用自定义称号 | 🔴 |
| `voidkit.title.vip` / `.builder` / `.admin` | 示例称号的**拥有条件**（在 `titles.json` 里配，可自定义） | — |
