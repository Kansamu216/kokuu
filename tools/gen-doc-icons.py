"""生成文档站的 **UI 线性图标**（可复现、无第三方依赖）。

产出：`docs/.vuepress/public/icons/*.svg` —— 24x24 描边图标，走 CSS mask 上色，
自动跟随主题与品牌强调色。

⚠️ 品牌标志 / favicon **不由本脚本生成** —— 它们来自品牌视觉包
   （`voidkit-brand-kit.zip`）：logo 用 `icons/icon.png`、favicon 用 `icons/icon.ico`。
   换品牌素材时直接替换 `docs/.vuepress/public/images/` 下的位图即可，别在这里手画。
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON_DIR = os.path.join(ROOT, "docs", ".vuepress", "public", "icons")

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


def main():
    os.makedirs(ICON_DIR, exist_ok=True)
    for name, d in ICONS.items():
        with open(os.path.join(ICON_DIR, f"{name}.svg"), "w", encoding="utf-8", newline="\n") as f:
            f.write(SVG_TMPL.format(d=d))
    print(f"[icons] 生成 {len(ICONS)} 个 UI 线性图标 → docs/.vuepress/public/icons/")
    print("[icons] 注意：品牌 logo / favicon 由品牌视觉包提供，见 docs/.vuepress/public/images/")


if __name__ == "__main__":
    main()
