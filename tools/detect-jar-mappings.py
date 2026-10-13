#!/usr/bin/env python3
"""判定一个 voidkit jar 是「开发态」还是「发布态（SRG 重混淆）」。

为什么需要它：
  1.20.1（MDG legacyforge）会产出**两套** jar，用途不能混：

    build/devlibs/voidkit_core-...jar  开发态，方法名是**官方映射名**（getTickCount）
                                        → 只给 MDG 的 runServer / IDE 调试
    build/libs/voidkit_core-...jar     发布态，方法名是 **SRG**（m_129921_）
                                        → 只给正式 Forge 服务端 / 整合包

  装反了**不会**"装不上"，而是模组能加载、命令能注册，跑到某条代码路径才炸：
      java.lang.NoSuchMethodError: 'int net.minecraft.server.MinecraftServer.m_129921_()'
  这个报错不看 jar 内部根本想不到是"装错 jar 了"。

用法：
  tools/detect-jar-mappings.py <jar>

输出（stdout，单行）：
  release   —— 含 SRG 名（m_/f_），是发布态
  dev       —— 不含 SRG 名但含官方名，是开发态
  unknown   —— 样本太少，判不了（不臆断）

退出码：0 release / 1 dev / 2 unknown / 3 参数错误
"""
import re
import sys
import zipfile
from pathlib import Path

# SRG 成员名：m_12345_ / f_12345_ / p_i12345_（参数名）
SRG_RE = re.compile(rb"\bm_\d+_\b|\bf_\d+_\b")
# 只挑几个稳定的官方映射名当"开发态"指纹（1.20.1 与 1.21.1 都有）
MOJ_RE = re.compile(
    rb"getTickCount|hasCustomHoverName|setHoverName|getServerDirectory|"
    rb"getAverageTickTime|addObjective|getOrCreatePlayerScore|broadcastSystemMessage"
)


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"用法：{argv[0]} <jar>", file=sys.stderr)
        return 3
    jar = Path(argv[1])
    if not jar.is_file():
        print(f"文件不存在：{jar}", file=sys.stderr)
        return 3

    srg = moj = classes = 0
    try:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if not name.endswith(".class"):
                    continue
                # 只看我们自己的代码，别把嵌套的 kotlin-stdlib 等算进来
                if not name.startswith("org/voidkit/"):
                    continue
                try:
                    data = z.read(name)
                except Exception:
                    continue
                classes += 1
                srg += len(SRG_RE.findall(data))
                moj += len(MOJ_RE.findall(data))
    except zipfile.BadZipFile:
        print(f"不是合法 zip/jar：{jar}", file=sys.stderr)
        return 3

    if classes == 0:
        print("unknown")
        return 2
    # SRG 名只可能来自重混淆：有一个就足以判定
    if srg > 0:
        print("release")
        return 0
    if moj > 0:
        print("dev")
        return 1
    print("unknown")
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
