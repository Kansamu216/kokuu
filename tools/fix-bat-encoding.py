# -*- coding: utf-8 -*-
"""
把 UTF-8 + LF 的 .bat 修成 GBK + CRLF。

为什么必须这样：
  cmd.exe 读取 .bat 时按【启动时的控制台代码页】解码，而且 LF-only 行尾解析不可靠。
  UTF-8 汉字是 3 字节，GBK(936) 是双字节 —— 解出来的"半个字符"会吞掉后面一个 ASCII 字符，
  于是 `REM 注释` 和下一行 `echo` 被黏成一条命令：
      '鍣?鈫?娣诲姞鏈嶅姟鍣?echo' 不是内部或外部命令
  `chcp 65001` 写在文件里是救不了的（对自身后续行不可靠）。

修法：GBK 编码 + CRLF，并在 @echo off 后强制 chcp 936（系统设成 UTF-8 时也能正确显示）。
"""
import pathlib
import sys

ROOT = pathlib.Path(r"C:/Users/26703/WorkBuddy/2026-10-12-15-46-51")
TARGETS = [
    "voidkit/hub/start-hub.bat",
    "voidkitserver/start.bat",
    "voidkitserver/run.bat",
]

# GBK 编不了或容易出岔子的字符
REPLACE = {
    "\u2192": "->",   # → 箭头
    "\u2014": "-",    # — 破折号
    "\u2500": "-",    # ─ 制表线
}


def convert(path: pathlib.Path) -> bool:
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        # 已经是 GBK 就直接按 GBK 读
        text = raw.decode("gbk")

    # 统一成 LF 再来处理
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = text.split("\n")

    out = []
    for line in lines:
        stripped = line.strip().lower()
        # 丢掉旧的 chcp（65001 是有害的；936 下面会重新插）
        if stripped.startswith("chcp ") and stripped.endswith(">nul"):
            continue
        for bad, good in REPLACE.items():
            line = line.replace(bad, good)
        out.append(line)

    has_non_ascii = any(ord(ch) > 127 for line in out for ch in line)

    if has_non_ascii:
        for i, line in enumerate(out):
            if line.strip().lower().startswith("@echo off"):
                out.insert(i + 1, "chcp 936 >nul")
                break

    while out and out[-1].strip() == "":
        out.pop()

    data = ("\r\n".join(out) + "\r\n").encode("gbk")
    path.write_bytes(data)
    return has_non_ascii


def main():
    for rel in TARGETS:
        p = ROOT / rel
        if not p.exists():
            print(f"跳过（不存在）: {rel}")
            continue
        try:
            non_ascii = convert(p)
        except UnicodeEncodeError as e:
            print(f"!! {rel} GBK 编码失败: {e}")
            print(f"   问题字符: {e.object[e.start:e.end]!r}")
            return 1
        data = p.read_bytes()
        crlf = data.count(b"\r\n")
        lone_lf = data.count(b"\n") - crlf
        print(f"OK {rel:32s} {len(data):6d}B  CRLF={crlf:3d} 孤立LF={lone_lf}  含中文={'是' if non_ascii else '否'}")

    print()
    print("=== 回读校验（按 GBK 解码）===")
    for rel in TARGETS:
        p = ROOT / rel
        if not p.exists():
            continue
        text = p.read_bytes().decode("gbk")
        first = [l for l in text.splitlines() if l.strip()][:2]
        print(f"{rel:34s} 首两行 -> {first}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
