package io.github.xiaowu20245.nekopaint.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class QueueUiState(
    val queue: QueueSnapshot? = null,
    val progress: Map<String, TaskProgress> = emptyMap(),
    val tasks: List<TaskRecord> = emptyList(),
    val connection: ComfyConnectionState = ComfyConnectionState.Disconnected,
    val queueRemaining: Int = 0,
    val localClientId: String? = null,
    val error: String? = null,
    val actionError: String? = null,
    val refreshing: Boolean = false,
)

class QueueViewModel(
    private val taskManager: TaskManager,
) : ViewModel() {

    private val base = combine(
        taskManager.queue,
        taskManager.progress,
        taskManager.tasks,
        taskManager.connection,
        taskManager.queueRemaining,
    ) { queue, progress, tasks, connection, remaining ->
        QueueUiState(
            queue = queue,
            progress = progress,
            tasks = tasks,
            connection = connection,
            queueRemaining = remaining,
        )
    }

    val uiState: StateFlow<QueueUiState> = combine(
        base,
        taskManager.localClientId,
        taskManager.queueError,
        taskManager.refreshing,
        taskManager.actionError,
    ) { state, localClientId, error, refreshing, actionError ->
        state.copy(
            localClientId = localClientId,
            error = error,
            refreshing = refreshing,
            actionError = actionError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QueueUiState(),
    )

    init {
        taskManager.refresh()
    }

    fun refresh() = taskManager.refresh()

    fun interrupt(promptId: String) = taskManager.interrupt(promptId)

    fun deletePending(promptId: String) = taskManager.deletePending(promptId)

    fun clearActionError() = taskManager.clearActionError()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { QueueViewModel(container.taskManager) }
        }
    }
}
