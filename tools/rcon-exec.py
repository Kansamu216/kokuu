#!/usr/bin/env python3
"""极简 RCON 客户端 —— 给 Voidkit 做命令行为实测用。

为什么需要它：Gradle 的 `runServer` **不转发 stdin**，所以没法往服务器控制台
喂命令；真机服务端也一样（控制台在另一个进程里）。RCON 是唯一能脚本化驱动
命令通道，且不依赖任何 Mod 侧代码。

用法：
    python rcon-exec.py <host> <port> <password> <命令> [<命令> ...]
    python rcon-exec.py <host> <port> <password> < commands.txt   # 一行一条

输出：每条命令前打印 `>>> <命令>`，随后是其响应。

协议：Source RCON（Valve 那套），包体 = id(int32) + type(int32) + body + 两个 0 字节。
认证 type=3，执行 type=2；认证失败时服务器回的 id 是 -1。

🚨 **必须读完全部分包**（2026-10-14 踩坑）：
Minecraft 的服务端把一条命令的响应**按 4096 字节切块**，分多个包发回来。
只读一个包的话，`help` 恰好被截成 4096 字节，而**剩余的分包会变成下一条命令
的"响应"** —— 表现为莫名其妙的错位（例如 `help 2` 收到的是 `help 1` 的输出）。
这里用「读到一轮短暂空闲为止」的方式把所有分包拼起来。
"""
import socket
import struct
import sys
import time

TYPE_AUTH = 3
TYPE_EXEC = 2

# 分包的判据：第一条包用完整超时等，后续包只要在这么短的时间内没来，就认为发完了。
IDLE_TIMEOUT = 0.35


def _pack(req_id: int, ptype: int, body: str) -> bytes:
    payload = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def _read_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise EOFError("连接被服务器关闭")
        buf += chunk
    return buf


def read_packet(sock: socket.socket):
    (length,) = struct.unpack("<i", _read_exact(sock, 4))
    data = _read_exact(sock, length)
    req_id, ptype = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", "replace")
    return req_id, ptype, body


class Rcon:
    """一次连接里可以连发多条命令；超时按「空响应」处理而不是抛错。"""

    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0):
        self.timeout = timeout
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.sendall(_pack(1, TYPE_AUTH, password))
        req_id, _, _ = read_packet(self.sock)
        if req_id == -1:
            raise SystemExit("RCON 认证失败：密码不对")

    def run(self, command: str) -> str:
        """执行一条命令，返回**拼接后的完整响应**（自动收拢所有 4096 字节分包）。"""
        self.sock.sendall(_pack(2, TYPE_EXEC, command))
        parts = []
        self.sock.settimeout(self.timeout)
        try:
            parts.append(read_packet(self.sock)[2])
        except socket.timeout:
            return ""  # 这条命令压根没有控制台输出
        # 继续收后续分包，直到短暂空闲
        self.sock.settimeout(IDLE_TIMEOUT)
        while True:
            try:
                parts.append(read_packet(self.sock)[2])
            except (socket.timeout, EOFError):
                break
        self.sock.settimeout(self.timeout)
        return "".join(parts)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 2
    host, port, password = argv[1], int(argv[2]), argv[3]
    commands = argv[4:] or [line.rstrip("\n") for line in sys.stdin if line.strip()]
    rcon = Rcon(host, port, password)
    try:
        for cmd in commands:
            print(">>> %s" % cmd)
            print(rcon.run(cmd))
            time.sleep(0.05)
    finally:
        rcon.close()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
