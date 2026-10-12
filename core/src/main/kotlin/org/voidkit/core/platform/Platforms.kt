package org.voidkit.core.platform

import org.voidkit.core.platform.neoforge.NeoForgePlatform

/**
 * 当前加载器适配层的持有者。
 *
 * 默认指向 NeoForge 实现；将来移植 Forge / Fabric 时，在对应加载器的
 * 内核入口里换成对应的实现即可，业务代码感知不到差异。
 */
object Platforms {

    @Volatile
    var current: Platform = NeoForgePlatform
        private set

    /** 供未来加载器入口调用，替换适配实现。 */
    fun install(platform: Platform) {
        current = platform
    }
}
