#!/usr/bin/env bash
# KokuuCore 冒烟自检：起一个开发用服务端，等它加载完成，逐个打管理 API，然后收尾。
#
# 目的不是"能不能编译"，而是"模组真的能加载、15 个模块真的注册上了、
# 管理 API 真的能响应、配置写回与热重载真的生效"。
#
# 用法：tools/smoke-test.sh
#       KOKUU_SMOKE_LOG=/tmp/x.log tools/smoke-test.sh
#
# ⚠️ 不覆盖：优雅关服（onServerStop → DataStore.shutdown）没法在这里端到端验证 ——
# Gradle 的 runServer 用自己接管的 stdin，控制台指令进不到服务端，只能强杀。
# 不过落盘本身是"写时即落"的（玩家档案原子写、joins.jsonl 每行 flush），
# 强杀不会丢已产生的数据，关服时只是再保险地整体保存一次。
set -u

cd "$(dirname "$0")/.."
PROJECT="$(pwd)"

# 注意：日志/临时文件都用**项目相对路径**。
# Git Bash 的 /tmp 与 Windows 版 python 眼中的 /tmp 不是同一个地方，
# 混用会出现"文件明明写了却读不到"。bash 侧无所谓，跨到 python 就必须统一。
LOG="${KOKUU_SMOKE_LOG:-$PROJECT/build/smoke-server.log}"
FEATURES_JSON="$PROJECT/build/smoke-features.json"

# ⚠️ 自检用**独立端口**：开发机上常年有一个真实测试服在跑（25565 / 8765）。
# 不避开的话，要么 BindException 起不来，要么连到别人的 API 上、拿旧代码的结果误判为通过。
API_PORT="${KOKUU_SMOKE_API_PORT:-8766}"
API="http://127.0.0.1:$API_PORT"

PY=""
for candidate in "C:/Users/26703/.workbuddy/binaries/python/versions/3.13.12/python.exe" python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
  if [ -f "$candidate" ]; then PY="$candidate"; break; fi
done

mkdir -p build run

# MC 服务端首次运行需要同意 EULA，缺文件时会直接退出
if [ ! -f run/eula.txt ]; then
  printf 'eula=true\n' > run/eula.txt
  echo "[smoke] 已写入 run/eula.txt"
fi

# ---------- 隔离运行环境（避免与正在运行的真实服撞车） ----------
printf 'server-port=25599\n' > run/server.properties

mkdir -p run/config/kokuucore
if [ -f tools/smoke-config.json ]; then
  cp tools/smoke-config.json run/config/kokuucore/config.json
  echo "[smoke] 已应用 tools/smoke-config.json（API 端口 $API_PORT，与真实服隔离）"
else
  echo "[smoke] ⚠️ 缺 tools/smoke-config.json —— 可能会撞上真实服的 8765"
fi

: > "$LOG"
echo "[smoke] 项目: $PROJECT"
echo "[smoke] 日志: $LOG"

# stdin 必须保持打开：DedicatedServer 在 stdin 读到 EOF 时会主动关服。
# tail -f /dev/null 永远不产出也不 EOF，正好用来"占住" stdin。
nohup bash -c 'tail -f /dev/null | ./build.sh runServer' > "$LOG" 2>&1 &

# ---------- 等待启动 ----------
READY=0
ROUND=0
for i in $(seq 1 96); do
  ROUND=$i
  sleep 5
  if grep -q 'Done (' "$LOG" 2>/dev/null; then READY=1; break; fi
  if grep -qE 'Failed to start the minecraft server|Exception in thread "main"|A fatal error|Mod loading has failed' "$LOG" 2>/dev/null; then break; fi
done

echo "[smoke] 等待 $((ROUND * 5))s，启动状态 ready=$READY"

echo ""
echo "=================== 模组加载日志 ==================="
grep -nE 'KokuuCore|管理 API|数据目录|Done \(|已载入|已注册' "$LOG" | head -40

echo ""
echo "=================== 异常日志 ==================="
if grep -qE 'Mod loading has failed|Failed to create mod instance|Exception|Caused by' "$LOG"; then
  grep -nE 'Mod loading has failed|Failed to create mod instance|Exception|Caused by|ERROR' "$LOG" | head -30
else
  echo "（无异常）"
fi

# ---------- 找配置文件与令牌 ----------
CFG=""
for candidate in run/config/kokuucore/config.json runs/server/config/kokuucore/config.json; do
  if [ -f "$candidate" ]; then CFG="$candidate"; break; fi
done

TOKEN=""
if [ -n "$CFG" ]; then
  TOKEN=$(grep -o '"apiToken"[[:space:]]*:[[:space:]]*"[^"]*"' "$CFG" | head -1 | sed -E 's/.*"apiToken"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')
fi

echo ""
echo "=================== 配置文件 ==================="
echo "路径: ${CFG:-未找到}"
if [ -n "$CFG" ]; then
  echo "apiToken 长度: ${#TOKEN}"
fi

# ---------- API 自检 ----------
check() {
  local label="$1" path="$2" method="${3:-GET}" body="${4:-}"
  local out code payload
  if [ -n "$body" ]; then
    out=$(curl -s -m 15 -w '\n%{http_code}' -X "$method" \
      -H "X-Auth-Token: $TOKEN" -H 'Content-Type: application/json' \
      -d "$body" "$API$path")
  else
    out=$(curl -s -m 15 -w '\n%{http_code}' -X "$method" -H "X-Auth-Token: $TOKEN" "$API$path")
  fi
  code=$(echo "$out" | tail -1)
  payload=$(echo "$out" | sed '$d')
  printf '%-40s HTTP %s  %s\n' "$label" "$code" "$(echo "$payload" | head -c 170)"
}

echo ""
echo "=================== API 自检 ==================="
check "GET  /api/health（免令牌）" "/api/health"
check "GET  /api/stats" "/api/stats"
check "GET  /api/features" "/api/features"
check "GET  /api/placeholders" "/api/placeholders"
check "GET  /api/clearlag" "/api/clearlag"
check "GET  /api/players" "/api/players?limit=3"
check "GET  /api/logs/joins" "/api/logs/joins?limit=5"
check "GET  /api/motd" "/api/motd"
check "GET  /api/tab" "/api/tab"
check "GET  /api/scoreboard" "/api/scoreboard"
check "GET  /api/groups" "/api/groups"
check "GET  /api/config" "/api/config"
check "GET  /api/permissions/groups" "/api/permissions/groups"
check "GET  /api/permissions/users" "/api/permissions/users"
check "GET  /api/permissions/settings" "/api/permissions/settings"
check "POST /api/broadcast" "/api/broadcast" POST '{"message":"[自检] 广播测试"}'
check "POST /api/clearlag/run" "/api/clearlag/run" POST '{}'
check "POST /api/motd（写回 MOTD）" "/api/motd" POST '{"rotation":"fixed","variants":[{"lines":["&b自检 MOTD 第一行","&7第二行 %online% 在线"],"weight":1}]}'
check "GET  /api/motd（确认已生效）" "/api/motd"
check "POST /api/reload" "/api/reload" POST '{}'
check "GET  /api/features（重载后）" "/api/features"

# ---------- 模块清单 ----------
echo ""
echo "=================== 模块启用统计 ==================="
if [ -n "$TOKEN" ]; then
  curl -s -m 15 -H "X-Auth-Token: $TOKEN" "$API/api/features" -o "$FEATURES_JSON"
  if [ -n "$PY" ] && [ -f "$FEATURES_JSON" ]; then
    "$PY" - "$FEATURES_JSON" <<'PYEOF'
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding='utf-8'))
except Exception as exc:
    print('解析失败:', exc); raise SystemExit(1)
feats = data.get('features', [])
print(f"总计 {len(feats)} 个模块，启用 {sum(1 for f in feats if f.get('enabled'))} 个")
for f in feats:
    mark = '[开]' if f.get('enabled') else '[关]'
    print(f"  {mark} {f['id']:<18} {f.get('name','')}")
PYEOF
  else
    cat "$FEATURES_JSON"
  fi
fi

# ---------- API 结构断言 ----------
# 只看 HTTP 200 是不够的：曾经 ApiJson.put 把 List 序列化成字符串，
# 所有接口都返回 200，但前端 .map() 全部报 "is not a function"。
echo ""
echo "=================== API 结构断言 ==================="
SHAPE_RC=0
if [ -n "$TOKEN" ] && [ -n "$PY" ]; then
  "$PY" "$PROJECT/tools/api-shape-check.py" "$API" "$TOKEN"
  SHAPE_RC=$?
else
  echo "（跳过：缺令牌或 Python）"
fi

# ---------- 收尾 ----------
echo ""
echo "=================== 收尾 ==================="
KILLED=0
JPS="C:/Users/26703/.jdks/jdk-21.0.12.1+1/bin/jps.exe"
if [ -f "$JPS" ]; then
  # 只杀命令行是 devlaunch 的进程，不碰机器上其它 java（比如生产服务端）
  for pid in $("$JPS" -l 2>/dev/null | grep -iE 'devlaunch' | awk '{print $1}'); do
    taskkill //F //PID "$pid" >/dev/null 2>&1 && { echo "已终止服务端进程 pid=$pid"; KILLED=1; }
  done
fi
[ "$KILLED" = "0" ] && echo "（没找到残留的服务端进程，可能已自行退出）"

echo ""
echo "=================== 数据落盘产物 ==================="
find run -maxdepth 3 -path '*kokuucore*' 2>/dev/null | head -20

echo ""
if [ "$READY" != "1" ]; then
  echo "[smoke] 失败：服务端未正常启动，请看上面的异常日志"
  exit 1
fi
if [ "$SHAPE_RC" != "0" ]; then
  echo "[smoke] 失败：API 结构断言未通过（见上面 ✗）—— 接口活着，但返回的数据结构不对"
  exit 1
fi
echo "[smoke] 通过：服务端已加载、模块已注册、API 可响应且数据结构正确"
