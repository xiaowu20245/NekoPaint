package io.github.xiaowu20245.nekopaint.core.workflow

/**
 * 提示词联想的子串索引。
 *
 * 按 tag/中文/alias 中出现过的所有字符分桶，保留完整 contains 召回，
 * 又避免每次输入都全量扫描；空输入返回空列表，候选数硬上限 50。
 */
class PromptLexiconIndex(
    suggestions: List<PromptTagSuggestion>,
) {

    // 按“所有出现过的字符”分桶，既保留 contains 召回，又避免每次全量扫描。
    private val buckets: Map<Char, List<PromptTagSuggestion>> = buildMap<Char, MutableList<PromptTagSuggestion>> {
        suggestions.forEach { suggestion ->
            val keys = buildSet {
                suggestion.tag.lowercase().forEach(::add)
                suggestion.zh.lowercase().forEach(::add)
                suggestion.aliases.forEach { alias ->
                    alias.lowercase().forEach(::add)
                }
            }
            keys.forEach { key -> getOrPut(key) { mutableListOf() }.add(suggestion) }
        }
    }

    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<PromptTagSuggestion> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return emptyList()
        val candidates = buckets[keyword.first()].orEmpty()
        return candidates
            .filter { suggestion ->
                suggestion.tag.lowercase().contains(keyword) ||
                    suggestion.zh.contains(keyword, ignoreCase = true) ||
                    suggestion.aliases.any { it.contains(keyword, ignoreCase = true) }
            }
            .sortedByDescending { suggestion ->
                suggestion.tag.lowercase().startsWith(keyword)
            }
            .take(limit.coerceIn(1, MAX_LIMIT))
    }

    companion object {
        const val DEFAULT_LIMIT = 8
        const val MAX_LIMIT = 50
    }
}
