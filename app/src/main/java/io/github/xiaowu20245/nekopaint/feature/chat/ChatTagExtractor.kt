package io.github.xiaowu20245.nekopaint.feature.chat

/**
 * 从视觉模型回答中提取可复制标签。
 *
 * 优先读取 ```tags 代码块；兼容“标签：tag1, tag2”单行格式。
 */
internal fun extractCopyableTags(text: String): String? {
    val fenced = TAGS_BLOCK.find(text)?.groupValues?.getOrNull(1)
    val inline = TAGS_LINE.find(text)?.groupValues?.getOrNull(1)
    val raw = fenced ?: inline ?: return null
    val cleaned = raw.lines()
        .map { line ->
            line.trim()
                .removePrefix("- ")
                .removePrefix("* ")
                .trim()
        }
        .filter { line ->
            line.isNotBlank() &&
                !line.equals("标签", ignoreCase = true) &&
                !line.equals("tags", ignoreCase = true)
        }
        .joinToString(", ")
        .replace(Regex(",\\s*,"), ",")
        .trim()
        .trimEnd(',')
    return cleaned.takeIf { it.isNotBlank() }
}

/**
 * 去掉回答中的标签区域，只保留画面描述，避免标签在聊天流里重复显示。
 */
internal fun stripTagsSection(text: String): String {
    val withoutBlock = TAGS_BLOCK.replace(text, "")
    val withoutInline = TAGS_LINE.replace(withoutBlock, "")
    return withoutInline
        .replace(BARE_TAGS_LABEL, "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private val TAGS_BLOCK = Regex(
    pattern = "```(?:tags|标签)\\s*\\n([\\s\\S]*?)```",
    option = RegexOption.IGNORE_CASE,
)

private val TAGS_LINE = Regex(
    pattern = "(?m)^\\s*标签\\s*[：:]\\s*(.+)$",
)

private val BARE_TAGS_LABEL = Regex(
    pattern = "(?m)^\\s*标签\\s*[：:]\\s*$",
)
