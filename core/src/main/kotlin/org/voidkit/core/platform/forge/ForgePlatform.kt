package org.voidkit.core.platform.forge

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.loading.FMLPaths
import org.voidkit.core.api.ServerLifecycleListener
import org.voidkit.core.platform.Platform
import java.nio.file.Path

/**
 * Forge 平台的适配实现（Minecraft 1.21.1 / Forge 52.x）。
 *
 * 这里是本仓库**唯一允许直接 import `net.minecraftforge.*` 的地方之一**
 * （连同 `@Mod` 入口与生命周期桥）。其余代码要么走 [Platform] 接口，
 * 要么用 `net.minecraft.*` 原版类（各加载器同名）。
 *
 * ## 与 NeoForge 1.21.1 的差异（移植要点）
 *
 * | 能力 | NeoForge | Forge 1.21.1 |
 * |---|---|---|
 * | 事件总线 | `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
 * | 游戏目录 | `FMLPaths.GAMEDIR` | **同名**（`net.minecraftforge.fml.loading.FMLPaths`） |
 * | 假玩家类 | `net.neoforged.neoforge.common.util.FakePlayer` | **已移除**（见下） |
 *
 * ## ⚠️ Forge 1.21.1 没有 `FakePlayer` 类
 *
 * 实测（javap 1.21.1-52.1.19 的 universal jar）：整个 jar 里**不存在**任何
 * `*FakePlayer*` 类 —— Forge 已移除该 API（NeoForge 保留并扩展了它）。
 * 因此 [isFakePlayer] 退化为「有玩家身份但无网络连接」这一条判据（与
 * [isUnconnectedPlayer] 同一实现）。这与科技整合包的现实相符：绝大多数
 * 自动化假玩家都是「ServerPlayer 但 connection == null」的形态。
 * 若将来发现漏判，在此处补更多启发式（如名字前缀、已知模组标记）。
 */
object ForgePlatform : Platform {

    override val id: String = "forge"

    override fun gameDir(): Path = FMLPaths.GAMEDIR.get()

    override fun isFakePlayer(entity: Entity): Boolean = isUnconnectedPlayer(entity as? Player ?: return false)

    override fun isUnconnectedPlayer(player: Player): Boolean =
        player is ServerPlayer && player.connection == null

    override fun registerListener(listener: Any) {
        MinecraftForge.EVENT_BUS.register(listener)
    }

    override fun unregisterListener(listener: Any) {
        MinecraftForge.EVENT_BUS.unregister(listener)
    }

    override fun registerLifecycle(listener: ServerLifecycleListener) {
        MinecraftForge.EVENT_BUS.register(ForgeLifecycleBridge(listener))
    }
}
