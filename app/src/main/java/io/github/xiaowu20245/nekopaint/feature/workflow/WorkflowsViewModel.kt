package io.github.xiaowu20245.nekopaint.feature.workflow

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCompatibilityAnalyzer
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowPresetMatcher
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowImportResult
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowsUiState(
    val loading: Boolean = true,
    val templates: List<WorkflowTemplate> = emptyList(),
    val modeFilter: WorkflowMode? = null,
    val importing: Boolean = false,
    val busyTemplateId: String? = null,
    val runningTemplateId: String? = null,
    val actionNotice: WorkflowActionNotice? = null,
    @param:StringRes val errorRes: Int? = null,
    val errorDetail: String? = null,
    val importedTemplateId: String? = null,
) {
    val filteredTemplates: List<WorkflowTemplate>
        get() = if (modeFilter == null) {
            templates
        } else {
            templates.filter { it.compatibility?.mode == modeFilter }
        }
}

sealed interface WorkflowActionNotice {
    data class Renamed(val name: String) : WorkflowActionNotice
    data class Copied(val name: String) : WorkflowActionNotice
    data object Deleted : WorkflowActionNotice
    data class QuickSubmitted(val name: String, val count: Int) : WorkflowActionNotice
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
            ensureTemplatesCompatibility(templates)
        }
    }

    fun refreshList() {
        viewModelScope.launch {
            val templates = runCatching { repository.listTemplates() }
                .getOrDefault(_uiState.value.templates)
            _uiState.update { it.copy(templates = templates) }
        }
    }

    fun setModeFilter(mode: WorkflowMode?) {
        _uiState.update { it.copy(modeFilter = mode) }
    }

    /**
     * 在列表页直接按工作流当前已保存的参数快捷运行（支持连发次数）。
     * 若工作流含种子参数，每次自动生成新随机种子以避免命中 ComfyUI 缓存。
     */
    fun quickRun(id: String, batchCount: Int = 1) {
        if (_uiState.value.runningTemplateId != null) return
        val count = batchCount.coerceIn(1, 16)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    runningTemplateId = id,
                    errorRes = null,
                    errorDetail = null,
                )
            }
            val baseTemplate = runCatching { repository.getTemplate(id) }.getOrNull()
                ?.let { current ->
                    current.copy(
                        candidates = current.candidates.map { candidate ->
                            if (candidate.value.isBlank() && candidate.defaultValue.isNotBlank()) {
                                candidate.copy(value = candidate.defaultValue)
                            } else {
                                candidate
                            }
                        },
                    )
                }
            if (baseTemplate == null) {
                _uiState.update {
                    it.copy(
                        runningTemplateId = null,
                        errorRes = R.string.workflow_error_generic,
                    )
                }
                return@launch
            }

            var successCount = 0
            var lastTemplate: WorkflowTemplate = baseTemplate
            var errorRes: Int? = null
            var errorMessage: String? = null

            for (index in 0 until count) {
                val runTemplate = WorkflowPresetMatcher.applySeedForRun(
                    template = lastTemplate,
                    strategy = SeedStrategy.RANDOM,
                    runIndex = index,
                )
                when (val result = repository.submit(runTemplate)) {
                    is WorkflowSubmitResult.Success -> {
                        successCount++
                        lastTemplate = runTemplate
                    }
                    WorkflowSubmitResult.NoServer -> {
                        errorRes = R.string.workflow_submit_no_server
                        break
                    }
                    is WorkflowSubmitResult.Validation -> {
                        errorMessage = result.summary
                        break
                    }
                    is WorkflowSubmitResult.Error -> {
                        errorMessage = result.message
                        break
                    }
                }
            }

            if (successCount > 0) {
                runCatching { repository.updateTemplate(lastTemplate) }
                refreshTemplates()
            }

            _uiState.update {
                it.copy(
                    runningTemplateId = null,
                    actionNotice = if (successCount > 0) {
                        WorkflowActionNotice.QuickSubmitted(baseTemplate.name, successCount)
                    } else {
                        it.actionNotice
                    },
                    errorRes = errorRes,
                    errorDetail = errorMessage,
                )
            }
        }
    }

    private fun ensureTemplatesCompatibility(templates: List<WorkflowTemplate>) {
        viewModelScope.launch {
            var changed = false
            val updated = templates.map { template ->
                if (
                    template.compatibility == null ||
                    template.compatibility.analyzerVersion != WorkflowCompatibilityAnalyzer.CURRENT_VERSION
                ) {
                    val analyzed = repository.analyzeCompatibility(template)
                    if (analyzed != null) {
                        changed = true
                        runCatching { repository.updateTemplate(analyzed) }
                        analyzed
                    } else {
                        template
                    }
                } else {
                    template
                }
            }
            if (changed) {
                _uiState.update { it.copy(templates = updated) }
            }
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
