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
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val retryingPromptId: String? = null,
    val localNotice: String? = null,
    val recordWriteError: String? = null,
)

class QueueViewModel(
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
    private val operationScope: CoroutineScope,
) : ViewModel() {

    private val retryController = QueuePendingRetryController(
        scope = operationScope,
        retrySubmission = workflowRepository::retryUnknown,
        refresh = taskManager::refresh,
    )

    private val taskData = combine(
        taskManager.queue,
        taskManager.progress,
        taskManager.tasks,
    ) { queue, progress, tasks ->
        Triple(queue, tasks, progress)
    }

    private val base = combine(
        taskData,
        taskManager.connection,
        taskManager.queueRemaining,
    ) { (queue, tasks, progress), connection, remaining ->
        QueueUiState(
            queue = queue,
            progress = progress,
            tasks = tasks,
            connection = connection,
            queueRemaining = queue?.pending?.size ?: remaining,
        )
    }

    private val actions = combine(
        taskManager.actionError,
        retryController.state,
        taskManager.taskRecordWriteState,
    ) { actionError, retry, writeState ->
        Triple(
            actionError,
            retry,
            queueRecordWriteError(writeState),
        )
    }

    val uiState: StateFlow<QueueUiState> = combine(
        base,
        taskManager.localClientId,
        taskManager.queueError,
        taskManager.refreshing,
        actions,
    ) { state, localClientId, error, refreshing, action ->
        state.copy(
            localClientId = localClientId,
            error = error,
            refreshing = refreshing,
            actionError = action.first,
            retryingPromptId = action.second.retryingPromptId,
            localNotice = action.second.notice,
            recordWriteError = action.third,
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

    /** 结果未知的记录复用原 promptId 重试，避免服务器已受理时生成重复任务。 */
    fun retryUnknown(task: TaskRecord) = retryController.retry(task)

    /** 定向检查队列 + `/history/{promptId}`，确认服务器是否已受理该 unknown 任务。 */
    fun recheckUnknown(task: TaskRecord) {
        operationScope.launch {
            retryController.showNotice(
                recheckNotice { taskManager.recheckPending(task.promptId) },
            )
        }
    }

    fun clearLocalNotice() = retryController.clearNotice()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                QueueViewModel(
                    taskManager = container.taskManager,
                    workflowRepository = container.workflowRepository,
                    operationScope = container.operationScope,
                )
            }
        }
    }
}
