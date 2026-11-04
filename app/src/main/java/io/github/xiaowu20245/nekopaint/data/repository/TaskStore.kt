package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class TaskStore(
    context: Context,
    private val json: Json,
) {

    private val rootDir = File(context.applicationContext.filesDir, "tasks")

    suspend fun save(record: TaskRecord) = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) rootDir.mkdirs()
        write(record)
    }

    suspend fun get(promptId: String): TaskRecord? = withContext(Dispatchers.IO) {
        val file = File(rootDir, "$promptId.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString(TaskRecord.serializer(), file.readText())
        }.getOrNull()
    }

    suspend fun update(record: TaskRecord) = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) rootDir.mkdirs()
        write(record)
    }

    private fun write(record: TaskRecord) {
        File(rootDir, "${record.promptId}.json").writeText(
            json.encodeToString(TaskRecord.serializer(), record),
        )
    }

    suspend fun list(): List<TaskRecord> = withContext(Dispatchers.IO) {
        rootDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file ->
                runCatching {
                    json.decodeFromString(TaskRecord.serializer(), file.readText())
                }.getOrNull()
            }
            .sortedByDescending { it.submittedAt }
    }
}
