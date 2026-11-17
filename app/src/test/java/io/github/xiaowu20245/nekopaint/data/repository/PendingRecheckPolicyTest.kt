package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRecheckPolicyTest {

    @Test
    fun queueHitReturnsTrueWithoutLoadingHistory() = runTest {
        var historyLoaded = false

        val result = PendingRecheckPolicy.recheck(
            promptId = "p1",
            loadQueue = { Result.success(queue(pending = listOf("p1"))) },
            loadHistory = {
                historyLoaded = true
                Result.success(null)
            },
            applyQueue = {},
            applyHistory = { error("不应命中历史") },
            markNotFound = { error("不应标记未找到") },
        )

        assertEquals(true, result.getOrThrow())
        assertFalse(historyLoaded)
    }

    @Test
    fun historyHitReturnsTrueAndAppliesEntry() = runTest {
        val entry = entry("p1")

        val result = PendingRecheckPolicy.recheck(
            promptId = "p1",
            loadQueue = { Result.success(queue()) },
            loadHistory = { Result.success(entry) },
            applyQueue = {},
            applyHistory = { applied -> assertEquals(entry, applied) },
            markNotFound = { error("不应标记未找到") },
        )

        assertEquals(true, result.getOrThrow())
    }

    @Test
    fun bothMissesReturnFalseAndMarkNotFound() = runTest {
        var marked = false

        val result = PendingRecheckPolicy.recheck(
            promptId = "p1",
            loadQueue = { Result.success(queue()) },
            loadHistory = { Result.success(null) },
            applyQueue = {},
            applyHistory = { error("不应命中历史") },
            markNotFound = { marked = true },
        )

        assertEquals(false, result.getOrThrow())
        assertTrue(marked)
    }

    @Test
    fun queueFailureIsNotReportedAsNotFound() = runTest {
        var marked = false

        val result = PendingRecheckPolicy.recheck(
            promptId = "p1",
            loadQueue = { Result.failure(IllegalStateException("queue down")) },
            loadHistory = { error("不应查询历史") },
            applyQueue = {},
            applyHistory = {},
            markNotFound = { marked = true },
        )

        assertTrue(result.isFailure)
        assertEquals("queue down", result.exceptionOrNull()?.message)
        assertFalse(marked)
    }

    @Test
    fun historyFailureIsNotReportedAsNotFound() = runTest {
        var marked = false

        val result = PendingRecheckPolicy.recheck(
            promptId = "p1",
            loadQueue = { Result.success(queue()) },
            loadHistory = { Result.failure(IllegalStateException("history 500")) },
            applyQueue = {},
            applyHistory = {},
            markNotFound = { marked = true },
        )

        assertTrue(result.isFailure)
        assertEquals("history 500", result.exceptionOrNull()?.message)
        assertFalse(marked)
    }

    private fun queue(pending: List<String> = emptyList()) = QueueSnapshot(
        running = emptyList(),
        pending = pending.map { promptId ->
            QueueItem(
                number = 1,
                promptId = promptId,
                clientId = "client",
                prompt = null,
                nodeTitles = emptyMap(),
            )
        },
        updatedAt = 1L,
    )

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
