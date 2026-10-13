package org.voidkit.title

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerAboutToStartEvent
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
// ⚠️ 1.20.1（Forge）的 mod 构造器**只认两种签名**：`()` 和 `(FMLJavaModLoadingContext)`
//    （见 FMLModContainer.constructMod）；`(IEventBus, ModContainer)` 是 NeoForge 1.21 的写法，
//    在 1.20.1 上会直接报 NoSuchMethodException 拒绝加载。
//    本套件不使用 mod 事件总线（游戏事件统一走 MinecraftForge.EVENT_BUS，见 Platforms.registerListener），
//    因此这里用无参构造 —— 两条版本线语义一致。
@Mod(VoidkitTitle.MODID)
class VoidkitTitle {

    init {
        PluginRegistry.register(TitleFeature(), MODID)

        // %title% 由本插件自己注册 —— 内核的变量表里没有这一项
        PlaceholderRegistry.register("title") { ctx ->
            // 返回带 & 颜色码的原始显示文本，交给调用方的文本解析器去上色；
            // 自定义称号也走同一个入口（activeTitle 内部会优先返回自定义称号）。
            // ⚠️ 没戴称号时返回**空串**（不是 null）—— 返回 null 会让占位符原样保留，
            // 聊天/记分牌里就会残留一个字面的 `%title%`。
            ctx.player?.let { TitleFeature.activeTitle(it)?.display } ?: ""
        }

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        MinecraftForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            TitleRegistry.load()
        }
    }

    companion object {
        const val MODID = "voidkit_title"
    }
}
