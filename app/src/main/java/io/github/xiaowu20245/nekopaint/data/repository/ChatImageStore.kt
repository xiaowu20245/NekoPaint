package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 保存用户发给 Neko I 的图片 data URL，供重启后重新调用视觉模型。
 *
 * 只保存压缩后的 data URL 文本，按图片 ID 命名；最多保留 50 张，避免无限增长。
 */
class ChatImageStore(context: Context) : ChatImageStorage {

    private val dir = File(context.applicationContext.filesDir, "chat_images")
    private val mutex = Mutex()

    override suspend fun save(id: String, dataUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.mkdirs()
            val file = fileFor(id)
            file.writeText(dataUrl)
            prune()
        }
    }

    override suspend fun load(id: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = fileFor(id)
            if (!file.exists()) return@withLock null
            runCatching { file.readText() }.getOrNull()
        }
    }

    override suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        mutex.withLock {
            ids.forEach { id -> fileFor(id).delete() }
        }
    }

    suspend fun stats(): ChatImageStats = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
            ChatImageStats(
                count = files.size,
                bytes = files.sumOf { it.length() },
            )
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun fileFor(id: String): File =
        File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")

    private fun prune() {
        val files = dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        if (files.size <= MAX_FILES) return
        files.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private companion object {
        const val MAX_FILES = 50
    }
}

data class ChatImageStats(
    val count: Int,
    val bytes: Long,
)
