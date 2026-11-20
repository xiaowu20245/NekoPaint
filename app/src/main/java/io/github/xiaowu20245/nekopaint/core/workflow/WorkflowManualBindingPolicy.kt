package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

enum class ManualBindingError {
    NODE_MISSING,
    INPUT_MISSING,
    DUPLICATE_ID,
    LABEL_BLANK,
    VALUE_INVALID,
}

/**
 * 手动绑定参数的纯策略。
 *
 * 模板 JSON 中 nodeId/inputName 的存在性由调用方先校验；本对象只负责
 * ID/标签、类型推断与值合法性，便于 JVM 测试。
 */
object WorkflowManualBindingPolicy {

    fun candidateId(nodeId: String, inputName: String): String =
        "${nodeId.trim()}:${inputName.trim()}"

    fun inferType(
        rawInput: JsonElement?,
        schema: InputSchema?,
    ): ParameterType {
        schema?.type?.takeIf { it != ParameterType.UNKNOWN }?.let { return it }
        // api.json 的 inputs 存的是实际值，不是 schema 数组；按值推断只能作为降级。
        if (rawInput is JsonPrimitive) {
            return when {
                rawInput.booleanOrNull != null -> ParameterType.BOOLEAN
                rawInput.intOrNull != null -> ParameterType.INT
                rawInput.doubleOrNull != null -> ParameterType.FLOAT
                rawInput.isString -> ParameterType.MULTILINE_TEXT
                else -> ParameterType.UNKNOWN
            }
        }
        val array = rawInput as? JsonArray ?: return ParameterType.UNKNOWN
        if (array.size == 2 &&
            array.getOrNull(0) is JsonPrimitive &&
            array.getOrNull(1) is JsonPrimitive &&
            (array.getOrNull(1) as JsonPrimitive).intOrNull != null
        ) {
            // ["12", 0] 是节点连线；常见于 IMAGE/latent 输入。
            return ParameterType.IMAGE
        }
        val typeElement = array.getOrNull(0) ?: return ParameterType.UNKNOWN
        return when {
            typeElement is JsonArray -> ParameterType.ENUM
            typeElement is JsonObject -> ParameterType.ENUM
            typeElement is JsonPrimitive -> when (typeElement.contentOrNull) {
                "INT" -> ParameterType.INT
                "FLOAT" -> ParameterType.FLOAT
                "BOOLEAN" -> ParameterType.BOOLEAN
                "STRING" -> ParameterType.MULTILINE_TEXT
                "IMAGE", "MASK" -> ParameterType.IMAGE
                else -> ParameterType.UNKNOWN
            }

            else -> ParameterType.UNKNOWN
        }
    }

    fun defaultGroup(type: ParameterType): ParameterGroup = when (type) {
        ParameterType.MULTILINE_TEXT,
        ParameterType.TEXT,
        -> ParameterGroup.PROMPT

        ParameterType.IMAGE -> ParameterGroup.IMAGE
        ParameterType.INT,
        ParameterType.FLOAT,
        ParameterType.BOOLEAN,
        ParameterType.ENUM,
        -> ParameterGroup.SAMPLING

        ParameterType.UNKNOWN -> ParameterGroup.OTHER
    }

    fun validate(
        nodeExists: Boolean,
        inputExists: Boolean,
        existingIds: Set<String>,
        candidate: ParameterCandidate,
        schema: InputSchema? = null,
    ): ManualBindingError? = when {
        !nodeExists -> ManualBindingError.NODE_MISSING
        !inputExists -> ManualBindingError.INPUT_MISSING
        candidate.id in existingIds -> ManualBindingError.DUPLICATE_ID
        candidate.label.isBlank() -> ManualBindingError.LABEL_BLANK
        !isValueValid(
            candidate.value,
            candidate.type,
            schema,
            schema?.constraints?.options ?: candidate.constraints.options,
        ) -> ManualBindingError.VALUE_INVALID

        else -> null
    }

    fun isValueValid(
        value: String,
        type: ParameterType,
        schema: InputSchema?,
        options: List<String> = schema?.constraints?.options.orEmpty(),
    ): Boolean {
        val trimmed = value.trim()
        return when (type) {
            ParameterType.INT -> trimmed.toLongOrNull() != null
            ParameterType.FLOAT -> trimmed.toDoubleOrNull() != null
            ParameterType.BOOLEAN -> trimmed.toBooleanStrictOrNull() != null
            ParameterType.ENUM -> options.isEmpty() || trimmed in options
            ParameterType.IMAGE,
            ParameterType.MULTILINE_TEXT,
            ParameterType.TEXT,
            ParameterType.UNKNOWN,
            -> trimmed.isNotEmpty() || type != ParameterType.IMAGE
        }
    }

    fun buildCandidate(
        nodeId: String,
        inputName: String,
        label: String,
        type: ParameterType,
        group: ParameterGroup,
        value: String,
        classType: String,
    ): ParameterCandidate = ParameterCandidate(
        id = candidateId(nodeId, inputName),
        nodeId = nodeId.trim(),
        inputName = inputName.trim(),
        label = label.trim().ifBlank { inputName.trim() },
        type = type,
        group = group,
        defaultValue = value,
        value = value,
        selected = true,
        classType = classType,
    )
}

private fun String.toBooleanStrictOrNull(): Boolean? = when (lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}
