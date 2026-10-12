#!/usr/bin/env python3
"""把 inspector 模块里的「查熊/熊孩子/反熊孩子」替换成专业命名（审计/违规行为）。"""
import glob
import os

# 顺序很重要：长的先替换，避免被短词提前吃掉
REPLS = [
    ("反熊孩子", "操作审计"),
    ("查熊（COI）", "操作审计"),
    ("查熊", "审计"),
    ("熊孩子干的", "玩家干的"),
    ("熊孩子", "违规行为"),
]

files = (
    glob.glob("inspector/src/**/*.kt", recursive=True)
    + glob.glob("inspector/src/**/*.gradle", recursive=True)
    + ["inspector/build.gradle"]
)

changed = 0
for path in files:
    if "build/" in path:
        continue
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    new = text
    for a, b in REPLS:
        new = new.replace(a, b)
    if new != text:
        with open(path, "w", encoding="utf-8") as f:
            f.write(new)
        changed += 1
        print("改:", path)

print(f"\n共修改 {changed} 个文件")
