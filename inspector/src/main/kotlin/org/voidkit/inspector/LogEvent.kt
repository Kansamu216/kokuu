package org.voidkit.inspector

/**
 * 动作类型编码。存进数据库是 tinyint，省空间、好索引。
 */
object Actions {
    const val PLACE = 0          // 放置方块
    const val BREAK = 1          // 破坏方块
    const val CONTAINER_PUT = 2  // 往容器放入
    const val CONTAINER_TAKE = 3 // 从容器取出
    const val KILL = 4           // 击杀实体
    const val DROP = 5           // 丢出物品
    const val PICKUP = 6         // 捡起物品

    val NAMES: Map<Int, String> = mapOf(
        PLACE to "放置", BREAK to "破坏",
        CONTAINER_PUT to "放入", CONTAINER_TAKE to "取出",
        KILL to "击杀", DROP to "丢出", PICKUP to "捡起",
    )
}

/**
 * 一条待落库的审计事件。
 *
 * 关键设计：**在事件发生的那一刻（游戏主线程）就把所有数据拍平** ——
 * 方块/物品的名字、NBT 都序列化成不可变值，之后丢给写线程，主线程不再碰它。
 * 这样写线程读到的都是纯数据，不存在跨线程共享的可变世界状态。
 */
class LogEvent(
    val time: Long,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    /** 来源名：真人玩家就是昵称，其余是 `#creeper` `#create` 这类 `#` 前缀。 */
    val actorName: String,
    /** 玩家 UUID（小写去横线），非玩家来源为 null。 */
    val actorUuid: String?,
    val source: SourceKind,
    val action: Int,
    /** 方块/物品的注册名，如 `minecraft:stone`、`create:cogwheel`。 */
    val material: String,
    val amount: Int,
    /** 序列化后的 NBT（方块实体 / 物品组件），无则 null。 */
    val nbt: ByteArray?,
)
