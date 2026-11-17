package io.github.xiaowu20245.nekopaint.data.repository

/** 任务状态常量与来源优先级。 */
object TaskStatus {
    const val SUBMITTING = "submitting"
    const val UNKNOWN = "unknown"
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val SUCCESS = "success"
    const val FAILED = "failed"
    const val INTERRUPTED = "interrupted"

    fun isTerminal(status: String): Boolean =
        status == SUCCESS || status == FAILED || status == INTERRUPTED

    fun isLocalPending(status: String): Boolean =
        status == SUBMITTING || status == UNKNOWN
}

enum class TaskStatusSource(val priority: Int) {
    DISK(1),
    LOCAL(2),
    WS(3),
    QUEUE(4),
    HISTORY(5),
    ;

    companion object {
        fun from(value: String?): TaskStatusSource? =
            entries.firstOrNull { it.name == value }
    }
}
