# 命令速查

## 权限怎么看

命令分两类，**「用」和「管」分开**：

| 类型 | 说明 | 默认 |
|---|---|---|
| 🟢 **玩家命令** | 日常功能（回城、传送、查余额…） | **所有玩家可用**，可用 `-节点` 关闭 |
| 🔴 **管理命令** | 修改服务器 / 操作别人 | 仅 OP 或 `voidkit.admin` |

> 下表每行的「默认」列就是这个含义。完整节点说明见
> **[权限节点参考](./permissions.md)**。

没装 [`voidkit_perm`](../modules/perm.md) 时，🔴 管理命令只认**原版 OP**（`/op`）；
🟢 玩家命令不受影响，普通玩家照常能用。

---

## 内核（`voidkit_core`）

| 命令 | 说明 | 默认 |
|---|---|---|
| `/vk` | 管理主命令（见下） | 🔴 |
| `/vk status` | 服务器与内核概况 | 🔴 |
| `/vk reload` | 重载配置并热切换模块 | 🔴 |
| `/vk save` | 立刻落盘玩家数据 | 🔴 |
| `/vk token` | 查看管理 API 令牌 | 🔴 |
| `/vk features [id]` | 模块清单 / 开关 | 🔴 |
| `/vk api` | 管理 API 监听状态 | 🔴 |
| `/vk placeholders` | 可用变量清单 | 🔴 |
| `/vk group <玩家> <组>` | 设置玩家权限组 | 🔴 |
| `/vk perm add\|del <玩家> <节点>` | 增删玩家权限节点 | 🔴 |

> 主命令是**简写 `/vk`**，全名 **`/voidkit`** 等价（同一棵命令树），
> 脚本 / 控制台里想写清楚就用全名。

## essentials（传送 / 家 / 管理工具）

### 家 · 传送 · 回城

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/sethome [名字]` | 设置家 | `voidkit.command.home` | 🟢 |
| `/home [名字]` | 回家 | 同上 | 🟢 |
| `/delhome <名字>` | 删除家 | 同上 | 🟢 |
| `/homes` | 列出我的家 | 同上 | 🟢 |
| `/warps` `/warp <名字>` | 查看 / 前往传送点 | `voidkit.command.warp` | 🟢 |
| `/spawn` | 回到出生点 | `voidkit.command.spawn` | 🟢 |
| `/back` `/return` | 回到死亡点 / 上次传送前 | `voidkit.command.back` | 🟢 |
| `/tpa <玩家>` `/tpahere <玩家>` | 请求传送到对方 / 让对方过来 | `voidkit.command.tpa` | 🟢 |
| `/tpaccept` `/tpdeny` `/tpacancel` | 接受 / 拒绝 / 取消请求 | 同上 | 🟢 |

> **家的数量上限**用 `voidkit.homes.limit.<N>` 分级，例如给 VIP 组
> `voidkit.homes.limit.10`。

### 传送点与出生点的管理

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/setwarp <名字>` | 在当前位置建传送点 | `voidkit.command.setwarp` | 🔴 |
| `/delwarp <名字>` | 删除传送点 | 同上 | 🔴 |
| `/setspawn` | 把全服出生点设到当前位置 | `voidkit.command.setspawn` | 🔴 |

> ⚠️ 这两组是**刻意分开**的：`/warp` 是玩家日常操作，`/setwarp` 是管理操作。
> 合成一个节点就会出现「要么人人能删传送点，要么人人不能用传送点」的两难。

### 管理工具

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/fly` `/god` `/heal` `/feed` | 开关飞行 / 无敌 / 治疗 / 喂食（对自己） | `voidkit.command.fly` 等 | 🔴 |
| `/fly <玩家>` 等 | **对别人**动手 | 必须 `voidkit.admin` | 🔴 |
| `/speed <值> [玩家]` | 移动速度 | `voidkit.command.speed` | 🔴 |
| `/vanish`(`/v`) | 隐身 | `voidkit.command.vanish` | 🔴 |
| `/tphere <玩家>` `/tpo <玩家>` `/tpall` | 把人拉过来 / 传送到某人 / 全服拉人 | `voidkit.command.teleport` | 🔴 |
| `/invsee <玩家>` | 查看物品栏 | `voidkit.command.invsee` | 🔴 |
| `/broadcast <内容>` | 全服公告 | `voidkit.command.broadcast` | 🔴 |
| `/mute <玩家> [分钟] [原因]` `/unmute <玩家>` | 禁言 / 解除 | `voidkit.command.mute` | 🔴 |

### 玩家自助工具

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/hat` | 把手上的物品戴到头上 | `voidkit.command.hat` | 🟢 |
| `/enderchest` | 打开自己的末影箱 | `voidkit.command.enderchest` | 🟢 |
| `/enderchest <玩家>` | 打开**别人**的末影箱 | `voidkit.command.enderchest` | 🔴 |
| `/workbench` | 打开工作台 | `voidkit.command.workbench` | 🟢 |

### 信息查询

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/who` | 在线列表 | `voidkit.command.info` | 🟢 |
| `/seen <玩家>` | 最后在线时间 | 同上 | 🟢 |
| `/playtime [玩家]` | 游戏时长 | 同上 | 🟢 |
| `/tps` | 服务器 TPS | 同上 | 🟢 |
| `/whois <玩家>` | 玩家详情 | `voidkit.command.whois` | 🔴 |

> ⚠️ `/whois` 会打印该玩家的 **IP 记录**，所以是管理命令。
> `/list` 用的是**原版**的（本套件不重定义它）。

### 原版命令简写（`command-alias`）

| 命令 | 节点 | 默认 |
|---|---|---|
| `/day` `/night` `/noon` `/midnight` `/dawn` `/dusk` `/freeze` `/unfreeze` | `voidkit.command.<名字>` | 🔴 |
| `/sun` `/rain` `/thunder` | `voidkit.command.<名字>` | 🔴 |
| `/gr <规则> [值]` | `voidkit.command.gamerule` | 🔴 |
| `/diff <级别>` | `voidkit.command.difficulty` | 🔴 |
| `/gm <模式> [玩家]` | `voidkit.command.gamemode` | 🔴 |

> 简写是**转发**给原版执行而不是重写 —— 行为、消息本地化、边界处理都与 Mojang 写的一致。
> 每条单独做冲突保护：名字被别的 mod 占了只跳过这一条。
> 想主动让出某条，见 [`essentials/commands.json`](./config.md#essentialsconfigjson)。

## display（展示 / 公告 / 菜单）

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/menu` `/menu <名字>` | 打开菜单（含手持绑定物品右键） | `voidkit.menu.use` | 🟢 |
| `/menu list` | 列出全部菜单 | `voidkit.menu` | 🔴 |
| `/menu reload` | 重载菜单配置 | `voidkit.menu` | 🔴 |
| `/menu open <玩家> <菜单>` | 给指定玩家打开菜单 | `voidkit.menu` | 🔴 |
| `/announce <内容>` | 立即发一条公告 | `voidkit.announce` | 🔴 |
| `/announce auto on\|off` | 开关定时轮播 | `voidkit.announce` | 🔴 |
| `/announce list` | 查看公告配置 | `voidkit.announce` | 🔴 |

## perm（权限）

| 命令 | 说明 | 默认 |
|---|---|---|
| `/perm` | 权限管理主命令（组 / 玩家 / 节点） | 🔴 |

常见用法：

```
/perm group vip add voidkit.command.fly      # 给组加节点
/perm user <玩家> group vip                  # 设主组
/perm user <玩家> add voidkit.title.vip      # 玩家级节点
/perm user <玩家> add -voidkit.command.back  # 否定节点
```

## title（称号）

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/title` | 打开称号 GUI | `voidkit.command.title` | 🟢 |
| `/title list` | 列出全部称号 | 同上 | 🟢 |
| `/title set <id>` | 佩戴称号 | 同上（还需拥有该称号） | 🟢 |
| `/title clear` | 清空称号 | 同上 | 🟢 |
| `/title custom <文本>` | 自定义称号 | `voidkit.title.custom` | 🔴 |

## economy（经济）

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/balance`(`/bal`) [玩家] | 查余额 | `voidkit.economy.balance` | 🟢 |
| `/baltop` | 财富排行 | 同上 | 🟢 |
| `/pay <玩家> <金额>` | 转账 | `voidkit.economy.pay` | 🟢 |
| `/worth [数量]` `/value` | 手上物品估价 | `voidkit.economy.value` | 🟢 |
| `/sell`（`/sell hand` `/sell all`） | 回收 | `voidkit.economy.sell` | 🟢 |
| `/shop` | 打开商店 | `voidkit.economy.shop` | 🟢 |
| `/eco give\|take\|set <玩家> <金额>` | 管理端增减 | `voidkit.economy.admin` | 🔴 |

## utility（清扫 / 性能）

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/trash` | 打开垃圾桶（取回被清掉的掉落物） | `voidkit.clearlag.trash` | 🟢 |
| `/clearlag` | 立刻清扫一次 | `voidkit.clearlag` | 🔴 |
| `/clearlag status` | 查看清扫与垃圾桶状态 | 同上 | 🔴 |
| `/clearlag test` | 发一条测试播报 | 同上 | 🔴 |
| `/perf` | 性能报告 | `voidkit.perf` | 🔴 |
| `/perf chunks` | 区块实体热点排名 | 同上 | 🔴 |

## web（管理 API）

不注册游戏内命令，只在 `127.0.0.1:8765` 起 HTTP 服务。
令牌用 `/vk token` 查看。见 [管理平台](../panel/)。

## 独立模块

| 命令 | 模块 | 文档 |
|---|---|---|
| `/guard` `/gban` `/gscreen` `/gscan` `/greport` … | 反作弊 | [VoidkitGuard](../anticheat/) |
| `/inspect`（`/coi` `/ins`） | 审计 | [VoidkitInspector](../audit/) |
