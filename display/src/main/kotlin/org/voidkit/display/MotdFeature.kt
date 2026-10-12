package org.voidkit.display

import net.minecraft.server.MinecraftServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.placeholder.PlaceholderRegistry
import org.voidkit.core.util.Text
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

/**
 * MOTD：服务器列表里显示的两行描述。配置在 `display/motd.json`。
 *
 * NeoForge 1.21.1 没有 MOTD 事件，但 [MinecraftServer] 提供了 `setMotd` 与
 * `invalidateStatus`，配合可以直接做到**热更新**（不用重启）：
 *  1. `setMotd(text)` 改写内存里的 motd；
 *  2. `invalidateStatus()` 丢掉缓存的 ServerStatus，客户端下次 ping 就拿到新值；
 *  3. 顺带写回 `server.properties`，保证重启后不丢。
 *
 * 支持配多套并按 `rotation` 轮换（`fixed` / `order` / `random`），
 * 文本里可以用 `&` 颜色码与 `%变量%`。
 *
 * ⚠️ 服务器列表**不支持点击事件** —— 写 `[文字](run:/命令)` 只会显示成普通文字，
 * 所以这里走 [Text.toSection] 把动作标记剥掉，只留颜色与文本。
 */
class MotdFeature : Feature() {

    override val id = "motd"
    override val displayName = "服务器 MOTD"
    override val description = "服务器列表显示的文字，支持多套轮换、颜色代码与变量"

    override val defaultEnabled: Boolean get() = DisplayStore.motd.enabled

    private var seconds = 0
    private var cursor = 0
    private var lastApplied: String = ""

    /** 当前生效的那套文本（供面板显示“现在用的是哪套”）。 */
    @Volatile
    private var currentLines: List<String> = emptyList()

    override fun onServerStart(server: MinecraftServer) {
        seconds = 0
        cursor = 0
        apply(server)
    }

    override fun onReload(server: MinecraftServer) {
        seconds = 0
        cursor = 0
        lastApplied = ""
        apply(server)
    }

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) return
        val interval = max(1, DisplayStore.motd.updateSeconds)
        if (++seconds % interval != 0) return
        apply(server)
    }

    // ---------- 实现 ----------

    /** 按 `rotation` 挑一套 MOTD。 */
    private fun pick(): MotdVariant? {
        val cfg = DisplayStore.motd
        val variants = cfg.variants
        if (variants.isEmpty()) return null
        return when (cfg.rotation) {
            "order" -> variants[cursor++ % variants.size]

            "random" -> {
                val total = variants.sumOf { max(1, it.weight) }
                var roll = ThreadLocalRandom.current().nextInt(total)
                var chosen = variants.first()
                for (variant in variants) {
                    roll -= max(1, variant.weight)
                    if (roll < 0) {
                        chosen = variant
                        break
                    }
                }
                chosen
            }

            else -> variants.first()
        }
    }

    private fun apply(server: MinecraftServer) {
        val variant = pick() ?: return
        currentLines = variant.lines

        // MOTD 在 ping 阶段解析，此时没有任何玩家，所以 player 传 null
        val ctx = PlaceholderRegistry.Context(server, null)
        val resolved = PlaceholderRegistry.resolve(variant.lines.joinToString("\n"), ctx)
        val section = Text.toSection(resolved)

        if (section == lastApplied) return
        lastApplied = section

        // 只有在用户显式打开时才动 server.properties —— 见 MotdConfig.writeServerProperties 的说明。
        // 运行时的列表 MOTD 上面那句 setMotd 已经生效，这一步纯粹是“让重启后也不丢”。
        if (DisplayStore.motd.writeServerProperties) {
            writePropertyFile(server, section)
        }
        runCatching {
            server.setMotd(section)
            server.invalidateStatus()
        }.onFailure { VoidkitCore.LOG.warn("动态更新 MOTD 失败：{}", it.message) }
    }

    /**
     * 写回 `server.properties`，保证重启后依然是这套 MOTD。
     *
     * ⚠️ 默认不调用它（见 [MotdConfig.writeServerProperties]）—— 整合包与本机服主
     * 往往自己维护这个文件，模组擅自改写属于越界，而且会被整合包更新覆盖回去。
     */
    private fun writePropertyFile(server: MinecraftServer, text: String) {
        runCatching {
            val file = server.serverDirectory.resolve("server.properties")
            if (!Files.isRegularFile(file)) return
            val props = Properties()
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { props.load(it) }
            if (props.getProperty("motd") == text) return
            props.setProperty("motd", text)
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer ->
                props.store(writer, "Minecraft server properties")
            }
        }.onFailure { VoidkitCore.LOG.warn("写入 server.properties 的 motd 失败：{}", it.message) }
    }

    /** 把面板提交的 JSON 应用到配置并落盘。返回是否真的改了东西。 */
    fun updateFrom(json: com.google.gson.JsonObject): Boolean {
        val cfg = DisplayStore.motd
        var changed = false

        json.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { cfg.enabled = it; changed = true }
        json.get("updateSeconds")?.takeIf { it.isJsonPrimitive }?.asInt?.let {
            cfg.updateSeconds = it.coerceIn(1, 3600); changed = true
        }
        json.get("rotation")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it in setOf("fixed", "order", "random")) { cfg.rotation = it; changed = true }
        }
        json.getAsJsonArray("variants")?.let { array ->
            val variants = mutableListOf<MotdVariant>()
            array.forEach { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val variant = MotdVariant()
                obj.getAsJsonArray("lines")?.let { lines ->
                    variant.lines = lines.map { it.asString }.take(2).toMutableList()
                }
                obj.get("weight")?.takeIf { it.isJsonPrimitive }?.asInt?.let { variant.weight = max(1, it) }
                variant.normalize()
                variants += variant
            }
            if (variants.isNotEmpty()) {
                cfg.variants = variants
                changed = true
            }
        }

        if (changed) {
            cfg.normalize()
            DisplayStore.saveMotd()
            onReload(org.voidkit.core.api.VoidkitServer.require())
        }
        return changed
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/motd") { _ ->
            val cfg = DisplayStore.motd
            val server = org.voidkit.core.api.VoidkitServer.require()
            val ctx = PlaceholderRegistry.Context(server, null)
            ApiJson.obj(
                "ok", true,
                "enabled", enabled,
                "rotation", cfg.rotation,
                "updateSeconds", cfg.updateSeconds,
                "variants", ApiJson.arr(
                    cfg.variants.map { variant ->
                        ApiJson.obj("lines", ApiJson.arr(variant.lines), "weight", variant.weight)
                    }
                ),
                "currentLines", ApiJson.arr(currentLines),
                "resolved", ApiJson.arr(
                    PlaceholderRegistry.resolve(currentLines, ctx).map { Text.strip(it) }
                )
            )
        }

        router.post("/api/motd") { request ->
            val changed = updateFrom(request.requireField("variants"))
            ApiJson.obj("ok", true, "changed", changed)
        }
    }
}
