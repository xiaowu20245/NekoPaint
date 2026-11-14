package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord

internal fun chatStatusLabel(status: String): String = when (status) {
    "success" -> "成功"
    "failed" -> "失败"
    "interrupted" -> "已中断"
    "running" -> "运行中"
    "queued" -> "排队中"
    else -> status
}

/**
 * 从历史/任务详情进入 Neko I 时携带的只读上下文。
 *
 * summary 只包含任务元数据和错误摘要，不包含提示词正文、图片内容或服务器地址。
 */
data class ChatTaskContext(
    val promptId: String,
    val title: String,
    val status: String,
    val summary: String,
) {
    val statusLabel: String get() = chatStatusLabel(status)

    val shortId: String
        get() = if (promptId.length <= 8) promptId else promptId.take(8)

    val isFailure: Boolean get() = status == "failed" || status == "interrupted"

    companion object {
        fun from(
            entry: HistoryEntry,
            task: TaskRecord?,
            summary: String,
        ): ChatTaskContext = ChatTaskContext(
            promptId = entry.promptId,
            title = task?.templateName?.takeIf { it.isNotBlank() } ?: "未命名任务",
            status = entry.status,
            summary = summary,
        )
    }
}
