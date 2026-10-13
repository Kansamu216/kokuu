package org.voidkit.core.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.voidkit.core.api.VItems

/**
 * 实体模块：可复用的实体查询与批量操作。
 *
 * 清扫、实体统计、批量清理之类的需求都应该走这里，而不是各自写一套遍历逻辑。
 * 后续要做“实体模块”（如刷怪控制、实体统计）时，在这里扩展即可。
 */
object EntityTools {

    /** 一整块世界范围内的实体快照。 */
    fun allEntities(level: ServerLevel): List<Entity> = level.allEntities.toList()

    /** 以某点为中心、指定半径内的实体。 */
    fun entitiesNear(level: ServerLevel, center: Vec3, radius: Double): List<Entity> {
        val box = AABB(
            center.x - radius, center.y - radius, center.z - radius,
            center.x + radius, center.y + radius, center.z + radius
        )
        return level.getEntitiesOfClass(Entity::class.java, box) { true }
    }

    /** 指定类型的实体。box 传 null 表示“整个维度”。 */
    fun <T : Entity> entitiesOfClass(level: ServerLevel, type: Class<T>, box: AABB? = null): List<T> =
        if (box == null) {
            // ServerLevel 没有单参数的 getEntitiesOfClass，整维度检索直接筛全量快照
            level.allEntities.filterIsInstance(type)
        } else {
            level.getEntitiesOfClass(type, box) { true }
        }

    /** 立即移除一批实体，返回实际移除数量。 */
    fun remove(entities: Collection<Entity>): Int {
        var count = 0
        entities.forEach { entity ->
            if (!entity.isRemoved) {
                entity.discard()
                count++
            }
        }
        return count
    }

    /** 按类型统计当前世界的实体数量（用于面板）。 */
    fun countByCategory(level: ServerLevel): Map<String, Int> {
        val counters = HashMap<String, Int>()
        allEntities(level).forEach { entity ->
            counters.merge(category(entity), 1, Int::plus)
        }
        return counters
    }

    /** 实体归类，用于清扫与统计。 */
    fun category(entity: Entity): String = when {
        entity is ItemEntity -> "item"
        entity is ExperienceOrb -> "xp_orb"
        entity is AbstractArrow -> "arrow"
        entity is Projectile -> "projectile"
        entity is net.minecraft.world.entity.Mob -> "mob"
        entity is net.minecraft.world.entity.player.Player -> "player"
        else -> "other"
    }

    /** 是否是被保护的实体：有自定义名称的物品一般被视为玩家珍视物。 */
    fun isProtected(entity: Entity, keepNamedItems: Boolean): Boolean {
        if (entity is ItemEntity) {
            // 1.21 起物品名称走数据组件，没有 hasCustomHoverName() 了
            if (keepNamedItems && VItems.hasCustomName(entity.item)) return true
            // 拾取延迟还没走完 —— 刚被扔出或刚由方块产出的掉落物，不动它
            if (entity.hasPickUpDelay()) return true
        }
        // 无敌实体（展示架、标记）与坐骑上的实体不清理
        return entity.isInvulnerable || (!entity.isRemoved && entity.isPassenger)
    }

    /** 玩家当前所在维度中、以玩家为中心的实体（供命令使用）。 */
    fun around(player: net.minecraft.server.level.ServerPlayer, radius: Double): List<Entity> {
        val level = player.level() as? ServerLevel ?: return emptyList()
        return entitiesNear(level, player.position(), radius)
            .filter { it !== player }
    }
}
