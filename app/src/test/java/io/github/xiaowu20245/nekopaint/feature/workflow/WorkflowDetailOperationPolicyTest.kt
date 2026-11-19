package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.data.operation.OperationStatus
import io.github.xiaowu20245.nekopaint.data.operation.SubmissionOperationState
import io.github.xiaowu20245.nekopaint.data.repository.NodeErrorInfo
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowDetailOperationPolicyTest {

    private val template = WorkflowTemplate(
        id = "t1",
        name = "template",
        sourceFileName = "api.json",
        sourceHash = "hash",
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
        candidates = listOf(
            ParameterCandidate(
                id = "1:seed",
                nodeId = "1",
                inputName = "seed",
                label = "Seed",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                defaultValue = "1",
                value = "42",
                selected = true,
            ),
            ParameterCandidate(
                id = "1:steps",
                nodeId = "1",
                inputName = "steps",
                label = "Steps",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                defaultValue = "20",
                value = "20",
                selected = true,
            ),
        ),
    )

    @Test
    fun fastSuccessReplacesSubmittingInsteadOfBeingOverwritten() {
        val running = state(status = OperationStatus.RUNNING)
        val success = state(
            status = OperationStatus.SUCCESS,
            success = 1,
            processed = 1,
            lastSuccess = WorkflowSubmitResult.Success("p1", 1),
        )

        val runningUi = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            running,
        )
        assertTrue(runningUi.state.submitting)

        val successUi = WorkflowDetailOperationPolicy.apply(runningUi.state, success)

        assertFalse(successUi.state.submitting)
        assertEquals("p1", successUi.state.submission?.promptId)
        assertTrue(successUi.state.saved)
    }

    @Test
    fun savedExecutionValuesDriveDirtyAndSaved() {
        val operation = state(
            status = OperationStatus.SUCCESS,
            success = 1,
            processed = 1,
            lastSuccess = WorkflowSubmitResult.Success("p1", 1),
            executionValuesPersisted = false,
        )

        val result = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation,
        )

        assertTrue(result.state.dirty)
        assertFalse(result.state.saved)
        assertNotNull(result.state.submitError)
    }

    @Test
    fun partialMapsValidationToFieldErrorsAndRequestsRefresh() {
        val operation = state(
            status = OperationStatus.PARTIAL,
            success = 1,
            failed = 1,
            processed = 2,
            total = 3,
            skipped = 1,
            lastValidation = WorkflowSubmitResult.Validation(
                summary = NekoMessage(
                    NekoMessageKey.WORKFLOW_VALIDATION_FAILED,
                    listOf("bad seed"),
                ),
                nodeErrors = listOf(NodeErrorInfo("1", "seed invalid")),
            ),
        )

        val result = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation,
        )

        assertTrue(result.refreshCompatibility)
        assertEquals(
            FieldError.Server("seed invalid"),
            result.state.fieldErrors["1:seed"],
        )
        val stats = result.state.submitError?.batchStats
        assertEquals(1, stats?.success)
        assertEquals(1, stats?.failed)
        assertEquals(1, stats?.skipped)
        assertEquals(
            NekoMessageKey.OPERATION_PARTIAL_FAILED,
            result.state.submitError?.message?.key,
        )
    }

    @Test
    fun inputLevelValidationMarksOnlyMatchingInput() {
        val operation = state(
            status = OperationStatus.PARTIAL,
            success = 0,
            failed = 1,
            processed = 1,
            lastValidation = WorkflowSubmitResult.Validation(
                summary = NekoMessage(
                    NekoMessageKey.WORKFLOW_VALIDATION_FAILED,
                    listOf("bad seed"),
                ),
                nodeErrors = listOf(
                    NodeErrorInfo("1", "seed invalid", inputName = "seed"),
                ),
            ),
        )

        val result = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation,
        )

        assertEquals(FieldError.Server("seed invalid"), result.state.fieldErrors["1:seed"])
        assertNull(result.state.fieldErrors["1:steps"])
    }

    @Test
    fun nodeLevelValidationFallsBackToAllInputs() {
        val operation = state(
            status = OperationStatus.PARTIAL,
            success = 0,
            failed = 1,
            processed = 1,
            lastValidation = WorkflowSubmitResult.Validation(
                summary = NekoMessage(NekoMessageKey.WORKFLOW_VALIDATION_FAILED, listOf("node invalid")),
                nodeErrors = listOf(
                    NodeErrorInfo("1", "node invalid", inputName = null),
                ),
            ),
        )

        val result = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation,
        )

        assertEquals(FieldError.Server("node invalid"), result.state.fieldErrors["1:seed"])
        assertEquals(FieldError.Server("node invalid"), result.state.fieldErrors["1:steps"])
    }

    @Test
    fun unknownShowsResultUnknownAndAvoidsDuplicateSubmissionPrompt() {
        val operation = state(
            status = OperationStatus.UNKNOWN,
            unknown = 1,
            processed = 1,
            total = 1,
        )

        val result = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template, submitting = true),
            operation,
        )

        assertFalse(result.state.submitting)
        assertEquals(
            NekoMessageKey.WORKFLOW_UNKNOWN_RESULT,
            result.state.submitError?.message?.key,
        )
        assertTrue(
            result.state.submitError?.extraMessages
                ?.any { it.key == NekoMessageKey.WORKFLOW_UNKNOWN_CONFIRM } == true,
        )
        assertEquals(1, result.state.submitError?.batchStats?.unknown)
    }

    @Test
    fun successCombinesLocalRecordWarningAndExecutionValueFailure() {
        val operation = state(
            status = OperationStatus.SUCCESS,
            success = 1,
            processed = 1,
            lastSuccess = WorkflowSubmitResult.Success(
                promptId = "p1",
                queueNumber = 1,
                localRecordWarning = NekoMessage(
                    NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED,
                ),
            ),
            executionValuesPersisted = false,
        )

        val result = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation,
        )

        val messages = result.state.submitError?.extraMessages.orEmpty()
        assertTrue(messages.any { it.key == NekoMessageKey.WORKFLOW_EXECUTION_VALUES_FAILED })
        assertTrue(messages.any { it.key == NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED })
        assertTrue(result.state.dirty)
        assertFalse(result.state.saved)
    }

    private fun state(
        status: OperationStatus,
        total: Int = 1,
        processed: Int = 0,
        success: Int = 0,
        failed: Int = 0,
        skipped: Int = 0,
        unknown: Int = 0,
        lastSuccess: WorkflowSubmitResult.Success? = null,
        lastValidation: WorkflowSubmitResult.Validation? = null,
        executionValuesPersisted: Boolean? = null,
    ) = SubmissionOperationState(
        operationId = "op",
        templateId = template.id,
        status = status,
        total = total,
        processed = processed,
        success = success,
        failed = failed,
        skipped = skipped,
        unknown = unknown,
        lastSuccess = lastSuccess,
        lastValidation = lastValidation,
        lastTemplate = template,
        executionValuesPersisted = executionValuesPersisted,
        updatedAt = 1L,
    )
}
