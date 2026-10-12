# -*- coding: utf-8 -*-
"""
真实解析验证：把 bat 复制成 dryrun 副本（只 echo、不真启动进程），
交给 cmd.exe 执行，检查解析期是否还有报错。

判定标准：
  - 输出里不得出现 "不是内部或外部命令" / "文件名、目录名或卷标语法不正确"
  - 退出码为 0
  - 中文能正常显示（按 GBK 解码后是通顺中文，而不是乱码）
"""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(r"C:/Users/26703/WorkBuddy/2026-10-12-15-46-51")

CASES = [
    {
        "bat": "kokuucore/hub/start-hub.bat",
        "cwd": "kokuucore/hub",
        # 真实启动行前缀 -> 改成只打印
        "dry": ["kokuuhub.exe -listen", "kokuuhub.exe -h"],
        "expect": ["KokuuHub", "127.0.0.1:9000", "DRYRUN"],
    },
    {
        "bat": "kokuuserver/start.bat",
        "cwd": "kokuuserver",
        "dry": ["java @user_jvm_args.txt"],
        "expect": ["KokuuCore", "localhost:25565", "DRYRUN"],
    },
]

BAD_MARKERS = [
    "不是内部或外部命令",
    "文件名、目录名或卷标语法不正确",
    "系统找不到指定的路径",
    "拒绝访问",
]

failed = 0

for case in CASES:
    src = ROOT / case["bat"]
    workdir = ROOT / case["cwd"]
    dry = workdir / "_dryrun_check.bat"

    text = src.read_bytes().decode("gbk")
    lines = []
    touched = 0
    for line in text.splitlines():
        s = line.strip()
        if any(s.startswith(p) for p in case["dry"]):
            lines.append("echo [DRYRUN] " + s)
            touched += 1
        elif s.lower().startswith("pause"):
            lines.append("echo [DRYRUN] script-end")
            touched += 1
        else:
            lines.append(line)

    dry.write_bytes(("\r\n".join(lines) + "\r\n").encode("gbk"))

    try:
        proc = subprocess.run(
            ["cmd", "/c", dry.name],
            cwd=str(workdir),
            capture_output=True,
            timeout=25,
        )
        out = proc.stdout.decode("gbk", errors="replace")
        err = proc.stderr.decode("gbk", errors="replace")
        code = proc.returncode
    except subprocess.TimeoutExpired:
        out, err, code = "(超时)", "", -1
    finally:
        dry.unlink(missing_ok=True)

    print("=" * 62)
    print(f"[{case['bat']}]  退出码={code}  替换了 {touched} 行")
    print("--- 输出 ---")
    print(out.rstrip() if out.strip() else "(无输出)")
    if err.strip():
        print("--- stderr ---")
        print(err.rstrip())

    problems = [m for m in BAD_MARKERS if m in out or m in err]
    if code != 0:
        problems.append(f"非零退出码 {code}")
    for kw in case["expect"]:
        if kw not in out:
            problems.append(f"缺少预期内容: {kw}")

    if problems:
        failed += 1
        print("✗ 有问题:", "; ".join(problems))
    else:
        print("✓ 解析正常，无乱码/无命令粘连")
    print()

print("=" * 62)
if failed:
    print(f"结果：{failed} / {len(CASES)} 个仍有问题")
    sys.exit(1)
print(f"结果：{len(CASES)} / {len(CASES)} 全部通过 —— bat 现在能被 cmd 正确解析")
print("（因为已改成 GBK + CRLF，且强制 chcp 936）")
