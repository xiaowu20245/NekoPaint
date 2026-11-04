package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PromptRequestDto(
    val prompt: JsonObject,
    @SerialName("client_id") val clientId: String,
    @SerialName("prompt_id") val promptId: String? = null,
)

@Serializable
data class PromptResponseDto(
    @SerialName("prompt_id") val promptId: String? = null,
    val number: Int? = null,
    @SerialName("node_errors") val nodeErrors: Map<String, NodeErrorDto> = emptyMap(),
)

@Serializable
data class PromptErrorResponseDto(
    val error: PromptErrorDto? = null,
    @SerialName("node_errors") val nodeErrors: Map<String, NodeErrorDto> = emptyMap(),
)

@Serializable
data class PromptErrorDto(
    val type: String? = null,
    val message: String? = null,
    val details: String? = null,
)

@Serializable
data class NodeErrorDto(
    val errors: List<NodeErrorDetailDto> = emptyList(),
    @SerialName("class_type") val classType: String? = null,
)

@Serializable
data class NodeErrorDetailDto(
    val type: String? = null,
    val message: String? = null,
    val details: String? = null,
)
