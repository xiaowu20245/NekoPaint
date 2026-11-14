package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 自动把历史中的最终 output 图片保存到系统相册。
 *
 * 只处理 App 已经拉取到的历史；保存过的图片用持久化 key 去重，避免每次刷新重复创建文件。
 * 临时预览图（type=temp）不会自动保存。
 */
class HistoryAutoSaver(
    context: Context,
    private val json: Json,
    private val settingsStore: SettingsStore,
    private val imageSaver: ImageSaver,
) {

    private val file = File(context.applicationContext.filesDir, AUTO_SAVE_FILE)
    private val mutex = Mutex()
    private var savedKeys: LinkedHashSet<String>? = null

    suspend fun saveNew(serverId: String, entries: List<HistoryEntry>) {
        val enabled = runCatching { settingsStore.autoSaveHistoryImages.first() }
            .getOrDefault(false)
        if (!enabled) return

        mutex.withLock {
            val keys = savedKeys ?: loadKeys().also { savedKeys = it }
            var changed = false
            entries.forEach { entry ->
                entry.images
                    .asSequence()
                    .filter { image -> image.type == "output" && !image.url.isNullOrBlank() }
                    .forEach { image ->
                        val key = autoSaveImageKey(serverId, entry.promptId, image)
                        if (key in keys) return@forEach
                        val result = runCatching {
                            imageSaver.save(image.url.orEmpty(), image.filename)
                        }.getOrNull()
                        if (result?.isSuccess == true) {
                            keys += key
                            changed = true
                        }
                    }
            }
            if (changed) {
                persist(keys)
            }
        }
    }

    private suspend fun loadKeys(): LinkedHashSet<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext LinkedHashSet()
        runCatching {
            json.decodeFromString<List<String>>(file.readText())
        }.getOrDefault(emptyList()).let(::LinkedHashSet)
    }

    private suspend fun persist(keys: LinkedHashSet<String>) = withContext(Dispatchers.IO) {
        while (keys.size > MAX_SAVED_KEYS) {
            keys.remove(keys.first())
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "$AUTO_SAVE_FILE.tmp")
        temp.writeText(json.encodeToString(keys.toList()))
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private companion object {
        const val AUTO_SAVE_FILE = "auto_saved_history_images.json"
        const val MAX_SAVED_KEYS = 5000
    }
}

internal fun autoSaveImageKey(
    serverId: String,
    promptId: String,
    image: HistoryImage,
): String = buildString {
    append(serverId)
    append('\u0000')
    append(promptId)
    append('\u0000')
    append(image.type)
    append('\u0000')
    append(image.subfolder)
    append('\u0000')
    append(image.filename)
}
