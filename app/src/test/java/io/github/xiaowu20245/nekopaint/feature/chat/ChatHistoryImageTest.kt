package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryImageTest {

    @Test
    fun `selects latest history entry with images`() {
        val history = listOf(
            entry("no-image", images = emptyList()),
            entry(
                "task-1",
                images = listOf(
                    image("a.png", "output"),
                    image("b.png", "temp"),
                ),
            ),
        )

        val result = selectHistoryImages(
            history = history,
            tasks = listOf(task("task-1")),
            promptId = null,
            maxImages = 4,
        )

        assertEquals("task-1", result?.promptId)
        assertEquals("任务 A", result?.title)
        assertEquals(2, result?.images?.size)
        assertTrue(result?.images?.first()?.isOutput == true)
    }

    @Test
    fun `selects requested prompt id`() {
        val history = listOf(
            entry("task-2", images = listOf(image("b.png", "output"))),
            entry("task-1", images = listOf(image("a.png", "output"))),
        )

        val result = selectHistoryImages(
            history = history,
            tasks = emptyList(),
            promptId = "task-1",
            maxImages = 4,
        )

        assertEquals("task-1", result?.promptId)
        assertEquals("a.png", result?.images?.single()?.filename)
    }

    @Test
    fun `caps image count and skips blank urls`() {
        val history = listOf(
            entry(
                "task-1",
                images = listOf(
                    image("a.png", "output"),
                    HistoryImage("b.png", "", "output", url = ""),
                    image("c.png", "output"),
                    image("d.png", "output"),
                ),
            ),
        )

        val result = selectHistoryImages(
            history = history,
            tasks = emptyList(),
            promptId = null,
            maxImages = 2,
        )

        assertEquals(listOf("a.png", "c.png"), result?.images?.map { it.filename })
    }

    @Test
    fun `returns null when no usable images`() {
        assertNull(
            selectHistoryImages(
                history = listOf(entry("task-1", images = emptyList())),
                tasks = emptyList(),
                promptId = null,
                maxImages = 4,
            ),
        )
    }

    private fun entry(promptId: String, images: List<HistoryImage>): HistoryEntry =
        HistoryEntry(
            promptId = promptId,
            status = "success",
            completed = true,
            timestamp = 0L,
            startedAt = 0L,
            finishedAt = 0L,
            images = images,
            errorNodeId = null,
            errorMessage = null,
        )

    private fun image(filename: String, type: String): HistoryImage =
        HistoryImage(filename = filename, subfolder = "", type = type, url = "http://host/$filename")

    private fun task(promptId: String): TaskRecord = TaskRecord(
        promptId = promptId,
        templateId = "template-1",
        templateName = "任务 A",
        clientId = "client-1",
        submittedAt = 0L,
    )
}
