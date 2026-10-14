#!/usr/bin/env bash
# 对**已装好的**服务端目录做一次完整验收：起服 → 模块 → API → 全命令树 → MOTD。
#
# 为什么需要它：项目里已有三件各自独立的验证手段，但每次都要手工拼起来：
#   * tools/smoke-test.sh        —— 开发服 + API 断言（只覆盖加载与 API）
#   * tools/command-audit.py     —— 全命令树实跑（抓执行期崩溃）
#   * tools/server-ping.py       —— Server List Ping（验证 MOTD 真的推给了客户端）
# 这个脚本把它们串成一条命令，任意版本线/加载器只要有一个装好 jar 的目录就能跑，
# 用来回答同一个问题：**这一版包，在真机上到底行不行。**
#
# 用法：
#   tools/verify-installed-server.sh <服务端目录> [选项]
#
# 选项：
#   --jdk <路径>        Java 可执行文件所在目录（默认 JDK 21）
#   --port <端口>       服务端监听端口（默认 25565，用于 MOTD ping）
#   --rcon-port <端口>  RCON 端口（默认 25575）
#   --rcon-pass <口令>  RCON 口令（默认 voidkit-verify）
#   --api-port <端口>   管理 API 端口（默认 8766）
#   --wait <秒>         等待 "Done" 的最长秒数（默认 360）
#   --skip-commands     跳过全命令树审计
#   --skip-motd         跳过 MOTD ping
#
# 退出码：0 全过；1 有阶段失败；2 用法/环境错误。
set -u

DIR="${1:-}"
if [ -z "$DIR" ] || [ ! -d "$DIR" ]; then
  sed -n '2,30p' "$0"
  exit 2
fi
shift || true

JDK="${KOKUU_JDK21:-C:/Users/26703/.jdks/jdk-21.0.12.1+1}"
PORT=25565
RCON_PORT=25575
RCON_PASS=voidkit-verify
API_PORT=8766
WAIT=360
SKIP_COMMANDS=0
SKIP_MOTD=0

while [ $# -gt 0 ]; do
  case "$1" in
    --jdk) JDK="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --rcon-port) RCON_PORT="$2"; shift 2 ;;
    --rcon-pass) RCON_PASS="$2"; shift 2 ;;
    --api-port) API_PORT="$2"; shift 2 ;;
    --wait) WAIT="$2"; shift 2 ;;
    --skip-commands) SKIP_COMMANDS=1; shift ;;
    --skip-motd) SKIP_MOTD=1; shift ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$(cd "$SELF_DIR/.." && pwd)"
DIR="$(cd "$DIR" && pwd)"
LOG="$DIR/verify-server.log"

# 找一个可用的 Python（托管版优先）
PY=""
for candidate in \
  "C:/Users/26703/.workbuddy/binaries/python/versions/3.13.12/python.exe" \
  "$(command -v python3 || true)" "$(command -v python || true)"; do
  if [ -n "$candidate" ] && "$candidate" -c "" >/dev/null 2>&1; then PY="$candidate"; break; fi
done
[ -n "$PY" ] || { echo "找不到可用的 Python"; exit 2; }

[ -f "$JDK/bin/java.exe" ] || [ -f "$JDK/bin/java" ] || { echo "找不到 JDK：$JDK"; exit 2; }
JAVA="$JDK/bin/java.exe"
[ -f "$JAVA" ] || JAVA="$JDK/bin/java"

FAILED=0
pass() { echo "  ✅ $1"; }
fail() { echo "  ❌ $1"; FAILED=1; }

# 端口是否空闲。连续验收多台服务端时最容易踩的就是"上一台还没退干净"，
# 表现为 BindException / FAILED TO BIND TO PORT —— 那既不是代码问题，也不该白等超时。
port_free() {
  "$PY" - "$1" <<'PYPORT'
import socket, sys
s = socket.socket()
s.settimeout(1)
try:
    s.bind(("0.0.0.0", int(sys.argv[1])))
except OSError:
    sys.exit(1)
finally:
    s.close()
PYPORT
}

# 与启动无关但会导致"起不来"的日志特征，出现即立刻判定失败，不必等满超时
FATAL_PATTERNS='Failed to start|Mod loading has failed|Incompatible mod set|FAILED TO BIND TO PORT|Address already in use|Encountered an unexpected exception'

echo "=================== 验收开始 ==================="
echo "目录：$DIR"
echo "Java：$JAVA"
echo "Python：$PY"

# ---------- 0. 前置：jar、eula、RCON、API 配置 ----------
JARS=$(ls "$DIR"/mods/voidkit_*.jar 2>/dev/null | wc -l)
if [ "$JARS" -eq 0 ]; then
  fail "mods/ 下没有 voidkit_*.jar，无从验收"
  exit 1
fi
echo "  mods 里的 voidkit jar：$JARS 个"
ls "$DIR"/mods/voidkit_*.jar 2>/dev/null | sed 's#.*/##; s/^/     /'

# 🚨 同一个模块出现多份（不同版本号）= 同名 modId 重复。FML 会自己挑一个（通常是版本最高的）
#    并只打一条 warning —— 于是"验收"可能验的是你并不想测的那个 jar。
#    这种假绿比直接失败危险得多，所以这里硬性拦下。
DUPES=$("$PY" - "$DIR" <<'PYDUP'
import os, re, sys, collections
d = sys.argv[1]
seen = collections.defaultdict(list)
for name in os.listdir(os.path.join(d, "mods")):
    m = re.match(r"(voidkit_[a-z]+)-(.+)\+mc(.+)-(neoforge|forge|fabric)\.jar$", name)
    if m:
        seen[m.group(1)].append(m.group(2))
bad = {k: v for k, v in seen.items() if len(v) > 1}
for k, v in sorted(bad.items()):
    print("     %s: %s" % (k, ", ".join(sorted(v))))
PYDUP
)
if [ -n "$DUPES" ]; then
  fail "mods/ 里同一模块存在多个版本（FML 会自己挑一个，验收结果不可信）："
  echo "$DUPES"
  echo "     请先清理为**只留一个版本**，例如："
  echo "       ls $DIR/mods/voidkit_*.jar | grep -v <要测的版本> | xargs rm"
  exit 1
fi
printf 'eula=true\n' > "$DIR/eula.txt"
"$PY" - "$DIR" "$RCON_PORT" "$RCON_PASS" <<'PY'
import io, os, sys
d, rport, rpass = sys.argv[1], sys.argv[2], sys.argv[3]
p = os.path.join(d, "server.properties")
s = io.open(p, encoding="utf-8").read() if os.path.exists(p) else ""
kv = {"enable-rcon": "true", "rcon.port": rport, "rcon.password": rpass,
      "online-mode": "false", "server-port": "25565"}
for k, v in kv.items():
    if any(l.startswith(k + "=") for l in s.splitlines()):
        s = "\n".join((k + "=" + v) if l.startswith(k + "=") else l for l in s.splitlines())
    else:
        s = s.rstrip("\n") + "\n" + k + "=" + v + "\n"
io.open(p, "w", encoding="utf-8").write(s)
PY

# API（voidkit_web）——端口与令牌写在启动前，起服后即可访问
mkdir -p "$DIR/voidkit/web"
cat > "$DIR/voidkit/web/config.json" <<JSON
{
  "enabled": true,
  "bind": "127.0.0.1",
  "port": $API_PORT,
  "token": "verify-token",
  "rateLimitPerSecond": 60
}
JSON

# ---------- 1. 启动 ----------
LAUNCH_KIND=""
ARGS=()
# ⚠️ 别写死 net/minecraftforge —— NeoForge 1.20.1 装出来是
#    libraries/net/**neoforged**/forge/<ver>/win_args.txt（本机实测踩过）。
#    直接在 libraries 下找 args 文件，两种布局都能覆盖。
ARGS_FILE=$(find "$DIR/libraries" -name 'win_args.txt' 2>/dev/null | head -1)
[ -n "$ARGS_FILE" ] || ARGS_FILE=$(find "$DIR/libraries" -name 'unix_args.txt' 2>/dev/null | head -1)
if [ -n "$ARGS_FILE" ]; then
  LAUNCH_KIND="forge 系（$(echo "$ARGS_FILE" | sed -E 's#.*/libraries/net/##; s#/forge/.*##')）"
  ARGS=("@$DIR/user_jvm_args.txt" "@$ARGS_FILE" "nogui")
elif ls "$DIR"/*fabric*server*launch*.jar >/dev/null 2>&1; then
  LAUNCH_KIND="fabric"
  ARGS=("-Xmx2G" "-Dfile.encoding=UTF-8" "-jar" "$(ls "$DIR"/*fabric*server*launch*.jar | head -1)" "nogui")
else
  echo "  ⚠️  没识别出启动方式（既没有 forge 的 win_args.txt，也没有 fabric server launch jar）"
  echo "      请手动起服后单独跑 command-audit.py / server-ping.py"
  exit 2
fi
echo "启动方式：$LAUNCH_KIND"
if [ "$LAUNCH_KIND" = "forge" ] && [ ! -f "$DIR/user_jvm_args.txt" ]; then
  printf -- '-Xmx2G\n-Dfile.encoding=UTF-8\n' > "$DIR/user_jvm_args.txt"
fi

# 🚨 必须先切到服务端目录：Forge 的 win_args.txt 里引用的是**相对路径**
#    （`-jar forge-<ver>-shim.jar`），在别处启动会直接报 "Unable to access jarfile"。
cd "$DIR" || exit 2

if ! port_free "$PORT"; then
  fail "端口 $PORT 已被占用（多半是上一个服务端还没退干净）—— 先关掉它，或用 --port 换一个"
  exit 2
fi

: > "$LOG"
"$JAVA" "${ARGS[@]}" > "$LOG" 2>&1 &
PID=$!
echo "  服务端 pid=$PID，日志 $LOG"

READY=0
for i in $(seq 1 $((WAIT / 5))); do
  sleep 5
  grep -qa 'Done (' "$LOG" && { READY=1; break; }
  grep -qaE "$FATAL_PATTERNS" "$LOG" && break
done
echo ""
echo "--- 阶段 1：启动 ---"
if [ "$READY" = "1" ]; then
  pass "服务端启动完成（等待 $((i * 5))s）"
  grep -aE 'Voidkit 模块|Done \(' "$LOG" | tail -2 | sed 's/^/     /'
else
  fail "服务端未能启动（等了 $((i * 5))s）"
  echo "     日志尾部："
  tail -25 "$LOG" | sed 's/^/     /'
  taskkill //F //PID "$PID" >/dev/null 2>&1 || true
  exit 1
fi

# ---------- 2. 加载期致命错误 ----------
echo ""
echo "--- 阶段 2：加载期致命错误 ---"
FATAL=$(grep -aE 'NoSuchMethodError|NoClassDefFoundError|InvalidModFileException|ModLoadingException|Failed to create mod instance' "$LOG" | head -5)
if [ -n "$FATAL" ]; then
  fail "发现加载期致命错误："
  echo "$FATAL" | sed 's/^/     /'
else
  pass "无加载期致命错误"
fi

MODS=$(grep -aoE 'Voidkit 模块 [0-9]+ / [0-9]+ 已启用' "$LOG" | tail -1)
if [ -n "$MODS" ]; then
  N=$(echo "$MODS" | grep -oE '[0-9]+ / [0-9]+' | head -1)
  CASE_N="${N%% *}"; TOTAL="${N##* }"
  if [ "$CASE_N" = "$TOTAL" ]; then pass "模块全部启用（$N）"; else fail "模块未全启用（$N）"; fi
else
  fail "日志里没有模块注册信息"
fi

# ---------- 3. 管理 API ----------
echo ""
echo "--- 阶段 3：管理 API ---"
API="http://127.0.0.1:$API_PORT"
TOKEN="verify-token"
api_ok=0
for path in /api/health /api/stats /api/perf /api/features; do
  code=$(curl --noproxy '*' -s -m 15 -o /dev/null -w '%{http_code}' -H "X-Auth-Token: $TOKEN" "$API$path")
  if [ "$code" = "200" ]; then api_ok=$((api_ok + 1)); else echo "     $path -> HTTP $code"; fi
done
if [ "$api_ok" = "4" ]; then pass "4 个只读 API 全部 200"; else fail "只读 API 只有 $api_ok/4 返回 200"; fi

# ---------- 4. 全命令树 ----------
echo ""
echo "--- 阶段 4：全命令树实跑 ---"
if [ "$SKIP_COMMANDS" = "1" ]; then
  echo "  （已跳过）"
else
  if "$PY" "$SELF_DIR/command-audit.py" 127.0.0.1 "$RCON_PORT" "$RCON_PASS" --log "$LOG" | tail -18; then
    pass "全命令树无异常"
  else
    fail "全命令树审计发现问题（见上）"
  fi
fi

# ---------- 5. MOTD ----------
echo ""
echo "--- 阶段 5：MOTD（客户端真正收到的）---"
if [ "$SKIP_MOTD" = "1" ]; then
  echo "  （已跳过）"
else
  MOTD_OUT=$("$PY" "$SELF_DIR/server-ping.py" 127.0.0.1 "$PORT" 1 0.2 2>&1)
  echo "$MOTD_OUT" | sed 's/^/     /'
  if echo "$MOTD_OUT" | grep -q 'motd='; then
    if echo "$MOTD_OUT" | grep -qE '%[a-z_]+%'; then
      fail "MOTD 里还有没被替换的变量（%xxx%）"
    else
      pass "MOTD 可被客户端取到，且变量已解析"
    fi
  else
    fail "取不到 MOTD（SLP 失败）"
  fi
fi
rm -f "$DIR/voidkit/display/motd.json.verifybak" 2>/dev/null || true

# ---------- 收尾 ----------
echo ""
echo "--- 收尾 ---"
# 优雅关服：先用 RCON 发 stop（服务端会正常保存世界、退出）。
# ⚠️ 兜底时**绝不**用 `taskkill /IM java.exe` —— 那会把用户自己的游戏客户端、IDE、
#    Gradle 守护进程一起杀掉（本机实测就有一个 9GB 的 Minecraft 客户端在跑）。
#    只杀**占着这个端口**的那个 PID。
"$PY" "$SELF_DIR/rcon-exec.py" 127.0.0.1 "$RCON_PORT" "$RCON_PASS" stop >/dev/null 2>&1 || true
for _ in $(seq 1 20); do port_free "$PORT" && break; sleep 2; done
if ! port_free "$PORT"; then
  PIDS=$(netstat -ano 2>/dev/null | grep -E ":$PORT[[:space:]].*LISTENING" | awk '{print $NF}' | sort -u)
  for p in $PIDS; do
    [ "$p" = "0" ] && continue
    echo "  端口仍被 PID $p 占用，只强制结束它"
    taskkill //F //PID "$p" >/dev/null 2>&1 || true
  done
  for _ in $(seq 1 10); do port_free "$PORT" && break; sleep 2; done
fi
port_free "$PORT" && echo "  端口 $PORT 已释放" || echo "  ⚠️  端口 $PORT 仍被占用"
taskkill //F //PID "$PID" >/dev/null 2>&1 || true
echo ""
echo "=================== 验收结论 ==================="
if [ "$FAILED" = "0" ]; then
  echo "✅ 全部阶段通过"
  exit 0
fi
echo "❌ 有阶段未通过（见上面 ❌ 行），完整日志：$LOG"
exit 1
