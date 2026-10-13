# web（管理 API 桥）

```
mods/voidkit_web-0.1.0-beta.1+mc1.21.1-neoforge.jar      ← 可选
```

服务器管理 API 的**桥接**：把各模块登记在 `ApiRouter` 里的路由，通过一个轻量
HTTP 服务暴露出来，供 [管理平台](../panel/) 消费。

> **没装它也能用**：服务器不会监听任何端口，一行 HTTP 都不跑。
> 它是 Voidkit 里**唯一会监听端口的模块**。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `remote-api` | 管理 HTTP API | 开 |

## 它是怎么工作的

内核里**没有一行 HTTP 代码**。各模块（display / essentials / economy / guard / inspector …）
只是往 `ApiRouter` 这张**纯数据登记表**里挂路由，`voidkit_web` 负责把它们真正跑起来。

```
其他模块 ──登记路由──▶ ApiRouter（纯数据）
                          │
                    voidkit_web 消费
                          │
                    HTTP :8765 ◀── VoidkitHub（管理平台）
```

## 配置

`voidkit/web/config.json`：

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `bind` | `127.0.0.1` | 监听地址（**默认只本机**） |
| `port` | `8765` | 监听端口 |
| `token` | 自动生成 | 访问令牌，用 `/vk token` 查看 |
| `rateLimitPerSecond` | `30` | 每秒请求上限 |

### 🔒 安全建议

**不要**把 `bind` 改成 `0.0.0.0` 暴露到公网。正确做法：

```
游戏服（同机）                          外部
┌──────────────────────────┐
│ voidkit_web  :8765       │◀── VoidkitHub（Go 程序）
│ 只听 127.0.0.1           │     对外提供登录与令牌托管
└──────────────────────────┘
```

让 web 只听 `127.0.0.1`，由本机的管理平台去连它，只把平台暴露出去。

## 令牌

- 首次启动自动生成，存在 `web/config.json`。
- `/vk token` 随时查看。
- [管理平台](../panel/) 用它来访问各游戏服的 API。

## 提供的路由

路由由各模块注册，常见分组：

| 前缀 | 来源 |
|---|---|
| `/api/motd` `/api/tab` `/api/scoreboard` `/api/chat` | display |
| `/api/perf` | utility |
| `/api/inspector/*` | 审计（voidkit_inspector） |
| `/api/guard/*` | 反作弊（voidkit_guard） |

`/vk api` 可查看管理 API 的监听状态（监听地址 / 端口 / 限流）。

## 相关

- [管理平台 VoidkitHub](../panel/) —— 把多台服务器的 API 聚合成一个后台
- [审计模块](../audit/) · [反作弊模块](../anticheat/)
