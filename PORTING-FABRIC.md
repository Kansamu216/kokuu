# Voidkit → Fabric 移植施工单

> 目标：把 Voidkit 在 **Fabric（1.20.1 / 1.21.1）** 上跑起来，纯服务端、零 mixin、业务代码尽量零改动。
>
> 模块形态：当前分支是 **8 个模块**（`core`/`essentials`/`display`/`perm`/`title`/`economy`/`utility`/`web`）；
> 合并任务完成后为 **5 个 module / 5 个 modId**（`perm`+`economy` 并入 `essentials`，`title` 并入 `display`，
> `utility`/`web`/`core` 不变）。两种形态在本文里都覆盖（见 §6.1）。
>
> 本文所有结论都有依据：**读过的仓库文件**、**解包的 Fabric API jar**、**Fabric Loom 官方文档与源码**。
> 凡是没有实锤的地方，都在文中标注「⚠️ 待实测」，不凭印象下结论。
>
> 适用分支：`feat/fabric`（基于 `main` = 1.21.1 线；1.20.1 的 Fabric 版将来另开分支）。
> 反作弊 `voidkit-guard`、审计 `voidkit-inspector` **不在本套件内**（独立仓，另行处理）。
>
> ## 实施进度（2026-10-13）
>
> | 阶段 | 状态 |
> |---|---|
> | 0 · Loom 构建底座 | ✅ 完成 |
> | 1 · core 能被 Fabric 加载 | ✅ 完成（`Done (6.400s)! For help`） |
> | 2 · 23 个 feature 全量注册 | ✅ 完成（`23 / 23 已启用`） |
> | 3 · 逐个 feature 行为恢复 | ⬜ 待做（需真人 / RCON 进服逐项验证） |
> | 4 · 管理 API 与打包 | 🟡 打包已验证（`bundleZip` 出 5 个 remap jar）；管理 API 尚未跑 |
>
> 实施中实测到 4 个**原文没有预料到**的坑，见 §8「实施记录与实测踩坑」。

---

## 0. 结论速览（TL;DR）

| 问题 | 结论 |
|---|---|
| Fabric 用什么构建 | **Fabric Loom**。本仓 Gradle 8.12.1 → 用 **Loom 1.10.5**（见 §1.1）。 |
| 能否在同一 Gradle 工程按 `-Ploader` 在 MDG / Loom 间切换 | **同一个 subproject 不行**。两者都接管 `jar`/`build`/`runServer` 与 Minecraft 依赖，必然打架。**但**可以「一个多模块工程、每加载器一个 subproject」共存（§1.2）。 |
| 给本仓的落地方式 | 沿用用户已定的**走分支**：Fabric 分支把根 `build.gradle` 的 MDG 换成 Loom 即可，不做 `-Ploader` 切换。版本切换仍用 `-PmcVersion`（Loom 与 MC 版本无关，天然支持）。 |
| 映射用什么 | **必须用 `loom.officialMojangMappings()`**（Mojang 官方名）。这样全部 `net.minecraft.*` 与 NeoForge 线**同名**，业务代码零改名（§1.4）。 |
| 业务代码要改多少 | 只有 **5 个模块入口类（合并前 8 个）+ `core.platform` + `core.api/Feature.kt` 的一处 import** 要动。其余 100% 复用（§2）。 |
| Kotlin 运行时怎么带 | **`include`（jar-in-jar）**，只有 `core` 带一份 stdlib，与 NeoForge 线的 `jarJar` 对称（§3.5）。 |
| 纯服务端 | `fabric.mod.json` 的 `"environment": "server"`；不发自定义网络包 → **原版客户端可直连**（§3.6）。 |
| 合并形态（已定） | **真源码合并、一 jar 一 modId**：5 个 module / 5 个 `fabric.mod.json`（§6.1）。 |
| 最大风险 | ① FakePlayer 判定 Fabric 无官方类（§6.2）；② 1.20.1 与 1.21.1 的**唯一**事件 API 差异 = `ServerPlayerEvents.JOIN/LEAVE` 在 1.20.1 不存在（§3.1.1）。 |
| **实施中发现的额外风险** | Fabric 的 **entrypoint 顺序不保证**（官方如此声明），`depends` 也管不住 → 插件读配置**不能**靠监听加载器事件，必须走内核钩子（§8-④）。 |

---

## 1. 工具链可行性（最关键）

### 1.1 Fabric 的构建工具与版本选型

**Fabric 用 Fabric Loom**（Gradle 插件）。关键事实（来源：<https://docs.fabricmc.net/develop/loom/>）：

> "Loom supports *all* versions of Minecraft, even those not officially supported by Fabric API, **because it is version-independent**."

即 **Loom 与 MC 版本解耦** —— 同一个 Loom 版本既能构建 1.20.1 也能构建 1.21.1，版本差异只体现在
`minecraft`/`mappings`/`fabric-api` 三个依赖坐标上。这和本仓现在 `-PmcVersion` 切版本线的思路完全契合。

**Loom 版本受 Gradle 版本约束**（来源：Loom 各版本 release notes / ModDevGradle 迁移实践）：

| Loom | 最低 Gradle | 备注 |
|---|---|---|
| 1.8 | 8.10 | |
| 1.9 | 8.11 | 最低 Kotlin 2.0，支持 Kotlin 2.1（改用 kotlin-metadata） |
| **1.10** | **8.12** | **← 本仓 Gradle 8.12.1 落在这一档** |
| 1.11 | 8.14 | 要求 Java 21；"Loom 1.11 是最后一个支持 Gradle 8.x 的版本" |
| 1.14 | 9.2 | 引入新插件 ID `net.fabricmc.fabric-loom-remap` |

本仓 `gradle/wrapper/gradle-wrapper.properties` 是 `gradle-8.12.1`（腾讯云镜像）。
**⇒ 选 Loom 1.10.5**（`maven.fabricmc.net` 上存在，已 `curl` 验证 200）。插件 ID 用老名字 `fabric-loom`
（Loom 1.14 才引入 `net.fabricmc.fabric-loom-remap`，1.10 没有）。

**版本线建议表**（Fabric 官方 meta 与 maven 实测，日期 2026-10）：

| 目标 | Loom | Fabric Loader | Fabric API | 映射 | JDK |
|---|---|---|---|---|---|
| **1.21.1** | 1.10.5 | 0.19.5（stable；最低要求 ≥0.15.11） | `0.116.17+1.21.1` | `officialMojangMappings()` | 21 |
| **1.20.1** | 1.10.5 | 0.19.5（同上） | `0.92.12+1.20.1` | `officialMojangMappings()` | 17 |

> ✅ **两个版本的 Fabric API 事件类/签名都已解包 `javap` 复核**（1.21.1 = `0.116.17+1.21.1`，
> 1.20.1 = `0.92.12+1.20.1`）。唯一实质差异见 §3.1.1。命令见附录 A。

依据：
- Loader 版本：`https://meta.fabricmc.net/v2/versions/loader/1.21.1` 与 `/1.20.1` 均返回 `0.19.5`（stable）。
- Fabric API 版本：`maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`，
  1.21.1 尾号 `0.116.17+1.21.1`，1.20.1 尾号 `0.92.12+1.20.1`。
- Loader ≥0.15.11：来自解包 `fabric-lifecycle-events-v1` 的 `fabric.mod.json` → `"fabricloader": ">=0.15.11"`。
- Fabric API 0.116.17 的 `fabric.mod.json` 声明 `"minecraft": ">1.20-alpha..."` 之类宽范围，但**必须用 `+1.21.1` 后缀那一支**——
  `+1.21.11` 是另一个 MC 版本，别选错（maven 列表里两者混排，很容易看漏）。

**网络连通性（已实测）**：
```
maven.fabricmc.net                          → 200（直连，无需代理）
piston-meta.mojang.com（MC 版本清单/映射）   → 200
libraries.minecraft.net                     → 根路径 404（Maven 仓库正常表现，可达）
```
> 注意：仓库里的 `mirror.init.gradle` 只重写 `maven.neoforged.net`，对 Fabric **不生效也不需要**。
> Loom 会自动添加 `maven.fabricmc.net` + Mojang + Maven Central 三个仓库。
> 若国内网络对 `libraries.minecraft.net`/`piston-meta.mojang.com` 不稳，再考虑补 init-script 重定向
> （BMCLAPI 覆盖 Mojang 资源；但 Fabric 站点本身直连可用）。

### 1.2 同一工程能否按参数在 MDG 与 Loom 之间切换？—— **不能（同一 project）**

**结论：ModDevGradle（`net.neoforged.moddev`）与 `fabric-loom` 不能同时 apply 到同一个 Gradle project。**

依据（都是「两个插件各自独占同一批任务/配置」）：

1. **Loom 接管 `jar` 与 `build`**（来源：Loom 官方文档「Default Configuration」）：
   > "Configures the `remapJar` task to output a JAR with the same name as the `jar` task output, then adds a "dev" classifier to the `jar` task"
   > "Adds the `remapJar` task and the `remapSourcesJar` task as dependencies of the `build` task"
   
   即 Loom 把 `jar` 变成「开发态中间产物（`-dev` classifier）」，真正的交付物是 `remapJar`，并把 `build` 重挂到 `remapJar`。

2. **MDG 同样接管 `jar`/`build`/`runServer`**：本仓 `build.gradle` 里
   `jar { archiveFileName = ... manifest { ... } }` 就是 MDG 管线下的 `jar`；MDG 还提供 `runServer`/`runClient`
   与 `neoForge { mods { } }`、`additionalRuntimeClasspath`（见 `build.gradle:111-115`）。两边都要定义
   `jar` 的名字、manifest、以及「运行时 classpath 怎么拼」。

3. **两边都争夺 Minecraft 依赖与映射**：MDG 用 `neoForge { version = ... }` 自行解析 Minecraft/NeoForge；
   Loom 用 `minecraft "com.mojang:minecraft:..."` + `mappings "..."` 自行解析并 remap。同时存在 → 依赖冲突 + 双份反编译/remap。

4. **业界没有「同 project 双插件」的先例**：多加载器工程的通行做法是**每加载器一个 subproject**，
   根 `build.gradle` 里两个插件都 `apply false`，各 subproject 只 apply 一个
   （来源：多加载器迁移实践 <https://github.com/crittscott/SomeStacks/blob/main/multi-loader-transition.md>；
   统一抽象层 Modstitch <https://github.com/isXander/modstitch> 也是「一个 buildscript 一次面向一个加载器」，
   常配合 Stonecutter 预处理器为每个目标生成独立 source set，而**不是**同一源码集同时挂两个插件）。

**⇒ 所以「按 `-Ploader` 在 MDG/Loom 间切换」在单 project 层面不可行。**

### 1.3 若将来真要「一个工程出多加载器」，替代方案与代价

| 方案 | 做法 | 代价 |
|---|---|---|
| **A. 每加载器一个 subproject**（Architectury/Loom 标准模式） | 拆 `:common`（纯 loader-free 源码）+ `:fabric` + `:neoforge`；根声明两个插件 `apply false`；`:fabric` 用 `project(path: ":common", configuration: "namedElements")` 依赖（Loom 官方文档「Depending on Subprojects」）。 | 模块数翻倍；`common` 必须真的不 import 加载器类；资源（mods.toml/fabric.mod.json）要按加载器各出一份；本仓 8 模块 → 至少 17 个 subproject。**用户已否决**（要「走分支」）。 |
| **B. 复合构建 `includeBuild`** | 各加载器一个独立 Gradle 构建目录，共享一个普通 Kotlin library 模块。 | 两套 wrapper/缓存/IDE 工程；共享层要脱离 loader 类；调试跨构建麻烦。 |
| **C. 走分支（本项目选定）** | `main` 用 MDG；`feat/fabric-*` 分支把根 `build.gradle` 换成 Loom。同一时间只 checkout 一个加载器。 | 无法一次构建同时产出两加载器（CI 要分别 checkout）；merge/back-port 需人工 forward-port。**但**源码组织最干净、改动最小，符合用户决策。 |

**本仓落地就按 C**：Fabric 分支不动模块结构，只换构建插件 + 入口 + 平台层（见 §2、§7）。

### 1.4 映射：**必须用官方 Mojang 映射**（这是「业务代码零改动」的关键）

Loom 支持三种映射：Yarn / 官方 Mojang / 自定义。**本仓必须用 `loom.officialMojangMappings()`**：

- NeoForge 线（MDG）用的就是 Mojang 官方名，所以 `net.minecraft.server.level.ServerPlayer`、
  `net.minecraft.world.item.ItemStack`、`net.minecraft.network.chat.Component` 在两线**完全同名**。
- 若改用 Yarn，会变成 `ServerPlayerEntity`/`ItemStack`(同名但包不同)/`Text` —— **全仓所有 `net.minecraft.*`
  引用都要改名**，与「只重写 platform 层」的目标直接冲突。**不要用 Yarn。**
- Fabric API 是发在 intermediary 名下的，Loom 会在 `modImplementation` 时把它 remap 到本工程映射，
  所以 API 内部对 `MinecraftServer` 的引用会自动对齐官方名，无需我们关心。

> 验证方式：解包 Fabric API jar，`javap` 看到的是 `net.minecraft.class_2168`（intermediary）；
> 但那是发布产物。**开发环境里 Loom 会 remap 成官方名**（`CommandSourceStack`）。

### 1.5 服务端产物怎么出：`jar`(dev) 与 `remapJar`(intermediary) 的区别

Fabric 的混淆链与 NeoForge 不同，必须搞清楚「开发态」与「发布态」：

| | NeoForge 线（MDG） | Fabric 线（Loom） |
|---|---|---|
| 开发态运行时 | 加载 `build/classes`（named 名） | 加载 `build/classes` + `-dev` jar（named 名，`-Dfabric.development=true`） |
| 交付 jar | `jar` 直接可用（ModLauncher 运行时映射） | **只有 `remapJar` 产物可用**：被 remap 到 **intermediary** 名 |
| 中间命名 | SRG→官方 | **intermediary**（`net.minecraft.class_xxxx`） |
| 任务名 | `jar` | `jar`（自动 `-dev`）+ **`remapJar`**（交付） |

Loom 默认行为（官方文档）：
- `jar` 任务产物被加 `-dev` classifier；
- `remapJar` 产物**沿用 `jar` 的名字**（不带 `-dev`），并成为 `build` 的依赖与 `archives` 制品。

**⇒ 交付/发布要取 `remapJar` 的产物（`build/libs/voidkit_xxx-...+mc1.21.1-fabric.jar`），
不要发 `-dev.jar`（只能在开发映射环境跑）。** 因此 `bundleZip` 任务要把 `tasks.named('jar')`
改成 `tasks.named('remapJar')`（见 §7）。

> `include` 的嵌套 jar 也是被塞进 `remapJar` 的（Loom 文档：
> "Dependencies declared with `include` are added to `remapJar` in remapping Loom"）。

---

## 2. 现有抽象层要动哪里

先给**实测的「加载器泄漏面」**（`grep -rn "net\.neoforged\.|@SubscribeEvent" --include=*.kt`，排除 `platform/neoforge/`）：

```
核心入口:   core/.../VoidkitCore.kt                        import net.neoforged.* / @Mod
7 个插件入口: essentials|display|perm|title|economy|utility|web 各自的 VoidkitXxx.kt（含 web）
              （合并后 perm/economy/title 的入口并入宿主，共 4 个插件入口）
平台实现:   core/.../platform/neoforge/{NeoForgePlatform, NeoForgeEventBridge, NeoForgeLifecycleBridge}.kt
契约层泄漏: core/.../api/Feature.kt:6  import net.neoforged.bus.api.SubscribeEvent
清单文件:   */src/main/resources/META-INF/neoforge.mods.toml
```

**除以上之外**，`core.api` / `core.data` / `core.config` / `core.util` / `core.feature` /
`core.teleport` / `core.entity` / 各插件的 feature 业务代码 **全部只用 `net.minecraft.*` + `core.api`** —— 直接复用。

### 2.1 逐项判定

| 文件 / 接口 | 判定 | 说明 |
|---|---|---|
| `platform/Platform.kt`（接口） | **原样可用** | 契约本身不含加载器类，字段只有 `Platform`/`ServerLifecycleListener`。 |
| `Platform.registerListener/unregisterListener` | **保留但 Fabric 实现为空操作** | Fabric 无「带注解监听方法」的事件总线；改用回调。见 §2.2。 |
| `Platform.registerLifecycle` | **原样可用** | Fabric 侧写 `FabricLifecycleBridge` 即可。 |
| `Platform.isFakePlayer` | **需 Fabric 实现（降级）** | Fabric **无官方 `FakePlayer` 类**（已查：解包 `fabric-api` 无 `FakePlayer`）。当前 core 里无人调用它，先返回 `false` 并标注待办；inspector 仓移植时再认真做。 |
| `Platform.isUnconnectedPlayer` | **原样可用** | 逻辑是 `player is ServerPlayer && player.connection == null`，纯原版。 |
| `Platform.gameDir()` | **需 Fabric 实现** | `FabricLoader.getInstance().gameDir`。 |
| `Platforms.kt` | **需小改** | 现在 `current` 默认值是 `NeoForgePlatform`（**编译期硬引用**）。Fabric 分支把默认值换成 `FabricPlatform`（或让内核入口 `install(FabricPlatform)`）；这是「按分支替换」的常规动作。 |
| `api/Feature.kt` | **必须改一处** | 见 §2.2（去掉 `@SubscribeEvent` 反射）。 |
| `api/ServerLifecycleListener.kt` | **原样可用** | 参数全是原版类（`MinecraftServer`/`CommandDispatcher`/`CommandSourceStack`）。 |
| `api/events/VEvents.kt` | **原样可用** | 字段只用原版类 + 基本类型。 |
| `api/VItem.kt`（`ItemAccessor`/`VItems`） | **原样可用** | 接口只用 `ItemStack`/`Component`。 |
| `platform/neoforge/NeoForgeItemAccessor.kt` | **可直接复用（改名/搬家）** | ⚠️ 它其实**不含任何 `net.neoforged` import**（只用了 `DataComponents`/`ItemLore` 等**原版**类）。在 Fabric 1.21.1 上逐字可用，只是包名/文件名带 `NeoForge` 有误导。**建议搬到与加载器无关的位置**（如 `platform/v1211/VanillaItemAccessor.kt`）供两线共用。 |
| `api/PermissionProvider.kt` / `VoidkitServices.kt` / `VoidkitServer.kt` / `ApiRouter.kt` / `ServerEndpoint.kt` / `PlayerViews.kt` 等 | **原样可用** | 全是原版类 + 基本类型 + JDK。`VanillaPermissionProvider` 用 `player.hasPermissions(n)`，Fabric 同样成立。 |
| `event/ServerLifecycle.kt` | **原样可用** | 只用 `Platforms.current` + 原版 + `PluginRegistry`。注意其中的**时序**依赖（§5.1）。 |
| `core/core/PluginRegistry.kt` | **原样可用** | 不 import 加载器类。`attachEventHandlers()` 依赖 `Feature.listensToEvents()`，见 §2.2。 |
| `web/ApiServer.kt` | **原样可用** | 用 JDK 内置 `com.sun.net.httpserver.HttpServer`，零加载器依赖。**管理 API 移植量≈0**，只有入口类要换（§3.7）。 |
| `*/src/main/resources/META-INF/neoforge.mods.toml` | **新增 `fabric.mod.json`** | 见 §3.2。 |
| 8 个 `VoidkitXxx.kt` 入口 | **重写为 Fabric 入口** | 见 §2.3。 |
| `platform/neoforge/*` | **替换为 `platform/fabric/*`** | 见 §2.4。 |

### 2.2 `core.api` 里唯一的加载器泄漏：`Feature.kt`

`Feature.kt:6` `import net.neoforged.bus.api.SubscribeEvent`，并在 `listensToEvents()` 里用反射扫
`@SubscribeEvent`（`Feature.kt:33-37`）。**这是 `core.api` 契约层唯一一处加载器依赖，Fabric 上编译不过。**

**实测：本项目已经没有 Feature 用 `@SubscribeEvent` 了**（VEvent 抽象已落地，见 `NeoForgeEventBridge.kt`；
`grep "@SubscribeEvent"` 只剩 `platform/neoforge/` 与 `Feature.kt` 自身）。所以：

```kotlin
// 方案（推荐）：删掉反射 + 那个 import，改成显式开关
abstract class Feature {
    /** 本模块是否要进加载器原生事件总线。VEvent 抽象后恒为 false（保留了兼容位）。 */
    open fun listensToEvents(): Boolean = false
    // ... 其余不变
}
```
- `PluginRegistry.attachEventHandlers()` 无需改动（`listensToEvents()` 恒 false → 全部 skip，日志里
  `N 个模块无事件监听（不进总线）` 合理）。
- Fabric 侧 `Platform.registerListener/unregisterListener` 直接空实现即可。
- 这样 `core.api` 回到「只依赖原版类」的干净状态。

### 2.3 入口类怎么改（合并前 8 个，合并后 **5 个**）

NeoForge 入口长这样（以 `essentials` 为例，`VoidkitEssentials.kt:36-73`）：
```kotlin
@Mod(VoidkitEssentials.MODID)
class VoidkitEssentials(bus: IEventBus, container: ModContainer) {
    init {
        ...
        PluginRegistry.register(HomeFeature(), MODID)
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            EssentialsStore.load(); CommandsStore.load()
        }
    }
}
```

Fabric 等价物（`ModInitializer`，**用普通 class 而非 object**，见 §3.5 的语言适配器说明）：
```kotlin
// essentials/src/main/kotlin/org/voidkit/essentials/VoidkitEssentialsFabric.kt
class VoidkitEssentialsFabric : ModInitializer {          // 注意：class，不是 object
    override fun onInitialize() {
        VoidkitServices.installHomeLimits(EssentialsStore)
        ConfigSnapshots.register("essentials") { /* 同 NeoForge 版 */ }
        PluginRegistry.register(HomeFeature(), MODID)
        // …其余 8 个 register 原样搬

        // ServerAboutToStartEvent → ServerLifecycleEvents.SERVER_STARTING
        ServerLifecycleEvents.SERVER_STARTING.register { _ -> EssentialsStore.load(); CommandsStore.load() }
    }
}
```
映射规律：
- `@Mod(MODID) class X(bus, container)` → `class XFabric : ModInitializer { override fun onInitialize() }`
- `NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent -> ... }` → `ServerLifecycleEvents.SERVER_STARTING.register { _ -> ... }`
- `web` 额外有 `ServerStoppedEvent` → `ServerLifecycleEvents.SERVER_STOPPED.register { _ -> ... }`。
- `VoidkitCore` 里的 `ModList.get().getModContainerById(MODID).map{...}.orElse("dev")`
  → `FabricLoader.getInstance().getModContainer(MODID).map { it.metadata.version.friendlyString }.orElse("dev")`。

> 为什么要用 `class` 而不是 `object`：Fabric 默认的 `java` 语言适配器要求入口类是**可无参实例化**的类
> （`newInstance()`）。Kotlin `object` 没有公开无参构造 → 会加载失败。用 `class`（无主构造参数）即可，
> **不需要 Fabric Language Kotlin**。若要继续用 `object`，见 §3.5 的 FLK 方案。

**合并后（5 module）入口类的变化**：`perm`/`economy`/`title` 的入口类随源码并入宿主模块，
各自**不再单独存在**；由宿主入口一并登记：
```kotlin
// 合并后的 essentials 入口
class VoidkitEssentialsFabric : ModInitializer {
    override fun onInitialize() {
        VoidkitServices.installHomeLimits(EssentialsStore)
        VoidkitServices.installPermission(PermService)     // ← 原 VoidkitPerm 的构造逻辑
        VoidkitServices.installEconomy(EconomyService)     // ← 原 VoidkitEconomy 的构造逻辑
        ConfigSnapshots.register("essentials") { ... }
        ConfigSnapshots.register("perm") { ... }
        ConfigSnapshots.register("economy") { ... }
        listOf(HomeFeature(), /*…*/, PermFeature(), /*…*/, EconomyFeature() /*…*/)
            .forEach { PluginRegistry.register(it, MODID) }
        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            EssentialsStore.load(); CommandsStore.load(); PermStore.load(); EconomyStore.load()
        }
    }
    companion object { const val MODID = "voidkit_essentials" }
}
```
> ⚠️ 注意 `PluginRegistry.register(feature, owner)` 的 `owner` 会从 `voidkit_perm`/`voidkit_economy`
> 变成 `voidkit_essentials` —— 面板的「插件分组」显示随之变化（`PluginRegistry.pluginSummary()`）。
> `ConfigSnapshots` 的键（`"perm"`/`"economy"`）是**配置分区名**、不是 modId，可保留不变。
> `config/snapshot` 与 feature id 都是字面量，**合并不影响既有服务器的配置文件**（与 MIGRATION.md §四 结论一致）。

### 2.4 新增 Fabric 平台实现（3 个文件，替换 `platform/neoforge/`）

```kotlin
// core/src/main/kotlin/org/voidkit/core/platform/fabric/FabricPlatform.kt
object FabricPlatform : Platform {
    override val id = "fabric"
    override fun gameDir() = FabricLoader.getInstance().gameDir
    override fun isFakePlayer(entity: Entity): Boolean = false   // ⚠️ Fabric 无官方 FakePlayer（见 §6.2）
    override fun isUnconnectedPlayer(player: Player) = player is ServerPlayer && player.connection == null
    override fun registerListener(listener: Any) {}              // 空实现：Feature 不再用原生总线
    override fun unregisterListener(listener: Any) {}
    override fun registerLifecycle(listener: ServerLifecycleListener) { FabricLifecycleBridge(listener) }
}
```

```kotlin
// core/src/main/kotlin/org/voidkit/core/platform/fabric/FabricLifecycleBridge.kt
class FabricLifecycleBridge(private val listener: ServerLifecycleListener) {
    init {
        ServerLifecycleEvents.SERVER_STARTING.register { listener.onServerAboutToStart(it) }
        // ⚠️ 命令注册要先于 boot（见 §5.1），Fabric 用 CommandRegistrationCallback
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> listener.onRegisterCommands(dispatcher) }
        ServerLifecycleEvents.SERVER_STARTED.register { listener.onServerStarted(it) }
        ServerLifecycleEvents.SERVER_STOPPING.register { listener.onServerStopping(it) }
        ServerTickEvents.END_SERVER_TICK.register { listener.onServerTick(it) }
    }
}
```

```kotlin
// core/src/main/kotlin/org/voidkit/core/platform/fabric/FabricEventBridge.kt（VEvent 翻译，见 §3.1）
```

> `SERVER_STOPPING` ↔ NeoForge `ServerStoppingEvent`；`SERVER_STOPPED` ↔ `ServerStoppedEvent`。
> 现有 `ServerLifecycleListener` 只需要 `onServerStopping`（`ServerLifecycle.kt:114`），
> 对应 `SERVER_STOPPING`（此时落盘正合适）。

---

## 3. 差异映射表（照着写代码）

### 3.1 事件映射

| NeoForge 事件（现用） | 现用位置 | Fabric 回调 | Fabric API 模块 | 备注 |
|---|---|---|---|---|
| `ServerAboutToStartEvent` | `NeoForgeLifecycleBridge`、各入口类（现 8 个，合并后 5 个） | `ServerLifecycleEvents.SERVER_STARTING` | `fabric-lifecycle-events-v1` | 触发于 `runServer()` 调 `setupServer()` 前（Loom/Fabric 源码 `MinecraftServerMixin`），与 NeoForge `ServerAboutToStart` 语义一致。 |
| `RegisterCommandsEvent` | `NeoForgeLifecycleBridge` | `CommandRegistrationCallback.EVENT` | `fabric-command-api-v2` | 见 §3.3，回调签名 `(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)`。 |
| `ServerStartedEvent` | `NeoForgeLifecycleBridge` | `ServerLifecycleEvents.SERVER_STARTED` | `fabric-lifecycle-events-v1` | |
| `ServerStoppingEvent` | `NeoForgeLifecycleBridge` | `ServerLifecycleEvents.SERVER_STOPPING` | `fabric-lifecycle-events-v1` | 用于落盘。 |
| `ServerStoppedEvent` | `VoidkitWeb` | `ServerLifecycleEvents.SERVER_STOPPED` | `fabric-lifecycle-events-v1` | |
| `ServerTickEvent.Post` | `NeoForgeLifecycleBridge` | `ServerTickEvents.END_SERVER_TICK` | `fabric-lifecycle-events-v1` | |
| `PlayerEvent.PlayerLoggedInEvent` | `NeoForgeEventBridge` | **1.21.1**：`ServerPlayerEvents.JOIN`（→ `onJoin(ServerPlayer)`）；**1.20.1**：`ServerPlayConnectionEvents.JOIN`（→ `onPlayReady(ServerPlayNetworkHandler, PacketSender, MinecraftServer)`） | `fabric-entity-events-v1` / `fabric-networking-api-v1` | ★ **唯一实质差异**：`ServerPlayerEvents.JOIN/LEAVE` **在 1.20.1 不存在**（已 javap 确认）。要一套代码通吃两版 → 统一用 `ServerPlayConnectionEvents.JOIN`（两版都有）。玩家对象从 `handler.player` 取。 |
| `PlayerEvent.PlayerLoggedOutEvent` | `NeoForgeEventBridge` | **1.21.1**：`ServerPlayerEvents.LEAVE`；**1.20.1**：`ServerPlayConnectionEvents.DISCONNECT`（→ `onPlayDisconnect(ServerPlayNetworkHandler, MinecraftServer)`） | 同上 | 同上。 |
| `LivingDeathEvent` | `NeoForgeEventBridge` | `ServerLivingEntityEvents.AFTER_DEATH`（→ `afterDeath(LivingEntity, DamageSource)`） | `fabric-entity-events-v1` | 只取 `LivingEntity as? ServerPlayer`，与原逻辑一致。 |
| `ServerChatEvent` | `NeoForgeEventBridge` | `ServerMessageEvents.ALLOW_CHAT_MESSAGE`（返回 `false` 取消） | `fabric-message-api-v1` | 见下方「聊天」说明。 |
| `PlayerInteractEvent.RightClickItem` | `NeoForgeEventBridge` | `UseItemCallback.EVENT`（返回 `TypedActionResult<ItemStack>`） | `fabric-events-interaction-v0` | 见下方「右键」说明。 |

以上类名/签名均已**解包并 `javap` 复核，1.21.1 与 1.20.1 两版都确认**（附录 A 有命令）。
逐版实测结果见 §3.1.1，**除 `ServerPlayerEvents.JOIN/LEAVE` 外，其余事件两版完全一致。**

**聊天（重要）**：现有 `ChatFormatFeature`（`display/.../ChatFormatFeature.kt:52-105`）的做法是
**取消原版广播 + 自己 `broadcastSystemMessage` 整条**（为了不叠加原版 `<玩家名>` 装饰），
**并没有真的去改 `VChat.message`**（全仓无人读 `VChat.message`）。所以 Fabric 侧只需：
```kotlin
ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { signed, sender, _ ->
    val ve = VChat(sender, signed.signedContent(), Component.empty())
    PluginRegistry.dispatch { it.onChat(ve) }
    !ve.canceled          // false = 取消原版广播
}
```
- 好处：**绕开了 Fabric 1.21+ 聊天签名（signed chat）不能随意改写正文的限制**。
- 若将来真要「改写显示文本」（而不是取消重播），用 `ServerMessageDecoratorEvent.EVENT`
  （`fabric-message-api-v1`，`CONTENT_PHASE`/`STYLING_PHASE`）——已确认存在。
- ✅ 1.20.1 已复核：`ServerMessageEvents.ALLOW_CHAT_MESSAGE` 签名与 1.21.1 **逐字一致**
  ——`boolean allowChatMessage(SignedMessage, ServerPlayer, MessageType.Parameters)`
  （两版 intermediary 参数 id 都是 `class_7471, class_3222, class_2556$class_7602`）；
  `CHAT_MESSAGE`、`ServerMessageDecoratorEvent` 也都在。**无需为 1.20.1 改写这段。**

**右键**：`UseItemCallback.EVENT` 返回 `TypedActionResult<ItemStack>`，不是布尔。
`VRightClickItem.canceled` 的桥接方式：
```kotlin
UseItemCallback.EVENT.register { player, level, hand ->
    val p = player as? ServerPlayer ?: return@register TypedActionResult.pass(ItemStack.EMPTY)
    val ve = VRightClickItem(p, p.getItemInHand(hand), hand)
    PluginRegistry.dispatch { it.onRightClickItem(ve) }
    if (ve.canceled) TypedActionResult.fail(p.getItemInHand(hand))
    else TypedActionResult.pass(p.getItemInHand(hand))
}
```
⚠️ `UseItemCallback` 是**双端**事件——已反编译确认：`fabric-events-interaction-v0` 的
`ServerPlayerInteractionManagerMixin#interactItem(...)`（服务端）与
`ClientPlayerInteractionManagerMixin`（客户端）**都引用 `UseItemCallback`**
（1.20.1 与 1.21.1 皆是）。但上面的写法天然安全：客户端的 `player` 是 `LocalPlayer`，
`as? ServerPlayer` 直接返回 null → 走 `pass`。**专用的服务端环境（`environment:"server"`）里客户端 mixin 根本不加载**，
所以线上只会触发一次。
- ✅ **两版签名一致**：`TypedActionResult<ItemStack> interact(Player, Level, InteractionHand)`。
- ⚠️ 仍待**行为实测**的一点：NeoForge `PlayerInteractEvent.RightClickItem` 与 Fabric
  `UseItemCallback` 在「取消后物品是否被消耗/进入使用态」上细节可能不同（现有唯一用途是钟表菜单
  `MenuFeature`）。若不理想，退路是放弃「阻止物品使用」语义，改为「右键后主动关物品栏」（§6.3）。

### 3.1.1 逐版实测：1.20.1 vs 1.21.1（解包 `javap` 复核，非推断）

数据来源：`fabric-api-0.116.17+1.21.1.jar` 与 `fabric-api-0.92.12+1.20.1.jar`（均下自 `maven.fabricmc.net`，
已解包各模块 jar 后 `javap`；命令见附录 A）。

| 类 / 字段 | 1.21.1 | 1.20.1 | 差异 |
|---|---|---|---|
| `ServerLifecycleEvents.SERVER_STARTING` | `onServerStarting(MinecraftServer)` | 同 | **无** |
| `ServerLifecycleEvents.SERVER_STARTED / SERVER_STOPPING / SERVER_STOPPED` | `onServerStarted/Stopping/Stopped(MinecraftServer)` | 同 | **无** |
| `ServerTickEvents.END_SERVER_TICK` | `onEndTick(MinecraftServer)` | 同 | **无** |
| `CommandRegistrationCallback.EVENT` | `register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` | 同（intermediary `class_2168, class_7157, class_2170$class_5364`） | **无** |
| **`ServerPlayerEvents.JOIN` / `.LEAVE`** | **存在**（`onJoin/onLeave(ServerPlayer)`） | **不存在** | ★ **关键差异** |
| `ServerPlayerEvents.AFTER_RESPAWN / COPY_FROM / ALLOW_DEATH` | 存在 | 存在 | **无** |
| `ServerPlayConnectionEvents.JOIN` | `onPlayReady(ServerPlayNetworkHandler, PacketSender, MinecraftServer)` | 同 | **无** |
| `ServerPlayConnectionEvents.DISCONNECT` | `onPlayDisconnect(ServerPlayNetworkHandler, MinecraftServer)` | 同 | **无** |
| `ServerLivingEntityEvents.AFTER_DEATH` | `afterDeath(LivingEntity, DamageSource)` | 同 | **无**（1.20.1 无 `AFTER_DAMAGE`，本套件不用） |
| `ServerMessageEvents.ALLOW_CHAT_MESSAGE` | `boolean allowChatMessage(SignedMessage, ServerPlayer, MessageType.Parameters)` | **逐字相同** | **无** |
| `ServerMessageEvents.CHAT_MESSAGE` | `onChatMessage(SignedMessage, ServerPlayer, MessageType.Parameters)` | 同 | **无** |
| `ServerMessageDecoratorEvent.EVENT` | 存在 | 存在 | **无** |
| `UseItemCallback.EVENT` | `TypedActionResult<ItemStack> interact(Player, Level, InteractionHand)`，双端 | 同 | **无** |
| `FakePlayer` | **不存在** | **不存在** | 两版都没有（§6.2） |

**⇒ 结论：本套件用到的全部事件里，1.20.1 与 1.21.1 只差 `ServerPlayerEvents.JOIN/LEAVE` 一项。**
因此 §2.4 的 `FabricEventBridge` **只需一份实现**：玩家上下线统一走
`ServerPlayConnectionEvents.JOIN/DISCONNECT`（两版都有），无需按版本分叉。

### 3.2 注册清单：`mods.toml` → `fabric.mod.json`

现 NeoForge 清单（`core/.../META-INF/neoforge.mods.toml`）：
```toml
modLoader = "javafml"
loaderVersion = "${loader_range}"
license = "MIT"
issueTrackerURL = "..."
[[mods]]
modId = "voidkit_core"
version = "${mod_version}"
displayName = "VoidkitCore"
authors = "Kokuu Studio"
logoFile = "icon.png"
description = '''...'''
[[dependencies.voidkit_core]]
modId = "neoforge"  / "minecraft" / "voidkit_core"
type = "required"
versionRange = "${neo_range}" / "${mc_range}"
ordering = "NONE" / "AFTER"
side = "BOTH" / "SERVER"
```

Fabric 对应（`core/src/main/resources/fabric.mod.json`，占位符仍由 `processResources` 展开）：
```json
{
  "schemaVersion": 1,
  "id": "voidkit_core",
  "version": "${mod_version}",
  "name": "VoidkitCore",
  "authors": ["Kokuu Studio"],
  "contact": { "issues": "https://github.com/KokuuWork/voidkit/issues" },
  "license": "MIT",
  "icon": "icon.png",
  "environment": "server",
  "entrypoints": { "main": ["org.voidkit.core.VoidkitCoreFabric"] },
  "depends": {
    "fabricloader": ">=0.15.11",
    "fabric-api": "*",
    "minecraft": "${mc_range}",
    "java": ">=21"
  }
}
```

插件清单（以 `essentials` 为例）额外加 `"voidkit_core": ">=${mod_version}"`：
```json
"depends": {
  "fabricloader": ">=0.15.11",
  "fabric-api": "*",
  "minecraft": "${mc_range}",
  "voidkit_core": ">=${mod_version}"
}
```

**合并后（5 module）的清单数量**：因为走的是「真源码合并、一 jar 一 modId」，
合并**不会**产生多 modId 单 jar 的问题，而是**每个 module 一份 `fabric.mod.json`**，
共 **5 份**（`core` / `essentials` / `display` / `utility` / `web`）：

```
core/fabric.mod.json       id = voidkit_core       entrypoints.main = [org.voidkit.core.VoidkitCoreFabric]
essentials/fabric.mod.json id = voidkit_essentials entrypoints.main = [org.voidkit.essentials.VoidkitEssentialsFabric]
display/fabric.mod.json    id = voidkit_display    entrypoints.main = [org.voidkit.display.VoidkitDisplayFabric]
utility/fabric.mod.json    id = voidkit_utility    entrypoints.main = [org.voidkit.utility.VoidkitUtilityFabric]
web/fabric.mod.json        id = voidkit_web        entrypoints.main = [org.voidkit.web.VoidkitWebFabric]
```
- **一个 module 一个 `main` entrypoint**（合并进来的 `perm`/`economy`/`title` 不再有自己的 entrypoint 文件，
  由宿主入口统一登记 —— 见 §2.3）。
- 依赖声明：除 `core` 外，其余 4 份都 `depends.voidkit_core`。**不再出现**
  `voidkit_perm` / `voidkit_economy` / `voidkit_title` 这三个 id（它们随源码合并而消失）。
- `jars`（Kotlin stdlib）**仍然只有 `core` 那一份有**（§3.5）。
- ⚠️ 若外部有 `[[dependencies.voidkit_perm]]` 之类的引用，随合一一并改成 `voidkit_essentials`
  （与 MIGRATION.md §五「方案 B」的代价一致：`voidkit_perm` 等 modId 会从 Mod List 消失）。

字段对照：

| `mods.toml` | `fabric.mod.json` | 说明 |
|---|---|---|
| `modLoader="javafml"`, `loaderVersion` | — | Fabric 无此概念（Loader 自己就是） |
| `[[mods]].modId` | `id` | **一个文件只能一个 `id`**（见 §6.1） |
| `version` | `version` | 占位符 `"${mod_version}"` 照旧 |
| `displayName` | `name` | |
| `authors` | `authors`（数组） | |
| `logoFile="icon.png"` | `icon` | |
| `description` | `description` | |
| `issueTrackerURL` | `contact.issues` | |
| `[[dependencies.X]].modId="neoforge"` | — | 删除（无对等项） |
| `modId="minecraft"` + `versionRange` | `depends.minecraft` | Fabric 用 Maven 语义 = NeoForge 的 `versionRange` 类似格式 |
| `modId="voidkit_core"` `side="SERVER"` | `depends.voidkit_core` + 顶层 `environment:"server"` | Fabric **无 per-dependency side**，只有 mod 级 `environment` |
| （无） | `entrypoints.main` | Fabric 独有，指向 `ModInitializer` |
| （无） | `jars` | 嵌套 jar（Kotlin 运行时，Loom `include` 自动注入，§3.5） |

`environment` 取值：`"*"`（双端）/`"client"`/`"server"`。本套件全用 **`"server"`**。

### 3.3 命令注册

| | NeoForge | Fabric |
|---|---|---|
| 事件类 | `RegisterCommandsEvent` | `CommandRegistrationCallback`（`fabric-command-api-v2`） |
| 拿 dispatcher | `event.dispatcher` | 回调参数 1 |
| 签名 | `RegisterCommandsEvent(dispatcher, buildContext, selection)` | `register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` |
| 监听方式 | `@SubscribeEvent fun onRegisterCommands(e: RegisterCommandsEvent)` | `CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> ... }` |

`ServerLifecycleListener.onRegisterCommands(dispatcher)` 与 `PluginRegistry.registerCommands(dispatcher)`
**完全不用改**（都只吃 `CommandDispatcher`）。唯一要处理的是**时序**（§5.1）。

### 3.4 权限

**结论：Fabric 上权限层零改动。** 依据：
- 本套件自带 `voidkit_perm`，**不依赖任何加载器的权限 API**。
- 兜底实现 `VanillaPermissionProvider` 只用 `ServerPlayer.hasPermissions(int)` 与 `CommandSourceStack`
  —— 纯原版，Fabric 完全一致（Fabric 同样以原版 OP level 为准）。
- 抽象层 `PermissionProvider` 有意只暴露基本类型（`Boolean`/`String`/`Int`），无加载器耦合。

对比：Forge/NeoForge 有 `PermissionAPI`/`PermissionGatherEvent`；Fabric **没有**等价物
（社区用 `fabric-permissions-api` 或 Mecha 之类）。本套件**不需要它**。

> 可选的未来增强：若想让 `voidkit_perm` 与 Fabric 上的 LuckPerms / fabric-permissions-api 互通，
> 再单独写一个 `PermissionProvider` 适配实现接入 `VoidkitServices.installPermission(...)` 即可——
> 这是插件级增量，不影响移植主线。**当前不做。**

### 3.5 依赖打包（Kotlin 运行时）

NeoForge 线：只有 `core` 用 `jarJar` 内嵌 `kotlin-stdlib`（`build.gradle:88-103`），其余插件从 core 拿。

Fabric 线用 **Loom 的 `include`（jar-in-jar）**，语义对称：
```groovy
// 只有 core 模块
include "org.jetbrains.kotlin:kotlin-stdlib:${rootProject.ext.kotlinVersion}"
```
Loom 行为（官方文档）：
- 把该依赖作为**嵌套 jar**塞进最终 `remapJar` 的 `META-INF/jars/`；
- 并把它写进产物 `fabric.mod.json` 的 `jars` 数组（Loom 在 remap 阶段注入，**源文件里不用手写**；
  ⚠️ 首次构建后要 `unzip` 产物核对一眼）；
- 对于**非 mod 依赖**（kotlin-stdlib 就是），Loom 会生成一个带 `fabric.mod.json` 的包装 mod，
  id 用依赖名、版本用依赖版本（文档原话："Loom will generate a mod jar with a `fabric.mod.json`
  using the mod ID for the name, and the same version"）。
- ⚠️ `include` **不是传递的**，所以只有 core 声明它，插件不声明（防止 8 份 stdlib）。

**Fabric Loader 的嵌套 jar 是全局可见的**（Knot 单一 classloader），所以 core 带的 stdlib
对所有插件可见 —— 与 NeoForge 线 jarJar 的效果一致。

**开发态**：Loom 的 `runServer` 直接用工程 runtime classpath，`implementation` 里的 kotlin-stdlib
天然在场，**不像 MDG 那样需要额外的 `additionalRuntimeClasspath` 补丁**（本仓 `build.gradle:111-115`
那段 MDG 专用 hack 在 Fabric 分支删掉）。保留 `implementation "org.jetbrains.kotlin:kotlin-stdlib:..."` 即可。

**Kotlin 版本要求**：
- 本仓用 Kotlin **2.1.0**；**Loom 1.9+ 最低支持 Kotlin 2.0，2.1 明确支持**（Loom 1.9 release notes:
  "Support Kotlin 2.1+ by using the kotlin-metadata library ... Minimum supported Kotlin version is now 2.0"）。
  ⇒ Kotlin 2.1.0 + Loom 1.10.5 兼容。
- `kotlin-stdlib` 版本仍是 2.1.0；`-Xjvm-default=all`、`jvmTarget` 保持。

**入口类的语言适配器**（重复强调，这是 Fabric 常见坑）：
- **不装 FLK**：入口类写成普通 `class`（可无参实例化），用默认 `java` 适配器 —— **推荐**。
- **装 Fabric Language Kotlin（FLK）**：才能在 `fabric.mod.json` 里写
  `"value": "org.voidkit.core.VoidkitCore::onInitialize"` 用 `object`。
  FLK 自带 kotlin-stdlib → **会和 core 里 `include` 的 stdlib 撞版本**，且多一个硬依赖，
  版本还得跟 Kotlin 2.1.0 对齐（FLK 形如 `1.13.x+kotlin.2.1.0`）。**不推荐**，除非确实需要 FLK 的其他特性。

### 3.6 纯服务端 / 客户端连接

- 清单：`"environment": "server"` → 玩家客户端**不加载**这些 mod，也不会因缺失而报错。
- 网络：本套件**不发任何自定义网络包**（已核对：无 `CustomPayload`/`registerPayload`/自定义 channel；
  MOTD/TAB/记分牌/聊天/菜单全部走**原版包**）。⇒ **原版客户端可直接连服务器**，
  与 NeoForge 线「客户端不需要」的承诺一致。
- 「不影响客户端连接」的另一面：Fabric 不注册自定义注册表内容（方块/物品），所以不会有
  「客户端缺 mod」的握手失败。✅

### 3.7 管理 API（`web` 插件）

- HTTP 服务用 JDK 内置 `com.sun.net.httpserver.HttpServer`（`web/.../ApiServer.kt:4-5`），
  **零加载器依赖**，Fabric 上逐字可用。
- 切主线程用 `server.execute` / `server.isSameThread`（`VoidkitServer.kt`），纯原版。
- 唯一改动：`VoidkitWeb.kt` 入口（`@Mod` + 两个 `NeoForge.EVENT_BUS.addListener`）→
  `class VoidkitWebFabric : ModInitializer` + `SERVER_STARTING`/`SERVER_STOPPED`。
- **改动量评估：≈1 个入口类，约 20 行。** 其余（`ApiRouter`/`ApiDefaults`/`WebConfig`/`ApiServer`）不动。

---

## 4. 施工顺序与验收

> 原则：**先能启动 → 再逐个 feature 恢复**。每阶段有「可执行命令 + 期望输出」。

### 阶段 0：构建底座（不改业务代码）
**做什么**
1. 根 `build.gradle`：`plugins` 里把 `net.neoforged.moddev` 换成 `fabric-loom`（1.10.5，`apply false`）；
   `subprojects` 里把 `apply plugin: 'net.neoforged.moddev'` 换成 Loom，删掉 `neoForge { }`，
   加 `minecraft`/`mappings`/`modImplementation fabric-api`/`include kotlin-stdlib`（见 §7 草图）。
2. `settings.gradle`：`pluginManagement.repositories` 加 `maven { url = 'https://maven.fabricmc.net/' }`。
3. `gradle.properties`：加 `mcVersion`→loader/Fabric API 版本对照（沿用现有 `-PmcVersion` 机制）。
4. 版本线表：在现有 `lines` 里加入 `1.20.1`（Fabric 线**不需要** Forge 的 JDK17-跑-Gradle 约束；
   但为与 `build.sh` 一致，1.20.1 仍走 JDK 17、1.21.1 走 JDK 21）。

**验收**：`./gradlew tasks` 能列出 `runServer`/`remapJar`/`genSources`（Loom 任务出现即插件生效）；
`./gradlew :core:dependencies --configuration compileClasspath` 里能看到 `net.minecraft` + `fabric-loader`。

### 阶段 1：core 能被 Fabric 加载（**关键里程碑**）
**做什么**：§2.2 改 `Feature.kt`；§2.4 写 `platform/fabric/*`；§2.3 写 `VoidkitCoreFabric`；
`Platforms.current` 默认改 `FabricPlatform`；`core/resources/fabric.mod.json`。

**验收**
```bash
./gradlew :core:runServer            # 起开发服务端
# 期望日志（顺序错的话此处就会暴露时序问题，见 §5.1）：
#   VoidkitCore 1.0.0 已装载，配置将在服务端启动阶段读取
#   内核配置就绪：.../voidkit/core/config.json
#   数据根目录: .../voidkit
#   [CMD] 注册命令，active 模块 = [player-tracking, core]     ← 只有 core 自带 2 个
#   Done (...)! For help
# 且 .../voidkit/core/config.json 被生成
```
> 这是**机械替换**占绝大多数的一步；唯二「要动脑」的是 `Feature.listensToEvents` 与命令/启动时序。

### 阶段 2：VEvent 桥 + 23 个 feature 全量注册
**做什么**：写 `FabricEventBridge`（§3.1 五个 VEvent）；各模块入口换成 `*Fabric`；
各模块加 `fabric.mod.json`（合并后共 5 份）。

**验收**：把 4 个插件模块的 remapJar 产物丢进 `core/run/mods/`（**别放 `voidkit_core`**）再 `runServer`：
```
[CMD] 注册命令，active 模块 = [ ... ]     ← 23 个 feature 一个不少（对照 ARCHITECTURE/ROADMAP）
事件总线：挂载 0，摘除 0，N 个模块无事件监听（不进总线）
没有 [CMD] /xxx 注册失败 / FATAL / mod 依赖解析失败
```
**这一步是「机械替换」**（入口类逐个换），风险集中在 VEvent 桥的语义对齐。

### 阶段 3：逐个 feature 行为恢复（真实交互）
按「有事件的先做」：`player-tracking`（join/quit）→ `scoreboard`（join）→ `back`（death）→
`tpa`/`menu`（quit）→ `chat-format`（chat）。**必须真人/脚本进服验证**（`tools/smoke-test.sh` 的思路可复用，
它走 RCON，与加载器无关）。

**验收**：进服后 `/voidkit status`、`/home`、`/warp`、`/tpa`、记分牌、聊天格式、菜单右键逐项可用；
`~/.voidkit/voidkit/` 下配置与数据落盘正确。

### 阶段 4：管理 API 与打包
**做什么**：`VoidkitWebFabric`；`bundleZip` 从 `jar` 改到 `remapJar`；jar 命名保持
`voidkit_<mod>-<ver>+mc<mc>-fabric.jar`。

**验收**
```bash
./gradlew build -PmcVersion=1.21.1     # 产出 build/libs/*-fabric.jar（remap 产物）+ *-dev.jar
./gradlew build -PmcVersion=1.20.1     # 同 vk1.20.1（JDK17）
./gradlew bundleZip                    # zip 内 N 个 jar，无 *-dev.jar
# 起服后：curl http://127.0.0.1:8765/api/... 管理接口可用
```

### 阶段划分总表

| 阶段 | 内容 | 性质 |
|---|---|---|
| 0 | Loom 构建底座 | 机械替换 |
| 1 | core 起得来 | 机械替换 + 2 处判断 |
| 2 | 23 feature 全注册 | 机械替换（入口类） + 桥语义对齐 |
| 3 | 行为恢复 | **需实测**（右键取消行为、FakePlayer 判定） |
| 4 | 管理 API + 打包 | 机械替换（Loom 侧用 remapJar） |

---

## 5. 需要注意的时序与细节

### 5.1 命令注册 vs 启动事件的先后（**务必实测**）

NeoForge 1.21.1 的实测结论（`PluginRegistry.kt:148-157` 注释）：**`RegisterCommandsEvent` 先于
`ServerAboutToStartEvent` 触发**，所以内核用 `pendingCommands` 暂存 dispatcher，等 `boot()` 填好
`active` 后再补注册。

Fabric 的对应事实（源码实锤）：
- `SERVER_STARTING` 在 **`MinecraftServer.runServer()`** 里、调 `setupServer()` **之前**触发
  （`fabric-lifecycle-events-v1` 的 `MinecraftServerMixin#beforeSetupServer`，已读源码）。
- `CommandRegistrationCallback` 在 **`CommandManager` 构造函数**里触发
  （`fabric-command-api-v2` 的 `CommandManagerMixin`，`@Inject(method="<init>")`，已读源码）。
- `CommandManager` 在 `MinecraftServer` 构造期创建（先于 `runServer`）—— 否则 NeoForge 那边的
  实测「命令先于 about-to-start」不可能成立（两边 patch 的是同一个 `CommandManager.<init>` 与同一段 `runServer`）。

**⇒ 结论：Fabric 上同样是「命令注册先于 SERVER_STARTING」。现有 `pendingCommands` 机制必须保留，
且它是加载器无关的、在 Fabric 上照常工作。** ⚠️ 但这是**推断 + 源码推理**，**阶段 1 验收时用日志
实测一次**（看 `[CMD] 收到 RegisterCommandsEvent，active 模块数 = 0` 是否先出现），再决定要不要动。

### 5.2 插件级配置加载的回调顺序

每个插件入口用 `SERVER_STARTING` 加载自己的配置（`EssentialsStore.load()` 等），而内核
`PluginRegistry.boot()`（决定 feature 开关）也挂在 `SERVER_STARTING`。Fabric 按**注册顺序**派发，
而 core 是插件依赖、**core 的 `ModInitializer` 先执行** → core 的 `boot` 回调可能先于插件配置加载。

影响面很小：`applyConfig` 只在 core 配置里**没有** `features[id]` 时才回退到 `feature.defaultEnabled`
（如 `RemoteApiFeature.defaultEnabled` 读 `WebStore.config.enabled`）。首次生成配置后就不再走回退分支。
- 若实测发现默认开关不对：用 Fabric 的**事件相位**（`Event.addPhase` / `register(phase, cb)`）
  把 core 的 `boot` 放到一个 late 相位，让插件配置加载先跑。**不预先复杂化，实测后再说。**

### 5.3 运行目录

Loom 的 run 目录默认是 `${projectDir}/run`（即 `core/run`、`essentials/run`…）。
本仓现有约定是 `core/run`（`build.sh`、`smoke-test.sh` 都在用）。**建议**在根 `build.gradle` 里
对每个 subproject 设 `loom { runs { server { runDir = "run" } } }`，或统一用根 `run/`，
并同步改 `smoke-test.sh` 里的路径与 `core/run/mods` 约定（避免在合并后的 5 个 module 下路径错乱）。

---

## 6. 风险清单（可能做不成 / 要降级 / 前置未知）

### 6.1 ✅ 合并形态（已定）：真源码合并、一 jar 一 modId

**背景事实**：**Fabric Loader 的 `fabric.mod.json` 一个文件只能声明一个 `id`**；一个 jar = 一个 mod
（额外的 mod 只能以**嵌套 jar** 形式存在）。而 `MIGRATION.md` 原本的 5-jar 方案是「保留各自 modId，
一个 jar 内放多个 `[[mods]]`」—— 这是 **NeoForge 特性，Fabric 不支持**。

**已定方案（team-lead 决策）**：走 **「真源码合并、一 jar 一 modId」**，两边（NeoForge / Fabric）都成立：

| 合并后 module | 吸收的源码 | modId | fabric.mod.json |
|---|---|---|---|
| `core` | — | `voidkit_core` | 1 份 |
| `essentials` | `perm` + `economy` | `voidkit_essentials` | 1 份 |
| `display` | `title` | `voidkit_display` | 1 份 |
| `utility` | — | `voidkit_utility` | 1 份 |
| `web` | — | `voidkit_web` | 1 份 |

⇒ **5 个 module / 5 个 modId / 5 份 `fabric.mod.json`。不存在嵌套 jar bundle 方案。**

对 Fabric 移植的影响：
- **不再是多 modId 单 jar**，「bundle jar（嵌套 jar 打包）」等备选方案**不再适用/已废弃**。
- 各模块 `fabric.mod.json` 只需一份 `entrypoints.main`（合并进来的 `perm`/`economy`/`title`
  **不再有自己的入口类**，由宿主入口统一登记 —— 见 §2.3、§3.2）。
- `voidkit_perm` / `voidkit_economy` / `voidkit_title` 三个 modId **从 Mod List 消失**；
  若有外部 `[[dependencies.voidkit_perm]]` 引用，需一并改成 `voidkit_essentials`
  （与 MIGRATION.md §五「方案 B」代价一致）。
- feature id、配置目录、配置键都是**字面量、与 modId 无关** ⇒ 既有服务器配置文件**不受影响**
  （`PluginRegistry.pluginSummary()` 的面板分组名会变，属预期）。
- **对移植工作的净影响：减少入口类数量（8→5），不增加任何 Fabric 侧的特殊处理。**

### 6.2 🟡 FakePlayer 判定在 Fabric 上没有官方类

- NeoForge 有 `net.neoforged.neoforge.common.util.FakePlayer`；**Fabric 无官方等价类**
  ——已解包确认 **`fabric-api-0.116.17+1.21.1.jar` 与 `0.92.12+1.20.1` 两版都没有 `FakePlayer`**。
- 现有 core 内**无人调用** `Platform.isFakePlayer`（调用方在独立的 `voidkit-inspector`/`guard`），
  所以本套件移植可先返回 `false`。
- 真正受影响的是 inspector（独立仓）——**它的 Fabric 移植要自己解决「科技整合包假玩家」
  判定（按类名/按未连接 ServerPlayer/按线程等启发式），这是它自己的风险，不在本套件范围。**

### 6.3 🟡 右键物品取消语义（`UseItemCallback`）需实测

- NeoForge `PlayerInteractEvent.RightClickItem` 是**服务端、可取消**；Fabric `UseItemCallback.EVENT`
  是**双端**、返回 `TypedActionResult`。
- 现有唯一用途：`MenuFeature`（钟表右键开菜单，`e.canceled = true` 阻止物品被使用）。
- 风险：`UseItemCallback` 在服务端的触发时机/返回值处理与 NeoForge 不完全一致。
  ⚠️ 阶段 3 实测；不理想时的降级：不取消物品使用（仅叠加菜单），或改用原版「使用物品」结果判断。

### 6.4 ✅ 1.20.1 的 API 形态已复核（原「🟡 需复核」已消）

- **已完成**：解包 `fabric-api-0.92.12+1.20.1.jar`，`javap` 逐类比对，结果见 §3.1.1。
- **结论**：本套件用到的全部事件里，1.20.1 与 1.21.1 **只差一项**——
  `ServerPlayerEvents.JOIN` / `.LEAVE` 在 **1.20.1 不存在**（1.20.1 的 `ServerPlayerEvents`
  只有 `COPY_FROM`/`AFTER_RESPAWN`/`ALLOW_DEATH`）。
  → 玩家上下线统一改用两版都有的 `ServerPlayConnectionEvents.JOIN/DISCONNECT` 即可，**一份代码通吃**。
- 其余（`ServerLifecycleEvents`、`ServerTickEvents`、`CommandRegistrationCallback`、
  `ServerLivingEntityEvents.AFTER_DEATH`、`ServerMessageEvents.*`、`ServerMessageDecoratorEvent`、
  `UseItemCallback`）两版**逐字一致**。`FakePlayer` 两版都无（§6.2）。
- **仍未确认**（与 1.20.1 无关，属运行时/MC 版本问题）：
  - 1.20.1 的 ItemStack 走 **NBT**，`NeoForgeItemAccessor` 的 DataComponent 版**编不过**，
    要按 `VItem.kt` 的既定设计补一份 NBT 实现 —— 这是**跨 MC 版本**问题（与 `mc/1.20.1` 分支共享），
    不是 Fabric 问题。⚠️ 此点仍待处理。

### 6.5 🟡 命令/启动时序（§5.1）与插件配置顺序（§5.2）

均为**推断/源码推理**得出的结论，需阶段 1 用日志实测确认；错了也不难修（事件相位或调整注册时机）。

### 6.6 🟢 已排除的风险（有实锤，不用再担心）

- **网络**：`maven.fabricmc.net` / Mojang 仓库直连可用（已 curl）。
- **Loom 版本**：1.10.5 与 Gradle 8.12.1 匹配（Loom release notes）。
- **映射**：用官方名 → 业务代码零改名（§1.4）。
- **权限层**：零改动（§3.4）。
- **管理 API**：零改动（§3.7）。
- **Kotlin**：Loom 1.10 支持 Kotlin 2.1（Loom 1.9 release notes）。
- **1.20.1 vs 1.21.1 事件 API**：已解包 `javap` 双版比对，除 `ServerPlayerEvents.JOIN/LEAVE` 外逐字一致（§3.1.1）。

---

## 7. 附录：`build.gradle` 改造草图（Fabric 分支）

```groovy
plugins {
    id 'fabric-loom' version '1.10.5' apply false
    id 'org.jetbrains.kotlin.jvm' version '2.1.0' apply false
}

ext {
    voidkitVersion = '1.0.0'
    // ...
    loader = 'fabric'                       // 参与 jar 命名
    mcVersion = (findProperty('mcVersion') ?: '1.21.1').toString()
    def lines = [
        '1.21.1': [mappings: 'official', loader: '0.19.5', fapi: '0.116.17+1.21.1',
                   mcRange: '[1.21.1,1.22)', java: 21],
        '1.20.1': [mappings: 'official', loader: '0.19.5', fapi: '0.92.12+1.20.1',
                   mcRange: '[1.20.1,1.21)', java: 17],
    ]
    def line = lines[mcVersion]
    if (line == null) throw new GradleException("未知版本线 mcVersion=${mcVersion}")
    mcRange  = line.mcRange
    javaToolchain = line.java as Integer
    fabricLoaderVersion = line.loader
    fabricApiVersion = line.fapi
    kotlinVersion = '2.1.0'
}

subprojects { p ->
    apply plugin: 'fabric-loom'
    apply plugin: 'org.jetbrains.kotlin.jvm'

    group = rootProject.ext.voidkitGroup
    version = rootProject.ext.voidkitVersion
    def modId = "voidkit_${p.name}"
    ext.modId = modId
    base { archivesName = modId }        // Loom 会把 jar 加 -dev，remapJar 用这个名

    java.toolchain.languageVersion = JavaLanguageVersion.of(rootProject.ext.javaToolchain)

    repositories {
        maven { url = 'https://maven.aliyun.com/repository/public' }
        mavenCentral()
    }

    dependencies {
        minecraft "com.mojang:minecraft:${rootProject.ext.mcVersion}"
        mappings loom.officialMojangMappings()        // ★ 关键：官方名，业务代码零改名
        modImplementation "net.fabricmc:fabric-loader:${rootProject.ext.fabricLoaderVersion}"
        modImplementation "net.fabricmc.fabric-api:fabric-api:${rootProject.ext.fabricApiVersion}"

        implementation "org.jetbrains.kotlin:kotlin-stdlib:${rootProject.ext.kotlinVersion}"
        testImplementation "org.jetbrains.kotlin:kotlin-test-junit5:${rootProject.ext.kotlinVersion}"

        // 只有 core 内嵌 Kotlin 运行时（对称于 NeoForge 线的 jarJar）
        if (p.name == 'core') {
            include "org.jetbrains.kotlin:kotlin-stdlib:${rootProject.ext.kotlinVersion}"
        }
    }

    loom {
        runs { server { runDir = 'run' } }             // 统一运行目录
    }

    tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        compilerOptions {
            jvmTarget.set(rootProject.ext.javaToolchain == 17
                ? org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                : org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add('-Xjvm-default=all')
        }
    }

    // fabric.mod.json 占位符展开（对称于原 processResources）
    processResources {
        def props = [mod_version: p.version, mc_range: rootProject.ext.mcRange]
        inputs.properties props
        filesMatching('fabric.mod.json') { expand props }
    }
    // 交付物命名：remapJar 已是 voidkit_xxx-<ver>.jar，改成 +mc<mcv>-fabric.jar
    tasks.named('remapJar') {
        archiveFileName = "${modId}-${p.version}+mc${rootProject.ext.mcVersion}-fabric.jar"
    }
}

// bundleZip 改从 remapJar 取产物
tasks.register('bundleZip', Zip) {
    dependsOn subprojects.collect { it.tasks.named('remapJar') }
    archiveFileName = "voidkit-${voidkitVersion}+mc${mcVersion}-fabric.zip"
    destinationDirectory = layout.buildDirectory.dir('dist')
    subprojects.each { p -> from(p.tasks.named('remapJar').flatMap { it.archiveFile }) }
    eachFile { it.path = it.name }
    includeEmptyDirs = false
}
```
> `settings.gradle` 的 `pluginManagement` 需加 `maven { url = 'https://maven.fabricmc.net/' }`。
> `mirror.init.gradle` 对 Fabric 无作用（它只重写 `maven.neoforged.net`），保留无害。

---

## 8. 实施记录与实测踩坑（2026-10-13）

分支 `feat/fabric`，阶段 0 / 1 / 2 已完成，且都经过**真启动验证**（不是只编译）。

### 8.1 完成清单

- **阶段 0**：根 `build.gradle` 换成 Loom 1.10.5（Gradle 8.12.1 能用的最高版本）；
  `settings.gradle` 加 `maven.fabricmc.net`；交付物从 `jar` 改到 **`remapJar`**，`bundleZip` 同步改。
- **阶段 1**：新增 `platform/fabric/`（`FabricPlatform` / `FabricLifecycleBridge` /
  `FabricEventBridge` / `FabricItemAccessor`）；`VoidkitCore` 从 `@Mod class` 改为 `object`
  + 薄入口 `VoidkitCoreFabric`；`Platform` 契约新增 `modVersion(modId)`（替代直接调 `ModList`）。
- **阶段 2**：4 个插件入口换成 `*Fabric : ModInitializer`；5 份 `fabric.mod.json`；
  删掉 `platform/neoforge/` 与 5 份 `neoforge.mods.toml`。
- **阶段 4（部分）**：`./build.sh bundleZip` → `voidkit-1.0.0+mc1.21.1-fabric.zip`（5 个 remap jar）。

### 8.2 四个「施工单没预料到」的坑

**① `fabric.mod.json` 的 `${version}` 不会被 Loom 自动展开。**
那段 `filesMatching('fabric.mod.json') { expand ... }` 是 `fabric-example-mod` **模板手写的**，
不是插件内置行为。不补的话 Loader 读到的 mod 版本是字面量 `${version}`，而报出来的错误是
「Minecraft 版本不对」—— **方向完全被带偏**。已在根 `build.gradle` 补上。

**② Fabric 不认 Maven 区间语法。**
`"minecraft": "[1.21.1,1.22)"`（NeoForge 线的写法）被判为**不匹配**；要写 `">=1.21.1 <1.22"`。

**③ 官方映射下有两个 API 名的写法与「网上教程」不同。**
- `TypedActionResult` → 官方名是 **`InteractionResultHolder`**（`TypedActionResult` 是 Yarn 名，
  而 Fabric 教程几乎都用 Yarn，很容易照抄错）。
- `ItemStack.isComponentsPatchEmpty` 是 **NeoForge 打的补丁方法**，原版没有；
  Fabric 下等价写法是 `stack.componentsPatch.isEmpty`。

**④ 🚨 最重要：Fabric 的 entrypoint 顺序不保证，插件不能靠监听加载器事件读配置。**
- 施工单原文验证了「命令注册先于 SERVER_STARTING」（这点**实测成立**，`pendingCommands` 无需改），
  但**漏了另一半**：插件自己的 `SERVER_STARTING` 回调与**内核的**回调之间同样没有顺序保证。
- 实测：四个插件的 entrypoint 跑在内核**前面** → 插件回调里 `ConfigFiles.resolve` 抛
  `IllegalStateException: ConfigFiles 尚未初始化（rootDir 未设置）`，服务端起不来。
  （日志证据：`已注册 23 个内置变量` 出现在 `VoidkitCore 已装载` **之前** —— 阶段 1 时是 21 个，
  多出来的 2 个正是 display/essentials 先跑注册进去的 `%title%` / `%balance%`。）
- 这**不是 Fabric 的 bug**，是官方明说的语义（"The order in which entrypoints are called
  is not guaranteed"）。NeoForge 线之所以没暴露，是因为它用 `EventPriority.HIGHEST`
  把内核**写死**在最前 —— 也就是说那份「顺序保证」本来就是靠优先级硬撑的。
- **修法（已实施）**：`PluginRegistry` 新增 **`onBoot(owner) { ... }`** 启动钩子，
  内核在 `boot()` 里「`aboutToStart` 之后、命令注册之前」按登记顺序调用。
  这套机制**与加载器无关** —— NeoForge 线将来也可以改用，从而去掉对 `EventPriority` 的隐式依赖。

### 8.3 仍待实测（阶段 3 的范围，施工单原文已列）

- `UseItemCallback` 取消后「物品是否被消耗 / 进入使用态」与 NeoForge `RightClickItem` 的差异
  （唯一用途是钟表菜单）。
- 逐个 feature 的真实行为（进服后 `/home`、`/warp`、`/tpa`、记分牌、聊天格式、菜单右键…）。

---

## 附录 A：验证回调类/签名的可复现命令

```bash
cd /tmp && mkdir -p fapi && cd fapi
# —— 1.21.1 ——
curl -sO https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/fabric-api-0.116.17+1.21.1.jar
mkdir -p x211 && (cd x211 && unzip -qo ../fabric-api-0.116.17+1.21.1.jar 'META-INF/jars/*')
# —— 1.20.1 ——
curl -sO https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.12+1.20.1/fabric-api-0.92.12+1.20.1.jar
mkdir -p x201 && (cd x201 && unzip -qo ../fabric-api-0.92.12+1.20.1.jar 'META-INF/jars/*')
# 各模块再解压一层（以 1.20.1 为例，版本号 0.92.12）
cd x201/META-INF/jars
for m in fabric-lifecycle-events-v1 fabric-command-api-v2 fabric-networking-api-v1 \
         fabric-message-api-v1 fabric-entity-events-v1 fabric-events-interaction-v0; do
  unzip -qo "$m-0.92.12.jar" -d "$m"
done
# javap（classpath 用 ';' 在 Windows 上分隔；单个模块可直接 -classpath 目录）
"C:/Users/26703/.jdks/jdk-21.0.12.1+1/bin/javap.exe" -p \
  -classpath fabric-entity-events-v1 net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
```

**实测结果（两版都跑过）**：
- ✅ 存在且两版一致：`ServerLifecycleEvents.SERVER_STARTING/SERVER_STARTED/SERVER_STOPPING/SERVER_STOPPED`、
  `ServerTickEvents.END_SERVER_TICK`、`CommandRegistrationCallback.EVENT`、
  `ServerPlayConnectionEvents.JOIN/DISCONNECT`、`ServerLivingEntityEvents.AFTER_DEATH`、
  `ServerMessageEvents.ALLOW_CHAT_MESSAGE/CHAT_MESSAGE`、`ServerMessageDecoratorEvent.EVENT`、
  `UseItemCallback.EVENT`。
- ⚠️ **1.21.1 独有**：`ServerPlayerEvents.JOIN` / `ServerPlayerEvents.LEAVE`
  （1.20.1 的 `ServerPlayerEvents` 只有 `COPY_FROM` / `AFTER_RESPAWN` / `ALLOW_DEATH`）。
- ✅ `FakePlayer`：**两版 fabric-api 都没有**（1.20.1 也无等价物）。

---

## 附录 B：本次调研实际读过的文件 / 源码 / 文档

**仓库**
- `build.gradle`、`settings.gradle`、`gradle.properties`、`build.sh`、`mirror.init.gradle`、
  `gradle/wrapper/gradle-wrapper.properties`
- `ARCHITECTURE.md`、`MIGRATION.md`、`ROADMAP.md`、`docs/dev/porting.md`
- `core/.../platform/Platform.kt`、`Platforms.kt`、`platform/neoforge/{NeoForgePlatform,NeoForgeEventBridge,NeoForgeLifecycleBridge,NeoForgeItemAccessor}.kt`
- `core/.../api/{Feature,ServerLifecycleListener,VItem,PermissionProvider,VoidkitServices,VoidkitServer,events/VEvents}.kt`
- `core/.../VoidkitCore.kt`、`core/.../core/PluginRegistry.kt`、`core/.../event/ServerLifecycle.kt`
- `core/.../resources/META-INF/neoforge.mods.toml`、`essentials/.../neoforge.mods.toml`、
  `essentials/.../VoidkitEssentials.kt`、`web/.../VoidkitWeb.kt`、`web/.../RemoteApiFeature.kt`、
  `web/.../ApiServer.kt`、`display/.../ChatFormatFeature.kt`、`tools/smoke-test.sh`

**外部**
- Fabric Loom 官方文档 <https://docs.fabricmc.net/develop/loom/>（remapJar/namedElements/include/split sources）
- Fabric 事件文档 <https://docs.fabricmc.net/develop/events>
- Loom release notes <https://github.com/FabricMC/fabric-loom/releases>（Gradle/Java/Kotlin 约束）
- Fabric 源码（mixin）：`fabric-lifecycle-events-v1` 的 `MinecraftServerMixin.java`、
  `fabric-command-api-v2` 的 `CommandManagerMixin.java`（GitHub `FabricMC/fabric` 1.21.1 分支）
- Fabric meta：`https://meta.fabricmc.net/v2/versions/loader/{1.21.1,1.20.1}`
- maven 元数据：`maven.fabricmc.net`（fabric-loader / fabric-api / fabric-loom / fabric-language-kotlin）
- 解包 + `javap`：**`fabric-api-0.116.17+1.21.1.jar` 与 `fabric-api-0.92.12+1.20.1.jar`（双版比对，见附录 A）**
- 双端触发点反编译：`fabric-events-interaction-v0` 的 `ServerPlayerInteractionManagerMixin`（服务端）
  与 `ClientPlayerInteractionManagerMixin`（客户端）都引用 `UseItemCallback`（两版皆然）
- 多加载器工程实践：<https://github.com/crittscott/SomeStacks/blob/main/multi-loader-transition.md>、
  Modstitch <https://github.com/isXander/modstitch>
