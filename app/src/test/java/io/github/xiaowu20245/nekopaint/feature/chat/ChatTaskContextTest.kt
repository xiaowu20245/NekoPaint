package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTaskContextTest {

    @Test
    fun `maps history and task metadata`() {
        val entry = historyEntry(promptId = "abc123456789", status = "failed")
        val task = TaskRecord(
            promptId = entry.promptId,
            templateId = "template-1",
            templateName = "Anime Detail",
            clientId = "client-1",
            submittedAt = 0L,
        )

        val context = ChatTaskContext.from(entry, task, summary = "summary")

        assertEquals("abc123456789", context.promptId)
        assertEquals("Anime Detail", context.title)
        assertEquals("失败", context.statusLabel)
        assertEquals("abc12345", context.shortId)
        assertTrue(context.isFailure)
    }

    @Test
    fun `falls back to unnamed task and treats interrupted as failure`() {
        val context = ChatTaskContext.from(
            entry = historyEntry(promptId = "short", status = "interrupted"),
            task = null,
            summary = "summary",
        )

        assertEquals("未命名任务", context.title)
        assertEquals("已中断", context.statusLabel)
        assertEquals("short", context.shortId)
        assertTrue(context.isFailure)
    }

    @Test
    fun `successful task is not a failure`() {
        val context = ChatTaskContext.from(
            entry = historyEntry(promptId = "done", status = "success"),
            task = null,
            summary = "summary",
        )

        assertEquals("成功", context.statusLabel)
        assertFalse(context.isFailure)
    }

    private fun historyEntry(promptId: String, status: String): HistoryEntry = HistoryEntry(
        promptId = promptId,
        status = status,
        completed = true,
        timestamp = 0L,
        startedAt = 0L,
        finishedAt = 0L,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )
}
