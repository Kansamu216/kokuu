#!/usr/bin/env bash
# 把根 README 的「Bug 维修群」章节同步到其余 worktree。
#
# 为什么用脚本而不是手工改：四个 worktree 的 README 结构完全一致，
# 手工改 4 遍容易漏、也容易和主线漂移。这里以根 README 为唯一真源。
#
# 用法： tools/sync-qq-banner.sh [--check]
#   --check  只报告差异，不写入（用于 CI / 提交前自查）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="C:/Users/26703/.workbuddy/binaries/python/envs/default/Scripts/python.exe"

"$PY" - "$ROOT" "$@" <<'EOF'
import pathlib, sys

root = pathlib.Path(sys.argv[1])
check_only = "--check" in sys.argv
BADGE = "[![QQ群](https://img.shields.io/badge/QQ%E7%BE%A4-498700382"
ISSUE = "也可以直接开 [Issue](https://github.com/KokuuWork/voidkit/issues)，但群里回复通常更快。"
START = "---"

def split_block(text: str):
    """抽出根 README 的 QQ 章节：从 Issue 行往上到徽章行之前的那条分隔线。"""
    lines = text.split("\n")
    try:
        b = next(i for i, l in enumerate(lines) if BADGE in l)
    except StopIteration:
        raise SystemExit("根 README 里找不到 QQ 徽章行")
    try:
        e = next(i for i, l in enumerate(lines) if ISSUE in l)
    except StopIteration:
        raise SystemExit("根 README 里找不到 Issue 行")
    if e < b:
        raise SystemExit(f"锚点顺序异常：徽章行 {b} 在 Issue 行 {e} 之后")
    # 从 Issue 行往上回溯到徽章行之前最近的分隔线
    cands = [i for i in range(b, e) if lines[i].strip() == START]
    if not cands:
        raise SystemExit("徽章行与 Issue 行之间没有 --- 分隔线")
    s = cands[-1]
    return s, e, "\n".join(lines[s:e + 1]).strip()

src = (root / "README.md").read_text(encoding="utf-8")
_, _, block = split_block(src)
print(f"片段长度 {len(block)} 字符，起始：{block.splitlines()[1][:60]}…\n")

for wt in ("main", "forge-1211", "fabric"):
    t = root / ".worktrees" / wt / "README.md"
    if not t.exists():
        print(f"{wt}: 不存在，跳过")
        continue
    old = t.read_text(encoding="utf-8")
    if BADGE in old:
        print(f"{wt}: 已有 QQ 章节，需手工核对（不再重复插入）")
        continue
    lines = old.split("\n")
    # 锚点：文档链接行结束、紧跟 </div> 与 --- 的那一段
    try:
        anchor = next(i for i, l in enumerate(lines) if l.strip() == "</div>")
    except StopIteration:
        print(f"{wt}: 找不到 </div> 锚点，跳过")
        continue
    new = "\n".join(lines[:anchor + 1] + ["", block, ""] + lines[anchor + 1:])
    if check_only:
        print(f"{wt}: 待更新（{len(old)} -> {len(new)} 字符）")
        continue
    t.write_text(new, encoding="utf-8", newline="\n")
    print(f"{wt}: 已同步")

if check_only:
    print("\n(check 模式，未写入)")
EOF