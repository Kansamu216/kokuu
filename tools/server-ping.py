#!/usr/bin/env python3
"""Server List Ping（SLP）客户端 —— 用来端到端验证 MOTD。

为什么需要它：MOTD 是**唯一不需要玩家就能验证的显示类功能**。它走的是
Server List Ping 协议（客户端在服务器列表里刷新的那一下），我们自己发一遍就能拿到
服务器**实际返回**的 motd 文本 —— 不是读配置文件、不是查 API，而是客户端真正会看到的东西。

能验证的东西：
  * 多套 MOTD 的轮换（order / random / fixed）是否真的在换
  * `%变量%`（如 `%online%`）是否解析（原文里不该再出现 `%` 包裹的变量名）
  * 颜色码是否保留

用法：
    python server-ping.py <host> <port> [次数] [间隔秒]

协议（1.7+）：Handshake(0x00) → Status Request(0x00) → Status Response(0x00)。
握手里的 protocol version 用 767（1.21.1），实际服务端不校验它。
"""
import json
import socket
import struct
import sys
import time


def write_varint(value: int) -> bytes:
    """VarInt 是**有符号**的：负数要先转成无符号 32 位再按 7 位一组写。"""
    out = bytearray()
    value &= 0xFFFFFFFF
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def read_varint(sock: socket.socket) -> int:
    result = 0
    shift = 0
    while True:
        chunk = sock.recv(1)
        if not chunk:
            raise EOFError("连接被关闭")
        byte = chunk[0]
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            break
        shift += 7
    # 转回有符号
    if result >= 1 << 31:
        result -= 1 << 32
    return result


def read_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise EOFError("连接被关闭")
        buf += chunk
    return buf


def write_string(text: str) -> bytes:
    raw = text.encode("utf-8")
    return write_varint(len(raw)) + raw


def flatten(component) -> str:
    """把文本组件（字符串或 JSON 对象）拍平成纯文本，供断言使用。"""
    if component is None:
        return ""
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(flatten(x) for x in component)
    if isinstance(component, dict):
        out = component.get("text", "")
        if "extra" in component:
            out += flatten(component["extra"])
        return out
    return str(component)


def ping(host: str, port: int, timeout: float = 8.0) -> dict:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        # Handshake：protocol=-1 表示"只想知道状态"，服务端不会因为我们版本不符而拒绝
        payload = (b"\x00" + write_varint(-1) + write_string(host)
                   + struct.pack(">H", port) + write_varint(1))
        sock.sendall(write_varint(len(payload)) + payload)
        # Status Request
        sock.sendall(b"\x01\x00")
        # Status Response
        read_varint(sock)               # 包长
        read_varint(sock)               # 包 id (0x00)
        length = read_varint(sock)      # JSON 字符串长度
        data = json.loads(read_exact(sock, length).decode("utf-8"))
        return data


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    host, port = argv[1], int(argv[2])
    times = int(argv[3]) if len(argv) > 3 else 6
    interval = float(argv[4]) if len(argv) > 4 else 1.0

    seen = []
    for i in range(times):
        try:
            data = ping(host, port)
        except Exception as exc:  # noqa: BLE001
            print("第 %d 次 ping 失败：%r" % (i + 1, exc))
            time.sleep(interval)
            continue
        desc = data.get("description", "")
        text = flatten(desc)
        players = data.get("players", {})
        seen.append(text)
        print("第 %d 次 | online=%s/%s | motd=%r"
              % (i + 1, players.get("online"), players.get("max"), text))
        if i == 0:
            print("       原始 description JSON：%s" % json.dumps(desc, ensure_ascii=False)[:300])
            print("       版本：%s" % json.dumps(data.get("version", {}), ensure_ascii=False))
        if i < times - 1:
            time.sleep(interval)

    distinct = sorted(set(seen))
    print("")
    print("共 %d 次采样，得到 %d 种不同的 MOTD" % (len(seen), len(distinct)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
