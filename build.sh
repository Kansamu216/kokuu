#!/usr/bin/env bash
# VoidkitCore 构建入口（Windows Git Bash / Linux 通用）
#
# 两件事：
#   1) 强制使用 JDK 21（MC 1.21.1 要求；本机默认 java 是 25，Gradle 8 用不了）
#   2) 带上镜像重定向 init script（maven.neoforged.net 国内被污染）
#
# 用法：./build.sh build        → 产出 build/libs/voidkit-<version>.jar
#       ./build.sh runServer    → 直接跑一个开发用服务端
set -e

cd "$(dirname "$0")"

# ---- JDK 21 ----
JDK21="${KOKUU_JDK21:-C:/Users/26703/.jdks/jdk-21.0.12.1+1}"
if [ ! -x "$JDK21/bin/java" ] && [ ! -f "$JDK21/bin/java.exe" ]; then
  echo "找不到 JDK 21: $JDK21"
  echo "请设置 KOKUU_JDK21 指向 JDK 21 安装目录"
  exit 1
fi
export JAVA_HOME="$JDK21"
# Git Bash 只认 POSIX 形式的 PATH 条目，Windows 风格路径会被静默忽略
JDK_POSIX=$(echo "$JDK21" | sed -E 's#^([A-Za-z]):#/\L\1#')
export PATH="$JDK_POSIX/bin:$PATH"

# ---- 代理（如本机有 HTTP 代理则透传给 Gradle） ----
GRADLE_OPTS_EXTRA=()
PROXY_URL="${https_proxy:-${HTTPS_PROXY:-}}"
if [ -n "$PROXY_URL" ]; then
  PROXY_HOST=$(echo "$PROXY_URL" | sed -E 's#^[a-zA-Z]+://##; s#[:/].*$##')
  PROXY_PORT=$(echo "$PROXY_URL" | sed -E 's#^[a-zA-Z]+://[^:]*:##; s#/.*$##')
  if [ -n "$PROXY_HOST" ] && [ -n "$PROXY_PORT" ]; then
    GRADLE_OPTS_EXTRA+=("-Dhttps.proxyHost=$PROXY_HOST" "-Dhttps.proxyPort=$PROXY_PORT"
                        "-Dhttp.proxyHost=$PROXY_HOST" "-Dhttp.proxyPort=$PROXY_PORT")
    echo "[build] 使用代理 $PROXY_HOST:$PROXY_PORT"
  fi
fi

TASK="${1:-build}"
shift || true

echo "[build] JAVA_HOME=$JAVA_HOME"
echo "[build] 任务: $TASK"

# ⚠️ ${arr[@]-} 在空数组时会展开出一个「空字符串参数」（老 bash 行为），
#    Gradle 会把它当成空任务路径报 "Cannot locate matching tasks for an empty path"。
#    用 ${arr[@]+...} 安全展开：数组不存在/为空时不产生任何参数。
./gradlew --init-script mirror.init.gradle "$TASK" "$@" ${GRADLE_OPTS_EXTRA[@]+"${GRADLE_OPTS_EXTRA[@]}"}
