package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowSubmitResultMapperTest {

    @Test
    fun successKeepsPromptQueueAndTypedWarning() {
        val warning = NekoMessage(NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED)
        val result = WorkflowSubmitResult.Success(
            promptId = "prompt-1",
            queueNumber = 3,
            localRecordWarning = warning,
        )

        assertEquals("prompt-1", result.promptId)
        assertEquals(3, result.queueNumber)
        assertEquals(warning, result.localRecordWarning)
    }

    @Test
    fun validationKeepsTypedSummaryAndNodeErrors() {
        val summary = NekoMessage(
            NekoMessageKey.WORKFLOW_VALIDATION_FAILED,
            listOf("bad params"),
        )
        val result = WorkflowSubmitResult.Validation(
            summary = summary,
            nodeErrors = listOf(
                NodeErrorInfo(
                    nodeId = "1",
                    message = "seed invalid",
                    inputName = "seed",
                    details = "range",
                ),
            ),
        )

        assertEquals(summary, result.summary)
        assertEquals("1", result.nodeErrors.single().nodeId)
        assertEquals("seed", result.nodeErrors.single().inputName)
    }

    @Test
    fun unknownAndErrorKeepTypedMessages() {
        val unknownMessage = NekoMessage(
            NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN,
            listOf("timeout"),
        )
        val errorMessage = NekoMessage(NekoMessageKey.WORKFLOW_TEMPLATE_NOT_FOUND)

        val unknown = WorkflowSubmitResult.Unknown("prompt-2", unknownMessage)
        val error = WorkflowSubmitResult.Error(errorMessage)

        assertEquals("prompt-2", unknown.promptId)
        assertEquals(unknownMessage, unknown.message)
        assertEquals(errorMessage, error.message)
    }
}
