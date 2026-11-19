package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.common.toNekoMessage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface QueueNotice {
    data class Text(val value: String) : QueueNotice
    data class Message(val value: NekoMessage) : QueueNotice
    data class Submitted(
        val promptId: String,
        val warning: NekoMessage? = null,
    ) : QueueNotice
    data class Unknown(
        val promptId: String,
        val message: NekoMessage,
    ) : QueueNotice
}

internal data class QueuePendingRetryState(
    val retryingPromptId: String? = null,
    val notice: QueueNotice? = null,
)

/**
 * 检查接线：同时处理 suspend 调用抛异常和内层 Result.failure，
 * 保证 QueueViewModel 不会把失败误当成“未找到”。
 */
internal suspend fun recheckNotice(recheck: suspend () -> Result<Boolean>): QueueNotice {
    val result = runCatching { recheck() }
        .getOrElse { throwable -> Result.failure(throwable) }
    return describeRecheckResult(result)
}

/** 检查结果必须区分“确认未找到”和“检查失败”，失败时不得提示可重试。 */
internal fun describeRecheckResult(result: Result<Boolean>): QueueNotice = result.fold(
    onSuccess = { found ->
        QueueNotice.Message(
            NekoMessage(
                if (found) NekoMessageKey.RECHECK_FOUND else NekoMessageKey.RECHECK_NOT_FOUND,
            ),
        )
    },
    onFailure = { throwable ->
        QueueNotice.Message(throwable.toNekoMessage(NekoMessageKey.GENERIC_ERROR))
    },
)

/**
 * Queue 页“复用原 ID 重试”的独立协程控制器。
 *
 * 使用 operation scope 而不是 viewModelScope：用户离开页面后重试仍应完成；
 * try/finally 保证异常或取消时不会永久卡在“重试中”。
 */
internal class QueuePendingRetryController(
    private val scope: CoroutineScope,
    private val retrySubmission: suspend (TaskRecord) -> WorkflowSubmitResult,
    private val refresh: () -> Unit,
) {

    private val _state = MutableStateFlow(QueuePendingRetryState())
    val state: StateFlow<QueuePendingRetryState> = _state.asStateFlow()

    fun retry(task: TaskRecord) {
        if (_state.value.retryingPromptId != null) return
        _state.update {
            it.copy(retryingPromptId = task.promptId, notice = null)
        }
        scope.launch {
            var notice: QueueNotice? = null
            try {
                notice = queueRetryNotice(retrySubmission(task))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                notice = QueueNotice.Message(
                    throwable.toNekoMessage(NekoMessageKey.GENERIC_ERROR),
                )
            } finally {
                _state.update {
                    it.copy(
                        retryingPromptId = null,
                        notice = notice ?: it.notice,
                    )
                }
            }
            runCatching { refresh() }
        }
    }

    fun showNotice(text: String) = showNotice(QueueNotice.Text(text))

    fun showNotice(notice: QueueNotice) {
        _state.update { it.copy(notice = notice) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

}

internal fun queueRetryNotice(result: WorkflowSubmitResult): QueueNotice = when (result) {
    is WorkflowSubmitResult.Success -> QueueNotice.Submitted(
        promptId = result.promptId,
        warning = result.localRecordWarning,
    )

    is WorkflowSubmitResult.Unknown -> QueueNotice.Unknown(
        promptId = result.promptId,
        message = result.message,
    )

    WorkflowSubmitResult.NoServer -> QueueNotice.Message(
        NekoMessage(NekoMessageKey.WORKFLOW_NO_SERVER),
    )

    is WorkflowSubmitResult.Validation -> QueueNotice.Message(result.summary)
    is WorkflowSubmitResult.Error -> QueueNotice.Message(result.message)
}
