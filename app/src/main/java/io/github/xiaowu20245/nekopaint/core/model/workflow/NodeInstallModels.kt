package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.Serializable

/**
 * 一个可安装的自定义节点包。
 *
 * [id] 是 ComfyUI-Manager 的包标识（通常是 CNR ID 或仓库 URL），
 * [files] 是安装源，[coveredNodeTypes] 用来解释为什么选中这个包。
 */
@Serializable
data class NodeInstallPackage(
    val id: String,
    val title: String,
    val files: List<String> = emptyList(),
    val installType: String = "git",
    val version: String = "unknown",
    val channel: String = "default",
    val mode: String = "cache",
    val coveredNodeTypes: List<String> = emptyList(),
) {
    val sourceUrl: String
        get() = files.firstOrNull().orEmpty()

    val sourceLabel: String
        get() = sourceUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix(".git")
            .ifBlank { id }
}

/**
 * 缺失节点安装计划。只描述“准备安装什么”，不代表已经安装。
 */
@Serializable
data class NodeInstallPlan(
    val templateId: String,
    val templateName: String,
    val missingNodeTypes: List<String> = emptyList(),
    val packages: List<NodeInstallPackage> = emptyList(),
    val unresolvedNodeTypes: List<String> = emptyList(),
    val managerVersion: String = "",
) {
    val packageCount: Int
        get() = packages.size

    val canInstall: Boolean
        get() = packages.isNotEmpty()
}
