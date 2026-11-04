package io.github.xiaowu20245.nekopaint.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryNotice {
    data class Success(val promptId: String) : HistoryNotice
    data object NoServer : HistoryNotice
    data class Error(val message: String) : HistoryNotice
}

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val tasks: List<TaskRecord> = emptyList(),
    val connection: ComfyConnectionState = ComfyConnectionState.Disconnected,
    val error: String? = null,
    val refreshing: Boolean = false,
    val resubmitting: Boolean = false,
    val notice: HistoryNotice? = null,
)

class HistoryViewModel(
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
) : ViewModel() {

    private val base = combine(
        taskManager.history,
        taskManager.tasks,
        taskManager.connection,
    ) { entries, tasks, connection ->
        HistoryUiState(entries = entries, tasks = tasks, connection = connection)
    }

    private val resubmitState = MutableStateFlow(ResubmitState())

    val uiState: StateFlow<HistoryUiState> = combine(
        base,
        taskManager.historyError,
        taskManager.refreshing,
        resubmitState,
    ) { state, error, refreshing, resubmit ->
        state.copy(
            error = error,
            refreshing = refreshing,
            resubmitting = resubmit.submitting,
            notice = resubmit.notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    init {
        taskManager.refresh()
    }

    fun refresh() = taskManager.refresh()

    fun resubmit(task: TaskRecord) {
        if (resubmitState.value.submitting) return
        resubmitState.value = ResubmitState(submitting = true)
        viewModelScope.launch {
            val result = workflowRepository.resubmit(task)
            val notice = when (result) {
                is WorkflowSubmitResult.Success -> HistoryNotice.Success(result.promptId)
                is WorkflowSubmitResult.NoServer -> HistoryNotice.NoServer
                is WorkflowSubmitResult.Validation -> HistoryNotice.Error(result.summary)
                is WorkflowSubmitResult.Error -> HistoryNotice.Error(result.message)
            }
            resubmitState.value = ResubmitState(submitting = false, notice = notice)
            if (result is WorkflowSubmitResult.Success) {
                taskManager.refresh()
            }
        }
    }

    fun consumeNotice() {
        resubmitState.value = resubmitState.value.copy(notice = null)
    }

    private data class ResubmitState(
        val submitting: Boolean = false,
        val notice: HistoryNotice? = null,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    taskManager = container.taskManager,
                    workflowRepository = container.workflowRepository,
                )
            }
        }
    }
}
