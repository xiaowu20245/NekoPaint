package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatus

/**
 * Queue 本地 pending 卡片的迁移判定。
 *
 * 优先使用记录自身持久化的 `supersededBy`，不依赖新记录是否已经进入内存读模型；
 * 兼容旧记录时再扫描 `requestedPromptId` 映射。
 */
internal object QueueMigrationPolicy {

    fun migratedTo(task: TaskRecord, tasks: List<TaskRecord>): String? {
        task.supersededBy?.takeIf { it.isNotBlank() }?.let { return it }
        val requestedId = task.requestedPromptId?.takeIf { it.isNotBlank() }
            ?: task.promptId
        // 只有原始请求记录才向后找新记录；新记录不能反向显示“已迁移到旧 ID”。
        if (task.promptId != requestedId) return null
        return tasks.firstOrNull { candidate ->
            candidate.promptId != task.promptId &&
                candidate.requestedPromptId == requestedId
        }?.promptId
    }

    fun canRetry(task: TaskRecord, migratedTo: String?): Boolean =
        task.status == TaskStatus.UNKNOWN && migratedTo == null
}
