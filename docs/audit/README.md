# 审计（VoidkitInspector）

```
mods/voidkit_inspector-<版本>.jar
```

> ⚠️ **独立仓库、独立发布**：[`KokuuWork/voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector)。
> 依赖 `voidkit_core` 作为前置模组。

CoreProtect 风格的**方块 / 容器 / 实体操作记录**，专为**科技整合包**
（机械动力 Create / Mekanism / AE2）设计。

---

## 为什么是它

普通查插件在科技包里会翻车：一条自动化产线刷几百万圆石、产几万铁锭，全记进数据库，
结果真正的破坏行为被淹没在海量噪音里，查不出来。Inspector 从架构上解决了这个问题。

## 核心特性

| 能力 | 说明 |
|---|---|
| **多线程写入** | 主线程只做 O(1) 入队，唯一写线程批量事务落库（WAL），队列满自动丢弃，**绝不卡 TPS** |
| **多线程查询** | 独立查询线程池 + 各自读连接，查询与写入并行、不占主线程、不卡磁盘 |
| **嵌入式 SQLite** | 零配置单文件数据库，方块/物品/维度/玩家名全字典化，行小而密 |
| **复合索引 + keyset 分页** | 几十万条记录翻页毫秒级，不会像老插件那样查一次遍历整表 |
| **完整 NBT** | 模组方块实体、带组件的物品都存全量 NBT |
| **箱子 GUI 查询** | 结果以物品图标直观展示，悬停看「谁 / 何时 / 动作 / 坐标」，翻页不刷屏 |
| **智能回滚** | 分类感知 + 预览 + 撤销，详见下文 |

---

## 科技包友好（核心设计）

科技整合包里绝大多数方块/容器变化是**自动化产线**干的，不是玩家。
Inspector 的灵魂是**来源分类** —— 先把「是谁干的」认出来，再决定记不记：

- 机械臂 / 管道 / 假玩家驱动的操作 → **自动识别，默认不记**
- 自动化来源名取方块实体命名空间，日志里能看出 `#create` / `#mekanism` / `#ae2`
- 树叶腐烂 / 流体 → 默认不记；爆炸 / 苦力怕 / 末影人 → **该记的照记**
- 结果：一条 Create 产线刷几百万圆石**进不了库**，那一下真正的破坏行为才查得出来

### 两扇门：先「筛」，再「记」

```
① 来源闸门  logPlayer / logAutomation / logNatural   按「是谁干的」分类
② 动作细分  logBlockBreak / logContainers / …         按「干了什么」再筛一层
```

`alwaysLogSources` 是一张**强制记录白名单**：即便来源类别被关掉，
爆炸、火焰、苦力怕这些「违规行为相关」的事件也照记不误（它们往往正是需要追责的场景）。

---

## 快速开始

前置：`voidkit_core`（同版本，含 `org.voidkit.core.api` 契约与 `core.platform` 适配层）。

```bash
./fetch-core.sh          # 从 voidkit Release 下载 core jar 到 libs/
./build.sh build         # 产出 build/libs/voidkit_inspector-<版本>.jar
```

服务端部署：把 `voidkit_inspector` 和 `voidkit_core` 两个 jar 一起放进 `mods/`。

---

## 命令

| 命令 | 说明 |
|---|---|
| `/inspect`（别名 `/coi` `/ins`） | 切换检查模式，点击方块查看历史 |
| `/inspect lookup u:玩家 r:半径 t:1h a:place b:石头` | 参数查询，结果弹箱子 GUI |
| `/inspect lookup ... --text` | 退回文字输出（方便控制台 / 复制） |
| `/inspect here` | 查看准星对准方块的历史 |
| `/inspect page` | 翻页 |
| `/inspect chain` | 查看因果链聚类（把一次破坏的连续动作串起来） |
| `/inspect preview` | 回滚**预览**（不改世界） |
| `/inspect rollback` | 执行回滚 |
| `/inspect status` | 数据库状态 |
| `/inspect purge <天>` | 清理 N 天前的记录 |

**查询参数**：

| 参数 | 含义 |
|---|---|
| `u:` | 玩家 |
| `r:` | 半径 |
| `t:` | 时间（`1h` / `1d`） |
| `a:` | 动作（`place` / `break` / `open` / `kill`） |
| `b:` | 方块 / 物品 id |

## 权限

命令分**只读查询**与**改数据**两类，节点分开 —— 给版主「查一下谁破坏了方块」的权限，
**不等于**让他能回滚世界或清空历史。

| 节点 | 覆盖的命令 | 说明 |
|---|---|---|
| `voidkit.inspector` | `/inspect` `/inspect on\|off` `/inspect here` `/inspect lookup` `/inspect page` `/inspect chain` `/inspect status` | **只读**：查看历史 |
| `voidkit.inspector.rollback` | `/inspect preview` `/inspect rollback` | 会**改世界**（回滚） |
| `voidkit.inspector.purge` | `/inspect purge <天>` | 会**删历史数据** |

- 三个命令名等价：`/inspect`（别名 `/coi` `/ins`）。
- 没装/关掉权限模块时按原版 OP 判定（普通玩家一律拒绝）。
- ⚠️ **升级注意**：早期版本整棵树只由 `voidkit.inspector` 把守。
  若你依赖「有 `voidkit.inspector` 就能回滚」，升级后需补授后两个节点。

> 为什么拆开：**查看历史**和**改写世界**是两个完全不同的风险等级。
> 合成一个节点就没法「只让版主查、不让版主回滚」。

---

## 智能回滚

回滚不是「一键抹掉某段时间的全服改动」那么粗暴。它做了五层：

| 层 | 能力 |
|---|---|
| 1 | **分类感知** —— 只回滚玩家行为；机器网络（Create 线缆、Mek 管道、AE2 线）**默认跳过**，避免拆断仍在运转的产线 |
| 2 | **防雪崩闸门** —— 单次最多回滚 `maxRollbackEvents` 条、半径上限 `maxRollbackRadius` 格、按 `rollbackBlocksPerSecond` 分帧执行，大回滚不烧 TPS |
| 3 | **因果链聚类** —— 把「拆箱子 → 拿东西 → 放 TNT → 炸」这类连续破坏串成一条链（时间窗 + 空间半径） |
| 4 | **预览** —— `preview` 先算好、给服主看，确认后再 `rollback`；预览有 TTL，防止拿旧预览回滚新世界 |
| 5 | **撤销** —— 回滚窗口内可 `--undo`（`rollbackUndoWindowMinutes`，默认 30 分钟），每个回滚只能撤销一次 |

> 关键设计：**预览和执行用的是同一批数据**，不会出现「预览一套、执行另一套」。

---

## 配置

`voidkit/inspector/config.json`。

### 来源闸门

| 字段 | 默认 | 说明 |
|---|---|---|
| `logPlayer` | `true` | 记录真人玩家操作（几乎总该开着） |
| `logAutomation` | **`false`** | 记录自动化来源 —— 科技包数据量的头号来源，**默认关** |
| `logNatural` | **`false`** | 记录自然来源（树叶腐烂、流体流动）—— 几乎全是噪音，**默认关** |
| `alwaysLogSources` | `#creeper #tnt #fire …` | 强制记录名单（爆炸 / 火 / 岩浆等需追责的自然事件） |

### 动作细分

| 字段 | 默认 |
|---|---|
| `logBlockBreak` / `logBlockPlace` | `true` |
| `logContainers` | `true` |
| `logKills` | `true` |
| `logItemDropPickup` | `false`（太吵） |

### 黑名单

`blockBlacklist` / `itemBlacklist` / `worldBlacklist` —— 某些方块 / 物品 / 维度**绝对不记**。

### 性能保护

| 字段 | 默认 | 说明 |
|---|---|---|
| `maxEventsPerTick` | `2000` | 单 tick 最多入队数（防雪崩闸门） |
| `maxPerSourcePerSecond` | `500` | 单来源每秒上限 |
| `queueCapacity` | `100000` | 写队列容量，满则丢、**绝不阻塞主线程** |
| `batchSize` / `flushIntervalMs` | `500` / `200` | 批量事务大小 / 最长攒批时间 |
| `queryThreads` | `2` | 查询线程池 |
| `retentionDays` | `30` | 记录保留天数（0 = 永久） |

### 回滚

| 字段 | 默认 | 说明 |
|---|---|---|
| `rollbackEnabled` | `true` | 回滚总开关 |
| `maxRollbackEvents` | `10000` | 单次最多处理记录数 |
| `maxRollbackRadius` | `50` | 单次最大半径（格） |
| `rollbackBlocksPerSecond` | `2000` | 执行速率（分帧） |
| `rollbackMachineNamespaces` | `create / mekanism / ae2 / …` | 跳过的机器命名空间 |
| `rollbackPreviewTtlSeconds` | `300` | 预览有效期 |
| `rollbackUndoEnabled` / `rollbackUndoWindowMinutes` | `true` / `30` | 撤销开关与时间窗 |
| `chainIdleMinutes` / `chainRadiusBlocks` | `5` / `32` | 因果链的时间窗与空间半径 |

---

## 数据

```
voidkit/inspector/
├── config.json
└── inspector.db      SQLite（WAL）；方块/物品/维度/玩家名全字典化
```

---

## 架构

插件只依赖 core 的 `org.voidkit.core.api` 契约，并通过 `core.platform` 适配层
跨 NeoForge / Forge / Fabric 移植。

---

## 在管理平台里

[管理平台](../panel/)的「审计」页提供**智能审计**：按玩家 / 动作聚合的条形图、
操作时间线、跨服审计流水汇总。数据来自 `GET /api/inspector/lookup`。
