#!/usr/bin/env python3
"""VoidkitCore 管理面板 —— 极简静态服务器。

面板与模组是分离的两块：模组只提供 HTTP API，界面就是本目录这几个静态文件。
所以它不需要跟服务端放在一起，扔到任意 WEB 服务器（Nginx / Caddy / 对象存储）都能跑；
本地想省事就用这个脚本：

    python serve.py            # http://127.0.0.1:8766
    python serve.py 8080       # 指定端口

打开页面后，"接口地址"填服务端的管理 API 地址（默认 http://127.0.0.1:8765），
令牌用游戏内 /kcore token 获取。
"""

import http.server
import os
import socketserver
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8766

os.chdir(os.path.dirname(os.path.abspath(__file__)))


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        # 面板要能随时反映最新配置，禁掉缓存省得手动强刷
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, fmt, *args):
        pass


class Server(socketserver.TCPServer):
    allow_reuse_address = True


if __name__ == "__main__":
    with Server(("127.0.0.1", PORT), Handler) as httpd:
        print(f"VoidkitCore 管理面板: http://127.0.0.1:{PORT}")
        print("接口地址填服务端 API，例如 http://127.0.0.1:8765")
        print("按 Ctrl+C 退出")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n已停止")
