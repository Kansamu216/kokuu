package org.voidkit.essentials

import net.minecraft.commands.CommandSourceStack
import org.voidkit.core.api.VoidkitServices

/**
 * 玩家日常命令的权限门槛。
 *
 * 与 admin 命令的关键区别：admin 命令不装权限插件时退化成「只认 OP」是合理的；
 * 但 /home /tpa /back /spawn 是玩家日常功能，也退化成 OP-only 就等于把功能锁死了。
 * 所以这里多问一句：没装 voidkit_perm → 人人放行（与这些命令的历史行为一致）；
 * 装了 → 按节点判定（default 组默认带 spawn/home/tpa/back 四个节点）。
 */
object PermGate {

    /** 需要是玩家，且拥有 [node] 节点；未装权限插件时对所有玩家放行。 */
    fun player(source: CommandSourceStack, node: String): Boolean {
        if (source.player == null) return false
        if (!VoidkitServices.hasPermissionPlugin()) return true
        return VoidkitServices.permission().has(source, node)
    }
}
