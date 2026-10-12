#!/usr/bin/env bash
# KokuuHub 启动脚本（Linux / macOS）
#
# Windows 用 start-hub.bat；这个脚本是它的等价物，会自动按当前平台挑对应的二进制。
#
# 用法：
#   ./start-hub.sh                        # 默认 0.0.0.0:9000 + ./hubdata
#   KOKUUHUB_LISTEN=127.0.0.1:9000 ./start-hub.sh
#   KOKUUHUB_DATA=/var/lib/kokuuhub ./start-hub.sh
#   ./start-hub.sh -poll 30               # 额外参数直接透传给 kokuuhub
set -euo pipefail

cd "$(dirname "$0")"

# ---------- 挑二进制 ----------
BIN="${KOKUUHUB_BIN:-}"
if [ -z "$BIN" ]; then
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64|Linux-amd64)   BIN="dist/kokuuhub-linux-amd64" ;;
    Linux-aarch64|Linux-arm64)  BIN="dist/kokuuhub-linux-arm64" ;;
    Darwin-arm64)               BIN="dist/kokuuhub-darwin-arm64" ;;
    Darwin-x86_64)              BIN="dist/kokuuhub-darwin-amd64" ;;
  esac
fi
# 退回到"就地 go build"的产物
if [ -z "$BIN" ] || [ ! -x "$BIN" ]; then
  for candidate in ./kokuuhub ./kokuuhub.exe; do
    [ -x "$candidate" ] && { BIN="$candidate"; break; }
  done
fi

if [ -z "$BIN" ] || [ ! -x "$BIN" ]; then
  echo "找不到 kokuuhub 可执行文件。"
  echo "  先交叉编译：./build-all.sh"
  echo "  或本机编译：go build -o kokuuhub ."
  echo "  也可以用 KOKUUHUB_BIN=/path/to/kokuuhub 指定"
  exit 1
fi

LISTEN="${KOKUUHUB_LISTEN:-0.0.0.0:9000}"
DATA="${KOKUUHUB_DATA:-./hubdata}"

echo "============================================================"
echo "  KokuuHub  多服务器管理平台"
echo "  面板地址:  http://${LISTEN/0.0.0.0/本机IP}"
echo "  数据目录:  $DATA"
echo "  账号密码 / 机器令牌: 见启动后下方日志（也存在 $DATA/config.json）"
echo "============================================================"

# exec 让 Ctrl+C / systemd stop / docker stop 的信号直接打到进程上，
# 这样它能优雅退出（Go 侧监听了 SIGINT / SIGTERM）。
exec "$BIN" -listen "$LISTEN" -data "$DATA" "$@"
