#!/usr/bin/env bash
# VoidkitHub 自检：起 Hub → 注册一台 Agent → 验舰队/代理/跨服搜索 → 收尾。
#
# 前置条件：至少有一台游戏服的 voidkit API 在跑（tools 里的 boot-check.sh --keep 可以拉一台）。
#
# 用法：
#   tools/smoke-test.sh                          # 用默认 Agent 地址 http://127.0.0.1:8765
#   AGENT_TOKEN=xxx tools/smoke-test.sh          # 指定 Agent 令牌（不指定则从测试服配置里读）
#   tools/smoke-test.sh --keep                   # 自检完成后保持 Hub 运行
set -u

cd "$(dirname "$0")/.."
HUB_DIR="$(pwd)"
PORT="${HUB_PORT:-9000}"
BASE="http://127.0.0.1:$PORT"
DATA="$HUB_DIR/build/hubdata"
LOG="$HUB_DIR/build/hub.log"
HUB_USER="${HUB_USER:-admin}"
HUB_PASS="${HUB_PASS:-smoke-pass-123}"
AGENT_API="${AGENT_API:-http://127.0.0.1:8765}"
# Agent 配置的候选路径：独立测试服（与 voidkit 同级）优先，其次是 Gradle 开发运行目录
AGENT_CFG="${AGENT_CFG:-}"
if [ -z "$AGENT_CFG" ]; then
  for candidate in \
    "$HUB_DIR/../../voidkitserver/config/voidkit/config.json" \
    "$HUB_DIR/../run/config/voidkit/config.json" \
    "$HUB_DIR/../runs/server/config/voidkit/config.json"; do
    if [ -f "$candidate" ]; then AGENT_CFG="$candidate"; break; fi
  done
fi

MODE="${1:-}"
BIN="$HUB_DIR/voidkithub.exe"
[ -f "$BIN" ] || BIN="$HUB_DIR/voidkithub"

if [ ! -f "$BIN" ]; then
  echo "找不到 voidkithub 可执行文件，先构建："
  echo "  export GOROOT=~/.workbuddy/binaries/go/go && \$GOROOT/bin/go.exe build -o voidkithub.exe ."
  exit 1
fi

mkdir -p "$HUB_DIR/build"

# ---------- --fake：自带两个假 Agent ----------
# 这样自检不依赖真实游戏服，也不需要手工先起一台。
# 假 Agent 会校验 X-Auth-Token，所以顺带能证明"Hub 确实注入了每台服自己的令牌"。
FAKE_PIDS=""
NODE_BIN="${NODE_BIN:-C:/Users/26703/.workbuddy/binaries/node/versions/22.22.2-6/node.exe}"
if [ "$MODE" = "--fake" ]; then
  if [ ! -f "$NODE_BIN" ]; then
    echo "找不到 node：$NODE_BIN（用 NODE_BIN 指定）"
    exit 1
  fi
  "$NODE_BIN" "$HUB_DIR/tools/fake-agent.js" 8787 fake-token-a "假服一区" > "$HUB_DIR/build/fake1.log" 2>&1 &
  FAKE_PIDS="$FAKE_PIDS $!"
  "$NODE_BIN" "$HUB_DIR/tools/fake-agent.js" 8788 fake-token-b "假服二区" > "$HUB_DIR/build/fake2.log" 2>&1 &
  FAKE_PIDS="$FAKE_PIDS $!"
  sleep 2

  AGENT_API="http://127.0.0.1:8787"
  AGENT_TOKEN="fake-token-a"
  AGENT2_API="http://127.0.0.1:8788"
  AGENT2_TOKEN="fake-token-b"
  echo "[hub-smoke] 已拉起 2 个假 Agent（8787 / 8788）"
fi

# unused helper kept for ad-hoc debugging
jsonfield() {
  # 极简 JSON 取值，避免依赖 jq
  "$PY" -c "import json,sys;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$2" 2>/dev/null || true
}

PY="C:/Users/26703/.workbuddy/binaries/python/versions/3.13.12/python.exe"

# ---------- Agent 令牌 ----------
AGENT_TOKEN="${AGENT_TOKEN:-}"
if [ -z "$AGENT_TOKEN" ] && [ -f "$AGENT_CFG" ]; then
  AGENT_TOKEN=$(grep -o '"apiToken"[[:space:]]*:[[:space:]]*"[^"]*"' "$AGENT_CFG" | head -1 | sed -E 's/.*"apiToken"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')
fi
if [ -z "$AGENT_TOKEN" ]; then
  echo "⚠️  拿不到 Agent 令牌（$AGENT_CFG），请用 AGENT_TOKEN=... 传入"
  exit 1
fi
echo "[hub-smoke] Agent: $AGENT_API （令牌长度 ${#AGENT_TOKEN}）"

# ---------- 起 Hub ----------
rm -rf "$DATA"
mkdir -p "$DATA" "$HUB_DIR/build"
: > "$LOG"

nohup "$BIN" -listen "127.0.0.1:$PORT" -data "$DATA" -poll 5 \
  -user "$HUB_USER" -pass "$HUB_PASS" > "$LOG" 2>&1 &
HUB_PID=$!
echo "[hub-smoke] Hub 已拉起 pid=$HUB_PID，日志: $LOG"

for i in $(seq 1 20); do
  sleep 1
  if curl -s -m 3 -o /dev/null "$BASE/hub/api/health"; then break; fi
done

HUB_TOKEN=$("$BIN" -data "$DATA" -show-token 2>/dev/null | tr -d '\r\n')
echo "[hub-smoke] Hub 令牌长度: ${#HUB_TOKEN}"

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; FAILED=1; }
FAILED=0

check() {
  local label="$1" path="$2" method="${3:-GET}" body="${4:-}"
  local out code payload
  if [ -n "$body" ]; then
    out=$(curl -s -m 20 -w '\n%{http_code}' -X "$method" -u "$HUB_USER:$HUB_PASS"  \
      -H 'Content-Type: application/json' -d "$body" "$BASE$path")
  else
    out=$(curl -s -m 20 -w '\n%{http_code}' -X "$method" -u "$HUB_USER:$HUB_PASS"  "$BASE$path")
  fi
  code=$(echo "$out" | tail -1)
  payload=$(echo "$out" | sed '$d')
  if [ "$code" = "200" ]; then ok "$label  HTTP $code  $(echo "$payload" | head -c 150)"
  else fail "$label  HTTP $code  $(echo "$payload" | head -c 200)"; fi
  LAST_PAYLOAD="$payload"
}

echo ""
echo "=================== Hub 自检 ==================="
check "GET  /hub/api/health（免令牌）" "/hub/api/health"

echo ""
echo "--- 认证：面板用账号密码，脚本用机器令牌 ---"
code=$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE/hub/api/servers")
[ "$code" = "401" ] && ok "无凭据 → 401" || fail "无凭据 → $code（应 401）"
code=$(curl -s -m 10 -o /dev/null -w '%{http_code}' -u "$HUB_USER:definitely-wrong" "$BASE/hub/api/servers")
[ "$code" = "401" ] && ok "密码错误 → 401" || fail "密码错误 → $code（应 401）"
code=$(curl -s -m 10 -o /dev/null -w '%{http_code}' -u "$HUB_USER:$HUB_PASS" "$BASE/hub/api/servers")
[ "$code" = "200" ] && ok "账号密码正确 → 200" || fail "账号密码正确 → $code（应 200）"
code=$(curl -s -m 10 -o /dev/null -w '%{http_code}' -H "X-Hub-Token: $HUB_TOKEN" "$BASE/hub/api/servers")
[ "$code" = "200" ] && ok "机器令牌 → 200" || fail "机器令牌 → $code（应 200）"

check "GET  /hub/api/servers（空清单）" "/hub/api/servers"
check "POST /hub/api/servers（注册 Agent）" "/hub/api/servers" POST \
  "{\"name\":\"测试服一区\",\"api\":\"$AGENT_API\",\"token\":\"$AGENT_TOKEN\",\"tags\":[\"测试\"],\"note\":\"自检注册\"}"

# 第二台（可选）：用来验证"舰队"和"跨服玩家搜索"真的是多服合并，而不是只看一台
if [ -n "${AGENT2_API:-}" ] && [ -n "${AGENT2_TOKEN:-}" ]; then
  check "POST /hub/api/servers（注册第二台）" "/hub/api/servers" POST \
    "{\"name\":\"测试服二区\",\"api\":\"$AGENT2_API\",\"token\":\"$AGENT2_TOKEN\",\"tags\":[\"测试\",\"副服\"]}"
fi

check "GET  /hub/api/fleet" "/hub/api/fleet"
check "GET  /hub/api/servers（已含状态）" "/hub/api/servers"
check "GET  /hub/api/players（跨服搜索）" "/hub/api/players?limit=5"

SRV_ID=$(echo "$LAST_PAYLOAD" >/dev/null; curl -s -m 10 -u "$HUB_USER:$HUB_PASS"  "$BASE/hub/api/servers" \
  | "$PY" -c "import json,sys;d=json.load(sys.stdin);print(d['servers'][0]['id'] if d.get('servers') else '')" 2>/dev/null)

if [ -n "$SRV_ID" ]; then
  echo "  服务器 id = $SRV_ID"
  check "GET  /hub/proxy/{id}/api/stats" "/hub/proxy/$SRV_ID/api/stats"
  check "GET  /hub/proxy/{id}/api/features" "/hub/proxy/$SRV_ID/api/features"
  check "GET  /hub/proxy/{id}/api/players" "/hub/proxy/$SRV_ID/api/players?limit=3"
  check "GET  /hub/proxy/{id}/api/clearlag" "/hub/proxy/$SRV_ID/api/clearlag"

  echo ""
  echo "--- 代理错误路径（应 401：代理不带 Hub 令牌） ---"
  code=$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE/hub/proxy/$SRV_ID/api/stats")
  [ "$code" = "401" ] && ok "无令牌访问代理 → 401" || fail "无令牌访问代理 → $code（应为 401）"

  echo ""
  echo "--- 舰队汇总 ---"
  curl -s -m 10 -u "$HUB_USER:$HUB_PASS"  "$BASE/hub/api/fleet" | "$PY" -c "
import json,sys
d=json.load(sys.stdin)
print(f\"  服务器 {d['onlineServers']}/{d['servers']} 在线，玩家 {d['players']}/{d['maxPlayers']}，平均TPS {d['avgTps']:.1f}\")
for it in d['items']:
    tag = '在线' if it['online'] else ('停用' if not it['enabled'] else '离线')
    extra = f\"{it['players']}人 TPS{it['avgTps']:.1f} {it['latencyMs']}ms\" if it['online'] else (it['error'] or '')
    print(f\"    [{tag}] {it['name']:<12} {extra}\")
"
else
  fail "没能取到服务器 id"
fi

# ---------- 令牌隔离验证 ----------
echo ""
echo "--- 令牌隔离：浏览器拿不到 Agent 的 apiToken ---"
if curl -s -m 10 -u "$HUB_USER:$HUB_PASS"  "$BASE/hub/api/servers" | grep -q "$AGENT_TOKEN"; then
  fail "响应里出现了 Agent 的 apiToken（不该出现！）"
else
  ok "响应中没有 Agent 的 apiToken"
fi

# ---------- 收尾 ----------
echo ""
for pid in $FAKE_PIDS; do
  taskkill //F //PID "$pid" >/dev/null 2>&1 && echo "[hub-smoke] 已停止假 Agent pid=$pid"
done

if [ "$MODE" = "--keep" ]; then
  echo "[hub-smoke] 保持 Hub 运行中 pid=$HUB_PID → $BASE"
  echo "[hub-smoke] 面板令牌: $HUB_TOKEN"
  exit 0
fi

taskkill //F //PID "$HUB_PID" >/dev/null 2>&1 && echo "[hub-smoke] 已停止 Hub pid=$HUB_PID"
[ "$FAILED" = "0" ] && echo "[hub-smoke] 全部通过" || echo "[hub-smoke] 有失败项，见上面 ✗"
exit "$FAILED"
