package org.kokuu.web

import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.minecraft.server.MinecraftServer
import org.kokuu.core.KokuuCore
import org.kokuu.core.api.ApiJson
import org.kokuu.core.api.ApiRouter
import org.kokuu.core.core.PluginRegistry
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 内嵌管理 API（HTTP + JSON），供 Web 管理面板调用。
 *
 * 用 JDK 自带的 `com.sun.net.httpserver`：Minecraft 的 classpath 里没有
 * netty-codec-http，所以不能拿 Netty 写 HTTP；这样也不用额外依赖。
 *
 * **面板与模组完全分离**：这里只提供 API，界面是仓库 `panel/` 下的独立静态站点，
 * 可放任意静态服务器上。因此这里必须开 CORS —— 面板与 API 通常不同源。
 *
 * 安全：默认只监听 127.0.0.1；除 `/api/health` 外全部要求
 * `Authorization: Bearer <token>` 或 `X-Auth-Token`。
 */
class ApiServer private constructor() {

    companion object {
        private val INSTANCE = ApiServer()
        private const val MAX_BODY_BYTES = 1 shl 20

        fun get(): ApiServer = INSTANCE
    }

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val router = ApiRouter()
    private val rateCounters = ConcurrentHashMap<String, AtomicInteger>()

    private var httpServer: HttpServer? = null
    private var executor: ExecutorService? = null

    @Volatile
    private var minecraftServer: MinecraftServer? = null

    private var rateWindowStart: Long = System.currentTimeMillis() / 1000L
    private var routesRegistered = false

    fun router(): ApiRouter = router

    fun running(): Boolean = httpServer != null

    fun server(): MinecraftServer = minecraftServer
        ?: throw IllegalStateException("服务端尚未就绪")

    // ---------- 启停 ----------

    @Synchronized
    fun start(server: MinecraftServer) {
        minecraftServer = server
        if (running()) return

        if (!routesRegistered) {
            // 路由由各模块自己挂：RemoteApiFeature 负责内核通用接口（ApiDefaults），
            // 其余模块挂各自的路由。这里只管"把大家的入口都装上"。
            PluginRegistry.registerApi(router)
            routesRegistered = true
        }

        val bind = KokuuCore.config.apiBind
        val port = KokuuCore.config.apiPort
        val address = if (bind.isBlank()) InetSocketAddress(port) else InetSocketAddress(bind, port)

        try {
            val server2 = HttpServer.create(address, 0)
            server2.createContext("/", ::handle)
            val pool = Executors.newFixedThreadPool(4) { runnable ->
                Thread(runnable, "KokuuCore-API").apply { isDaemon = true }
            }
            server2.executor = pool
            server2.start()

            httpServer = server2
            executor = pool
            KokuuCore.LOG.info("管理 API 已启动: http://{}:{}", address.hostString, address.port)
            if (bind == "0.0.0.0") {
                KokuuCore.LOG.warn("管理 API 正在监听所有网卡，请确保 apiToken 严格保密")
            }
        } catch (t: Throwable) {
            KokuuCore.LOG.error("管理 API 启动失败: {}", t.toString())
            stop()
        }
    }

    @Synchronized
    fun stop() {
        runCatching { httpServer?.stop(0) }
        httpServer = null
        runCatching { executor?.shutdownNow() }
        executor = null
    }

    // ---------- 主线程桥接 ----------

    /**
     * 把任务丢回服务端主线程执行并等待结果。
     * 所有涉及世界 / 玩家状态的操作都必须走这里。
     */
    fun <T> onServer(task: Callable<T>): T {
        val server = minecraftServer ?: throw IllegalStateException("服务端尚未就绪")
        if (server.isSameThread) return task.call()
        val future = CompletableFuture<T>()
        server.execute {
            runCatching { future.complete(task.call()) }
                .onFailure { future.completeExceptionally(it) }
        }
        return try {
            future.get(20, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            // 拆掉包装，让 IllegalArgumentException 之类的业务异常能映射回 400 而不是 500
            throw e.cause ?: e
        }
    }

    // ---------- 请求处理 ----------

    private fun handle(exchange: HttpExchange) {
        try {
            val request = ApiRouter.Request().apply {
                method = exchange.requestMethod
                val uri = exchange.requestURI
                path = uri.path
                query.putAll(parseQuery(uri.rawQuery))
                body = readBody(exchange)
                remoteIp = remoteIp(exchange)
                authToken = extractToken(exchange)
            }

            when {
                request.method.equals("OPTIONS", ignoreCase = true) -> {
                    respond(exchange, 204, null)
                    return
                }

                request.path != "/api/health" && !authorized(request.authToken) -> {
                    respond(exchange, 401, ApiJson.error("unauthorized", "缺少或错误的访问令牌"))
                    return
                }

                !rateOk(request.remoteIp) -> {
                    respond(exchange, 429, ApiJson.error("rate_limited", "请求过于频繁"))
                    return
                }
            }

            val handler = router.match(request)
            if (handler == null) {
                respond(exchange, 404, ApiJson.error("not_found", "接口不存在: ${request.path}"))
                return
            }

            try {
                respond(exchange, 200, handler.handle(request))
            } catch (e: IllegalArgumentException) {
                respond(exchange, 400, ApiJson.error("bad_request", e.message ?: "参数错误"))
            } catch (e: IllegalStateException) {
                respond(exchange, 400, ApiJson.error("bad_state", e.message ?: "状态错误"))
            } catch (t: Throwable) {
                KokuuCore.LOG.error("API 处理失败 {} {}", request.method, request.path, t)
                respond(exchange, 500, ApiJson.error("internal_error", t.message ?: t.toString()))
            }
        } catch (t: Throwable) {
            runCatching { respond(exchange, 500, ApiJson.error("internal_error", t.message ?: t.toString())) }
        } finally {
            exchange.close()
        }
    }

    private fun authorized(token: String): Boolean {
        val expected = KokuuCore.config.apiToken
        return expected.isNotBlank() && expected == token
    }

    private fun extractToken(exchange: HttpExchange): String {
        val auth = exchange.requestHeaders.getFirst("Authorization")
        if (auth != null && auth.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)) {
            return auth.substring(7).trim()
        }
        exchange.requestHeaders.getFirst("X-Auth-Token")?.let { return it.trim() }
        val query = exchange.requestURI.rawQuery ?: return ""
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "token") {
                return decode(pair.substring(eq + 1))
            }
        }
        return ""
    }

    private fun remoteIp(exchange: HttpExchange): String {
        exchange.requestHeaders.getFirst("X-Forwarded-For")?.let { forwarded ->
            if (forwarded.isNotBlank()) {
                return forwarded.substringBefore(',').trim()
            }
        }
        return exchange.remoteAddress?.address?.hostAddress ?: "unknown"
    }

    private fun readBody(exchange: HttpExchange): String = runCatching {
        exchange.requestBody.use { it.readNBytes(MAX_BODY_BYTES).toString(StandardCharsets.UTF_8) }
    }.getOrDefault("")

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = HashMap<String, String>()
        rawQuery.split("&").forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val eq = pair.indexOf('=')
            if (eq < 0) {
                result[decode(pair)] = ""
            } else {
                result[decode(pair.substring(0, eq))] = decode(pair.substring(eq + 1))
            }
        }
        return result
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    @Synchronized
    private fun rateOk(ip: String): Boolean {
        val limit = KokuuCore.config.apiRateLimitPerSecond.coerceAtLeast(1)
        val second = System.currentTimeMillis() / 1000L
        if (second != rateWindowStart) {
            rateWindowStart = second
            rateCounters.clear()
        }
        return rateCounters.computeIfAbsent(ip) { AtomicInteger() }.incrementAndGet() <= limit
    }

    private fun respond(exchange: HttpExchange, status: Int, body: Any?) {
        val payload = when (body) {
            null -> ByteArray(0)
            is String -> body.toByteArray(StandardCharsets.UTF_8)
            else -> gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        }
        val headers = exchange.responseHeaders
        headers.set("Content-Type", "application/json; charset=utf-8")
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Headers", "authorization,content-type,x-auth-token")
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")

        if (status == 204 || payload.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
            return
        }
        exchange.sendResponseHeaders(status, payload.size.toLong())
        exchange.responseBody.use { out: OutputStream -> out.write(payload) }
    }
}
