package org.voidkit.core

import net.fabricmc.api.ModInitializer

/**
 * Fabric 入口 —— 对应 NeoForge 线的 `@Mod class VoidkitCore`。
 *
 * 只做一件事：把初始化交给平台无关的 [VoidkitCore.bootstrap]。
 * 入口之所以这么薄，是因为内核逻辑全部在 [VoidkitCore] 里，
 * 换加载器时只需要换这个壳（`core` 包本身不 import 任何加载器类）。
 */
class VoidkitCoreFabric : ModInitializer {

    override fun onInitialize() {
        VoidkitCore.bootstrap()
    }
}
