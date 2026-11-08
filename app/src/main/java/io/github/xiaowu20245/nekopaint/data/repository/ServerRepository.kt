package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
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

    val servers = store.servers
    val activeServerId = store.activeServerId
    val activeServer = store.activeServer

    suspend fun loadConfig(): ServerConfig? = store.config.first()

    suspend fun saveConfig(config: ServerConfig) {
        store.save(config)
    }

    suspend fun saveServer(profile: ServerProfile, activate: Boolean = false) {
        store.saveServer(profile, activate)
    }

    suspend fun deleteServer(id: String) {
        store.deleteServer(id)
    }

    suspend fun setActive(id: String) {
        store.setActive(id)
    }

    suspend fun check(config: ServerConfig): Result<ConnectionSnapshot> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        runCatching {
            val api = apiFactory.create(config)
            val stats = api.systemStats()
            if (stats.system == null && stats.devices.isEmpty()) {
                throw InvalidComfyResponseException()
            }
            val queue = runCatching { api.queue() }.getOrNull()
            val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
            ConnectionSnapshot(
                stats = stats.toDomain(),
                latencyMs = latencyMs,
                checkedAt = System.currentTimeMillis(),
                runningCount = queue?.running?.size ?: 0,
                pendingCount = queue?.pending?.size ?: 0,
            )
        }
    }

    /**
     * 仅用于测试/诊断：判断 HTTP 状态是否属于可重试的服务端错误。
     */
    fun isServerError(code: Int): Boolean = code >= HttpURLConnection.HTTP_INTERNAL_ERROR
}
