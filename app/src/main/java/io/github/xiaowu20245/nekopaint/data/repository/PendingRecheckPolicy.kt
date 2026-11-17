package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot

/**
 * unknown 任务定向检查的可测编排：
 * 队列命中 -> true；否则定向 history 命中 -> true；都未命中 -> false。
 * 任一查询失败返回 failure，调用方不得把失败当成“未找到”。
 */
internal object PendingRecheckPolicy {

    suspend fun recheck(
        promptId: String,
        loadQueue: suspend () -> Result<QueueSnapshot>,
        loadHistory: suspend () -> Result<HistoryEntry?>,
        applyQueue: suspend (QueueSnapshot) -> Unit,
        applyHistory: suspend (HistoryEntry) -> Unit,
        markNotFound: suspend () -> Unit,
    ): Result<Boolean> {
        val queue = loadQueue().getOrElse { throwable ->
            return Result.failure(throwable)
        }
        applyQueue(queue)
        val inQueue = queue.running.any { it.promptId == promptId } ||
            queue.pending.any { it.promptId == promptId }
        if (inQueue) return Result.success(true)

        val history = loadHistory().getOrElse { throwable ->
            return Result.failure(throwable)
        }
        if (history != null) {
            applyHistory(history)
            return Result.success(true)
        }
        markNotFound()
        return Result.success(false)
    }
}
