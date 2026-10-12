#!/usr/bin/env python3
"""第二批 issue：智能回滚、面板/hub 合并、火焰/岩浆日志。

- 更新 #4：把「面板/Hub 同步」改成「合并为单一平台（删 panel）」
- 新建：智能回滚、火焰/岩浆/流体日志
"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"

MERGE_BODY = """## 背景

现在有两份管理平台，都是 Go 单二进制：

- `panel/` —— 单服面板：静态前端 + 把 `/api/*` 反向代理到本机 `remote-api`
- `hub/` —— 多服中心平台：轮询各服 agent、托管 apiToken、跨服搜索

用户明确：**不需要两份管理平台，合掉。**

## 结论：panel 是 hub 的退化子集，直接删

对比之后 `panel/` 没有一样 hub 没有的能力：

| 能力 | panel | hub |
|---|---|---|
| 单服实时透传 | `-api` 反代 | 已有 `/hub/proxy/{id}/api/...` 透传 |
| API 令牌托管 | `-token` 注入 | hub 本来就把各服 token 收在自己这 |
| 面板登录 | HTTP Basic | 内置账号 + 机器令牌 |
| 前端 | app.js 29KB（旧） | app.js 47KB（MC 控制台风格，更新） |
| 多服 / 跨服搜索 | 无 | 有 |

单服场景 = hub 配置里只加一台服。所以合并 = **删 `panel/`，用 hub 单服配置兜底。**

## 方案

1. 删除 `panel/` 整个目录（server.go / serve.py / app.js / index.html / style.css / dist/）
2. hub 的「服务器」页支持「添加本机服」一键（默认填 `127.0.0.1:8765`，提示去 web 配置取 token）
3. 部署脚本 / README 里所有 `voidkitpanel` / panel 引用一并清掉
4. 确认 hub 的 `/hub/proxy` 透传在单服下就是 panel 的等价物（实时性足够）
5. 补齐原 #4 里列的页面（垃圾桶/性能/权限/查熊），在 hub 前端统一实现

## 迁移

从 panel 迁到 hub 的用户：hub 首次运行自动生成管理员密码并打印，添加一台服即可。
无数据迁移负担 —— panel 本来就不存数据，只是个反代壳。

## 验收

- 仓库里只有一个 Go 管理程序（hub），`panel/` 目录不存在
- 单服场景：hub 配一台服后，功能与旧 panel 等价（垃圾桶/性能/权限/查熊页都能看）
- 多服场景：不受影响
"""

ROLLBACK_BODY = """## 背景

`voidkit_inspector` 已落地：记录玩家方块破坏/放置、容器拿取/放入、击杀，全量存 NBT，
SQLite 存储、多线程写读、自动化产线已过滤。但目前**只做查询，没有回滚**。

需求：能不能做成「智能回滚」——不是 CoreProtect 那种无脑 `rollback`，而是在科技整合包里
也能安全地把熊孩子的破坏**精准还原**，同时不误伤还在运转的机器。

## 为什么传统回滚在科技包里会翻车

| 场景 | 无脑回滚的后果 |
|---|---|
| 机械动力传动轴被破坏 | 只补回方块，传动轴断裂、应力失效 |
| AE2 线缆被拆 | 补回线缆，但 ME 网络已断、物品卡在管道里 |
| Mek 多方块机器 | 只回滚一半 = 结构不完整，直接崩 |
| 箱子里被偷了物品 | 只补方块不够，必须恢复 NBT 里的物品 |

## 设计：五层「智能」

### 第 1 层：分类感知（RollbackPolicy）

回滚前先把要回滚的方块/物品**分类**，每类给不同策略：

| 类别 | 判定 | 策略 |
|---|---|---|
| 普通方块（石头/泥土/木板…） | 无方块实体 | 直接回滚 |
| 容器（箱子/熔炉/桶/潜影盒） | 方块实体 + `minecraft:` 命名空间 | 回滚物品（恢复 NBT） |
| 机器/装置（Create/Mek/AE2…） | 方块实体命名空间非 `minecraft:` | **默认跳过 + 列出警告** |
| 线缆/管道/红石 | 按命名空间/标签 | 默认跳过（避免断网络） |

判定依据：`BlockEntityType` 注册名命名空间。`minecraft:` 的箱子/熔炉是安全容器；
`create:` `mekanism:` `ae2:` 等是机器，默认不碰，服主显式 `--include-machines` 才回滚。

### 第 2 层：多方块/结构感知

对「属于更大结构」的方块（Create 装置、AE2 网络、多方块机器），
要么**整结构一起回滚**，要么**整结构跳过**，绝不回滚一半。
v1 建议：识别失败/复杂结构一律整体跳过 + 警告，「整结构回滚」留作后续。

### 第 3 层：因果链聚类

熊孩子常见破坏链：拆箱子 → 拿东西 → 放 TNT → 炸。
按「同一玩家 + 邻近区域 + 连续时间段」把操作**聚成一条破坏链**，
支持一键回滚整条链，而不是让服主手动挑每一个动作。

### 第 4 层：预览 + 白名单 + 排除

```
/inspect rollback u:熊孩子 t:1h r:30 --preview
/inspect rollback ... --only container        # 只回滚容器
/inspect rollback ... --skip create:*         # 跳过机械动力
/inspect rollback ... --dry-run               # 只出报告，不动世界
```

预览按类别统计「将回滚 N 个方块 / M 个物品 / K 台机器（跳过）」。

### 第 5 层：可撤销 + 自审计

- 回滚前把「被回滚方块/容器的当前状态」快照下来，支持 `--undo` 撤销回滚
- 回滚操作本身也写审计日志（来源 `#rollback`），追责链完整

## v1 落地范围（风险从低到高）

1. **容器回滚**（价值最高、风险最低、NBT 已存）：熊孩子从箱子拿走的物品还回去
2. **普通方块回滚**：无方块实体的破坏/放置
3. 机器/线缆**默认跳过 + 列出警告**（不做自动回滚，避免二次破坏）
4. 预览 + dry-run + 撤销

## 依赖与约束

- 依赖 inspector 已记录的数据（已满足：全量 NBT + 字典化 + 索引）
- 回滚本质是「以插件身份改世界」，要在主线程做，且**分帧处理**（一次回滚几万方块
  不能卡主线程 —— 复用清扫模块「分轮预算」的思路）
- 不改动世界时（dry-run/预览）走查询线程池，真正回滚时切回主线程分帧执行

## 验收

- `/inspect rollback --preview` 对真实破坏场景给出准确类别统计
- 熊孩子偷走的箱子物品能原样（含 NBT）还回
- 对 Create/Mek/AE2 机器默认跳过、并在输出里明确提示「有 K 台机器未回滚」
- 回滚后可撤销，且回滚操作本身可被查询到
"""

FIRE_BODY = """## 背景

`InspectorConfig.alwaysLogSources` 里已预留 `#fire` `#lava` 等自然来源，但目前只实现了
两类自然事件：爆炸（`ExplosionEvent.Detonate`）和末影人搬方块（`BlockEvent.EntityPlaceEvent`）。

火焰蔓延烧掉房子、岩浆流成黑曜石/石头、水冲掉红石线，这些「没有真人玩家但需要追责」
的事件**还没接**。

## 难点：零 mixin 约束下没有干净事件

- 火焰烧方块：走 `Level.removeBlock` + 方块 tick，没有 Forge 的「block burned」事件
- 岩浆成岩 / 水成石：方块被「替换」，没有直接事件
- CoreProtect / Ledger 都是靠 mixin 注入 `BlockBehaviour` 才拿到这些的

我们的铁律是**零 mixin**（整合包里有 30+ mixin mod，不能再插一脚）。

## 可选路线

1. **`BlockEvent.NeighborNotifyEvent`**：能捕获部分方块更新，但火焰/流体不保证走这里，覆盖不全
2. **周期性区域快照 diff**：对「正在燃烧/流动」的区域定期对比，开销和误报都难控
3. **只记「玩家点火/放岩浆」这个动作**（`BlockEvent.EntityPlaceEvent` 里放打火石/岩浆桶的玩家），
   把「火是谁点的」记下来，但「火蔓延烧了哪些」不记 —— **零 mixin 就能做，推荐先做**
4. **评估为这一个功能破例引入 mixin**：收益高（完整火焰/流体审计），但要重新审视「零 mixin」边界

## 建议

先做路线 3（玩家点火/放岩浆 → 记 `#fire` `#lava` 来源 + 点火玩家名），
把「谁是纵火犯」解决掉。火蔓延的逐方块记录留到 mixin 决策之后。

## 验收

- 玩家用打火石点火、用岩浆桶放岩浆，能在 `/inspect lookup` 查到（来源 `#fire`/`#lava`）
- 点火者能追溯到具体玩家，而不是笼统的 `#fire`
"""


def run(args, body):
    r = subprocess.run(args, input=body, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        print(f"  ✗ {(r.stderr or r.stdout).strip()[:300]}")
        return 1
    print(f"  ✓ {r.stdout.strip()}")
    return 0


def main() -> int:
    fail = 0

    print("更新 #4 → 合并 panel 与 hub：")
    fail += run(
        ["gh", "issue", "edit", "4", "--repo", REPO,
         "--title", "架构：合并 panel 与 hub 为单一管理平台（删除 panel）",
         "--body-file", "-"],
        MERGE_BODY,
    )

    print("新建 → 智能回滚：")
    fail += run(
        ["gh", "issue", "create", "--repo", REPO,
         "--title", "查熊：智能回滚（分类感知 + 预览 + 撤销）",
         "--label", "enhancement", "--body-file", "-"],
        ROLLBACK_BODY,
    )

    print("新建 → 火焰/岩浆日志：")
    fail += run(
        ["gh", "issue", "create", "--repo", REPO,
         "--title", "查熊：火焰/岩浆/流体蔓延等自然事件日志",
         "--label", "enhancement", "--body-file", "-"],
        FIRE_BODY,
    )

    return fail


if __name__ == "__main__":
    sys.exit(main())
