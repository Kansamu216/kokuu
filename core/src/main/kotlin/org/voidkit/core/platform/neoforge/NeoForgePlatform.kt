package org.voidkit.core.platform.neoforge

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayer
import org.voidkit.core.api.ServerLifecycleListener
import org.voidkit.core.platform.Platform
import java.nio.file.Path

/**
 * NeoForge 平台的适配实现。
 *
 * 这里是全仓库**唯一允许直接 import `net.neoforged.*` 的地方之一**（连同
 * `@Mod` 入口和生命周期监听）。其余代码要么走 [Platform] 接口，要么用
 * `net.minecraft.*` 原版类（三平台同名）。
 */
object NeoForgePlatform : Platform {

    override val id: String = "neoforge"

    override fun gameDir(): Path = FMLPaths.GAMEDIR.get()

    override fun isFakePlayer(entity: Entity): Boolean = entity is FakePlayer

    override fun isUnconnectedPlayer(player: Player): Boolean =
        player is ServerPlayer && player.connection == null

    override fun registerListener(listener: Any) {
        NeoForge.EVENT_BUS.register(listener)
    }

    override fun unregisterListener(listener: Any) {
        NeoForge.EVENT_BUS.unregister(listener)
    }

    override fun registerLifecycle(listener: ServerLifecycleListener) {
        NeoForge.EVENT_BUS.register(NeoForgeLifecycleBridge(listener))
    }
}
