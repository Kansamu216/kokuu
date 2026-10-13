#!/usr/bin/env bash
# ============================================================================
# 把 NeoFormRuntime 需要的制品**从官方 CDN 灌进 NFRT 缓存**，
# 绕过国内被 DNS 污染的 maven.neoforged.net。
#
# 背景
#   NFRT（NeoFormRuntime）是独立进程，它的仓库地址来自 Forge userdev 配置，
#   **不受 mirror.init.gradle / Gradle 仓库设置影响**。当它要下某个制品时，
#   会去 https://maven.neoforged.net/releases/...，而该域名在国内解析到
#   Facebook/Dropbox 段（污染），必然失败：
#       java.io.IOException: Tunnel failed, got: 502
#
# 原理
#   NFRT 的下载缓存布局是「artifacts/ + 仓库 URL 里 /releases/ 之后的部分」，
#   而官方 CDN（neoforged.forgecdn.net）**镜像了完全相同的路径**。
#   所以把这些 URL 改从 CDN 取、按同样的相对路径写进缓存，NFRT 就不会再下载。
#
# 用法
#   1) 先跑一次构建，拿到含 "DL https://maven.neoforged.net/..." 的日志
#        ./build.sh build -PmcVersion=1.20.1 > /tmp/nfrt.log 2>&1
#   2) 本脚本从日志里捞出所有这类 URL 并灌进缓存
#        tools/fetch-nfrt-artifacts.sh /tmp/nfrt.log
#   3) 重跑构建；若还有新的（每次都会多暴露几个），重复 1-2 步
#      —— 配合 tools/seed-nfrt.sh 可一键循环
#
# 注：这是**一次性**成本。工具链全部下完后，之后的构建都命中缓存。
# ============================================================================
set -u

CACHE="${NFRT_CACHE:-$HOME/.gradle/caches/neoformruntime/artifacts}"
CDN="${NFRT_CDN:-https://neoforged.forgecdn.net/releases}"
POISONED='https://maven\.neoforged\.net/releases/'
LOG="${1:-}"

if [ -z "$LOG" ] || [ ! -f "$LOG" ]; then
  echo "用法: $0 <构建日志文件>"
  echo "  从日志里提取 'DL https://maven.neoforged.net/...' 并改用官方 CDN 下载到 NFRT 缓存。"
  exit 1
fi

mkdir -p "$CACHE"

# 提取唯一的 URL（日志形如：  DL    https://maven.neoforged.net/releases/...）
urls=$(grep -oE "DL +${POISONED}[^ ]+" "$LOG" | sed -E 's/^DL +//' | sort -u)

if [ -z "$urls" ]; then
  echo "[seed-nfrt] 日志里没有需要从 CDN 补的制品（可能已经都补完了）"
  exit 0
fi

ok=0; fail=0; skip=0
while IFS= read -r url; do
  rel="${url#https://maven.neoforged.net/releases/}"
  dst="$CACHE/$rel"
  if [ -s "$dst" ]; then
    skip=$((skip + 1))
    continue
  fi
  mkdir -p "$(dirname "$dst")"
  if curl -s --noproxy '*' -L --fail --max-time 300 -o "$dst" "$CDN/$rel"; then
    printf '  ✓ %s (%s)\n' "$rel" "$(wc -c < "$dst" | tr -d ' ')"
    ok=$((ok + 1))
  else
    printf '  ✗ %s（CDN 上也没有？）\n' "$rel"
    rm -f "$dst"
    fail=$((fail + 1))
  fi
done <<< "$urls"

echo "[seed-nfrt] 新下载 $ok，已存在 $skip，失败 $fail"
[ "$fail" -eq 0 ]
