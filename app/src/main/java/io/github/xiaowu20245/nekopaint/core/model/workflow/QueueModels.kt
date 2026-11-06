package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class QueueItem(
    val number: Int?,
    val promptId: String,
    val clientId: String?,
    val prompt: JsonObject?,
    val nodeTitles: Map<String, String>,
)

data class QueueSnapshot(
    val running: List<QueueItem>,
    val pending: List<QueueItem>,
    val updatedAt: Long,
)

@Serializable
data class HistoryImage(
    val filename: String,
    val subfolder: String,
    val type: String,
    val url: String? = null,
)

@Serializable
data class HistoryEntry(
    val promptId: String,
    val status: String,
    val completed: Boolean,
    val timestamp: Long?,
    val startedAt: Long?,
    val finishedAt: Long?,
    val images: List<HistoryImage>,
    val errorNodeId: String?,
    val errorMessage: String?,
)

data class TaskProgress(
    val promptId: String,
    val status: String,
    val currentNode: String?,
    val currentNodeLabel: String?,
    val value: Double?,
    val max: Double?,
)
