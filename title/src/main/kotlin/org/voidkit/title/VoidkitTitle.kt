package org.voidkit.title

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.placeholder.PlaceholderRegistry

/**
 * voidkit_title —— 称号插件。
 *
 * 称号的「谁拥有哪一个」不单独存一份名单，而是直接复用权限节点
 * （`voidkit.title.vip` 之类）—— 发权限和发称号是同一个动作，不会出现
 * 「给了权限忘了给称号 / 撤了权限忘撤称号」这种经典错位。
 *
 * 因此本插件不硬依赖 voidkit_perm：没装权限插件时，内核会用「只认 OP」的兜底实现，
 * 那时所有称号只对管理员可见 —— 能用，只是不精细。
 */
@Mod(VoidkitTitle.MODID)
class VoidkitTitle(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(TitleFeature(), MODID)

        // %title% 由本插件自己注册 —— 内核的变量表里没有这一项
        PlaceholderRegistry.register("title") { ctx ->
            // 返回带 & 颜色码的原始显示文本，交给调用方的文本解析器去上色；
            // 自定义称号也走同一个入口（activeTitle 内部会优先返回自定义称号）
            ctx.player?.let { TitleFeature.activeTitle(it)?.display }
        }

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            TitleRegistry.load()
        }
    }

    companion object {
        const val MODID = "voidkit_title"
    }
}
