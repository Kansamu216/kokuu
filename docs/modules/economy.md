# economy（经济）

> 📦 **已包含在 `voidkit_essentials` 里** —— 装
> `mods/voidkit_essentials-1.0.1+mc1.21.1-neoforge.jar` 即获得本模块，
> **不需要**（也没有）单独的 `voidkit_economy` jar。本页保留，因为经济的 feature、
> 权限节点与配置都没变；数据目录也仍是 `voidkit/economy/`（见下）。

货币系统：余额、转账、排行，外加物品**估值表**（`/worth` `/sell`）与一个
**商店 GUI**（`/shop`）。

> **没装/关掉它也能用**：扣款恒返回失败，表现为「暂时不能购买」而不是报错。
> 它为商店 / 任务类扩展预留了 `EconomyProvider` 接口。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `economy` | 经济本体（余额 / 转账 / 排行 / 估值 / 商店） | 开 |

## 命令

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/balance`(`/bal`) [玩家] | 查余额（查他人另需 `.balance.others`） | `voidkit.economy.balance` | 🟢 |
| `/baltop` | 财富排行 | `voidkit.economy.balance` | 🟢 |
| `/pay <玩家> <金额>` | 转账 | `voidkit.economy.pay` | 🟢 |
| `/worth [数量]` / `/value` | 查看手上物品的估价 | `voidkit.economy.value` | 🟢 |
| `/sell`（`/sell hand` `/sell all`） | 卖掉手上物品 / 全部可卖物 | `voidkit.economy.sell` | 🟢 |
| `/shop` | 打开商店 GUI | `voidkit.economy.shop` | 🟢 |
| `/eco give\|take\|set <玩家> <金额>` | 管理端增减余额 | `voidkit.economy.admin` | 🔴 |

## 配置

`voidkit/economy/config.json`：

| 字段 | 默认 | 说明 |
|---|---|---|
| `currencyName` | `金币` | 货币名 |
| `format` | `{amount} {currency}` | 金额显示格式 |
| `startingBalance` | `0.0` | 新玩家初始余额 |
| `maxBalance` | `1000000000.0` | 余额上限 |
| `payEnabled` | `true` | 是否允许转账 |
| `payMin` | `0.01` | 单次转账下限 |
| `payCooldownSeconds` | `0` | 转账冷却 |
| `taxPercent` | `0.0` | 转账手续费百分比 |

## 估值表

`voidkit/economy/values.json` —— 物品 id → 单价。`/worth`、`/sell`、`/shop` 都读它。

```jsonc
{
  "values": {
    "minecraft:diamond": 100.0,
    "minecraft:iron_ingot": 5.0,
    "minecraft:cobblestone": 0.1
  }
}
```

- 没在表里的物品默认不可回收（可在配置里调整默认策略）。
- 支持按物品 id 前缀批量为模组物品定价。

## 账户数据

`voidkit/economy/accounts.json` —— 按玩家 UUID 记录余额。

## 权限节点

| 节点 | 说明 | 默认 |
|---|---|---|
| `voidkit.economy.balance` | 查余额 / 排行 | 🟢 公开 |
| `voidkit.economy.pay` | 转账 | 🟢 公开 |
| `voidkit.economy.value` | 查看估价 | 🟢 公开 |
| `voidkit.economy.sell` | 回收 | 🟢 公开 |
| `voidkit.economy.shop` | 使用商店 | 🟢 公开 |
| `voidkit.economy.balance.others` | 查**他人**余额 | 🔴 |
| `voidkit.economy.admin` | 管理端增减余额 | 🔴 |

> 玩家日常经济操作都是公开节点 —— 零配置下装完就能用，想限制某组再加 `-节点`。
