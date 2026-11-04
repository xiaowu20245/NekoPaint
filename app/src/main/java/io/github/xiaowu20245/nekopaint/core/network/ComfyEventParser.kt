package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 将 ComfyUI WebSocket 文本帧转换为领域事件。
 *
 * 独立出来后可以脱离网络连接做单元测试；未知事件与坏 JSON 一律返回 null，
 * 避免服务器升级新增事件时导致客户端崩溃。
 */
class ComfyEventParser(private val json: Json) {

    fun parse(text: String): ComfyEvent? {
        val root = runCatching {
            json.parseToJsonElement(text) as? JsonObject
        }.getOrNull() ?: return null
        val type = (root["type"] as? JsonPrimitive)?.contentOrNull ?: return null
        val data = root["data"] as? JsonObject ?: JsonObject(emptyMap())

        return when (type) {
            "status" -> {
                val statusObject = data["status"] as? JsonObject
                val execInfo = statusObject?.get("exec_info") as? JsonObject
                val remaining = (execInfo?.get("queue_remaining") as? JsonPrimitive)?.intOrNull
                    ?: 0
                ComfyEvent.Status(queueRemaining = remaining)
            }

            "execution_start" -> data.promptId()?.let { ComfyEvent.ExecutionStart(it) }

            "execution_cached" -> {
                val promptId = data.promptId() ?: return null
                val nodes = (data["nodes"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                ComfyEvent.ExecutionCached(promptId, nodes)
            }

            "executing" -> {
                val promptId = data.promptId() ?: return null
                val nodeId = (data["node"] as? JsonPrimitive)?.contentOrNull
                ComfyEvent.Executing(promptId, nodeId)
            }

            "progress" -> {
                val promptId = data.promptId() ?: return null
                val nodeId = (data["node"] as? JsonPrimitive)?.contentOrNull ?: return null
                val value = (data["value"] as? JsonPrimitive)?.doubleOrNull ?: return null
                val max = (data["max"] as? JsonPrimitive)?.doubleOrNull ?: 1.0
                ComfyEvent.Progress(promptId, nodeId, value, max)
            }

            "progress_state" -> {
                val promptId = data.promptId() ?: return null
                val nodesObject = data["nodes"] as? JsonObject ?: JsonObject(emptyMap())
                val nodes = nodesObject.mapNotNull { (nodeId, value) ->
                    val node = value as? JsonObject ?: return@mapNotNull null
                    NodeProgress(
                        nodeId = nodeId,
                        value = (node["value"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                        max = (node["max"] as? JsonPrimitive)?.doubleOrNull ?: 1.0,
                        state = (node["state"] as? JsonPrimitive)?.contentOrNull,
                    )
                }
                ComfyEvent.ProgressState(promptId, nodes)
            }

            "executed" -> {
                val promptId = data.promptId() ?: return null
                val nodeId = (data["node"] as? JsonPrimitive)?.contentOrNull ?: return null
                ComfyEvent.Executed(
                    promptId = promptId,
                    nodeId = nodeId,
                    output = data["output"] as? JsonObject,
                )
            }

            "execution_success" -> data.promptId()?.let { ComfyEvent.Success(it) }

            "execution_error" -> {
                val promptId = data.promptId() ?: return null
                val nodeId = (data["node_id"] as? JsonPrimitive)?.contentOrNull
                val message = (data["exception_message"] as? JsonPrimitive)?.contentOrNull
                    ?: (data["exception_type"] as? JsonPrimitive)?.contentOrNull
                    ?: "执行失败"
                ComfyEvent.Error(promptId, nodeId, message)
            }

            "execution_interrupted" -> data.promptId()?.let { ComfyEvent.Interrupted(it) }

            else -> null
        }
    }

    private fun JsonObject.promptId(): String? =
        (this["prompt_id"] as? JsonPrimitive)?.contentOrNull
}
