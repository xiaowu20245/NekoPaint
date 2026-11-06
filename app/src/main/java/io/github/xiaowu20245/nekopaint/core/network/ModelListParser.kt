package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `/models/{folder}` 在不同版本/不同安装下可能返回：
 * - 字符串数组：["a.safetensors", ...]
 * - 包装对象：{"value":["a.safetensors", ...], "Count": 1}
 */
object ModelListParser {

    fun parse(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonObject -> (element["value"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()

        else -> emptyList()
    }
}
