package io.github.xiaowu20245.nekopaint.core.workflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PromptLexiconEntry(
    val tag: String,
    val zh: String,
    val aliases: List<String> = emptyList(),
    val category: String = "",
)

/**
 * 外部词库解析器。
 *
 * CSV 采用社区常见的 `tag, category, count, zh_aliases`；JSON 兼容数组、
 * `{ "entries": [...] }` 包装以及“tag -> 中文别名”映射三种形态。
 * 解析器只做纯文本转换，不接触 Android API，方便单元测试。
 */
object PromptLexiconParser {

    private val aliasSplitter = Regex("[|;/,、]")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String, fileName: String = ""): Result<List<PromptLexiconEntry>> =
        runCatching {
            val text = content.removePrefix("\uFEFF").trim()
            if (text.isEmpty()) error("文件为空")
            val looksJson = text.startsWith("{") || text.startsWith("[") ||
                fileName.endsWith(".json", ignoreCase = true)
            val entries = if (looksJson) parseJson(text) else parseCsv(text)
            if (entries.isEmpty()) error("文件中没有可用词条")
            entries
        }

    private fun parseCsv(text: String): List<PromptLexiconEntry> {
        val rows = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { splitCsvLine(it) }
            .toList()
        if (rows.isEmpty()) return emptyList()

        val header = rows.first()
        val hasHeader = header.any { it.equals("tag", ignoreCase = true) } &&
            header.size >= 2
        val tagIndex: Int
        val zhIndex: Int
        val categoryIndex: Int
        if (hasHeader) {
            tagIndex = header.indexOfFirst {
                it.equals("tag", ignoreCase = true) || it.equals("name", ignoreCase = true)
            }.coerceAtLeast(0)
            zhIndex = header.indexOfFirst {
                it.contains("zh", ignoreCase = true) ||
                    it.contains("alias", ignoreCase = true) ||
                    it.contains("translation", ignoreCase = true)
            }
            categoryIndex = header.indexOfFirst { it.contains("category", ignoreCase = true) }
        } else {
            tagIndex = 0
            categoryIndex = if (header.size >= 3) 1 else -1
            zhIndex = if (header.size >= 4) 3 else if (header.size >= 2) 1 else -1
        }

        val merged = LinkedHashMap<String, PromptLexiconEntry>()
        val dataRows = if (hasHeader) rows.drop(1) else rows
        dataRows.forEach { row ->
            val tag = row.getOrNull(tagIndex)?.trim().orEmpty()
            if (tag.isEmpty()) return@forEach
            val category = row.getOrNull(categoryIndex)?.trim().orEmpty()
            val aliases = row.getOrNull(zhIndex)
                .orEmpty()
                .split(aliasSplitter)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val entry = PromptLexiconEntry(
                tag = tag,
                zh = aliases.firstOrNull().orEmpty(),
                aliases = aliases.drop(1),
                category = category,
            )
            merge(merged, entry)
        }
        return merged.values.toList()
    }

    private fun parseJson(text: String): List<PromptLexiconEntry> {
        val root = json.parseToJsonElement(text)
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val wrapped = root["entries"] ?: root["tags"] ?: root["data"] ?: root["items"]
                when {
                    wrapped is JsonArray -> wrapped
                    wrapped is JsonObject -> mappingToArray(wrapped)
                    root.containsKey("tag") || root.containsKey("name") -> JsonArray(listOf(root))
                    // 兼容 { "tag": "中文别名" } 的映射格式。
                    else -> mappingToArray(root)
                }
            }
            else -> error("无法识别的 JSON 结构")
        }

        val merged = LinkedHashMap<String, PromptLexiconEntry>()
        array.forEach { element ->
            when (element) {
                is JsonPrimitive -> {
                    val tag = element.contentOrNull?.trim().orEmpty()
                    if (tag.isNotEmpty()) {
                        merge(merged, PromptLexiconEntry(tag = tag, zh = ""))
                    }
                }
                is JsonObject -> {
                    val tag = firstString(element, "tag", "name", "prompt").orEmpty()
                    if (tag.isEmpty()) return@forEach
                    val aliases = firstAliases(element)
                    merge(
                        merged,
                        PromptLexiconEntry(
                            tag = tag,
                            zh = aliases.firstOrNull().orEmpty(),
                            aliases = aliases.drop(1),
                            category = firstString(element, "category").orEmpty(),
                        ),
                    )
                }
                else -> Unit
            }
        }
        return merged.values.toList()
    }

    private fun mappingToArray(obj: JsonObject): JsonArray = JsonArray(
        obj.entries.map { (tag, value) ->
            JsonObject(
                mapOf(
                    "tag" to JsonPrimitive(tag),
                    "zh_aliases" to value,
                ),
            )
        },
    )

    private fun firstString(obj: JsonObject, vararg keys: String): String? {
        keys.forEach { key ->
            val value = obj[key] as? JsonPrimitive ?: return@forEach
            if (value.isString) return value.contentOrNull?.trim()
        }
        return null
    }

    private fun firstAliases(obj: JsonObject): List<String> {
        val keys = listOf("zh_aliases", "zh", "translation", "aliases", "alias")
        keys.forEach { key ->
            when (val value = obj[key]) {
                is JsonPrimitive -> {
                    if (!value.isString) return@forEach
                    val parsed = value.contentOrNull.orEmpty()
                        .split(aliasSplitter)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (parsed.isNotEmpty()) return parsed
                }
                is JsonArray -> {
                    val parsed = value.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull?.trim()
                    }.filter { it.isNotEmpty() }
                    if (parsed.isNotEmpty()) return parsed
                }
                else -> Unit
            }
        }
        return emptyList()
    }

    private fun merge(
        target: MutableMap<String, PromptLexiconEntry>,
        entry: PromptLexiconEntry,
    ) {
        val key = entry.tag.lowercase()
        val existing = target[key]
        if (existing == null) {
            target[key] = entry
        } else {
            target[key] = existing.copy(
                zh = existing.zh.ifBlank { entry.zh },
                aliases = (existing.aliases + entry.aliases).distinct(),
            )
        }
    }

    /** 支持带双引号的字段，避免别名内的逗号被错误切分。 */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells += current.toString()
        return cells
    }
}
