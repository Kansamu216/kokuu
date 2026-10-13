package org.voidkit.core.platform.fabric

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.voidkit.core.api.ServerLifecycleListener
import org.voidkit.core.platform.Platform
import java.nio.file.Path

/**
 * Fabric 平台的适配实现。
 *
 * 这里是本分支**唯一允许直接 import `net.fabricmc.*` 的地方之一**（连同
 * `ModInitializer` 入口 `VoidkitCoreFabric` 与几个桥类）。其余代码要么走 [Platform]
 * 接口，要么用 `net.minecraft.*` 原版类（三平台同包同名）。
 */
object FabricPlatform : Platform {

    override val id: String = "fabric"

    /** Fabric 的游戏目录由 Loader 提供（对应 NeoForge 的 `FMLPaths.GAMEDIR`）。 */
    override fun gameDir(): Path = FabricLoader.getInstance().gameDir

    /**
     * ⚠️ Fabric **没有官方的 FakePlayer 类**（`fabric-api` 里不存在，1.20.1/1.21.1 都确认过）。
     *
     * 所以这里恒为 `false`：自动化代理的判定在 Fabric 上会退化为
     * 「只靠 [isUnconnectedPlayer] 那一条」，也就是「有玩家身份但没有网络连接」。
     * 真正受影响的是独立仓库里的审计模块（`voidkit-inspector`），本套件内无人调用这个方法。
     */
    override fun isFakePlayer(entity: Entity): Boolean = false

    override fun isUnconnectedPlayer(player: Player): Boolean =
        player is ServerPlayer && player.connection == null

    /**
     * 空实现 —— VEvent 抽象落地后，已没有任何 Feature 依赖**加载器原生**事件总线，
     * 业务事件统一由 [FabricEventBridge] 翻译后经 `PluginRegistry.dispatch` 分发。
     * 保留这两个方法是契约对齐（[Platform] 接口要求），不是遗漏。
     */
    override fun registerListener(listener: Any) {}

    override fun unregisterListener(listener: Any) {}

    override fun registerLifecycle(listener: ServerLifecycleListener) {
        FabricLifecycleBridge(listener)
    }

    override fun modVersion(modId: String): String? =
        FabricLoader.getInstance().getModContainer(modId)
            .map { it.metadata.version.friendlyString }
            .orElse(null)
}
