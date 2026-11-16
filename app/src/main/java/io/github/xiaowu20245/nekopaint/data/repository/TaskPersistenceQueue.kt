package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces task persistence by server + prompt id.
 *
 * Progress events can arrive much faster than disk writes; only the latest
 * record per task is kept. The writer runs in arrival order. Background drains
 * skip records that no longer belong to the current identity, while [flush]
 * and [shutdown] force-write everything pending, including the last snapshot
 * of the previous server.
 */
class TaskPersistenceQueue(
    private val scope: CoroutineScope,
    private val isCurrent: (ServerIdentity, Long) -> Boolean,
    private val writer: suspend (String, TaskRecord) -> Unit,
    private val onWriteError: (String, TaskRecord, Throwable) -> Unit = { _, _, _ -> },
) {

    private data class Entry(
        val key: String,
        val serverId: String,
        val record: TaskRecord,
        val identity: ServerIdentity,
        val generation: Long,
    )

    private val pending = LinkedHashMap<String, Entry>()
    private val lock = Any()
    private val drainMutex = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var closed = false

    init {
        scope.launch {
            for (ignored in signal) {
                drain(force = false)
            }
        }
    }

    fun enqueue(
        serverId: String,
        record: TaskRecord,
        identity: ServerIdentity,
        generation: Long,
    ) {
        synchronized(lock) {
            if (closed) return
            val key = "$serverId\u0000${record.promptId}"
            if (pending.size >= MAX_PENDING && !pending.containsKey(key)) {
                pending.remove(pending.keys.firstOrNull())
            }
            pending[key] = Entry(
                key = key,
                serverId = serverId,
                record = record,
                identity = identity,
                generation = generation,
            )
        }
        signal.trySend(Unit)
    }

    /**
     * Writes every pending record, regardless of the current identity token.
     * Used before an identity switch and on shutdown.
     */
    suspend fun flush() {
        drain(force = true)
    }

    /**
     * Stops accepting new records, writes the final snapshots, and closes the
     * consumer signal. Safe to call after the owning scope was cancelled.
     */
    suspend fun shutdown() {
        synchronized(lock) { closed = true }
        drain(force = true)
        signal.close()
    }

    private suspend fun drain(force: Boolean) {
        drainMutex.withLock {
            while (true) {
                val batch = synchronized(lock) {
                    if (force) {
                        val values = pending.values.toList()
                        pending.clear()
                        values
                    } else {
                        val current = mutableListOf<Entry>()
                        val stale = mutableListOf<Entry>()
                        pending.values.forEach { entry ->
                            if (isCurrent(entry.identity, entry.generation)) {
                                current += entry
                            } else {
                                stale += entry
                            }
                        }
                        pending.clear()
                        stale.forEach { entry -> pending[entry.key] = entry }
                        current
                    }
                }
                if (batch.isEmpty()) return@withLock
                batch.forEach { entry -> writeWithRetry(entry) }
            }
        }
    }

    private suspend fun writeWithRetry(entry: Entry) {
        for (attempt in 0..1) {
            try {
                writer(entry.serverId, entry.record)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (attempt == 1) {
                    onWriteError(
                        entry.serverId,
                        entry.record,
                        throwable,
                    )
                }
            }
        }
    }

    private companion object {
        const val MAX_PENDING = 512
    }
}
