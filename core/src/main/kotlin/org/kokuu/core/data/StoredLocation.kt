package org.kokuu.core.data

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import kotlin.math.roundToInt

/**
 * 可序列化的坐标（维度 + 位置 + 朝向），用于 home / warp / spawn / back。
 */
class StoredLocation(
    var dim: String = Level.OVERWORLD.location().toString(),
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var yaw: Float = 0f,
    var pitch: Float = 0f
) {

    companion object {

        fun of(player: ServerPlayer): StoredLocation = StoredLocation(
            player.level().dimension().location().toString(),
            player.x, player.y, player.z,
            player.yRot, player.xRot
        )

        fun of(level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float = 0f, pitch: Float = 0f) =
            StoredLocation(level.dimension().location().toString(), x, y, z, yaw, pitch)
    }

    fun dimensionKey(): ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim))

    fun resolve(server: MinecraftServer?): ServerLevel? {
        if (server == null) return null
        return runCatching { server.getLevel(dimensionKey()) }.getOrNull()
    }

    fun shortDim(): String = dim.substringAfter(':', dim)

    fun display(): String = "${shortDim()} ${x.roundToInt()}, ${y.roundToInt()}, ${z.roundToInt()}"

    override fun toString(): String = display()
}
