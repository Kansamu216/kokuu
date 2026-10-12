#!/usr/bin/env python3
"""往 issue #1 追加「core 内分 api + platform 两层」的架构决策。"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"

APPEND = """

---

## 关键架构决策：core 内分 api + platform 两层（2026-10-12 定）

用户提出「插件高度依赖 core 自建 api，移植/跨版本只改 core」。方向正确，但要补一个关键点：
**不能只靠"改 core"，core 内部必须再分两层**，否则稳定的东西和易变的东西混在一起，
移植时改 A 崩 B：

```
插件 ──只依赖──▶ core.api（契约，稳定，跨平台跨版本不变）
                    │
                 core.platform（适配层，唯一按加载器重写）
                    ├── neoforge   ├── forge   └── fabric
```

- **插件绝不直接碰平台类**：不 import `net.minecraft.*`、不 import 加载器事件类。
  要用物品 / 方块 / 文本 / 事件，走 `core.api` 的抽象（`VItem` / `VBlock` / `VEvent`…）。
  现状尚未完全达标 —— 插件里还有不少 `net.minecraft.*`（ItemStack / BlockState / 事件类），
  抽 platform 层时要逐步收口。
- 移植 = 只重写 `platform/xxx/`，`core.api` 与全部插件零改动。
- platform 层要重点收口的难点：**事件抽象**（Fabric 无事件总线，要自建翻译层）、
  **假玩家判定**（查熊自动化过滤依赖它，三平台各不相同）、物品 / 方块 / NBT、注册表 API。
"""


def main() -> int:
    r = subprocess.run(
        ["gh", "api", f"repos/{REPO}/issues/1", "--jq", ".body"],
        capture_output=True, text=True, encoding="utf-8",
    )
    if r.returncode != 0:
        print(f"  ✗ 取正文失败：{(r.stderr or r.stdout)[:200]}")
        return 1

    body = r.stdout.strip()
    if "core 内分 api + platform 两层" in body:
        print("  已包含该段落，跳过")
        return 0

    new_body = body + APPEND
    r2 = subprocess.run(
        ["gh", "issue", "edit", "1", "--repo", REPO, "--body-file", "-"],
        input=new_body, capture_output=True, text=True, encoding="utf-8",
    )
    if r2.returncode != 0:
        print(f"  ✗ 更新失败：{(r2.stderr or r2.stdout)[:200]}")
        return 1
    print("  ✓ 已追加分层决策到 #1")
    return 0


if __name__ == "__main__":
    sys.exit(main())
