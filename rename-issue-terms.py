#!/usr/bin/env python3
"""替换 issue body 里的「查熊/熊孩子/反熊」为专业命名。"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"
ISSUES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

REPLS = [
    ("反熊孩子", "操作审计"),
    ("查熊", "审计"),
    ("熊孩子", "违规行为"),
]


def get_body(num):
    r = subprocess.run(["gh", "api", f"repos/{REPO}/issues/{num}", "--jq", ".body"],
                       capture_output=True, text=True, encoding="utf-8")
    return r.stdout if r.returncode == 0 else None


def edit_body(num, body):
    subprocess.run(["gh", "issue", "edit", str(num), "--repo", REPO, "--body-file", "-"],
                   input=body, capture_output=True, text=True, encoding="utf-8")


def main():
    changed = 0
    for num in ISSUES:
        body = get_body(num)
        if not body:
            continue
        new = body
        for a, b in REPLS:
            new = new.replace(a, b)
        if new != body:
            edit_body(num, new)
            changed += 1
            print(f"✓ 更新 #{num} 正文")
    print(f"共更新 {changed} 个 issue")
    return 0


if __name__ == "__main__":
    sys.exit(main())
