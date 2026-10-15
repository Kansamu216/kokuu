#!/usr/bin/env bash
# 启动「1.21.1 Fabric + Voidkit beta0.4 + minecraft-mod-mcp」的 AI 实测环境。
#
# 为什么不用 bridge 的 `minecraft-mod-mcp server`：
#   🚨 它忽略 --java，硬用系统 JDK 25 → Fabric 1.21.1 直接起不来（server-launch.log 里可见）。
#      必须手动用 JDK 21 起。
#
# 为什么不用 tools/verify-installed-server.sh：
#   那个脚本收尾会杀服（给「一次性验收」设计），而 AI 实测要服务端常驻。
#
# 用法：
#   tools/ai-test-env.sh up      # 起服务端（后台常驻）
#   tools/ai-test-env.sh status  # 看端口 / 模块数
#   tools/ai-test-env.sh down    # 按 PID 停
#
# 🚨 进程存活：普通 `nohup ... &` 拉起的进程会随shell 结束被回收（实测），
#    所以这个脚本的 up 必须用**受管理的后台任务**方式跑（AI 侧：工具的
#    run_in_background 参数；人侧：在自己的终端里跑）。
#    客户端用tools/ai-shot-client.sh 起，端口 9876。
#
# 🚨 改完 ops.json 必须**重启服务端 + 玩家重新进服** —— OP 只在登录时下发。
#    UUID 别猜：curl 管理 API 的 /api/players 能拿到服务端认的真实 UUID。
#
# 环境位置（bridge 建的目录，不在仓库内）：
#   服务端  C:/Users/26703/AppData/Roaming/.minecraft/mcp_launcher/server/1.21.1-Forge52.1.0-fabric
#   客户端  C:/Users/26703/AppData/Roaming/.minecraft/mcp_launcher/game
set -u

SRV="$HOME/AppData/Roaming/.minecraft/mcp_launcher/server/1.21.1-Forge52.1.0-fabric"
JAVA21="C:/Users/26703/.jdks/jdk-21.0.12.1+1/bin/javaw.exe"
JAVA21C="C:/Users/26703/.jdks/jdk-21.0.12.1+1/bin/java.exe"
PIDFILE="$SRV/ai-test.pid"
LOG="$SRV/server-ai-test.log"

up() {
  if [ ! -d "$SRV" ]; then
    echo "找不到服务端目录：$SRV"
    echo "先跑一次：minecraft-mod-mcp server 1.21.1 --type fabric   # 让 bridge 建目录"
    exit 2
  fi
  if [ ! -f "$SRV/mods/voidkit_core-0.1.0-beta.4+mc1.21.1-fabric.jar" ]; then
    echo "mods 里没有 Voidkit jar —— 先把 5 个 jar 拷进 $SRV/mods/"
    exit 2
  fi
  if ! ls "$SRV/mods/" | grep -q 'fabric-api'; then
    echo "mods 里没有 fabric-api —— Voidkit 在 Fabric 线是硬依赖 Fabric API 的"
    echo "  从 https://api.modrinth.com/v2/project/fabric-api/version?game_versions=[\"1.21.1\"]&loaders=[\"fabric\"] 取"
    exit 2
  fi

  cd "$SRV" || exit 2
  nohup "$JAVA21" -Xmx2G -Dfile.encoding=UTF-8 -jar fabric-server-launch.jar nogui \
    > "$LOG" 2>&1 &
  echo $! > "$PIDFILE"
  echo "已起服，PID=$(cat "$PIDFILE")，日志：$LOG"
  echo "等待 'Done' …"

  for _ in $(seq 1 60); do
    if grep -q 'Done (' "$LOG" 2>/dev/null; then
      grep -E '模块 .* 已启用|Done \(|管理 API' "$LOG" | tail -3
      return 0
    fi
    sleep 3
  done
  echo "超时未见到 Done，末尾日志："
  tail -15 "$LOG"
  return 1
}

status() {
  echo "=== 端口 ==="
  netstat -ano 2>/dev/null | grep -E ':25565|:8765' | head -4
  echo "=== 模块 / 启动标记 ==="
  grep -E '模块 . * 已启用|Done \(|管理 API' "$LOG" 2>/dev/null | tail -4
  echo "=== 致命错误 ==="
  grep -iE 'FATAL|Exception in thread' "$LOG" 2>/dev/null | head -5
  echo "(以上为空 = 无致命错误)"
}

down() {
  if [ -f "$PIDFILE" ]; then
    PID="$(cat "$PIDFILE")"
    taskkill //PID "$PID" //F 2>&1 | head -2
    rm -f "$PIDFILE"
  else
    echo "没有 $PIDFILE；请手动按端口找 PID（🚨 绝不要 taskkill /IM java）"
  fi
}

case "${1:-up}" in
  up) up ;;
  status) status ;;
  down) down ;;
  *) sed -n '2,20p' "$0" ;;
esac
