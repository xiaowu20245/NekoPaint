package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface TaskRecordEvent {
    val serverId: String
    val promptId: String
    val sequence: Long

    data class Persisted(
        override val serverId: String,
        override val promptId: String,
        override val sequence: Long,
        val record: TaskRecord,
    ) : TaskRecordEvent

    data class Deleted(
        override val serverId: String,
        override val promptId: String,
        override val sequence: Long,
    ) : TaskRecordEvent
}

/**
 * 单消费者事件队列：保证 repository 通知按写入顺序进入 TaskManager 的 stateMutex。
 */
class TaskRecordEventDispatcher(
    scope: CoroutineScope,
    private val handler: suspend (TaskRecordEvent) -> Unit,
) {
    private sealed interface Item {
        data class Event(val event: TaskRecordEvent) : Item
        data class Ack(val deferred: CompletableDeferred<Unit>) : Item
    }

    private val channel = Channel<Item>(Channel.UNLIMITED)
    @Volatile
    private var closed = false
    private val lock = Any()

    init {
        scope.launch {
            try {
                for (item in channel) {
                    when (item) {
                        is Item.Event -> try {
                            handler(item.event)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // Handler 失败不能让后续 flush/事件永久阻塞。
                        }
                        is Item.Ack -> item.deferred.complete(Unit)
                    }
                }
            } finally {
                closed = true
            }
        }
    }

    fun offer(event: TaskRecordEvent) {
        if (closed) return
        channel.trySend(Item.Event(event))
    }

    /** 等待此前 offer 的事件全部被消费；超时/关闭返回 false。 */
    suspend fun flush(timeoutMs: Long = 2_000L): Boolean {
        if (closed) return false
        val ack = CompletableDeferred<Unit>()
        if (!channel.trySend(Item.Ack(ack)).isSuccess) return false
        return withTimeoutOrNull(timeoutMs) {
            ack.await()
            true
        } ?: false
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            channel.close()
        }
    }
}
