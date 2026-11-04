package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowFormat
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowGraph
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

sealed interface WorkflowParseResult {
    data class Success(
        val graph: WorkflowGraph,
        val rawJson: String,
    ) : WorkflowParseResult

    data object UiFormat : WorkflowParseResult

    data class Invalid(val reason: String) : WorkflowParseResult
}

object WorkflowParser {

    private val outputClassTypes = setOf(
        "SaveImage",
        "PreviewImage",
        "SaveAnimatedWEBP",
        "SaveVideo",
        "VHS_VideoCombine",
    )

    fun parse(rawJson: String): WorkflowParseResult {
        val root = runCatching { Json.parseToJsonElement(rawJson) as? JsonObject }
            .getOrNull()
            ?: return WorkflowParseResult.Invalid("文件不是有效的 JSON 对象")

        if (root["nodes"] is JsonArray && root["links"] is JsonArray) {
            return WorkflowParseResult.UiFormat
        }

        if (root.isEmpty()) {
            return WorkflowParseResult.Invalid("工作流为空")
        }

        val nodeObjects = root.mapValues { (_, value) -> value as? JsonObject }
        if (nodeObjects.values.any { it == null }) {
            return WorkflowParseResult.Invalid("不是 ComfyUI API 格式")
        }

        val nodes = mutableMapOf<String, WorkflowNode>()
        for ((nodeId, nodeObject) in nodeObjects) {
            val safeObject = nodeObject ?: continue
            val classType = (safeObject["class_type"] as? JsonPrimitive)?.contentOrNull
                ?: return WorkflowParseResult.Invalid("节点 $nodeId 缺少 class_type")
            val inputs = safeObject["inputs"] as? JsonObject
                ?: return WorkflowParseResult.Invalid("节点 $nodeId 缺少 inputs")
            val title = (safeObject["_meta"] as? JsonObject)
                ?.get("title")
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotBlank() }
                ?: classType

            nodes[nodeId] = WorkflowNode(
                id = nodeId,
                classType = classType,
                title = title,
                inputs = inputs,
            )
        }

        val outputIds = nodes.values
            .filter { it.classType in outputClassTypes }
            .map { it.id }
            .toSet()

        val reachable = if (outputIds.isEmpty()) {
            nodes.keys
        } else {
            collectReachable(nodes, outputIds)
        }

        return WorkflowParseResult.Success(
            graph = WorkflowGraph(
                nodes = nodes,
                outputNodeIds = outputIds,
                reachableNodeIds = reachable,
                format = WorkflowFormat.API,
            ),
            rawJson = rawJson,
        )
    }

    private fun collectReachable(
        nodes: Map<String, WorkflowNode>,
        outputIds: Set<String>,
    ): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque(outputIds)
        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()
            if (!visited.add(nodeId)) continue
            val node = nodes[nodeId] ?: continue
            for (value in node.inputs.values) {
                val link = value.asNodeLink(nodes.keys)
                if (link != null && link.first !in visited) {
                    queue.addLast(link.first)
                }
            }
        }
        return visited
    }
}

fun JsonElement.asNodeLink(nodeIds: Set<String>): Pair<String, Int>? {
    val array = this as? JsonArray ?: return null
    if (array.size != 2) return null
    val sourceId = (array[0] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?: return null
    if (sourceId !in nodeIds) return null
    val outputSlot = (array[1] as? JsonPrimitive)?.intOrNull ?: return null
    return sourceId to outputSlot
}

fun JsonElement.scalarText(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonObject -> toString()
    is JsonArray -> toString()
}
