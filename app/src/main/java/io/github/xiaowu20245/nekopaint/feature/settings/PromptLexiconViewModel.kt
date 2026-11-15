package io.github.xiaowu20245.nekopaint.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagCatalog
import io.github.xiaowu20245.nekopaint.data.repository.PromptLexiconRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PromptLexiconNotice {
    data class Imported(val fileName: String, val count: Int) : PromptLexiconNotice
    data object Cleared : PromptLexiconNotice
    data class Exported(val count: Int) : PromptLexiconNotice
    data class CustomTagAdded(val tag: String) : PromptLexiconNotice
    data class CustomTagRemoved(val tag: String) : PromptLexiconNotice
    data class Failed(val message: String) : PromptLexiconNotice
}

data class PromptLexiconUiState(
    val builtInCount: Int = PromptTagCatalog.builtInCount,
    val externalCount: Int = 0,
    val customCount: Int = 0,
    val customTags: List<String> = emptyList(),
    val fileName: String = "",
    val importing: Boolean = false,
    val clearing: Boolean = false,
    val exporting: Boolean = false,
    val addingCustom: Boolean = false,
    val removingCustomTag: String? = null,
    val notice: PromptLexiconNotice? = null,
)

class PromptLexiconViewModel(
    private val repository: PromptLexiconRepository,
    private val workflowRepository: WorkflowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptLexiconUiState())
    val uiState: StateFlow<PromptLexiconUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { state ->
                _uiState.update {
                    it.copy(
                        builtInCount = PromptTagCatalog.builtInCount,
                        externalCount = state.externalCount,
                        customCount = state.customCount,
                        customTags = state.customTags,
                        fileName = state.fileName,
                    )
                }
            }
        }
    }

    fun import(uri: Uri) {
        if (_uiState.value.importing) return
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true) }
            repository.import(uri)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            importing = false,
                            notice = PromptLexiconNotice.Imported(
                                fileName = result.fileName,
                                count = result.addedCount,
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            importing = false,
                            notice = PromptLexiconNotice.Failed(
                                throwable.message ?: "导入失败",
                            ),
                        )
                    }
                }
        }
    }

    fun clearExternal() {
        if (_uiState.value.clearing) return
        viewModelScope.launch {
            _uiState.update { it.copy(clearing = true) }
            runCatching { repository.clear() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            clearing = false,
                            notice = PromptLexiconNotice.Cleared,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            clearing = false,
                            notice = PromptLexiconNotice.Failed(
                                throwable.message ?: "清空失败",
                            ),
                        )
                    }
                }
        }
    }

    fun addCustomTag(tag: String, zh: String) {
        if (_uiState.value.addingCustom) return
        val value = tag.trim()
        if (value.isBlank()) {
            _uiState.update {
                it.copy(notice = PromptLexiconNotice.Failed("标签不能为空"))
            }
            return
        }
        if (_uiState.value.customTags.any { it.equals(value, ignoreCase = true) }) {
            _uiState.update {
                it.copy(notice = PromptLexiconNotice.Failed("标签已存在"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(addingCustom = true) }
            repository.addCustomTag(value, zh)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            addingCustom = false,
                            notice = PromptLexiconNotice.CustomTagAdded(value),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            addingCustom = false,
                            notice = PromptLexiconNotice.Failed(
                                throwable.message ?: "添加标签失败",
                            ),
                        )
                    }
                }
        }
    }

    fun removeCustomTag(tag: String) {
        if (_uiState.value.removingCustomTag != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(removingCustomTag = tag) }
            repository.removeCustomTag(tag)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            removingCustomTag = null,
                            notice = PromptLexiconNotice.CustomTagRemoved(tag),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            removingCustomTag = null,
                            notice = PromptLexiconNotice.Failed(
                                throwable.message ?: "删除标签失败",
                            ),
                        )
                    }
                }
        }
    }

    fun exportTagGroups(uri: Uri) {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            val groups = runCatching {
                workflowRepository.listTemplates().flatMap { it.promptTagGroups }
            }.getOrDefault(emptyList())
            repository.exportTagGroups(uri, groups)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            exporting = false,
                            notice = PromptLexiconNotice.Exported(count),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            exporting = false,
                            notice = PromptLexiconNotice.Failed(
                                throwable.message ?: "导出失败",
                            ),
                        )
                    }
                }
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PromptLexiconViewModel(
                    repository = container.promptLexiconRepository,
                    workflowRepository = container.workflowRepository,
                )
            }
        }
    }
}
