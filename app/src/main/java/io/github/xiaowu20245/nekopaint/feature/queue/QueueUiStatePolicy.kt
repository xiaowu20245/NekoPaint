package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot

/**
 * 队列首屏状态。`queue == null` 不再一律显示 spinner：
 * 只有真正的首次加载/重试才显示 Loading，失败与未配置都显示可操作空状态。
 */
enum class QueueLoadState {
    Unconfigured,
    Loading,
    Refreshing,
    Error,
    LoadedWithError,
    Empty,
    Loaded,
}

object QueueUiStatePolicy {

    fun loadState(
        queue: QueueSnapshot?,
        refreshing: Boolean,
        error: NekoMessage?,
        configured: Boolean,
    ): QueueLoadState {
        if (!configured) return QueueLoadState.Unconfigured
        if (queue == null) {
            return when {
                refreshing -> QueueLoadState.Loading
                error != null -> QueueLoadState.Error
                else -> QueueLoadState.Loading
            }
        }
        return when {
            refreshing -> QueueLoadState.Refreshing
            error != null -> QueueLoadState.LoadedWithError
            queue.running.isEmpty() && queue.pending.isEmpty() -> QueueLoadState.Empty
            else -> QueueLoadState.Loaded
        }
    }
}
