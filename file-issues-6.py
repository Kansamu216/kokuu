#!/usr/bin/env python3
"""提 issue：菜单对照 DeluxeMenus 补齐 + 迁移。"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"

BODY = """## 背景（优先级：高，用户明确「要先用」）

用户要「还原 Spigot 服务器上的钟表菜单（DeluxeMenus 杜蕾斯菜单插件）」——
多层级、独立配置文件写菜单、轻量。

## 现状：核心能力已经做了

菜单系统已在 `voidkit_display` 里落地（`display/menu.json`，带中文注释）：

- ✅ **独立配置文件**：`menu.json`，服主直接改
- ✅ **多层级**：`action: menu:<名字>` 跳转 + `back` 返回
- ✅ **钟表菜单**：`openers` 把菜单绑定到物品（如手持 `minecraft:clock` 右键打开）
- ✅ **图标 / 名称 / lore / 数量**，支持 `&` 颜色与 `%变量%`
- ✅ **权限门禁**（permission）
- ✅ **8 种动作**：`run:` / `console:` / `menu:` / `back` / `close` / `url:` / `copy:` / `suggest:`
- ✅ 图标 id 校验（写错会警告，不静默崩）
- ✅ 示例菜单（main + info + clock 绑定），smoke-test 有 `/api/menus` 端点

## 对照 DeluxeMenus 还差的（本 issue 要做）

1. **打开要求（open requirement）**：菜单级 / 按钮级的「需要权限/物品/金钱才能打开或显示」。
   现在按钮有 permission，但**没有「打开菜单前置条件」**和**「按条件显示/隐藏按钮」**。
2. **视图条件（view requirement）**：根据占位符/权限动态显示或隐藏某个按钮
   （DeluxeMenus 的 `view-requirement`）。没有它，就做不了「VIP 才看到这排」这类动态菜单。
3. **更多动作**：`sound:`（播放音效）、`message:`（发消息）、`give:`（给物品）、
   `money:`（扣钱，联动 voidkit_economy）、`broadcast:`（广播）、`tp:`（传送）。
4. **玩家头像图标**：`head:玩家名`（DeluxeMenus 常见）。
5. **配置热重载**：确认改 menu.json 后 `/voidkit reload` 能即时生效（大概率已支持，需验证）。

## 迁移（如果用户手头有 DeluxeMenus 配置）

若用户能提供 Spigot 服上的 DeluxeMenus `config.yml` / `menus/*.yml`，
需要一份**转换说明或脚本**（YAML → menu.json），把既有菜单内容原样搬过来。

## 验收

- 打开要求 / 视图条件生效（VIP 才能看到某排按钮）
- 新动作（sound/message/give/money）点击生效
- 能完整复刻用户 Spigot 服上的钟表菜单
"""


def main() -> int:
    r = subprocess.run(
        ["gh", "issue", "create", "--repo", REPO,
         "--title", "菜单：对照 DeluxeMenus 补齐高级能力 + 迁移 Spigot 钟表菜单（高优先级）",
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
