"""生成文档站图标资源（可复现、无第三方依赖）。

产出：
  docs/.vuepress/public/icons/*.svg      —— 24x24 线性图标（stroke 版，走 CSS mask 上色）
  docs/.vuepress/public/images/logo.svg  —— 站点 logo（首页 hero + 导航栏）
  docs/.vuepress/public/images/favicon.svg
  docs/.vuepress/public/images/favicon.ico        —— 多尺寸 ICO（16/32/48/64/128/256）
  docs/.vuepress/public/images/apple-touch-icon.png

设计取向：圆角方块 + 白色人字形 "V"，绿色主色（贴合主题 #3eaf7c）。
PNG/ICO 由纯 Python 光栅化（4x4 超采样抗锯齿 + zlib 写 PNG），不依赖 Pillow。
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUB = os.path.join(ROOT, "docs", ".vuepress", "public")
ICON_DIR = os.path.join(PUB, "icons")
IMG_DIR = os.path.join(PUB, "images")

# ---------------------------------------------------------------- 线性图标

ICONS = {
    # 内核：立方体骨架
    "core": 'M12 3 20 7.5V16.5L12 21 4 16.5V7.5Z M4 7.5 12 12 20 7.5 M12 12V21',
    # 基础指令：房子
    "essentials": 'M4 11 12 4 20 11 M6.5 9.6V20h11V9.6 M10 20v-5.5h4V20',
    # 展示层：显示器
    "display": 'M3 5h18v11H3Z M8.5 20h7 M12 16v4',
    # 权限：挂锁
    "perm": 'M6 11h12v9H6Z M9 11V8a3 3 0 0 1 6 0v3 M12 14.5v2.5',
    # 称号：星章
    "title": 'M12 3.5l2.6 5.3 5.9.85-4.25 4.15 1 5.85L12 16.9l-5.25 2.75 1-5.85L3.5 9.65l5.9-.85Z',
    # 经济：钱币
    "economy": 'M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17Z M9 8.8l3 3.6 3-3.6 M12 12.4V16 M9.7 13.6h4.6',
    # 清扫/性能：仪表盘
    "utility": 'M4 17.5a8 8 0 1 1 16 0 M12 17.5l4.6-5.6 M12 17.5h.01',
    # 管理 API：插头
    "web": 'M9 3.5V7 M15 3.5V7 M6 7h12v4.2a6 6 0 0 1-12 0Z M12 17.4V20.5',
    # 反作弊：盾牌 + 勾
    "anticheat": 'M12 3l7 2.8V12c0 4.4-2.9 7.4-7 9-4.1-1.6-7-4.6-7-9V5.8Z M8.9 12l2.2 2.2 4.1-4.3',
    # 审计：放大镜
    "audit": 'M11 4a6.6 6.6 0 1 0 0 13.2A6.6 6.6 0 0 0 11 4Z M15.8 15.8 21 21 M8.6 10.6h4.8 M11 8.2v4.8',
    # 管理平台：窗口 / 面板
    "panel": 'M3 5h18v14H3Z M3 9.2h18 M6.6 12.6h4.2 M6.6 15.4h7.4',
    # 指南：书
    "guide": 'M5 5.5A2 2 0 0 1 7 3.5h12v17H7a2 2 0 0 1-2-2Z M9 3.5v17 M12 8h4 M12 11.5h3',
    # 下载
    "download": 'M12 3.5v10.5 M8 10.5l4 4 4-4 M4.5 19.5h15',
    # 火箭（快速开始）
    "rocket": 'M12 3.2c3 1.9 4.6 5.4 4.6 8.9L12 16.4 7.4 12.1c0-3.5 1.6-7 4.6-8.9Z M12 16.4l-1.9 4.1 M12 16.4l1.9 4.1 M12 8.6h.01',
    # 闪耀（AI / 智能）
    "spark": 'M11 3.2l1.55 4.25L16.8 9l-4.25 1.55L11 14.8 9.45 10.55 5.2 9l4.25-1.55Z M18 14.2l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8Z',
    # 齿轮（工程 / 配置）
    "gear": 'M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8Z '
            'M19.2 14.3l1.7 1.3-1.7 3-2-.8a6.9 6.9 0 0 1-1.9 1.1l-.3 2.1h-3.4l-.3-2.1a6.9 6.9 0 0 1-1.9-1.1l-2 .8-1.7-3 1.7-1.3a6.9 6.9 0 0 1 0-2.2L6.7 10.8l1.7-3 2 .8a6.9 6.9 0 0 1 1.9-1.1l.3-2.1h3.4l.3 2.1a6.9 6.9 0 0 1 1.9 1.1l2-.8 1.7 3-1.7 1.3a6.9 6.9 0 0 1 0 2.2Z',
}

SVG_TMPL = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" '
    'stroke="currentColor" stroke-width="1.7" stroke-linecap="round" '
    'stroke-linejoin="round" role="img" aria-hidden="true">\n'
    '  <path d="{d}"/>\n'
    '</svg>\n'
)

# ---------------------------------------------------------------- logo SVG

LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256" role="img" aria-label="Voidkit">
  <defs>
    <linearGradient id="vkBg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#46c98d"/>
      <stop offset="1" stop-color="#2f9c6c"/>
    </linearGradient>
    <linearGradient id="vkFg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#ffffff"/>
      <stop offset="1" stop-color="#e8fbf2"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="256" height="256" rx="58" fill="url(#vkBg)"/>
  <path d="M72 86 L128 178 L184 86" fill="none" stroke="url(#vkFg)" stroke-width="34"
        stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="128" cy="200" r="7" fill="#ffffff" opacity="0.75"/>
</svg>
"""

# ---------------------------------------------------------------- 纯 Python 光栅化

SIZE = 256
SS = 4          # 每像素 4x4 超采样
RADIUS = 58.0   # 圆角半径
STROKE = 34.0   # 人字形笔画宽度
P0 = (72.0, 86.0)
P1 = (128.0, 178.0)
P2 = (184.0, 86.0)

BG_TOP = (0x46, 0xC9, 0x8D)
BG_BOT = (0x2F, 0x9C, 0x6C)


def _seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    if l2 == 0.0:
        return ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
    t = ((px - ax) * dx + (py - ay) * dy) / l2
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    qx, qy = ax + t * dx, ay + t * dy
    return ((px - qx) ** 2 + (py - qy) ** 2) ** 0.5


def _inside_round_rect(x, y, w, h, r):
    if x < 0 or y < 0 or x > w or y > h:
        return False
    cx = r if x < r else (w - r if x > w - r else x)
    cy = r if y < r else (h - r if y > h - r else y)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def render_rgba(size=SIZE):
    """返回 size*size*4 的 RGBA 字节串。"""
    n = size
    scale = 256.0 / n
    half = STROKE / 2.0
    step = 1.0 / SS
    off = step / 2.0
    inv = 1.0 / (SS * SS)
    buf = bytearray(n * n * 4)
    for py in range(n):
        for px in range(n):
            bg_hits = 0
            fg_hits = 0
            for sy in range(SS):
                for sx in range(SS):
                    # 采样点映射到 256 坐标系
                    x = (px + off + sx * step) * scale
                    y = (py + off + sy * step) * scale
                    if _inside_round_rect(x, y, 256.0, 256.0, RADIUS):
                        bg_hits += 1
                        d = min(
                            _seg_dist(x, y, P0[0], P0[1], P1[0], P1[1]),
                            _seg_dist(x, y, P1[0], P1[1], P2[0], P2[1]),
                        )
                        if d <= half:
                            fg_hits += 1
                        elif (x - 128.0) ** 2 + (y - 200.0) ** 2 <= 7.0 * 7.0:
                            fg_hits += 1
            bg_a = bg_hits * inv
            fg_a = fg_hits * inv
            if bg_a <= 0.0:
                continue
            ty = (py + 0.5) / n
            br = BG_TOP[0] + (BG_BOT[0] - BG_TOP[0]) * ty
            bgc = BG_TOP[1] + (BG_BOT[1] - BG_TOP[1]) * ty
            bb = BG_TOP[2] + (BG_BOT[2] - BG_TOP[2]) * ty
            # 白色人字形按 fg_a 叠加在背景上
            r = br + (255.0 - br) * fg_a
            g = bgc + (255.0 - bgc) * fg_a
            b = bb + (255.0 - bb) * fg_a
            i = (py * n + px) * 4
            buf[i] = int(r + 0.5)
            buf[i + 1] = int(g + 0.5)
            buf[i + 2] = int(b + 0.5)
            buf[i + 3] = int(bg_a * 255.0 + 0.5)
    return bytes(buf)


def downscale(rgba, src, dst):
    """盒式下采样（要求 src 是 dst 的整数倍）。"""
    if src == dst:
        return rgba
    f = src // dst
    out = bytearray(dst * dst * 4)
    for y in range(dst):
        for x in range(dst):
            r = g = b = a = 0
            for dy in range(f):
                row = (y * f + dy) * src
                for dx in range(f):
                    i = (row + x * f + dx) * 4
                    r += rgba[i]
                    g += rgba[i + 1]
                    b += rgba[i + 2]
                    a += rgba[i + 3]
            cnt = f * f
            o = (y * dst + x) * 4
            out[o] = r // cnt
            out[o + 1] = g // cnt
            out[o + 2] = b // cnt
            out[o + 3] = a // cnt
    return bytes(out)


def png_bytes(rgba, size):
    raw = bytearray()
    stride = size * 4
    for y in range(size):
        raw.append(0)  # filter: none
        raw += rgba[y * stride:(y + 1) * stride]
    comp = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", comp)
        + chunk(b"IEND", b"")
    )


def ico_bytes(entries):
    """entries: [(size, png_bytes)]，写 PNG 承载的 ICO。"""
    count = len(entries)
    header = struct.pack("<HHH", 0, 1, count)
    offset = 6 + 16 * count
    dirs = b""
    blobs = b""
    for size, data in entries:
        dim = 0 if size >= 256 else size
        dirs += struct.pack(
            "<BBBBHHII", dim, dim, 0, 0, 1, 32, len(data), offset
        )
        blobs += data
        offset += len(data)
    return header + dirs + blobs


def main():
    os.makedirs(ICON_DIR, exist_ok=True)
    os.makedirs(IMG_DIR, exist_ok=True)

    for name, d in ICONS.items():
        with open(os.path.join(ICON_DIR, f"{name}.svg"), "w", encoding="utf-8", newline="\n") as f:
            f.write(SVG_TMPL.format(d=d))
    print(f"[icons] 生成 {len(ICONS)} 个线性图标")

    with open(os.path.join(IMG_DIR, "logo.svg"), "w", encoding="utf-8", newline="\n") as f:
        f.write(LOGO_SVG)
    # favicon.svg 直接用同一份（浏览器按 SVG 渲染，最清晰）
    with open(os.path.join(IMG_DIR, "favicon.svg"), "w", encoding="utf-8", newline="\n") as f:
        f.write(LOGO_SVG)
    print("[icons] 生成 logo.svg / favicon.svg")

    print("[icons] 光栅化（4x4 超采样，稍慢）...")
    big = render_rgba(SIZE)

    with open(os.path.join(IMG_DIR, "apple-touch-icon.png"), "wb") as f:
        f.write(png_bytes(downscale(big, SIZE, 180), 180))
    print("[icons] 生成 apple-touch-icon.png (180)")

    entries = []
    for s in (16, 32, 48, 64, 128, 256):
        entries.append((s, png_bytes(downscale(big, SIZE, s), s)))
    with open(os.path.join(IMG_DIR, "favicon.ico"), "wb") as f:
        f.write(ico_bytes(entries))
    print("[icons] 生成 favicon.ico（16/32/48/64/128/256 六尺寸）")


if __name__ == "__main__":
    main()
