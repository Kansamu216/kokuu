package org.voidkit.economy

import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.EconomyEntry
import org.voidkit.core.api.EconomyProvider
import org.voidkit.core.api.VoidkitServer
import org.voidkit.core.util.ConfigFiles
import net.minecraft.server.level.ServerPlayer
import java.text.DecimalFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 一个账户。 */
class Account {
    /** 最近一次看到的玩家名 —— 只为了方便人工查看这个文件，不参与任何逻辑。 */
    var name: String = ""

    var balance: Double = 0.0

    /** 最后一次变动的时间戳（毫秒），排查异常交易时有用。 */
    var lastChange: Long = 0L
}

/** `economy/accounts.json` 的结构。 */
class AccountFile {
    var accounts: MutableMap<String, Account> = linkedMapOf()
}

/**
 * 账户数据 + 默认经济实现。
 *
 * ## 落盘策略：脏标记 + 定期刷盘
 *
 * 每次交易都写整个 JSON 显然不行（几千个账户时每笔交易都要序列化全量）。
 * 所以只在内存改，标脏，由 [flush] 每 30 秒写一次，关服时强制写一次。
 *
 * 代价是崩溃时最多丢 30 秒的交易记录。对一个游戏经济来说这个权衡是合理的 ——
 * 换来的是「转账不会因为写盘卡住主线程」。要更保险就把 [FLUSH_INTERVAL_MS] 调小。
 *
 * ## 金额精度
 *
 * 所有写入都过 [EconomyProvider.round]，避免反复加减累积出
 * `10.000000000000002` 这种余额。
 */
object EconomyStore : EconomyProvider {

    /** 自动刷盘间隔。 */
    private const val FLUSH_INTERVAL_MS = 30_000L

    private const val FILE = "economy/accounts.json"

    private val accounts = ConcurrentHashMap<String, Account>()

    @Volatile
    private var dirty = false

    @Volatile
    private var lastFlush = 0L

    override val available: Boolean get() = VoidkitServer.isReady()

    // ---------- 读写 ----------

    fun load() {
        val file = ConfigFiles.load(FILE, AccountFile::class.java, emptyMap()) { AccountFile() }
        accounts.clear()
        file.accounts?.forEach { (uuid, account) ->
            if (account == null) return@forEach
            // 补 normalize —— Gson 反序列化缺字段时是 null / 0
            if (account.name == null) account.name = ""
            account.balance = round(account.balance)
            accounts[uuid.lowercase()] = account
        }
        dirty = false
        lastFlush = System.currentTimeMillis()
        VoidkitCore.LOG.info("经济数据就绪：{} 个账户", accounts.size)
    }

    /** 有新交易时才真正写盘。由 Feature 的每秒钩子调用。 */
    fun flushIfDirty() {
        if (!dirty) return
        if (System.currentTimeMillis() - lastFlush < FLUSH_INTERVAL_MS) return
        flush()
    }

    fun flush() {
        val snapshot = AccountFile()
        accounts.forEach { (uuid, account) -> snapshot.accounts[uuid] = account }
        ConfigFiles.save(FILE, snapshot)
        dirty = false
        lastFlush = System.currentTimeMillis()
    }

    /** 取账户；不存在就按配置初始化一个（新玩家自动开户）。 */
    private fun accountOf(uuid: UUID, name: String? = null): Account =
        accounts.computeIfAbsent(uuid.toString().lowercase()) {
            Account().apply {
                balance = EconomyConfigStore.config.startingBalance
                lastChange = System.currentTimeMillis()
            }
        }.also { if (!name.isNullOrBlank()) it.name = name }

    // ---------- EconomyProvider ----------

    override fun balance(uuid: UUID): Double = accounts[uuid.toString().lowercase()]?.balance ?: 0.0

    override fun deposit(player: ServerPlayer, amount: Double, reason: String): Double {
        val cfg = EconomyConfigStore.config
        val account = accountOf(player.uuid, player.gameProfile.name)
        val next = (account.balance + amount).coerceAtMost(cfg.maxBalance)
        account.balance = round(next.coerceAtLeast(0.0))
        account.lastChange = System.currentTimeMillis()
        dirty = true
        return account.balance
    }

    override fun withdraw(player: ServerPlayer, amount: Double, reason: String): Double {
        val account = accountOf(player.uuid, player.gameProfile.name)
        if (account.balance < amount) return -1.0
        account.balance = round(account.balance - amount)
        account.lastChange = System.currentTimeMillis()
        dirty = true
        return account.balance
    }

    override fun setBalance(player: ServerPlayer, amount: Double, reason: String) {
        val cfg = EconomyConfigStore.config
        val account = accountOf(player.uuid, player.gameProfile.name)
        account.balance = round(amount.coerceIn(0.0, cfg.maxBalance))
        account.lastChange = System.currentTimeMillis()
        dirty = true
    }

    override fun transfer(from: ServerPlayer, to: ServerPlayer, amount: Double): Boolean {
        val cfg = EconomyConfigStore.config
        val tax = round(amount * cfg.taxPercent / 100.0)
        val total = round(amount + tax)

        val fromAccount = accountOf(from.uuid, from.gameProfile.name)
        if (fromAccount.balance < total) return false

        fromAccount.balance = round(fromAccount.balance - total)
        fromAccount.lastChange = System.currentTimeMillis()

        val toAccount = accountOf(to.uuid, to.gameProfile.name)
        toAccount.balance = round((toAccount.balance + amount).coerceAtMost(cfg.maxBalance))
        toAccount.lastChange = System.currentTimeMillis()

        dirty = true
        return true
    }

    override fun top(limit: Int): List<EconomyEntry> = accounts.entries
        .asSequence()
        .filter { it.value.balance > 0 }
        .sortedByDescending { it.value.balance }
        .take(limit)
        .map { EconomyEntry(it.value.name.ifBlank { it.key }, it.key, it.value.balance) }
        .toList()

    override fun format(amount: Double): String {
        val cfg = EconomyConfigStore.config
        return cfg.format
            .replace("{amount}", AMOUNT_FORMAT.format(round(amount)))
            .replace("{currency}", cfg.currencyName)
    }

    /** 给面板/命令用的原始数字（不带货币名）。 */
    fun plain(amount: Double): String = AMOUNT_FORMAT.format(round(amount))

    // ---------- 迁移用 ----------

    /** 把一个旧数据源里的余额搬进来（给将来从别的经济插件迁移留的口子）。 */
    fun importBalance(uuid: UUID, name: String, amount: Double) {
        val account = accountOf(uuid, name)
        account.balance = round(amount)
        account.lastChange = System.currentTimeMillis()
        dirty = true
    }

    private val AMOUNT_FORMAT = DecimalFormat("#,##0.00")
}
