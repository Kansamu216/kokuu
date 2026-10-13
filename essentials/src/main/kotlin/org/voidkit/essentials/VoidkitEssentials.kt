package org.voidkit.essentials

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.api.ConfigSnapshots
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.core.PluginRegistry
import org.voidkit.economy.VoidkitEconomy
import org.voidkit.perm.VoidkitPerm

/**
 * voidkit_essentials —— 基础指令插件。
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
 * ## 为什么原版命令简写在这里
 *
 * 它本质也是"命令"，和 `/home` `/fly` 是同一类东西，没理由单独占一个 jar。
 * 而它依然是一个**独立可关的模块** —— 大型整合包里 `/day` 这种短名字经常被别的
 * mod 抢注，那时把 `command-alias` 关掉（或在它自己的配置里逐条停用）就行，
 * 不影响其它指令。
 *
 * 构造期只做纯内存登记（见 [PluginRegistry] 的说明），不读任何文件。
 */
@Mod(VoidkitEssentials.MODID)
class VoidkitEssentials(bus: IEventBus, container: ModContainer) {

    init {
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

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            EssentialsStore.load()
            CommandsStore.load()
        }
    }

    companion object {
        const val MODID = "voidkit_essentials"
    }
}
