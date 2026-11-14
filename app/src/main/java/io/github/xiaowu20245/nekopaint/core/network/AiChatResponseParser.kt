package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 OpenAI 兼容接口的响应文本，独立成纯函数以便单元测试。
 */
object AiChatResponseParser {

    fun parseContent(json: Json, rawBody: String): String? =
        parseCompletion(json, rawBody)?.content

    fun parseCompletion(json: Json, rawBody: String): AiChatCompletion? {
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val firstChoice = choices.firstOrNull() as? JsonObject ?: return null
        val message = firstChoice["message"] as? JsonObject ?: return null
        val content = message["content"]?.jsonPrimitive?.contentOrNull?.trim()
        val toolCalls = (message["tool_calls"] as? JsonArray).orEmpty().mapNotNull { element ->
            val toolCall = element as? JsonObject ?: return@mapNotNull null
            val id = toolCall["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val function = toolCall["function"] as? JsonObject ?: return@mapNotNull null
            val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            AiToolCall(id = id, name = name, arguments = arguments)
        }
        val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull
        return AiChatCompletion(
            content = content?.takeIf { it.isNotBlank() },
            toolCalls = toolCalls,
            finishReason = finishReason,
        )
    }

    fun parseErrorMessage(json: Json, rawBody: String): String? {
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return null
        val error = root["error"] as? JsonObject ?: return null
        return error["message"]?.jsonPrimitive?.contentOrNull?.trim()
    }
}
