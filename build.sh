#!/usr/bin/env bash
# VoidkitCore 构建入口（Windows Git Bash / Linux 通用）
#
# 两件事：
#   1) 按**版本线**选对 JDK（1.21.x → 21；1.20.x → 17；本机默认 java 是 25，Gradle 8 用不了）
#   2) 带上镜像重定向 init script（maven.neoforged.net 国内被污染）
#
# 用法：./build.sh build        → 产出各模块 build/libs/voidkit_<mod>-<版本>+mc<MC版本>-<加载器>.jar
#       ./build.sh bundleZip    → 再打一个整版 zip 到 build/dist/（Release 附件）
#       ./build.sh runServer    → 直接跑一个开发用服务端
#       ./build.sh <任务> -PmcVersion=1.20.1   → 切到 1.20.1 版本线（会自动改用 JDK 17）
set -e

cd "$(dirname "$0")"

# ---- 按版本线选 JDK ----
# 🚨 不只是「编译目标」的问题：Forge 1.20.1 的 createMinecraftArtifacts（NeoFormRuntime
#    解反编译）**本身**就跑不起来在 JDK 21 上 —— 会用 JDK 21 启动子进程然后 exit 1。
#    所以 1.20.x 必须让 **Gradle 自己**也跑在 JDK 17 上。
MCVER="1.21.1"
for arg in "$@"; do
  case "$arg" in
    -PmcVersion=*) MCVER="${arg#-PmcVersion=}" ;;
  esac
done

case "$MCVER" in
  1.20.*)
    JDK="${KOKUU_JDK17:-C:/Users/26703/.jdks/jdk-17.0.20.1+1}"
    JDK_LABEL="JDK 17（${MCVER} 需要）"
    JDK_ENV_HINT="KOKUU_JDK17"
    ;;
  *)
    JDK="${KOKUU_JDK21:-C:/Users/26703/.jdks/jdk-21.0.12.1+1}"
    JDK_LABEL="JDK 21（${MCVER} 需要）"
    JDK_ENV_HINT="KOKUU_JDK21"
    ;;
esac

if [ ! -x "$JDK/bin/java" ] && [ ! -f "$JDK/bin/java.exe" ]; then
  echo "找不到 $JDK_LABEL：$JDK"
  echo "请设置 $JDK_ENV_HINT 指向对应 JDK 安装目录"
  exit 1
fi
export JAVA_HOME="$JDK"
# Git Bash 只认 POSIX 形式的 PATH 条目，Windows 风格路径会被静默忽略
JDK_POSIX=$(echo "$JDK" | sed -E 's#^([A-Za-z]):#/\L\1#')
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
