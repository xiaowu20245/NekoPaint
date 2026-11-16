package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

class WorkflowApplyException(message: String) : IllegalArgumentException(message)

object WorkflowJsonApplier {

    fun apply(
        rawJson: String,
        candidates: List<ParameterCandidate>,
    ): JsonObject {
        val root = Json.parseToJsonElement(rawJson).jsonObject.toMutableMap()
        val nodeIds = root.keys

        for (candidate in candidates.filter { it.selected && !it.readOnly }) {
            val node = root[candidate.nodeId] as? JsonObject
                ?: throw WorkflowApplyException("找不到节点 ${candidate.nodeId}")
            val inputs = node["inputs"] as? JsonObject
                ?: throw WorkflowApplyException("节点 ${candidate.nodeId} 缺少 inputs")
            val original = inputs[candidate.inputName]
                ?: throw WorkflowApplyException("找不到输入 ${candidate.inputName}")
            if (original.asNodeLink(nodeIds) != null) {
                throw WorkflowApplyException(
                    "输入 ${candidate.inputName} 是连线，不能直接写入",
                )
            }
            val originalPrimitive = original as? JsonPrimitive
                ?: throw WorkflowApplyException(
                    "输入 ${candidate.inputName} 不是标量，不能直接写入",
                )
            if (originalPrimitive is JsonNull) {
                throw WorkflowApplyException(
                    "输入 ${candidate.inputName} 不是标量，不能直接写入",
                )
            }

            val updatedInputs = inputs.toMutableMap()
            updatedInputs[candidate.inputName] = candidate.value.toJsonValue(
                type = candidate.type,
                original = originalPrimitive,
            )
            root[candidate.nodeId] = JsonObject(
                node + mapOf("inputs" to JsonObject(updatedInputs)),
            )
        }

        return JsonObject(root)
    }

    /**
     * 只保留在原始 api.json 中对应输入为标量（非连线/对象/数组/null）的候选。
     * 用于旧 profile 的安全视图和提交前最终过滤；不修改候选值。
     */
    fun safeCandidates(
        rawJson: String,
        candidates: List<ParameterCandidate>,
    ): List<ParameterCandidate> {
        val root = runCatching {
            Json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull() ?: return emptyList()
        return candidates.filter { candidate ->
            val inputs = (root[candidate.nodeId] as? JsonObject)
                ?.get("inputs") as? JsonObject
                ?: return@filter false
            val value = inputs[candidate.inputName] ?: return@filter false
            value is JsonPrimitive && value !is JsonNull
        }
    }

    private fun String.toJsonValue(
        type: ParameterType,
        original: JsonPrimitive,
    ): JsonElement = when (type) {
        ParameterType.INT -> JsonPrimitive(
            trim().toLongOrNull()
                ?: throw WorkflowApplyException("不是有效的整数：$this"),
        )

        ParameterType.FLOAT -> JsonPrimitive(
            trim().toDoubleOrNull()
                ?: throw WorkflowApplyException("不是有效的小数：$this"),
        )

        ParameterType.BOOLEAN -> JsonPrimitive(
            when (trim().lowercase()) {
                "true", "1", "yes", "是" -> true
                "false", "0", "no", "否", "" -> false
                else -> throw WorkflowApplyException("不是有效的开关值：$this")
            },
        )

        ParameterType.UNKNOWN -> when {
            original.isString -> JsonPrimitive(this)
            original.booleanOrNull != null -> JsonPrimitive(
                when (trim().lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> throw WorkflowApplyException("不是有效的布尔值：$this")
                },
            )

            original.longOrNull != null -> JsonPrimitive(
                trim().toLongOrNull()
                    ?: throw WorkflowApplyException("不是有效的整数：$this"),
            )

            original.doubleOrNull != null -> JsonPrimitive(
                trim().toDoubleOrNull()
                    ?: throw WorkflowApplyException("不是有效的小数：$this"),
            )

            else -> throw WorkflowApplyException("不是有效的标量值：$this")
        }

        ParameterType.TEXT,
        ParameterType.MULTILINE_TEXT,
        ParameterType.ENUM,
        ParameterType.IMAGE,
        -> JsonPrimitive(this)
    }
}
