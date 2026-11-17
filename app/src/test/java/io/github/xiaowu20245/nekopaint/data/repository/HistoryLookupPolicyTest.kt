package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLookupPolicyTest {

    @Test
    fun exactPromptIdMatchIsFound() {
        val entry = entry("p1")

        val outcome = HistoryLookupPolicy.classify(200, listOf(entry), "p1")

        assertTrue(outcome is HistoryLookupOutcome.Found)
        assertEquals("p1", (outcome as HistoryLookupOutcome.Found).entry.promptId)
    }

    @Test
    fun emptyOrMismatchedResponseIsNotFound() {
        assertEquals(
            HistoryLookupOutcome.NotFound,
            HistoryLookupPolicy.classify(200, emptyList(), "p1"),
        )
        assertEquals(
            HistoryLookupOutcome.NotFound,
            HistoryLookupPolicy.classify(200, listOf(entry("other")), "p1"),
        )
    }

    @Test
    fun notFoundHttpStatusIsNotFoundButOtherErrorsAreFailures() {
        assertEquals(
            HistoryLookupOutcome.NotFound,
            HistoryLookupPolicy.classify(404, emptyList(), "p1"),
        )
        assertTrue(
            HistoryLookupPolicy.classify(500, emptyList(), "p1") is HistoryLookupOutcome.Failed,
        )
        assertTrue(
            HistoryLookupPolicy.classify(401, emptyList(), "p1") is HistoryLookupOutcome.Failed,
        )
    }

    private fun entry(promptId: String) = HistoryEntry(
        promptId = promptId,
        status = TaskStatus.SUCCESS,
        completed = true,
        timestamp = 1L,
        startedAt = 0L,
        finishedAt = 1L,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )
}
