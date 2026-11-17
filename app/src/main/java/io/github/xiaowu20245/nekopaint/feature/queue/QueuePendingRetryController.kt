package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class QueuePendingRetryState(
    val retryingPromptId: String? = null,
    val notice: String? = null,
)

/**
 * 检查接线：同时处理 suspend 调用抛异常和内层 Result.failure，
 * 保证 QueueViewModel 不会把失败误当成“未找到”。
 */
internal suspend fun recheckNotice(recheck: suspend () -> Result<Boolean>): String {
    val result = runCatching { recheck() }
        .getOrElse { throwable -> Result.failure(throwable) }
    return describeRecheckResult(result)
}

/** 检查结果必须区分“确认未找到”和“检查失败”，失败时不得提示可重试。 */
internal fun describeRecheckResult(result: Result<Boolean>): String = result.fold(
    onSuccess = { found ->
        if (found) {
            "已在服务器队列/历史找到该任务"
        } else {
            "队列/历史仍未找到，可复用原 ID 重试"
        }
    },
    onFailure = { throwable ->
        "检查失败：${throwable.message ?: "未知错误"}，请勿据此重试"
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
            var notice: String? = null
            try {
                notice = describe(retrySubmission(task))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                notice = "重试失败：${throwable.message ?: "未知错误"}"
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

    fun showNotice(text: String) {
        _state.update { it.copy(notice = text) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun describe(result: WorkflowSubmitResult): String = when (result) {
        is WorkflowSubmitResult.Success -> buildString {
            append("已重新提交，任务 ID：")
            append(result.promptId.take(8))
            result.localRecordWarning?.let { warning ->
                append("；").append(warning)
            }
        }

        is WorkflowSubmitResult.Unknown -> "结果仍未知：${result.message}"
        WorkflowSubmitResult.NoServer -> "尚未连接服务器"
        is WorkflowSubmitResult.Validation -> result.summary
        is WorkflowSubmitResult.Error -> result.message
    }
}
