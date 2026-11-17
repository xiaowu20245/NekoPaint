package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord

/** TaskManager 读模型的纯合并函数，便于在不构造 Android 依赖的情况下测试。 */
object TaskListSyncPolicy {

    data class Change(
        val tasks: List<TaskRecord>,
        val changed: List<TaskRecord>,
    )

    /**
     * 启动/刷新 reload：先把超时 submitting 归一化为 unknown，再与内存读模型合并。
     * [Change.changed] 是需要再次落盘的记录（TaskManager 负责 upsert）。
     */
    fun reloadWithRecovery(
        stored: List<TaskRecord>,
        live: List<TaskRecord>,
        now: Long,
    ): Change {
        val normalized = PendingSubmissionRecoveryPolicy.normalizeAll(stored, now)
        val changed = normalized.zip(stored).mapNotNull { (record, original) ->
            record.takeIf { it != original }
        }
        return Change(
            tasks = reload(normalized, live),
            changed = changed,
        )
    }

    fun reload(stored: List<TaskRecord>, live: List<TaskRecord>): List<TaskRecord> {
        val liveById = live.associateBy { it.promptId }
        val merged = LinkedHashMap<String, TaskRecord>()
        stored.forEach { record ->
            val local = liveById[record.promptId]
            merged[record.promptId] = if (local != null) {
                TaskStatusSyncPolicy.merge(local, record, TaskStatusSource.DISK)
            } else {
                record
            }
        }
        liveById.forEach { (promptId, record) ->
            if (promptId !in merged) merged[promptId] = record
        }
        return merged.values.sortedByDescending { it.requestedAt ?: it.submittedAt }
    }

    fun applyQueue(
        tasks: List<TaskRecord>,
        snapshot: QueueSnapshot,
        now: Long,
    ): Change {
        val running = snapshot.running.map { it.promptId }.toSet()
        val pending = snapshot.pending.map { it.promptId }.toSet()
        val changed = mutableListOf<TaskRecord>()
        val updated = tasks.map { task ->
            val targetStatus = when (task.promptId) {
                in running -> TaskStatus.RUNNING
                in pending -> TaskStatus.QUEUED
                else -> null
            } ?: return@map task
            if (targetStatus == task.status) return@map task
            val incoming = task.copy(
                status = targetStatus,
                startedAt = if (targetStatus == TaskStatus.RUNNING) {
                    task.startedAt ?: now
                } else {
                    task.startedAt
                },
                statusUpdatedAt = now,
                statusSource = TaskStatusSource.QUEUE.name,
            )
            val merged = TaskStatusSyncPolicy.merge(task, incoming, TaskStatusSource.QUEUE)
            if (merged != task) changed += merged
            merged
        }
        return Change(updated, changed)
    }

    fun applyHistory(
        tasks: List<TaskRecord>,
        entries: List<HistoryEntry>,
        now: Long,
    ): Change {
        val byId = entries.associateBy { it.promptId }
        val changed = mutableListOf<TaskRecord>()
        val updated = tasks.map { task ->
            val entry = byId[task.promptId] ?: return@map task
            val status = when (entry.status) {
                TaskStatus.SUCCESS -> TaskStatus.SUCCESS
                TaskStatus.FAILED -> TaskStatus.FAILED
                TaskStatus.INTERRUPTED -> TaskStatus.INTERRUPTED
                else -> return@map task
            }
            val incoming = task.copy(
                status = status,
                startedAt = task.startedAt ?: entry.startedAt,
                finishedAt = entry.finishedAt ?: entry.timestamp ?: task.finishedAt,
                errorMessage = if (status == TaskStatus.SUCCESS) {
                    null
                } else {
                    entry.errorMessage ?: task.errorMessage
                },
                statusUpdatedAt = entry.finishedAt
                    ?: entry.timestamp
                    ?: entry.startedAt
                    ?: now,
                statusSource = TaskStatusSource.HISTORY.name,
            )
            val merged = TaskStatusSyncPolicy.merge(task, incoming, TaskStatusSource.HISTORY)
            if (merged != task) changed += merged
            merged
        }
        return Change(updated, changed)
    }

    fun applyPersisted(
        tasks: List<TaskRecord>,
        record: TaskRecord,
    ): List<TaskRecord> {
        val existing = tasks.firstOrNull { it.promptId == record.promptId }
        val source = TaskStatusSource.from(record.statusSource) ?: TaskStatusSource.LOCAL
        val merged = if (existing == null) {
            record
        } else {
            TaskStatusSyncPolicy.merge(existing, record, source)
        }
        return (
            tasks.filterNot { it.promptId == record.promptId } + merged
            ).sortedByDescending { it.requestedAt ?: it.submittedAt }
    }

    fun applyEvent(
        tasks: List<TaskRecord>,
        promptId: String,
        source: TaskStatusSource,
        now: Long,
        fallback: TaskRecord,
        transform: (TaskRecord) -> TaskRecord,
    ): List<TaskRecord> {
        val existing = tasks.firstOrNull { it.promptId == promptId }
        val local = existing ?: fallback
        val incoming = transform(local).copy(
            statusUpdatedAt = now,
            statusSource = source.name,
        )
        val merged = TaskStatusSyncPolicy.merge(local, incoming, source)
        return (
            tasks.filterNot { it.promptId == promptId } + merged
            ).sortedByDescending { it.requestedAt ?: it.submittedAt }
    }
}
