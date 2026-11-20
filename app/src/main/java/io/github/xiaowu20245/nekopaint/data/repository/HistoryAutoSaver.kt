package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import io.github.xiaowu20245.nekopaint.core.common.AtomicFileWriter
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

sealed interface AutoSaveState {
    data object Idle : AutoSaveState
    data class Running(
        val total: Int,
        val completed: Int,
        val saved: Int,
        val failed: Int,
    ) : AutoSaveState

    data class Saved(val saved: Int) : AutoSaveState
    data class Failed(val saved: Int, val failed: Int) : AutoSaveState
    data class Cancelled(val saved: Int, val failed: Int) : AutoSaveState
}

/** 抽象下载器，便于 JVM 测试注入成功/失败/取消。 */
fun interface HistoryImageWriter {
    suspend fun save(url: String, originalName: String): Result<Unit>
}

fun interface AutoSaveEnabledProvider {
    suspend fun enabled(): Boolean
}

/**
 * 自动把历史中的最终 output 图片保存到系统相册。
 *
 * 每轮最多 3 张，只处理未保存过的新图片；进度通过 [state] 暴露，支持取消。
 * 失败图片不写入去重 key，下一轮可以重试；历史刷新不被保存阻塞。
 */
class HistoryAutoSaver internal constructor(
    private val file: File,
    private val json: Json,
    private val enabledProvider: AutoSaveEnabledProvider,
    private val imageWriter: HistoryImageWriter,
) {

    constructor(
        context: Context,
        json: Json,
        settingsStore: SettingsStore,
        imageSaver: ImageSaver,
    ) : this(
        file = File(context.applicationContext.filesDir, AUTO_SAVE_FILE),
        json = json,
        enabledProvider = { settingsStore.autoSaveHistoryImages.first() },
        imageWriter = { url, name -> imageSaver.save(url, name).map { Unit } },
    )

    private val mutex = Mutex()
    private var savedKeys: LinkedHashSet<String>? = null

    private val _state = MutableStateFlow<AutoSaveState>(AutoSaveState.Idle)
    val state: StateFlow<AutoSaveState> = _state.asStateFlow()

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var cancelGeneration = 0L

    fun cancel() {
        cancelRequested = true
        cancelGeneration += 1
    }

    suspend fun saveNew(serverId: String, entries: List<HistoryEntry>): AutoSaveState {
        val enabled = runCatching { enabledProvider.enabled() }.getOrDefault(false)
        if (!enabled) {
            _state.value = AutoSaveState.Idle
            return AutoSaveState.Idle
        }

        val launchGeneration = cancelGeneration
        return mutex.withLock {
            // 若在排队等锁期间用户已取消，这一轮不再开始。
            if (launchGeneration != cancelGeneration) {
                val cancelled = AutoSaveState.Cancelled(saved = 0, failed = 0)
                _state.value = cancelled
                return@withLock cancelled
            }
            cancelRequested = false
            val keys = savedKeys ?: loadKeys().also { savedKeys = it }
            val candidates = entries
                .asSequence()
                .flatMap { entry ->
                    entry.images.asSequence()
                        .filter { image -> image.type == "output" && !image.url.isNullOrBlank() }
                        .map { image ->
                            Candidate(
                                key = autoSaveImageKey(serverId, entry.promptId, image),
                                url = image.url.orEmpty(),
                                filename = image.filename,
                            )
                        }
                }
                .distinctBy { it.key }
                .filterNot { it.key in keys }
                .take(MAX_IMAGES_PER_ROUND)
                .toList()
            if (candidates.isEmpty()) {
                _state.value = AutoSaveState.Idle
                return@withLock AutoSaveState.Idle
            }

            var saved = 0
            var failed = 0
            _state.value = AutoSaveState.Running(
                total = candidates.size,
                completed = 0,
                saved = 0,
                failed = 0,
            )
            for ((index, candidate) in candidates.withIndex()) {
                if (cancelRequested) {
                    // 取消前已成功的图片仍要记住，避免下一轮重复下载。
                    if (saved > 0) {
                        persist(keys)
                    }
                    val cancelled = AutoSaveState.Cancelled(saved = saved, failed = failed)
                    _state.value = cancelled
                    return@withLock cancelled
                }
                val result = runCatching {
                    imageWriter.save(candidate.url, candidate.filename)
                }.getOrNull()
                if (result?.isSuccess == true) {
                    keys += candidate.key
                    saved += 1
                } else {
                    failed += 1
                }
                _state.value = AutoSaveState.Running(
                    total = candidates.size,
                    completed = index + 1,
                    saved = saved,
                    failed = failed,
                )
            }
            if (saved > 0) {
                persist(keys)
            }
            val finalState = if (failed > 0) {
                AutoSaveState.Failed(saved = saved, failed = failed)
            } else {
                AutoSaveState.Saved(saved = saved)
            }
            _state.value = finalState
            finalState
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
        AtomicFileWriter.writeText(file, json.encodeToString(keys.toList()))
    }

    private data class Candidate(
        val key: String,
        val url: String,
        val filename: String,
    )

    private companion object {
        const val AUTO_SAVE_FILE = "auto_saved_history_images.json"
        const val MAX_SAVED_KEYS = 5000
        const val MAX_IMAGES_PER_ROUND = 3
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
