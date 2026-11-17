package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord

/**
 * 启动/刷新时对本地 pending 任务做保守恢复。
 *
 * 只有超过 30 秒仍停留在 submitting 的记录才降级为 unknown；
 * 队列/历史中的命中由 TaskListSyncPolicy 负责升级，不能在这里猜测成功或失败。
 */
object PendingSubmissionRecoveryPolicy {

    const val STALE_SUBMITTING_MS = 30_000L

    fun normalize(record: TaskRecord, now: Long): TaskRecord {
        if (record.status != TaskStatus.SUBMITTING) return record
        val requestedAt = record.requestedAt ?: record.submittedAt
        if (now - requestedAt < STALE_SUBMITTING_MS) return record
        return record.copy(
            status = TaskStatus.UNKNOWN,
            statusUpdatedAt = now,
            statusSource = TaskStatusSource.LOCAL.name,
        )
    }

    fun normalizeAll(records: List<TaskRecord>, now: Long): List<TaskRecord> =
        records.map { normalize(it, now) }
}
