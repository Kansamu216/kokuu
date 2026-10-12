package org.voidkit.core.api

import net.minecraft.server.MinecraftServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 当前服务端实例的持有者，以及「切回主线程」的工具。
 *
 * ## 为什么内核要有这个
 *
 * 管理 API 的请求跑在 HTTP 线程上，而世界/实体/玩家数据只能在服务端主线程碰。
 * 所以每个 API handler 都需要「把活扔回主线程并等结果」这个能力。
 *
 * 这个能力原先由 `ApiServer` 提供 —— 但那是 web 插件里的类，于是所有插件都被迫
 * 依赖 web 插件。现在它上移到内核：不装 web 插件，这个工具照样存在（只是没人调）。
 *
 * 用法：
 * ```kotlin
 * router.get("/api/spawn") { _ ->
 *     VoidkitServer.onServer {                       // 切主线程
 *         val server = VoidkitServer.require()
 *         ApiJson.obj("x", server.overworld().spawnPos.x)
 *     }
 * }
 * ```
 */
object VoidkitServer {

    private const val TIMEOUT_SECONDS = 20L

    @Volatile
    private var instance: MinecraftServer? = null

    fun get(): MinecraftServer? = instance

    fun isReady(): Boolean = instance != null

    fun require(): MinecraftServer = instance ?: error("服务端尚未就绪")

    /** 由内核的生命周期驱动调用。 */
    fun attach(server: MinecraftServer) {
        instance = server
    }

    fun detach() {
        instance = null
    }

    /**
     * 在服务端主线程上同步执行并返回结果。
     *
     * 已经在主线程时直接执行（避免自己等自己造成死锁）。跨线程时投递到服务端
     * 的执行队列并等待，超时则抛异常 —— 超时通常意味着某张地图卡住了，这种情况
     * 让请求失败比让它无限挂住更好。
     */
    fun <T> onServer(task: () -> T): T {
        val server = require()
        if (server.isSameThread) return task()

        val future = CompletableFuture<T>()
        server.execute {
            runCatching { future.complete(task()) }
                .onFailure { future.completeExceptionally(it) }
        }
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
}
