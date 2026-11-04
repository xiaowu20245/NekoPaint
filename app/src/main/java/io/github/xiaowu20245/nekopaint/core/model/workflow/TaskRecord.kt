package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.Serializable

@Serializable
data class TaskRecord(
    val promptId: String,
    val templateId: String,
    val templateName: String,
    val clientId: String,
    val queueNumber: Int? = null,
    val submittedAt: Long,
    val status: String = "queued",
    val values: Map<String, String> = emptyMap(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val errorMessage: String? = null,
    val currentNode: String? = null,
    val progressValue: Double? = null,
    val progressMax: Double? = null,
)
