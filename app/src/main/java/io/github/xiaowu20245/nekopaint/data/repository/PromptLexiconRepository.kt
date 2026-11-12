package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.workflow.PromptLexiconEntry
import io.github.xiaowu20245.nekopaint.core.workflow.PromptLexiconParser
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagCatalog
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class PromptLexiconState(
    val externalCount: Int = 0,
    val fileName: String = "",
    val importedAt: Long = 0L,
) {
    val hasExternal: Boolean
        get() = externalCount > 0

    val totalCount: Int
        get() = PromptTagCatalog.builtInCount + externalCount
}

data class PromptImportResult(
    val fileName: String,
    val addedCount: Int,
)

/**
 * 外部词库的本地持久化与应用。
 *
 * 外部词条保存为应用私有目录下的 JSON，启动时加载并写入 PromptTagCatalog；
 * 导入/清空后立即刷新覆盖层，无需重启应用。
 */
class PromptLexiconRepository(
    context: Context,
    private val json: Json,
) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "prompt_lexicon/external_lexicon.json")

    private val _state = MutableStateFlow(PromptLexiconState())
    val state: StateFlow<PromptLexiconState> = _state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            apply(emptyList(), "", 0L)
            return@withContext
        }
        runCatching {
            json.decodeFromString<StoredLexicon>(file.readText())
        }.onSuccess { stored ->
            applyStored(stored.entries, stored.fileName, stored.importedAt)
        }.onFailure {
            // 文件损坏时保持内置词库可用，不阻塞应用启动。
            apply(emptyList(), "", 0L)
        }
    }

    suspend fun import(uri: Uri): Result<PromptImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "外部词表"
            val content = appContext.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("无法读取所选文件")
            if (content.length > MAX_FILE_CHARS) {
                error("文件过大，请选择小于 20 MB 的词表")
            }
            val entries = PromptLexiconParser.parse(content, displayName).getOrThrow()
            val stored = StoredLexicon(
                fileName = displayName,
                importedAt = System.currentTimeMillis(),
                entries = entries.map { entry ->
                    StoredEntry(
                        tag = entry.tag,
                        zh = entry.zh,
                        aliases = entry.aliases,
                        category = entry.category,
                    )
                },
            )
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(stored))
            apply(entries, displayName, stored.importedAt)
            PromptImportResult(fileName = displayName, addedCount = entries.size)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        apply(emptyList(), "", 0L)
    }

    suspend fun exportTagGroups(
        uri: Uri,
        groups: List<PromptTagGroup>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (groups.isEmpty()) error("没有可导出的标签组")
            val backup = TagGroupBackup(
                exportedAt = System.currentTimeMillis(),
                groups = groups.map { group ->
                    StoredTagGroup(name = group.name, tags = group.tags)
                },
            )
            appContext.contentResolver.openOutputStream(uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(json.encodeToString(backup)) }
                ?: error("无法写入所选位置")
            groups.size
        }
    }

    private fun apply(
        entries: List<PromptLexiconEntry>,
        fileName: String,
        importedAt: Long,
    ) {
        val suggestions = entries.map { entry ->
            PromptTagSuggestion(
                tag = entry.tag,
                zh = entry.zh,
                aliases = entry.aliases,
            )
        }
        PromptTagCatalog.setExternalSuggestions(suggestions)
        _state.value = PromptLexiconState(
            externalCount = suggestions.size,
            fileName = fileName,
            importedAt = importedAt,
        )
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        }.getOrNull()

    @Serializable
    private data class StoredLexicon(
        val version: Int = 1,
        val fileName: String = "",
        val importedAt: Long = 0L,
        val entries: List<StoredEntry> = emptyList(),
    )

    @Serializable
    private data class StoredEntry(
        val tag: String,
        val zh: String = "",
        val aliases: List<String> = emptyList(),
        val category: String = "",
    )

    private fun applyStored(
        entries: List<StoredEntry>,
        fileName: String,
        importedAt: Long,
    ) = apply(
        entries = entries.map { PromptLexiconEntry(it.tag, it.zh, it.aliases, it.category) },
        fileName = fileName,
        importedAt = importedAt,
    )

    @Serializable
    private data class TagGroupBackup(
        val version: Int = 1,
        val exportedAt: Long = 0L,
        val groups: List<StoredTagGroup> = emptyList(),
    )

    @Serializable
    private data class StoredTagGroup(
        val name: String,
        val tags: List<String> = emptyList(),
    )

    private companion object {
        const val MAX_FILE_CHARS = 20 * 1024 * 1024
    }
}
