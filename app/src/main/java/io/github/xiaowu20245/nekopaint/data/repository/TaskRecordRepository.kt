package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

sealed interface TaskRecordWriteState {
    data object Idle : TaskRecordWriteState
    data class Saved(val promptId: String) : TaskRecordWriteState
    data class Deleted(val promptId: String) : TaskRecordWriteState
    data class Failed(val promptId: String, val message: String) : TaskRecordWriteState
}

interface TaskRecordObserver {
    fun onTaskRecord(serverId: String, record: TaskRecord, sequence: Long) = Unit

    fun onTaskRecordDeleted(serverId: String, promptId: String, sequence: Long) = Unit
}

/**
 * 任务记录的统一写入口。
 *
 * 只依赖 TaskStore，不依赖 TaskManager；TaskManager 通过 observer 更新内存读模型，
 * 避免 recorder 与 TaskManager 形成构造/调用环。
 */
class TaskRecordRepository(
    private val taskStore: TaskStore,
) {

    private val _writeState = MutableStateFlow<TaskRecordWriteState>(TaskRecordWriteState.Idle)
    val writeState: StateFlow<TaskRecordWriteState> = _writeState.asStateFlow()

    private val observers = CopyOnWriteArrayList<TaskRecordObserver>()
    private val writeMutex = Mutex()
    private val sequence = AtomicLong(0)

    // v1：删除后同一 serverId+promptId 不允许重建，新 attempt 必须使用新 ID。
    // tombstone 仅进程内保存，重启后自然清理。
    private val tombstones = mutableMapOf<String, Long>()

    fun addObserver(observer: TaskRecordObserver) {
        observers.addIfAbsent(observer)
    }

    fun removeObserver(observer: TaskRecordObserver) {
        observers.remove(observer)
    }

    suspend fun upsert(serverId: String, record: TaskRecord): Result<Unit> =
        writeMutex.withLock {
            writeLocked(serverId, record)
        }

    /**
     * 结果未知重试的原子预写。
     *
     * 在读/写同一把锁内确认当前记录仍是 UNKNOWN/SUBMITTING，且不存在
     * `requestedPromptId == 旧 ID` 的迁移新记录，避免“重读后又被队列/历史升级”
     * 的竞态把已确认任务重新写回 SUBMITTING。
     */
    suspend fun prepareRetry(serverId: String, record: TaskRecord): Result<Unit> =
        writeMutex.withLock {
            val current = taskStore.get(serverId, record.promptId)
                ?: return@withLock failure(
                    promptId = record.promptId,
                    message = "本地任务记录已不存在，请刷新队列",
                )
            if (current.supersededBy != null) {
                return@withLock failure(
                    promptId = record.promptId,
                    message = "该请求已由任务 ${current.supersededBy.take(8)} 接管，请勿重复提交",
                )
            }
            if (current.status != TaskStatus.UNKNOWN &&
                current.status != TaskStatus.SUBMITTING
            ) {
                return@withLock failure(
                    promptId = record.promptId,
                    message = "任务状态已更新为 ${current.status}，请刷新队列后再试",
                )
            }
            val requestedId = current.requestedPromptId?.takeIf { it.isNotBlank() }
                ?: current.promptId
            // 只有“原始请求记录”才需要找迁移后的新记录；新记录本身不能反向把旧记录当目标。
            val migrated = if (current.promptId == requestedId) {
                taskStore.list(serverId).firstOrNull { candidate ->
                    candidate.promptId != current.promptId &&
                        candidate.requestedPromptId == requestedId
                }
            } else {
                null
            }
            if (migrated != null) {
                return@withLock failure(
                    promptId = record.promptId,
                    message = "该请求已由任务 ${migrated.promptId.take(8)} 接管，请勿重复提交",
                )
            }
            writeLocked(serverId, record)
        }

    suspend fun get(serverId: String, promptId: String): TaskRecord? =
        taskStore.get(serverId, promptId)

    suspend fun list(serverId: String): List<TaskRecord> = taskStore.list(serverId)

    suspend fun isDeleted(serverId: String, promptId: String): Boolean =
        writeMutex.withLock { tombstones.containsKey(key(serverId, promptId)) }

    suspend fun delete(serverId: String, promptId: String): Result<Unit> =
        writeMutex.withLock {
            val writeSequence = sequence.incrementAndGet()
            val result = taskStore.delete(serverId, promptId)
            result.onSuccess {
                tombstones[key(serverId, promptId)] = writeSequence
                _writeState.value = TaskRecordWriteState.Deleted(promptId)
                observers.forEach {
                    it.onTaskRecordDeleted(serverId, promptId, writeSequence)
                }
            }.onFailure { throwable ->
                _writeState.value = TaskRecordWriteState.Failed(
                    promptId = promptId,
                    message = throwable.message ?: "任务记录删除失败",
                )
            }
            result
        }

    private suspend fun writeLocked(serverId: String, record: TaskRecord): Result<Unit> {
        val scoped = record.copy(serverId = serverId)
        val recordKey = key(serverId, scoped.promptId)
        if (tombstones.containsKey(recordKey)) {
            return failure(
                promptId = scoped.promptId,
                message = "任务记录已删除，拒绝旧写入",
            )
        }
        val writeSequence = sequence.incrementAndGet()
        val result = runCatching { taskStore.update(serverId, scoped) }
        result.onSuccess {
            _writeState.value = TaskRecordWriteState.Saved(scoped.promptId)
            observers.forEach { it.onTaskRecord(serverId, scoped, writeSequence) }
        }.onFailure { throwable ->
            _writeState.value = TaskRecordWriteState.Failed(
                promptId = scoped.promptId,
                message = throwable.message ?: "任务记录保存失败",
            )
        }
        return result
    }

    private fun failure(promptId: String, message: String): Result<Unit> {
        _writeState.value = TaskRecordWriteState.Failed(
            promptId = promptId,
            message = message,
        )
        return Result.failure(IllegalStateException(message))
    }

    private fun key(serverId: String, promptId: String): String =
        "$serverId\u0000$promptId"
}
