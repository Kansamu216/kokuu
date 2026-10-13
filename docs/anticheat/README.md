# 反作弊（VoidkitGuard）

```
客户端 + 服务端同一个 jar：mods/voidkit_guard-1.0.0.jar
```

独立仓库：[`KokuuWork/voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss)（公开 OSS 版）。
依赖 `voidkit_core` 作为前置。

它是 Voidkit 套件的第 9 个组件：**客户端 Agent + 服务端校验**。

```
guard/build/libs/voidkit_guard-1.0.0.jar   ← 唯一产物，客户端服务端都装它
```

---

## 0. 隐私与数据采集

只采集反作弊所需的最少信息：

| 采集项 | 是否采集 | 说明 |
|---|---|---|
| 连接 IP | ✅ | 连接必有的信息，封禁所需 |
| 机器码（加盐哈希） | ✅ | 系统/主板唯一标识哈希，封禁「换号重进」所需；**原文不出客户端** |
| 系统版本 / CPU / GPU / 内存 | ✅ | 指向「什么机器」而非「谁」，用于虚拟机/软渲染检测 |
| 网卡 MAC | ❌ | 不采集（更敏感） |
| QQ / 微信痕迹 | ❌ | 不采集 |
| 时区 / 系统区域 / 语言 | ❌ | 不采集（位置类信息） |

- **截图只截 MC 游戏窗口**，不抓窗口之外的内容（桌面 / 任务栏 / 其它应用）。
- 机器码只上报**加盐 SHA-256 哈希**，原文不出客户端；换服盐不同，数据不能跨服串用。
- 服务端代码里**不存在**网卡 / QQ / 微信的采集路径（不是开关关掉，是物理上没有）。

---

## 1. 架构（为移植而生）

```
guard/
├── common/        协议、加密、内存机密保护（纯 Kotlin，零 MC import）
├── platform/      抽象接口 GServer/GChannel/GMods + GuardPlatforms
│   └── neoforge/  ★ 唯一碰 net.neoforged.* 的一层
├── server/        七个 Feature：完整性/封禁/截屏/审计/深度扫描/挖掘统计/战斗遥测
├── client/        Agent：握手应答、硬件采集、截屏、深度扫描
└── GuardBootstrap.kt   加载器无关装配
```

换加载器 = 新增 `platform/fabric/`（一个入口 + 一个 `FabricMods`），
`common` / `server` / `client` 业务代码**零改动**。

---

## 2. 七个模块

| 模块 | 命令 | 说明 | 默认 |
|---|---|---|---|
| 客户端完整性 | `/guard` | 智能识别 / 哈希锁双模式；进服握手 + **中途随机复检** + 会话保活 | 开 |
| 封禁 | `/gban` `/gunban` `/gbanlist` `/gcheck` | IP + 机器码 + UUID，任一条命中即拒 | 开 |
| 截屏 | `/gscreen` `/greports` | 按需截屏 + **校验失败静默连拍取证**，分片加密传输 | 开 |
| 审计 | API | 上下线 / 校验 / 封禁 / 截屏 / 白名单全量记录，SQLite | 开 |
| 深度扫描 | `/gscan` `/greport` | 上报本机 IP / 文件夹清单 / **进程窗口黑名单** / 心跳 RTT，输出综合判定报告 | 开 |
| 挖掘统计 | 自动 | 稀有矿占比 + 挖矿暴露度滑动窗口，透视行为**只告警不处置** | 开 |
| 战斗遥测 | 自动 | reach / 瞄准急转 / CPS 机械性，战斗类作弊**只告警不处置** | **关** |

模块开关统一走 core 的 `features` 表：面板「模块」页一键切换，或配置
`features.guard_combat = true` —— 每个模块独立启停（审计除外，其它模块的记录都落在它上面）。

---

## 3. 防护体系（按「能刺到谁」分层）

### 传输与内存（防篡改、防重放）

- **握手**：服务端 RSA-2048 → 客户端 AES-256 会话密钥（RSA-OAEP）→
  此后全链路 AES-256-GCM。
- **防重放**：auth 回显 nonce；keepalive 每轮换 nonce；截屏/深度扫描按 reqId 绑定。
- **防中间代理篡改**：服务端给每次挑战**签名**，客户端用内置公钥环验签 ——
  代理想清空特征库？改完签名就废。
- **蜜罐协议**（默认开）：服务端向已验证会话随机（10–30 分钟）下发一条
  「看起来像正常功能、真 agent 协议里不存在」的诱饵消息。真 agent 静默丢弃、永不应答；
  被改过的协议壳一旦回包 = **篡改实锤**，走失败处置（截图取证 + 踢/封），
  踢人话术不露蜜罐存在。
- **内存机密保护**：会话密钥/私钥/指纹只以 XOR 混淆副本驻留堆，明文只在 lambda 里
  存活几微秒、用完即擦；敏感值用 `char[]`/`byte[]` 不用 `String`。
- **Agent 自检**：自身 jar 哈希基线 + `-javaagent`/调试器参数检测。

### 服务端（改客户端也没用的部分 —— 主刃）

- **中途复检**：随机间隔（默认 5–15 分钟）换全新 nonce 重走完整握手，
  可选全量重扫 mod 哈希（防「改文件保留 size+mtime」），按概率顺手截图。
  「进服时干净、开局后作弊」在这层现形。
- **哈希锁**：mod 清单（id+version+SHA-256）与白名单逐项比对，多一个少一个都拒。
- **深度扫描**：进程名/窗口标题黑名单（CE、x64dbg、ProcessHacker、各类外挂 overlay）、
  文件夹清单（专抓「mods 目录里没加载的作弊 jar」）、多网卡/VPN 痕迹。
- **综合判定**：多源线索加权评分（确证 +100 / 白名单不符 +60 / 可疑进程每个 +50 …），
  输出 `clean` / `suspicious` / `likely` / `confirmed` 四级结论，每条带理由。
  统计检测作为**疑似档**参与（单条 +25、合计封顶 55，单靠统计到不了确证档），
  理由标注「需人工复核」—— **处置权永远留给服主**。
- **归属地（离线）**：内置 ip2region xdb（打进 jar）。**IP 永不出服务器**，
  不喂任何第三方 API。粒度到省市+运营商，用于识别「换宽带/机房 IP」。
- **同源账号挖掘**：机器码/IP 交叉撞库，`/gcheck` 与 `/greport` 直接列出
  「这台机器上的所有马甲」。机器码是强关联（换号无效），IP 是弱关联。
  关联账号中有人被封 = 换号 evasion，报告 +60 实锤档。
- **联防**：`/guard fence export|import`（或 API `/api/guard/fence`）在自己几个服之间
  同步封禁清单 —— 这服封了，那服自动拦。

### 静默（反侦察）

- 客户端全程**零提示零 info 日志**（debug 级才输出），无客户端命令。
- 踢人话术只说「环境校验未通过」，**真实原因只进审计 + 在线管理员私信** ——
  对手连自己漏在哪都不知道。
- 截屏无闪光无声响，只截 MC 游戏窗口区域。

---

## 4. 威胁模型边界（不夸大）

| 能做到 | 做不到 |
|---|---|
| 赶走「装个透视/杀戮光环就进服」的绝大多数人 | 防不住**内核级**对手（能读任意进程内存）—— 那需要驱动签名，与「几百 mod 整合包零 mixin」冲突，明确不做 |
| 让逆向成本高到不值得（签名挑战 + 随机复检 + 静默） | 防不住「重写一个假 agent」的人 —— 客户端在对方机器上，JVM 层物理上不存在不可破解 |
| 换号不换机的人被机器码封禁抓住 | 换电脑 + 换网络的人（那真是另一个人了） |
| 执行动作本身会暴露存在（踢人=被发现） | 「完全不被发现」做不到 —— 保密的是**检测细节**，不是存在本身 |

---

## 5. 性能（客户端零常驻开销）

- **客户端无 tick 循环**：纯载荷驱动，不进事件总线，静态开销 ≈ 0。
- mod 哈希按 `(size, mtime)` 缓存到 `.minecraft/voidkit_guard/modcache.json`，
  首检几秒、复检毫秒级。
- 重活全在后台线程：报告构建、深度扫描（PowerShell 6s 超时强杀）、GPU 读取（1.5s 超时）。
- 服务端：SQLite 单写线程 + WAL 多读 + 有界审计队列（满则丢，绝不堵主线程）。

---

## 6. 部署

### 安装

服务端 `mods/` 放 `voidkit_guard-1.0.0.jar` + `voidkit_core`；
玩家客户端同样放这两个 jar。没装客户端的玩家能进服，但握不上手 →
按 `integrityOnFail` 踢/封。

### 哈希锁开通流程（「先学习，后锁定」）

1. `/guard mode hashlock` —— 白名单为空时自动学习已开启。
2. 玩家进服，清单作为**待审**条目进白名单（`hashlockRequireReview=true` 会拦着等审核）。
3. `/guard whitelist list` 审查 → `/guard whitelist approve-all`（或逐个 approve）。
4. 之后任何新 mod / 被改过的 jar 一律拒之门外。
5. 玩家加了合法新 mod：服主 `/guard whitelist approve <modId>` 后放行。

### 密钥轮换（让旧客户端定期死亡）

1. `/guard key rotate` —— 换服务端密钥对，旧客户端立刻全部失效。
2. `/guard key export` 拿新公钥 base64。
3. 贴进客户端 `client/GuardKeys.kt` 的 `RING`，重新构建发布。
4. 被破解的 jar 每轮换一次就要重新破解一次 —— 成本转嫁给对手。

### 数据位置（`<服务端根>/voidkit/guard/`）

```
config.json       带中文注释的全部配置
guard.db          封禁/身份/审计/截屏记录（SQLite WAL）
server.pem        服务端 RSA 私钥（0600，私钥不出服务端）
screenshots/      截屏 PNG（按月分目录）
whitelist.json    哈希锁白名单
```

---

## 7. 命令与权限

| 命令 | 说明 | 权限节点 |
|---|---|---|
| `/guard status` `/guard mode` `/guard reload` `/guard whitelist ...` | 日常运维 | `voidkit.guard` |
| `/guard fence export` | 导出本服封禁清单 | `voidkit.guard` |
| `/gscan` `/greport` | 深度扫描与判定报告 | `voidkit.guard` |
| `/gban` `/gunban` `/gbanlist` `/gcheck` | 封禁管理 | `voidkit.guard.ban` |
| `/gscreen` `/greports` | 截屏与报告 | `voidkit.guard.screen` |
| `/guard key rotate` `/guard key export` | 🚨 **密钥轮换** | `voidkit.guard.key` |
| `/guard fence import` | 导入别服封禁清单 | `voidkit.guard.fence` |

**权限按破坏性分层**：

- `/guard key rotate` 会让**所有已发布客户端立刻失效**（不可逆），
  所以单独一个节点 —— 和 `/guard status` 这种「看一眼状态」不该同级。
- `/guard fence import` 会把**别服的封禁**写进本服，也单独一个节点。
- 这些节点**刻意不并入 `voidkit.admin`**：反作弊权限敏感，应该单独授予，
  而不是「给了管理总权限就顺带拿到封禁 / 密钥轮换」。
- 反作弊**没有面向玩家的命令** —— 没装/关掉权限模块时一律按原版 OP 判定，
  普通玩家全部拒绝。

## 8. API（给管理面板）

经 `voidkit_web` 的 `ApiRouter` 暴露：

```
GET  /api/guard/status
GET  /api/guard/sessions
GET  /api/guard/bans          POST /api/guard/bans
POST /api/guard/bans/lift
GET  /api/guard/identities
GET  /api/guard/screenshots   GET /api/guard/screenshots/{id}
POST /api/guard/scan
GET  /api/guard/report/{player}
GET  /api/guard/audit
GET  /api/guard/whitelist
```

在[管理平台](../panel/)里对应「反作弊」页：会话表、一键操作、封禁管理、判定报告、
白名单审核、跨服审计流水。

---

## 9. Roadmap

| 状态 | 项 |
|---|---|
| ✅ | X 射线统计检测（纯服务端，卸客户端也没用） |
| ✅ | 战斗遥测（reach / 瞄准急转 / CPS，只告警不处置） |
| ✅ | 蜜罐协议 |
| ⏳ | 群体离群评分（滚动 z-score 对比全服基线） |
| ❌ | LLM 读截图（需求方明确砍掉） |

## 10. 开发

```bash
./build.sh build       # → guard/build/libs/voidkit_guard-1.0.0.jar
./build.sh runServer   # 开发服务端（core + guard 一起从 classpath 加载）
```

- JDK 21，Gradle 8.12.1，Kotlin 2.1.0。
- 依赖 **voidkit_core 1.0.0**（platform 适配层版本）。
- 全 Kotlin，注释讲「为什么」；**零 mixin**；中文注释。
