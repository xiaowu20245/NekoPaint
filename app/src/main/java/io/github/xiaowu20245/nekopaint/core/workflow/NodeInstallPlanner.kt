package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 把 ComfyUI-Manager 的 `/customnode/getmappings` 结果转换成安装计划。
 *
 * 映射格式：包 ID -> [节点类名数组, 包元数据]。
 * 只生成计划，不发起安装；同一个节点被多个包提供时，优先选择覆盖缺失节点最多的包。
 */
object NodeInstallPlanner {

    fun buildPlan(
        template: WorkflowTemplate,
        mappings: JsonObject,
        managerVersion: String = "",
    ): NodeInstallPlan {
        val missing = template.missingNodeTypes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val available = parsePackages(mappings)
        val selected = linkedMapOf<String, NodeInstallPackage>()
        val unresolved = mutableListOf<String>()

        missing.forEach { nodeType ->
            val candidates = available.filter { nodeType in it.coveredNodeTypes }
            if (candidates.isEmpty()) {
                unresolved += nodeType
            } else {
                val best = candidates.maxWith(
                    compareBy<NodeInstallPackage> { candidate ->
                        candidate.coveredNodeTypes.count { it in missing }
                    }
                        // 同等覆盖时优先 CNR/非 URL 包，租用主机通常不允许 Git URL 安装。
                        .thenBy { candidate ->
                            if (candidate.id.startsWith("http", ignoreCase = true)) 0 else 1
                        }
                        .thenBy { candidate ->
                            if (candidate.version == "unknown") 0 else 1
                        }
                        .thenBy { it.title.lowercase() },
                )
                selected.putIfAbsent(best.id, best)
            }
        }

        return NodeInstallPlan(
            templateId = template.id,
            templateName = template.name,
            missingNodeTypes = missing,
            packages = selected.values.sortedBy { it.title.lowercase() },
            unresolvedNodeTypes = unresolved,
            managerVersion = managerVersion,
        )
    }

    fun buildInstallRequest(pkg: NodeInstallPackage): JsonObject = buildJsonObject {
        put("id", pkg.id)
        put("title", pkg.title)
        put("name", pkg.title)
        putJsonArray("files") {
            pkg.files.forEach { add(JsonPrimitive(it)) }
        }
        put("install_type", pkg.installType)
        put("version", pkg.version)
        // Manager 用 version 判断 CNR 包；unknown 表示按 files 中的 Git URL 安装。
        put("selected_version", pkg.version)
        put("ui_id", pkg.id)
        put("channel", pkg.channel)
        put("mode", pkg.mode)
        put("skip_post_install", false)
    }

    private fun parsePackages(mappings: JsonObject): List<NodeInstallPackage> =
        mappings.mapNotNull { (id, value) ->
            val entry = value as? JsonArray ?: return@mapNotNull null
            val nodeTypes = (entry.getOrNull(0) as? JsonArray)
                .orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotBlank() }
            if (nodeTypes.isEmpty()) return@mapNotNull null
            val metadata = entry.getOrNull(1) as? JsonObject ?: return@mapNotNull null
            val files = metadata.stringList("files")
                .ifEmpty { metadata.stringList("reference") }
            if (files.isEmpty()) return@mapNotNull null
            NodeInstallPackage(
                id = id,
                title = metadata.string("title") ?: id,
                files = files,
                installType = metadata.string("install_type") ?: "git",
                version = metadata.string("version") ?: "unknown",
                channel = metadata.string("channel") ?: "default",
                mode = metadata.string("mode") ?: "cache",
                coveredNodeTypes = nodeTypes,
            )
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringList(key: String): List<String> =
        when (val value = this[key]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotBlank() }

            is JsonPrimitive -> listOfNotNull(value.contentOrNull?.takeIf { it.isNotBlank() })
            else -> emptyList()
        }
}
