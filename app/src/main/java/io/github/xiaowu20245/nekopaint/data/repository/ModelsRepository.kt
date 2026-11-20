package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.common.AppLog
import io.github.xiaowu20245.nekopaint.core.common.AtomicFileWriter
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ModelListParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File

/** 模型候选来源；UI 用它区分网络、缓存和不支持。 */
enum class ModelSource {
    NETWORK,
    CACHE,
    UNSUPPORTED,
    ERROR,
}

data class ModelFolderSnapshot(
    val models: List<String>,
    val source: ModelSource,
)

data class ModelFolderResult(
    val folders: Map<String, ModelFolderSnapshot>,
) {
    fun modelsFor(folder: String): List<String> =
        folders[folder]?.models.orEmpty()
}

/**
 * 模型目录缓存，按服务器隔离。键是 ComfyUI 目录名（checkpoints/loras/vae...）。
 */
class ModelCatalogStore private constructor(
    private val rootDir: File,
    private val legacyDir: File,
    private val json: Json,
) {

    constructor(context: Context, json: Json) : this(
        rootDir = File(context.applicationContext.filesDir, "servers"),
        legacyDir = File(context.applicationContext.filesDir, "models"),
        json = json,
    )

    internal constructor(filesDir: File, json: Json) : this(
        rootDir = File(filesDir, "servers"),
        legacyDir = File(filesDir, "models"),
        json = json,
    )

    private val mutex = Mutex()

    suspend fun load(serverId: String): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val file = fileFor(serverId)
        if (!file.exists()) return@withContext emptyMap()
        runCatching {
            json.decodeFromString<Map<String, List<String>>>(file.readText())
        }.getOrDefault(emptyMap())
    }

    suspend fun save(serverId: String, catalog: Map<String, List<String>>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                AtomicFileWriter.writeText(
                    target = fileFor(serverId),
                    text = json.encodeToString(catalog),
                )
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
 * 读取模型列表。返回每个目录的成功来源/缓存/不支持/错误状态，不再静默回退。
 * 只读，不提供下载/删除。
 */
/** 单个模型目录的网络结果到 UI 来源的纯映射。 */
object ModelFolderResultPolicy {

    fun errorResult(
        folders: Set<String>,
        cached: Map<String, List<String>>,
    ): ModelFolderResult = ModelFolderResult(
        folders.associateWith { folder ->
            ModelFolderSnapshot(cached[folder].orEmpty(), ModelSource.ERROR)
        },
    )

    fun snapshot(
        cached: List<String>,
        network: Result<List<String>>,
    ): ModelFolderSnapshot = network.fold(
        onSuccess = { models -> ModelFolderSnapshot(models, ModelSource.NETWORK) },
        onFailure = { throwable ->
            when {
                throwable is HttpException && throwable.code() == 404 ->
                    ModelFolderSnapshot(cached, ModelSource.UNSUPPORTED)

                cached.isNotEmpty() -> ModelFolderSnapshot(cached, ModelSource.CACHE)
                else -> ModelFolderSnapshot(emptyList(), ModelSource.ERROR)
            }
        },
    )
}

class ModelsRepository(
    private val apiFactory: ComfyApiFactory,
    private val serverConfigStore: ServerConfigStore,
    private val store: ModelCatalogStore,
) {

    suspend fun load(folders: Set<String>): ModelFolderResult =
        withContext(Dispatchers.IO) {
            if (folders.isEmpty()) return@withContext ModelFolderResult(emptyMap())
            val serverId = runCatching { serverConfigStore.activeServerId.first() }
                .getOrNull()
                ?: return@withContext ModelFolderResultPolicy.errorResult(folders, emptyMap())
            val config = runCatching { serverConfigStore.config.first() }.getOrNull()
                ?: return@withContext ModelFolderResultPolicy.errorResult(folders, emptyMap())
            val cached = runCatching { store.load(serverId) }.getOrDefault(emptyMap())
            val api = runCatching { apiFactory.create(config) }.getOrNull()
                ?: return@withContext ModelFolderResultPolicy.errorResult(folders, cached)
            val networkCatalog = cached.toMutableMap()
            val result = folders.associateWith { folder ->
                val cachedModels = cached[folder].orEmpty()
                val network = runCatching { ModelListParser.parse(api.models(folder)) }
                    .onSuccess { models -> networkCatalog[folder] = models }
                    .onFailure { throwable -> AppLog.w("models/$folder failed", throwable) }
                ModelFolderResultPolicy.snapshot(cachedModels, network)
            }
            runCatching { store.save(serverId, networkCatalog) }
                .onFailure { AppLog.w("save model catalog failed", it) }
            ModelFolderResult(result)
        }
}
