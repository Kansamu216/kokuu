# economy（经济）

```
mods/voidkit_economy-1.2.1.jar      ← 可选
```

货币系统：余额、转账、排行，外加物品**估值表**（`/worth` `/sell`）与一个
**商店 GUI**（`/shop`）。

> **没装它也能用**：扣款恒返回失败，表现为「暂时不能购买」而不是报错。
> 它为商店 / 任务类扩展预留了 `EconomyProvider` 接口。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `economy` | 经济本体（余额 / 转账 / 排行 / 估值 / 商店） | 开 |

## 命令

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/balance`(`/bal`) [玩家] | 查余额 | `voidkit.economy.balance`（查他人 `.balance.others`） |
| `/pay <玩家> <金额>` | 转账 | `voidkit.economy.pay` |
| `/baltop` | 财富排行 | — |
| `/worth [数量]` / `/value` | 查看手上物品的估价 | `voidkit.economy.value` |
| `/sell`（`/sell hand` `/sell all`） | 卖掉手上物品 / 全部可卖物 | `voidkit.economy.sell` |
| `/shop` | 打开商店 GUI | `voidkit.economy.shop` |
| `/eco give\|take\|set <玩家> <金额>` | 管理端增减余额 | `voidkit.economy.admin` |

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

| 节点 | 说明 |
|---|---|
| `voidkit.economy.balance` | 查自己余额 |
| `voidkit.economy.balance.others` | 查他人余额 |
| `voidkit.economy.pay` | 转账 |
| `voidkit.economy.value` | 查看估价 |
| `voidkit.economy.sell` | 回收 |
| `voidkit.economy.shop` | 使用商店 |
| `voidkit.economy.admin` | 管理端增减余额 |
