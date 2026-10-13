package org.voidkit.display

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.data.DataStore
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.placeholder.PlaceholderRegistry
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 聊天栏美化。配置在 `display/chat.json`。
 *
 * 通过 [ServerChatEvent] 重写整条消息，实现：
 *  - 自定义格式模板（默认 `%prefix%&f{name} &8» &f{message}`）
 *  - 模板里可以用 `%占位符%`（如 `%prefix%` `%group%` `%online%`），也可用 `{name}` 系列简写
 *  - 模板里可以用 `[文字](动作)` 可点击语法，做成“点名字自动填私聊”这种效果（见 [Text]）
 *  - 有权限的玩家可以打 `&a` 之类的彩色字
 *  - 禁言拦截（`/mute` 由管理工具模块写入档案，这里负责拒绝发言）
 *
 * ⚠️ 安全边界：**模板**走完整富文本解析（含点击），**玩家发言**只解析颜色 ——
 * 否则玩家可以发出可点击的链接或指令，变成钓鱼入口。
 *
 * 注意：NeoForge 1.21.1 用的是 `ServerChatEvent`，事件在**服务端主线程**触发，
 * 可安全读取世界与玩家状态。
 */
class ChatFormatFeature : Feature() {

    private companion object {
        /** 格式模板里的消息占位标记。 */
        const val MESSAGE_TOKEN = "{message}"

        /** 玩家名占位标记。要单独渲染（挂悬停浮层），不能跟其它占位符一起做纯文本替换。 */
        const val NAME_TOKEN = "{name}"
    }

    override val id = "chat-format"
    override val displayName = "聊天栏美化"
    override val description = "自定义聊天格式、彩色字、变量、点击动作与禁言拦截"

    override val defaultEnabled: Boolean get() = DisplayStore.chat.enabled

    override fun onChat(e: org.voidkit.core.api.events.VChat) {
        if (!enabled) return
        val cfg = DisplayStore.chat
        val store = DataStore.get() ?: return
        val player = e.player
        val profile = store.profile(player.uuid, player.gameProfile.name)

        // ---------- 1. 禁言拦截 ----------
        val now = System.currentTimeMillis()
        if (profile.isMuted(now)) {
            if (cfg.blockMuted) e.canceled = true
            Msg.err(player, muteNotice(profile.muteReason, profile.mutedUntil, now))
            return
        }

        val raw = e.rawText

        // ---------- 2. 权限决定能做什么 ----------
        val canColor = cfg.colorEnabled && VoidkitServices.permission().has(player, cfg.colorPermission)
        val canPlaceholder = VoidkitServices.permission().has(player, cfg.placeholderPermission)

        // ---------- 3. 渲染头部 / 尾部模板 ----------
        val ctx = PlaceholderRegistry.Context(player.server, player)
        var template = cfg.format
        if (canPlaceholder) {
            // 普通玩家解析了也只会看到 %xxx% 原文，这里直接跳过省点开销
            template = PlaceholderRegistry.resolve(template, ctx)
        }
        template = template
            // ⚠️ 故意**不**在这里替换 {name}：它要单独渲染成带悬停浮层的组件，见 renderHead()。
            // 先替换成纯文本的话，悬停就没地方挂了。
            .replace("{prefix}", VoidkitServices.permission().prefixOf(player))
            .replace("{suffix}", VoidkitServices.permission().suffixOf(player))
            .replace("{group}", VoidkitServices.permission().groupOf(player))
            .replace("{world}", player.level().dimension().location().path)

        val cursor = template.indexOf(MESSAGE_TOKEN)
        val head = if (cursor >= 0) template.substring(0, cursor) else template
        val tail = if (cursor >= 0) template.substring(cursor + MESSAGE_TOKEN.length) else ""

        // ---------- 4. 拼成完整消息 ----------
        val message: MutableComponent = Component.empty()
            .append(renderHead(head, player))
            .append(body(raw, canColor))
            .append(Text.legacy(tail.replace(NAME_TOKEN, Text.strip(player.gameProfile.name))))

        // 🚨 取消原版广播，再自己广播 voidkit 的完整格式 —— 因为 Minecraft 1.19+ 的聊天
        // 是「原版 ChatType 装饰（`<玩家名>`）+ message 内容」两层：只改 message 的话，
        // 原版装饰的 `<玩家名>` 会和我们渲染的名字叠加，变成「Kansamu216 Kansamu216 » 内容」。
        // 取消原版（丢弃那层装饰与聊天签名），由 voidkit 广播整条，才能真正接管聊天外观。
        // 代价：消息不再带 Mojang 聊天签名（客户端不显示「已验证」标记）——服务端 mod 普遍如此。
        e.canceled = true
        player.server.playerList.broadcastSystemMessage(message, false)
    }

    /**
     * 渲染玩家名**之前**那段模板。
     *
     * 与尾段不同，这里要单独处理 `{name}` —— 给它挂上鼠标悬停浮层（坐标 / 等级 / 组别…）。
     * 玩家名本身必须先 [Text.strip] 再渲染：否则有人把名字改成 `&c假管理` 就能冒充管理员。
     */
    private fun renderHead(head: String, player: ServerPlayer): Component {
        val idx = head.indexOf(NAME_TOKEN)
        if (idx < 0) return Text.legacy(head)

        val before = head.substring(0, idx)
        val after = head.substring(idx + NAME_TOKEN.length)
        val nameText = Text.strip(player.gameProfile.name)
        val nameComponent = Text.legacy(nameText)

        val cfg = DisplayStore.chat
        var style = Style.EMPTY

        // 悬停：坐标 / 等级 / 组别 等（可配置，见 chat.json）
        if (cfg.nameHoverEnabled && cfg.nameHover.isNotEmpty()) {
            val ctx = PlaceholderRegistry.Context(player.server, player)
            style = style.withHoverEvent(Text.hoverEvent(PlaceholderRegistry.resolve(cfg.nameHover, ctx).joinToString("\n")))
        }
        // 点击：默认 `/tpa {name}` —— 点一下名字就给对方发传送请求
        if (cfg.nameClick.isNotBlank()) {
            Text.clickEvent(cfg.nameClick.replace(NAME_TOKEN, nameText))?.let { style = style.withClickEvent(it) }
        }
        // ⚠️ 必须用 setStyle（setter，改自身）——withStyle 在某些实现里返回新对象，
        // 不接返回值会让悬停/点击**静默丢失**（表现为「名字点不了、悬停没说明」）。
        if (!style.isEmpty) nameComponent.setStyle(style)

        return Text.legacy(before).append(nameComponent).append(Text.legacy(after))
    }

    /** 玩家发言：只解析颜色（不解析点击语法），无权限时连颜色都剥掉。 */
    private fun body(text: String, canColor: Boolean): Component =
        if (canColor) Text.legacyColor(text) else Component.literal(Text.strip(text))

    private fun muteNotice(reason: String, until: Long, now: Long): String {
        val base = if (reason.isBlank()) "你已被禁言" else "你已被禁言：$reason"
        if (until <= 0L) return base
        val remain = ((until - now) / 1000L).coerceAtLeast(0L)
        val stamp = SimpleDateFormat("MM-dd HH:mm").format(Date(until))
        return "$base（剩 ${Msg.duration(remain)}，$stamp 解禁）"
    }

    /** 面板写回。 */
    fun updateFrom(json: com.google.gson.JsonObject): Boolean {
        val cfg = DisplayStore.chat
        var changed = false

        json.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { cfg.enabled = it; changed = true }
        json.get("format")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let {
            cfg.format = it.take(512); changed = true
        }
        json.get("colorEnabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { cfg.colorEnabled = it; changed = true }
        json.get("blockMuted")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { cfg.blockMuted = it; changed = true }

        if (changed) {
            cfg.normalize()
            DisplayStore.saveChat()
        }
        return changed
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/chat/format") { _ ->
            val cfg = DisplayStore.chat
            ApiJson.obj(
                "ok", true,
                "enabled", enabled,
                "format", cfg.format,
                "colorEnabled", cfg.colorEnabled,
                "colorPermission", cfg.colorPermission,
                "placeholderPermission", cfg.placeholderPermission,
                "blockMuted", cfg.blockMuted,
                // 聊天栏支持点击/悬停
                "clickable", true,
                "modes", ApiJson.arr(
                    listOf(
                        ApiJson.obj("name", "颜色代码", "permission", cfg.colorPermission),
                        ApiJson.obj("name", "变量", "permission", cfg.placeholderPermission)
                    )
                )
            )
        }
        router.post("/api/chat/format") { request ->
            val changed = updateFrom(request.bodyJson())
            ApiJson.obj("ok", true, "changed", changed)
        }
    }
}
