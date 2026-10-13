# 1.20.1 移植施工单

> 分支：**`mc/1.20.1`**（`main` 保持 1.21.1 不动）
> 状态：**构建配置已就位；工具链被网络阻塞（见第零节）；源码移植未开始**。

---

## 零、🚧 当前阻塞：工具链下载被网络挡住（2026-10-13 诊断完成）

### 现象

```
> Task :core:createMinecraftArtifacts FAILED
  WARNING: de.oceanlabs.mcp:mcp_config:1.20.1-20230612.114412@zip is not present in the artifact manifest
  DL    https://maven.neoforged.net/releases/de/oceanlabs/mcp/mcp_config/1.20.1-...zip
Caused by: java.io.IOException: Tunnel failed, got: 502
    at java.net.http...PlainTunnelingConnection   ← 走了系统代理隧道
```

### 根因（已用 curl 逐条验证）

| 域名 | 直连 | 走代理 |
|---|---|---|
| `maven.neoforged.net` | ❌ 000 | ❌ 000 |
| **`neoforged.forgecdn.net`（官方 CDN）** | ✅ 200 | ✅ 200 |
| `maven.minecraftforge.net` | ✅ 200 | — |

即：**要下的那个文件就在 CDN 上（200 可下）**，但 NFRT 硬走 `maven.neoforged.net`
（国内 DNS 被污染），于是 502。

### 为什么现成的镜像脚本救不了

`mirror.init.gradle` 只重写 **Gradle 的仓库**；而 NFRT（NeoFormRuntime）是**独立进程**，
它的仓库地址来自 `--neoforge/--neoform` 传入的 NeoForm 配置 + userdev 元数据，
**init script 覆盖不到**。已确认：

- `javap` 看 MDG 的 `LegacyForgeExtension` / `NeoForgeExtension`：**没有公开的仓库入口**
- MDG jar 里硬编码了 `https://maven.neoforged.net/releases/`、`/mojang-meta/`、`https://maven.minecraftforge.net/`
- NFRT 命令行参数里**没有** `--repository` / `--mirror` 之类选项
- 制品清单（`nfrt_artifact_manifest.properties`）是「坐标 → **本地文件路径**」映射，
  且**每次运行都被 MDG 重写** —— 手工往里补条目无效（实测：补完再跑，条目被覆盖，仍去下载）

### 候选解法（按推荐顺序）

1. **把 CDN 映射到那个域名**（最干净）：hosts 加
   `neoforged.forgecdn.net` 对应的 IP → `maven.neoforged.net`
   （需要管理员权限改 hosts，或走**本地 HTTPS 反代 + 自签证书并导入信任库**）
2. **让 NFRT 的 NeoForm 配置指向 CDN**：找到 MDG 生成的那份 neoform 配置
   （`core/build/tmp/createMinecraftArtifacts/` 下的 json），把里面的
   `maven.neoforged.net` 全部替换为 `neoforged.forgecdn.net`，再手工跑一次该任务；
   但**同样会被重写**，只适合一次性把工具链缓存下来
3. **先在能直连的环境跑一次**（挂代理/VPN/换网络），把
   `~/.gradle/caches/neoformruntime/` 与 `~/.gradle/caches/modules-2/` 缓存下来复用
4. 给 MDG 提 issue：legacy 模式应允许配置 NFRT 的仓库镜像

> 💡 一次性成本：工具链只需成功下载**一次**，之后走缓存。
> 卡住的是「首次下载」，不是「每次构建」。

### 顺带修掉的一个真问题

`build.sh` 原来写死 JDK 21；但 1.20.1 的 `createMinecraftArtifacts` 至少要让
**Gradle 自己**跑在 JDK 17 上更稳。已改为按 `-PmcVersion=` 自动选
（1.20.* → JDK 17，其余 → JDK 21），并支持 `KOKUU_JDK17` / `KOKUU_JDK21` 覆盖。

---

## 一、关键结论（先读，能省掉大量试错）

### 1.1 NeoForge 1.20.1 ≡ Forge 47.1.x —— 一份 jar 两边通用 ✅

多方资料一致：

- NeoForge 从 Forge 分裂于 2023 年，**1.20.1 时期的构建「与 Forge 几乎完全相同」**，
  沿用 Forge 的老版本号（47.1.x）；
- **API 包名完全相同**（都是 `net.minecraftforge.*`），只有 Maven group 不同
  （`net.minecraftforge:forge` vs `net.neoforged:forge`）；
- **1.20.1 上绝大多数 Forge mod 可以直接在 NeoForge 上跑**（1.20.2 起才彻底分家）。

→ **所以我们只编译一份**，按公共 API（`net.minecraftforge.*`）出 jar，
Forge 1.20.1 与 NeoForge 1.20.1 都能加载。**不需要为两者各出一版。**

> 若将来要显式依赖 NeoForge 的制品，MDG legacy（≥ 2.0.87）支持
> `legacyForge { neoForgeVersion = "1.20.1-47.1.106" }`，会换成 `net.neoforged:forge`。

### 1.2 工具链：ModDevGradle **legacyforge** 插件

| 项 | 值 |
|---|---|
| 插件 id | `net.neoforged.moddev.legacyforge`（与 `moddev` 同版本号，**2.0.148** 已验证存在） |
| DSL 块 | `legacyForge { version = "1.20.1-47.4.20" }` |
| Java | **17** |
| 产物坐标 | `net.minecraftforge:forge:1.20.1-47.4.20`（maven.minecraftforge.net，已验证 200） |
| **重混淆** | Forge 1.20.1 用 SRG 中间名，**必须 reobf**：`jar` 任务自动配置，成品在 `build/libs`，开发态 jar 移到 `build/devlibs` |

> 1.20.1 与 1.21.1 的差异是「结构性」的，不是「换个版本号」能解决的 —— 见第二节。

---

## 二、施工清单

### 2.1 ✅ 已完成（本分支）

- [x] 建分支 `mc/1.20.1`
- [x] `build.gradle`：`plugins` 同时声明 `moddev` 与 `moddev.legacyforge`
- [x] `build.gradle`：版本线表加入 `1.20.1` 行（`engine: 'legacyforge'`, `loader: 'forge'`,
      `ver: '1.20.1-47.4.20'`, `java: 17`）
- [x] `build.gradle`：子项目按 `engine` 选择 `apply plugin` 与 `neoForge{}` / `legacyForge{}` 块
- [x] `build.gradle`：`processResources` 同时匹配 `neoforge.mods.toml` 与 `mods.toml`
- [x] `core/build.gradle`：`runs { server { ... } }` 随版本线切换 DSL 块

### 2.2 ⬜ 待做（按顺序）

#### A. `mods.toml` 改名与内容

1.20.1（Forge）读的是 **`META-INF/mods.toml`**，不是 `neoforge.mods.toml`：

```bash
# 对 8 个模块各做一次
git mv <模块>/src/main/resources/META-INF/neoforge.mods.toml \
       <模块>/src/main/resources/META-INF/mods.toml
```

内容上要改的：

```toml
<!-- 依赖项 modId：1.20.1 是 forge（NeoForge 1.20.1 同样提供 forge 这个 modId） -->
[[dependencies.voidkit_core]]
modId = "forge"          # ← 原来是 neoforge
type = "required"
versionRange = "[47,)"   # ← 由 ${loader_range} 提供（已在 build.gradle 里按版本线设为 [47,)）

[[dependencies.voidkit_core]]
modId = "minecraft"
type = "required"
versionRange = "[1.20.1,1.20.2)"   # ← 由 ${mc_range} 提供
```

> `displayName` / `description` / `authors` / `logoFile` 都不用动。

#### B. 包名重写（机械替换，全仓）

| 旧（1.21 NeoForge） | 新（1.20.1 Forge） |
|---|---|
| `net.neoforged.bus.api.SubscribeEvent` | `net.minecraftforge.eventbus.api.SubscribeEvent` |
| `net.neoforged.bus.api.IEventBus` | `net.minecraftforge.eventbus.api.IEventBus` |
| `net.neoforged.fml.common.Mod` | `net.minecraftforge.fml.common.Mod` |
| `net.neoforged.fml.ModContainer` | `net.minecraftforge.fml.ModContainer` |
| `net.neoforged.neoforge.common.NeoForge` | `net.minecraftforge.common.MinecraftForge` |
| `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| `net.neoforged.neoforge.event.server.ServerAboutToStartEvent` | `net.minecraftforge.event.server.ServerAboutToStartEvent` |
| `net.neoforged.neoforge.event.server.ServerStartedEvent` | `net.minecraftforge.event.server.ServerStartedEvent` |
| `net.neoforged.neoforge.event.server.ServerStoppingEvent` | `net.minecraftforge.event.server.ServerStoppingEvent` |
| `net.neoforged.neoforge.event.RegisterCommandsEvent` | `net.minecraftforge.event.RegisterCommandsEvent` |
| `net.neoforged.neoforge.event.entity.*` | `net.minecraftforge.event.entity.*` |
| `net.neoforged.neoforge.event.level.*` | `net.minecraftforge.event.level.*` |

#### C. 结构性差异（要动手改代码，不能只替换）

| 位置 | 1.21.1 (NeoForge) | 1.20.1 (Forge) |
|---|---|---|
| 每 tick | `ServerTickEvent.Post` | `TickEvent.ServerTickEvent`（`phase == END`） |
| 聊天 | `ServerChatEvent`：`message` / `rawText` | `ServerChatEvent`：`getMessage()` / `getRawText()`（字段名不同，需核对） |
| 右键物品 | `PlayerInteractEvent.RightClickItem` | 同名，但事件字段/包名不同 |
| mod 列表 | `net.neoforged.fml.ModList` | `net.minecraftforge.fml.ModList` |

**涉及文件（core/platform/neoforge 下，是最集中的地方）**：

```
core/src/main/kotlin/org/voidkit/core/platform/neoforge/
├── NeoForgeEventBridge.kt        ← VEvent 桥，事件类集中在这里
├── NeoForgeLifecycleBridge.kt    ← 生命周期事件
├── NeoForgeItemAccessor.kt       ← 物品组件 → NBT
└── NeoForgePlatform.kt           ← 平台实现
core/src/main/kotlin/org/voidkit/core/event/ServerLifecycle.kt
core/src/main/kotlin/org/voidkit/core/feature/CoreFeature.kt
```

**各模块入口类（8 个）**：`VoidkitCore` / `VoidkitEssentials` / `VoidkitDisplay` /
`VoidkitPerm` / `VoidkitTitle` / `VoidkitEconomy` / `VoidkitUtility` / `VoidkitWeb`
—— 都 import 了 `@Mod` / `IEventBus` / `ModContainer` / `MinecraftForge.EVENT_BUS`。

#### D. Minecraft API 差异（主要工作量，逐处编译驱动）

| 主题 | 1.21.1 | 1.20.1 |
|---|---|---|
| **物品自定义名 / Lore** | `DataComponents.CUSTOM_NAME`、「组件 patch」 | **NBT**：`stack.hasCustomHoverName()` / `setHoverName()` / `display.Name` |
| 物品组件系统 | `DataComponents.*`（1.20.5+ 引入） | 无组件，走 NBT |
| `isComponentsPatchEmpty` | 有 | **无** |
| 其它 | 待编译暴露 | 待编译暴露 |

> ✅ 好消息：`core/api/VItem.kt`（`ItemAccessor` / `VItems`）**就是为这件事准备的抽象**
> —— 受影响的主要是 `NeoForgeItemAccessor` 一个文件，而不是所有用到物品的地方。

#### E. 构建产物命名

`build.gradle` 的 `jar.archiveFileName` 用 `${mod:...}+mc${mcVersion}-${loader}`，
1.20.1 会自动产出 **`voidkit_core-1.0.0+mc1.20.1-forge.jar`** ✅（`loader` 已按版本线设为 `forge`）。

---

## 三、验证方式

```bash
# 1) 编译
./build.sh build --continue -PmcVersion=1.20.1

# 2) 真实启动（core/run 会被 MDG legacy 用于 dev 运行）
./build.sh runServer -PmcVersion=1.20.1
```

**合格标准**（与 1.21.1 一致）：

- `Done (...)! For help` 出现
- `[CMD] 注册命令，active 模块 = [...]` —— **23 个 feature 一个不少**
- **无** `[CMD] /xxx 注册失败`
- **无** `FATAL` / mod 依赖解析失败

命令实测：Gradle 的 `runServer` **不转发 stdin**，要开 RCON
（`core/run/server.properties` 里 `enable-rcon=true` + 密码），用极简 Python RCON 客户端发
`/vk status`。

> ⚠️ 1.20.1 的 dev 运行目录可能仍是 `core/run`，但**注意别把 1.21.1 的 jar 留在 `core/run/mods/`**
> —— 两个版本线的 jar 混放会直接起不来。

---

## 四、风险与注意

1. **首次构建很慢**：MDG legacy 要下载并反编译 Forge 1.20.1（NeoFormRuntime），
   可能数分钟；之后有缓存。
2. **SRG 重混淆**：成品必须用 `build/libs` 里 reobf 过的 jar，
   `build/devlibs` 里的是开发态，直接丢进服务器**不能用**。
3. **一定不要 `--no-verify` 跳过任何检查**，也不要为了「先编过」而删掉功能
   —— 移植的标准是行为等价，不是编译通过。
4. **分支纪律**：所有 1.20.1 改动只在 `mc/1.20.1` 上，**不要合回 `main`**；
   `core.api` 与插件业务代码的**跨版本改动**（如果有）要能同时满足两条版本线。

---

## 五、与「4 jar 合并」的关系

`MIGRATION.md`（在 `main` 上）描述的 **8 jar → 4 jar 合并**是**独立任务**。
建议顺序：**先把 1.20.1 移植做完并验证**，再做合并 —— 否则合并会与移植互相干扰，
出问题难定位。合并时在两条版本线上都要各自做一遍（或先把合并做进 `main` 再 forward-port）。
