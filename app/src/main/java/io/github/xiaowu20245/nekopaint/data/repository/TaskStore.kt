package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.common.AtomicFileWriter
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class TaskStore private constructor(
    private val rootDir: File,
    private val legacyDir: File,
    private val json: Json,
) {

    constructor(context: Context, json: Json) : this(
        rootDir = File(context.applicationContext.filesDir, "servers"),
        legacyDir = File(context.applicationContext.filesDir, "tasks"),
        json = json,
    )

    internal constructor(filesDir: File, json: Json) : this(
        rootDir = File(filesDir, "servers"),
        legacyDir = File(filesDir, "tasks"),
        json = json,
    )

    private val mutex = Mutex()
    private val migrationLock = Any()

    suspend fun save(serverId: String, record: TaskRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            write(serverId, record)
        }
    }

    suspend fun get(serverId: String, promptId: String): TaskRecord? = withContext(Dispatchers.IO) {
        val file = taskFile(serverId, promptId)
        if (!file.exists()) return@withContext null
        readRecord(file, serverId)?.takeIf { record ->
            record.serverId.isBlank() || record.serverId == serverId
        }
    }

    suspend fun update(serverId: String, record: TaskRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            write(serverId, record)
        }
    }

    private fun write(serverId: String, record: TaskRecord) {
        val dir = dirFor(serverId)
        dir.mkdirs()
        val scoped = if (record.serverId.isBlank()) record.copy(serverId = serverId) else record
        AtomicFileWriter.writeText(
            target = taskFile(serverId, record.promptId),
            text = json.encodeToString(TaskRecord.serializer(), scoped),
        )
    }

    suspend fun list(serverId: String): List<TaskRecord> = withContext(Dispatchers.IO) {
        readTasks(
            dir = dirFor(serverId),
            fallbackServerId = serverId,
            expectedServerId = serverId,
        ).sortedByDescending { it.submittedAt }
    }

    /**
     * 读取所有主机的本地任务归档，任务记录自身带 serverId。
     */
    suspend fun listAll(): List<TaskRecord> = withContext(Dispatchers.IO) {
        val dirs = rootDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .toMutableList()
        val result = mutableListOf<TaskRecord>()
        dirs.forEach { dir ->
            val fallback = dir.name.takeUnless { it.startsWith("sha256_") }
            result += readTasks(
                dir = File(dir, "tasks"),
                fallbackServerId = fallback,
                expectedServerId = null,
            )
        }
        if (dirs.none { it.name == SafeFileNames.encode(ServerConfigStore.DEFAULT_SERVER_ID) }) {
            result += list(ServerConfigStore.DEFAULT_SERVER_ID)
        }
        result
            .distinctBy { task -> task.serverId to task.promptId }
            .sortedByDescending { it.submittedAt }
    }

    private fun readTasks(
        dir: File,
        fallbackServerId: String?,
        expectedServerId: String?,
    ): List<TaskRecord> =
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file -> readRecord(file, fallbackServerId) }
            .filter { record ->
                expectedServerId == null || record.serverId == expectedServerId
            }

    private fun readRecord(file: File, fallbackServerId: String?): TaskRecord? =
        runCatching {
            val record = json.decodeFromString(TaskRecord.serializer(), file.readText())
            when {
                record.serverId.isNotBlank() -> record
                fallbackServerId != null -> record.copy(serverId = fallbackServerId)
                else -> record
            }
        }.getOrNull()

    private fun taskFile(serverId: String, promptId: String): File =
        safeChild(dirFor(serverId), "${SafeFileNames.encode(promptId)}.json")

    private fun dirFor(serverId: String): File {
        val safeServerId = SafeFileNames.encode(serverId)
        val dir = safeChild(rootDir, "$safeServerId/tasks")
        if (serverId == ServerConfigStore.DEFAULT_SERVER_ID &&
            !dir.exists() &&
            legacyDir.exists()
        ) {
            synchronized(migrationLock) {
                if (!dir.exists() && legacyDir.exists()) {
                    dir.parentFile?.mkdirs()
                    if (!legacyDir.renameTo(dir)) {
                        legacyDir.copyRecursively(dir, overwrite = false)
                        legacyDir.deleteRecursively()
                    }
                }
            }
        }
        return dir
    }

    /**
     * 目标路径必须位于任务根目录内。调用前文件名已经过 [SafeFileNames]，
     * 这里再做一次 canonical 校验作为纵深防线。
     */
    private fun safeChild(parent: File, name: String): File {
        val rootPath = rootDir.canonicalFile
        val target = File(parent.canonicalFile, name).canonicalFile
        require(
            target.path == rootPath.path ||
                target.path.startsWith(rootPath.path + File.separator),
        ) {
            "任务路径越界"
        }
        return target
    }
}
