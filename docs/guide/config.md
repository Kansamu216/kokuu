# 配置文件

首次启动后，`voidkit/` 会出现在**服务端根目录**下：

```
voidkit/
├── core/
│   ├── config.json          内核：语言 / 模块开关 / 世界名 / 追踪 / 落盘间隔
│   └── data/                玩家档案、上下线日志
├── display/
│   ├── motd.json            MOTD（可配多套轮换）
│   ├── tab.json             TAB 顶栏 / 底栏
│   ├── scoreboard.json      记分牌（可按权限切换多套）
│   ├── chat.json            聊天栏格式、悬停浮层、点击动作
│   ├── announce.json        公告框线 / 通道 / 轮播
│   └── menu.json            菜单与物品绑定
├── perm/
│   ├── groups.json          权限组树
│   ├── users.json           玩家级覆盖
│   └── settings.json        全局设置
├── title/
│   └── titles.json          称号定义
├── essentials/
│   ├── config.json          家上限 / TPA / back / 传送冷却
│   └── commands.json        要停用的命令简写
├── economy/
│   ├── config.json          货币名 / 初始余额 / 转账手续费
│   ├── values.json          物品估值表（/sell、/worth）
│   └── accounts.json        账户余额
├── utility/
│   ├── clearlag.json        清扫间隔 / 通道 / 垃圾桶规则
│   └── perf.json            TPS·MSPT 阈值 / 区块热点 / 干预开关
└── web/
    └── config.json          管理 API 监听地址与令牌
```

**所有文件都带中文注释**，打开即可改。**配置文件按插件分家** —— 一份插件的配置可以整体备份、整体交接。

> 📌 目录里的 `perm/` `title/` `economy/` 虽然已不再是独立 jar（源码并入 `voidkit_essentials` /
> `voidkit_display`），但**它们的配置与数据目录刻意保持不变** —— 升级到合并版后这些文件原地不动，
> 不需要搬迁。

> 改完配置执行 `/vk reload` 生效。
>
> `reload` 会**重新读盘**的范围：内核配置、显示层（MOTD / TAB / 记分牌 / 聊天）、公告、菜单、
> 称号、传送与家、清扫与性能、经济（开关与价格表）、权限数据（组 / 用户 / 设置）、管理接口。
> 命令回执里也会列出这份清单。
>
> 以下**不随 reload 变更**，属有意为之：
>
> 1. **玩家余额**（`economy/accounts.json`）—— 运行数据，用游戏内 `/eco` 或网页面板改。
> 2. **管理接口的 `bind` / `port`** —— 监听套接字已经绑好，改这两项要重启服务端
>    （`token` 与限流是每请求实时读的，`reload` 后立即生效）。
> 3. 其它注释里明确写「仅启动期读取」的项。

---

## core/config.json

| 字段 | 说明 |
|---|---|
| `language` | 界面语言 `zh_cn`（默认）/ `en_us`，词表在 `voidkit/lang/` |
| `features` | 模块开关。键是模块 id，值 `false` 表示停用 |
| `worldNames` | 维度显示名（`%world%` 变量用），支持 `&` 颜色码 —— 见下 |
| `recordIps` | 是否记录玩家 IP 历史 |
| `maxJoinLogEntries` | 上下线日志最多保留多少条 |
| `autoSaveSeconds` | 玩家档案自动落盘的兜底间隔（秒） |

### worldNames：给世界起中文名

```jsonc
"worldNames": {
  // 完整维度 ID 或只写路径都可以；值支持 & 颜色码
  "minecraft:overworld": "&a主世界",
  "minecraft:the_nether": "&c下界",
  "minecraft:the_end": "&5末地",
  // 模组维度照写自己的 ID
  "twilightforest:twilight_forest": "&d暮色森林"
}
```

- 匹配顺序：**完整 ID → 路径简写（`overworld`）→ 维度路径原文**。
- 没配到的维度回退显示路径原文，不会留空或显示 `%world%`。
- 空 `{}` 合法，表示全部按路径原文显示。

---

## essentials/config.json

| 字段 | 默认 | 说明 |
|---|---|---|
| `defaultHomeLimit` | `3` | 默认家的数量上限 |
| `maxHomes` | `32` | 权限能给到的最高上限 |
| `tpaEnabled` | `true` | 是否允许玩家互传 |
| `tpaTimeoutSeconds` | `60` | TPA 请求超时 |
| `backEnabled` | `true` | `/back` 是否可用 |
| `teleportCooldownSeconds` | `0` | 传送冷却（秒） |

家上限的分级：用权限节点 `voidkit.homes.limit.<N>`（例如 `voidkit.homes.limit.10`）
给不同组不同上限。

`commands.json` 用来**停用**某个命令简写（整合包里被别的 mod 抢了名字时）：

```jsonc
{ "disabled": ["day", "sun"] }
```

可用名字：`day night noon midnight dawn dusk freeze unfreeze sun rain thunder gr diff gm`。

---

## display/chat.json

聊天栏的核心是 `format` 一行：

```jsonc
"format": "&8[&6%group%&8]&7[&aLv%level%&7] %title%&b{name} &8» &f{message}"
```

| 字段 | 说明 |
|---|---|
| `format` | 整条消息的模板，见 [文本语法](../guide/text.md) |
| `colorEnabled` / `colorPermission` | 谁能在自己的发言里打 `&` 颜色码 |
| `placeholderPermission` | 允许在聊天里用 `%变量%` 的权限节点 |
| `blockMuted` | 被禁言玩家发言是否直接拦下 |
| `nameHover` | 鼠标悬停玩家名时的浮层（每项一行） |
| `nameClick` | 点击玩家名做什么，如 `run:/tpa {name}` |

常用占位符：`{name}` `{message}` `{world}` `%group%` `%prefix%` `%title%`
`%level%` `%playtime%` `%ping%` `%health%` `%x% %y% %z%` `%online%` `%tps%`。

---

## perm/settings.json

| 字段 | 说明 |
|---|---|
| `defaultGroup` | 没有显式分组的玩家归到哪个组 |
| `opBypass` | 原版 OP 是否直接放行所有权限检查 |
| `defaultValue` | 谁都没命中时的默认结果（**一般保持 false**） |
| `wildcardEnabled` | 是否启用通配（`*` 与 `voidkit.home.*`） |
| `prefixFromHighestWeight` | 前缀取权重最高的组 / 始终取主组 |

组的字段：`prefix` `suffix` `weight` `permissions` `parents`。
详见 [权限模块](../modules/perm.md)。

---

## title/titles.json

```jsonc
"titles": {
  "vip": {
    "display": "&6[VIP]",
    "description": "VIP 专属称号",
    "permission": "voidkit.title.vip",   // 留空 = 人人可用
    "weight": 10,
    "icon": "minecraft:gold_ingot"
  }
}
```

「谁佩戴了哪个称号」**不在这里** —— 那存在玩家档案里；「谁有资格用」直接复用权限系统。
让某玩家拿到称号：`/perm user <玩家> add voidkit.title.vip`。

---

## economy/config.json

| 字段 | 默认 | 说明 |
|---|---|---|
| `currencyName` | `金币` | 货币名 |
| `format` | `{amount} {currency}` | 金额显示格式 |
| `startingBalance` | `0.0` | 新玩家初始余额 |
| `maxBalance` | `1e9` | 余额上限 |
| `payEnabled` | `true` | 是否允许转账 |
| `payMin` | `0.01` | 单次转账下限 |
| `payCooldownSeconds` | `0` | 转账冷却 |
| `taxPercent` | `0.0` | 转账手续费百分比 |

`values.json` 是物品估值表，供 `/sell`、`/worth`、`/shop` 使用。

---

## utility/clearlag.json

| 字段 | 默认 | 说明 |
|---|---|---|
| `intervalSeconds` | `300` | 清扫间隔 |
| `warnSeconds` | `30` | 清扫前提前播报 |
| `announceChannel` | `chat` | 播报通道 |
| `removeItems` / `removeXpOrbs` / `removeArrows` | `true` | 各类实体的处理开关 |
| `maxTypes` | `45` | 垃圾桶最多保留多少种物品 |
| `junkKeepLimit` | `512` | 垃圾物品每类保留上限 |
| `protectedItems` | — | **永不动**的贵重物白名单 |
| `trashItems` | — | 优先淘汰的垃圾物 |
| `maxPerSweep` | `20000` | 单轮处理实体数上限（防卡主线程） |

垃圾桶的淘汰策略详见 [清扫与性能](../modules/utility.md)。

---

## web/config.json

| 字段 | 默认 | 说明 |
|---|---|---|
| `bind` | `127.0.0.1` | 监听地址（**默认只本机**，安全） |
| `port` | `8765` | 监听端口 |
| `token` | 自动生成 | 访问令牌，用 `/vk token` 查看 |
| `rateLimitPerSecond` | `30` | 每秒请求上限 |

> ⚠️ 不要随便把 `bind` 改成 `0.0.0.0` 暴露到公网 —— 正确做法是让它只听 `127.0.0.1`，
> 由本机的 [管理平台](../panel/) 去连它。
