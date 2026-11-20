package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
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
    val error: NekoMessage? = null,
    val dirty: Boolean = false,
)

class NekoSettingsViewModel(
    private val aiAssistantStore: AiAssistantSettingsStore,
    private val settingsStore: NekoSettingsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NekoSettingsUiState())
    val uiState: StateFlow<NekoSettingsUiState> = _uiState.asStateFlow()

    private var savedPersona = ""
    private var savedPreInstruction = ""

    init {
        viewModelScope.launch {
            aiAssistantStore.config.collect { config ->
                _uiState.update { current ->
                    if (current.dirty) {
                        // 用户正在编辑时不覆盖草稿；保存成功后由 save() 更新基线。
                        current.copy(loading = false)
                    } else {
                        savedPersona = config.persona
                        savedPreInstruction = config.preInstruction
                        current.copy(
                            persona = config.persona,
                            preInstruction = config.preInstruction,
                            loading = false,
                        )
                    }
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
        _uiState.update {
            it.copy(persona = value, saved = false).withDirty()
        }
    }

    fun onPreInstructionChange(value: String) {
        _uiState.update {
            it.copy(preInstruction = value, saved = false).withDirty()
        }
    }

    /** 默认模式是即时设置：点击后立刻持久化，不参与文本字段的未保存判定。 */
    fun setAgentModeByDefault(value: Boolean) {
        _uiState.update { it.copy(agentModeByDefault = value) }
        viewModelScope.launch {
            settingsStore.setAgentModeByDefault(value)
        }
    }

    fun save() {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saved = false, error = null) }
            val persona = _uiState.value.persona
            val preInstruction = _uiState.value.preInstruction
            val result = runCatching {
                aiAssistantStore.updatePersonaInstruction(
                    persona = persona,
                    preInstruction = preInstruction,
                ).getOrThrow()
            }
            _uiState.update {
                val savedPersonaValue = persona.trim()
                val savedInstructionValue = preInstruction.trim()
                if (result.isSuccess) {
                    savedPersona = savedPersonaValue
                    savedPreInstruction = savedInstructionValue
                }
                it.copy(
                    persona = if (result.isSuccess) savedPersonaValue else it.persona,
                    preInstruction = if (result.isSuccess) {
                        savedInstructionValue
                    } else {
                        it.preInstruction
                    },
                    saving = false,
                    saved = result.isSuccess,
                    error = result.exceptionOrNull()?.let { throwable ->
                        NekoMessage(
                            NekoMessageKey.SETTINGS_NEKO_SAVE_FAILED,
                            listOf(throwable.message ?: throwable.javaClass.simpleName),
                        )
                    },
                ).withDirty()
            }
        }
    }

    /** 返回门禁选择“放弃修改”时恢复已保存的人设与预指令。 */
    fun discardChanges() {
        _uiState.update {
            it.copy(
                persona = savedPersona,
                preInstruction = savedPreInstruction,
                saved = false,
                error = null,
                dirty = false,
            )
        }
    }

    private fun NekoSettingsUiState.withDirty(): NekoSettingsUiState = copy(
        dirty = persona != savedPersona || preInstruction != savedPreInstruction,
    )

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
