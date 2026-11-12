package io.github.xiaowu20245.nekopaint.feature.settings

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAssistantFormState(
    val providerId: String = AiProviderPreset.DeepSeek.id,
    val baseUrl: String = "",
    val apiKey: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    val keyVisible: Boolean = false,
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testLatencyMs: Long? = null,
    val testError: String? = null,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
) {
    val canSubmit: Boolean
        get() = baseUrl.isNotBlank() && textModel.isNotBlank()

    fun toConfig(): AiAssistantConfig = AiAssistantConfig(
        providerId = providerId,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        textModel = textModel.trim(),
        visionModel = visionModel.trim(),
    )
}

class AiAssistantSettingsViewModel(
    private val store: AiAssistantStore,
    private val client: AiAssistantClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantFormState())
    val uiState: StateFlow<AiAssistantFormState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = store.current()
            _uiState.update { state ->
                state.copy(
                    providerId = config.providerId.ifBlank {
                        AiProviderPreset.DeepSeek.id
                    },
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    textModel = config.textModel,
                    visionModel = config.visionModel,
                    loaded = true,
                )
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update {
            it.copy(
                baseUrl = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update {
            it.copy(
                apiKey = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun onTextModelChange(value: String) {
        _uiState.update {
            it.copy(
                textModel = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun onVisionModelChange(value: String) {
        _uiState.update {
            it.copy(
                visionModel = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun toggleKeyVisible() {
        _uiState.update { it.copy(keyVisible = !it.keyVisible) }
    }

    fun applyPreset(preset: AiProviderPreset) {
        _uiState.update {
            it.copy(
                providerId = preset.id,
                baseUrl = preset.baseUrl,
                textModel = preset.textModel,
                visionModel = preset.visionModel,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.testing || !state.canSubmit) {
            if (!state.canSubmit) {
                _uiState.update { it.copy(error = "请填写 Base URL 和文本模型") }
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    testing = true,
                    testLatencyMs = null,
                    testError = null,
                    error = null,
                )
            }
            val result = client.testConnection(state.toConfig())
            _uiState.update {
                it.copy(
                    testing = false,
                    testLatencyMs = result.getOrNull(),
                    testError = result.exceptionOrNull()?.message ?: "连接失败",
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return
        if (!state.canSubmit) {
            _uiState.update { it.copy(error = "请填写 Base URL 和文本模型") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, saved = false) }
            store.save(state.toConfig())
                .onSuccess {
                    _uiState.update { it.copy(saving = false, saved = true) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = throwable.message ?: "保存失败",
                        )
                    }
                }
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiAssistantSettingsViewModel(
                    store = container.aiAssistantStore,
                    client = container.aiAssistantClient,
                )
            }
        }
    }
}
