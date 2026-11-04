package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NodeInfoDto(
    val input: NodeInputsDto? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val category: String? = null,
)

@Serializable
data class NodeInputsDto(
    val required: Map<String, JsonElement> = emptyMap(),
    val optional: Map<String, JsonElement> = emptyMap(),
)
