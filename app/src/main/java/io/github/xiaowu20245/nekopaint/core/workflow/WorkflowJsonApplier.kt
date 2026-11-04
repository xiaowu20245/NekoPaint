package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
            if (inputs[candidate.inputName]?.asNodeLink(nodeIds) != null) {
                throw WorkflowApplyException(
                    "输入 ${candidate.inputName} 是连线，不能直接写入",
                )
            }

            val updatedInputs = inputs.toMutableMap()
            updatedInputs[candidate.inputName] = candidate.value.toJsonValue(candidate.type)
            root[candidate.nodeId] = JsonObject(
                node + mapOf("inputs" to JsonObject(updatedInputs)),
            )
        }

        return JsonObject(root)
    }

    private fun String.toJsonValue(type: ParameterType): JsonElement = when (type) {
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

        ParameterType.TEXT,
        ParameterType.MULTILINE_TEXT,
        ParameterType.ENUM,
        ParameterType.IMAGE,
        ParameterType.UNKNOWN,
        -> JsonPrimitive(this)
    }
}
