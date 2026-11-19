package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.data.operation.OperationStatus
import io.github.xiaowu20245.nekopaint.data.operation.SubmissionOperationState

internal data class WorkflowDetailOperationUi(
    val state: WorkflowDetailUiState,
    val refreshCompatibility: Boolean = false,
)

/**
 * 将 OperationCenter 的终态映射为详情页 UI 状态。
 *
 * 抽成纯函数，保证“快速终态不被 submitting=true 覆盖”等回归可在 JVM 测试中覆盖。
 */
internal object WorkflowDetailOperationPolicy {

    fun apply(
        state: WorkflowDetailUiState,
        operation: SubmissionOperationState,
    ): WorkflowDetailOperationUi = when (operation.status) {
        OperationStatus.RUNNING -> WorkflowDetailOperationUi(
            state.copy(
                submitting = true,
                submitError = null,
            ),
        )

        OperationStatus.SUCCESS -> WorkflowDetailOperationUi(
            state.copy(
                template = operation.lastTemplate ?: state.template,
                submitting = false,
                submission = operation.lastSuccess,
                submittedBatchCount = operation.success,
                dirty = operation.executionValuesPersisted == false,
                saved = operation.executionValuesPersisted != false,
                submitError = operation.successSubmitError(),
            ),
        )

        OperationStatus.PARTIAL -> {
            val validation = operation.lastValidation
            val fieldErrors = validation.toFieldErrors(state)
            WorkflowDetailOperationUi(
                state = state.copy(
                    template = operation.lastTemplate ?: state.template,
                    submitting = false,
                    submission = operation.lastSuccess,
                    submittedBatchCount = operation.success,
                    dirty = operation.executionValuesPersisted == false,
                    saved = operation.executionValuesPersisted != false,
                    fieldErrors = fieldErrors,
                    submitError = SubmitErrorUi(
                        message = buildString {
                            append(operation.message ?: "部分任务提交失败")
                            append("（成功 ${operation.success}，失败 ${operation.failed}")
                            if (operation.unknown > 0) {
                                append("，结果未知 ${operation.unknown}")
                            }
                            append("，跳过 ${operation.skipped}）")
                            if (operation.executionValuesPersisted == false) {
                                append("；本地执行参数保存失败")
                            }
                            operation.warningText()?.let { warning ->
                                append("；").append(warning)
                            }
                        },
                        nodeErrors = validation?.nodeErrors.orEmpty(),
                    ),
                ),
                refreshCompatibility = validation != null,
            )
        }

        OperationStatus.UNKNOWN -> WorkflowDetailOperationUi(
            state.copy(
                submitting = false,
                submittedBatchCount = operation.success,
                submitError = SubmitErrorUi(
                    message = buildString {
                        append(operation.message ?: "提交结果未知")
                        append("（成功 ${operation.success}")
                        if (operation.unknown > 0) {
                            append("，结果未知 ${operation.unknown}")
                        }
                        if (operation.failed > 0) {
                            append("，失败 ${operation.failed}")
                        }
                        append("，跳过 ${operation.skipped}）")
                        operation.warningText()?.let { warning ->
                            append("；").append(warning)
                        }
                        append("；服务器可能已接收任务，请先在任务列表确认，避免重复提交")
                    },
                ),
            ),
        )

        OperationStatus.FAILED,
        OperationStatus.CANCELLED,
        -> {
            val validation = operation.lastValidation
            val fieldErrors = validation.toFieldErrors(state)
            WorkflowDetailOperationUi(
                state = state.copy(
                    submitting = false,
                    submittedBatchCount = operation.success,
                    fieldErrors = fieldErrors,
                    submitError = SubmitErrorUi(
                        message = operation.message ?: "提交失败",
                        nodeErrors = validation?.nodeErrors.orEmpty(),
                    ),
                ),
                refreshCompatibility = validation != null,
            )
        }
    }

    private fun SubmissionOperationState.warningText(): String? =
        localRecordWarning ?: lastSuccess?.localRecordWarning

    private fun SubmissionOperationState.successSubmitError(): SubmitErrorUi? {
        val messages = buildList {
            if (executionValuesPersisted == false) {
                add("任务已提交，但本地执行参数保存失败")
            }
            warningText()?.let(::add)
        }
        return if (messages.isEmpty()) {
            null
        } else {
            SubmitErrorUi(message = messages.joinToString("；"))
        }
    }

    private fun io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult.Validation?
        .toFieldErrors(state: WorkflowDetailUiState): Map<String, FieldError> {
        if (this == null) return emptyMap()
        return state.template?.selectedCandidates.orEmpty().mapNotNull { candidate ->
            val nodeError = nodeErrors.firstOrNull {
                it.nodeId == candidate.nodeId && it.inputName == candidate.inputName
            } ?: nodeErrors.firstOrNull {
                it.nodeId == candidate.nodeId && it.inputName == null
            }
            nodeError?.let { candidate.id to FieldError.Server(it.message) }
        }.toMap()
    }
}
