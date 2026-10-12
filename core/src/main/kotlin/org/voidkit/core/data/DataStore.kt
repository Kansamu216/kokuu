package org.voidkit.core.data

import com.google.gson.GsonBuilder
import org.voidkit.core.VoidkitCore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全部持久化数据的入口。
 *
 * 目录结构（位于存档根目录下）：
 * ```
 * <world>/voidkit/
 *   ├── players/<uuid>.json     单个玩家档案
 *   ├── global.json             传送点、出生点、白名单、封禁
 *   └── logs/joins.jsonl        上下线原始事件流
 * ```
 * 全局配置在 `config/voidkit/config.json`，由 [org.voidkit.core.VoidkitCore] 管理。
 */
class DataStore private constructor(private val root: Path) {

    companion object {

        private val GSON = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()

        @Volatile
        private var instance: DataStore? = null

        fun get(): DataStore? = instance

        fun ready(): Boolean = instance != null

        fun init(root: Path): DataStore {
            val store = DataStore(root)
            instance = store
            return store
        }

        fun shutdown() {
            instance?.let { store ->
                store.saveAll()
                store.saveGlobal()
                store.joinLog.close()
            }
            instance = null
        }
    }

    private val playersDir: Path = root.resolve("players")
    private val profiles = ConcurrentHashMap<UUID, PlayerProfile>()
    private val names = ConcurrentHashMap<String, UUID>()
    private val dirty = AtomicBoolean(false)

    val joinLog = JoinLog(root.resolve("logs").resolve("joins.jsonl"))

    var global: GlobalData = GlobalData()
        private set

    init {
        runCatching { Files.createDirectories(playersDir) }
            .onFailure { VoidkitCore.LOG.error("创建数据目录失败: {}", it.message) }
        loadGlobal()
        loadAllProfiles()
    }

    fun root(): Path = root

    // ---------- 全局数据 ----------

    private fun loadGlobal() {
        val file = root.resolve("global.json")
        if (!Files.exists(file)) return
        runCatching {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                GSON.fromJson(reader, GlobalData::class.java)?.let { global = it }
            }
        }.onFailure { VoidkitCore.LOG.warn("读取 global.json 失败: {}", it.message) }
        global.normalize()
    }

    fun saveGlobal() {
        writeJson(root.resolve("global.json"), global)
    }

    // ---------- 玩家档案 ----------

    /** 取玩家档案；不存在则创建。name 传 null 表示“不更新名字”。 */
    fun profile(uuid: UUID, name: String?): PlayerProfile {
        val profile = profiles.getOrPut(uuid) {
            loadProfile(uuid) ?: PlayerProfile().also { it.uuid = uuid.toString() }
        }
        if (!name.isNullOrBlank()) {
            if (name != profile.lastName) {
                profile.addKnownName(profile.lastName)
                profile.lastName = name
                profile.addKnownName(name)
                markDirty()
            }
            names[name.lowercase()] = uuid
        }
        return profile
    }

    /** 按名字找档案（含离线）。 */
    fun byName(name: String?): PlayerProfile? {
        if (name.isNullOrBlank()) return null
        names[name.lowercase()]?.let { return profiles[it] }
        return profiles.values.firstOrNull { profile ->
            profile.lastName.equals(name, ignoreCase = true) ||
                profile.knownNames.any { it.equals(name, ignoreCase = true) }
        }
    }

    fun all(): Collection<PlayerProfile> = profiles.values

    fun online(): Collection<PlayerProfile> = profiles.values.filter { it.online }

    fun onlineCount(): Int = profiles.values.count { it.online }

    fun markDirty() {
        dirty.set(true)
    }

    fun isDirty(): Boolean = dirty.get()

    fun save(profile: PlayerProfile) {
        if (profile.uuid.isBlank()) return
        writeJson(playersDir.resolve("${profile.uuid}.json"), profile)
    }

    fun saveAll() {
        profiles.values.forEach { save(it) }
        dirty.set(false)
    }

    private fun loadProfile(uuid: UUID): PlayerProfile? {
        val file = playersDir.resolve("$uuid.json")
        if (!Files.exists(file)) return null
        val profile = runCatching {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                GSON.fromJson(reader, PlayerProfile::class.java)
            }
        }.onFailure { VoidkitCore.LOG.warn("读取玩家档案失败 {}: {}", uuid, it.message) }.getOrNull()
            ?: return null

        profile.normalize()
        if (profile.uuid.isBlank()) profile.uuid = uuid.toString()
        if (profile.lastName.isNotBlank()) names[profile.lastName.lowercase()] = uuid
        profile.knownNames.forEach { names[it.lowercase()] = uuid }
        return profile
    }

    private fun loadAllProfiles() {
        if (!Files.isDirectory(playersDir)) return
        var loaded = 0
        runCatching {
            Files.list(playersDir).use { stream ->
                stream.forEach { file ->
                    val fileName = file.fileName.toString()
                    if (!fileName.endsWith(".json") || fileName.endsWith(".tmp")) return@forEach
                    runCatching { UUID.fromString(fileName.removeSuffix(".json")) }
                        .getOrNull()
                        ?.let { uuid ->
                            loadProfile(uuid)?.let {
                                profiles[uuid] = it
                                loaded++
                            }
                        }
                }
            }
        }.onFailure { VoidkitCore.LOG.warn("扫描玩家目录失败: {}", it.message) }
        VoidkitCore.LOG.info("已载入 {} 份玩家档案", loaded)
    }

    // ---------- 落盘工具 ----------

    private fun writeJson(file: Path, value: Any) {
        try {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8)
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Throwable) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (t: Throwable) {
            VoidkitCore.LOG.error("写入 {} 失败: {}", file, t.message)
        }
    }
}
