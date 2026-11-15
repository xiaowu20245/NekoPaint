package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
import io.github.xiaowu20245.nekopaint.core.datastore.NekoSettingsPreferences
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NekoSettingsUiState(
    val persona: String = "",
    val preInstruction: String = "",
    val agentModeByDefault: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class NekoSettingsViewModel(
    private val aiAssistantStore: AiAssistantSettingsStore,
    private val settingsStore: NekoSettingsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NekoSettingsUiState())
    val uiState: StateFlow<NekoSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            aiAssistantStore.config.collect { config ->
                _uiState.update {
                    it.copy(
                        persona = config.persona,
                        preInstruction = config.preInstruction,
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.agentModeByDefault.collect { value ->
                _uiState.update { it.copy(agentModeByDefault = value) }
            }
        }
    }

    fun onPersonaChange(value: String) {
        _uiState.update { it.copy(persona = value, saved = false) }
    }

    fun onPreInstructionChange(value: String) {
        _uiState.update { it.copy(preInstruction = value, saved = false) }
    }

    fun setAgentModeByDefault(value: Boolean) {
        _uiState.update { it.copy(agentModeByDefault = value, saved = false) }
        viewModelScope.launch {
            settingsStore.setAgentModeByDefault(value)
        }
    }

    fun save() {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saved = false, error = null) }
            val result = runCatching {
                aiAssistantStore.updatePersonaInstruction(
                    persona = _uiState.value.persona,
                    preInstruction = _uiState.value.preInstruction,
                ).getOrThrow()
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    saved = result.isSuccess,
                    error = result.exceptionOrNull()?.message,
                )
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
                NekoSettingsViewModel(
                    aiAssistantStore = container.aiAssistantStore,
                    settingsStore = container.settingsStore,
                )
            }
        }
    }
}
