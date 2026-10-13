#!/usr/bin/env bash
# VoidkitCore 冒烟自检：起一个开发用服务端，等它加载完成，逐个打管理 API，然后收尾。
#
# 目的不是"能不能编译"，而是"模组真的能加载、23 个模块真的注册上了、
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

# ---------- 版本线 ----------
# 仓库进入「多版本 / 多加载器」后，build/libs 里会**同时**躺着不同版本线的 jar
# （如 voidkit_web-beta0.1+mc1.21.1-neoforge.jar 与 +mc1.20.1-forge.jar）。
# 那种情况下按 mtime 取「最新」是不安全的：两条线交替构建时很容易挑错，
# 而把 1.21.1 的 jar 丢进 1.20.1 的开发服会直接加载失败（甚至看不出真正原因）。
# 所以这里显式按**产物后缀**过滤，并把它透传给 build.sh。
#
# 用法：tools/smoke-test.sh -PmcVersion=1.20.1
MCVER="1.21.1"
for arg in "$@"; do
  case "$arg" in
    -PmcVersion=*) MCVER="${arg#-PmcVersion=}" ;;
  esac
done
case "$MCVER" in
  1.20.*) LOADER="forge" ;;
  *)      LOADER="neoforge" ;;
esac
SUFFIX="+mc${MCVER}-${LOADER}"
echo "[smoke] 版本线 $MCVER（加载器 $LOADER，产物后缀 $SUFFIX）"

# ---------- 开发服该装哪种 jar ----------
# 🚨 1.20.1（legacyforge）**必须装 `build/devlibs` 的开发态 jar**，不能装 `build/libs`：
#     - `build/libs`     = reobfJar 产物，方法名是 **SRG**（`m_129921_` / `f_xxxxx_`），只给正式服
#     - `build/devlibs`  = jar 产物，**官方映射名**，与 `runServer` 的开发运行时一致
#    装错的表现不是"装不上"，而是模组能加载、命令能注册、**一部分接口一调用就炸**：
#        java.lang.NoSuchMethodError: 'int net.minecraft.server.MinecraftServer.m_129921_()'
#    （因为 dev 运行时的 Minecraft 是官方名，jar 里却在找 SRG 名。）
#    1.21.x 的 neoforge 没有重混淆这一步，`build/libs` 就是唯一产物 —— 两边需求正好相反。
case "$MCVER" in
  1.20.*) JARDIR="build/devlibs" ;;
  *)      JARDIR="build/libs" ;;
esac
echo "[smoke] 开发服将使用 $JARDIR 下的 jar"

# 合并 jar 后功能粒度不变：整版始终注册 23 个 feature（合并前 8 jar / 合并后 5 jar 都是 23）。
EXPECT_MODULES=23

PY=""
for candidate in "C:/Users/26703/.workbuddy/binaries/python/versions/3.13.12/python.exe" python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
  if [ -f "$candidate" ]; then PY="$candidate"; break; fi
done

mkdir -p build core/run/mods

# MC 服务端首次运行需要同意 EULA，缺文件时会直接退出
if [ ! -f core/run/eula.txt ]; then
  printf 'eula=true\n' > core/run/eula.txt
  echo "[smoke] 已写入 core/run/eula.txt"
fi

# ---------- 隔离运行环境（避免与正在运行的真实服撞车） ----------
printf 'server-port=25599\n' > core/run/server.properties

# ---------- 版本线隔离：切到另一条 MC 版本时，旧存档必须让位 ----------
# 存档里带 DataVersion，1.20.1 **拒绝加载** 1.21.1 生成的 world，会直接启动失败；
# 而且报错长得像「模组加载失败」，极易被误判成移植炸了。
# 用标记文件记住上次跑的是哪条版本线；不一致就把 world 整体**改名保留**
# （mv 而非 rm —— 开发存档虽然是一次性的，但删掉就没法随时切回去对比了），
# 让当前版本线重新生成一份干净的。
MARK="core/run/.voidkit-last-mcver"
LAST_MCVER="$(cat "$MARK" 2>/dev/null || true)"
if [ "$LAST_MCVER" != "$MCVER" ]; then
  for w in world world_nether world_the_end; do
    [ -d "core/run/$w" ] || continue
    bak="core/run/${w}.bak-${LAST_MCVER:-unknown}"
    n=1
    while [ -e "$bak" ]; do bak="core/run/${w}.bak-${LAST_MCVER:-unknown}.$n"; n=$((n + 1)); done
    mv "core/run/$w" "$bak"
    echo "[smoke] 存档版本线不符（上次 ${LAST_MCVER:-未知} → 本次 $MCVER），已移开 core/run/$w → ${bak##*/}"
  done
fi
printf '%s\n' "$MCVER" > "$MARK"

# ---------- 把插件 jar 放进开发服的 mods/ ----------
# 多项目之后 :core:runServer 只加载内核自己。要验证整条链路，必须把各插件产出的
# jar 放进 core/run/mods/ —— 这也正是整合包里的真实加载路径，比在 Gradle 里拼
# sourceSet 更能暴露问题（例如之前那个 modId 不能带连字符的坑，就是启动才发现的）。
#
# ⚠️ **不要放 voidkit_core 的 jar**。
#    :core:runServer 已经通过 moddev 的 mods{} 从 classes 目录加载内核，
#    而 build.gradle 里那句 additionalRuntimeClasspath 又给开发运行补了一份
#    kotlin-stdlib。再把内核 jar（内部 jarJar 嵌套了 kotlin-stdlib）放进来，
#    就会变成两份同名模块，启动直接炸：
#        ResolutionException: reads more than one module named kotlin.stdlib
#    （**只影响开发运行**：整合包里没有 additionalRuntimeClasspath，
#      只有内核 jar 那一份，所以生产环境不会重复）
# ⚠️ 每个插件目录**只取当前版本线、且 mtime 最新的一个 jar**：
#     - 版本线过滤：避免把另一条 MC 版本的 jar 放进 mods/（加载必炸）
#     - mtime 取最新：版本升级后同一条线里也会残留旧 jar（如 voidkit_web-1.1.0 与
#       1.2.0 并存），无脑 glob 会把两个版本都复制进去，同 mod id 冲突。
echo "[smoke] 构建全部子项目（$MCVER）…"
./build.sh build --continue -PmcVersion="$MCVER" > build/smoke-build.log 2>&1
rm -f core/run/mods/*.jar
PLUGINS=0
for d in */$JARDIR; do
  case "$d" in core/*) continue ;; esac
  j=$(ls -t "$d"/voidkit_*"$SUFFIX".jar 2>/dev/null | head -1)
  if [ ! -f "$j" ]; then
    echo "[smoke] ⚠️ $d 下没有 $SUFFIX 的 jar（该模块可能未参与本次构建）"
    continue
  fi
  cp "$j" core/run/mods/
  PLUGINS=$((PLUGINS + 1))
done
# 合并后整版共 5 个 jar：core / essentials / display / utility / web。
# 内核（core）由开发环境从 classes 目录提供、**不放 jar**，所以 mods/ 里应有 4 个：
#   essentials（内含 modId voidkit_perm + voidkit_economy）
#   display   （内含 modId voidkit_title）
#   utility
#   web
# 注意：是 4 个不是 3 个 —— 只有 essentials/display/utility 是「合并出来的」，
# web 虽然原样不动，但它同样是一个要放进 mods/ 的插件 jar（API 自检全靠它）。
EXPECT_PLUGINS=4
echo "[smoke] 已放入 $PLUGINS 个插件 jar（期望 $EXPECT_PLUGINS 个；内核由开发环境提供，不放 jar）"

# ---------- 配置：内核一份 + web 一份（端口隔离 + 令牌）----------
# ⚠️ 配置根目录是 `<服务端目录>/voidkit`，**不是** `<服务端目录>/config/voidkit`。
#    （GAMEDIR 对 :core:runServer 就是 core/run/）
RUN_ROOT="core/run"
mkdir -p "$RUN_ROOT/voidkit/core" "$RUN_ROOT/voidkit/web"
if [ -f tools/smoke-config.json ]; then
  cp tools/smoke-config.json "$RUN_ROOT/voidkit/core/config.json"
  echo "[smoke] 已应用 tools/smoke-config.json（内核配置）"
fi
# 管理 API 的端口与令牌现在归 web 插件管（见 voidkit/web/config.json）
cat > "$RUN_ROOT/voidkit/web/config.json" <<EOF
{
  "enabled": true,
  "bind": "127.0.0.1",
  "port": $API_PORT,
  "token": "smoke-test-token",
  "rateLimitPerSecond": 60
}
EOF
echo "[smoke] 已写 $RUN_ROOT/voidkit/web/config.json（API 端口 $API_PORT，与真实服隔离）"

: > "$LOG"
echo "[smoke] 项目: $PROJECT"
echo "[smoke] 日志: $LOG"

# ---------- 编码：让开发服的日志是可读的中文 ----------
# 🚨 JDK 17（1.20.x 版本线的必需 JDK）在中文 Windows 上默认 file.encoding=GBK
#    —— JEP 400 把默认改成 UTF-8 是 JDK 18 的事，所以 1.21.1（JDK 21）一直没这问题。
#    不处理的话服务端日志里的中文是 GBK，两个后果：
#      1. 用 UTF-8 打开全是乱码（面板 / Hub 读日志尤其难看）
#      2. 本脚本下面那些**中文 pattern 的 grep 会静默失配**，自检形同虚设
#    core/build.gradle 的 jvmArgument 会把参数写进 MDG 生成的 serverRunVmArgs.txt，
#    但实测 dev 运行的**文件 appender** 仍是 GBK；JAVA_TOOL_OPTIONS 由 JVM 启动器读取，
#    必定生效，且会传给 Gradle 派生出来的服务端进程。（生产环境用 -Dfile.encoding=UTF-8 已验证有效。）
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

# stdin 必须保持打开：DedicatedServer 在 stdin 读到 EOF 时会主动关服。
# tail -f /dev/null 永远不产出也不 EOF，正好用来"占住" stdin。
nohup bash -c "tail -f /dev/null | ./build.sh :core:runServer -PmcVersion=$MCVER" > "$LOG" 2>&1 &

# ---------- 等待启动 ----------
# ⚠️ 失败判定必须覆盖**两条版本线的不同崩法**：
#   1.21.x：崩在 mod 加载阶段，打印 "Failed to start the minecraft server"
#   1.20.1：崩在服务端线程（如配置未初始化），打印 "[Server thread/FATAL] /
#           Encountered an unexpected exception"，而且 **Gradle 依然报 BUILD SUCCESSFUL**
#           （runServer 任务本身算成功）—— 少了这条，脚本会空等满 8 分钟才报错。
READY=0
CRASH=0
ROUND=0
for i in $(seq 1 96); do
  ROUND=$i
  sleep 5
  if grep -q 'Done (' "$LOG" 2>/dev/null; then READY=1; break; fi
  if grep -qE 'Failed to start the minecraft server|Exception in thread "main"|A fatal error|Mod loading has failed|Encountered an unexpected exception|\[Server thread/FATAL\]' "$LOG" 2>/dev/null; then
    CRASH=1
    break
  fi
  # Gradle 进程已收工（服务端退出）但没等到 "Done (" —— 也算启动失败
  if grep -qE '^BUILD (SUCCESSFUL|FAILED)' "$LOG" 2>/dev/null; then break; fi
done

echo "[smoke] 等待 $((ROUND * 5))s，启动状态 ready=$READY crash=$CRASH"

echo ""
echo "=================== 模组加载日志 ==================="
grep -nE 'VoidkitCore|管理 API|数据目录|Done \(|已载入|已注册' "$LOG" | head -40

echo ""
echo "=================== 异常日志 ==================="
if grep -qE 'Mod loading has failed|Failed to create mod instance|Exception|Caused by' "$LOG"; then
  grep -nE 'Mod loading has failed|Failed to create mod instance|Exception|Caused by|ERROR' "$LOG" | head -30
else
  echo "（无异常）"
fi

# ---------- 找配置文件与令牌 ----------
# 令牌现在归 web 插件（voidkit/web/config.json），内核配置里已经没有这一项了
CFG=""
TOKEN=""
for candidate in "$RUN_ROOT/voidkit/web/config.json" core/run/config/voidkit/web/config.json; do
  if [ -f "$candidate" ]; then CFG="$candidate"; break; fi
done

if [ -n "$CFG" ]; then
  TOKEN=$(grep -o '"token"[[:space:]]*:[[:space:]]*"[^"]*"' "$CFG" | head -1 | sed -E 's/.*"token"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')
fi

echo ""
echo "=================== 配置文件 ==================="
echo "web 配置: ${CFG:-未找到}"
if [ -n "$CFG" ]; then
  echo "token 长度: ${#TOKEN}"
fi

# ---------- 生成的配置目录一览（验证"配置按插件分家"）----------
echo ""
echo "=================== 生成的配置目录 ==================="
find "$RUN_ROOT/voidkit" -type f 2>/dev/null | sort | sed "s|^$RUN_ROOT/voidkit/|  |"

# ---------- API 自检 ----------
check() {
  local label="$1" path="$2" method="${3:-GET}" body="${4:-}"
  local out code payload
  # ⚠️ --noproxy '*' 不能省：沙箱/开发机上 curl 会走 http_proxy，
  #    而代理连不到 127.0.0.1，于是所有本机请求都返回 502「upstream connect failed」，
  #    看起来像"服务端没起来"，其实是代理伪造的错误。
  if [ -n "$body" ]; then
    out=$(curl --noproxy '*' -s -m 15 -w '\n%{http_code}' -X "$method" \
      -H "X-Auth-Token: $TOKEN" -H 'Content-Type: application/json' \
      -d "$body" "$API$path")
  else
    out=$(curl --noproxy '*' -s -m 15 -w '\n%{http_code}' -X "$method" -H "X-Auth-Token: $TOKEN" "$API$path")
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
check "GET  /api/players" "/api/players?limit=3"
check "GET  /api/logs/joins" "/api/logs/joins?limit=5"
check "GET  /api/motd" "/api/motd"
check "GET  /api/tab" "/api/tab"
check "GET  /api/scoreboard" "/api/scoreboard"
check "GET  /api/config" "/api/config"
check "GET  /api/config/plugins（插件配置快照）" "/api/config/plugins"
check "GET  /api/permissions/groups" "/api/permissions/groups"
check "GET  /api/permissions/users" "/api/permissions/users"
check "GET  /api/permissions/settings" "/api/permissions/settings"
# 新增插件：清扫垃圾桶 / 性能 / 经济 / 公告 / 菜单
check "GET  /api/clearlag" "/api/clearlag"
check "GET  /api/clearlag/trash（垃圾桶内容）" "/api/clearlag/trash"
check "GET  /api/perf（TPS/MSPT/区块热点）" "/api/perf"
check "GET  /api/economy/top" "/api/economy/top"
check "GET  /api/announce" "/api/announce"
check "GET  /api/menus" "/api/menus"
check "POST /api/broadcast" "/api/broadcast" POST '{"message":"[自检] 广播测试"}'
check "POST /api/announce（公告）" "/api/announce" POST '{"message":"[自检] 公告测试 %online% 在线"}'
check "POST /api/clearlag/run" "/api/clearlag/run" POST '{}'
check "POST /api/clearlag/trash/clear" "/api/clearlag/trash/clear" POST '{}'
check "POST /api/motd（写回 MOTD）" "/api/motd" POST '{"rotation":"fixed","variants":[{"lines":["&b自检 MOTD 第一行","&7第二行 %online% 在线"],"weight":1}]}'
check "GET  /api/motd（确认已生效）" "/api/motd"
check "POST /api/reload" "/api/reload" POST '{}'
check "GET  /api/features（重载后）" "/api/features"

# ---------- 模块清单 ----------
# 合并为 5 个 jar 只是**打包方式**变了，功能粒度不能变：
# 合并前 8 个 jar 一共注册 23 个 feature，合并后仍必须是 23 个（一个不少）。
echo ""
echo "=================== 模块启用统计 ==================="
FEATURES_RC=0
if [ -n "$TOKEN" ]; then
  curl -s -m 15 -H "X-Auth-Token: $TOKEN" "$API/api/features" -o "$FEATURES_JSON"
  if [ -n "$PY" ] && [ -f "$FEATURES_JSON" ]; then
    "$PY" - "$FEATURES_JSON" "$EXPECT_MODULES" <<'PYEOF'
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding='utf-8'))
except Exception as exc:
    print('解析失败:', exc); raise SystemExit(1)
expected = int(sys.argv[2])
feats = data.get('features', [])
print(f"总计 {len(feats)} 个模块（期望 {expected}），启用 {sum(1 for f in feats if f.get('enabled'))} 个")
for f in feats:
    mark = '[开]' if f.get('enabled') else '[关]'
    print(f"  {mark} {f['id']:<18} {f.get('name','')}")
if len(feats) != expected:
    print(f"✗ 模块数量不符：期望 {expected}，实际 {len(feats)}（合并不该改变功能粒度）")
    raise SystemExit(1)
PYEOF
    FEATURES_RC=$?
  else
    cat "$FEATURES_JSON"
    FEATURES_RC=1
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
# jps 用哪条版本线的 JDK 都行（它只列进程）；按 1.20.x→17 / 其它→21 的顺序找，避免硬编码单一路径
JPS=""
for c in "C:/Users/26703/.jdks/jdk-17.0.20.1+1/bin/jps.exe" "C:/Users/26703/.jdks/jdk-21.0.12.1+1/bin/jps.exe"; do
  [ -f "$c" ] && { JPS="$c"; break; }
done
if [ -n "$JPS" ]; then
  # 只杀命令行是 devlaunch 的进程，不碰机器上其它 java（比如生产服务端）
  for pid in $("$JPS" -l 2>/dev/null | grep -iE 'devlaunch' | awk '{print $1}'); do
    taskkill //F //PID "$pid" >/dev/null 2>&1 && { echo "已终止服务端进程 pid=$pid"; KILLED=1; }
  done
fi
[ "$KILLED" = "0" ] && echo "（没找到残留的服务端进程，可能已自行退出）"

echo ""
echo "=================== 数据落盘产物 ==================="
find core/run -maxdepth 3 -path '*voidkit*' 2>/dev/null | head -25

echo ""
if [ "$PLUGINS" != "$EXPECT_PLUGINS" ]; then
  echo "[smoke] 失败：插件 jar 数量不符（期望 $EXPECT_PLUGINS，实际 $PLUGINS）—— 合并后应为 essentials/display/utility/web 四个"
  exit 1
fi
if [ "$READY" != "1" ]; then
  echo "[smoke] 失败：服务端未正常启动，请看上面的异常日志"
  exit 1
fi
if [ "$FEATURES_RC" != "0" ]; then
  echo "[smoke] 失败：功能模块数量不符（期望 $EXPECT_MODULES）—— 合并不该丢模块"
  exit 1
fi
if [ "$SHAPE_RC" != "0" ]; then
  echo "[smoke] 失败：API 结构断言未通过（见上面 ✗）—— 接口活着，但返回的数据结构不对"
  exit 1
fi
echo "[smoke] 通过：服务端已加载、$EXPECT_MODULES 个模块已注册、API 可响应且数据结构正确"
