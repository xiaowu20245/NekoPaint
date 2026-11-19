package io.github.xiaowu20245.nekopaint.feature.workflow

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.common.toNekoMessage
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.operation.OperationCenter
import io.github.xiaowu20245.nekopaint.data.operation.OperationStatus
import io.github.xiaowu20245.nekopaint.data.operation.SubmissionOperationState
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowImportResult
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowsUiState(
    val loading: Boolean = true,
    val templates: List<WorkflowTemplate> = emptyList(),
    val modeFilter: WorkflowMode? = null,
    val importing: Boolean = false,
    val refreshingCompatibility: Boolean = false,
    val busyTemplateId: String? = null,
    val runningTemplateId: String? = null,
    val actionNotice: WorkflowActionNotice? = null,
    @param:StringRes val errorRes: Int? = null,
    val errorDetail: NekoMessage? = null,
    val importedTemplateId: String? = null,
) {
    val filteredTemplates: List<WorkflowTemplate>
        get() = if (modeFilter == null) {
            templates
        } else {
            templates.filter { it.compatibility?.mode == modeFilter }
        }
}

internal fun SubmissionOperationState.operationErrorRes(): Int? =
    if (status == OperationStatus.FAILED &&
        message?.key == NekoMessageKey.WORKFLOW_NO_SERVER
    ) {
        R.string.workflow_submit_no_server
    } else {
        null
    }

internal fun SubmissionOperationState.operationErrorDetail(): NekoMessage? =
    localRecordWarning ?: message?.takeIf {
        failed > 0 ||
            unknown > 0 ||
            skipped > 0 ||
            status == OperationStatus.UNKNOWN
    }

sealed interface WorkflowActionNotice {
    data class Renamed(val name: String) : WorkflowActionNotice
    data class Copied(val name: String) : WorkflowActionNotice
    data object Deleted : WorkflowActionNotice
    data class QuickSubmitted(
        val name: String,
        val count: Int,
        val failed: Int = 0,
        val unknown: Int = 0,
        val skipped: Int = 0,
    ) : WorkflowActionNotice
}

class WorkflowsViewModel(
    private val repository: WorkflowRepository,
    private val operationCenter: OperationCenter,
    private val activeServerId: Flow<String?>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowsUiState())
    val uiState: StateFlow<WorkflowsUiState> = _uiState.asStateFlow()

    private var compatibilityRefreshPending = false
    private var refreshJob: Job? = null
    private val viewModelStartedAt = System.currentTimeMillis()
    private val handledOperations = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            operationCenter.states
                .map { states -> states.values.toList() }
                .distinctUntilChanged()
                .collect { states ->
                    states.forEach(::handleOperationState)
                }
        }
        viewModelScope.launch {
            val templates = runCatching { repository.listTemplates() }.getOrDefault(emptyList())
            _uiState.update { it.copy(loading = false, templates = templates) }
            // 让列表先完成首帧绘制，再开始兼容性检查，避免和页面切换动画抢帧。
            delay(INITIAL_SETTLE_DELAY_MS)
            refreshCompatibilitySnapshot()
        }
        viewModelScope.launch {
            // 首次值由上面的初始化刷新处理；之后每次切换服务器都重新检查节点。
            activeServerId.distinctUntilChanged().drop(1).collect {
                refreshCompatibilitySnapshot()
            }
        }
    }

    fun refreshList() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // ON_RESUME 会频繁触发；延迟到页面切换稳定后再读盘，避免和转场抢帧。
            delay(INITIAL_SETTLE_DELAY_MS)
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
            if (baseTemplate.repairRequired) {
                _uiState.update {
                    it.copy(
                        runningTemplateId = null,
                        errorRes = R.string.workflow_repair_required,
                    )
                }
                return@launch
            }

            val started = operationCenter.start(
                template = baseTemplate,
                totalRuns = count,
                seedStrategy = SeedStrategy.RANDOM,
            )
            started.getOrElse { error ->
                _uiState.update {
                    val typed = error.toNekoMessage(NekoMessageKey.GENERIC_ERROR)
                    it.copy(
                        runningTemplateId = null,
                        errorDetail = if (typed.key == NekoMessageKey.GENERIC_ERROR) {
                            NekoMessage(
                                NekoMessageKey.WORKFLOW_QUICK_RUN_START_FAILED,
                                listOf(error.message ?: error.javaClass.simpleName),
                            )
                        } else {
                            typed
                        },
                    )
                }
                return@launch
            }
        }
    }

    private fun handleOperationState(operation: SubmissionOperationState) {
        if (operation.status == OperationStatus.RUNNING) {
            _uiState.update { state ->
                if (state.runningTemplateId == null) {
                    state.copy(runningTemplateId = operation.templateId)
                } else {
                    state
                }
            }
            return
        }
        if (operation.updatedAt < viewModelStartedAt - RECENT_OPERATION_WINDOW_MS) return
        if (!handledOperations.add(operation.operationId)) return
        val name = _uiState.value.templates
            .firstOrNull { it.id == operation.templateId }
            ?.name
            .orEmpty()
        _uiState.update {
            it.copy(
                runningTemplateId = null,
                actionNotice = if (operation.success > 0) {
                    WorkflowActionNotice.QuickSubmitted(
                        name = name,
                        count = operation.success,
                        failed = operation.failed,
                        unknown = operation.unknown,
                        skipped = operation.skipped,
                    )
                } else {
                    it.actionNotice
                },
                errorRes = operation.operationErrorRes(),
                errorDetail = operation.operationErrorDetail(),
            )
        }
        if (operation.success > 0) {
            viewModelScope.launch { refreshTemplates() }
        }
    }

    private suspend fun refreshCompatibilitySnapshot() {
        if (_uiState.value.refreshingCompatibility) {
            compatibilityRefreshPending = true
            return
        }
        _uiState.update { it.copy(refreshingCompatibility = true) }
        try {
            do {
                compatibilityRefreshPending = false
                val updated = repository.refreshAllCompatibility()
                if (updated != null) {
                    _uiState.update { it.copy(templates = updated) }
                }
            } while (compatibilityRefreshPending)
        } finally {
            _uiState.update { it.copy(refreshingCompatibility = false) }
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
                            errorDetail = NekoMessage(
                                NekoMessageKey.WORKFLOW_RENAME_FAILED,
                                listOf(throwable.message ?: throwable.javaClass.simpleName),
                            ),
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
                            errorDetail = NekoMessage(
                                NekoMessageKey.WORKFLOW_COPY_FAILED,
                                listOf(throwable.message ?: throwable.javaClass.simpleName),
                            ),
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
                            errorDetail = NekoMessage(
                                NekoMessageKey.WORKFLOW_DELETE_FAILED,
                                listOf(throwable.message ?: throwable.javaClass.simpleName),
                            ),
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
        private const val INITIAL_SETTLE_DELAY_MS = 250L
        private const val RECENT_OPERATION_WINDOW_MS = 5 * 60 * 1000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkflowsViewModel(
                    repository = container.workflowRepository,
                    operationCenter = container.operationCenter,
                    activeServerId = container.serverConfigStore.activeServerId,
                )
            }
        }
    }
}
