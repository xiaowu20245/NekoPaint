package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 任务持久化事件与内存读模型之间的可测协调器。
 *
 * Repository 的写事件按 sequence 投递到单消费者；remove/replace 通过 flush 在返回前
 * 等待内存读模型消费完成，保证调用方立即读取 tasks 时已一致。
 */
class TaskRecordEventCoordinator(
    private val scope: CoroutineScope,
    private val repository: TaskRecordRepository,
    private val stateMutex: Mutex,
    private val tasks: MutableStateFlow<List<TaskRecord>>,
    private val currentServerId: () -> String?,
    private val localClientId: () -> String?,
    private val queueSnapshot: () -> QueueSnapshot?,
    private val persist: (TaskRecord) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    cleanupEnabled: Boolean = true,
) {

    private data class PendingUpdate(
        val promptId: String,
        val serverId: String,
        val source: TaskStatusSource,
        val now: Long,
        val createdAt: Long,
        val transform: (TaskRecord) -> TaskRecord,
    )

    private val sequenceGate = TaskRecordSequenceGate()
    private val pendingUnknownEvents = LinkedHashMap<String, PendingUpdate>()
    private val pendingLock = Any()
    private val cleanupJob = if (cleanupEnabled) {
        scope.launch {
            while (true) {
                delay(PENDING_CLEANUP_INTERVAL_MS)
                prunePending(now())
            }
        }
    } else {
        null
    }
    private val dispatcher = TaskRecordEventDispatcher(scope) { event ->
        stateMutex.withLock {
            if (!sequenceGate.accept(event.sequence)) return@withLock
            if (event.serverId != currentServerId()) return@withLock
            when (event) {
                is TaskRecordEvent.Persisted -> {
                    tasks.value = TaskListSyncPolicy.applyPersisted(tasks.value, event.record)
                }

                is TaskRecordEvent.Deleted -> {
                    tasks.value = tasks.value.filterNot { it.promptId == event.promptId }
                }
            }
        }
    }

    private val observer = object : TaskRecordObserver {
        override fun onTaskRecord(
            serverId: String,
            record: TaskRecord,
            sequence: Long,
        ) {
            dispatcher.offer(
                TaskRecordEvent.Persisted(
                    serverId = serverId,
                    promptId = record.promptId,
                    sequence = sequence,
                    record = record,
                ),
            )
        }

        override fun onTaskRecordDeleted(
            serverId: String,
            promptId: String,
            sequence: Long,
        ) {
            dispatcher.offer(
                TaskRecordEvent.Deleted(
                    serverId = serverId,
                    promptId = promptId,
                    sequence = sequence,
                ),
            )
        }
    }

    init {
        repository.addObserver(observer)
    }

    suspend fun updateTask(
        promptId: String,
        source: TaskStatusSource,
        transform: (TaskRecord) -> TaskRecord,
    ) {
        stateMutex.withLock {
            updateTaskLocked(promptId, source, transform)
        }
    }

    /**
     * 调用方已经持有 `stateMutex` 时使用（例如 TaskManager 的 WS 事件处理）。
     * 不会再次获取 stateMutex，避免 kotlinx Mutex 非重入死锁。
     */
    suspend fun updateTaskLocked(
        promptId: String,
        source: TaskStatusSource,
        transform: (TaskRecord) -> TaskRecord,
    ) {
        val now = now()
        val serverId = activeServerId()
        val current = tasks.value
        val existing = current.firstOrNull { it.promptId == promptId }
        val stored = existing ?: repository.get(serverId, promptId)
        val queueItem = queueSnapshot()
            ?.let { it.running + it.pending }
            ?.firstOrNull { it.promptId == promptId }
        val localId = localClientId()
        if (isConfirmedExternal(queueItem, localId)) return
        if (stored == null && !isConfirmedLocal(queueItem, localId)) {
            rememberPending(promptId, serverId, source, now, transform)
            return
        }
        applyUpdate(
            promptId = promptId,
            serverId = serverId,
            source = source,
            now = now,
            transform = transform,
            stored = stored,
        )
    }

    /** 在 clientId/队列快照到达后重放暂存的未知事件。 */
    suspend fun flushPendingUpdates(now: Long = now()) {
        stateMutex.withLock {
            val snapshot = synchronized(pendingLock) {
                pendingUnknownEvents.entries.map { it.key to it.value }
            }
            snapshot.forEach { (key, pending) ->
                if (pending.serverId != activeServerId()) {
                    removePendingKey(key)
                    return@forEach
                }
                if (now - pending.createdAt > PENDING_TTL_MS) {
                    removePendingKey(key)
                    return@forEach
                }
                if (repository.isDeleted(pending.serverId, pending.promptId)) {
                    removePendingKey(key)
                    return@forEach
                }
                val existing = tasks.value.firstOrNull { it.promptId == pending.promptId }
                val stored = existing ?: repository.get(pending.serverId, pending.promptId)
                val queueItem = queueSnapshot()
                    ?.let { it.running + it.pending }
                    ?.firstOrNull { it.promptId == pending.promptId }
                val localId = localClientId()
                if (isConfirmedExternal(queueItem, localId)) {
                    removePendingKey(key)
                    return@forEach
                }
                if (stored != null || isConfirmedLocal(queueItem, localId)) {
                    applyUpdate(
                        promptId = pending.promptId,
                        serverId = pending.serverId,
                        source = pending.source,
                        now = pending.now,
                        transform = pending.transform,
                        stored = stored,
                    )
                    removePendingKey(key)
                }
            }
        }
    }

    fun clearPendingUpdates() {
        synchronized(pendingLock) { pendingUnknownEvents.clear() }
    }

    fun close() {
        cleanupJob?.cancel()
        dispatcher.close()
    }

    /** 周期/测试触发：清理过期 pending，容量超限时优先移除过期/异服务器项。 */
    fun prunePending(now: Long = now()) {
        synchronized(pendingLock) {
            pendingUnknownEvents.entries.removeAll { (_, pending) ->
                now - pending.createdAt > PENDING_TTL_MS ||
                    pending.serverId != activeServerId()
            }
            while (pendingUnknownEvents.size > MAX_PENDING_EVENTS) {
                pendingUnknownEvents.remove(pendingUnknownEvents.keys.firstOrNull())
            }
        }
    }

    suspend fun remove(serverId: String, promptId: String): Result<Unit> {
        removePending(serverId, promptId)
        val result = repository.delete(serverId, promptId)
        if (result.isFailure) return result
        return if (dispatcher.flush()) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException("本地记录已删除，但内存事件同步超时（可安全重试）"),
            )
        }
    }

    suspend fun replace(
        serverId: String,
        oldPromptId: String,
        record: TaskRecord,
    ): Result<Unit> {
        if (oldPromptId == record.promptId) {
            return Result.failure(
                IllegalArgumentException("replace 需要不同的 promptId"),
            )
        }
        removePending(serverId, oldPromptId)
        val deleted = repository.delete(serverId, oldPromptId)
        if (deleted.isFailure) return deleted
        val saved = repository.upsert(serverId, record)
        if (saved.isFailure) return saved
        return if (dispatcher.flush()) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException("本地记录已替换，但内存事件同步超时（可安全重试）"),
            )
        }
    }

    fun isConfirmedLocal(queueItem: QueueItem?, localClientId: String?): Boolean =
        queueItem != null &&
            queueItem.clientId != null &&
            localClientId != null &&
            queueItem.clientId == localClientId

    fun isConfirmedExternal(queueItem: QueueItem?, localClientId: String?): Boolean =
        queueItem != null &&
            queueItem.clientId != null &&
            localClientId != null &&
            queueItem.clientId != localClientId

    private suspend fun applyUpdate(
        promptId: String,
        serverId: String,
        source: TaskStatusSource,
        now: Long,
        transform: (TaskRecord) -> TaskRecord,
        stored: TaskRecord?,
    ) {
        val base = stored ?: TaskRecord(
            promptId = promptId,
            serverId = serverId,
            templateId = "",
            templateName = "",
            clientId = localClientId().orEmpty(),
            submittedAt = now,
            requestedAt = now,
            status = TaskStatus.QUEUED,
            statusUpdatedAt = now,
            statusSource = TaskStatusSource.LOCAL.name,
        )
        tasks.value = TaskListSyncPolicy.applyEvent(
            tasks = tasks.value,
            promptId = promptId,
            source = source,
            now = now,
            fallback = base,
            transform = transform,
        )
        // 未知事件只影响内存展示，不落盘空 template；完整记录由预写/队列/历史补齐。
        if (stored != null) {
            tasks.value.firstOrNull { it.promptId == promptId }?.let(persist)
        }
    }

    private fun rememberPending(
        promptId: String,
        serverId: String,
        source: TaskStatusSource,
        now: Long,
        transform: (TaskRecord) -> TaskRecord,
    ) {
        val key = "$serverId\u0000$promptId"
        synchronized(pendingLock) {
            if (pendingUnknownEvents.size >= MAX_PENDING_EVENTS) {
                pendingUnknownEvents.remove(pendingUnknownEvents.keys.firstOrNull())
            }
            pendingUnknownEvents[key] = PendingUpdate(
                promptId = promptId,
                serverId = serverId,
                source = source,
                now = now,
                createdAt = now,
                transform = transform,
            )
        }
    }

    private fun removePending(serverId: String, promptId: String) {
        removePendingKey("$serverId\u0000$promptId")
    }

    private fun removePendingKey(key: String) {
        synchronized(pendingLock) { pendingUnknownEvents.remove(key) }
    }

    private fun activeServerId(): String =
        currentServerId() ?: ServerConfigStore.DEFAULT_SERVER_ID

    private companion object {
        const val MAX_PENDING_EVENTS = 256
        const val PENDING_TTL_MS = 30_000L
        const val PENDING_CLEANUP_INTERVAL_MS = 5_000L
    }
}
