# -*- coding: utf-8 -*-
"""
解剖一个整合包服务端，回答三个决定生死的问题：

  1. 谁在提供 kotlin 运行时？是 KotlinForForge 直接展开的，还是各 mod 自己 jarJar 的？
     —— 如果有两种机制并存，同名类会按 classpath 顺序加载，可能随机崩。
  2. 现有 mod 的 id 里，有没有和 voidkit-* 撞车的？
  3. 这个包在动我们也要动的地方吗（聊天事件 / 记分牌 / TAB / 权限）？

用法: python analyze-pack.py [整合包服务端路径]
"""
import json
import re
import sys
import zipfile
from pathlib import Path

PACK = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    r"C:\Users\26703\Downloads\SkyExplorer-v0.1.2服务端\SkyExplorer-Server-0.1.2")
MODS = PACK / "mods"

INTRINSICS = "kotlin/jvm/internal/Intrinsics.class"
JARJAR_META = "META-INF/jarjar/metadata.json"
TOMLS = ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")


def read_text(zf, name):
    try:
        return zf.read(name).decode("utf-8", "replace")
    except Exception:
        return None


def mod_ids_of(zf):
    """从一个 jar 里抠出它声明的全部 modId。"""
    out = []
    for t in TOMLS:
        raw = read_text(zf, t)
        if not raw:
            continue
        for m in re.finditer(r'^\s*modId\s*=\s*["\']([^"\']+)["\']', raw, re.M):
            out.append(m.group(1))
    # 老式 mcmod.info
    raw = read_text(zf, "mcmod.info")
    if raw:
        for m in re.finditer(r'"modid"\s*:\s*"([^"]+)"', raw):
            out.append(m.group(1))
    return sorted(set(out))


def jarjar_children(zf):
    """读 jarJar 清单，返回 [(groupId:artifactId, version)]。"""
    raw = read_text(zf, JARJAR_META)
    if not raw:
        return []
    try:
        meta = json.loads(raw)
    except Exception:
        return []
    res = []
    for jar in meta.get("jars", []):
        ident = jar.get("identifier", {})
        g = ident.get("group", "")
        a = ident.get("artifact", "")
        v = (jar.get("version") or {}).get("range", "") or ""
        res.append((f"{g}:{a}", v))
    return res


def find_kotlin_version(zf):
    """从 jar 里找 kotlin 版本号（stdlib 会带一个 .kotlin_module 或 properties）。"""
    for n in zf.namelist():
        if n.endswith("kotlin-stdlib.properties") or n == "kotlin/kotlin.kotlin_builtins":
            pass
    raw = read_text(zf, "META-INF/MANIFEST.MF") or ""
    m = re.search(r'(?:Implementation-Version|Bundle-Version|kotlin[.\-]?version)\s*[:=]\s*([0-9]+\.[0-9]+\.[0-9]+)', raw, re.I)
    if m:
        return m.group(1)
    # jarJar 的 metadata 里带版本
    for ident, ver in jarjar_children(zf):
        if "kotlin-stdlib" in ident and ver:
            return f"(jarJar) {ver}"
    return "?"


def main():
    if not MODS.is_dir():
        print(f"[!] 找不到 mods 目录: {MODS}")
        return

    jars = sorted(MODS.glob("*.jar"))
    print(f"整合包: {PACK}")
    print(f"mod 数量: {len(jars)}")
    print()

    kotlin_direct = []      # 直接展开 kotlin/*.class 的
    kotlin_jarjar = []      # 通过 jarJar 提供 kotlin-stdlib 的
    all_modids = {}         # modId -> [jar 名]
    interesting = []        # 碰聊天/记分牌/TAB/权限的

    KEYWORDS = {
        "聊天": ["chat", "speech", "message"],
        "记分牌": ["scoreboard", "sidebar", "objective"],
        "TAB": ["tablist", "playerlist", " chattab", "tab-"],
        "权限": ["permission", "luckperms", "ranks", "prefix"],
        "传送": ["teleport", "warp", "home", "back", "tpa", "spawn"],
        "计分板": ["ftbteams", "teams"],
    }

    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
        except Exception as e:
            print(f"  [!] 打不开 {jar.name}: {e}")
            continue
        names = set(z.namelist())

        if INTRINSICS in names:
            kotlin_direct.append((jar.name, find_kotlin_version(z), jar.stat().st_size))

        kids = jarjar_children(z)
        kk = [k for k in kids if "kotlin" in k[0].lower()]
        if kk:
            kotlin_jarjar.append((jar.name, kk))

        for mid in mod_ids_of(z):
            all_modids.setdefault(mid, []).append(jar.name)

        low = jar.name.lower()
        for label, kws in KEYWORDS.items():
            if any(k in low for k in kws):
                interesting.append((label, jar.name))
                break

    print("=" * 72)
    print("【1】谁在提供 kotlin 运行时")
    print("=" * 72)
    if kotlin_direct:
        print(f"\nA. 直接展开 kotlin 类的 mod（{len(kotlin_direct)} 个）—— 这是 KotlinForForge 那一类：")
        for name, ver, size in kotlin_direct:
            print(f"     {name}")
            print(f"        版本标记={ver}  体积={size / 1024 / 1024:.1f}MB")
    else:
        print("\nA. 没有 mod 直接展开 kotlin 类。")

    if kotlin_jarjar:
        print(f"\nB. 通过 jarJar 嵌套 kotlin 的 mod（{len(kotlin_jarjar)} 个）：")
        for name, kk in kotlin_jarjar:
            print(f"     {name}")
            for ident, ver in kk:
                print(f"        {ident}  {ver}")
    else:
        print("\nB. 没有 mod 通过 jarJar 带 kotlin。")

    print()
    if kotlin_direct and kotlin_jarjar:
        print(">>> 结论：**两种机制并存**！这是必须处理的冲突风险。")
    elif kotlin_direct:
        print(">>> 结论：全部靠直接展开，说明生态统一走 KotlinForForge。")
    elif kotlin_jarjar:
        print(">>> 结论：全部靠 jarJar，说明生态统一走自带。")
    else:
        print(">>> 结论：没有任何 mod 用 kotlin。")

    print()
    print("=" * 72)
    print("【2】mod id 冲突检查（对 voidkit-* 前缀）")
    print("=" * 72)
    mine = ["voidkit-core", "voidkit-essentials", "voidkit-display", "voidkit-perm",
            "voidkit-title", "voidkit-utility", "voidkit-web"]
    clash = [m for m in mine if m in all_modids]
    risky = [m for m in mine if m.replace("voidkit-", "") in all_modids]
    print(f"\n总共 {len(all_modids)} 个 modId")
    print(f"与 voidkit-* 直接撞车: {clash or '无 ✓'}")
    print(f"裸名撞车（如别的 mod 就叫 core）: {risky or '无 ✓'}")
    for probe in ["core", "essentials", "display", "perm", "title", "utility", "web", "voidkit", "voidkit"]:
        if probe in all_modids:
            print(f"  ⚠ 已被占用: {probe}  <- {all_modids[probe]}")

    print()
    print("=" * 72)
    print("【3】会在同一战场交手的 mod")
    print("=" * 72)
    if interesting:
        print()
        for label, name in interesting:
            print(f"  [{label}] {name}")
    else:
        print("\n  没有明显重叠的 mod。")

    print()
    print("=" * 72)
    print("【4】全部 modId 清单")
    print("=" * 72)
    ids = sorted(all_modids)
    for i in range(0, len(ids), 4):
        print("  " + "  ".join(f"{x:<26}" for x in ids[i:i + 4]))


if __name__ == "__main__":
    main()
