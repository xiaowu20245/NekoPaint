package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
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
                        message = operation.message
                            ?: NekoMessage(NekoMessageKey.OPERATION_PARTIAL_FAILED),
                        extraMessages = operation.extraMessages(),
                        nodeErrors = validation?.nodeErrors.orEmpty(),
                        batchStats = BatchSubmitStats(
                            success = operation.success,
                            failed = operation.failed,
                            unknown = operation.unknown,
                            skipped = operation.skipped,
                        ),
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
                    message = operation.message
                        ?: NekoMessage(NekoMessageKey.WORKFLOW_UNKNOWN_RESULT),
                    extraMessages = operation.extraMessages() + listOf(
                        NekoMessage(NekoMessageKey.WORKFLOW_UNKNOWN_CONFIRM),
                    ),
                    batchStats = BatchSubmitStats(
                        success = operation.success,
                        failed = operation.failed,
                        unknown = operation.unknown,
                        skipped = operation.skipped,
                        unknownOutcome = true,
                    ),
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
                        message = operation.message
                            ?: NekoMessage(NekoMessageKey.WORKFLOW_FAILED),
                        nodeErrors = validation?.nodeErrors.orEmpty(),
                    ),
                ),
                refreshCompatibility = validation != null,
            )
        }
    }

    private fun SubmissionOperationState.warningMessage(): NekoMessage? =
        localRecordWarning ?: lastSuccess?.localRecordWarning

    private fun SubmissionOperationState.extraMessages(): List<NekoMessage> = buildList {
        if (executionValuesPersisted == false) {
            add(NekoMessage(NekoMessageKey.WORKFLOW_EXECUTION_VALUES_FAILED))
        }
        warningMessage()?.let(::add)
    }

    private fun SubmissionOperationState.successSubmitError(): SubmitErrorUi? {
        val messages = extraMessages()
        return if (messages.isEmpty()) {
            null
        } else {
            SubmitErrorUi(extraMessages = messages)
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
