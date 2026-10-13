package org.voidkit.core.api

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

/**
 * 权限判定服务。
 *
 * 内核**不实现**权限系统 —— 它只定义这个接口，并提供一个「只认原版 OP」的兜底实现
 * （[VanillaPermissionProvider]）。装了 `voidkit_perm` 插件后，插件会通过
 * [VoidkitServices.installPermission] 把自己的实现换进来。
 *
 * 这么设计的原因：权限模型各家不同（自研组树 / LuckPerms / FTB Ranks …），
 * 把某一家的实现编译进内核，就等于逼所有使用者接受它。
 *
 * ⚠️ 有意只暴露**基本类型**（Boolean / String / Int）。如果这里返回某个插件的
 * 数据类，内核就会反向依赖插件 —— 那正是这次重构要拆掉的东西。
 */
interface PermissionProvider {

    /**
     * 是否拥有某个权限节点。支持 `xxx.*` 通配与 `-xxx` 否定由实现方决定。
     *
     * ⚠️ 实现方**必须**在「显式判定（玩家节点 / 各组）都没命中之后」放行
     * 登记在 [PublicNodes] 里的公开节点 —— 否则像 `/warp` `/spawn` 这种
     * 玩家日常命令在零配置下会被误拒。顺序很关键：先看显式配置，再看公开节点，
     * 这样 `-节点` 的否定才能真正生效。
     */
    fun has(player: ServerPlayer, node: String): Boolean

    /**
     * 命令源版本的节点检查，供 Brigadier 的 `requires { }` 使用。
     *
     * 控制台 / 命令方块一律放行 —— 它们没有玩家实体，也不该被玩家权限挡住。
     */
    fun has(source: CommandSourceStack, node: String): Boolean {
        val player = source.player ?: return true
        return has(player, node)
    }

    /** 是否视为管理员（命令 `requires` 用）。 */
    fun admin(player: ServerPlayer): Boolean

    /** 命令源是否为管理员。控制台 / 命令方块一律视为管理员。 */
    fun admin(source: CommandSourceStack): Boolean

    /** 玩家所在组的名字。没有组概念时返回空串。 */
    fun groupOf(player: ServerPlayer): String

    /**
     * 按 UUID 查组名（面板用，玩家可能离线）。没有组概念时返回空串。
     *
     * 与 [groupOf] 区别：面板展示的玩家可能不在线，拿不到 ServerPlayer，
     * 只能按 UUID 查。离线时实现方应返回持久化数据里的组，而非空。
     */
    fun groupOf(uuid: java.util.UUID): String

    /** 组前缀（含颜色码）。没有则返回空串。 */
    fun prefixOf(player: ServerPlayer): String

    /** 组后缀（含颜色码）。没有则返回空串。 */
    fun suffixOf(player: ServerPlayer): String

    /** 该玩家最多能设几个家。 */
    fun homeLimit(player: ServerPlayer): Int

    /**
     * 设置玩家的**主组**，成功返回 true。
     *
     * 这是「修改玩家所属组」的唯一对外入口 —— 内核的 `/vk group` 通过它落库，而不是
     * 去写一份自己维护、权限判定根本不读的副本（那样会让管理员以为改了组、实际没生效）。
     *
     * 组不存在、或该权限实现**不支持组**（如 [VanillaPermissionProvider] 只认原版 OP）
     * 时返回 false，调用方据此给出明确反馈。
     *
     * 用 UUID + 组名两个基本类型做参数：玩家可能离线，且内核不得依赖插件的数据结构。
     */
    fun setGroup(uuid: java.util.UUID, group: String): Boolean

    /**
     * 增删玩家的**专属权限节点**（正节点授权 / `-` 前缀则否定，由实现方解释）。
     *
     * 与 [setGroup] 同理：这是「修改玩家节点」的唯一对外入口，内核的 `/vk perm add|del`
     * 通过它落库，而不是只写一份权限判定不读的档案副本。
     *
     * @param granted true = 授予该节点（幂等：已存在视为已达成）；false = 移除该节点。
     * @return true = 调用已生效（含“本就是这个状态”的幂等情形）；false = 该实现不支持
     *         玩家级节点（如 [VanillaPermissionProvider] 只认原版 OP）或参数非法。
     */
    fun setNode(uuid: java.util.UUID, node: String, granted: Boolean): Boolean

    /**
     * 当前存在的全部组名。
     *
     * 内核需要它来做命令补全（`/voidkit group <名字>`），但它**不能**去读权限插件的
     * 内部数据结构 —— 那样内核就反向依赖插件了。所以由接口把这份名单要出来。
     */
    fun groups(): Collection<String>
}

/**
 * 兜底实现：只认原版 OP（权限等级 2）。
 *
 * 没装任何权限插件时用它，保证内核与其它插件**始终能跑** —— 而不是因为缺权限插件
 * 就整个不可用。这也是 `voidkit_perm` 能做成「可选插件」的前提。
 */
object VanillaPermissionProvider : PermissionProvider {

    private const val OP_LEVEL = 2
    private const val OP_HOME_LIMIT = 100
    private const val DEFAULT_HOME_LIMIT = 3

    // 公开节点（/warp /spawn /home /tpa …）默认放行：零配置下也得能用，
    // 否则「装上就能用」就成了空话。它们由各插件在注册命令时登记（见 PublicNodes）。
    override fun has(player: ServerPlayer, node: String): Boolean =
        player.hasPermissions(OP_LEVEL) || PublicNodes.isPublic(node)

    override fun admin(player: ServerPlayer): Boolean = player.hasPermissions(OP_LEVEL)

    override fun admin(source: CommandSourceStack): Boolean {
        // 控制台与命令方块没有玩家实体，一律放行
        val player = source.player ?: return true
        return admin(player)
    }

    override fun groupOf(player: ServerPlayer): String = if (admin(player)) "op" else ""

    override fun groupOf(uuid: java.util.UUID): String = "" // 离线判断不了 OP，返回空

    override fun prefixOf(player: ServerPlayer): String = ""

    override fun suffixOf(player: ServerPlayer): String = ""

    override fun homeLimit(player: ServerPlayer): Int =
        if (admin(player)) OP_HOME_LIMIT else DEFAULT_HOME_LIMIT

    /** 原版兜底没有“组”这一模型（只有 OP / 非 OP），无法设置，明确回 false。 */
    override fun setGroup(uuid: java.util.UUID, group: String): Boolean = false

    /** 原版兜底没有“玩家级权限节点”模型，无法增删，明确回 false。 */
    override fun setNode(uuid: java.util.UUID, node: String, granted: Boolean): Boolean = false

    override fun groups(): Collection<String> = listOf("op")
}
