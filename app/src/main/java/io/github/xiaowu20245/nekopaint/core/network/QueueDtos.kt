package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class QueueDto(
    @SerialName("queue_running") val running: List<JsonArray> = emptyList(),
    @SerialName("queue_pending") val pending: List<JsonArray> = emptyList(),
)

@Serializable
data class HistoryItemDto(
    val prompt: JsonElement? = null,
    val outputs: Map<String, JsonElement> = emptyMap(),
    val meta: Map<String, JsonElement> = emptyMap(),
    val status: HistoryStatusDto? = null,
)

@Serializable
data class HistoryStatusDto(
    @SerialName("status_str") val statusStr: String? = null,
    val completed: Boolean? = null,
    val messages: List<JsonElement> = emptyList(),
)

@Serializable
data class InterruptRequestDto(
    @SerialName("prompt_id") val promptId: String? = null,
)

@Serializable
data class QueueOperationDto(
    val clear: Boolean? = null,
    val delete: List<String>? = null,
)

@Serializable
data class HistoryOperationDto(
    val clear: Boolean? = null,
    val delete: List<String>? = null,
)
