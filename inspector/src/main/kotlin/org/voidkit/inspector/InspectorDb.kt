package org.voidkit.inspector

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.voidkit.core.VoidkitCore
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SQLite 存储引擎 —— 审计模块的「多核」核心。
 *
 * ## 为什么是 SQLite
 *
 * 嵌入式、零配置、单文件，随服务端走，不额外起数据库进程（「占用低」）。
 * CoreProtect / Ledger 都默认它，证明它扛得住方块的审计负载。配合 WAL 模式，
 * 它能做到「一写多读」—— 正好映射到本类的线程模型。
 *
 * ## 线程模型（「性能开销少」的关键）
 *
 * 写与读**完全隔离**，游戏主线程只做一件事：把事件塞进有界队列（O(1)，不阻塞）：
 *
 * ```
 * 主线程（tick）──offer()──▶ 有界队列 ──▶ 写线程（唯一写者，批量事务落库）
 *                                              ▲
 * 查询（命令/API）──────────▶ 查询线程池 ──────┘  （WAL 多读，与写并行不阻塞）
 * ```
 *
 * - 写线程是**唯一写者**，把最多 [batchSize] 条事件合成一个事务提交，写放大最小。
 * - 查询在**独立线程池**跑，用各自的读连接，走 WAL 多读，不占主线程、不卡 TPS。
 * - 队列有界：写线程来不及消费就丢（计入统计），**绝不阻塞主线程**。
 *
 * ## 为什么查得快（「查询快」）
 *
 * 1. **字典化**：方块/物品名、维度名、玩家名都换成整数 id 存进 events 表，行小而密。
 * 2. **复合索引**按查询方向铺：点位 `(dim,x,y,z,id)`、区域 `(dim,x,z,id)`、
 *    玩家 `(actor,id)`、物品 `(mat,id)`、时间 `(time)`。
 * 3. **keyset 分页**：用 `id < 游标` 翻页而不是 `OFFSET`，几十万行里翻到第 100 页也毫秒级。
 */
class InspectorDb(
    private val dbPath: Path,
    queueCapacity: Int,
    private val batchSize: Int,
    flushIntervalMs: Int,
    queryThreads: Int,
    private val purgeBatchSize: Int,
) : AutoCloseable {

    // ---------------- 查询参数与结果 ----------------

    /** 一条查询：所有字段都可空，空的就不做过滤。 */
    class QuerySpec(
        val world: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val z: Int? = null,
        val radius: Int? = null,
        val actor: String? = null,
        val action: Int? = null,
        val material: String? = null,
        val sourceKinds: Set<Int>? = null,
        val since: Long? = null,
        val until: Long? = null,
        val limit: Int = 20,
        val beforeId: Long? = null,
    )

    class QueryRow(
        val id: Long,
        val time: Long,
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val actor: String,
        val actorUuid: String?,
        val source: Int,
        val action: Int,
        val material: String,
        val amount: Int,
        val nbt: ByteArray?,
    )

    class QueryResult(
        val rows: List<QueryRow>,
        /** 还有下一页时的翻页游标（= 本页最后一条的 id），没有则 null。 */
        val nextCursor: Long?,
    )

    // ---------------- 状态 ----------------

    private val running = AtomicBoolean(true)

    /** 有界写队列。满了 offer 返回 false，主线程据此丢事件。 */
    private val queue = LinkedBlockingQueue<LogEvent>(queueCapacity)

    /** 写线程攒批的最长等待时间（毫秒）。 */
    private val flushIntervalMillis = flushIntervalMs.toLong()

    /** 字典缓存：name↔id。写线程写、查询线程读，用并发容器。 */
    private val dimId = ConcurrentHashMap<String, Int>()
    private val dimName = ConcurrentHashMap<Int, String>()
    private val matId = ConcurrentHashMap<String, Int>()
    private val matName = ConcurrentHashMap<Int, String>()
    private val actorId = ConcurrentHashMap<String, Int>()
    private val actorName = ConcurrentHashMap<Int, String>()
    private val actorUuid = ConcurrentHashMap<Int, String>()

    /** 待清理的截止时间（0 = 无任务），由写线程空闲时消费。 */
    private val purgeCutoff = AtomicLong(0L)

    // ---------------- 统计 ----------------

    private val queued = AtomicLong(0)
    private val written = AtomicLong(0)
    private val dropped = AtomicLong(0)

    // ---------------- 连接与线程 ----------------

    private lateinit var writerConn: Connection
    private lateinit var writer: Thread

    /** 查询线程池：查询在这里跑，不占游戏主线程。 */
    private val queryPool = Executors.newFixedThreadPool(queryThreads) { r ->
        Thread(r, "voidkit-inspector-query").apply { isDaemon = true }
    }

    /** 查询线程各自的读连接（ThreadLocal 复用）。 */
    private val readConnLocal = ThreadLocal<Connection>()
    private val readConns = CopyOnWriteArrayList<Connection>()

    private val LOG get() = VoidkitCore.LOG

    // ---------------- 生命周期 ----------------

    /** 建库、开连接、起写线程。 */
    fun open() {
        val dir = dbPath.toAbsolutePath().parent ?: return
        Files.createDirectories(dir)
        writerConn = openConnection()
        initSchema(writerConn)
        writer = Thread({ writerLoop() }, "voidkit-inspector-writer").apply { isDaemon = true }
        writer.start()
        LOG.info("审计数据库就绪：{}", dbPath.toAbsolutePath())
    }

    override fun close() {
        running.set(false)
        if (::writer.isInitialized) {
            writer.interrupt()
            runCatching { writer.join(8000) }
        }
        if (::writerConn.isInitialized) runCatching { writerConn.close() }
        readConns.forEach { runCatching { it.close() } }
        readConns.clear()
        queryPool.shutdownNow()
    }

    // ---------------- 写入入口 ----------------

    /** 主线程唯一要调的方法：入队。满了丢并计数，永不阻塞。 */
    fun offer(e: LogEvent): Boolean {
        if (queue.offer(e)) {
            queued.incrementAndGet()
            return true
        }
        dropped.incrementAndGet()
        return false
    }

    fun stats(): Map<String, Long> = mapOf(
        "queued" to queued.get(),
        "written" to written.get(),
        "dropped" to dropped.get(),
        "pending" to queue.size.toLong(),
    )

    fun schedulePurge(cutoffMs: Long) {
        purgeCutoff.set(cutoffMs)
    }

    // ---------------- 查询入口 ----------------

    /** 异步查询（命令用，不占主线程）。 */
    fun queryAsync(spec: QuerySpec): CompletableFuture<QueryResult> =
        CompletableFuture.supplyAsync({ runQuery(spec) }, queryPool)

    /** 同步查询（API / HTTP 线程用，本身就不在主线程）。 */
    fun query(spec: QuerySpec): QueryResult = runQuery(spec)

    // ---------------- 写线程 ----------------

    private fun writerLoop() {
        try {
            while (running.get()) {
                val batch = ArrayList<LogEvent>(batchSize)
                val first = try {
                    queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS)
                } catch (ie: InterruptedException) {
                    null
                }
                if (first == null) {
                    drainPurge()
                    continue
                }
                batch += first
                queue.drainTo(batch, batchSize - 1)
                writeBatch(batch)
                drainPurge()
            }
        } catch (t: Throwable) {
            if (running.get()) LOG.error("审计写线程异常退出", t)
        } finally {
            // 关服前的最后一次机会：清空队列
            val tail = ArrayList<LogEvent>()
            queue.drainTo(tail)
            if (tail.isNotEmpty()) writeBatch(tail)
            drainPurge()
        }
    }

    private fun writeBatch(batch: List<LogEvent>) {
        if (batch.isEmpty()) return
        writerConn.autoCommit = false
        try {
            val ps = writerConn.prepareStatement(
                "INSERT INTO events(time,dim,x,y,z,actor,action,mat,amount,data,source) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
            )
            for (e in batch) {
                ps.setLong(1, e.time)
                ps.setInt(2, dimensionIdForWrite(e.world))
                ps.setInt(3, e.x)
                ps.setInt(4, e.y)
                ps.setInt(5, e.z)
                ps.setInt(6, actorIdForWrite(e.actorName, e.actorUuid, e.source.id))
                ps.setInt(7, e.action)
                ps.setInt(8, materialIdForWrite(e.material))
                ps.setInt(9, e.amount)
                if (e.nbt != null) ps.setBytes(10, e.nbt) else ps.setNull(10, Types.BLOB)
                ps.setInt(11, e.source.id)
                ps.addBatch()
            }
            ps.executeBatch()
            ps.close()
            writerConn.commit()
            written.addAndGet(batch.size.toLong())
        } catch (t: Throwable) {
            runCatching { writerConn.rollback() }
            LOG.error("审计写库失败，丢弃 {} 条", batch.size, t)
        } finally {
            runCatching { writerConn.autoCommit = true }
        }
    }

    /** 写线程空闲时消费清理任务。 */
    private fun drainPurge() {
        val cutoff = purgeCutoff.getAndSet(0)
        if (cutoff > 0) runPurge(cutoff)
    }

    /** 分批删除过期记录，每轮最多做 50ms，避免卡住写线程。 */
    private fun runPurge(cutoff: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50)
        var total = 0
        while (System.nanoTime() < deadline) {
            val n = writerConn.prepareStatement(
                "DELETE FROM events WHERE id IN (SELECT id FROM events WHERE time < ? LIMIT ?)"
            ).use { ps ->
                ps.setLong(1, cutoff)
                ps.setInt(2, purgeBatchSize)
                ps.executeUpdate()
            }
            total += n
            if (n < purgeBatchSize) break
        }
        if (total > 0) LOG.info("审计：已清理过期记录 {} 条", total)
        // 被时间盒打断时把任务放回，下轮接着清
        if (total >= purgeBatchSize) purgeCutoff.set(cutoff)
    }

    // ---------------- 字典解析（写线程，单线程） ----------------

    private fun dimensionIdForWrite(name: String): Int =
        dimId[name] ?: resolveOrInsert("dimension", name).also { dimId[name] = it; dimName[it] = name }

    private fun materialIdForWrite(name: String): Int =
        matId[name] ?: resolveOrInsert("material", name).also { matId[name] = it; matName[it] = name }

    private fun actorIdForWrite(name: String, uuid: String?, kind: Int): Int =
        actorId[name] ?: run {
            val existing = selectId("actor", name)
            if (existing != null) {
                existing
            } else {
                writerConn.prepareStatement(
                    "INSERT INTO actor(name,uuid,kind) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, uuid)
                    ps.setInt(3, kind)
                    ps.executeUpdate()
                    ps.generatedKeys.use { rs -> if (rs.next()) rs.getInt(1) else -1 }
                }
            }
        }.also {
            actorId[name] = it
            actorName[it] = name
            if (uuid != null) actorUuid[it] = uuid
        }

    /** 先查再插（单写线程，无并发冲突，不需要 OR IGNORE）。 */
    private fun resolveOrInsert(table: String, name: String): Int =
        selectId(table, name) ?: writerConn.prepareStatement(
            "INSERT INTO $table(name) VALUES(?)", Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setString(1, name)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> if (rs.next()) rs.getInt(1) else -1 }
        }

    private fun selectId(table: String, name: String): Int? =
        writerConn.prepareStatement("SELECT id FROM $table WHERE name=?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    // ---------------- 字典解析（查询线程，只读） ----------------

    private fun dimensionIdForRead(name: String): Int =
        dimId[name] ?: readSelectId("dimension", name)?.also { dimId[name] = it; dimName[it] = name } ?: -1

    private fun materialIdForRead(name: String): Int =
        matId[name] ?: readSelectId("material", name)?.also { matId[name] = it; matName[it] = name } ?: -1

    private fun actorIdForRead(name: String): Int =
        actorId[name] ?: readSelectId("actor", name)?.also { actorId[name] = it; actorName[it] = name } ?: -1

    private fun readSelectId(table: String, name: String): Int? =
        readConn().prepareStatement("SELECT id FROM $table WHERE name=?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    // ---------------- 反向解析（查询渲染用） ----------------

    private fun dimNameOf(id: Int): String = dimName[id] ?: readName("dimension", id).also { dimName[id] = it }
    private fun matNameOf(id: Int): String = matName[id] ?: readName("material", id).also { matName[id] = it }
    private fun actorNameOf(id: Int): String = actorName[id] ?: readName("actor", id).also { actorName[id] = it }

    private fun readName(table: String, id: Int): String =
        readConn().prepareStatement("SELECT name FROM $table WHERE id=?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else "?" }
        }

    // ---------------- 查询执行 ----------------

    private fun runQuery(q: QuerySpec): QueryResult {
        val where = ArrayList<String>()
        val args = ArrayList<Any>()

        if (q.world != null) { where += "e.dim=?"; args += dimensionIdForRead(q.world) }

        if (q.x != null && q.y != null && q.z != null) {
            val r = q.radius ?: 0
            if (r > 0) {
                where += "e.x BETWEEN ? AND ?"; args += (q.x - r); args += (q.x + r)
                where += "e.z BETWEEN ? AND ?"; args += (q.z - r); args += (q.z + r)
                where += "e.y BETWEEN ? AND ?"; args += (q.y - r); args += (q.y + r)
            } else {
                where += "e.x=? AND e.y=? AND e.z=?"; args += q.x; args += q.y; args += q.z
            }
        }

        if (q.actor != null) { where += "e.actor=?"; args += actorIdForRead(q.actor) }
        if (q.action != null) { where += "e.action=?"; args += q.action }
        if (q.material != null) { where += "e.mat=?"; args += materialIdForRead(q.material) }
        if (!q.sourceKinds.isNullOrEmpty()) {
            where += "e.source IN (${q.sourceKinds.joinToString(",")})"
        }
        if (q.since != null) { where += "e.time>=?"; args += q.since }
        if (q.until != null) { where += "e.time<=?"; args += q.until }
        if (q.beforeId != null) { where += "e.id<?"; args += q.beforeId }

        val whereSql = if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")
        val limit = q.limit.coerceIn(1, 500)
        // 多取一条用来判断「是否还有下一页」，省一次 COUNT
        val sql = "SELECT e.id,e.time,e.dim,e.x,e.y,e.z,e.actor,e.action,e.mat,e.amount,e.data,e.source" +
            " FROM events e" + whereSql + " ORDER BY e.id DESC LIMIT " + (limit + 1)

        val rows = ArrayList<QueryRow>()
        var nextCursor: Long? = null
        readConn().prepareStatement(sql).use { ps ->
            args.forEachIndexed { i, a ->
                when (a) {
                    is Long -> ps.setLong(i + 1, a)
                    is Int -> ps.setInt(i + 1, a)
                    else -> ps.setObject(i + 1, a)
                }
            }
            ps.executeQuery().use { rs ->
                var n = 0
                while (rs.next()) {
                    if (n == limit) {
                        nextCursor = rs.getLong(1)
                        break
                    }
                    rows += QueryRow(
                        id = rs.getLong(1),
                        time = rs.getLong(2),
                        world = dimNameOf(rs.getInt(3)),
                        x = rs.getInt(4),
                        y = rs.getInt(5),
                        z = rs.getInt(6),
                        actor = actorNameOf(rs.getInt(7)),
                        actorUuid = actorUuid[rs.getInt(7)],
                        source = rs.getInt(12),
                        action = rs.getInt(8),
                        material = matNameOf(rs.getInt(9)),
                        amount = rs.getInt(10),
                        nbt = rs.getBytes(11),
                    )
                    n++
                }
            }
        }
        return QueryResult(rows, nextCursor)
    }

    // ---------------- 连接 ----------------

    private fun openConnection(): Connection {
        val cfg = SQLiteConfig()
        cfg.setJournalMode(SQLiteConfig.JournalMode.WAL)
        cfg.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
        cfg.setBusyTimeout(5000)
        cfg.setTempStore(SQLiteConfig.TempStore.MEMORY)
        val ds = SQLiteDataSource(cfg)
        ds.url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
        return ds.connection
    }

    private fun readConn(): Connection {
        readConnLocal.get()?.let { if (!it.isClosed) return it }
        val c = openConnection()
        readConnLocal.set(c)
        readConns += c
        return c
    }

    // ---------------- 建表 ----------------

    private fun initSchema(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate("CREATE TABLE IF NOT EXISTS dimension (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS material (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS actor (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, uuid TEXT, kind INTEGER NOT NULL DEFAULT 0)")
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "time INTEGER NOT NULL," +
                    "dim INTEGER NOT NULL," +
                    "x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL," +
                    "actor INTEGER NOT NULL," +
                    "action INTEGER NOT NULL," +
                    "mat INTEGER NOT NULL," +
                    "amount INTEGER NOT NULL DEFAULT 1," +
                    "data BLOB," +
                    "source INTEGER NOT NULL" +
                    ")"
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ev_point ON events(dim,x,y,z,id)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ev_area ON events(dim,x,z,id)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ev_actor ON events(actor,id)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ev_time ON events(time)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ev_mat ON events(mat,id)")
        }
    }
}
