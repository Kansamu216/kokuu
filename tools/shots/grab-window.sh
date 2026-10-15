#!/usr/bin/env bash
# 用 Windows GDI 直接抓「游戏窗口」像素（PrintWindow + PW_RENDERFULLCONTENT），落盘 PNG。
#
# 为什么不用 minecraft-mod-mcp 的 /api/screenshot：
#   它返回 "Failed to get Minecraft instance"（mod 的 mixin 没注入到 Minecraft 实例），
#   与焦点/最小化无关，是 mod 侧问题。绕开它 —— 截图是「展示效果」需求，
#   直接抓窗口像素更稳，也不受游戏内状态影响。
#
# 🚨 三个实测坑：
#   1. OpenGL 窗口普通 BitBlt 抓不到内容，必须 PrintWindow(..., 2 /*RENDERFULLCONTENT*/)。
#   2. **按 PID 定位窗口，不要按标题匹配** —— 标题含 "Minecraft" 的浏览器页面
#      （mcmod 那个）会先被枚举到，抓回来是一张网页截图。
#   3. ctypes 里 GetWindowThreadProcessId **在 user32 上**（不是 kernel32）；
#      且所有 Win32 函数必须在进回调前先取出引用 —— 回调里访问 windll.xxx
#      属性会重新__getattr__ 解析并抛 'function not found'，枚举会静默返回空。
#
# 用法： tools/shots/grab-window.sh <输出名> [游戏PID]
set -u
PY="C:/Users/26703/.workbuddy/binaries/python/envs/default/Scripts/python.exe"
SHOTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/docs/release/images/shots"
mkdir -p "$SHOTS"
PID="${2:-}"
if [ -z "$PID" ]; then
  PID=$(netstat -ano | grep '0.0.0.0:9876.*LISTENING' | head -1 | awk '{print $5}')
  [ -n "$PID" ] || { echo "找不到监听 9876 的进程（客户端没起？）"; exit 2; }
fi
"$PY" - "$SHOTS/${1:?用法: grab-window.sh <输出名> [PID]}" "$PID" <<'EOF'
import ctypes, ctypes.wintypes as wt, sys
from PIL import Image

out, target_pid = sys.argv[1], int(sys.argv[2])
u = ctypes.WinDLL("user32", use_last_error=True)

# 回调里不能用 windll.xxx，必须提前取引用（见脚本头说明 3）
IWV, GWL, GW, GR = u.IsWindowVisible, u.GetWindowTextLengthW, u.GetWindowTextW, u.GetWindowRect
GWTP = u.GetWindowThreadProcessId

hwnd = None
@ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)
def cb(h, _):
    global hwnd
    if hwnd:
        return False
    if IWV(h):
        n = GWL(h)
        if n:
            b = ctypes.create_unicode_buffer(n + 1)
            GW(h, b, n + 1)
            pid = wt.DWORD()
            GWTP(h, ctypes.byref(pid))
            if pid.value == target_pid:
                hwnd = h
                return False
    return True
u.EnumWindows(cb, 0)
if not hwnd:
    sys.exit(f"pid={target_pid} 没有可见窗口")

r = wt.RECT(); GR(hwnd, ctypes.byref(r))
w, h = r.right - r.left, r.bottom - r.top
title = ctypes.create_unicode_buffer(GWL(hwnd) + 1); GW(hwnd, title, len(title))

g = ctypes.WinDLL("gdi32")
hdc = u.GetWindowDC(hwnd)
mdc = g.CreateCompatibleDC(hdc)
bmp = g.CreateCompatibleBitmap(hdc, w, h)
g.SelectObject(mdc, bmp)
ok = u.PrintWindow(hwnd, mdc, 2)

class BI(ctypes.Structure):
    _fields_ = [("biSize", wt.DWORD), ("biWidth", wt.LONG), ("biHeight", wt.LONG),
                ("biPlanes", wt.WORD), ("biBitCount", wt.WORD), ("biCompression", wt.DWORD),
                ("biSizeImage", wt.DWORD), ("biXPelsPerMeter", wt.LONG),
                ("biYPelsPerMeter", wt.LONG), ("biClrUsed", wt.DWORD), ("biClrImportant", wt.DWORD)]
bi = BI(ctypes.sizeof(BI), w, -h, 1, 32, 0, 0, 0, 0, 0, 0)
buf = ctypes.create_string_buffer(w * h * 4)
g.GetDIBits(mdc, bmp, 0, h, buf, ctypes.byref(bi), 0)
g.DeleteObject(bmp); g.DeleteDC(mdc); u.ReleaseDC(hwnd, hdc)

im = Image.frombuffer("RGB", (w, h), buf, "raw", "BGRX", 0, 1)
im.save(out)
colors = len(im.getcolors(maxcolors=1 << 24) or [])
print(f"窗口「{title.value}」 {w}x{h}")
print(f"  -> {out.split('/')[-1]}  {colors} 色"
      + ("" if ok else "  (PrintWindow 返回 0)"))
EOF