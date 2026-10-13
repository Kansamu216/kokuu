# 内核生命周期加固施工单（Core Hardening）

> **这份文档给执行代码开发的模型看**（尤其是参数较小的模型）：按 T1 → T6 顺序一张任务卡一张任务卡地做，
> 每张卡都是自包含的 —— 文件路径、现状代码、目标代码、操作步骤、验证命令、验收标准、禁止事项全部写在卡里。
> **不要自由发挥**：卡里没让你改的文件一行都别动，卡里没让你加的方法一个都别加。

- 关联 issue：`KokuuWork/voidkit` 的「架构：内核生命周期加固」issue（本文档是它的施工依据）
- 前置阅读：`CONTRIBUTING.md`（commit 规范）、`docs/dev/adding-a-feature.md`（Feature 写法）
- 施工分支：`main`（1.21.1 / NeoForge）。完成后按 §4.3 forward-port 到 `mc/1.20.1` 与 `feat/fabric`。
- **代码可信度**：本稿中的「目标文件全文 / 目标代码」已于 2026-10-13 在 main 线**实际编译通过并跑完 smoke-test**
  （23 模块注册、28 个管理 API 全 200、19 项结构断言全 ✓），验证后已回滚、留给施工模型按卡施工。
  照抄即可，不需要「优化」。

---

## 1. 背景：外部审计结论逐条核验

有人对 `voidkit-main.zip` 的 core 层做了一份审计报告（下称「审计」），列出 CORE-001 ~ CORE-019。
我们**逐条对照当前源码核验**过，结论分三类：属实 / 部分过时 / 前瞻性风险。**以本表为准，不要直接照抄审计原文。**

| 审计条目 | 核验结论 | 证据（当前源码） |
|---|---|---|
| CORE-001/011 Feature 无生命周期状态机 | ✅ 属实 | `core/api/Feature.kt` 只有 `var enabled: Boolean`，无 onEnable/onDisable/destroy/state |
| CORE-002 reload 不是完整重载 | ✅ 属实（框架层） | `PluginRegistry.reload()` = `applyConfig` + `onReload`；停用的模块没有被停掉/回收，模块只能自救（如 `RemoteApiFeature.onReload` 自己起停 HTTP 服务） |
| CORE-003 缺模块依赖系统 | ✅ 属实但当前无需求 | `register(feature, owner)` 只有 id/owner；合并 5 jar 后插件间零编译依赖，通信走 `VoidkitServices`（带兜底），暂无拓扑排序需要 → **本轮不做**（见 §3） |
| CORE-004/009/010 active 列表状态同步风险 | ✅ 属实 | `PluginRegistry.kt` 注释写「用并发容器」，实际是 `LinkedHashMap` / `HashMap` / `ArrayList`；`applyConfig` 里 `active.clear()+add()`；`web/ApiDefaults.kt` 从 HTTP 线程读 `PluginRegistry.all()` |
| CORE-005/013 异常无分级 | ✅ 部分属实 | 模块级 `runCatching` 隔离**是刻意设计**（一个坏模块不能炸服务器），保留；真正的问题是内核级失败也被同等吞掉（见 CORE-006/012/018） |
| CORE-006/018 配置加载 `catch(Throwable)` 过宽 | ✅ 属实 | `VoidkitCore.loadConfig()` `catch (t: Throwable)` → 默认值静默继续，管理员无从得知 |
| CORE-007 事件无优先级 | ✅ 属实但当前无跨模块顺序依赖 | `dispatch` 按 active 注册顺序逐个通知；现有 VEvent 消费方之间无顺序依赖 → **本轮不做** |
| CORE-008 无卸载能力 | ✅ 属实 | 无 `unregister()`；MC mod 本就不热卸载 → **本轮不做**（ destroy 钩子会补上） |
| CORE-012 boot 失败仍标记 booted | ✅ 属实 | `boot()` 里 `aboutToStart` 的异常被 `forEach` 吞掉后照常 `booted = true`，无 per-feature 状态 |
| CORE-014 DataStore 原子写/并发容器 | ✅ 好评属实 | `DataStore.writeJson` tmp + ATOMIC_MOVE 回退；`profiles` 是 `ConcurrentHashMap` |
| CORE-015 PlayerProfile 非线程安全 | ⚠️ 部分过时 | 集合字段**已经**是并发容器（`knownNames`/`ips`/`homes`，为修面板 500 改过一次）；但 `permissions` 仍是 `mutableListOf()`，`recordIp`/`addKnownName` 是 check-then-act 竞态 → T5 |
| CORE-016 DataStore 单例 | ⚠️ 部分过时 | 已是 `class` + companion `instance`（不是 `object`）；仍是 Service Locator，但测试困难是既有取舍 → **本轮不做** |
| CORE-017 启动缺失败回滚 | ✅ 属实 | `ServerLifecycle.onServerAboutToStart` 顺序合理（建目录→迁移→内核配置→DataStore→boot），但某模块失败后没有「标记+禁功能+继续核心」的机制 → T2/T4 |
| CORE-019 CoreServices 上帝类 | ⚠️ 前瞻性风险 | 当前只有 4 个方法，主通道是 `VoidkitServices` → **本轮不做** |
| 「不适合几百 Mod 长期运行」 | ⚠️ 论断过重 | 已在 165 mod 整合包实测跑通；真实风险集中在「多次 reload + 长时运行」的隐蔽状态问题，正是本轮要解决的 |

---

## 2. 本轮总目标

把「架构已是框架、生命周期还是中小 mod 水平」这个问题补上，让 `/vk reload` 成为**安全的**运维操作：

1. 注册表读写线程安全（T1）；
2. Feature 有显式生命周期状态与 enable/disable/destroy 钩子（T2）；
3. reload 按 diff 走各自生命周期：新停用的停掉并回收，新启用的补启动，失败的出列并可经 reload 重试（T3）；
4. 失败**可见**：启动日志、`/vk features` 有标记；内核配置损坏时明确告知「安全模式」且只吞 `Exception`（T4）；
5. 收尾两个小的并发/文档残留（T5、T6）。

---

## 3. 明确不做的事（及理由）

- **不**做模块依赖拓扑（depends/optional/conflicts）：当前无跨插件编译依赖，服务走 `VoidkitServices` 兜底。
- **不**做事件优先级：现有 VEvent 消费方无顺序依赖；真有需求时再加，加的理由要写进注释。
- **不**做 `unregister()` / ClassLoader 隔离 / 热卸载：MC mod 生命周期=服务端生命周期，做了也没有收益。
- **不**拆 `CoreServices`、不引入 `VoidkitContext` 依赖注入：等真的出现第 5、6 个服务再说。
- **不**在配置损坏时阻断服务器启动：产品立场是「服务器必须能起来」，默认值都调过；
  改用 ERROR 级日志 + `configSafeMode` 标记 + `/vk status` 提示（T4）。
- **不**改任何玩法行为、不改任何配置结构、不改数据路径。

---

## 4. 全局纪律（所有任务通用）

### 4.1 代码纪律

1. 全 Kotlin，不写 Java；注释用中文，讲「为什么这么写」，不复述代码。
2. `core.api` 契约包（`org.voidkit.core.api.*`）**不许 import 任何加载器类**（`net.neoforged.*` / `net.minecraftforge.*` / `net.fabricmc.*`）。`net.minecraft.*` 可以直接用。
3. 零 mixin；不碰原版已有人碰的东西；周期逻辑只写 `Feature.onSecond`。
4. 不新增第三方依赖；不改 `build.gradle` / `settings.gradle` / 任何 toml/json 资源。
5. **只改任务卡里列出的文件**。发现卡外问题：记到该任务「验收」最后的「附带发现」里，不要顺手改。
6. 每个任务一个 commit，message 按 `CONTRIBUTING.md`：`type(scope): English subject` + 空行 + 中文正文（为什么改、根因、影响面、验证结果；未能验证的写明「仅静态推理」）。
   scope 用 `core`。例如：`refactor(core): make PluginRegistry reads snapshot-based and writes lock-guarded`。
7. 任务卡里的「目标代码」是**逐字可用的**：照抄，不要「优化」。如果照抄后编译不过，**停下来报告**，不要自己改设计。

### 4.2 验证纪律（每张卡做完各跑一遍）

```bash
# main 线（1.21.1）
./build.sh build --continue -PmcVersion=1.21.1
tools/smoke-test.sh -PmcVersion=1.21.1
```

合格线（缺一不可）：`Done (...)` ＋ **23 个 feature 一个不少** ＋ 0 注册失败 ＋ **28 个管理 API 全 200** ＋ **19 项结构断言全 ✓**；日志里 `[CMD] 注册命令` 有 23 项。

> 🚨 jar 编译过 ≠ 能加载。T1–T4 动了注册表/生命周期，**必须真起一次开发服**（smoke-test 就是干这个的）。
> 🚨 权限判定类、事件顺序类行为冒烟测不到 → 验收里注明「仅静态推理」，**不许写「已验证」**。

### 4.3 分支纪律

1. 全部任务在 `main` 上按 T1→T6 顺序提交，每个任务 commit 前跑 4.2。
2. 六个任务全部完成、main 全绿后，**依次** cherry-pick 到 `mc/1.20.1` 和 `feat/fabric`（都是同一批 commit，不要重写）：
   ```bash
   git -C .worktrees/main log --oneline -6   # 找到 T1..T6 的 commit（老的在前）
   # 在主工作区（mc/1.20.1）：
   git cherry-pick <T1> <T2> <T3> <T4> <T5> <T6>
   ./build.sh build --continue -PmcVersion=1.20.1
   tools/smoke-test.sh -PmcVersion=1.20.1
   tools/e2e-forge-server.sh
   # fabric 线（.worktrees/fabric，只需编译过；行为验证属 PORTING-FABRIC 阶段 3，本轮不做）：
   git -C .worktrees/fabric cherry-pick <T1> <T2> <T3> <T4> <T5> <T6>
   ./build.sh build --continue -PmcVersion=1.21.1
   ```
3. 三条线都绿了再 push（`git push`）；mc/1.20.1 若 cherry-pick 冲突，**停下来报告**，不要手动揉。
4. 🚨 Gradle 一次只跑一个（共用 `~/.gradle`）：三条线的构建**串行**做，不要并行。

---

## 5. 任务卡

### T1 — PluginRegistry 线程安全（审计 CORE-004 / 009 / 010）

**目标**：写路径加锁、读路径取快照；启用集合用 `@Volatile` 快照整体原子替换。修掉「注释说并发容器、实际不是」的声明与实现不一致。

**文件**：`core/src/main/kotlin/org/voidkit/core/core/PluginRegistry.kt`（只改这一个文件）

**做法**：用下面的「目标文件全文」整体替换该文件。已对照当前源码逐行核对，未改动部分的语义与原文件一致。

**目标文件全文**：

```kotlin
package org.voidkit.core.core

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.FeatureState
import org.voidkit.core.config.VoidkitConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 功能模块注册表 —— 插件系统的唯一入口。
 *
 * ## 与旧版的关键区别
 *
 * 内核**不再硬编码**模块清单。每个插件在自己的 `@Mod` 构造里调 [register]，
 * 把自己的模块交进来。内核完全不知道有哪些插件存在，也不知道它们装了几个模块。
 *
 * 这让「装满所有模块」和「只装核心」跑的是同一条代码路径 —— 没有 if 判断某个来源
 * 在不在，只有「注册表里有几个模块」这一个事实。
 *
 * ## 注册时机
 *
 * [register] 是纯内存操作，可以在任何插件的构造期随时调用，**不需要等内核先初始化**。
 * 真正的启动动作（读配置、挂事件、注册命令）统一定在服务端启动阶段，见 [boot].
 *
 * ## 线程安全策略：写加锁、读快照
 *
 * 注册发生在 mod 构造期（FML 单线程），但 reload（`/vk reload`，服务器线程）会改写
 * 启用集合，Web API 线程又会并发读取注册表（见 `web` 模块 `ApiDefaults` 的
 * `/api/features`）。LinkedHashMap / ArrayList 都不是并发容器，「一边遍历一边 clear」
 * 会抛 ConcurrentModificationException。
 *
 * 所以：**写**操作统一进 [lock]（锁内只有纯内存操作，无 IO，不会长时间持锁）；
 * **读**操作一律拷贝快照；启用集合用 [activeSnapshot] 整体原子替换 ——
 * 正在跑的事件循环拿着旧列表跑完，新循环用新列表，读写互不阻塞。
 */
object PluginRegistry {

    /** 没标明来源时的默认归属。 */
    const val CORE_OWNER = "voidkit_core"

    /** 注册表写锁。 */
    private val lock = Any()

    private val all = LinkedHashMap<String, Feature>()
    private val owners = HashMap<String, String>()

    /**
     * 每个模块最近一次生命周期失败的原因（键不存在 = 正常）。
     *
     * 为什么需要它：模块失败不能只躺在日志里 —— `/vk features` 要把 FAILED 标出来，
     * 启动汇总也要报数，否则「模块启动失败」和「一切正常」在运维眼里没有区别（审计 CORE-012）。
     * 重新启用/重试成功时由 [step] 清除。
     */
    private val failures = ConcurrentHashMap<String, String>()

    /**
     * 当前启用的模块快照。
     *
     * reload 时**整体替换**而不是 clear()+add()：旧列表可能正被某个事件循环遍历，
     * 让它跑完；新循环看到的是完整的新列表，不会出现「半个新列表」。
     */
    @Volatile
    private var activeSnapshot: List<Feature> = emptyList()

    @Volatile
    private var booted = false

    /** RegisterCommandsEvent 先于 boot 触发时暂存的 dispatcher（见 registerCommands）。 */
    @Volatile
    private var pendingCommands: CommandDispatcher<CommandSourceStack>? = null

    /**
     * 启动钩子：各插件登记「内核 boot 完成后、命令注册之前」要做的事（通常是读自己的配置）。
     *
     * ## 为什么不让插件直接监听加载器的「服务端即将启动」事件
     *
     * 插件读配置的前提是「内核已经建好配置根目录、初始化了
     * [org.voidkit.core.util.ConfigFiles]」—— 这是个**顺序要求**。而加载器事件回调的顺序
     * **不由我们控制**：
     *  - 本分支（NeoForge 1.21.1）历史上靠给内核的监听标 `EventPriority.HIGHEST`
     *    把内核排到最前（见 `NeoForgeLifecycleBridge`）。那是**靠优先级硬撑**出来的顺序，
     *    并没有写进任何契约 —— 只要以后有别的注册路径插入，或者换到没有优先级的加载器，就会破。
     *  - **Fabric 就没有事件优先级**。2026-10-13 的 Fabric 分支实测：四个插件的 entrypoint
     *    跑在内核**前面**，插件回调里的 `ConfigFiles.resolve` 直接抛
     *    `IllegalStateException: ConfigFiles 尚未初始化（rootDir 未设置）`，服务端起不来。
     *
     * 所以把时机收回内核自己手里：插件登记钩子，内核在 [boot] 里按登记顺序调用。
     * 顺序由**内核的代码顺序**保证，而不是由加载器的派发语义保证 —— 这套机制
     * **与加载器无关**，NeoForge / Fabric 两条线用同一套，不再需要优先级。
     */
    private val bootHooks = CopyOnWriteArrayList<Pair<String, () -> Unit>>()

    /** 登记一个启动钩子。`owner` 只用于失败时的日志归属。 */
    fun onBoot(owner: String, block: () -> Unit) {
        bootHooks += owner to block
    }

    fun isBooted(): Boolean = booted

    /**
     * 登记一个功能模块。由各插件的 `@Mod` 构造调用。
     *
     * @param owner 该模块的来源标签（如 `voidkit_essentials`），仅用于面板分组显示，
     *              不要求它对应一个独立插件（合并后一个 jar 可提供多个来源标签）。
     */
    fun register(feature: Feature, owner: String = CORE_OWNER) {
        synchronized(lock) {
            if (all.containsKey(feature.id)) {
                LOG.error("重复的功能模块 id: {}（来自 {}），已忽略后者", feature.id, owner)
                return
            }
            all[feature.id] = feature
            owners[feature.id] = owner
        }
    }

    /**
     * 服务端即将启动时由内核调用：读配置 → 决定开关 → 挂事件总线。
     *
     * 放在这里而不是 mod 构造期，是为了让**构造期保持零 IO** —— 大型整合包里
     * 上百个 mod 并行加载，在构造期读文件会把启动时间拉长好几秒。
     */
    fun boot(config: VoidkitConfig, server: MinecraftServer) {
        if (booted) {
            LOG.warn("PluginRegistry 已经启动过一次，忽略重复调用")
            return
        }
        applyConfig(config)

        // ① 启用（onEnable）：配置打开的模块先启用。此刻命令还没注册，模块可以做准备。
        //    失败即出列（状态 FAILED）—— 坏模块不会拖垮整台服务器。
        val enabled = ArrayList<Feature>()
        activeSnapshot.forEach { feature ->
            if (feature.step(FeatureState.ENABLED, "启用") { feature.onEnable(server) }) {
                enabled.add(feature)
            }
        }
        activeSnapshot = enabled

        attachEventHandlers()
        aboutToStart(server)

        // 插件的配置加载：放在 aboutToStart 之后、命令注册之前。
        // 顺序由**本方法**保证，不再依赖加载器事件的派发顺序（见 onBoot 的说明）。
        runBootHooks()

        booted = true

        // 若 RegisterCommandsEvent 已先于 boot 触发（NeoForge 1.21.1 的时序），
        // 现在 active 已填好，补注册命令。
        pendingCommands?.let { doRegisterCommands(it) }
        pendingCommands = null

        val byOwner = pluginSummary()
        val failed = all().count { it.state == FeatureState.FAILED }
        LOG.info(
            "Voidkit 模块 {} / {} 已启用{}，按来源分组：{}",
            activeSnapshot.size, all().size,
            if (failed > 0) "，${failed} 个失败（/vk features 查看）" else "",
            byOwner.entries.joinToString(", ") { "${it.key}(${it.value})" },
        )
    }

    /** 依据配置刷新启用状态（reload 复用）。 */
    fun applyConfig(config: VoidkitConfig) {
        val next = ArrayList<Feature>(all.size)
        synchronized(lock) {
            all.values.forEach { feature ->
                val on = config.featureEnabled(feature.id, feature.defaultEnabled)
                feature.enabled = on
                if (on) next.add(feature)
            }
        }
        // 整体替换：旧列表可能正被事件循环遍历，让它跑完；新循环用新列表。
        activeSnapshot = next
    }

    /**
     * 事件挂载接入点（历史保留位）。
     *
     * VEvent 抽象（见 [dispatch]）落地后，Feature 的业务事件统一由框架翻译后经 [dispatch]
     * 逐个通知，**没有任何 Feature 再挂到加载器原生事件总线**，本方法因此是无害空实现。
     *
     * 保留它（而非删掉调用点）是为了不改变 [boot] / [reload] 的调用时序与可读性。
     */
    fun attachEventHandlers() {
        // 空实现：不再有模块挂到加载器事件总线（VEvent 已取代）。
    }

    /**
     * 通知已启用的模块：服务端即将启动，可以加载自己的配置了。
     *
     * 逐个走状态机：本阶段失败的模块出列 —— 不再注册它的命令、不会启动它。
     * 没有这套机制时，某模块启动失败会被静默吞掉、服务器却认为一切正常（审计 CORE-012）。
     */
    fun aboutToStart(server: MinecraftServer) {
        val ready = ArrayList<Feature>()
        activeSnapshot.forEach { feature ->
            if (feature.step(FeatureState.ENABLED, "启动准备") { feature.onServerAboutToStart(server) }) {
                ready.add(feature)
            }
        }
        activeSnapshot = ready
    }

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        if (!booted) {
            // 🚨 NeoForge 1.21.1 实测：RegisterCommandsEvent 在 ServerAboutToStartEvent
            // （boot 填 active）**之前**触发。此时 active 还是空的，直接注册会一个命令都不注册。
            // 所以先暂存 dispatcher，等 boot 完成后再补注册（见 boot()）。
            pendingCommands = dispatcher
            return
        }
        doRegisterCommands(dispatcher)
    }

    private fun doRegisterCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        LOG.info("[CMD] 注册命令，active 模块 = {}", activeSnapshot.map { it.id })
        activeSnapshot.forEach { feature ->
            runCatching { feature.registerCommands(dispatcher) }
                .onFailure { LOG.error("模块 {} 注册命令失败", feature.id, it) }
        }
    }

    fun registerApi(router: ApiRouter) {
        activeSnapshot.forEach { feature ->
            runCatching { feature.registerApi(router) }
                .onFailure { LOG.error("模块 {} 注册 API 失败", feature.id, it) }
        }
    }

    /**
     * ServerStarted：逐个启动。
     *
     * 启动失败的模块出列（状态 FAILED，记原因；可经 `/vk reload` 重试，见 [reload]）——
     * 否则它会以半初始化状态继续收 onSecond / 事件，问题更难查。
     */
    fun startAll(server: MinecraftServer) {
        val running = ArrayList<Feature>()
        activeSnapshot.forEach { feature ->
            if (feature.step(FeatureState.RUNNING, "启动") { feature.onServerStart(server) }) {
                running.add(feature)
            }
        }
        activeSnapshot = running
    }

    fun stopAll(server: MinecraftServer) = forEach("停止") { it.onServerStop(server) }

    fun tickSecond(server: MinecraftServer) = forEach("tick") { it.onSecond(server) }

    /**
     * 重载：按新配置 diff 出「新停用 / 新启用 / 保持」三类，分别走各自的生命周期。
     *
     * 旧实现只是 `applyConfig` + 全员 `onReload`：被关掉的模块没人停、没人回收，
     * 新打开的模块从没启动过 —— 资源泄漏和双实例问题的根源。现在：
     *
     * ```
     * reload
     *  ↓  applyConfig（重算开关，整体替换 activeSnapshot）
     * ① 新停用的：onServerStop → onDisable → destroy，状态 DISABLED
     * ② 新启用的（含上次失败待重试的）：onEnable → onServerStart → onReload，状态 RUNNING
     * ③ 保持启用的：onReload，状态 RUNNING
     * ```
     */
    fun reload(server: MinecraftServer) {
        val beforeIds = activeSnapshot.map { it.id }.toSet()
        applyConfig(VoidkitCore.config)
        val candidates = activeSnapshot
        val afterIds = candidates.map { it.id }.toSet()

        // ① 被关掉的模块：停 → 回收 → 置 DISABLED。之后它收不到任何回调。
        all().filter { it.id in beforeIds && it.id !in afterIds }.forEach { feature ->
            feature.step(FeatureState.DISABLED, "停用") {
                feature.onServerStop(server)
                feature.onDisable(server)
                feature.destroy()
            }
        }

        // ②③ 仍然/刚刚在列的模块：新的补完整启动，旧的只做热更新。失败的出列。
        val next = ArrayList<Feature>(candidates.size)
        candidates.forEach { feature ->
            val fresh = feature.id !in beforeIds || feature.state == FeatureState.FAILED
            val ok = if (fresh) {
                // 它的 onServerStart 从没被调用过（启动时它不在列），必须补一次
                feature.step(FeatureState.RUNNING, "启用并启动") {
                    feature.onEnable(server)
                    feature.onServerStart(server)
                    feature.onReload(server)
                }
            } else {
                feature.step(FeatureState.RUNNING, "重载") { feature.onReload(server) }
            }
            if (ok) next.add(feature)
        }
        activeSnapshot = next
    }

    private inline fun forEach(action: String, block: (Feature) -> Unit) {
        activeSnapshot.forEach { feature ->
            runCatching { block(feature) }
                .onFailure { LOG.error("模块 {} {}失败", feature.id, action, it) }
        }
    }

    /** 按登记顺序调用启动钩子；单个插件抛异常不影响其它插件。 */
    private fun runBootHooks() {
        bootHooks.forEach { (owner, block) ->
            runCatching(block).onFailure { LOG.error("{} 的启动钩子失败（配置可能未加载）", owner, it) }
        }
    }

    /**
     * 把事件分发给所有已启用模块（VEvent 抽象的核心）。
     *
     * 由 platform 层的 EventBridge 调用：它把加载器原生事件翻译成 VEvent 后，
     * 通过本方法逐个通知 active 模块的对应回调。单个模块抛异常不影响其它模块。
     */
    fun dispatch(block: (Feature) -> Unit) {
        activeSnapshot.forEach { feature ->
            runCatching { block(feature) }
                .onFailure { LOG.error("模块 {} 事件处理失败", feature.id, it) }
        }
    }

    /**
     * 执行一次生命周期动作：成功切到 [ok] 状态；异常记 FAILED 与原因，返回 false。
     *
     * 为什么模块级仍然 catch Throwable：一个坏模块不能炸掉整台服务器，这是刻意的隔离。
     * 要分级的是**内核级**失败（配置加载等，见 `VoidkitCore.loadConfig`），不在这里。
     */
    private fun Feature.step(ok: FeatureState, action: String, block: () -> Unit): Boolean {
        return try {
            block()
            state = ok
            failures.remove(id)
            true
        } catch (t: Throwable) {
            state = FeatureState.FAILED
            failures[id] = t.message ?: t.javaClass.simpleName
            LOG.error("模块 {} {}失败", id, action, t)
            false
        }
    }

    fun all(): List<Feature> = synchronized(lock) { all.values.toList() }

    fun active(): List<Feature> = activeSnapshot

    fun byId(id: String): Feature? = synchronized(lock) { all[id] }

    fun ownerOf(featureId: String): String = synchronized(lock) { owners[featureId] } ?: CORE_OWNER

    /** 来源标签 -> 该来源提供的模块数。面板用。 */
    fun pluginSummary(): Map<String, Int> = synchronized(lock) { owners.values.groupingBy { it }.eachCount() }

    private val LOG get() = VoidkitCore.LOG
}
```

**步骤**：
1. 备份思路不需要（git 里有历史）；直接整文件替换。
2. `./build.sh build --continue -PmcVersion=1.21.1` —— 必须 0 错误。
3. `tools/smoke-test.sh -PmcVersion=1.21.1` —— 23 feature / 28 API / 19 断言全过。

**验收**：
- [ ] 编译 0 错误；冒烟全绿（23 feature 一个不少）。
- [ ] `git diff --stat` 只有 `PluginRegistry.kt` 一个文件。
- [ ] 启动日志出现 `Voidkit 模块 23 / 23 已启用`（数量以实际注册为准，关键是**没有**「个失败」后缀）。
- [ ] 附带发现（卡外问题）记入 commit 正文，不要顺手改。

**禁止**：不要顺手改 `Feature.kt`（那是 T2）；不要给 `register` 加锁之外的逻辑；不要把 `LinkedHashMap` 换成其它类型（插入序要为命令注册顺序保持稳定）。

---

### T2 — Feature 生命周期状态机（审计 CORE-001 / 011 / 012）

**目标**：Feature 有显式状态与 enable/disable/destroy 钩子；注册表在 boot/startAll/reload 的每个转折点驱动状态迁移，失败即出列。

**文件**：
- 新增 `core/src/main/kotlin/org/voidkit/core/api/FeatureState.kt`
- 改 `core/src/main/kotlin/org/voidkit/core/api/Feature.kt`

**新增文件 `FeatureState.kt` 全文**：

```kotlin
package org.voidkit.core.api

/**
 * 功能模块的生命周期状态。
 *
 * 为什么需要显式状态：reload（`/vk reload`）会改配置开关，框架必须分清每个模块
 * 「从没启动过」和「正在运行」，才能安全地关掉一个刚刚还被启用的模块
 * （回收它的缓存/任务/监听），或为刚被打开的模块补一次启动。
 * 一个 `enabled: Boolean` 分不清这两件事，reload 也就只能「全员 onReload」——
 * 资源泄漏和双实例问题的根源（审计 CORE-001/011）。
 *
 * 状态迁移全部发生在服务器线程、且是同步的，所以不设 INITIALIZING 这类瞬时态。
 */
enum class FeatureState {
    /** 已构造，未启用。 */
    CREATED,

    /** 已启用，等待启动（onServerStart 尚未调用）。 */
    ENABLED,

    /** 运行中。 */
    RUNNING,

    /** 已停用并回收（destroy 已调用），等下一次启用。 */
    DISABLED,

    /** 启用/启动过程抛异常，模块不可用；可经 `/vk reload` 重试。 */
    FAILED,
}
```

**`Feature.kt` 修改点**（其余部分不动）：

1. `enabled` 的 setter 收紧为 `internal`（当前只有 `PluginRegistry.applyConfig` 写它，收紧后可防插件私改）：

```kotlin
    /** 由 [PluginRegistry] 按配置写入。 */
    var enabled: Boolean = false
        internal set
```

2. 在 `enabled` 字段之后新增 `state`：

```kotlin
    /**
     * 当前生命周期状态。由框架（PluginRegistry）驱动，模块只读。
     *
     * 模块在自己的回调里可以用它做防御，例如 `if (state != FeatureState.RUNNING) return`。
     */
    var state: FeatureState = FeatureState.CREATED
        internal set
```

3. 在 `onReload` 之后、`// ---------------- 平台无关事件（VEvent 抽象） ----------------` 之前，新增三个钩子：

```kotlin
    /** 模块被启用时调用一次（配置打开、进入 active 集合）。默认无动作。 */
    open fun onEnable(server: MinecraftServer) {}

    /**
     * 模块被停用时调用一次（配置关闭、离开 active 集合）。
     * 应停止周期任务、取消注册监听 —— 与 [destroy] 的分工：这个还可以用 server。
     */
    open fun onDisable(server: MinecraftServer) {}

    /** 资源回收：停用或框架卸载时调用，应清缓存、停线程、删引用。默认无动作。 */
    open fun destroy() {}
```

**注意**：`MinecraftServer` 已在文件里 import（`onServerStart` 等签名在用），不需要加 import。

**步骤**：
1. 新增 `FeatureState.kt`，按上面三点改 `Feature.kt`。
2. **先只做 T2 的前两步文件修改，不要动 PluginRegistry**（T2 的注册表驱动部分已在 T1 的目标文件里一并完成 —— 对，没看错：T1 的目标文件已经包含 `step`/`aboutToStart`/`startAll`/`reload` 的状态机驱动，它们依赖本任务新增的 `FeatureState` 与三个钩子。**所以 T1 和 T2 必须一起提交**，或者先做 T2 的文件、再整体替换 T1 的文件、一个 commit 交付）。

   ⚠️ 顺序修正：如果 T1 已单独提交并跑过验证，现在补 T2 的文件后**再跑一次**完整验证；如果还没提交 T1，把 T1+T2 放同一个 commit（message 写清两件事）。

3. `./build.sh build --continue -PmcVersion=1.21.1` —— 0 错误。
4. `tools/smoke-test.sh -PmcVersion=1.21.1` —— 全绿。所有现有模块都没有覆写新钩子，行为应与改动前完全一致（这就是本设计的兼容性目标）。

**验收**：
- [ ] 编译 0 错误；冒烟全绿（23 feature / 28 API / 19 断言）。
- [ ] `git diff --stat` 只有 `Feature.kt` + 新增 `FeatureState.kt`（若与 T1 同 commit 则含 `PluginRegistry.kt`）。
- [ ] 静态检查：`git grep -n "\.state = \|state = " -- '*/core/api/Feature.kt'` 应无结果（插件永远不直接写状态，只由框架的 `step` 驱动）。
- [ ] 附带发现记入 commit 正文。

**禁止**：不要给现有 23 个 Feature 批量加 `onEnable/onDisable/destroy` 覆写（默认无动作就是兼容）；不要改 `listensToEvents()`；不要在 `core.api` 里 import 任何加载器类。

---

### T3 — reload 按 diff 走生命周期（审计 CORE-002）

**说明**：T1 的目标文件里 `reload()` 已是按 diff 的三段式实现（见 T1 文件中 `reload` 的 KDoc 流程图）。**本任务没有独立代码改动**，是「验证 + 记录」任务：

**验证步骤**（在 main 线、T1+T2 完成后）：
1. 起开发服（或直接用 smoke-test 的服），开 RCON（`core/run/server.properties` 里 `enable-rcon=true`，参照仓库既有做法用极简 Python 客户端发命令 —— Gradle `runServer` 不转发 stdin，管道喂命令无效）。
2. 依次执行并记录输出：
   - `/vk features` → 记下模块数与开关；
   - 把某个**可安全开关**的模块在 `voidkit/core/config.json` 的 `features` 里改为 `false`（例如 `clearlag`）→ `/vk reload` → `/vk features` 确认它变 `[关]`，日志无异常；
   - 再改回 `true` → `/vk reload` → `/vk features` 确认回到 `[开]`，且该模块功能恢复（clearlag 的 `/clearlag` 命令仍在、周期清扫恢复）。
   - 保持启用的模块（如 `motd`）reload 后照常工作。
3. 若某模块 reload 后行为异常：**停下来报告**，把模块名、日志、复现步骤写进 issue，不要自行扩大改动。

**验收**：
- [ ] 上面三步的命令输出各截图/各抄一份，附到 commit 正文或 issue 评论。
- [ ] 「仅静态推理」的部分（如权限判定）明确标注，不写成「已验证」。

---

### T4 — 失败可见性 + 配置异常分级（审计 CORE-006 / 012 / 013 / 018）

**目标**：① 内核配置加载只吞 `Exception`（Error 照常抛出）；② 配置损坏时有明确的「安全模式」信号（日志 + `/vk status`）；③ `/vk features` 能标出 FAILED 模块。

**文件**：
- `core/src/main/kotlin/org/voidkit/core/VoidkitCore.kt`
- `core/src/main/kotlin/org/voidkit/core/feature/CoreFeature.kt`

#### 4a. `VoidkitCore.kt`

1. import 区已有 `org.voidkit.core.api.VoidkitLang` 等，无需新增。

2. `loadConfig()` 的 catch 收窄，并在方法开头复位安全模式标记：

```kotlin
        /** 由 [ServerLifecycle] 在服务端启动阶段调用（此时配置根目录已就绪）。 */
        fun loadConfig() {
            configSafeMode = false
            try {
                // ⚠️ 必须在改写文件**之前**留底 —— 旧版把各插件的配置都塞在这里，
                //    插件要靠这份留存把自己的初版配置从旧值生成出来（见 LegacyConfig）。
                LegacyConfig.capture()

                config = ConfigFiles.load(CORE_FILE, VoidkitConfig::class.java, ConfigComments.CORE) { VoidkitConfig() }
                // Gson 走 Unsafe 分配对象，Kotlin 的默认参数不生效 —— 加载后必须补一遍
                config.normalize()
                // 语言要在任何消息产生之前定下来
                VoidkitLang.setLocale(config.language)
                configFile = ConfigFiles.resolve(CORE_FILE)
                saveConfig()
                LOG.info("内核配置就绪：{}", configFile)
            } catch (e: Exception) {
                // 只吞 Exception：OutOfMemoryError / StackOverflowError 是 JVM 级故障，
                // 吞掉只会带着坏状态继续跑，必须照原样抛出、崩在明处（审计 CORE-018）。
                //
                // 配置损坏时**不阻断启动**：产品立场是「服务器必须能起来」，默认值都调过；
                // 但必须用 ERROR 级明确告知正在用默认配置，否则管理员会以为自己改的配置
                // 生效了（审计 CORE-006）。
                LOG.error(
                    "内核配置加载失败，本轮以默认配置运行（安全模式）——请检查 {} 是否为合法 JSONC：{}",
                    CORE_FILE, e.message
                )
                configSafeMode = true
            }
        }
```

3. `companion object` 里 `var config: VoidkitConfig` 之后新增：

```kotlin
        /**
         * 内核配置是否处于「安全模式」—— 最近一次加载失败、正在用默认值运行。
         *
         * `/vk status` 会显示它。加载成功时复位（见 [loadConfig]）。
         */
        var configSafeMode: Boolean = false
            private set
```

#### 4b. `CoreFeature.kt`

1. import 区（第 12 行 `import org.voidkit.core.api.Feature` 之后）加一行：

```kotlin
import org.voidkit.core.api.FeatureState
```

2. `features()`（当前约第 246 行）改为：

```kotlin
    private fun features(source: CommandSourceStack): Int {
        Msg.info(source, "===== 功能模块（${PluginRegistry.active().size}/${PluginRegistry.all().size}） =====")
        PluginRegistry.all().forEach { feature ->
            val mark = when {
                feature.state == FeatureState.FAILED -> "&c[失败]"
                feature.enabled -> "&a[开]"
                else -> "&7[关]"
            }
            val line = "$mark &f${feature.id} &7- ${feature.displayName}"
            Msg.text(source, line)
        }
        return 1
    }
```

3. `status()` 里「模块 x/y 已启用」那一行之后加一段安全模式提示（`DataStore` 那行之后、`return 1` 之前）：

```kotlin
        if (VoidkitCore.configSafeMode) {
            Msg.err(source, "⚠ 内核配置加载失败，正在以默认配置运行（安全模式）——请检查 voidkit/core/config.json")
        }
```

**步骤**：按 4a / 4b 改完 → `./build.sh build --continue -PmcVersion=1.21.1` → `tools/smoke-test.sh -PmcVersion=1.21.1` 全绿。

**验收**：
- [ ] 编译 0 错误；冒烟全绿。
- [ ] 静态检查：`git grep -n "catch (t: Throwable)\|catch(Throwable)" -- core/src/main/kotlin/org/voidkit/core/VoidkitCore.kt` 无结果（已被 `catch (e: Exception)` 取代）。
- [ ] `git diff --stat` 只有 `VoidkitCore.kt` + `CoreFeature.kt`。
- [ ] 手动验证（有条件时）：故意把 `voidkit/core/config.json` 写坏 → 启动 → 日志有 ERROR「安全模式」、服务器照常起来、`/vk status` 有红字提示；恢复文件后 `/vk reload` 提示消失。无条件就在验收里写「仅静态推理 + 冒烟覆盖默认路径」。

**禁止**：不要因为「安全模式」去加启动阻断逻辑；不要改 `ConfigFiles.load` 的行为；不要在新的 catch 里吞 Error。

---

### T5 — PlayerProfile 并发残留（审计 CORE-015）

**目标**：`permissions` 换成并发容器；`recordIp` / `addKnownName` 的 check-then-act 加锁。与类 KDoc 的承诺（集合字段都是并发容器）对齐。

**文件**：`core/src/main/kotlin/org/voidkit/core/data/PlayerProfile.kt`

1. 第 54 行：

```kotlin
    /** 附加权限节点，独立于分组。 */
    var permissions: MutableList<String> = CopyOnWriteArrayList()
```

2. `normalize()` 里对应行（当前约第 78 行）：

```kotlin
        if (permissions == null) permissions = CopyOnWriteArrayList()
```

3. `recordIp` 整体替换为（把「查存在 → 否则新增」变成原子操作；两个线程同时给同一玩家记同一个 IP 时不再可能添加两条重复记录）：

```kotlin
    /** 记录一次 IP 访问，返回对应记录。 */
    fun recordIp(ip: String?, name: String?, now: Long): IpRecord? {
        if (ip.isNullOrBlank()) return null
        // check-then-act 必须整体锁住：Web API 线程（面板查询触发）与命令线程会并发调它
        synchronized(ips) {
            val existing = ips.firstOrNull { it.ip == ip }
            if (existing != null) {
                existing.touch(name, now)
                return existing
            }
            val record = IpRecord(ip, now, now, 1)
            if (!name.isNullOrBlank()) record.names.add(name)
            ips.add(record)
            return record
        }
    }
```

4. `addKnownName` 整体替换为：

```kotlin
    fun addKnownName(name: String?) {
        if (name.isNullOrBlank() || name == lastName) return
        synchronized(knownNames) {
            if (!knownNames.contains(name)) knownNames.add(name)
        }
    }
```

5. 文件头 KDoc 第 9-12 行那段「集合字段刻意用并发容器」的说明后面补一句原因说明不需要改；但要确认 `java.util.concurrent.CopyOnWriteArrayList` 已在 import（当前文件第 4 行有，`knownNames` 在用）—— 无需新增 import。

**步骤**：改完 → build → smoke-test 全绿。

**验收**：
- [ ] 编译 0 错误；冒烟全绿（玩家档案读写被 28 个 API 覆盖）。
- [ ] `git diff --stat` 只有 `PlayerProfile.kt`。
- [ ] 静态检查：`git grep -n "mutableListOf" -- core/src/main/kotlin/org/voidkit/core/data/PlayerProfile.kt` 无结果。

**禁止**：不要把 `homes`（已是 ConcurrentHashMap）也包 synchronized（没必要，且可能死缠）；不要改 `lastName` 等标量字段的写法（last-write-wins 的良性竞争，改了反而引入锁序问题）。

---

### T6 — 修正两处过时的数据路径注释

**目标**：KDoc 与真实路径对齐（实际是 `<服务端根>/voidkit/core/data/`，见 `ServerLifecycle` 的 `ROOT_DIR_NAME` 与 `DataStore.init(root.resolve("core").resolve("data"))`）。纯注释，零行为变更。

**文件**：
- `core/src/main/kotlin/org/voidkit/core/data/DataStore.kt` —— 类 KDoc 的目录树改为：

```
 * 目录结构（位于**服务端根目录**下，不放存档里 —— 删存档不丢玩家数据）：
 * ```
 * <服务端根>/voidkit/core/data/
 *   ├── players/<uuid>.json     单个玩家档案
 *   ├── global.json             传送点、出生点、白名单、封禁
 *   └── logs/joins.jsonl        上下线原始事件流
 * ```
```

（原文档写的是「位于存档根目录下」`<world>/voidkit/`，是 `world/kokuucore` 时代的遗留。）

- `core/src/main/kotlin/org/voidkit/core/data/PlayerProfile.kt` —— 类 KDoc 第 7 行 `voidkit/players/<uuid>.json` 改为 `voidkit/core/data/players/<uuid>.json`。

**步骤**：改完 → build（确认没碰坏代码）→ smoke-test 可跳过（纯注释变更，但如果你顺手跑了也没坏处）。

**验收**：`git diff` 只有注释行；`git diff --stat` 两个文件、行数为个位数。

---

## 6. 已知限制（本轮明确不修，写进文档备查）

1. **启动时被关掉的模块，经 `/vk reload` 打开后命令不会出现**（Brigadier 无注销语义，重注册会撞名）。
   表现：功能本身起来了（周期任务、API 都在），但命令要等重启。修复需要「命令按 enabled 门控」式重构，超出本轮范围。
2. **`unregister()` 不存在**：模块进 `all` 后伴生注册表终身。MC mod 生命周期=服务端生命周期，无实际危害。
3. **事件分发顺序 = 注册顺序**，无优先级。当前 VEvent 消费方之间无顺序依赖；将来有时再加，加的理由写进注释。
4. **FAILED 模块的恢复路径是 `/vk reload`**（自动重试）或重启；没有单独的 `/vk retry`。
5. **`configSafeMode` 只在 `/vk status` 可见**，不进面板 API（避免为它扩契约）。

## 7. 整轮完成定义（DoD）

- [ ] T1–T6 六个 commit 在 `main` 上，顺序清晰、message 双语合规。
- [ ] main 线：`build` + `smoke-test` 全绿（23 feature / 28 API / 19 断言）。
- [ ] `mc/1.20.1`：cherry-pick 后 `build` + `smoke-test` + `e2e-forge-server.sh` 全绿。
- [ ] `feat/fabric`：cherry-pick 后 `build` 通过（行为验证归 PORTING-FABRIC 阶段 3）。
- [ ] 三条线 push 完成；关联 issue 评论汇总各线验证结果后关闭。
