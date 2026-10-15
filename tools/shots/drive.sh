#!/usr/bin/env bash
# Voidkit 截图驱动 —— 封装 minecraft-mod-mcp 的指令调用 + 截图落盘
#
# ⚠️ 协议实测要点（别照抄文档，文档里的端点名是错的）：
#   · 唯一指令入口是 POST /api/cmd，body = {"cmd": "<名称>", ...参数平铺}。
#     **不存在** /api/press_key、/api/execute_command 这类独立 REST 端点（全部 404）。
#   · /api/screenshot 是**唯一不需要控制模式**的端点，直接返回 base64 PNG。
#   · 其余指令（press_key/type_text/inject_click/execute_command）都要求
#     ESC > MCP Take Over 已进入控制模式，否则 {"error":"not in control mode"}。
#   · 画面尺寸 = 客户端窗口实际尺寸（本机 854x480），不是 1920x1080。
#     inject_click 的坐标必须落在这个范围内。
#   · 截图有 ~1s 延迟（RLE 编码 + 读回），截完立刻拿去做关键判断会拿到上一帧。
set -u
MCP="http://127.0.0.1:9876"
PY="C:/Users/26703/.workbuddy/binaries/python/envs/default/Scripts/python.exe"
SHOTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/docs/release/images/shots"
mkdir -p "$SHOTS"

# cmd <名称> [k=v ...] —— 参数以 key=value 传，内部转 JSON
cmd() {
  local name="$1"; shift
  C:/Users/26703/.workbuddy/binaries/python/envs/default/Scripts/python.exe - "$MCP" "$name" "$@" <<'EOF'
import json, sys, urllib.request
url, name, args = sys.argv[1], sys.argv[2], sys.argv[3:]
body = {"cmd": name}
for a in args:
    k, _, v = a.partition("=")
    try: v = json.loads(v)
    except Exception: pass
    body[k] = v
req = urllib.request.Request(url + "/api/cmd", json.dumps(body).encode(),
                             {"Content-Type": "application/json"})
try:
    with urllib.request.urlopen(req, timeout=20) as r:
        print(r.read().decode("utf-8", "replace")[:400])
except Exception as e:
    print("ERR", e)
EOF
}

# shot <文件名> —— 截图并落盘，打印尺寸
shot() {
  local name="${1:?用法: shot <文件名>}"
  C:/Users/26703/.workbuddy/binaries/python/envs/default/Scripts/python.exe - "$MCP" "$SHOTS/$name" <<'EOF'
import base64, json, struct, sys, urllib.request
url, out = sys.argv[1], sys.argv[2]
with urllib.request.urlopen(url + "/api/screenshot", timeout=25) as r:
    d = json.loads(r.read().decode())
b = base64.b64decode(d["original"].split(",", 1)[1])
w, h = struct.unpack(">II", b[16:24])
open(out, "wb").write(b)
print(f"  -> {out.split('/')[-1]}  {w}x{h}  {len(b)//1024} KB")
EOF
}

# 组合：发指令 → 等帧 → 截图
act_shot() {
  local name="$1" out="$2"; shift 2
  cmd "$@" >/dev/null
  sleep "${SETTLE:-1.2}"
  shot "$out"
}

case "${1:-}" in
  cmd)  shift; cmd "$@" ;;
  shot) shift; shot "$@" ;;
  act)  shift; act_shot "$@" ;;
  *)    echo "用法: $0 {cmd|shot|act} ..."; exit 1 ;;
esac