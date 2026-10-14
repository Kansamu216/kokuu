# 移植施工单：Minecraft 1.21.1 · Forge

> 分支 `mc/1.21.1-forge`（worktree `.worktrees/forge-1211`），从 `main`（1.21.1 NeoForge）拉出。
>
> ## 当前状态：**阶段 1 + 阶段 2 全部通过** ✅✅
>
> **阶段 1（编译）**：`BUILD SUCCESSFUL`，5 个产物。
> **阶段 2（真机起服）**：Forge 1.21.1-52.1.19 真机服务端 →
> ```
> Voidkit 模块 23 / 23 已启用
> Done (8.207s)!   管理 API 已启动: http://127.0.0.1:8767
> 致命错误：（无）
> /api/health /api/stats /api/perf /api/features /api/reload /api/broadcast 全 200
> ```
>
> 剩余（A 档最后一环）：**发一个 beta 包**（bundleZip 已适配 core 取 jarJar 产物）。
>
> ### Kotlin 运行时打包（FG7 的关键差异，已解决）
>
> ForgeGradle 7 **把 Jar-in-Jar 拆成了独立 Gradle 插件**：
> ```groovy
> plugins { id 'net.minecraftforge.jarjar' version '0.2.3' apply false }   // 根
> apply plugin: 'net.minecraftforge.jarjar'                                // core
> jarJar.register()
> dependencies { jarJar("org.jetbrains.kotlin:kotlin-stdlib:2.4.21") }
> ```
> 依据官方示例 `MDKExamples/jarjar-only/fg7-kotlin`。
> 写精确版本 → 产出的 `META-INF/jarjar/metadata.json` 里约束为 `[该版本,)`，
> 即**只锁下界不写上界**（正是项目铁律要求）。
> **fat jar 由 `jarJar` 任务产出**（本项目让它直接用规范名，普通 jar 退为 `-slim`），
> `bundleZip` 对 core 取 `jarJar`。
>
> ### 根因链（三层，逐层剥开才看到真因）
>
> 表面报错：`Could not isolate value ...MavenizerValueSource$Parameters`
>
> 1. **不是** Gradle 版本问题 —— 实测 9.5.1 与 9.3.1 报同样的错（**证伪**了我的第一假设）
> 2. **不是** configuration-cache —— 本仓库本来就 `org.gradle.configuration-cache=false`
> 3. **真因**：该 value source 的嵌套原因被 Gradle 吞在第二层 ——
>    ```
>    Cannot find a Java installation on your machine matching:
>      {languageVersion=25, ...}. Toolchain auto-provisioning is not enabled.
>    ```
>    **ForgeGradle 7 的 mavenizer 需要 JDK 25 工具链**（它跑在较新的 JVM 上），
>    而本仓库 `gradle.properties` 只登记了 JDK 21/17 且禁用了自动下载 →
>    Gradle 找不到 JDK 25，value source 求值失败，外层只报「无法隔离」。
>
> 修复（本分支 `gradle.properties`）：
> ```
> org.gradle.java.installations.paths=<...JDK21>,<...JDK17>,C:/Program Files/Microsoft/jdk-25.0.3.9-hotspot
> ```
> （JDK 25 是本机自带的 Microsoft OpenJDK，见 `MEMORY.md` 的「构建与环境」）
>
> 同时按官方 MDK 补上了 `repositories { minecraft.mavenizer(it); maven fg.forgeMaven;
> maven fg.minecraftLibsMaven }` —— 这三行是 ForgeGradle 的标准配置，之前的普通 maven URL
> 写法不完整（虽然真因是 JDK 25，但这两处都该修）。

## 0. 为什么这条线比 1.20.1 简单

| | 1.20.1（已发布线） | **1.21.1（本线）** |
|---|---|---|
| 运行时映射 | **SRG**（`m_41619_`）→ 必须 `reobf` | **官方映射**（`isEmpty`）→ **无需 reobf** |
| 发布产物 | `build/libs`（reobf 后），`devlibs` 是开发态 | `build/libs` **就是**发布产物 |
| 冒烟/真机 | 必须区分 dev/release 两套 jar | 一套 jar 走天下 |

> 实测依据：`javap` Forge 1.21.1 的 `forge-1.21.1-52.1.19-universal.jar` →
> `net.minecraftforge.common.ForgeHooks` 里引用的是 `ItemStack.isEmpty` / `Item.getItem`；
> 同一方法在 Forge 1.20.1（47.4.20）的 jar 里是 `ItemStack.m_41619_` / `m_41720_`。

## 1. 已核实的事实（2026-10-14，全部有实证）

| 项 | 值 | 来源 |
|---|---|---|
| Forge 版本 | **1.21.1-52.1.19**（推荐线 52.1.x） | `maven.minecraftforge.net` 的 maven-metadata |
| 构建插件 | **ForgeGradle 7**（`net.minecraftforge.gradle` `[7.0.3,8)`） | 官方 MDK 的 `build.gradle` |
| 映射 | `mappings channel: 'official', version: '1.21.1'` | 同上 |
| JDK | **21** | 官方 MDK `java.toolchain.languageVersion = 21` |
| 依赖声明 | `implementation minecraft.dependency('net.minecraftforge:forge:1.21.1-52.1.19')` | 同上 |
| mod 清单 | **`META-INF/mods.toml`**（**不是** `neoforge.mods.toml`） | MDK + 实测 |
| 清单头 | `modLoader="javafml"`；依赖 `modId="forge"` + `mandatory=true` | MDK |
| Forge 自身 modId | `forge`（`displayName="Forge"`） | universal jar 的 `mods.toml` |
| maven 可达性 | `maven.minecraftforge.net` ✅ 可达（`maven.neoforged.net` ❌ 被污染） | curl 实测 |

关于 `loaderVersion`：官方 MDK 用宽松的 `[0,)`；Forge 自身的 mods.toml 用 `[24,)`；
本线按「Forge 大版本号」约定写 **`[52,)`**（与 1.20.1 线写 `[47,)` 同一套约定），
若真机报 loader 版本不匹配则改为 `[24,)`。

## 1b. 构建系统的硬约束（踩过，别再试）

**MDG（ModDevGradle）不支持 Forge 1.21.1** —— 它的 `legacyforge` 插件只覆盖
**1.17 ~ 1.20.1**（官方 ModDevGradle 2 发布说明 + DeepWiki 的插件 ID 清单）。
主线 `main` 用的 MDG **无法**编译这条线。

**唯一路线 = ForgeGradle 7 + Gradle ≥ 9.3.0。** 实测报错原文：

```
ERROR: ForgeGradle 7 requires Gradle 9.3.0 or later to run. You are currently using Gradle 8.12.1.
```

因此本分支（且**只在**本分支）把构建工具升级为：

| 组件 | 其余版本线 | 本线 `mc/1.21.1-forge` |
|---|---|---|
| Gradle | 8.12.1 | **9.5.1** |
| Kotlin Gradle 插件 / stdlib | 2.1.0 | **2.4.21**（Gradle 9 需要新版 Kotlin 插件） |

⚠️ 升级只动本分支的 `gradle/wrapper/gradle-wrapper.properties` 与 `build.gradle`，
其余线不受影响；后续 main / 1.20.1 / fabric 的改动仍要 forward-port 到本线时注意
Kotlin 版本差异（2.1 vs 2.4 的语法/告警可能不同）。

## 2. NeoForge → Forge 符号对照表（平台层用，已 javap 逐个核实）

| 能力 | NeoForge 1.21.1 | Forge 1.21.1 |
|---|---|---|
| `@Mod` | `net.neoforged.fml.common.Mod` | `net.minecraftforge.fml.common.Mod` |
| mod 列表 | `net.neoforged.fml.ModList` | `net.minecraftforge.fml.ModList` |
| mod 构造器 | `(IEventBus, ModContainer)` | `()`（本线只用无参；Forge 52 亦可注入 `FMLJavaModLoadingContext`） |
| 主事件总线 | `net.neoforged.neoforge.common.NeoForge.EVENT_BUS` | `net.minecraftforge.common.MinecraftForge.EVENT_BUS` |
| `@SubscribeEvent` | `net.neoforged.bus.api.SubscribeEvent` | `net.minecraftforge.eventbus.api.SubscribeEvent` |
| `EventPriority` | `net.neoforged.bus.api.EventPriority` | `net.minecraftforge.eventbus.api.EventPriority` |
| 游戏目录 | `net.neoforged.fml.loading.FMLPaths` | `net.minecraftforge.fml.loading.FMLPaths`（**同类名**） |
| 登录/登出 | `...neoforge.event.entity.player.PlayerEvent` | `net.minecraftforge.event.entity.player.PlayerEvent` |
| 死亡 | `...neoforge.event.entity.living.LivingDeathEvent` | `net.minecraftforge.event.entity.living.LivingDeathEvent` |
| 聊天 | `...neoforge.event.ServerChatEvent` | `net.minecraftforge.event.ServerChatEvent`（`getRawText/getMessage/setMessage` 同名） |
| 右键 | `...neoforge.event.entity.player.PlayerInteractEvent` | `net.minecraftforge.event.entity.player.PlayerInteractEvent`（`getHand/getItemStack`） |
| 注册命令 | `...neoforge.event.RegisterCommandsEvent` | `net.minecraftforge.event.RegisterCommandsEvent` |
| 服务器生命周期 | `...neoforge.event.server.{ServerAboutToStart,ServerStarted,ServerStopping}Event` | `net.minecraftforge.event.server.*`（同类名） |
| 每 tick | `...neoforge.event.tick.ServerTickEvent.Post` | **`net.minecraftforge.event.TickEvent.ServerTickEvent.Post`**（Forge 52 拆 Pre/Post） |
| 假玩家 | `net.neoforged.neoforge.common.util.FakePlayer` | **不存在**（见下） |
| 物品「有无额外组件」 | `ItemStack#isComponentsPatchEmpty`（NeoForge 便捷方法） | 原版 `ItemStack#getComponentsPatch().isEmpty()` |

## 3. ⚠️ 坑：Forge 1.21.1 没有 `FakePlayer`

`find -iname '*fake*'` 扫遍 `forge-1.21.1-52.1.19-universal.jar` → **零命中**。
Forge 已移除该 API（NeoForge 保留并扩展）。

→ `ForgePlatform.isFakePlayer()` 退化为与 `isUnconnectedPlayer()` 同一判据
（`ServerPlayer && connection == null`）。这与科技包的现实相符（自动化假玩家绝大多数
就是「有玩家身份、无网络连接」），但**覆盖率低于 NeoForge 线**，
审计模块的「自动化过滤」在 Forge 线上可能漏判更多 —— 上线前需实测校准。

## 4. 已完成的改动（相对 main）

| 文件 | 改动 |
|---|---|
| `core/.../platform/forge/{ForgePlatform,ForgeEventBridge,ForgeLifecycleBridge,ForgeItemAccessor}.kt` | **新增**，逐行对应 neoforge/ 四件套 |
| `core/.../platform/neoforge/**` | **删除**（Forge classpath 上不存在 `net.neoforged.*`） |
| `core/.../platform/Platforms.kt` | 默认实现 → `ForgePlatform` |
| 5 个入口 `Voidkit{Core,Essentials,Display,Utility,Web}.kt` | 换 import（`net.minecraftforge.fml.*`）、构造器改无参、`@Mod` 换包 |
| 5 个 `META-INF/neoforge.mods.toml` | 改名 `mods.toml`；依赖 `modId="neoforge"`→`"forge"`；`type="required"`→`mandatory=true` |
| `build.gradle` | MDG → **ForgeGradle 7**；`neoForge{}` → `minecraft{ mappings official }`；依赖改 `minecraft.dependency(...)`；`filesMatching` 改 `mods.toml` |

## 5. 待办（按顺序）

1. **编译通过**（当前里程碑）—— 修 ForgeGradle 7 DSL 与 Kotlin 插件的摩擦点
2. **Kotlin 运行时打包**：`core` 用 jarJar 内嵌 `kotlin-stdlib`（ForgeGradle 7 的 jarJar 语法与 MDG 不同），并保留「只锁下界、不写上界」的铁律
3. **服务端真机启动**：装 Forge 1.21.1 服务端（`installer --installServer`，注意 `maven.neoforged.net` 污染的绕法见主 `MEMORY.md`），验 23 模块注册 + API 全 200
4. **冒烟脚本扩展**：`tools/smoke-test.sh` 目前按 `-PmcVersion` 推加载器，需要支持「1.21.1 + forge」这一组合（`+mc1.21.1-forge` 后缀）
5. **文档与发布**：`docs/guide/versions.md` 支持矩阵、README 版本表、`bundleZip` 产物命名

## 6. 验证（改完必跑）

```bash
cd .worktrees/forge-1211
./build.sh build --continue            # 编译（JDK 21）
# 真机：装 Forge 1.21.1 服务端后，把 build/libs 的 5 个 jar 丢进 mods/
```
