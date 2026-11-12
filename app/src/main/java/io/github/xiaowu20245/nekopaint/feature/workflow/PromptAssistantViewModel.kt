package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantClient
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PromptAssistResult {
    data class Success(val text: String) : PromptAssistResult
    data object NotConfigured : PromptAssistResult
    data class Failed(val message: String) : PromptAssistResult
}

/**
 * 提示词弹窗内的 AI 兜底能力。
 *
 * 配置来自加密存储，调用期间不把 API Key 或提示词正文写入日志。
 */
class PromptAssistantViewModel(
    store: AiAssistantStore,
    private val client: AiAssistantClient,
) : ViewModel() {

    private val _config = MutableStateFlow(AiAssistantConfig())
    val config: StateFlow<AiAssistantConfig> = _config.asStateFlow()

    init {
        viewModelScope.launch {
            store.config.collect { _config.value = it }
        }
    }

    suspend fun translate(input: String): PromptAssistResult = call { config ->
        client.translate(config, input)
    }

    suspend fun polish(prompt: String): PromptAssistResult = call { config ->
        client.polish(config, prompt)
    }

    private suspend fun call(
        block: suspend (AiAssistantConfig) -> Result<String>,
    ): PromptAssistResult {
        val config = _config.value
        if (!config.isConfigured) return PromptAssistResult.NotConfigured
        return block(config).fold(
            onSuccess = { PromptAssistResult.Success(it) },
            onFailure = { PromptAssistResult.Failed(it.message ?: "调用失败") },
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PromptAssistantViewModel(
                    store = container.aiAssistantStore,
                    client = container.aiAssistantClient,
                )
            }
        }
    }
}
