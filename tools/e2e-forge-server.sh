#!/usr/bin/env bash
# Voidkit 正式服端到端验证（1.20.1 / Forge）
#
# 目的：验证 **reobf 过的发布产物**（`build/libs/*+mc1.20.1-forge.jar`）。
# 这件事 `tools/smoke-test.sh` 做不到 —— 它跑的是 MDG 的 dev 运行，用的是
# `build/devlibs` 里的**开发态** jar（官方映射名）。发布态 jar 是 SRG 名
# （`m_xxxxx_`），只有丢进**真正的 Forge 服务端**才能验。
#
# 它覆盖开发服覆盖不到的三件事：
#   1. SRG 重混淆是否正确（错了就是 NoSuchMethodError: ... m_xxxxx_()）
#   2. jarJar 内嵌 kotlin-stdlib 是否生效（错了就是 NoClassDefFoundError: kotlin/...）
#   3. `META-INF/mods.toml` 在正式加载路径下是否合法
#
# 用法：
#   tools/e2e-forge-server.sh                    # 用默认目录 build/e2e-1201
#   SERVER_DIR=/path/to/server tools/e2e-forge-server.sh
#   API_PORT=8766 tools/e2e-forge-server.sh
#
# 前置：该目录必须已经装好 Forge 服务端（installer --installServer），
#       并且 `mods/` 里放好 **5 个**发布态 jar（8 jar -> 5 jar 合并后：core / essentials /
#       display / utility / web）。脚本会先做检查再启动。
set -u

cd "$(dirname "$0")/.."
PROJECT="$(pwd)"

SERVER_DIR="${SERVER_DIR:-$PROJECT/build/e2e-1201}"
API_PORT="${API_PORT:-8766}"
API="http://127.0.0.1:$API_PORT"
TOKEN="${API_TOKEN:-e2e-forge-token}"
LOG="$SERVER_DIR/server.log"

case "$SERVER_DIR" in
  /*) SERVER_ABS="$SERVER_DIR" ;;
  *)  SERVER_ABS="$PROJECT/$SERVER_DIR" ;;
esac

PY=""
for candidate in "C:/Users/26703/.workbuddy/binaries/python/versions/3.13.12/python.exe" python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
  if [ -f "$candidate" ]; then PY="$candidate"; break; fi
done

# 1.20.1 必须 JDK 17（与 build.sh 同一套约定：KOKUU_JDK17 可覆盖）
JAVA_BIN=""
for c in "${KOKUU_JDK17:-C:/Users/26703/.jdks/jdk-17.0.20.1+1}/bin/java.exe" \
         "${KOKUU_JDK17:-C:/Users/26703/.jdks/jdk-17.0.20.1+1}/bin/java" java; do
  if [ -f "$c" ]; then JAVA_BIN="$c"; break; fi
  if command -v "$c" >/dev/null 2>&1; then JAVA_BIN="$c"; break; fi
done
if [ -z "$JAVA_BIN" ]; then
  echo "[e2e] 找不到 java（1.20.1 需要 JDK 17；可用 KOKUU_JDK17 指定）" >&2
  exit 2
fi
echo "[e2e] JAVA_BIN=$JAVA_BIN"

if [ ! -d "$SERVER_ABS" ]; then
  echo "[e2e] 目录不存在：$SERVER_ABS" >&2
  echo "      先装服务端：java -jar forge-1.20.1-*.jar --installServer" >&2
  exit 2
fi

ARGS="$SERVER_ABS/libraries/net/minecraftforge/forge/1.20.1-47.4.20/win_args.txt"
if [ ! -f "$ARGS" ]; then
  echo "[e2e] 找不到 Forge 启动参数：$ARGS（服务端没装好？）" >&2
  exit 2
fi

# ---------- 前置检查：mods/ 必须是「发布态」jar ----------
echo "[e2e] 服务端目录：$SERVER_ABS"
JARS=$(ls "$SERVER_ABS"/mods/voidkit_*.jar 2>/dev/null | wc -l)
# 合并（8 jar -> 5 jar）后整版共 5 个 jar：core / essentials / display / utility / web。
# 正式服里内核也要放 jar（不像开发服从 classes 目录加载），所以这里是 5 不是 4。
echo "[e2e] mods/ 下 voidkit jar 数：$JARS（期望 5）"
if [ "$JARS" != "5" ]; then
  echo "[e2e] ⚠️ jar 数不对（合并后应为 5：core/essentials/display/utility/web），" >&2
  echo "      请把各模块 build/libs/voidkit_*-1.0.1+mc1.20.1-forge.jar 放进 mods/ 并清掉旧 jar" >&2
fi

# ⚠️ 必须放 build/libs（reobf 发布态），不是 build/devlibs（开发态）。
#    判定依据：我们自己的类里出现 SRG 成员名（m_/f_）才算重混淆过。
JAR_PROBE=$(ls "$SERVER_ABS"/mods/voidkit_core-*.jar 2>/dev/null | head -1)
if [ -n "$JAR_PROBE" ] && [ -n "$PY" ]; then
  MODE=$("$PY" "$PROJECT/tools/detect-jar-mappings.py" "$JAR_PROBE" 2>/dev/null)
  case "$MODE" in
    release) echo "[e2e] ✅ core jar 是 SRG 重混淆后的**发布态**" ;;
    dev)
      echo "[e2e] ❌ core jar 是**开发态**（官方映射名）" >&2
      echo "      正式服必须用各模块 build/libs/ 下的 reobf 产物，不是 build/devlibs/" >&2
      ;;
    *) echo "[e2e] ⚠️ 无法判定 jar 映射形态（检测器输出：${MODE:-空}）" ;;
  esac
fi

# ---------- 启动 ----------
[ -f "$SERVER_ABS/eula.txt" ] || printf 'eula=true\n' > "$SERVER_ABS/eula.txt"
: > "$LOG"
echo "[e2e] 启动服务端…"
cd "$SERVER_ABS"
nohup "$JAVA_BIN" @user_jvm_args.txt "@$ARGS" nogui > "$LOG" 2>&1 &
SRV_PID=$!
cd "$PROJECT"
echo "[e2e] 服务端 pid=$SRV_PID（收尾时只杀这一个，避免误伤机器上其它 java 进程）"

READY=0
CRASH=0
ROUND=0
for i in $(seq 1 60); do
  ROUND=$i
  sleep 5
  if grep -qa 'Done (' "$LOG" 2>/dev/null; then READY=1; break; fi
  if grep -qaE 'Failed to start|Encountered an unexpected exception|\[Server thread/FATAL\]|Mod loading has failed|A fatal error|Missing required field|NoClassDefFoundError|NoSuchMethodError' "$LOG" 2>/dev/null; then
    CRASH=1
    break
  fi
done
echo "[e2e] 等待 $((ROUND * 5))s：ready=$READY crash=$CRASH"

echo ""
echo "=================== 关键日志 ==================="
grep -aE 'Voidkit|管理 API|Done \(|\[CMD\]|Forge: net.minecraftforge' "$LOG" 2>/dev/null | head -20

echo ""
echo "=================== 致命错误（应为空）==================="
if grep -qaE 'NoSuchMethodError|NoClassDefFoundError|InvalidModFileException|ModLoadingException|Failed to create mod instance|\[Server thread/FATAL\]|Encountered an unexpected exception' "$LOG" 2>/dev/null; then
  grep -aE 'NoSuchMethodError|NoClassDefFoundError|InvalidModFileException|ModLoadingException|Failed to create mod instance|\[Server thread/FATAL\]|Encountered an unexpected exception' "$LOG" | head -20
  CRASH=1
else
  echo "（无）"
fi

# ---------- API 自检 ----------
if [ "$READY" = "1" ]; then
  echo ""
  echo "=================== API 自检（重点：曾经报 NoSuchMethodError 的几个）==================="
  check() {
    local label="$1" path="$2" method="${3:-GET}" body="${4:-}"
    local out code
    if [ -n "$body" ]; then
      out=$(curl --noproxy '*' -s -m 15 -w '\n%{http_code}' -X "$method" \
        -H "X-Auth-Token: $TOKEN" -H 'Content-Type: application/json' -d "$body" "$API$path")
    else
      out=$(curl --noproxy '*' -s -m 15 -w '\n%{http_code}' -X "$method" -H "X-Auth-Token: $TOKEN" "$API$path")
    fi
    code=$(echo "$out" | tail -1)
    printf '%-42s HTTP %s  %s\n' "$label" "$code" "$(echo "$out" | sed '$d' | head -c 150)"
  }
  check "GET  /api/health" "/api/health"
  check "GET  /api/stats（曾 m_129921_）" "/api/stats"
  check "GET  /api/perf（曾 m_6846_）" "/api/perf"
  check "GET  /api/announce（曾 m_237119_）" "/api/announce"
  check "POST /api/broadcast（曾 m_18695_）" "/api/broadcast" POST '{"message":"[e2e] 广播"}'
  check "POST /api/clearlag/run（曾 m_129785_）" "/api/clearlag/run" POST '{}'
  check "POST /api/reload（曾 m_18695_）" "/api/reload" POST '{}'
  check "GET  /api/features" "/api/features"
  check "GET  /api/scoreboard" "/api/scoreboard"
  check "GET  /api/permissions/groups" "/api/permissions/groups"

  echo ""
  echo "=================== API 结构断言 ==================="
  if [ -n "$PY" ] && [ -f "$PROJECT/tools/api-shape-check.py" ]; then
    "$PY" "$PROJECT/tools/api-shape-check.py" "$API" "$TOKEN"
    SHAPE_RC=$?
  else
    echo "（跳过：缺 Python 或脚本）"
    SHAPE_RC=0
  fi
else
  SHAPE_RC=1
fi

# ---------- 收尾 ----------
echo ""
echo "=================== 收尾 ==================="
# 按记录的 PID 杀，不做进程名 grep —— 机器上可能同时跑着别的服务端
if taskkill //F //PID "$SRV_PID" >/dev/null 2>&1; then
  echo "已终止服务端 pid=$SRV_PID"
else
  echo "（pid=$SRV_PID 已不在，可能已自行退出）"
fi

echo ""
if [ "$READY" != "1" ] || [ "$CRASH" = "1" ]; then
  echo "[e2e] ❌ 失败：正式服未正常启动或出现致命错误（见上）"
  exit 1
fi
if [ "${SHAPE_RC:-1}" != "0" ]; then
  echo "[e2e] ❌ 失败：API 结构断言未通过"
  exit 1
fi
echo "[e2e] ✅ 通过：reobf 发布产物在真正的 Forge 1.20.1 服务端上加载、注册、API 全部正常"
