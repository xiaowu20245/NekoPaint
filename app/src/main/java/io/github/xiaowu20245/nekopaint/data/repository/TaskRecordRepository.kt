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
            val scoped = record.copy(serverId = serverId)
            val key = key(serverId, scoped.promptId)
            if (tombstones.containsKey(key)) {
                _writeState.value = TaskRecordWriteState.Failed(
                    promptId = scoped.promptId,
                    message = "任务记录已删除，拒绝旧写入",
                )
                return@withLock Result.failure(
                    IllegalStateException("任务记录已删除，拒绝旧写入"),
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
            result
        }

    suspend fun get(serverId: String, promptId: String): TaskRecord? =
        taskStore.get(serverId, promptId)

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

    private fun key(serverId: String, promptId: String): String =
        "$serverId\u0000$promptId"
}
