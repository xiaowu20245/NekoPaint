package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 历史元数据的本地缓存，按服务器隔离。
 *
 * 服务器历史仍是最终事实来源；这里只保存最近一次成功拉取的结果，
 * 让 App 重启或临时离线时仍能打开历史列表。图片本身由 Coil 缓存。
 */
class HistoryStore(
    context: Context,
    private val json: Json,
) {

    private val filesDir = context.applicationContext.filesDir
    private val rootDir = File(filesDir, "servers")
    private val legacyDir = File(filesDir, "history")

    suspend fun load(serverId: String): List<HistoryEntry> = withContext(Dispatchers.IO) {
        val file = fileFor(serverId)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<HistoryEntry>>(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun save(serverId: String, entries: List<HistoryEntry>) = withContext(Dispatchers.IO) {
        val file = fileFor(serverId)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "history.json.tmp")
        temp.writeText(json.encodeToString(entries.take(MAX_ENTRIES)))
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun fileFor(serverId: String): File {
        val dir = File(rootDir, "$serverId/history")
        if (serverId == ServerConfigStore.DEFAULT_SERVER_ID &&
            !dir.exists() &&
            legacyDir.exists()
        ) {
            dir.parentFile?.mkdirs()
            if (!legacyDir.renameTo(dir)) {
                legacyDir.copyRecursively(dir, overwrite = false)
                legacyDir.deleteRecursively()
            }
        }
        return File(dir, "history.json")
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
