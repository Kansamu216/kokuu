#!/usr/bin/env bash
# 交叉编译 VoidkitHub 的全平台产物。
#
# Hub 是纯 Go（CGO_ENABLED=0），所以在任意一台机器上都能编出所有平台的二进制 ——
# 包括从 Windows 编出 Linux 版，不需要真的有台 Linux。
#
# 用法：./build-all.sh
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p dist

if ! command -v go >/dev/null 2>&1; then
  echo "找不到 go。装一个 Go 1.22+：https://go.dev/dl/"
  exit 1
fi

NAME=voidkithub
# 只发三个平台（2026-10-14 起）：Windows x86-64 / Linux x86-64 / macOS Apple Silicon。
# 用户实际部署面集中在这三类，其余交叉编译产物维护意义不大。
TARGETS="windows/amd64 linux/amd64 darwin/arm64"

for target in $TARGETS; do
  os="${target%/*}"
  arch="${target#*/}"
  out="dist/${NAME}-${os}-${arch}"
  [ "$os" = "windows" ] && out="${out}.exe"

  printf '编译 %-16s ... ' "$os/$arch"
  # -trimpath 去掉本机路径；-s -w 去掉符号表，二进制小一圈
  GOOS="$os" GOARCH="$arch" CGO_ENABLED=0 \
    go build -trimpath -ldflags "-s -w" -o "$out" .
  echo "$(du -h "$out" | cut -f1)"
done

echo ""
echo "产物："
ls -la dist/
echo ""
echo "部署到 Linux 服务器："
echo "  scp dist/voidkithub-linux-amd64 user@server:/opt/voidkithub/"
echo "  ssh user@server 'chmod +x /opt/voidkithub/voidkithub-linux-amd64 && /opt/voidkithub/voidkithub-linux-amd64 -listen 0.0.0.0:9000 -data /var/lib/voidkithub'"
