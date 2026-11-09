package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup

/**
 * 提示词标签组展开工具。
 *
 * 编辑器中允许用户输入 `#组名` 占位；提交前统一替换为组内标签，
 * 避免把占位符原样发给 ComfyUI。
 */
object PromptTagExpander {

    fun expand(value: String, groups: List<PromptTagGroup>): String {
        if (value.isBlank() || groups.isEmpty()) return value

        var result = value
        // 长组名优先，避免“#画质”先于“#起手画质组”被替换。
        groups
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.name.length }
            .forEach { group ->
                val replacement = group.tags.joinToString(", ")
                result = result.replace("#${group.name}", replacement)
            }

        return result
            .replace(Regex("\\s*,\\s*(?=,)"), "")
            .replace(Regex("^\\s*,\\s*"), "")
            .replace(Regex("\\s*,\\s*$"), "")
            .trim()
    }

    fun referencedGroups(value: String, groups: List<PromptTagGroup>): List<PromptTagGroup> =
        groups.filter { it.name.isNotBlank() && value.contains("#${it.name}") }

    fun promptPartCount(value: String): Int =
        value.split(',').count { it.isNotBlank() }

    fun expandedItemCount(value: String, groups: List<PromptTagGroup>): Int =
        promptPartCount(expand(value, groups))

    /** 仅用于提示词卡的粗略 token 估算，不追求与具体模型分词完全一致。 */
    fun estimateTokenCount(value: String): Int =
        value.split(Regex("[,\\s]+")).count { it.isNotBlank() }
}
