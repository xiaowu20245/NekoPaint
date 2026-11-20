package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.common.AtomicFileWriter
import io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * object_info 的 last-good 磁盘缓存。
 *
 * 缓存以 serverId + endpoint 双重隔离；TTL 内命中直接返回，失败请求不会调用
 * [save]，因此不会覆盖上一次成功结果。`fetchedAt` 使用注入时钟，便于测试。
 */
class NodeCatalogCache(
    private val rootDir: File,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    private val memory = mutableMapOf<String, CachedNodeCatalog>()
    private val memoryMutex = Mutex()

    suspend fun load(serverId: String, endpointKey: String): NodeCatalog? {
        val key = memoryKey(serverId, endpointKey)
        memoryMutex.withLock {
            memory[key]?.let { cached ->
                if (isFresh(cached, endpointKey)) return NodeCatalog(cached.nodes)
                memory.remove(key)
            }
        }
        return withContext(Dispatchers.IO) {
            val file = cacheFile(serverId, endpointKey)
            if (!file.exists()) return@withContext null
            runCatching {
                val cached = json.decodeFromString<CachedNodeCatalog>(file.readText())
                if (!isFresh(cached, endpointKey)) return@withContext null
                memoryMutex.withLock { memory[key] = cached }
                NodeCatalog(cached.nodes)
            }.getOrNull()
        }
    }

    suspend fun save(
        serverId: String,
        endpointKey: String,
        catalog: NodeCatalog,
    ) = withContext(Dispatchers.IO) {
        val payload = CachedNodeCatalog(
            version = CACHE_VERSION,
            endpointKey = endpointKey,
            fetchedAt = now(),
            nodes = catalog.nodes,
        )
        AtomicFileWriter.writeText(cacheFile(serverId, endpointKey), json.encodeToString(payload))
        memoryMutex.withLock { memory[memoryKey(serverId, endpointKey)] = payload }
        Unit
    }

    suspend fun invalidate(serverId: String) = withContext(Dispatchers.IO) {
        memoryMutex.withLock {
            memory.keys.removeAll { it.startsWith("$serverId\u0000") }
        }
        serverDir(serverId).deleteRecursively()
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryMutex.withLock { memory.clear() }
        rootDir.deleteRecursively()
        Unit
    }

    private fun isFresh(cached: CachedNodeCatalog, endpointKey: String): Boolean =
        cached.version == CACHE_VERSION &&
            cached.endpointKey == endpointKey &&
            now() - cached.fetchedAt in 0..ttlMs

    private fun memoryKey(serverId: String, endpointKey: String): String =
        "$serverId\u0000$endpointKey"

    private fun cacheFile(serverId: String, endpointKey: String): File =
        File(serverDir(serverId), "${endpointKey.sha256().take(HASH_LENGTH)}.json")

    private fun serverDir(serverId: String): File {
        val safe = serverId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(MAX_SERVER_DIR_LENGTH)
        return File(rootDir, safe.ifBlank { "default" })
    }

    @Serializable
    private data class CachedNodeCatalog(
        val version: Int,
        val endpointKey: String,
        val fetchedAt: Long,
        val nodes: Map<String, NodeInfoDto>,
    )

    private companion object {
        const val CACHE_VERSION = 1
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
        const val HASH_LENGTH = 32
        const val MAX_SERVER_DIR_LENGTH = 64
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
