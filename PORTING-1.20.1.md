# 1.20.1 移植施工单

> 分支：**`mc/1.20.1`**（`main` 保持 1.21.1 不动）
> 状态：**源码移植完成 + 「8 jar -> 5 jar」合并已 forward-port 到本线**（23 个 feature 全部注册、
> 28 个管理 API 全 200、数据结构断言全 ✓、命令注册 0 失败；开发服冒烟与正式服 reobf 复核均通过）。
> **合并后 1.20.1 线同样只出 5 个 jar**（`core` / `essentials` / `display` / `utility` / `web`），
> 与 1.21.1 线一致；`perm` / `economy` 源码并入 `essentials`，`title` 并入 `display`。
> 待办：Fabric（另见 `.worktrees/fabric` 分支的施工单）。

---

## 零、✅ 工具链网络阻塞 —— 已解决（2026-10-13）

> 结论：**不用改 hosts、不用管理员权限、不用换网络**。用 `tools/fetch-nfrt-artifacts.sh`
> 把缺失制品从官方 CDN 灌进 NFRT 缓存即可，**一次性，共补了 2 个文件**。
> 现在 `:core:createMinecraftArtifacts -PmcVersion=1.20.1` **能跑通**。

### 现象（已修复前的样子）

```
> Task :core:createMinecraftArtifacts FAILED
  WARNING: de.oceanlabs.mcp:mcp_config:1.20.1-...@zip is not present in the artifact manifest
  DL    https://maven.neoforged.net/releases/de/oceanlabs/mcp/mcp_config/1.20.1-...zip
Caused by: java.io.IOException: Tunnel failed, got: 502
```

### 根因

| 域名 | 直连 | 走代理 |
|---|---|---|
| `maven.neoforged.net` | ❌ 000 | ❌ 000 |
| **`neoforged.forgecdn.net`（官方 CDN）** | ✅ 200 | ✅ 200 |

该域名国内 DNS 被污染（系统 DNS / 阿里 / 腾讯 / doh.pub 给的 IP 分别是
`156.233.67.243` / `199.59.149.232` / `108.160.167.248` 等 Facebook 段地址，
TCP 443 要么关闭要么 TLS 直接被拦）。

**NFRT 是独立进程**，它的仓库地址来自 Forge userdev 配置，因此：
- `mirror.init.gradle` 只重写 **Gradle** 仓库 → 管不到
- MDG 扩展类没有公开仓库入口（`javap` 确认）；jar 里硬编码了该域名
- NFRT 命令行没有 `--repository` / `--mirror`
- 制品清单每次被 MDG 重写，手工补条目无效

### 解法：把制品从 CDN 灌进 NFRT 缓存

关键发现：**NFRT 的下载缓存路径 = `artifacts/` + 仓库 URL 里 `/releases/` 之后的部分**，
而官方 CDN **镜像了完全相同的路径**。例：

```
NFRT 要下:  https://maven.neoforged.net/releases/net/minecraftforge/mergetool/1.1.7/mergetool-1.1.7-api.jar
CDN 同路径:  https://neoforged.forgecdn.net/releases/net/minecraftforge/mergetool/1.1.7/mergetool-1.1.7-api.jar  ✅200
缓存落点:    ~/.gradle/caches/neoformruntime/artifacts/net/minecraftforge/mergetool/1.1.7/mergetool-1.1.7-api.jar
```

所以脚本只要「读日志 → 挑出走被污染域名的 URL → 改从 CDN 下 → 按相对路径写进缓存」：

```bash
# 1) 跑一次构建拿日志
./build.sh :core:createMinecraftArtifacts -PmcVersion=1.20.1 > /tmp/nfrt.log 2>&1
# 2) 灌入缺失制品（脚本会打印补了哪些）
tools/fetch-nfrt-artifacts.sh /tmp/nfrt.log
# 3) 重复 1-2 步直到成功（每次构建会多暴露几个，本次实际只需 2 轮）
```

**本次实际只补了 2 个**：`net.minecraftforge:mergetool:1.1.7:api`、
`org.jetbrains:annotations:24.0.1`。
（`mcp_config` 本地早有 ForgeGradle 缓存，手工复制进 NFRT 缓存即可。）

> 💡 这是**一次性**成本：工具链全部落缓存后，之后的构建都命中缓存。

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

**构建侧**

- [x] 建分支 `mc/1.20.1`
- [x] `build.gradle`：`plugins` 同时声明 `moddev` 与 `moddev.legacyforge`
- [x] `build.gradle`：版本线表加入 `1.20.1` 行（`engine: 'legacyforge'`, `loader: 'forge'`,
      `ver: '1.20.1-47.4.20'`, `java: 17`）
- [x] `build.gradle`：子项目按 `engine` 选择 `apply plugin` 与 `neoForge{}` / `legacyForge{}` 块
- [x] `build.gradle`：`processResources` 同时匹配 `neoforge.mods.toml` 与 `mods.toml`
- [x] `build.gradle`：**`reobfJar` 也要单独设 `archiveFileName`** —— 它是 MDG 注册的
      **另一个** Jar 任务，不继承 `jar` 上设的名字，不设就退回 `voidkit_core-1.0.0.jar`（见 2.3-①）
- [x] `build.gradle`：**`bundleZip` 按版本线取「发布态」任务**（legacyforge → `reobfJar`，
      neoforge → `jar`）。原来写死 `jar`，1.20.1 会把**开发态** jar 打进发布包（见 2.3-②）
- [x] `core/build.gradle`：`runs { server { ... } }` 随版本线切换 DSL 块
- [x] `core/build.gradle`：dev 运行显式补 UTF-8 的 JVM 参数（见 2.3-⑦）
- [x] `build.sh`：按 `-PmcVersion=` 选 JDK（1.20.* → JDK 17）；走代理时强制 TLS 1.2
- [x] `tools/fetch-nfrt-artifacts.sh`：把 NFRT 缺失制品从官方 CDN 灌进缓存（见第零节）

**源码侧**

- [x] `neoforge.mods.toml` → `mods.toml`；依赖 `modId` 改 `forge`；
      **依赖字段 `type = "required"` → `mandatory = true`**（见 2.3-③）
      （移植时是 8 个模块各一份；「8 jar -> 5 jar」合并 forward-port 到本线后只剩 5 个 jar /
      5 份 `mods.toml` —— `core` / `essentials` / `display` / `utility` / `web`，见 2.5）
- [x] 包名机械重写（见 2.2）
- [x] **模块入口类的构造器改无参**（见 2.3-④）：合并前 8 个 `@Mod` 入口类；合并后剩下 5 个
      `@Mod` 入口类（`VoidkitCore` / `VoidkitEssentials` / `VoidkitDisplay` / `VoidkitUtility` /
      `VoidkitWeb`）统一无参构造，被吸收的 `VoidkitPerm` / `VoidkitEconomy` / `VoidkitTitle`
      **去掉 `@Mod`**、保留无参构造，由宿主 `VoidkitEssentials` / `VoidkitDisplay` 构造期显式实例化。
- [x] 物品自定义名 / Lore：物品组件 → **NBT**（`NeoForgeItemAccessor`）
- [x] 每 tick：`TickEvent.ServerTickEvent` + `phase == END`
- [x] `ServerPlayer.latency`（**字段**，不在 `connection` 上）
- [x] `server.averageTickTime`（`float`，**毫秒**）替代 `averageTickTimeNanos`
- [x] `server.serverDirectory`（`java.io.File`）→ `.toPath()`
- [x] 记分牌：`ScoreHolder` → **裸字符串**持有者；`addObjective` 4 参；`Score.setScore(int)`；
      `Scoreboard.DISPLAY_SLOT_SIDEBAR`（int 常量，不是 `DisplaySlot` 枚举）
- [x] 清理 1.21 专属 import（`ItemLore` ×4、`DataComponents` ×8）
- [x] **`NeoForgeLifecycleBridge.onServerAboutToStart` 提为 `EventPriority.HIGHEST`**（见 2.3-⑧）

**验证侧**

- [x] `tools/smoke-test.sh` 版本线感知 + 存档隔离 + 失败判定补全（见 2.3-⑤⑥）
- [x] `core/util/ConfigFiles.resolve` 加未初始化校验，报错可读（见 2.3-⑧）
- [x] **开发服冒烟自检通过**：23 个模块全部注册、28 个 API 全 200、结构断言全 ✓、命令 0 失败

### 2.2 ✅ 包名映射（实际采用）

| 旧（1.21 NeoForge） | 新（1.20.1 Forge） |
|---|---|
| `net.neoforged.bus.api.SubscribeEvent` | `net.minecraftforge.eventbus.api.SubscribeEvent` |
| `net.neoforged.bus.api.IEventBus` | `net.minecraftforge.eventbus.api.IEventBus` |
| `net.neoforged.fml.common.Mod` | `net.minecraftforge.fml.common.Mod` |
| `net.neoforged.fml.ModContainer` | `net.minecraftforge.fml.ModContainer` |
| `net.neoforged.fml.ModList` | `net.minecraftforge.fml.ModList` |
| `net.neoforged.neoforge.common.NeoForge` | `net.minecraftforge.common.MinecraftForge` |
| `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| `net.neoforged.neoforge.common.util.FakePlayer` | `net.minecraftforge.common.util.FakePlayer` |
| `net.neoforged.neoforge.event.server.*` | `net.minecraftforge.event.server.*` |
| `net.neoforged.neoforge.event.RegisterCommandsEvent` | `net.minecraftforge.event.RegisterCommandsEvent` |
| `net.neoforged.neoforge.event.TickEvent` | `net.minecraftforge.event.TickEvent` |
| `net.neoforged.neoforge.event.entity.*` | `net.minecraftforge.event.entity.*` |
| `net.neoforged.neoforge.event.level.*` | `net.minecraftforge.event.level.*` |

### 2.3 🕳️ 移植中实际踩到的坑（逐条记录，全部已修）

> 这一节是**这次移植真正的价值**：编译器不会报、单测抓不到、只有真启动才会暴露的东西。

#### ① `reobfJar` 是独立任务，不继承 `jar` 的文件名

1.20.1 的产物有两份，文件名规则都得各自设一遍：

| 任务 | 输出 | 内容 |
|---|---|---|
| `jar` | `build/devlibs/voidkit_core-1.0.1+mc1.20.1-forge.jar` | **开发态**（官方映射名） |
| `reobfJar`（MDG 注册） | `build/libs/voidkit_core-1.0.1+mc1.20.1-forge.jar` | **发布态**（SRG 名，`m_/f_`） |

`reobfJar extends org.gradle.api.tasks.bundling.Jar`，但它是个**新任务**，只继承
`archiveBaseName` / `archiveVersion` 的约定值，**不会**继承 `jar { archiveFileName = ... }`
—— 不单独设就会产出 `voidkit_core-1.0.0.jar`，多版本发布时无法一眼分辨。

修法：`tasks.matching { it.name == 'reobfJar' }.configureEach { archiveFileName = ... }`
（惰性匹配，对 1.21.x 线零影响）。

#### ② `bundleZip` 曾经会把「开发态」jar 打进发布包 —— 静默的致命错误

`bundleZip` 原来无条件收集 `p.tasks.named('jar')`。1.21.x 上 `jar` 就是发布产物，没问题；
但 1.20.1 上 `jar` 产的是 **devlibs 里的开发态 jar**（官方映射名）。这种 jar 丢进正式服
不会"装不上"，而是**加载正常、一调用就 `NoSuchMethodError`** —— 最坏的一类错误。

修法：按版本线取发布态任务（`legacyforge` → `reobfJar`，否则 `jar`）。

#### ③ Forge 1.20.1 的 `mods.toml` 依赖字段是 `mandatory`，不是 `type`

```
InvalidModFileException: Missing required field mandatory in dependency (voidkit_display-...jar)
```

NeoForge 1.20.2+ 把依赖必选性改成 `type = "required" | "optional"`；Forge 1.20.1 仍只认
`mandatory = true | false`。报错发生在 mods/ 扫描阶段，还会连带报
`Failed to find system mod: minecraft`，极易被误读成"模组内部崩了"。

修法：`tools/fix-1201-modstoml.py`（保留，新增模块时还要用）。
> 注：内嵌 jarJar 的 `META-INF/jarjar/metadata.json` **格式两边一致**
> （对照 `net.minecraftforge:JarJarMetadata:0.3.19` 的序列化器确认），不用改。

#### ④ Forge 1.20.1 的 mod 构造器**只支持两种签名**

```
Failed to create mod instance. ModID: voidkit_core
java.lang.NoSuchMethodException: org.voidkit.core.VoidkitCore.<init>()
```

`FMLModContainer.constructMod()` 只尝试：

```java
constructor = modClass.getDeclaredConstructor(context.getClass());   // (FMLJavaModLoadingContext)
// 失败则退回
constructor = modClass.getDeclaredConstructor();                      // ()
```

`(IEventBus, ModContainer)` 是 NeoForge 1.21 才有的注入方式。本套件不使用 mod 事件总线
（游戏事件统一走 `MinecraftForge.EVENT_BUS`），所以模块入口类统一改成**无参构造**
（合并前 8 个，合并后 5 个 `@Mod` 入口类；被吸收的 perm / economy / title 去掉 `@Mod` 后
同样保留无参构造，由宿主实例化）。

#### ⑤ 开发服要装 `devlibs`，不能装 `libs`（1.20.1 特有）

`runServer` 的开发运行时是**官方映射名**的 Minecraft，而 `build/libs` 里的 jar 是
**SRG 名**的。装错的症状极具迷惑性：模组能加载、命令能注册、**一部分接口一调用才炸**：

```
NoSuchMethodError: 'int net.minecraft.server.MinecraftServer.m_129921_()'
NoSuchMethodError: 'boolean net.minecraft.server.MinecraftServer.m_18695_()'
NoSuchMethodError: 'net.minecraft.network.chat.MutableComponent ... Component.m_237119_()'
```

修法：smoke 脚本按版本线选目录（`1.20.*` → `build/devlibs`，否则 `build/libs`）。
> ⚠️ 这意味着**冒烟自检验证不了 reobf jar**，它只覆盖开发态。要验证发布产物，
> 必须丢到**真正的 Forge 1.20.1 服务端**上跑（见第三节）。

#### ⑥ 存档有版本线：1.20.1 拒绝加载 1.21.1 生成的 world

`core/run/world` 里带 `DataVersion`，跨版本线直接启动失败，报错还长得像"模组炸了"。
修法：smoke 脚本用 `core/run/.voidkit-last-mcver` 记录上次跑哪条线，不一致就把 world
**改名保留**（`mv`，不删）再重新生成。

顺带补了等待循环的失败判定：1.20.1 崩在服务端线程时 **Gradle 依然报 `BUILD SUCCESSFUL`**，
只认旧 pattern 会空等满 8 分钟才报错。

#### ⑦ JDK 17 在中文 Windows 上默认 `file.encoding=GBK`

JEP 400（默认 UTF-8）要到 **JDK 18** 才生效，而 1.20.x 线被迫用 **JDK 17**
（Forge 1.20.1 的 `createMinecraftArtifacts` 本身就跑不了在 21 上）。后果：

- 服务端日志里的中文以 GBK 落盘，而 Gradle 自身输出是 UTF-8 → 日志文件**混合编码**；
- 用 UTF-8 打开全是乱码，面板 / Hub 读日志尤其难看。

修法：dev 运行显式加 `-Dfile.encoding=UTF-8` / `-Dsun.stdout.encoding=UTF-8` /
`-Dsun.stderr.encoding=UTF-8`（JDK 21 上本来就是默认值，写上无害）。
> 生产环境部署时同样建议在 JVM 参数里加这三条。审计过全部文件 IO：**都显式写了 UTF-8**
> （`ConfigFiles` / `JoinLog` / `DataStore` / `MotdFeature` / `ApiServer`），
> 所以数据文件不受默认编码影响，只有日志受。

#### ⑧ 内核初始化与插件的顺序：不能靠"注册先后"这种偶然

```
UninitializedPropertyAccessException: lateinit property rootDir has not been initialized
    at ConfigFiles.resolve → PermissionStore.load → VoidkitPerm 的 ServerAboutToStart 监听器
```

除内核外，各插件（移植时为 8 jar 里的 7 个插件；合并后是 4 个插件 jar）都在构造期
`MinecraftForge.EVENT_BUS.addListener { ServerAboutToStartEvent -> ... }` 读自己的配置，
隐含依赖内核先完成"建目录 → `ConfigFiles.init` → `DataStore.init`"。
**1.21.1 上碰巧顺序正确**；1.20.1 上 Forge 并行构造 mod，且
`EVENT_BUS.addListener(Consumer)` 与 `EVENT_BUS.register(obj)` 是两条注册路径，
实测插件全部排在内核前面 → 启动即崩。

修法（两处，都是把"偶然"变成"保证"）：

1. `NeoForgeLifecycleBridge.onServerAboutToStart` 标 `@SubscribeEvent(priority = EventPriority.HIGHEST)`
   —— 事件优先级在 Forge / NeoForge 两条线都支持，顺序写死在代码里；
2. `ConfigFiles.resolve` 加未初始化校验：报错直接说清"配置读写必须在内核
   `onServerAboutToStart` 之后"并给出排查方向，而不是让人对着 `lateinit` 发呆。

> 这两条改动**与版本线无关**，`main`（1.21.1）也建议 cherry-pick 过去。

---

## 三、验证方式

### 3.1 开发服冒烟自检 —— ✅ 已通过（2026-10-13）

```bash
tools/smoke-test.sh -PmcVersion=1.20.1
```

脚本流程：按版本线构建 → 把各插件的**开发态** jar 放进 `core/run/mods/` → 起开发服
→ 打 28 个管理 API → 跑数据结构断言 → 收尾。

> ⚠️ 这里装的必须是 `build/devlibs` 的**开发态** jar（官方映射名），不是 `build/libs`
> 的发布态（SRG 名）—— 详见 2.3-⑤，装反了是一堆 `NoSuchMethodError`。

实测结果：

| 检查项 | 结果 |
|---|---|
| `Done (...)! For help` | ✅ |
| 插件 jar 数 | ✅ 4 个（essentials / display / utility / web；core 由开发环境从 classes 提供，不放 jar） |
| 模块注册 | ✅ 23/23 |
| `[CMD] 注册命令，active 模块 = [...]` | ✅ 23 个，**0 失败** |
| 管理 API（28 个） | ✅ 全部 HTTP 200 |
| API 结构断言 | ✅ 全 ✓（19 项，含 `/api/perf` 的 `chunks`） |
| `FATAL` / 依赖解析失败 | ✅ 无 |

### 3.2 正式服复核（reobf jar）—— 冒烟自检覆盖不到的一层

`runServer` 跑的是开发态代码，**验证不了发布产物**（SRG 重混淆 + jarJar 内嵌
kotlin-stdlib）。这一层必须在**真正的 Forge 1.20.1 服务端**上过：

```bash
# 干净环境放 build/e2e-1201/（已在 .gitignore 的 build/ 下）
cd build/e2e-1201
java -jar forge-1.20.1-47.4.20-installer.jar --installServer
# 把 5 个 build/libs/*+mc1.20.1-forge.jar（core / essentials / display / utility / web，含 core）放进 mods/
# eula=true；server-port 用 25599 之类的非占位端口避免撞车
java -Dfile.encoding=UTF-8 @libraries/net/minecraftforge/forge/1.20.1-47.4.20/unix_args.txt nogui
```

除 3.1 的合格标准外，还要专门确认两类"只在正式服出现"的错误：

- `NoSuchMethodError: ... m_xxxxx_()` → 装了开发态 jar（该用 `build/libs`）；
- `NoClassDefFoundError: kotlin/jvm/internal/...` → jarJar 内嵌 kotlin-stdlib 没生效。

### 3.3 合格标准（与 1.21.1 一致）

- `Done (...)! For help` 出现
- `[CMD] 注册命令，active 模块 = [...]` —— **23 个 feature 一个不少**
- **无** `[CMD] /xxx 注册失败`
- **无** `FATAL` / mod 依赖解析失败

> 注：Gradle 的 `runServer` **不转发 stdin**，所以不适合用控制台敲命令；
> 冒烟脚本改用**管理 HTTP API**（独立端口 8766）做端到端自检，比 RCON 更贴近面板的真实用法。

---

## 四、风险与注意

1. **两套产物用途不能混**（最容易踩、后果最隐蔽）：
   - `build/devlibs/` = 开发态（官方映射名）→ 只给 `runServer` / IDE 调试
   - `build/libs/` = 发布态（reobf / SRG 名）→ 只给正式服 / 整合包

   装反了**不会**"装不上"，而是模组能加载、命令能注册，跑到某条代码路径才炸。
2. **首次构建很慢**：MDG legacy 要下载并反编译 Forge 1.20.1（NeoFormRuntime），
   可能数分钟；之后有缓存。网络被污染时的处置见第零节。
3. **JDK 17 的默认编码**：正式服也建议在 JVM 参数里加 `-Dfile.encoding=UTF-8`
   （中文 Windows 上 JDK 17 默认 GBK，见 2.3-⑦）。
4. **一定不要 `--no-verify` 跳过任何检查**，也不要为了「先编过」而删掉功能
   —— 移植的标准是行为等价，不是编译通过。
5. **分支纪律**：1.20.1 的改动只在 `mc/1.20.1` 上，**不要合回 `main`**。
   但 2.3-⑧ 那两条（事件优先级 + ConfigFiles 未初始化校验）是**版本无关**的，
   建议 cherry-pick 回 `main` —— 它在 1.21.1 上也是个隐患，只是碰巧没触发。
6. **`core/run/mods/` 里不要混两条版本线的 jar**：脚本已按 `+mc<版本>-<加载器>` 过滤，
   手工操作时要留意。

---

## 五、与「5 jar 合并」的关系（已完成 forward-port）

`MIGRATION.md` 描述的 **8 jar → 5 jar 合并**（`core` 与 `web` 不动，其余按
`essentials` / `display` / `utility` 合并）原先被列为独立任务，**现已完成，并已 forward-port 到
本线**。

### 5.1 合并怎么落地的

1. 合并先在 `main`（1.21.1）上做完（`feat/5jar-merge`，提交 `0664fc0` / `0edf0c2` /
   `7854194` / `9b6e7ed`），再合入 `main`；
2. 把 `main` 合并进 `mc/1.20.1`：合并分支**搬了文件**
   （`perm/` / `economy/` → `essentials/src/main/kotlin/org/voidkit/{perm,economy}/`，
   `title/` → `display/src/main/kotlin/org/voidkit/title/`），因此产生大量
   **modify/delete 冲突**；
3. 冲突取舍原则：**内容取 `mc/1.20.1` 一侧（含全部 1.20.1 移植修正），位置取合并后的新路径**。
   具体地：
   - 纯搬运（R100）的文件由 git 自动解决了「改名 + 保留 1.20.1 内容」；
   - 被吸收的三个入口类（`VoidkitPerm` / `VoidkitEconomy` / `VoidkitTitle`）取 1.20.1 的
     Forge import，去掉 `@Mod`（合并分支的结构改动），落到新路径；
   - `mods.toml`（改名后的文件）由 git 自动合并了「1.20.1 的 `mandatory = true`」与
     「合并后的一 jar 一 modId 注释」；
   - 被吸收模块自己的 `mods.toml` / `icon.png` / `build.gradle` 一律删除（不再各自成 jar）。

### 5.2 合并后 1.20.1 线的不变量（回归点）

**合并不改变功能粒度**：仍是 23 个 feature；每个 feature 仍可单独开关。
**合并不改变数据路径**：`core/run/voidkit/` 下仍是 `perm/` / `economy/` / `title/` 三个目录
（路径是代码里的字面量常量，与 modId 无关），升级后配置与数据不丢。

合并 + forward-port 后，下面这些 1.20.1 线的既有不变量**必须依然成立**（回归清单，逐条对照
第 2.3 节）：`mods.toml`（非 `neoforge.mods.toml`）+ `mandatory = true`；`@Mod` 入口类无参构造；
`reobfJar` 单独设产物名；`bundleZip` 取 `reobfJar`；dev 运行 UTF-8 参数；
`core/platform/neoforge/` 的 NBT / `toPath()` / `latency` 字段 / `averageTickTime` 毫秒 /
裸字符串记分牌；`EventPriority.HIGHEST`；`ConfigFiles.resolve` 未初始化校验；`build.sh` 选 JDK + TLS 1.2。

### 5.3 验证（合并后重跑）

- 开发服冒烟：`tools/smoke-test.sh -PmcVersion=1.20.1` —— 4 个插件 jar（core 由开发环境提供）、
  23 个 feature 全注册、28 个管理 API 全 200、结构断言全 ✓、0 注册失败；
- 正式服复核：`tools/e2e-forge-server.sh` —— 5 个 `build/libs/*+mc1.20.1-forge.jar` 丢进干净
  Forge 1.20.1 服务端，退出码 0，无 `NoSuchMethodError` / `NoClassDefFoundError`。

