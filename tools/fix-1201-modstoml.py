#!/usr/bin/env python3
"""把 1.20.1（Forge）的 mods.toml 依赖语法从 NeoForge 模式改回 Forge 模式。

背景（实测踩坑）：
  NeoForge 1.20.2+ 的 TOML 把依赖的必选性写成  `type = "required" | "optional"`；
  而 Forge 1.20.1（含在它之上分支的 NeoForge 1.20.1）仍然只认
  `mandatory = true | false`。用错字段名时 Forge 直接拒绝加载整个 jar：

      InvalidModFileException: Missing required field mandatory in dependency (xxx.jar)

  → 后果是服务端起不来（Failed to find system mod: minecraft 只是连带的表象），
    而且这条报错出现在 mods/ 扫描阶段，很容易被误读成「模组内部崩了」。

只改依赖块里的 `type = "required"` / `type = "optional"`，不碰其它地方。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# NeoForge 的 type 取值 → Forge 的 mandatory 取值
TYPE_TO_MANDATORY = {
    "required": "true",
    "optional": "false",
}

# 只匹配「独占一行、且键名是 type、值是 required|optional」的依赖行
TYPE_RE = re.compile(r'^(\s*)type\s*=\s*"(required|optional)"\s*$')


def fix(path: Path) -> int:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    out = []
    changed = 0
    for line in lines:
        m = TYPE_RE.match(line.rstrip("\r\n"))
        if m:
            indent, value = m.group(1), m.group(2)
            eol = "\r\n" if line.endswith("\r\n") else "\n"
            out.append(f"{indent}mandatory = {TYPE_TO_MANDATORY[value]}{eol}")
            changed += 1
        else:
            out.append(line)
    if changed:
        path.write_text("".join(out), encoding="utf-8")
    return changed


def main() -> int:
    targets = sorted(ROOT.glob("*/src/main/resources/META-INF/mods.toml"))
    if not targets:
        print("没有找到任何 */src/main/resources/META-INF/mods.toml", file=sys.stderr)
        return 1
    total = 0
    for p in targets:
        n = fix(p)
        total += n
        rel = p.relative_to(ROOT)
        print(f"{'改写' if n else '跳过'} {rel}: {n} 处")
    # 兜底自检：改完不应再出现 type = "required"|"optional"
    bad = []
    for p in targets:
        txt = p.read_text(encoding="utf-8")
        if TYPE_RE.search(txt) or re.search(r'^\s*type\s*=\s*"', txt, re.M):
            bad.append(str(p.relative_to(ROOT)))
    if bad:
        print("⚠️ 仍有残留:", ", ".join(bad), file=sys.stderr)
        return 1
    print(f"完成：共改写 {total} 处依赖声明，{len(targets)} 个文件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
