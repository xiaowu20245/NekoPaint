package io.github.xiaowu20245.nekopaint.feature.workflow

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowImportResult
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowsUiState(
    val loading: Boolean = true,
    val templates: List<WorkflowTemplate> = emptyList(),
    val importing: Boolean = false,
    val busyTemplateId: String? = null,
    val actionNotice: WorkflowActionNotice? = null,
    @param:StringRes val errorRes: Int? = null,
    val errorDetail: String? = null,
    val importedTemplateId: String? = null,
)

sealed interface WorkflowActionNotice {
    data class Renamed(val name: String) : WorkflowActionNotice
    data class Copied(val name: String) : WorkflowActionNotice
    data object Deleted : WorkflowActionNotice
}

class WorkflowsViewModel(
    private val repository: WorkflowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowsUiState())
    val uiState: StateFlow<WorkflowsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val templates = runCatching { repository.listTemplates() }.getOrDefault(emptyList())
            _uiState.update { it.copy(loading = false, templates = templates) }
        }
    }

    fun import(uri: Uri) {
        if (_uiState.value.importing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(importing = true, errorRes = null, errorDetail = null)
            }
            when (val result = repository.import(uri)) {
                is WorkflowImportResult.Success -> {
                    val templates = runCatching { repository.listTemplates() }
                        .getOrDefault(_uiState.value.templates)
                    _uiState.update {
                        it.copy(
                            importing = false,
                            templates = templates,
                            importedTemplateId = result.template.id,
                        )
                    }
                }

                WorkflowImportResult.UiFormat -> {
                    _uiState.update {
                        it.copy(
                            importing = false,
                            errorRes = R.string.workflow_error_ui_format,
                        )
                    }
                }

                is WorkflowImportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            importing = false,
                            errorDetail = result.message,
                        )
                    }
                }
            }
        }
    }

    fun renameTemplate(id: String, name: String) {
        if (_uiState.value.busyTemplateId != null) return
        if (name.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.workflow_name_required) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyTemplateId = id,
                    errorRes = null,
                    errorDetail = null,
                )
            }
            repository.renameTemplate(id, name)
                .onSuccess {
                    refreshTemplates()
                    _uiState.update {
                        it.copy(
                            busyTemplateId = null,
                            actionNotice = WorkflowActionNotice.Renamed(name.trim()),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            busyTemplateId = null,
                            errorDetail = throwable.message ?: "重命名失败",
                        )
                    }
                }
        }
    }

    fun copyTemplate(id: String) {
        if (_uiState.value.busyTemplateId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyTemplateId = id,
                    errorRes = null,
                    errorDetail = null,
                )
            }
            repository.copyTemplate(id)
                .onSuccess { copied ->
                    refreshTemplates()
                    _uiState.update {
                        it.copy(
                            busyTemplateId = null,
                            actionNotice = WorkflowActionNotice.Copied(copied.name),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            busyTemplateId = null,
                            errorDetail = throwable.message ?: "复制失败",
                        )
                    }
                }
        }
    }

    fun deleteTemplate(id: String) {
        if (_uiState.value.busyTemplateId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyTemplateId = id,
                    errorRes = null,
                    errorDetail = null,
                )
            }
            runCatching { repository.deleteTemplate(id) }
                .onSuccess {
                    refreshTemplates()
                    _uiState.update {
                        it.copy(
                            busyTemplateId = null,
                            actionNotice = WorkflowActionNotice.Deleted,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            busyTemplateId = null,
                            errorDetail = throwable.message ?: "删除失败",
                        )
                    }
                }
        }
    }

    private suspend fun refreshTemplates() {
        val templates = runCatching { repository.listTemplates() }
            .getOrDefault(_uiState.value.templates)
        _uiState.update { it.copy(templates = templates) }
    }

    fun consumeActionNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }

    fun consumeImportedTemplate() {
        _uiState.update { it.copy(importedTemplateId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorRes = null, errorDetail = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkflowsViewModel(container.workflowRepository) }
        }
    }
}
