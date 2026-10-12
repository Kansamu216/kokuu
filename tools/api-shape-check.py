# -*- coding: utf-8 -*-
"""
API 结构断言。

HTTP 200 只能说明"接口活着"，说明不了"数据结构对"。

真实事故（2026-10-12）：ApiJson.put 的兜底分支把 List 序列化成了字符串，
所有接口照样返回 200，但面板的 .map() 全线崩溃：
    p.items.map is not a function
    entries.map is not a function

这个脚本专门盯这类"200 但结构错"的问题：逐个断言关键字段的 JSON 类型。

用法：api-shape-check.py http://127.0.0.1:8765 <apiToken>
退出码：0 = 全部通过；1 = 有断言失败
"""
import http.client
import json
import sys
from urllib.parse import urlparse

# (路径, 字段名, 说明) —— 这些字段必须是 JSON 数组，前端要对它们 .map()
ARRAY_CHECKS = [
    ("/api/features", "features", "模块清单"),
    ("/api/players?limit=3", "players", "玩家列表"),
    ("/api/logs/joins?limit=5", "entries", "上下线事件流"),
    ("/api/placeholders", "keys", "变量名清单"),
    ("/api/permissions/groups", "groups", "权限组清单"),
    ("/api/permissions/users", "users", "玩家权限覆盖"),
    # 新增插件
    ("/api/clearlag/trash", "entries", "垃圾桶内容"),
    ("/api/perf", "chunks", "区块热点排名"),
    ("/api/economy/top", "entries", "财富排行"),
    ("/api/menus", "menus", "菜单清单"),
    ("/api/menus", "openers", "菜单物品绑定"),
]

# 这些字段必须是 JSON 对象
OBJECT_CHECKS = [
    ("/api/config", "config", "配置对象"),
    ("/api/config/plugins", "plugins", "插件配置快照"),
    ("/api/permissions/settings", "settings", "权限全局设置"),
    ("/api/clearlag", "trash", "垃圾桶状态"),
    ("/api/clearlag", "rules", "清扫规则"),
    ("/api/perf", "entities", "实体统计"),
    ("/api/perf", "intervention", "干预状态"),
    ("/api/announce", "auto", "公告轮播设置"),
]


def fetch(base, token, path):
    u = urlparse(base)
    # 用 http.client 而不是 urllib：沙箱里 urllib 会读 http_proxy 环境变量绕道代理
    conn = http.client.HTTPConnection(u.hostname, u.port or 80, timeout=15)
    try:
        conn.request("GET", path, headers={"X-Auth-Token": token})
        resp = conn.getresponse()
        return resp.status, resp.read().decode("utf-8")
    finally:
        conn.close()


def type_name(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def main():
    if len(sys.argv) < 3:
        print("用法: api-shape-check.py <baseUrl> <apiToken>")
        return 2
    base, token = sys.argv[1], sys.argv[2]

    cache = {}
    failures = []

    def payload(path):
        if path not in cache:
            cache[path] = fetch(base, token, path)
        return cache[path]

    print("字段类型断言（期望 array）：")
    for path, field, label in ARRAY_CHECKS:
        status, body = payload(path)
        try:
            data = json.loads(body)
        except Exception as exc:
            failures.append(f"{path} 返回的不是合法 JSON: {exc}")
            print(f"  ✗ {path:<26} {field:<10} 解析失败")
            continue
        value = data.get(field)
        got = type_name(value)
        if isinstance(value, list):
            print(f"  ✓ {path:<26} {field:<10} array（{len(value)} 项）  {label}")
        else:
            failures.append(f"{path} 的 {field} 是 {got}，应为 array")
            preview = str(value)[:70]
            print(f"  ✗ {path:<26} {field:<10} {got}  ← 应为 array   {label}")
            print(f"      实际值: {preview}")

    print()
    print("字段类型断言（期望 object）：")
    for path, field, label in OBJECT_CHECKS:
        status, body = payload(path)
        try:
            data = json.loads(body)
        except Exception as exc:
            failures.append(f"{path} 返回的不是合法 JSON: {exc}")
            print(f"  ✗ {path:<26} {field:<10} 解析失败")
            continue
        value = data.get(field)
        got = type_name(value)
        if isinstance(value, dict):
            print(f"  ✓ {path:<26} {field:<10} object  {label}")
        else:
            failures.append(f"{path} 的 {field} 是 {got}，应为 object")
            print(f"  ✗ {path:<26} {field:<10} {got}  ← 应为 object   {label}")

    print()
    if failures:
        print(f"结构断言失败 {len(failures)} 项：")
        for item in failures:
            print("  -", item)
        return 1
    print(f"结构断言全部通过（{len(ARRAY_CHECKS) + len(OBJECT_CHECKS)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
