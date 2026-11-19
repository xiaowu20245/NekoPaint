package io.github.xiaowu20245.nekopaint.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.datastore.ThemeMode
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagCatalog
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageStore
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
    val error: NekoMessage? = null,
    val showTempPreviews: Boolean = false,
    val autoSaveHistoryImages: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val reduceMotion: Boolean = false,
    val agentModeByDefault: Boolean = false,
    val builtInWords: Int = 0,
    val externalWords: Int = 0,
    val customWords: Int = 0,
    val externalFileName: String = "",
    val aiConfig: AiAssistantConfig = AiAssistantConfig(),
    val chatImageCount: Int = 0,
    val chatImageBytes: Long = 0L,
    val chatImagesClearing: Boolean = false,
    val chatImagesCleared: Boolean = false,
)

class SettingsViewModel(
    private val cacheManager: MediaCacheManager,
    private val settingsStore: SettingsStore,
    private val serverConfigStore: ServerConfigStore,
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
    private val promptLexiconRepository: PromptLexiconRepository,
    private val aiAssistantStore: AiAssistantStore,
    private val chatImageStore: ChatImageStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshCacheSize()
        refreshChatImageStats()
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
            settingsStore.dynamicColor.collect { value ->
                _uiState.update { it.copy(dynamicColor = value) }
            }
        }
        viewModelScope.launch {
            settingsStore.reduceMotion.collect { value ->
                _uiState.update { it.copy(reduceMotion = value) }
            }
        }
        viewModelScope.launch {
            settingsStore.agentModeByDefault.collect { value ->
                _uiState.update { it.copy(agentModeByDefault = value) }
            }
        }
        viewModelScope.launch {
            promptLexiconRepository.state.collect { lexicon ->
                _uiState.update {
                    it.copy(
                        builtInWords = PromptTagCatalog.builtInCount,
                        externalWords = lexicon.externalCount,
                        customWords = lexicon.customCount,
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

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setDynamicColor(value)
        }
    }

    fun setReduceMotion(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setReduceMotion(value)
        }
    }

    fun setAgentModeByDefault(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setAgentModeByDefault(value)
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
        val chatImageStats = runCatching { chatImageStore.stats() }.getOrNull()
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
            appendLine("Neko I 聊天图片：${chatImageStats?.count ?: 0} 张 / ${(chatImageStats?.bytes ?: 0L) / 1024} KB")
            appendLine("主题：${theme.name}")
            appendLine("动态取色：${settingsStore.dynamicColor.first()}")
            appendLine("减少动画：${settingsStore.reduceMotion.first()}")
            appendLine("Neko I 默认 Agent：${settingsStore.agentModeByDefault.first()}")
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
            appendLine("队列错误：${taskManager.queueError.value?.key?.name ?: "无"}")
            appendLine("历史错误：${taskManager.historyError.value?.key?.name ?: "无"}")
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
                        error = SettingsErrorPolicy.cacheClearFailed(throwable),
                    )
                }
            }
        }
    }

    fun refreshChatImageStats() {
        if (_uiState.value.chatImagesClearing) return
        viewModelScope.launch {
            val stats = runCatching { chatImageStore.stats() }.getOrNull()
            _uiState.update {
                it.copy(
                    chatImageCount = stats?.count ?: 0,
                    chatImageBytes = stats?.bytes ?: 0L,
                )
            }
        }
    }

    fun clearChatImages() {
        if (_uiState.value.chatImagesClearing) return
        viewModelScope.launch {
            _uiState.update { it.copy(chatImagesClearing = true) }
            val result = runCatching { chatImageStore.clear() }
            val stats = runCatching { chatImageStore.stats() }.getOrNull()
            _uiState.update {
                it.copy(
                    chatImagesClearing = false,
                    chatImageCount = stats?.count ?: 0,
                    chatImageBytes = stats?.bytes ?: 0L,
                    chatImagesCleared = result.isSuccess,
                    error = SettingsErrorPolicy.cacheClearFailed(result.exceptionOrNull()),
                )
            }
        }
    }

    fun consumeChatImagesCleared() {
        _uiState.update { it.copy(chatImagesCleared = false) }
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
                    chatImageStore = container.chatImageStore,
                )
            }
        }
    }
}
