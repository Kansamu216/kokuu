#!/usr/bin/env python3
"""全命令树「执行即崩」审计 —— RCON 驱动，抓执行期异常。

背景：冒烟自检（tools/smoke-test.sh）只验证**加载**与**管理 API**；命令行为
它测不到。而命令是最容易出「玩家一点就报错」的地方（参数缺失、只允许玩家执行
的命令被控制台调用、子命令分发漏分支……）。这个脚本补上那一段：

  1. 用 `help` 把服务端注册的**全部根命令**抓出来（响应会被自动拼完整，见 rcon-exec.py）
  2. 逐条以「无参数」「给一个不存在的参数」两种形态执行一遍
  3. 再对 Voidkit 自己的命令做一批定向探针（子命令分发、双形态、边界值）
  4. 对比执行前后的服务端日志，报告**新出现的异常堆栈**

判据说明（重要，避免误报 / 假绿灯）：
  * **空响应不算问题** —— 很多命令把消息发给玩家而不是控制台，RCON 收不到东西
  * 真正的信号是**日志里的新异常**，以及响应文本里出现 "unexpected error"/堆栈片段
  * `/stop` 这类会关服的命令在 DENY 名单里
  * 🚨 **有效性守卫**：如果一条响应都没拿到，说明通道/解析坏了，脚本会**判自己无效**
    并返回 2 —— 绝不能在「什么都没测到」的情况下打出 ✅

用法：
    python command-audit.py <host> <port> <password> --log <服务端日志路径>
"""
import argparse
import re
import sys
import time

from importlib import util as importlib_util
from pathlib import Path

# 复用同目录的 rcon-exec.py（工具之间共享同一个客户端实现，避免两处漂移）
_spec = importlib_util.spec_from_file_location("rcon_exec", Path(__file__).with_name("rcon-exec.py"))
rcon_exec = importlib_util.module_from_spec(_spec)
_spec.loader.exec_module(rcon_exec)

# 会关服 / 有不可逆副作用的命令，绝不发送
DENY = {"stop", "restart", "shutdown"}

# 已知的玩家向 / 管理向命令，作为「命令表抓取失败」时的兜底（宁可多测，不少测）
FALLBACK_ROOTS = """
back balance bal baltop broadcast clearlag delhome delwarp eco enderchest feed fly god hat
heal home homes info invsee menu money mute pay perf perm playtime rtp seen sell setspawn
sethome setwarp shop spawn speed title tpa tpaccept tpacancel tpall tpdeny tpahere tphere tpo
tps tpacancel trash unmute vanish warp warps who whois workbench vk
""".split()

# 定向探针：Voidkit 自身命令的「坏输入」形态。
# 目标是走通「子命令分发 / 参数解析 / 双形态（对自己 vs 对别人）」这些分支，
# 它们最容易在某一条分支上抛 NPE。
PROBES = [
    # 内核
    "vk", "vk __probe__", "vk reload", "vk save", "vk status", "vk features", "vk api", "vk token",
    # 权限
    "perm", "perm __probe__", "perm group", "perm user", "perm group __probe__",
    "perm group default", "perm group default add", "perm group default add voidkit.chat.color",
    "perm group default remove voidkit.chat.color", "perm user __probe__",
    # 经济（玩家侧 + 管理侧）
    "eco", "eco __probe__", "eco give", "eco give __probe__", "eco give __probe__ 100",
    "eco take __probe__ 100", "eco set __probe__ 100", "eco reset __probe__",
    "money", "balance", "baltop", "pay", "pay __probe__ 1", "shop", "sell", "sell __probe__",
    "worth", "value", "value __probe__",
    # 传送系列（都要「移动方/锚点」「改前/改后」这类成对语义）
    "home", "home __probe__", "sethome", "sethome __probe__", "delhome __probe__",
    "homes", "warp", "warp __probe__", "setwarp", "setwarp __probe__", "delwarp __probe__",
    "warps", "spawn", "setspawn", "back", "tpa", "tpa __probe__", "tpahere __probe__",
    "tpaccept", "tpdeny", "tpacancel", "tphere __probe__", "tpall", "tpo __probe__",
    "rtp", "rtp __probe__",
    # 展示
    "menu", "menu __probe__", "title", "title __probe__", "title set __probe__ __probe__",
    "motd", "tab", "scoreboard", "announce", "announce list",
    # 工具
    "trash", "clearlag", "clearlag __probe__", "perf", "perf __probe__", "whois", "whois __probe__",
    "invsee", "invsee __probe__", "hat", "enderchest", "workbench", "enderchest __probe__",
    # 管理工具（双形态：对自己 / 对别人）
    "fly", "fly __probe__", "god", "god __probe__", "heal", "heal __probe__",
    "feed", "feed __probe__", "speed", "speed 2", "speed __probe__ 2",
    "vanish", "vanish __probe__", "broadcast", "broadcast __probe__", "broadcast * __probe__",
    "mute __probe__", "unmute __probe__", "info", "info __probe__", "seen __probe__",
]

ERR_MARKERS = (
    "An unexpected error occurred",
    "java.lang.NullPointerException",
    "java.lang.IllegalStateException",
    "java.lang.IllegalArgumentException",
    "java.lang.ClassCastException",
    "java.util.NoSuchElementException",
    "IndexOutOfBoundsException",
    "Exception in thread",
    "at org.voidkit",
)


def collect_commands(rcon):
    """用 `help` 抓出全部根命令。

    注意：**不要用 `help <页码>`** —— 1.21.1 里那个形式不成立（会回
    "Unknown command or insufficient permissions"）。裸 `help` 会一次性给出
    完整列表，只是响应被切成多个 RCON 分包，由 rcon-exec.py 负责拼回。
    """
    out = rcon.run("help")
    names = set(re.findall(r"^/([a-z0-9_\-]+)", out, re.MULTILINE))
    if not names:
        # 有些版本会把列表渲染成表格，退一步用宽松匹配
        names = set(re.findall(r"/([a-z][a-z0-9_\-]{1,20})\b", out))
    return sorted(names), len(out)


def snapshot(log_path):
    """记录日志当前行数，便于事后只看新产生的部分。"""
    p = Path(log_path)
    if not p.exists():
        return 0
    try:
        return sum(1 for _ in p.open("rb"))
    except OSError:
        return 0


def tail_from(log_path, start_line):
    p = Path(log_path)
    if not p.exists():
        return []
    with p.open("r", encoding="utf-8", errors="replace") as f:
        return [ln.rstrip() for i, ln in enumerate(f) if i >= start_line]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("host")
    ap.add_argument("port", type=int)
    ap.add_argument("password")
    ap.add_argument("--log", required=True, help="服务端日志路径（用于对比新异常）")
    ap.add_argument("--verbose", action="store_true", help="打印每条命令的响应")
    args = ap.parse_args()

    rcon = rcon_exec.Rcon(args.host, args.port, args.password, timeout=6.0)
    base_line = snapshot(args.log)

    print("=================== 命令审计：抓取命令表 ===================")
    roots, help_len = collect_commands(rcon)
    used_fallback = False
    if not roots:
        used_fallback = True
        print("⚠️  `help` 未解析出任何命令（响应 %d 字符）—— 改用源码兜底清单，"
              "本次结果**不覆盖**未在清单里的命令" % help_len)
        roots = sorted(FALLBACK_ROOTS)
    else:
        print("发现根命令 %d 个：%s" % (len(roots), " ".join(roots[:40]) + (" …" if len(roots) > 40 else "")))

    cases = []
    for name in roots:
        if name in DENY:
            continue
        cases.append(name)                            # 无参数
        cases.append("%s __voidkit_probe__" % name)   # 不存在的参数
    cases.extend(PROBES)
    seen = set()
    cases = [c for c in cases if not (c in seen or seen.add(c))]

    print("")
    print("=================== 执行 %d 条 ===================" % len(cases))
    bad_resp = []
    nonempty = 0
    empties = []
    for i, cmd in enumerate(cases, 1):
        try:
            out = rcon.run(cmd)
        except Exception as exc:  # noqa: BLE001 —— 连接断了也要报出来
            print("  ✗ [%d/%d] %s -> 连接异常 %r" % (i, len(cases), cmd, exc))
            bad_resp.append((cmd, "连接异常 %r" % (exc,)))
            continue
        if out.strip():
            nonempty += 1
        else:
            empties.append(cmd)
        if args.verbose:
            print(">>> %s\n%s" % (cmd, out))
        for marker in ERR_MARKERS:
            if marker in out:
                bad_resp.append((cmd, out.strip()[:300]))
                print("  ✗ [%d/%d] %s -> 响应里含 \"%s\"" % (i, len(cases), cmd, marker))
                break
    rcon.close()
    time.sleep(1.0)  # 给服务器把最后的错误刷进日志

    print("")
    print("=================== 执行后的新增日志异常 ===================")
    new_lines = tail_from(args.log, base_line)
    exc_lines = [ln for ln in new_lines if any(k in ln for k in (
        "Exception", "Error", "错误", "Caused by"))]
    # 过滤掉「正常的失败提示」：参数错误、权限不足、命令不存在都不是异常
    benign = ("Unknown or incomplete command", "Unknown command", "未知", "没有权限",
              "只能由玩家", "requires", "Incorrect argument", "<--[HERE]", "语法错误", "无效")
    real = [ln for ln in exc_lines if not any(b in ln for b in benign)]
    if real:
        print("发现 %d 行疑似异常（原始 %d 行）：" % (len(real), len(exc_lines)))
        for ln in real[:60]:
            print("  " + ln)
    else:
        print("✅ 无新增异常")

    print("")
    print("=================== 结论 ===================")
    if nonempty == 0:
        print("❌ **审计无效**：%d 条命令全都没有响应 —— 说明 RCON 通道或分包解析坏了，"
              "这次结果不能当作通过（见 tools/rcon-exec.py 的说明）" % len(cases))
        return 2
    print("有效响应 %d / %d 条（空响应通常代表「消息只发给玩家」，属正常）" % (nonempty, len(cases)))
    if empties:
        ours = [c for c in empties if c.split()[0] in set(FALLBACK_ROOTS)]
        print("完全无响应 %d 条，其中**我们自己的命令** %d 条：" % (len(empties), len(ours)))
        if ours:
            print("  ⚠️  下面这些「执行了却一声不吭」——玩家会以为命令没生效，值得人工看一眼：")
            for c in ours:
                print("   - %s" % c)
        others = [c for c in empties if c not in ours]
        if others:
            print("  其余（原版/其它模组，通常是把消息只发给了玩家）：%s" % ", ".join(others[:20]))
    if used_fallback:
        print("⚠️  命令表来自兜底清单，未覆盖全部命令")
    if bad_resp:
        print("❌ 响应异常 %d 条：" % len(bad_resp))
        for cmd, txt in bad_resp:
            print("  - %s\n      %s" % (cmd, txt))
    if real or bad_resp:
        print("结果：**有需要排查的地方**（上面逐条列出）")
        return 1
    print("结果：%d 条命令全部执行、无异常、无崩溃迹象 ✅" % len(cases))
    return 0


if __name__ == "__main__":
    sys.exit(main())
