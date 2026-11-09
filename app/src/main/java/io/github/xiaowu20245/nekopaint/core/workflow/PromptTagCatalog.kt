package io.github.xiaowu20245.nekopaint.core.workflow

/**
 * 提示词编辑器的本地联想词库。
 *
 * 只负责编辑体验与中文提示；标签组仍是用户数据，按工作流持久化。
 */
data class PromptTagSuggestion(
    val tag: String,
    val zh: String,
    val aliases: List<String> = emptyList(),
)

object PromptTagCatalog {

    val suggestions: List<PromptTagSuggestion> = listOf(
        PromptTagSuggestion("masterpiece", "杰作", listOf("画质", "高质量")),
        PromptTagSuggestion("best quality", "最佳质量", listOf("画质")),
        PromptTagSuggestion("very aesthetic", "高美感", listOf("美感")),
        PromptTagSuggestion("absurdres", "极高分辨率", listOf("分辨率")),
        PromptTagSuggestion("1girl", "1个女孩", listOf("女孩", "单人")),
        PromptTagSuggestion("1boy", "1个男孩", listOf("男孩")),
        PromptTagSuggestion("solo", "单人"),
        PromptTagSuggestion("silver hair", "银发", listOf("白发")),
        PromptTagSuggestion("long hair", "长发"),
        PromptTagSuggestion("short hair", "短发"),
        PromptTagSuggestion("red eyes", "红瞳", listOf("红眼")),
        PromptTagSuggestion("fox ears", "狐狸耳朵", listOf("兽耳", "狐耳")),
        PromptTagSuggestion("fox tail", "狐狸尾巴", listOf("尾巴")),
        PromptTagSuggestion("kemomimi-chan \\(naga u\\)", "兽耳酱", listOf("kemomimi")),
        PromptTagSuggestion("white thighhighs", "白色过膝袜", listOf("白丝")),
        PromptTagSuggestion("dress", "连衣裙"),
        PromptTagSuggestion("school uniform", "校服"),
        PromptTagSuggestion("sitting", "坐姿"),
        PromptTagSuggestion("standing", "站姿"),
        PromptTagSuggestion("looking at viewer", "看向镜头"),
        PromptTagSuggestion("smile", "微笑"),
        PromptTagSuggestion("cherry blossoms", "樱花"),
        PromptTagSuggestion("shrine", "神社"),
        PromptTagSuggestion("cinematic lighting", "电影感光影", listOf("电影光")),
        PromptTagSuggestion("soft lighting", "柔和光影", listOf("柔光")),
        PromptTagSuggestion("detailed eyes", "精细眼睛"),
        PromptTagSuggestion("depth of field", "景深"),
        PromptTagSuggestion("from above", "俯视"),
        PromptTagSuggestion("from below", "仰视"),
        PromptTagSuggestion("original", "原创"),
    )

    fun search(query: String, limit: Int = 8): List<PromptTagSuggestion> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return emptyList()
        return suggestions
            .filter { suggestion ->
                suggestion.tag.lowercase().contains(keyword) ||
                    suggestion.zh.contains(keyword, ignoreCase = true) ||
                    suggestion.aliases.any { it.contains(keyword, ignoreCase = true) }
            }
            .sortedByDescending { suggestion ->
                suggestion.tag.lowercase().startsWith(keyword)
            }
            .take(limit)
    }

    fun zhFor(tag: String): String? =
        suggestions.firstOrNull { it.tag.equals(tag.trim(), ignoreCase = true) }?.zh

    /**
     * 本地词库中文/别名 -> 英文 tag。全部词条命中才返回，否则交给 AI 兜底。
     */
    fun translateChinese(input: String): String? {
        val parts = input.split(Regex("[,，、\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val mapped = parts.map { part ->
            suggestions.firstOrNull { suggestion ->
                suggestion.zh == part ||
                    suggestion.aliases.any { alias -> alias == part }
            }?.tag
        }
        if (mapped.any { it == null }) return null
        return mapped.filterNotNull().joinToString(", ")
    }
}
