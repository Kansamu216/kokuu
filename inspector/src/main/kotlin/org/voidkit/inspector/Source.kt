package org.voidkit.inspector

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.voidkit.core.platform.Platforms

/**
 * 事件「是谁干的」的分类。
 *
 * 这是整个审计模块对付科技整合包的核心：**绝大多数方块/容器变化不是玩家干的，
 * 是机械臂、管道、自动合成干的**。要能查得到违规行为，先得把这些自动化来源认出来
 * 并挡在门外。
 */
enum class SourceKind(val id: Int) {
    PLAYER(0),      // 真人玩家
    AUTOMATION(1),  // 假玩家 / 机械臂 / 管道 / 方块实体驱动的操作
    NATURAL(2),     // 树叶腐烂 / 流体 / 爆炸 / 生物 / 重力方块
    OTHER(3);       // 无法归类

    companion object {
        fun of(id: Int): SourceKind = entries.getOrElse(id) { OTHER }
    }
}

object Source {

    /**
     * 判断一个 [Player] 是不是「真人」。
     *
     * 判据很关键，顺序不能反，且**已收口到 platform 层**（三平台判定方式不同）：
     *  1. 先判官方假玩家类（NeoForge/Forge 的 FakePlayer / Fabric 无官方类）；
     *  2. 其次「ServerPlayer 但没网络连接」—— 模组自造的假玩家常有这条特征。
     *
     * ⚠️ 不能用 `connection == null` 当唯一判据：官方 FakePlayer 自己带了一个
     * FakeConnection，connection 不是 null，必须先用 [Platform.isFakePlayer] 拦。
     */
    fun kindOfPlayer(p: Player?): SourceKind = when {
        p == null -> SourceKind.NATURAL
        Platforms.current.isFakePlayer(p) -> SourceKind.AUTOMATION
        Platforms.current.isUnconnectedPlayer(p) -> SourceKind.AUTOMATION
        else -> SourceKind.PLAYER
    }

    /** 对任意实体分类（放置方块的实体、掉落来源等）。 */
    fun kindOfEntity(e: Entity?): SourceKind = when (e) {
        null -> SourceKind.NATURAL
        is Player -> kindOfPlayer(e)
        else -> SourceKind.NATURAL
    }

    /** 真人玩家名。 */
    fun playerName(p: Player): String = p.gameProfile.name

    /** 玩家的 UUID 小写去横线；非玩家返回 null。 */
    fun playerUuid(p: Player): String? =
        if (kindOfPlayer(p) == SourceKind.PLAYER) p.uuid.toString().replace("-", "") else null

    /** 自然来源名：`#creeper` `#tnt` `#enderman` … */
    fun naturalName(e: Entity): String {
        val key = BuiltInRegistries.ENTITY_TYPE.getKey(e.type)
        return "#" + (key?.path ?: "unknown")
    }

    /**
     * 自动化来源名：优先取方块实体的命名空间（= 模组 id），如 `#create` `#mekanism` `#ae2`。
     *
     * 这样管理员能一眼看出「这一片是机械动力的机器在动」，而不是笼统的 `#automation`。
     */
    fun automationName(level: Level?, pos: BlockPos?): String {
        val be = level?.getBlockEntity(pos ?: return "#automation") ?: return "#automation"
        val key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.type) ?: return "#automation"
        return "#" + key.namespace
    }
}
