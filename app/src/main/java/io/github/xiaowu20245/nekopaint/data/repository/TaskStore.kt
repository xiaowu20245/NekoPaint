package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class TaskStore(
    context: Context,
    private val json: Json,
) {

    private val filesDir = context.applicationContext.filesDir
    private val rootDir = File(filesDir, "servers")
    private val legacyDir = File(filesDir, "tasks")

    suspend fun save(serverId: String, record: TaskRecord) = withContext(Dispatchers.IO) {
        val dir = dirFor(serverId)
        if (!dir.exists()) dir.mkdirs()
        write(serverId, record)
    }

    suspend fun get(serverId: String, promptId: String): TaskRecord? = withContext(Dispatchers.IO) {
        val file = File(dirFor(serverId), "$promptId.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString(TaskRecord.serializer(), file.readText())
                .copy(serverId = serverId)
        }.getOrNull()
    }

    suspend fun update(serverId: String, record: TaskRecord) = withContext(Dispatchers.IO) {
        val dir = dirFor(serverId)
        if (!dir.exists()) dir.mkdirs()
        write(serverId, record)
    }

    private fun write(serverId: String, record: TaskRecord) {
        val scoped = if (record.serverId.isBlank()) record.copy(serverId = serverId) else record
        File(dirFor(serverId), "${record.promptId}.json").writeText(
            json.encodeToString(TaskRecord.serializer(), scoped),
        )
    }

    suspend fun list(serverId: String): List<TaskRecord> = withContext(Dispatchers.IO) {
        val dir = dirFor(serverId)
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file ->
                runCatching {
                    json.decodeFromString(TaskRecord.serializer(), file.readText())
                        .copy(serverId = serverId)
                }.getOrNull()
            }
            .sortedByDescending { it.submittedAt }
    }

    private fun dirFor(serverId: String): File {
        val dir = File(rootDir, "$serverId/tasks")
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
        return dir
    }
}
