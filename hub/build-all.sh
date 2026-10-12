#!/usr/bin/env bash
# 交叉编译 KokuuHub 的全平台产物。
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

NAME=kokuuhub
TARGETS="linux/amd64 linux/arm64 linux/arm windows/amd64 darwin/amd64 darwin/arm64"

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
echo "  scp dist/kokuuhub-linux-amd64 user@server:/opt/kokuuhub/"
echo "  ssh user@server 'chmod +x /opt/kokuuhub/kokuuhub-linux-amd64 && /opt/kokuuhub/kokuuhub-linux-amd64 -listen 0.0.0.0:9000 -data /var/lib/kokuuhub'"
