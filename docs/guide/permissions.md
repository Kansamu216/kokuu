# 权限节点参考

Voidkit 的权限分成**两类**，判定方式不同 —— 理解这一点，后面所有节点都不用背。

| 类型 | 判定 | 默认 | 典型 |
|---|---|---|---|
| **玩家命令** | 直接查节点；节点若被登记为「公开」则**默认放行** | 开放 | `/warp` `/spawn` `/home` `/tpa` |
| **管理命令** | `voidkit.admin`（或原版 OP）**或**该命令自己的节点，任一命中即放行 | 关闭 | `/setwarp` `/setspawn` `/tphere` |

## 公开节点：默认开、可关闭

「公开节点」是为「装上就能用」准备的：像 `/warp` `/spawn` 这种玩家日常命令，
如果默认值也是拒绝，服主装完套件还得先把权限配一遍，普通玩家才能回城 —— 这不合理。

所以这些节点被登记为**公开**：

- **零配置下对所有玩家生效**（不装 `voidkit_perm` 也一样，见下）；
- 服主想关掉某个组，在该组加一条**否定节点**即可：

```jsonc
// 例：禁止 vip 组使用 /warp
{ "groups": { "vip": { "permissions": ["-voidkit.command.warp"] } } }
```

> ⚠️ 否定（`-节点`）**优先于**公开默认值 —— 判定顺序是
> 「玩家节点 → 各组按权重 → **公开节点** → 默认值」。
> 所以公开节点只是「没配置时的默认值」，不是「绕过权限」。

### 没装 `voidkit_perm` 时怎么算？

没装权限插件时，内核用「只认原版 OP」的兜底实现，但**公开节点依然放行**。
也就是说：

| 场景 | `/warp`（公开） | `/setwarp`（管理） |
|---|---|---|
| 没装 perm，普通玩家 | ✅ 可用 | ❌ 拒绝 |
| 没装 perm，OP | ✅ | ✅ |
| 装了 perm，默认组 | ✅（可用 `-节点` 关） | ❌ 除非给节点 |
| 装了 perm，带 `voidkit.admin` | ✅ | ✅ |

## 公开节点一览

| 节点 | 命令 | 模块 |
|---|---|---|
| `voidkit.command.home` | `/sethome` `/home` `/delhome` `/homes` | essentials |
| `voidkit.command.warp` | `/warp` `/warps` | essentials |
| `voidkit.command.spawn` | `/spawn` | essentials |
| `voidkit.command.back` | `/back` `/return` | essentials |
| `voidkit.command.tpa` | `/tpa` `/tpahere` `/tpaccept` `/tpdeny` `/tpacancel` | essentials |
| `voidkit.command.info` | `/who` `/seen` `/playtime` `/tps` | essentials |
| `voidkit.command.hat` | `/hat` | essentials |
| `voidkit.command.enderchest` | `/enderchest`（打开自己的） | essentials |
| `voidkit.command.workbench` | `/workbench` | essentials |
| `voidkit.command.title` | `/title` `/title list` `/title set` `/title clear` | title |
| `voidkit.menu.use` | `/menu [名字]`、右键绑定物品 | display |
| `voidkit.economy.balance` | `/balance` `/bal` `/baltop` | economy |
| `voidkit.economy.pay` | `/pay` | economy |
| `voidkit.economy.value` | `/worth` `/value` | economy |
| `voidkit.economy.sell` | `/sell` | economy |
| `voidkit.economy.shop` | `/shop` | economy |
| `voidkit.clearlag.trash` | `/trash` | utility |
| `voidkit.chat.color` | 在聊天里使用 `&` 颜色码 | display |
| `voidkit.chat.placeholder` | 在聊天里使用 `%变量%` | display |

## 管理节点一览

默认**关闭**。给某人 `voidkit.admin` 他会拿到下面**全部**命令；
也可以只发其中某一条（例如只给某组 `/setspawn`）。

### 总节点

| 节点 | 说明 |
|---|---|
| `voidkit.admin` | 管理总节点。核心管理命令 + 所有管理子命令。原版 OP（等级 ≥2）等价 |

### essentials

| 节点 | 命令 |
|---|---|
| `voidkit.command.setwarp` | `/setwarp` `/delwarp` |
| `voidkit.command.setspawn` | `/setspawn` |
| `voidkit.command.teleport` | `/tphere` `/tpo` `/tpall` |
| `voidkit.command.fly` | `/fly`（对自己；对他人另需 `voidkit.admin`） |
| `voidkit.command.god` | `/god` |
| `voidkit.command.heal` | `/heal` |
| `voidkit.command.feed` | `/feed` |
| `voidkit.command.speed` | `/speed` |
| `voidkit.command.vanish` | `/vanish` `/v` |
| `voidkit.command.enderchest` | `/enderchest <玩家>`（看别人） |
| `voidkit.command.invsee` | `/invsee <玩家>` |
| `voidkit.command.broadcast` | `/broadcast` |
| `voidkit.command.mute` | `/mute` `/unmute` |
| `voidkit.command.whois` | `/whois <玩家>`（**会打印 IP 记录**） |
| `voidkit.command.<简写名>` | `/day` `/night` `/sun` `/rain` … |
| `voidkit.command.gamerule` | `/gr` |
| `voidkit.command.difficulty` | `/diff` |
| `voidkit.command.gamemode` | `/gm` |
| `voidkit.homes.limit.<N>` | 家的数量上限，如 `voidkit.homes.limit.10` |

### 其它模块

| 节点 | 命令 / 用途 | 模块 |
|---|---|---|
| `voidkit.announce` | `/announce` | display |
| `voidkit.menu` | `/menu list` `/menu reload` `/menu open <玩家> <菜单>` | display |
| `voidkit.title.custom` | `/title custom <文本>` | title |
| `voidkit.title.vip` / `.builder` / `.admin` | 示例：称号的**拥有条件** | title |
| `voidkit.economy.admin` | `/eco give\|take\|set` | economy |
| `voidkit.clearlag` | `/clearlag` `/clearlag status` `/clearlag test` | utility |
| `voidkit.perf` | `/perf` `/perf chunks` | utility |
| `voidkit.perf.notify` | 接收 TPS/MSPT 超阈值提醒 | utility |

### 独立模块

反作弊与审计的命令**全部是管理命令**（没有面向玩家的命令），且**刻意不并入
`voidkit.admin`** —— 这类权限敏感，应该单独授予，而不是「给了管理总权限就顺带拿到
封禁 / 密钥轮换 / 回滚世界」。

| 节点 | 覆盖 | 模块 |
|---|---|---|
| `voidkit.guard` | `/guard status\|mode\|reload\|whitelist`、`/guard fence export`、`/gscan` `/greport` | [反作弊](../anticheat/) |
| `voidkit.guard.ban` | `/gban` `/gunban` `/gbanlist` `/gcheck` | 反作弊 |
| `voidkit.guard.screen` | `/gscreen` `/greports` | 反作弊 |
| `voidkit.guard.key` | `/guard key rotate\|export`（🚨 **不可逆**：旧客户端立即全失效） | 反作弊 |
| `voidkit.guard.fence` | `/guard fence import` | 反作弊 |
| `voidkit.inspector` | `/inspect` `/inspect here\|lookup\|page\|chain\|status`（**只读**） | [审计](../audit/) |
| `voidkit.inspector.rollback` | `/inspect preview` `/inspect rollback`（会**改世界**） | 审计 |
| `voidkit.inspector.purge` | `/inspect purge <天>`（会**删历史**） | 审计 |

## 常见配置

**只让 VIP 用 `/fly`：**

```jsonc
{ "groups": { "vip": { "permissions": ["voidkit.command.fly"] } } }
```

**禁止默认组使用传送点：**

```jsonc
{ "groups": { "default": { "permissions": ["-voidkit.command.warp"] } } }
```

**管理员组一把梭：**

```jsonc
{ "groups": { "admin": { "permissions": ["*"] } } }
```

更多权限语法（通配、权重、临时权限）见 [权限模块](../modules/perm.md)。
