#!/usr/bin/env python3
"""检查编译产物里的中文字符串常量是否完好。

背景：JDK 17 在中文 Windows 上默认 `file.encoding=GBK`（JEP 400 到 JDK 18 才改默认 UTF-8）。
如果**编译器或运行时**用平台默认编码去处理 UTF-8 源码/字符串，中文就会坏掉。
这个脚本直接在字节层面确认「坏」发生在哪一环：

  1. 源码 .kt 里的字面量（UTF-8 字节）
  2. 编译产物 .class 的常量池（Java 用 modified UTF-8）
  3. 运行期日志

只要 2 里能找到完整的 UTF-8 字节序列，就说明编译没问题，坏在输出环节。
"""
import sys
import zipfile
from pathlib import Path

NEEDLE = "已注册".encode("utf-8")          # E5 B7 B2 E6 B3 A8 E5 86 8C
NEEDLE2 = "个内置变量".encode("utf-8")


def scan_blob(label: str, data: bytes) -> None:
    n1 = data.count(NEEDLE)
    n2 = data.count(NEEDLE2)
    print(f"  {label}: '已注册' 命中 {n1} 次，'个内置变量' 命中 {n2} 次")
    if n1 or n2:
        i = data.find(NEEDLE) if n1 else data.find(NEEDLE2)
        print(f"    现场字节: {data[max(0,i-8):i+40]!r}")


def scan_jar(label: str, jar: Path) -> None:
    print(f"{label}: {jar}")
    try:
        with zipfile.ZipFile(jar) as z:
            total1 = total2 = 0
            for name in z.namelist():
                if name.endswith(".class") and name.startswith("org/voidkit/"):
                    d = z.read(name)
                    total1 += d.count(NEEDLE)
                    total2 += d.count(NEEDLE2)
            print(f"  class 常量池: '已注册' {total1} 次，'个内置变量' {total2} 次")
    except Exception as exc:
        print(f"  读取失败: {exc}")


def scan_dir(label: str, root: Path) -> None:
    print(f"{label}: {root}")
    total1 = total2 = 0
    for p in root.rglob("*.class"):
        d = p.read_bytes()
        total1 += d.count(NEEDLE)
        total2 += d.count(NEEDLE2)
    print(f"  class 常量池: '已注册' {total1} 次，'个内置变量' {total2} 次")


def main(argv: list[str]) -> int:
    print("== 源码（UTF-8 字节）==")
    for p in Path(".").rglob("*.kt"):
        if "build" in p.parts:
            continue
        d = p.read_bytes()
        if NEEDLE in d or NEEDLE2 in d:
            scan_blob(str(p), d)

    print("\n== 编译产物 ==")
    classes = Path("core/build/classes/kotlin/main")
    if classes.is_dir():
        scan_dir("dev classes", classes)
    for pattern in (
        "core/build/libs/voidkit_core-0.1.0-beta.3+mc1.20.1-forge.jar",
        "core/build/devlibs/voidkit_core-0.1.0-beta.3+mc1.20.1-forge.jar",
    ):
        p = Path(pattern)
        if p.is_file():
            scan_jar("jar", p)

    print("\n== 运行期日志 ==")
    for pattern in ("core/run/logs/latest.log", "build/e2e-1201/logs/latest.log"):
        p = Path(pattern)
        if p.is_file():
            d = p.read_bytes()
            scan_blob(str(p), d)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
