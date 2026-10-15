#!/usr/bin/env bash
# 启动「带 minecraft-mod-mcp 的 1.21.1 Fabric 客户端」，自动连本机 Voidkit 服务端。
#
# 🚨🚨 两个坑，报错都极具误导，别重走：
#   1. **命令行长度**：libraries 下 105 个 jar → classpath 约 12000 字符，
#      超 Windows 命令行 8191 上限 → 尾部静默截断 →
#      「ClassNotFoundException: net.fabricmc.loader.impl.launch.knot.KnotClient」
#      看着像 loader 没装，其实是命令行太长。解法：classpath 挪进 @argfile。
#   2. **argfile 里不能用反斜杠**：java 的 argfile 把 \ 当转义符，
#      `C:\Users\...` 会被吃掉。所以路径一律写正斜杠。
#   3. classpath 只放真 jar：混入 .json 会被 loader 当 jar 读 →
#      「ZipException: zip END header not found」（文件其实没坏）。
#   4. 🚨 **--assetIndex 必须匹配 MC 版本**：1.21.1 是 **17**（不是 1.20.1 的 8）。
#      写错不会崩，只报「Can't open the resource index file: assets/indexes/8.json」，
#      然后**所有中文渲染成方块** —— 画面其余部分完全正常，极难联想到资源索引。
#
# 用法： tools/ai-shot-client.sh
set -u

MC="$HOME/AppData/Roaming/.minecraft"
GD="$MC/mcp_launcher/game"
JAVA="C:/Users/26703/.jdks/jdk-21.0.12.1+1/bin/java.exe"
VER="fabric-loader-0.16.14-1.21.1"
UUID="2aa52454-2aa5-6454-be1c-14b90f6e2b3a"   # 离线账号 VoidkitTest
ARGFILE="$GD/.vk-client-args.txt"

[ -f "$MC/versions/$VER/$VER.json" ] || { echo "缺版本 JSON，先跑 minecraft-mod-mcp install 1.21.1 --loader fabric"; exit 2; }

# 🚨 路径必须转成 Windows 形式（C:/...）：
#    Git Bash 的 /c/Users/... 只有 bash 认，JVM 认不出 → 找不到 jar 就报
#    ClassNotFoundException。cygpath -m 输出正斜杠的 Windows 路径，正合 argfile 要求。
wpath() { cygpath -m "$1"; }

# 拼 classpath：全部用 Windows 正斜杠路径
CP=""
while read -r j; do
  CP="$CP$(wpath "$j");"
done < <(find "$MC/libraries" -name '*.jar' 2>/dev/null)
CP="$CP$(wpath "$MC/versions/1.21.1/1.21.1.jar")"

LIBPATH=$(wpath "$MC/libraries")
GDDIR=$(wpath "$GD")
ASSDIR=$(wpath "$MC/assets")
ARGFILE_W="$GDDIR/.vk-client-args.txt"

cat > "$ARGFILE_W" <<EOF
-Xmx4G
-Dfile.encoding=UTF-8
-Dmcp.port=9876
-Djava.library.path=$LIBPATH
-cp "$CP"
EOF

echo "classpath jar 数：$(find "$MC/libraries" -name '*.jar' | wc -l)"
cd "$GD" || exit 2
echo "gameDir：$GDDIR"
echo "MCP 调试面板：http://127.0.0.1:9876/debug"
echo "--------------------------------------------------------------"
exec "$JAVA" "@$ARGFILE_W" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --username VoidkitTest \
  --version "$VER" \
  --gameDir "$GDDIR" \
  --assetsDir "$ASSDIR" \
  --assetIndex 17 \
  --uuid "$UUID" \
  --accessToken 0 \
  --userType legacy \
  --versionType release \
  --width 1280 \
  --height 720 \
  --quickPlayMultiplayer 127.0.0.1:25565