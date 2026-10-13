package org.voidkit.title

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.placeholder.PlaceholderRegistry

/**
 * voidkit_title —— 称号实现。
 *
 * ## 合并说明（8 jar -> 5 jar，2026-10-13）
 *
 * 本模块的代码已并入 **voidkit_display** 的 jar。为满足「**一个 jar 只声明一个 mod id**」
 * （Fabric 线的 `fabric.mod.json` 一个 jar 只能有一个 mod id），它**不再是独立的 `@Mod`** ——
 * 初始化改由 [org.voidkit.display.VoidkitDisplay] 在构造期显式触发。
 *
 * 合并**不影响**：数据路径仍是 `voidkit/title/`（字面量常量，与 modId 无关）；
 * owner 标签仍用 [MODID]，展示 / 归组不变。
 *
 * ## 它为什么不需要一份「谁拥有称号」的名单
 *
 * 称号的归属直接复用权限节点（`voidkit.title.vip` 之类）—— 发权限和发称号是同一个动作，
 * 不会出现「给了权限忘了给称号 / 撤了权限忘撤称号」这种经典错位。
 *
 * 因此它不硬依赖权限实现：没有权限实现时，内核会用「只认 OP」的兜底实现，
 * 那时所有称号只对管理员可见 —— 能用，只是不精细。
 */
class VoidkitTitle {

    init {
        PluginRegistry.register(TitleFeature(), MODID)

        // %title% 由本模块自己注册 —— 内核的变量表里没有这一项
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
        /** 保留原 modId 字符串，仅用作 PluginRegistry 的 owner 归组标签（不再是 FML 的 mod id）。 */
        const val MODID = "voidkit_title"
    }
}
