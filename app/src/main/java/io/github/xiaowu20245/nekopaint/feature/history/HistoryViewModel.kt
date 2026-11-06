package io.github.xiaowu20245.nekopaint.feature.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.data.repository.ImageSaver
import io.github.xiaowu20245.nekopaint.data.repository.ImageShareManager
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

sealed interface SaveNotice {
    data class Success(val uri: Uri, val displayName: String) : SaveNotice
    data class Failure(val message: String) : SaveNotice
}

sealed interface ShareNotice {
    data class Success(val uri: Uri, val mimeType: String, val displayName: String) : ShareNotice
    data class Failure(val message: String) : ShareNotice
}

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val tasks: List<TaskRecord> = emptyList(),
    val connection: ComfyConnectionState = ComfyConnectionState.Disconnected,
    val error: String? = null,
    val refreshing: Boolean = false,
    val resubmitting: Boolean = false,
    val notice: HistoryNotice? = null,
    val savingImageUrl: String? = null,
    val saveNotice: SaveNotice? = null,
    val sharingImageUrl: String? = null,
    val shareNotice: ShareNotice? = null,
    val showTempPreviews: Boolean = false,
)

class HistoryViewModel(
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
    private val imageSaver: ImageSaver,
    private val imageShareManager: ImageShareManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val base = combine(
        taskManager.history,
        taskManager.tasks,
        taskManager.connection,
    ) { entries, tasks, connection ->
        HistoryUiState(entries = entries, tasks = tasks, connection = connection)
    }

    private val resubmitState = MutableStateFlow(ResubmitState())
    private val saveState = MutableStateFlow(SaveState())
    private val shareState = MutableStateFlow(ShareState())

    private val operationState = combine(
        resubmitState,
        saveState,
        shareState,
    ) { resubmit, save, share ->
        OperationState(resubmit = resubmit, save = save, share = share)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        base,
        taskManager.historyError,
        taskManager.refreshing,
        operationState,
        settingsStore.showTempPreviews,
    ) { state, error, refreshing, operation, showTempPreviews ->
        state.copy(
            error = error,
            refreshing = refreshing,
            resubmitting = operation.resubmit.submitting,
            notice = operation.resubmit.notice,
            savingImageUrl = operation.save.savingUrl,
            saveNotice = operation.save.notice,
            sharingImageUrl = operation.share.sharingUrl,
            shareNotice = operation.share.notice,
            showTempPreviews = showTempPreviews,
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

    fun saveImage(image: HistoryImage) {
        if (saveState.value.savingUrl != null) return
        val url = image.url
        if (url.isNullOrBlank()) {
            saveState.value = SaveState(notice = SaveNotice.Failure("图片地址无效"))
            return
        }
        saveState.value = SaveState(savingUrl = url)
        viewModelScope.launch {
            val result = imageSaver.save(url, image.filename)
            saveState.value = SaveState(
                notice = result.fold(
                    onSuccess = { SaveNotice.Success(it.uri, it.displayName) },
                    onFailure = { SaveNotice.Failure(it.message ?: "保存失败") },
                ),
            )
        }
    }

    fun consumeSaveNotice() {
        saveState.value = saveState.value.copy(notice = null)
    }

    fun shareImage(image: HistoryImage) {
        if (shareState.value.sharingUrl != null) return
        val url = image.url
        if (url.isNullOrBlank()) {
            shareState.value = ShareState(notice = ShareNotice.Failure("图片地址无效"))
            return
        }
        shareState.value = ShareState(sharingUrl = url)
        viewModelScope.launch {
            val result = imageShareManager.share(url, image.filename)
            shareState.value = ShareState(
                notice = result.fold(
                    onSuccess = {
                        ShareNotice.Success(it.uri, it.mimeType, it.displayName)
                    },
                    onFailure = { ShareNotice.Failure(it.message ?: "分享准备失败") },
                ),
            )
        }
    }

    fun consumeShareNotice() {
        shareState.value = shareState.value.copy(notice = null)
    }

    fun setShowTempPreviews(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setShowTempPreviews(value)
        }
    }

    private data class ResubmitState(
        val submitting: Boolean = false,
        val notice: HistoryNotice? = null,
    )

    private data class SaveState(
        val savingUrl: String? = null,
        val notice: SaveNotice? = null,
    )

    private data class ShareState(
        val sharingUrl: String? = null,
        val notice: ShareNotice? = null,
    )

    private data class OperationState(
        val resubmit: ResubmitState,
        val save: SaveState,
        val share: ShareState,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    taskManager = container.taskManager,
                    workflowRepository = container.workflowRepository,
                    imageSaver = container.imageSaver,
                    imageShareManager = container.imageShareManager,
                    settingsStore = container.settingsStore,
                )
            }
        }
    }
}
