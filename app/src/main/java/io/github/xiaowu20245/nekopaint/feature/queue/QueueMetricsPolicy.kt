package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusMapper
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoTaskStatus

data class QueueMetrics(
    val batchIndex: Int?,
    val batchCount: Int?,
    val showBatch: Boolean,
    val showOverall: Boolean,
    val showNodeProgress: Boolean,
    val status: NekoTaskStatus,
    val etaMillis: Long?,
)

/**
 * 队列卡片指标的唯一判定入口。
 *
 * 不可信的批次/ETA/进度一律隐藏，不用 0 或 1/1 占位。
 */
object QueueMetricsPolicy {

    fun forTask(
        task: TaskRecord?,
        progress: TaskProgress?,
        now: Long,
    ): QueueMetrics {
        val batchIndex = task?.batchIndex
        val batchCount = task?.batchCount
        val showBatch = batchCount != null &&
            batchCount > 1 &&
            batchIndex != null &&
            batchIndex in 0 until batchCount
        val value = progress?.value
        val max = progress?.max
        return QueueMetrics(
            batchIndex = batchIndex,
            batchCount = batchCount,
            showBatch = showBatch,
            showOverall = (progress?.totalNodes ?: 0) > 0,
            showNodeProgress = value != null && max != null && max > 0,
            status = NekoStatusMapper.task(progress?.status ?: task?.status),
            etaMillis = etaMillis(progress, now),
        )
    }

    fun etaMillis(progress: TaskProgress?, now: Long): Long? {
        val startedAt = progress?.nodeStartedAt ?: return null
        val value = progress.value ?: return null
        val max = progress.max ?: return null
        if (value <= 0 || max <= value) return null
        val elapsedMillis = (now - startedAt).coerceAtLeast(1L)
        val perSecond = value / (elapsedMillis / 1000.0)
        if (perSecond <= 0) return null
        return (((max - value) / perSecond) * 1000.0)
            .toLong()
            .coerceAtLeast(0L)
    }
}
