# essentials（基础指令）

```
mods/voidkit_essentials-1.0.0+mc1.21.1-neoforge.jar
```

Bukkit / Essentials 那一类「服务器基础指令」的 NeoForge 版：
家、传送点、出生点、玩家互传、管理工具、信息查询，以及原版命令简写。

> 📦 本 jar 还**内含权限（[perm](./perm.md)）与经济（[economy](./economy.md)）**两个模块 ——
> 它们的源码已并入本 jar，不再单独发布。功能与配置都没变；不需要的话，在 `features`
> 里把 `permissions` / `economy` 开关关掉即可（见 [/vk features](../guide/commands.md)）。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `homes` | 家（`/sethome` 等） | 开 |
| `warps` | 公共传送点 | 开 |
| `spawn` | 出生点 | 开 |
| `back` | 回到死亡点 / 上次传送前 | 开 |
| `tpa` | 玩家互传 | 开 |
| `teleport` | 管理传送（`/tphere` `/tpo` `/tpall`） | 开 |
| `admin-tools` | 飞行 / 无敌 / 治疗 / 喂食 / 隐身 / 末影箱 / 工作台 / 查看背包 / 公告 / 禁言 | 开 |
| `info` | 信息查询（`/who` `/whois` `/seen` `/playtime` `/tps`） | 开 |
| `command-alias` | 原版命令短别名（时间 / 天气 / 规则 / 难度 / 游戏模式） | 开 |
| `permissions` | 权限系统本体（原 perm 模块，见 [perm](./perm.md)） | 开 |
| `economy` | 经济本体（原 economy 模块，见 [economy](./economy.md)） | 开 |

## 命令

🟢 = 玩家命令（默认所有玩家可用，可用 `-节点` 关闭）｜🔴 = 管理命令（默认仅 OP / `voidkit.admin`）

### 家 · 传送 · 回城（🟢）

| 命令 | 说明 | 节点 |
|---|---|---|
| `/sethome [名字]` `/home [名字]` `/delhome <名字>` `/homes` | 家 | `voidkit.command.home` |
| `/warps` `/warp <名字>` | 查看 / 前往传送点 | `voidkit.command.warp` |
| `/spawn` | 回到出生点 | `voidkit.command.spawn` |
| `/back` `/return` | 回到死亡点 / 上次传送前 | `voidkit.command.back` |
| `/tpa <玩家>` `/tpahere <玩家>` `/tpaccept` `/tpdeny` `/tpacancel` | 玩家互传（请求自带可点按钮） | `voidkit.command.tpa` |

### 管理操作（🔴）

| 命令 | 说明 | 节点 |
|---|---|---|
| `/setwarp <名字>` `/delwarp <名字>` | 建 / 删传送点 | `voidkit.command.setwarp` |
| `/setspawn` | 设置全服出生点 | `voidkit.command.setspawn` |
| `/tphere <玩家>` `/tpo <玩家>` `/tpall` | 把人拉过来 / 传送到某人 / 全服拉人 | `voidkit.command.teleport` |
| `/fly` `/god` `/heal` `/feed` | 对自己开关（对**别人**需 `voidkit.admin`） | `voidkit.command.fly` 等 |
| `/speed <值> [玩家]` | 移动速度 | `voidkit.command.speed` |
| `/vanish`(`/v`) | 隐身 | `voidkit.command.vanish` |
| `/invsee <玩家>` | 查看物品栏 | `voidkit.command.invsee` |
| `/broadcast <内容>` | 全服公告 | `voidkit.command.broadcast` |
| `/mute <玩家> [分钟] [原因]` `/unmute <玩家>` | 禁言 / 解除 | `voidkit.command.mute` |
| `/whois <玩家>` | 玩家详情（**含 IP 记录**） | `voidkit.command.whois` |

### 玩家自助工具（🟢）

| 命令 | 说明 | 节点 |
|---|---|---|
| `/hat` | 把手上的物品戴到头上 | `voidkit.command.hat` |
| `/enderchest` | 打开自己的末影箱（看**别人**需 `voidkit.admin`） | `voidkit.command.enderchest` |
| `/workbench` | 打开工作台 | `voidkit.command.workbench` |

### 信息查询

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/who` `/seen <玩家>` `/playtime [玩家]` `/tps` | 在线 / 最后在线 / 时长 / TPS | `voidkit.command.info` | 🟢 |
| `/whois <玩家>` | 玩家详情（含 IP） | `voidkit.command.whois` | 🔴 |

> ⚠️ `/list` 用的是**原版**的 —— 本套件刻意不重定义它（重定义既会冲突，
> 又会把人人可用的原版命令变成需要权限）。

### 原版命令简写（🔴）

| 命令 | 节点 |
|---|---|
| `/day` `/night` `/noon` `/midnight` `/dawn` `/dusk` `/freeze` `/unfreeze` | `voidkit.command.<名字>` |
| `/sun` `/rain` `/thunder` | `voidkit.command.<名字>` |
| `/gr <规则> [值]` | `voidkit.command.gamerule` |
| `/diff <级别>` | `voidkit.command.difficulty` |
| `/gm <模式> [玩家]` | `voidkit.command.gamemode` |

> 简写是**转发**给原版执行而不是重写 —— 行为、消息本地化、边界处理都与 Mojang 写的一致。

## 权限节点

| 节点 | 说明 | 默认 |
|---|---|---|
| `voidkit.admin` | 管理总节点（管理工具、管理传送、warp / spawn 管理等） | 🔴 |
| `voidkit.command.home` | 家 | 🟢 公开 |
| `voidkit.command.warp` | `/warp` `/warps` | 🟢 公开 |
| `voidkit.command.spawn` | `/spawn` | 🟢 公开 |
| `voidkit.command.back` | `/back` `/return` | 🟢 公开 |
| `voidkit.command.tpa` | 玩家互传 | 🟢 公开 |
| `voidkit.command.info` | `/who` `/seen` `/playtime` `/tps` | 🟢 公开 |
| `voidkit.command.hat` / `.enderchest` / `.workbench` | 玩家自助工具 | 🟢 公开 |
| `voidkit.command.setwarp` | 建 / 删传送点 | 🔴 |
| `voidkit.command.setspawn` | 设置出生点 | 🔴 |
| `voidkit.command.teleport` | 管理传送 | 🔴 |
| `voidkit.command.fly` / `.god` / `.heal` / `.feed` / `.speed` / `.vanish` | 管理工具（可单独授权给某组） | 🔴 |
| `voidkit.command.invsee` / `.broadcast` / `.mute` / `.whois` | 管理工具 | 🔴 |
| `voidkit.command.<简写名>` | 原版命令简写 | 🔴 |
| `voidkit.homes.limit.<N>` | 家的数量上限，如 `voidkit.homes.limit.10` | — |

> 「🟢 公开」的含义与关闭方式见 [权限节点参考](../guide/permissions.md)。

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
