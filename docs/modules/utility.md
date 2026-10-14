# utility（清扫 / 性能）

```
mods/voidkit_utility-0.1.0-beta.2+mc1.21.1-neoforge.jar
```

服务器维护工具：**掉落物清扫 + 垃圾桶**，以及 **TPS / MSPT / 区块热点**性能监测。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `clearlag` | 清扫 + 垃圾桶 | 开 |
| `perf` | 性能监测 | 开 |

---

## 清扫与垃圾桶

清扫到的东西**默认不是删除，而是收进垃圾桶** —— 玩家可以在下一轮清扫前自己取回。
清扫完成的播报里带一个可点的「打开垃圾桶」入口，`/trash` 也随时能开。

同时做到「掉落物多到不会崩服」和「不会把有价值的物品挤掉」，靠三个机制：

### ① 按类型聚合，不是按堆栈

垃圾桶内部是 `Map<物品键, 数量>`。1 万个圆石是**一格**（显示 `圆石 × 10,000`），
不是 1 万格。所以内存占用跟着**物品种类数**走，与掉落物总量无关 ——
这是「不会因为掉落物太多崩掉」的根本原因。

### ② 超容量时按价值淘汰，不是先到先得

物品种类也可能超过格数上限（整合包里几十种矿物碎块、几十种模组零件）。
这时不能简单丢掉新来的 —— 那会变成「垃圾把钻石挤出去」，正好和垃圾桶的目的相反。
所以满了以后按价值分排序，把**最低分的**赶出去。价值分判定顺序：

```
① 配置 protectedItems 里的   → 永不动（连桶都不进）
② 有自定义名称的             → 永不动（玩家改过名，几乎肯定是珍视物）
③ 配置 trashItems 里的       → 优先淘汰 + 每类限量
④ 其它                       → 按稀有度 / 附魔 / 是否装备 / 堆叠上限累加
```

### ③ 垃圾物品有每类保留上限

圆石留 512 个够玩家看看是什么了，剩下几百万个连进桶的机会都没有。

### 三类东西处理不同

| 类别 | 处理 |
|---|---|
| `protectedItems` 的贵重物 | **完全不动** |
| 有自定义名称的物品 | **完全不动** |
| 其它掉落物 | 收进垃圾桶，可点开逐格或一键取回 |
| 经验球 / 箭 / 投掷物 | 直接删除（不是「物品」，没法原样还给你） |

另外单轮处理实体数有上限（`maxPerSweep`）：万一异常刷出几百万个实体，
一次性删完会让主线程卡十几秒（客户端看就是「服务器卡死」）—— 分轮处理，
每轮都在可接受耗时内。

### 配置 `voidkit/utility/clearlag.json`

| 字段 | 默认 | 说明 |
|---|---|---|
| `intervalSeconds` | `300` | 清扫间隔 |
| `warnSeconds` | `30` | 清扫前提前播报 |
| `announceChannel` | `chat` | 播报通道 |
| `removeItems` / `removeXpOrbs` / `removeArrows` / `removeProjectiles` | `true`/`true`/`true`/`false` | 各类实体处理开关 |
| `keepNamedItems` | `true` | 有自定义名称的物品不清理 |
| `trashEnabled` | `true` | 是否启用垃圾桶 |
| `maxTypes` | `45` | 垃圾桶最多保留的种类 |
| `junkKeepLimit` | `512` | 垃圾物品每类保留上限 |
| `protectedItems` | — | **永不动**的贵重物白名单 |
| `trashItems` | — | 优先淘汰的垃圾物 |
| `maxPerSweep` | `20000` | 单轮处理实体数上限 |

### 命令

| 命令 | 说明 | 节点 | 默认 |
|---|---|---|---|
| `/trash` | 打开垃圾桶（取回被清掉的掉落物） | `voidkit.clearlag.trash` | 🟢 |
| `/clearlag` | 手动触发一次清扫 | `voidkit.clearlag` | 🔴 |
| `/clearlag status` | 查看状态 | `voidkit.clearlag` | 🔴 |
| `/clearlag test` | 发一条测试播报（确认通道配置） | `voidkit.clearlag` | 🔴 |

> `/trash` 是**玩家**取回自己东西的入口，所以单独一个公开节点，
> 不和 `/clearlag` 混在一起。

---

## 性能监测

`/perf` 与 `GET /api/perf`：

- **TPS / MSPT** —— MSPT 直接读原版自己维护的滑动平均，TPS 由它反推
  （数 tick 得到的「过去一分钟平均」对突发卡顿反应太慢）。
- **实体分布** —— 总数 + 掉落物 / 经验球 / 投掷物 / 生物分类计数。
- **区块热点排名** —— 找产线、刷怪塔造成的热点区块（含区块坐标与世界坐标）。
- **超阈值提醒** —— 默认只写控制台（`warnChannel` 默认 `off`），免得刷屏。

### 温和干预（默认关闭）

打开后对超阈值的区块卸载其中的掉落物，三条硬边界：

1. 只删**掉落物**，**永远不碰方块、机器、生物**；
2. 只在实体**已加载**时操作，绝不为了优化去加载区块；
3. 每个区块有冷却、单次有总量上限。

也就是说，**即使配置写错，最坏情况也只是「某些掉落物被清掉了」，不会破坏存档结构。**

### 配置 `voidkit/utility/perf.json`

| 字段 | 默认 | 说明 |
|---|---|---|
| `sampleSeconds` | `60` | 采样间隔 |
| `warnTps` | `15.0` | TPS 低于此值提醒 |
| `warnMspt` | `45.0` | MSPT 高于此值提醒 |
| `warnCooldownSeconds` | `300` | 两次提醒最短间隔（防刷屏） |
| `warnChannel` | `off` | `chat` / `actionbar` / `both` / `off` |
| `warnToAdminsOnly` | `true` | 提醒只发给有 `voidkit.perf.notify` 的玩家 |
| `chunkTopEnabled` / `chunkTopSize` | `true` / `10` | 区块热点排名 |
| `entityWarnPerChunk` | `300` | 单区块实体数超过此值列为热点 |
| `interveneEnabled` | `false` | 温和干预总开关 |
| `interveneThreshold` | `800` | 触发干预的单区块实体数 |
| `interveneCooldownSeconds` | `600` | 同区块干预冷却 |
| `interveneMaxPerRun` | `5000` | 单次干预最多处理的掉落物数 |

### 命令与权限

| 命令 / 用途 | 节点 | 默认 |
|---|---|---|
| `/perf` / `/perf chunks` | `voidkit.perf` | 🔴 |
| 接收 TPS / MSPT 超阈值提醒 | `voidkit.perf.notify` | 🔴 |
