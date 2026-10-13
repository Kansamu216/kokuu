package org.voidkit.core.api

import net.minecraft.commands.CommandSourceStack

/**
 * 命令权限判定的统一入口。
 *
 * 套件里有两类命令，判定方式不同：
 *
 * | 类型 | 判定 | 典型 |
 * |---|---|---|
 * | **玩家命令** | 直接 `has(source, 节点)`，节点若是「公开节点」则默认放行（见 [PublicNodes]） | `/warp` `/spawn` `/home` `/tpa` |
 * | **管理命令** | [admin] 或该命令**自己的节点**，任一命中即放行 | `/setwarp` `/setspawn` `/tphere` |
 *
 * 管理命令为什么是「或」而不是「且」：
 * - 给 `voidkit.admin` 的人**自动**拥有全部管理命令 —— 不然服主发一次总权限还要再发十条细权限；
 * - 同时又能**只发某一项** —— 例如只想让某组能用 `/setspawn`，给它
 *   `voidkit.command.setspawn` 即可，不必给 `voidkit.admin`。
 */
object CommandPerms {

    /**
     * 管理命令判定：`voidkit.admin`（或原版 OP）**或**该命令自己的节点，任一命中即放行。
     *
     * @param node 该命令的专属节点，如 `voidkit.command.setwarp`
     */
    fun admin(source: CommandSourceStack, node: String): Boolean {
        val perm = VoidkitServices.permission()
        return perm.admin(source) || perm.has(source, node)
    }
}
