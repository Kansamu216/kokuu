package org.voidkit.core.platform

import org.voidkit.core.platform.forge.ForgePlatform

/**
 * 当前加载器适配层的持有者。
 *
 * 本分支（mc/1.21.1-forge）指向 Forge 实现；NeoForge 线用 NeoForgePlatform。
 * 业务代码只依赖 [Platform] 接口，感知不到差异。
 */
object Platforms {

    @Volatile
    var current: Platform = ForgePlatform
        private set

    /** 供未来加载器入口调用，替换适配实现。 */
    fun install(platform: Platform) {
        current = platform
    }
}
