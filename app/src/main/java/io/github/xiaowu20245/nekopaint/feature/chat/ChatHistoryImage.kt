package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.serialization.Serializable

@Serializable
data class ChatHistoryImage(
    val url: String,
    val filename: String,
    val type: String,
    val promptId: String,
) {
    val isOutput: Boolean get() = type == "output"
}

data class ChatHistoryImageResult(
    val promptId: String,
    val title: String,
    val status: String,
    val images: List<ChatHistoryImage>,
)

internal fun selectHistoryImages(
    history: List<HistoryEntry>,
    tasks: List<TaskRecord>,
    promptId: String?,
    maxImages: Int,
): ChatHistoryImageResult? {
    val entry = if (promptId.isNullOrBlank()) {
        history.firstOrNull { entry ->
            entry.images.any { !it.url.isNullOrBlank() }
        }
    } else {
        history.firstOrNull { it.promptId == promptId }
    } ?: return null

    val images = entry.images
        .asSequence()
        .filter { !it.url.isNullOrBlank() }
        .take(maxImages.coerceIn(1, 6))
        .map { image ->
            ChatHistoryImage(
                url = image.url.orEmpty(),
                filename = image.filename,
                type = image.type.orEmpty(),
                promptId = entry.promptId,
            )
        }
        .toList()
    if (images.isEmpty()) return null

    val task = tasks.firstOrNull { it.promptId == entry.promptId }
    return ChatHistoryImageResult(
        promptId = entry.promptId,
        title = task?.templateName?.takeIf { it.isNotBlank() } ?: "未命名任务",
        status = entry.status,
        images = images,
    )
}
