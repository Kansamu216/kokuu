#!/usr/bin/env python3
"""提 issue：审计游戏内 GUI 查询界面 + 后台可视化。"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"

BODY = """## 背景（用户明确要求）

用户要「可视化交互」：后台和游戏内都要**直观显示切切实实的东西**，而不是一个数据后台、
不是聊天框里的文字列表。尤其点名「查询那块以 MC 内 GUI 风格直观显示」。

## 现状

`/inspect lookup` 和检查模式的结果都是**聊天文字**：
`[时间] 玩家 破坏 stone @ x,y,z` —— 不直观，几十条记录刷屏。

## 目标：箱子 GUI 展示审计结果

玩家执行查询后，弹出一个**箱子界面**：

- 每个格子 = 一条审计记录，放的是**该方块/物品的图标**（破坏的石头就摆石头、取走的钻石就摆钻石）
- 鼠标悬停看详情：谁、什么时候、什么动作、坐标
- 底部翻页（下一页/上一页），复用菜单系统的分页思路
- 点击某条记录可进一步操作（如查看坐标）

## 实现要点

- **复用现有动态箱子 GUI**：垃圾桶的 `TrashMenu` 已经是「动态内容 + 点击处理」的箱子界面，
  审计结果展示可以套同一套模式（只读容器 + `ChestMenu` 接管点击）
- 查询结果已有物品 id（material），用 `Icons.of()` 渲染图标即可，带 NBT 的物品也能显示
- 命令：`/inspect lookup ...` 结果改成弹 GUI（保留 `--text` 开关退回文字，方便控制台/复制）

## 后台可视化（第二部分，可与上面分开做）

后台「智能审计」页目前是文字表格。可进一步：
- 按玩家/动作聚合成条形图（谁操作最多、破坏 vs 放置占比）
- 时间线视图（沿时间轴展示操作序列，一眼看出破坏链）

## 验收

- 游戏内查询结果以箱子界面展示，每个格子是物品图标
- 悬停能看到「谁/何时/动作/坐标」
- 能翻页，不刷屏
- 保留文字输出作为可选（`--text`）
"""


def main() -> int:
    r = subprocess.run(
        ["gh", "issue", "create", "--repo", REPO,
         "--title", "审计：游戏内 GUI 查询界面（箱子风格展示结果）+ 后台可视化",
         "--label", "enhancement", "--body-file", "-"],
        input=BODY, capture_output=True, text=True, encoding="utf-8",
    )
    if r.returncode != 0:
        print(f"  ✗ {(r.stderr or r.stdout).strip()[:300]}")
        return 1
    print(f"  ✓ {r.stdout.strip()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
