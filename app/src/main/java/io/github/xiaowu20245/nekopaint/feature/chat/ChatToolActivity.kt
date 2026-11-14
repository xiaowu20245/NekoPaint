package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一次只读工具调用的可视化记录，只保留展示需要的参数，不保存完整返回内容。
 */
@Serializable
data class ChatToolActivity(
    val name: String,
    val promptId: String? = null,
    val status: String? = null,
    val limit: Int? = null,
    val keyword: String? = null,
    val imageId: String? = null,
    val mode: String? = null,
    val isError: Boolean = false,
)

internal fun describeToolCall(
    json: Json,
    call: AiToolCall,
    fallbackPromptId: String? = null,
): ChatToolActivity {
    val args = runCatching {
        json.parseToJsonElement(call.arguments).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))
    return ChatToolActivity(
        name = call.name,
        promptId = args["promptId"]?.jsonPrimitive?.contentOrNull ?: fallbackPromptId,
        status = args["status"]?.jsonPrimitive?.contentOrNull,
        limit = args["limit"]?.jsonPrimitive?.intOrNull,
        keyword = args["keyword"]?.jsonPrimitive?.contentOrNull,
        imageId = args["imageId"]?.jsonPrimitive?.contentOrNull,
        mode = args["mode"]?.jsonPrimitive?.contentOrNull,
    )
}
