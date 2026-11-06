package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.common.AppLog
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ModelListParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 模型目录缓存，按服务器隔离。键是 ComfyUI 目录名（checkpoints/loras/vae...）。
 */
class ModelCatalogStore(
    context: Context,
    private val json: Json,
) {

    private val filesDir = context.applicationContext.filesDir
    private val rootDir = File(filesDir, "servers")
    private val legacyDir = File(filesDir, "models")

    suspend fun load(serverId: String): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val file = fileFor(serverId)
        if (!file.exists()) return@withContext emptyMap()
        runCatching {
            json.decodeFromString<Map<String, List<String>>>(file.readText())
        }.getOrDefault(emptyMap())
    }

    suspend fun save(serverId: String, catalog: Map<String, List<String>>) =
        withContext(Dispatchers.IO) {
            val file = fileFor(serverId)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "catalog.json.tmp")
            temp.writeText(json.encodeToString(catalog))
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }

    private fun fileFor(serverId: String): File {
        val dir = File(rootDir, "$serverId/models")
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
        return File(dir, "catalog.json")
    }
}

/**
 * 读取模型列表。优先网络，失败时退回上次成功缓存；
 * 只读，不提供下载/删除。
 */
class ModelsRepository(
    private val apiFactory: ComfyApiFactory,
    private val serverConfigStore: ServerConfigStore,
    private val store: ModelCatalogStore,
) {

    suspend fun load(folders: Set<String>): Result<Map<String, List<String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val serverId = serverConfigStore.activeServerId.first()
                    ?: error("未配置服务器")
                val config = serverConfigStore.config.first()
                    ?: error("未配置服务器")
                val cached = store.load(serverId).toMutableMap()
                val api = apiFactory.create(config)
                folders.forEach { folder ->
                    runCatching { ModelListParser.parse(api.models(folder)) }
                        .onSuccess { models ->
                            AppLog.d("models/$folder: ${models.size}")
                            cached[folder] = models
                        }
                        .onFailure { throwable ->
                            AppLog.w("models/$folder failed", throwable)
                        }
                }
                runCatching { store.save(serverId, cached) }
                    .onFailure { AppLog.w("save model catalog failed", it) }
                cached
            }
        }
}
