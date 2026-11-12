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

    private data class DemoQueueState(
        val queue: QueueSnapshot? = null,
        val tasks: List<TaskRecord> = emptyList(),
        val progress: Map<String, TaskProgress> = emptyMap(),
    )

    private val demoQueueState = kotlinx.coroutines.flow.MutableStateFlow(DemoQueueState())

    private val taskData = combine(
        taskManager.queue,
        taskManager.progress,
        taskManager.tasks,
        demoQueueState,
    ) { queue, progress, tasks, demo ->
        val effectiveQueue = demo.queue ?: queue
        val effectiveTasks = if (demo.tasks.isNotEmpty()) demo.tasks + tasks else tasks
        val effectiveProgress = if (demo.progress.isNotEmpty()) progress + demo.progress else progress
        Triple(effectiveQueue, effectiveTasks, effectiveProgress)
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

    fun loadDemoQueue() {
        val runningPromptId = "demo-run-001"
        val pendingPromptId1 = "demo-pend-002"
        val pendingPromptId2 = "demo-pend-003"
        val localClientId = taskManager.localClientId.value ?: "local-client"

        val qSnapshot = io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot(
            running = listOf(
                io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem(
                    number = 1,
                    promptId = runningPromptId,
                    clientId = localClientId,
                    prompt = null,
                    nodeTitles = mapOf("3" to "KSampler"),
                ),
            ),
            pending = listOf(
                io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem(
                    number = 2,
                    promptId = pendingPromptId1,
                    clientId = localClientId,
                    prompt = null,
                    nodeTitles = emptyMap(),
                ),
                io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem(
                    number = 3,
                    promptId = pendingPromptId2,
                    clientId = "other-device-x",
                    prompt = null,
                    nodeTitles = emptyMap(),
                ),
            ),
            updatedAt = System.currentTimeMillis(),
        )

        val tList = listOf(
            TaskRecord(
                promptId = runningPromptId,
                templateId = "flux-dev",
                templateName = "Flux 快速生图",
                clientId = localClientId,
                queueNumber = 1,
                submittedAt = System.currentTimeMillis() - 8000L,
                startedAt = System.currentTimeMillis() - 5000L,
                status = "running",
                values = mapOf(
                    "6:text" to "masterpiece, 1girl, cat ears, neon cyberpunk city, cinematic lighting",
                    "3:seed" to "42819024",
                    "3:steps" to "20",
                    "3:cfg" to "7.0",
                ),
            ),
            TaskRecord(
                promptId = pendingPromptId1,
                templateId = "anime-sdxl",
                templateName = "动漫插画高精度重绘",
                clientId = localClientId,
                queueNumber = 2,
                submittedAt = System.currentTimeMillis() - 3000L,
                status = "queued",
                values = mapOf(
                    "6:text" to "scenic mountain lake, cherry blossoms, floating lanterns, 8k",
                    "3:steps" to "28",
                    "5:width" to "1024",
                    "5:height" to "1024",
                ),
            ),
            TaskRecord(
                promptId = pendingPromptId2,
                templateId = "general-sd",
                templateName = "角色设计概念草图",
                clientId = "other-device-x",
                queueNumber = 3,
                submittedAt = System.currentTimeMillis() - 1000L,
                status = "queued",
                values = mapOf(
                    "6:text" to "fantasy warrior, silver armor, glowing runes",
                ),
            ),
        )

        val pMap = mapOf(
            runningPromptId to TaskProgress(
                promptId = runningPromptId,
                status = "running",
                currentNode = "3",
                currentNodeLabel = "KSampler",
                value = 14.0,
                max = 20.0,
                completedNodes = 5,
                totalNodes = 8,
                overallPercent = 0.65f,
            ),
        )

        demoQueueState.value = DemoQueueState(
            queue = qSnapshot,
            tasks = tList,
            progress = pMap,
        )
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
