# 命令速查

下表按模块分组。**没有权限插件时**，所有需要权限的命令只认**原版 OP**（`/op`）；
装了 [`voidkit_perm`](../modules/perm.md) 后按权限节点判定。

## 内核（`voidkit_core`）

| 命令 | 说明 |
|---|---|
| `/voidkit reload` | 重新读取全部配置 |
| `/voidkit save` | 立即把所有数据落盘 |
| `/voidkit token` | 查看管理 API 令牌 |
| `/voidkit status` | 运行状态总览 |
| `/voidkit features [id]` | 查看 / 切换功能模块开关 |
| `/voidkit api` | 查看已注册的 API 路由 |
| `/voidkit placeholders` | 列出全部可用变量 |

## essentials（传送 / 家 / 管理工具）

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/sethome [名字]` `/home [名字]` `/delhome <名字>` `/homes` | 家 | `voidkit.command.home` |
| `/setwarp <名字>` `/warp <名字>` `/warps` `/delwarp <名字>` | 公共传送点 | 管理端 `voidkit.admin` |
| `/spawn` `/setspawn` | 出生点 | `voidkit.command.spawn` |
| `/back` | 回到死亡点 / 上次传送前 | `voidkit.command.back` |
| `/tpa <玩家>` `/tpahere <玩家>` `/tpaccept` `/tpdeny` `/tpacancel` | 玩家互传（请求自带可点按钮） | `voidkit.command.tpa` |
| `/tphere <玩家>` `/tpo <玩家>` `/tpall` `/return` | 管理传送 | `voidkit.admin` |
| `/fly` `/god` `/heal` `/feed` `/speed <v>` `/vanish`(`/v`) `/hat` `/enderchest` `/workbench` `/invsee <玩家>` | 管理工具 | `voidkit.admin` |
| `/broadcast <内容>` `/mute <玩家>` `/unmute <玩家>` | 公告与禁言 | `voidkit.admin` |
| `/who` `/list` `/whois <玩家>` `/seen <玩家>` `/playtime [玩家]` `/tps` | 信息查询 | — |

**家数量上限**用权限节点分级：`voidkit.homes.limit.<N>`，例如给 VIP 组
`voidkit.homes.limit.10`。

### 原版命令简写（`command-alias`）

把常用的原版命令做成短别名，**转发**给原版执行而不是重写 ——
行为、消息本地化、边界处理都与 Mojang 写的一致。

| 命令 | 权限节点 |
|---|---|
| `/day` `/night` `/noon` `/midnight` `/dawn` `/dusk` `/freeze` `/unfreeze` | `voidkit.command.<名字>` |
| `/sun` `/rain` `/thunder` | `voidkit.command.<名字>` |
| `/gr <规则> [值]` | `voidkit.command.gamerule` |
| `/diff <级别>` | `voidkit.command.difficulty` |
| `/gm <模式> [玩家]` | `voidkit.command.gamemode` |

> 💡 每条简写单独做**冲突保护**：名字被别的 mod 占了只会跳过这一条，不影响其余注册
> （165 个 mod 的整合包里 `/day` 这种短名字很容易撞）。想主动让出某条，见
> [`essentials/commands.json`](./config.md#essentialsconfigjson)。

## display（展示 / 公告 / 菜单）

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/announce <内容>` | 立即发一条公告 | `voidkit.announce` |
| `/announce auto on\|off` | 开关定时轮播 | `voidkit.announce` |
| `/announce list` | 查看公告配置 | `voidkit.announce` |
| `/menu [名字]` | 打开菜单 | `voidkit.menu.use` |
| `/menu list` | 列出全部菜单 | `voidkit.menu` |
| `/menu open <玩家> <菜单>` | 给指定玩家打开菜单 | `voidkit.menu` |

## perm（权限）

| 命令 | 说明 |
|---|---|
| `/perm` | 权限管理主命令，游戏内可完成组/节点的增删改查 |

常见用法（示意）：

```
/perm group vip add voidkit.command.fly
/perm user <玩家> group vip
/perm user <玩家> add voidkit.title.vip
/perm user <玩家> add -voidkit.command.back      # 否定节点
```

详见 [权限模块](../modules/perm.md)。

## title（称号）

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/title` | 打开称号 GUI（玩家选称号） | — |
| `/title set <id>` | 设置自己的称号 | 拥有该称号的节点 |
| `/title clear` | 清空自己的称号 | — |
| `/title list` | 列出全部称号 | — |
| `/title custom <文本>` | 自定义称号 | `voidkit.title.custom` |
| `/title admin ...` | 管理操作（发放 / 回收） | `voidkit.title.admin` |

## economy（经济）

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/balance`(`/bal`) [玩家] | 查余额 | `voidkit.economy.balance` / `.balance.others` |
| `/pay <玩家> <金额>` | 转账 | `voidkit.economy.pay` |
| `/baltop` | 财富排行 | — |
| `/worth [数量]` / `/value` | 查看手上物品的估价 | `voidkit.economy.value` |
| `/sell`(`/sell hand` `/sell all`) | 卖掉手上物品 / 全部可卖物 | `voidkit.economy.sell` |
| `/shop` | 打开商店 | `voidkit.economy.shop` |
| `/eco give\|take\|set <玩家> <金额>` | 管理端增减 | `voidkit.economy.admin` |

## utility（清扫 / 性能）

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/clearlag` | 手动触发一次清扫 | `voidkit.clearlag` |
| `/clearlag status` | 查看清扫与垃圾桶状态 | `voidkit.clearlag` |
| `/clearlag test` | 发一条测试播报（用来确认通道配置） | `voidkit.clearlag` |
| `/trash` | **打开垃圾桶**，取回被清扫的掉落物 | `voidkit.clearlag.trash` |
| `/perf` | 性能报告 | `voidkit.perf` |
| `/perf chunks` | 区块实体热点排名 | `voidkit.perf` |

## web（管理 API）

`voidkit_web` 不注册游戏内命令，只在 `127.0.0.1:8765` 起一个 HTTP 服务，
配合 [管理平台](../panel/) 使用。令牌用 `/voidkit token` 查看。

## 反作弊（`voidkit_guard`）

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/guard status\|mode\|reload\|key\|whitelist` | 总控 | `voidkit.guard` |
| `/gban` `/gunban` `/gbanlist` `/gcheck` | 封禁管理 | `voidkit.guard.ban` |
| `/gscreen` `/greports` | 截屏与报告 | `voidkit.guard.screen` |
| `/gscan` `/greport` | 深度扫描与判定报告 | `voidkit.guard` |

详见 [反作弊模块](../anticheat/)。

## 审计（`voidkit_inspector`）

| 命令 | 说明 |
|---|---|
| `/inspect`（别名 `/coi` `/ins`） | 切换检查模式，点击方块查看历史 |
| `/inspect lookup u:玩家 r:半径 t:1h a:place b:石头` | 参数查询，结果弹箱子 GUI |
| `/inspect here` | 查看准星对准方块的历史 |
| `/inspect page` | 翻页 |
| `/inspect status` | 数据库状态 |
| `/inspect purge <天>` | 清理 N 天前的记录 |

详见 [审计模块](../audit/)。
