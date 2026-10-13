#!/usr/bin/env python3
"""第三批 issue：跨平台移植 + 前端重做。

- 更新 #1：多版本 → 多平台（NeoForge/Forge/Fabric）
- 新建：前端重做（Go 工具风 + 动效 + AI 零配置）
"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"

MULTI_BODY = """## 背景

套件目前硬绑 **NeoForge 21.1.x / Minecraft 1.21.1**。

用户新目标有两层：
1. **多版本**：同一份业务逻辑能编到多个 Minecraft 大版本
2. **多平台**：后续移植到 **Forge** 和 **Fabric**（把 Voidkit 做成"智能 AI 基础模组"，
   不绑定单一加载器）

## 核心手段：抽出 platform 层

把「每个平台都不一样」的东西收口到 `core/platform`，业务逻辑零改动：

| 层 | 内容 | 跨平台稳定性 |
|---|---|---|
| 业务逻辑 | 清扫、垃圾桶价值、权限继承、审计过滤/回滚、菜单分发 | 几乎不变 |
| 数据模型 | 配置结构、玩家档案、权限组树 | 基本不变 |
| **平台 API** | `Component`/`ItemStack` 组件、事件类、注册表、方块实体 NBT、假玩家判定 | **每个平台都变** |

## 三个平台的差异点（移植时要逐个对）

| 关注点 | NeoForge | Forge | Fabric |
|---|---|---|---|
| 假玩家判定（审计过滤核心） | `neoforged.common.util.FakePlayer` | `minecraftforge.common.util.FakePlayer` | `ServerPlayerEntity` 无官方 FakePlayer，需自造判据 |
| 事件包名 | `net.neoforged.neoforge.event.*` | `net.minecraftforge.event.*` | 无事件总线，用 Fabric API 的回调/注入 |
| 嵌套依赖 | jarJar | jarJar（旧版）/ shadow | `fabric.mod.json` 的 `jars` |
| 服务端-only 声明 | `side = "SERVER"` | 同左 | `"environment": "server"` |
| Kotlin 运行时 | 自带（core jarJar） | 自带 | 需 `fabric-language-kotlin` |
| 零 mixin 铁律 | 已遵守 | 已遵守 | **Fabric 几乎离不开 mixin**（要重新评估这条边界） |

## 建议顺序（风险从低到高）

1. **抽出 `core/platform` 层**：把所有 `net.minecraft.*` 调用收口，业务代码改调 platform。
   这一步无论走哪个平台都要先做，也是让"审计的自动化过滤、垃圾桶价值判定"这些
   核心逻辑能独立单测的前提（与 #6 单测联动）。
2. **NeoForge 多版本**（1.21.1 → 1.21.4+）：改动最小，先验证 platform 层够不够用。
3. **Forge 移植**：和 NeoForge 是近亲（事件模型、FakePlayer、jarJar 都同源），
   主要是包名和少量 API 差异。
4. **Fabric 移植**：差异最大，且要重新评估「零 mixin」——Fabric 生态基本靠 mixin，
   可能改为「只在 platform 层允许 mixin，业务层仍零 mixin」。

## 验收

- 业务代码里不再直接 import 平台相关类（Grep 脚本挂 CI 断言）
- 同一份审计/清扫/权限逻辑能编译到 ≥2 个加载器
- 新增平台时只需实现 platform 层，Feature 代码零改动
"""

FRONTEND_BODY = """## 背景

当前 hub 前端是「Minecraft 控制台」风格：深板岩底、矿物色（金/绿宝石/钻石）、
斜面按钮、还把服务器叫「舰队」。用户明确**不喜欢**，感觉像游戏 UI。

## 目标：Go 生态工具风（干净克制 + 丝滑）

对标 Grafana / Gitea / Portainer 这类 Go 运维工具的气质：

- **轻**：浅色为主（可切深色），中性灰阶，**单一克制强调色**（靛蓝），
  不再用高饱和游戏矿物色
- **克制**：0.5px 细边框、8–12px 圆角、系统字体、紧凑但有留白
- **丝滑**：过渡动画、hover 反馈、数字滚动、骨架屏、抽屉/弹层缓入缓出
- **逻辑菜单**：侧栏按用途分组（监控 / 数据 / 审计 / 系统），
  不再用「舰队总览」这类游戏隐喻，改用「总览 / 服务器」等中性词
- **AI 卖点外显**：把「审计 AI 智能回滚建议」这类能力做成显眼的卡片
  （检测到破坏链 → 建议回滚容器、自动跳过机器），让"AI 智能"一眼可见

## 视觉方向参考（已出 mockup）

- 品牌：Voidkit，靛蓝 #185FA5
- 状态色：正常=绿、偏卡/警告=琥珀、异常=红（只用填充淡色 + 深色文字，不整块高饱和）
- 服务器 = 服务器（不是"舰船"），状态是圆点 + 文本徽章

## 范围

1. 重写 `hub/web/style.css` + `index.html` + `app.js`（保持现有 class 名和功能不变，
   只换皮肤 + 补动效）
2. 侧栏改分组导航，文案去游戏化
3. 加动效：页面切换淡入、卡片 hover、数字滚动、toast 滑入滑出、骨架屏
4. 加一个「审计 AI」视图，展示智能回滚建议（配合 #8）
5. 与 #4（合并 panel/hub）一起做 —— 合并后只维护这一套前端

## 验收

- 视觉上不再有"游戏舰队"感，一眼是专业运维工具
- 所有现有功能（服务器/玩家/日志/模块/配置）在新皮肤下可用
- 有过渡动效，操作反馈丝滑，无卡顿
- 无外部 CDN 依赖，离线可用（延续现状）
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

    print("更新 #1 → 多平台 + 多版本：")
    fail += run(
        ["gh", "issue", "edit", "1", "--repo", REPO,
         "--title", "架构：多平台 + 多版本（NeoForge / Forge / Fabric）",
         "--body-file", "-"],
        MULTI_BODY,
    )

    print("新建 → 前端重做：")
    fail += run(
        ["gh", "issue", "create", "--repo", REPO,
         "--title", "前端：管理面板重做（Go 工具风 · 丝滑动效 · AI 零配置）",
         "--label", "enhancement", "--body-file", "-"],
        FRONTEND_BODY,
    )

    return fail


if __name__ == "__main__":
    sys.exit(main())
