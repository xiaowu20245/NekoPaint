package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.JsonObject

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AiChatCompletion(
    val content: String?,
    val toolCalls: List<AiToolCall>,
    val finishReason: String? = null,
)

data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)
