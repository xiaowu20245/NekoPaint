package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AiStreamToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

data class AiStreamDelta(
    val content: String? = null,
    val toolCalls: List<AiStreamToolCallDelta> = emptyList(),
    val finishReason: String? = null,
)

/**
 * 解析 OpenAI 兼容的 SSE 流。
 *
 * 同时兼容服务端忽略 `stream=true`、直接返回完整 JSON 的情况。
 */
object AiChatStreamParser {

    private const val DATA_PREFIX = "data:"

    fun extractPayload(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(":") || trimmed.startsWith("event:")) return null
        val payload = if (trimmed.startsWith(DATA_PREFIX)) {
            trimmed.removePrefix(DATA_PREFIX).trim()
        } else {
            trimmed
        }
        return payload.takeIf { it.isNotEmpty() }
    }

    fun isDone(payload: String): Boolean = payload == "[DONE]"

    fun parseDelta(json: Json, payload: String): AiStreamDelta? {
        val root = runCatching {
            json.parseToJsonElement(payload).jsonObject
        }.getOrNull() ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        val finishReason = first["finish_reason"]?.jsonPrimitive?.contentOrNull
        val delta = (first["delta"] as? JsonObject)
            ?: (first["message"] as? JsonObject)
            ?: return finishReason?.let { AiStreamDelta(finishReason = it) }

        val content = delta["content"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotEmpty() }
        val toolCalls = (delta["tool_calls"] as? JsonArray).orEmpty().mapNotNull { element ->
            val call = element as? JsonObject ?: return@mapNotNull null
            val function = call["function"] as? JsonObject
            AiStreamToolCallDelta(
                index = call["index"]?.jsonPrimitive?.intOrNull ?: 0,
                id = call["id"]?.jsonPrimitive?.contentOrNull,
                name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                arguments = function?.get("arguments")?.jsonPrimitive?.contentOrNull,
            )
        }
        return AiStreamDelta(
            content = content,
            toolCalls = toolCalls,
            finishReason = finishReason,
        )
    }
}
