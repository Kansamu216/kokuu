package org.voidkit.core.platform

import org.voidkit.core.platform.fabric.FabricPlatform

/**
 * 当前加载器适配层的持有者。
 *
 * 默认指向 Fabric 实现（本分支是 Fabric 线）；将来若在别处复用这套代码，
 * 在对应加载器的内核入口里调用 [install] 换成别的实现即可，业务代码感知不到差异。
 */
object Platforms {

    @Volatile
    var current: Platform = FabricPlatform
        private set

    /** 供加载器入口调用，替换适配实现。 */
    fun install(platform: Platform) {
        current = platform
    }
}
