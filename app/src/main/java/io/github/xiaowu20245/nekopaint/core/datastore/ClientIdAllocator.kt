package io.github.xiaowu20245.nekopaint.core.datastore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 串行化 clientId 的首次分配，避免提交、WebSocket、TaskManager 并发生成不同 ID。
 * 读写通过 lambda 注入，便于纯 JVM 并发测试。
 */
internal class ClientIdAllocator(
    private val read: suspend () -> String?,
    private val write: suspend (String) -> Unit,
    private val generate: () -> String = { UUID.randomUUID().toString() },
) {

    private val mutex = Mutex()

    suspend fun getOrCreate(): String = mutex.withLock {
        read()?.takeIf { it.isNotBlank() } ?: generate().also { created ->
            write(created)
        }
    }
}
