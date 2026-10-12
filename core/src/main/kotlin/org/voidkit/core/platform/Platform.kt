package org.voidkit.core.platform

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.nio.file.Path

/**
 * 加载器适配层的统一契约。
 *
 * ## 为什么需要这一层
 *
 * Voidkit 的目标是「一份业务逻辑跑在多个加载器上」（NeoForge / Forge / Fabric）。
 * 三个平台真正不一样的东西，**不是** `net.minecraft.*` 那些原版类——它们走 Mojang
 * 官方映射，在三个平台是同包同名的，可以自由使用——而是「加载器自己的 API」：
 *
 * | 差异点 | NeoForge | Forge | Fabric |
 * |---|---|---|---|
 * | 事件总线 | `net.neoforged.bus` | `net.minecraftforge.eventbus` | 无，用回调 |
 * | 假玩家类 | `neoforged...FakePlayer` | `minecraftforge...FakePlayer` | 无官方类 |
 * | 游戏目录 | `FMLPaths.GAMEDIR` | 同左 | `FabricLoader` |
 * | mod 入口 | `@Mod` | `@Mod` | `ModInitializer` |
 *
 * 这些「每个平台都不一样」的能力全部收口到本接口。业务代码与插件只依赖本接口，
 * 不 import 任何 `net.neoforged.*` / `net.minecraftforge.*` / `net.fabricmc.*`。
 *
 * ## 移植成本
 *
 * 移植到新加载器 = 只实现一份本接口（`platform/forge/`、`platform/fabric/`），
 * `core.api` 契约与全部插件零改动。
 */
interface Platform {

    /** 加载器标识：`neoforge` / `forge` / `fabric`。 */
    val id: String

    /** 服务端游戏根目录（替代 NeoForge 的 `FMLPaths.GAMEDIR`）。 */
    fun gameDir(): Path

    /**
     * 判断一个实体是不是「假玩家」（机械臂、管道、自动合成等自动化代理驱动的操作）。
     *
     * 这是审计模块「自动化过滤」的核心判据：科技整合包里绝大多数方块/容器变化
     * 都是假玩家干的，要能挡在门外。三平台判定方式不同，故收口到这里。
     */
    fun isFakePlayer(entity: Entity): Boolean

    /**
     * 判断「有玩家身份但无真实网络连接」的模组自造假玩家。
     *
     * 与 [isFakePlayer] 是两条互补判据：有的模组不继承官方的 FakePlayer 类，
     * 而是自己 new 一个 ServerPlayer 但没接网络。这个特征在三个平台一致
     * （`connection == null`），但保留在这里是为了和 FakePlayer 判定成对收口。
     */
    fun isUnconnectedPlayer(player: Player): Boolean

    /** 把一个带事件监听方法（`@SubscribeEvent`）的对象注册到加载器事件总线。 */
    fun registerListener(listener: Any)

    /** 从事件总线注销。 */
    fun unregisterListener(listener: Any)
}
