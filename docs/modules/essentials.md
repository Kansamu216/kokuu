# essentials（基础指令）

```
mods/voidkit_essentials-1.2.1.jar
```

Bukkit / Essentials 那一类「服务器基础指令」的 NeoForge 版：
家、传送点、出生点、玩家互传、管理工具、信息查询，以及原版命令简写。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `homes` | 家（`/sethome` 等） | 开 |
| `warps` | 公共传送点 | 开 |
| `spawn` | 出生点 | 开 |
| `back` | 回到死亡点 / 上次传送前 | 开 |
| `tpa` | 玩家互传 | 开 |
| `teleport` | 管理传送（`/tphere` `/tpo` `/tpall` `/return`） | 开 |
| `admin-tools` | 飞行 / 无敌 / 治疗 / 喂食 / 隐身 / 末影箱 / 工作台 / 查看背包 / 公告 / 禁言 | 开 |
| `info` | 信息查询（`/who` `/list` `/whois` `/seen` `/playtime` `/tps`） | 开 |
| `command-alias` | 原版命令短别名（时间 / 天气 / 规则 / 难度 / 游戏模式） | 开 |

## 命令

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/sethome [名字]` `/home [名字]` `/delhome <名字>` `/homes` | 家 | `voidkit.command.home` |
| `/setwarp <名字>` `/warp <名字>` `/warps` `/delwarp <名字>` | 公共传送点 | 管理端 `voidkit.admin` |
| `/spawn` `/setspawn` | 出生点 | `voidkit.command.spawn` |
| `/back` | 回到死亡点 / 上次传送前 | `voidkit.command.back` |
| `/tpa <玩家>` `/tpahere <玩家>` `/tpaccept` `/tpdeny` `/tpacancel` | 玩家互传 | `voidkit.command.tpa` |
| `/tphere <玩家>` `/tpo <玩家>` `/tpall` `/return` | 管理传送 | `voidkit.admin` |
| `/fly` `/god` `/heal` `/feed` `/speed <v>` `/vanish`(`/v`) `/hat` `/enderchest` `/workbench` `/invsee <玩家>` | 管理工具 | `voidkit.admin` |
| `/broadcast <内容>` `/mute <玩家>` `/unmute <玩家>` | 公告与禁言 | `voidkit.admin` |
| `/who` `/list` `/whois <玩家>` `/seen <玩家>` `/playtime [玩家]` `/tps` | 信息查询 | — |

### 原版命令简写

| 命令 | 权限节点 |
|---|---|
| `/day` `/night` `/noon` `/midnight` `/dawn` `/dusk` `/freeze` `/unfreeze` | `voidkit.command.<名字>` |
| `/sun` `/rain` `/thunder` | `voidkit.command.<名字>` |
| `/gr <规则> [值]` | `voidkit.command.gamerule` |
| `/diff <级别>` | `voidkit.command.difficulty` |
| `/gm <模式> [玩家]` | `voidkit.command.gamemode` |

> 简写是**转发**给原版执行而不是重写 —— 行为、消息本地化、边界处理都与 Mojang 写的一致。

## 权限节点

| 节点 | 说明 |
|---|---|
| `voidkit.admin` | 管理类命令总节点（管理工具、管理传送、warp 管理等） |
| `voidkit.command.home` | 家相关命令 |
| `voidkit.command.spawn` | 出生点 |
| `voidkit.command.back` | `/back` |
| `voidkit.command.tpa` | 玩家互传 |
| `voidkit.command.<简写名>` | 对应命令简写（如 `voidkit.command.day`） |
| `voidkit.command.gamerule` / `.difficulty` / `.gamemode` | 对应的原版接管命令 |
| `voidkit.homes.limit.<N>` | 家的数量上限分级，例如 `voidkit.homes.limit.10` |

## 配置

`voidkit/essentials/config.json`：

| 字段 | 默认 | 说明 |
|---|---|---|
| `defaultHomeLimit` | `3` | 默认家上限 |
| `maxHomes` | `32` | 权限能达到的最高上限 |
| `tpaEnabled` | `true` | 是否允许玩家互传 |
| `tpaTimeoutSeconds` | `60` | TPA 超时 |
| `backEnabled` | `true` | `/back` 是否可用 |
| `teleportCooldownSeconds` | `0` | 传送冷却 |

`voidkit/essentials/commands.json` —— 停用被别的 mod 抢了名字的简写：

```jsonc
{ "disabled": ["day", "sun"] }
```

可用名字：`day night noon midnight dawn dusk freeze unfreeze sun rain thunder gr diff gm`。
