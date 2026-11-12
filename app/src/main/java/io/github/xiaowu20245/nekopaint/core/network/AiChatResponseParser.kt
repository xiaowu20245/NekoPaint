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

    fun parseContent(json: Json, rawBody: String): String? {
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return null
        val choices = root["choices"] as? JsonArray ?: return null
        val firstChoice = choices.firstOrNull() as? JsonObject ?: return null
        val message = firstChoice["message"] as? JsonObject ?: return null
        return message["content"]?.jsonPrimitive?.contentOrNull?.trim()
    }

    fun parseErrorMessage(json: Json, rawBody: String): String? {
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return null
        val error = root["error"] as? JsonObject ?: return null
        return error["message"]?.jsonPrimitive?.contentOrNull?.trim()
    }
}
