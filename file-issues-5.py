#!/usr/bin/env python3
"""第四批 issue：技术栈现代化 + 工程质量（敏捷底座）。"""
import subprocess
import sys

REPO = "KokuuWork/voidkit"

BODY = """## 背景

用户要求：**敏捷开发 + 拥抱互联网最先进的技术栈 / 模块 / 语言**。

关键工程判断：**「敏捷」的底座是"能安全地快速改"** —— 自动化测试 + CI + 代码质量，
而不是堆新名词。选型要分层，每引入一样都得服务「稳 + 轻 + 零配置 + 好维护」，
否则就是把几百 mod 的整合包当试验场。

## 三层划分

### 已经是最新（保持不动）

| 项 | 说明 |
|---|---|
| Kotlin 2.1 | JVM 上最现代的语言之一（用户有意弃 Java） |
| NeoForge 1.21.1 + 数据组件系统 | 最新代加载器 + 1.20.5 的 Data Components |
| Go | 后端最热门之一（用户有意弃 Node/PHP） |
| SQLite WAL + 多线程写读 | 查熊已用，一写多读匹配线程模型 |
| Gradle 8 多项目 | 构建工具 |

### 值得引入（有真实收益、不破坏稳/轻/零配置）

1. **前端**（配合 #10 重做）：`Vite + TypeScript` 构建；框架选 **Vue 3 或 Svelte 5**
   （都比 React 轻）；动效用 **Motion**（Framer Motion 独立版）或 Web Animations API。
   产物仍 `go:embed` 进单二进制，**不破坏「单文件、离线可用」**。
2. **可观测性**：hub 与 web 插件加 **Prometheus `/metrics` 端点**（专业运维工具的标配，
   也契合"Go 工具风"气质）。TPS/MSPT/在线数/查熊写入量都能画曲线。
3. **数据库**：查熊补 **FTS5 全文检索**（SQLite 内置扩展），支持按物品名/方块名模糊搜，
   甚至搜 NBT 里的自定义名。零额外依赖。
4. **工程质量（敏捷真正的底座）**：`Detekt + Ktlint`（Kotlin 代码质量，挂 CI）、
   GitHub Actions 自动「构建 → 测试 → 发布」、`Renovate` 依赖自动更新。与 #6 单测联动。
5. **AI 可选层（卖点，但有边界）**：
   - 自然语言查熊（LLM 把「谁偷了我的钻石」转成结构化查询）
   - 异常检测（分析审计日志，找出熊孩子行为模式/可疑产线）
   - 🚨 **关键原则：AI 是可选增强，不是依赖。** 默认零配置、离线、规则引擎兜底；
     服主显式接入 LLM 后才升级成"真 AI"。不接 LLM 时功能照常，只是少"聪明"一点。

### 明确不碰（会破坏稳/轻/零配置）

| 项 | 为什么拒绝 |
|---|---|
| MySQL / PostgreSQL / Redis 等重型基础设施 | 多一个进程、多一份运维，与"零配置"冲突；嵌入式 SQLite/DuckDB 才是对的 |
| Kotlin Multiplatform / GraalVM native | MC 服务端必须跑 JVM，KMP/native 用不上 |
| React / Next.js 全家桶 | 太重，Vue3/Svelte5 更契合"轻" |
| 微服务 / 消息队列 | 单二进制 Go + 嵌入式存储已经够，加这些是过度工程 |

## 验收

- 前端用 Vite 构建后，hub 仍是单二进制、离线可用
- `/metrics` 端点能被 Prometheus 抓取
- CI 上 lint + 单测 + 自动发布全绿
- 不接 LLM 时，查熊等所有功能照常工作（AI 只是增强）
"""


def main() -> int:
    r = subprocess.run(
        ["gh", "issue", "create", "--repo", REPO,
         "--title", "架构：技术栈现代化 + 工程质量（敏捷开发底座）",
         "--label", "enhancement", "--body-file", "-"],
        input=BODY, capture_output=True, text=True, encoding="utf-8",
    )
    if r.returncode != 0:
        print(f"  ✗ {(r.stderr or r.stdout).strip()[:300]}")
        return 1
    print(f"  ✓ {r.stdout.strip()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
