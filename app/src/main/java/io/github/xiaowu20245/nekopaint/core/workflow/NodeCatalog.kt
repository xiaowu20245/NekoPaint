package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterConstraints
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

data class InputSchema(
    val type: ParameterType,
    val constraints: ParameterConstraints = ParameterConstraints(),
    val defaultText: String? = null,
)

class NodeCatalog(
    private val nodes: Map<String, NodeInfoDto>,
) {

    fun contains(classType: String): Boolean = classType in nodes

    fun inputSchema(classType: String, inputName: String): InputSchema? {
        val node = nodes[classType] ?: return null
        val raw = node.input?.required?.get(inputName)
            ?: node.input?.optional?.get(inputName)
            ?: return null
        return parseSchema(raw)
    }

    fun missingTypes(classTypes: Collection<String>): List<String> =
        classTypes.filterNot { it in nodes }.distinct().sorted()

    private fun parseSchema(raw: JsonElement): InputSchema {
        val array = raw as? JsonArray ?: return InputSchema(ParameterType.UNKNOWN)
        val typeElement = array.getOrNull(0) ?: return InputSchema(ParameterType.UNKNOWN)
        val options = array.getOrNull(1) as? JsonObject

        val optionsList = (typeElement as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()

        val typeName = (typeElement as? JsonPrimitive)?.contentOrNull
        val type = when {
            optionsList.isNotEmpty() -> ParameterType.ENUM
            typeName == "INT" -> ParameterType.INT
            typeName == "FLOAT" -> ParameterType.FLOAT
            typeName == "BOOLEAN" -> ParameterType.BOOLEAN
            typeName == "STRING" -> ParameterType.MULTILINE_TEXT
            typeName == "IMAGE" || typeName == "MASK" -> ParameterType.IMAGE
            else -> ParameterType.UNKNOWN
        }

        val defaultText = options?.get("default")?.toDisplayText()
        val min = options?.get("min")?.asDoubleOrNull()
        val max = options?.get("max")?.asDoubleOrNull()
        val step = options?.get("step")?.asDoubleOrNull()

        return InputSchema(
            type = type,
            constraints = ParameterConstraints(
                min = min,
                max = max,
                step = step,
                options = optionsList,
            ),
            defaultText = defaultText,
        )
    }
}

private fun JsonElement.asDoubleOrNull(): Double? = when (this) {
    is JsonPrimitive -> doubleOrNull
    else -> null
}

private fun JsonElement.toDisplayText(): String? = when (this) {
    is JsonPrimitive -> when {
        booleanOrNull != null -> booleanOrNull.toString()
        isString -> contentOrNull
        else -> contentOrNull
    }

    else -> toString()
}
