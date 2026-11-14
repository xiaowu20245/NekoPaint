package io.github.xiaowu20245.nekopaint.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.datastore.ThemeMode
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagCatalog
import io.github.xiaowu20245.nekopaint.data.repository.MediaCacheManager
import io.github.xiaowu20245.nekopaint.data.repository.PromptLexiconRepository
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val cacheBytes: Long = 0L,
    val loading: Boolean = true,
    val clearing: Boolean = false,
    val cacheCleared: Boolean = false,
    val error: String? = null,
    val showTempPreviews: Boolean = false,
    val autoSaveHistoryImages: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val builtInWords: Int = 0,
    val externalWords: Int = 0,
    val externalFileName: String = "",
    val aiConfig: AiAssistantConfig = AiAssistantConfig(),
)

class SettingsViewModel(
    private val cacheManager: MediaCacheManager,
    private val settingsStore: SettingsStore,
    private val serverConfigStore: ServerConfigStore,
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
    private val promptLexiconRepository: PromptLexiconRepository,
    private val aiAssistantStore: AiAssistantStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshCacheSize()
        viewModelScope.launch {
            settingsStore.showTempPreviews.collect { showTempPreviews ->
                _uiState.update { it.copy(showTempPreviews = showTempPreviews) }
            }
        }
        viewModelScope.launch {
            settingsStore.autoSaveHistoryImages.collect { autoSave ->
                _uiState.update { it.copy(autoSaveHistoryImages = autoSave) }
            }
        }
        viewModelScope.launch {
            settingsStore.themeMode.collect { themeMode ->
                _uiState.update { it.copy(themeMode = themeMode) }
            }
        }
        viewModelScope.launch {
            promptLexiconRepository.state.collect { lexicon ->
                _uiState.update {
                    it.copy(
                        builtInWords = PromptTagCatalog.builtInCount,
                        externalWords = lexicon.externalCount,
                        externalFileName = lexicon.fileName,
                    )
                }
            }
        }
        viewModelScope.launch {
            aiAssistantStore.config.collect { config ->
                _uiState.update { it.copy(aiConfig = config) }
            }
        }
    }

    fun setShowTempPreviews(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setShowTempPreviews(value)
        }
    }

    fun setAutoSaveHistoryImages(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoSaveHistoryImages(value)
            if (value) taskManager.refresh()
        }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(value)
        }
    }

    suspend fun buildDiagnostics(): String {
        val server = runCatching { serverConfigStore.activeServer.first() }.getOrNull()
        val host = server?.host.orEmpty()
        fun sanitize(text: String?): String {
            if (text.isNullOrBlank()) return "无"
            return if (host.isBlank()) text else text.replace(host, "***")
        }

        val queue = taskManager.queue.value
        val historyCount = taskManager.history.value.size
        val templateCount = runCatching { workflowRepository.listTemplates().size }
            .getOrDefault(-1)
        val cacheBytes = runCatching { cacheManager.sizeBytes() }.getOrDefault(0L)
        val theme = runCatching { settingsStore.themeMode.first() }
            .getOrDefault(ThemeMode.SYSTEM)
        val showTemp = runCatching { settingsStore.showTempPreviews.first() }
            .getOrDefault(false)
        val autoSave = runCatching { settingsStore.autoSaveHistoryImages.first() }
            .getOrDefault(false)
        val aiConfig = runCatching { aiAssistantStore.current() }
            .getOrDefault(AiAssistantConfig())
        val externalWords = promptLexiconRepository.state.value.externalCount
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        return buildString {
            appendLine("NekoPaint 诊断信息")
            appendLine("时间：$timestamp")
            appendLine("版本：${BuildConfig.VERSION_NAME}")
            appendLine("Android：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("服务器：")
            if (server == null) {
                appendLine("  未配置")
            } else {
                appendLine("  地址：${server.scheme}://***:${server.port}")
                appendLine("  连接状态：${taskManager.connection.value.name}")
                appendLine("  队列：运行 ${queue?.running?.size ?: 0} / 等待 ${queue?.pending?.size ?: 0}")
                appendLine("  历史条目：$historyCount")
            }
            appendLine("模板数量：${if (templateCount >= 0) templateCount else "读取失败"}")
            appendLine("图片缓存：${cacheBytes / 1024 / 1024} MB")
            appendLine("主题：${theme.name}")
            appendLine("显示临时预览图：$showTemp")
            appendLine("自动保存历史图片：$autoSave")
            appendLine("外部词库：$externalWords 词")
            appendLine(
                "AI 助手：${
                    if (aiConfig.isConfigured) {
                        "已配置 · ${aiConfig.textModel}"
                    } else {
                        "未配置"
                    }
                }",
            )
            appendLine()
            appendLine("队列错误：${sanitize(taskManager.queueError.value)}")
            appendLine("历史错误：${sanitize(taskManager.historyError.value)}")
            appendLine()
            appendLine("说明：本报告不包含提示词、图片内容、服务器 IP 或文件名。")
        }
    }

    fun refreshCacheSize(silent: Boolean = false) {
        if (_uiState.value.clearing) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(loading = true) }
            }
            val bytes = runCatching { cacheManager.sizeBytes() }.getOrDefault(0L)
            _uiState.update { it.copy(cacheBytes = bytes, loading = false) }
        }
    }

    fun clearCache() {
        if (_uiState.value.clearing) return
        viewModelScope.launch {
            _uiState.update { it.copy(clearing = true, error = null) }
            val result = cacheManager.clear()
            result.onSuccess {
                val bytes = runCatching { cacheManager.sizeBytes() }.getOrDefault(0L)
                _uiState.update {
                    it.copy(
                        cacheBytes = bytes,
                        clearing = false,
                        loading = false,
                        cacheCleared = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        clearing = false,
                        loading = false,
                        error = throwable.message ?: "清理失败",
                    )
                }
            }
        }
    }

    fun consumeCacheCleared() {
        _uiState.update { it.copy(cacheCleared = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    cacheManager = container.mediaCacheManager,
                    settingsStore = container.settingsStore,
                    serverConfigStore = container.serverConfigStore,
                    taskManager = container.taskManager,
                    workflowRepository = container.workflowRepository,
                    promptLexiconRepository = container.promptLexiconRepository,
                    aiAssistantStore = container.aiAssistantStore,
                )
            }
        }
    }
}
