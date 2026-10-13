package org.voidkit.essentials

import net.fabricmc.api.ModInitializer
import org.voidkit.core.api.ConfigSnapshots
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.core.PluginRegistry
import org.voidkit.economy.VoidkitEconomy
import org.voidkit.perm.VoidkitPerm

/**
 * voidkit_essentials —— 基础指令插件（**Fabric 入口**）。
 *
 * 九块内容，覆盖「绝大多数服都需要」的那一类：
 *
 * | 模块 id | 内容 |
 * |---|---|
 * | `home` / `warp` / `spawn` | 家 / 公共传送点 / 出生点 |
 * | `back` | 回到死亡点或上次传送前位置 |
 * | `tpa` | 玩家互传（请求自带可点的接受/拒绝按钮） |
 * | `teleport` | 管理员传送（tphere / tpall / tpo） |
 * | `admin-tools` | fly / god / heal / feed / speed / vanish / hat / enderchest / invsee … |
 * | `info` | 玩家信息、服务器信息查询 |
 * | `command-alias` | 原版命令接管：时间 / 天气 / 游戏规则 / 难度 / 游戏模式 |
 *
 * 本 jar 还内含原来的 **perm**（权限）与 **economy**（经济）—— 真源码合并，
 * 一个 jar 只声明一个 mod id（[VoidkitPerm] / [VoidkitEconomy] 不再是独立 mod，
 * 由这里显式实例化）。
 *
 * ## 为什么原版命令简写在这里
 *
 * 它本质也是"命令"，和 `/home` `/fly` 是同一类东西，没理由单独占一个 jar。
 * 而它依然是一个**独立可关的模块** —— 大型整合包里 `/day` 这种短名字经常被别的
 * mod 抢注，那时把 `command-alias` 关掉（或在它自己的配置里逐条停用）就行，
 * 不影响其它指令。
 *
 * ## 与 NeoForge 线的差别（入口写法 + 配置加载时机）
 *
 * 入口：`@Mod(...) class VoidkitEssentials(bus, container)` →
 * `class VoidkitEssentialsFabric : ModInitializer`。
 *
 * 配置加载：**不再监听加载器事件**，改用内核的 [PluginRegistry.onBoot]。
 * 原因是顺序 —— NeoForge 线靠 `EventPriority.HIGHEST` 把内核排到最前，而 Fabric
 * **没有事件优先级**，实测（2026-10-13）插件 entrypoint 会跑在内核之前，
 * 读配置时配置根目录还没建好，直接崩在 `ConfigFiles.resolve`。
 *
 * 初始化期只做纯内存登记（见 [PluginRegistry] 的说明），不读任何文件。
 */
class VoidkitEssentialsFabric : ModInitializer {

    override fun onInitialize() {
        // 把家上限的实现交出去 —— 权限实现（原 perm，已并入本 jar）要按权限组算家数，
        // 需要这个默认值。内核只认 HomeLimits 接口，不认识本插件的配置类。
        VoidkitServices.installHomeLimits(EssentialsStore)

        // 公开本插件的配置快照 —— 面板就能显示，而 web 插件不必 import 本插件
        ConfigSnapshots.register("essentials") {
            val c = EssentialsStore.config
            mapOf(
                "defaultHomeLimit" to c.defaultHomeLimit,
                "maxHomes" to c.maxHomes,
                "tpaEnabled" to c.tpaEnabled,
                "tpaTimeoutSeconds" to c.tpaTimeoutSeconds,
                "backEnabled" to c.backEnabled,
                "teleportCooldownSeconds" to c.teleportCooldownSeconds,
                "commands.disabled" to CommandsStore.config.disabled,
            )
        }

        PluginRegistry.register(HomeFeature(), MODID)
        PluginRegistry.register(WarpFeature(), MODID)
        PluginRegistry.register(SpawnFeature(), MODID)
        PluginRegistry.register(BackFeature(), MODID)
        PluginRegistry.register(TpaFeature(), MODID)
        PluginRegistry.register(TeleportFeature(), MODID)
        PluginRegistry.register(AdminToolsFeature(), MODID)
        PluginRegistry.register(InfoFeature(), MODID)
        PluginRegistry.register(CommandAliasFeature(), MODID)

        // 合并进来的 perm / economy：它们不再是独立 mod（一 jar 一 modId），
        // 由宿主在这里显式跑初始化。二者都是纯内存登记 + 挂启动事件，无构造期 IO。
        // owner 标签仍用各自原 modId（见 VoidkitPerm / VoidkitEconomy），展示归组不变。
        VoidkitPerm()
        VoidkitEconomy()

        // 配置在服务端启动阶段读 —— 初始化期保持零 IO。
        //
        // ⚠️ 用内核的 [PluginRegistry.onBoot]，**不要**直接监听加载器的「服务端即将启动」事件：
        //    那个事件的回调顺序不由我们控制（Fabric 没有事件优先级，实测插件 entrypoint 会跑在
        //    内核之前 → 读配置时 ConfigFiles 还没初始化，服务端直接崩）。
        PluginRegistry.onBoot(MODID) {
            EssentialsStore.load()
            CommandsStore.load()
        }
    }

    companion object {
        const val MODID = "voidkit_essentials"
    }
}
