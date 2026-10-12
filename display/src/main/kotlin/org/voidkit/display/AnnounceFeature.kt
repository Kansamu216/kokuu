package org.voidkit.display

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.placeholder.PlaceholderRegistry
import org.voidkit.core.util.Broadcast
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.util.concurrent.ThreadLocalRandom

/**
 * 公告。
 *
 * 两种用法：
 *  - **手动** `/announce <内容>` —— 带框线、支持颜色码与变量，可带音效；
 *  - **轮播** `/announce auto on` —— 从配置的列表里按间隔自动播报。
 *
 * ## 框线为什么用 `&m`
 *
 * 配置模板里写 `&8&m` + 一串空格 —— 这是“删除线”，客户端会画成一条横线。
 * 比让服主手数破折号靠谱（不同客户端字体宽度不一样，数出来的长度经常对不齐）。
 *
 * ## 通道
 *
 * 和清扫一样走 [Broadcast]，支持 chat / actionbar / both / off。
 * 轮播可以单独设通道（比如轮播只走动作栏，不占聊天记录）。
 */
class AnnounceFeature : Feature() {

    override val id = "announce"
    override val displayName = "公告"
    override val description = "手动播报与定时轮播"

    override val defaultEnabled: Boolean = true

    private var seconds = 0

    /** 顺序模式下走到第几条。 */
    private var cursor = 0

    override fun onServerStart(server: MinecraftServer) {
        AnnounceStore.load()
        seconds = 0
    }

    override fun onReload(server: MinecraftServer) {
        AnnounceStore.load()
        seconds = 0
    }

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) return
        val auto = AnnounceStore.config.auto
        if (!auto.enabled || auto.messages.isEmpty()) return

        seconds++
        if (seconds < auto.intervalSeconds) return
        seconds = 0

        val text = nextMessage(auto)
        if (text.isNullOrBlank()) return

        val channel = Broadcast.parse(auto.channel)
        Broadcast.send(server, render(server, text.split('\n')), channel)
    }

    private fun nextMessage(auto: AutoBroadcast): String? {
        if (auto.messages.isEmpty()) return null
        return if (auto.random) {
            auto.messages[ThreadLocalRandom.current().nextInt(auto.messages.size)]
        } else {
            val message = auto.messages[cursor % auto.messages.size]
            cursor = (cursor + 1) % auto.messages.size
            message
        }
    }

    // ---------- 渲染 ----------

    /**
     * 把几行文本渲染成一条完整公告（框线 + 前缀 + 变量解析）。
     *
     * 变量用无玩家的 [PlaceholderRegistry.Context]（`server` 级变量如 `%online%` `%tps%` 有效，
     * `%player%` 这类玩家级变量会解析成空）。
     */
    fun render(server: MinecraftServer, lines: List<String>): Component {
        val cfg = AnnounceStore.config
        val ctx = PlaceholderRegistry.Context(server, null)
        val out = Component.empty()

        fun line(raw: String) {
            if (raw.isBlank()) return
            out.append(Text.legacy(PlaceholderRegistry.resolve(raw, ctx)))
            out.append(Component.literal("\n"))
        }

        cfg.header.forEach { line(it) }
        lines.forEach { line(cfg.prefix + it) }
        cfg.footer.forEach { line(it) }
        return out
    }

    private fun playSound(server: MinecraftServer) {
        val id = AnnounceStore.config.sound
        if (id.isBlank()) return
        runCatching {
            val location = net.minecraft.resources.ResourceLocation.tryParse(id) ?: return
            val sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(null)
                ?: return
            server.playerList.players.forEach { player ->
                player.playNotifySound(sound, net.minecraft.sounds.SoundSource.MASTER, 0.7f, 1f)
            }
        }
    }

    /** 手动播报（供命令与 API 共用）。 */
    fun announce(server: MinecraftServer, text: String): Int {
        val cfg = AnnounceStore.config
        val channel = Broadcast.parse(cfg.channel)
        val sent = Broadcast.sendToPlayers(server, render(server, text.split('\n')), channel)
        if (channel != Broadcast.Channel.OFF) playSound(server)
        VoidkitCore.LOG.info("公告已发送给 {} 名玩家：{}", sent, text.replace('\n', ' '))
        return sent
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("announce")
            .requires { VoidkitServices.permission().has(it, "voidkit.announce") }

        // 子命令先注册 —— Brigadier 会优先匹配字面量，所以 /announce auto 不会被当成正文
        root.then(
            Commands.literal("auto")
                .executes { autoStatus(it.source) }
                .then(
                    Commands.literal("on").executes { setAuto(it.source, true) }
                )
                .then(
                    Commands.literal("off").executes { setAuto(it.source, false) }
                )
        )
        root.then(Commands.literal("list").executes { list(it.source) })
        root.then(Commands.literal("reload").executes { reload(it.source) })
        root.then(
            Commands.argument("text", StringArgumentType.greedyString())
                .executes { send(it.source, StringArgumentType.getString(it, "text")) }
        )
        root.executes { usage(it.source) }
        dispatcher.register(root)
    }

    private fun usage(source: CommandSourceStack): Int {
        Msg.text(source, "&8===== 公告 =====")
        Msg.text(source, "&f/announce <内容> &7发送一条公告（支持 & 颜色码与 %变量%，用 \\n 换行）")
        Msg.text(source, "&f/announce auto on|off &7定时轮播开关")
        Msg.text(source, "&f/announce list &7查看轮播内容")
        Msg.text(source, "&f/announce reload &7重新读取配置")
        Msg.text(source, "&8当前通道：&f${AnnounceStore.config.channel}")
        return 1
    }

    private fun send(source: CommandSourceStack, text: String): Int {
        if (!AnnounceStore.config.enabled) {
            Msg.err(source, "公告功能已在配置里关闭")
            return 0
        }
        val sent = announce(source.server, text)
        Msg.ok(source, "已发送公告（$sent 名在线玩家收到）")
        if (sent == 0) Msg.info(source, "当前没有在线玩家 —— 消息只写进了控制台")
        return 1
    }

    private fun autoStatus(source: CommandSourceStack): Int {
        val auto = AnnounceStore.config.auto
        Msg.text(source, "&7轮播：${if (auto.enabled) "&a开" else "&7关"} &7| 间隔 &f${auto.intervalSeconds}s" +
            " &7| 方式 &f${if (auto.random) "随机" else "顺序"} &7| 通道 &f${auto.channel}" +
            " &7| 共 &f${auto.messages.size} &7条")
        return 1
    }

    private fun setAuto(source: CommandSourceStack, on: Boolean): Int {
        AnnounceStore.config.auto.enabled = on
        AnnounceStore.save()
        seconds = 0
        Msg.ok(source, "定时轮播已${if (on) "开启" else "关闭"}")
        if (on && AnnounceStore.config.auto.messages.isEmpty()) {
            Msg.err(source, "轮播列表是空的，去 display/announce.json 的 auto.messages 里加几条")
        }
        return 1
    }

    private fun list(source: CommandSourceStack): Int {
        val messages = AnnounceStore.config.auto.messages
        Msg.text(source, "&8===== 轮播内容（${messages.size} 条）=====")
        if (messages.isEmpty()) {
            Msg.text(source, "&7（空）去 display/announce.json 的 auto.messages 里添加")
            return 1
        }
        messages.forEachIndexed { index, raw ->
            Msg.text(source, "&7${index + 1}. &f${raw.replace("\n", " ⏎ ")}")
        }
        return 1
    }

    private fun reload(source: CommandSourceStack): Int {
        AnnounceStore.load()
        Msg.ok(source, "公告配置已重新读取")
        return announceDebug(source)
    }

    /** 重载后顺手告诉管理员”当前通道会把消息发到哪" —— 避免又出现“只看得到后台”的困惑。 */
    private fun announceDebug(source: CommandSourceStack): Int {
        val cfg = AnnounceStore.config
        val channel = Broadcast.parse(cfg.channel)
        Msg.text(source, "&7播报通道 &f${cfg.channel} &7→ 解析为 &f${channel.name.lowercase()}")
        if (channel == Broadcast.Channel.OFF) {
            Msg.err(source, "当前是 off —— 公告不会出现在游戏里，只写控制台")
        }
        return 1
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/announce") { _ ->
            val cfg = AnnounceStore.config
            ApiJson.obj(
                "ok", true,
                "enabled", enabled && cfg.enabled,
                "channel", cfg.channel,
                "prefix", cfg.prefix,
                "auto", ApiJson.obj(
                    "enabled", cfg.auto.enabled,
                    "intervalSeconds", cfg.auto.intervalSeconds,
                    "random", cfg.auto.random,
                    "channel", cfg.auto.channel,
                    "count", cfg.auto.messages.size,
                    "messages", ApiJson.arr(cfg.auto.messages)
                )
            )
        }

        router.post("/api/announce") { request ->
            val body = request.requireField("message")
            val message = body.get("message").asString
            if (message.isBlank()) throw IllegalArgumentException("公告内容不能为空")
            val sent = org.voidkit.core.api.VoidkitServer.onServer { announce(org.voidkit.core.api.VoidkitServer.require(), message) }
            ApiJson.obj("ok", true, "sent", sent, "message", "已发送公告给 $sent 名玩家")
        }

        router.post("/api/announce/auto") { request ->
            val auto = AnnounceStore.config.auto
            // 不传 enabled 就是“翻转”，方便面板做一个开关按钮
            val enabled = request.bodyJson().get("enabled")?.let {
                if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) it.asBoolean else null
            } ?: !auto.enabled
            auto.enabled = enabled
            AnnounceStore.save()
            ApiJson.obj("ok", true, "enabled", enabled, "count", auto.messages.size)
        }
    }
}
