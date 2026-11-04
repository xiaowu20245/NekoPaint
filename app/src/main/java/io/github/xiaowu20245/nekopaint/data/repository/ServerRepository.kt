package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.data.mapper.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

class InvalidComfyResponseException : IllegalStateException("Response is not ComfyUI system_stats")

class ServerRepository(
    private val store: ServerConfigStore,
    private val apiFactory: ComfyApiFactory,
) {

    suspend fun loadConfig(): ServerConfig? = store.config.first()

    suspend fun saveConfig(config: ServerConfig) {
        store.save(config)
    }

    suspend fun check(config: ServerConfig): Result<ConnectionSnapshot> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        runCatching {
            val stats = apiFactory.create(config).systemStats()
            if (stats.system == null && stats.devices.isEmpty()) {
                throw InvalidComfyResponseException()
            }
            val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
            ConnectionSnapshot(
                stats = stats.toDomain(),
                latencyMs = latencyMs,
                checkedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * 仅用于测试/诊断：判断 HTTP 状态是否属于可重试的服务端错误。
     */
    fun isServerError(code: Int): Boolean = code >= HttpURLConnection.HTTP_INTERNAL_ERROR
}
