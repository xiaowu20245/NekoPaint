package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMergeTest {

    @Test
    fun `keeps local archive when server history is empty`() {
        val local = listOf(
            entry("old-1", timestamp = 100L),
            entry("old-2", timestamp = 200L),
        )

        val merged = mergeHistory(local, emptyList())

        assertEquals(listOf("old-2", "old-1"), merged.map { it.promptId })
    }

    @Test
    fun `adds new server entries and updates matching prompt ids`() {
        val local = listOf(
            entry("shared", timestamp = 100L, status = "running"),
            entry("old", timestamp = 50L),
        )
        val remote = listOf(
            entry("shared", timestamp = 100L, status = "success"),
            entry("new", timestamp = 150L),
        )

        val merged = mergeHistory(local, remote)

        assertEquals(listOf("new", "shared", "old"), merged.map { it.promptId })
        assertEquals("success", merged.first { it.promptId == "shared" }.status)
    }

    @Test
    fun `sorts by timestamp descending`() {
        val local = listOf(entry("a", timestamp = 100L))
        val remote = listOf(
            entry("b", timestamp = 300L),
            entry("c", timestamp = 200L),
        )

        val merged = mergeHistory(local, remote)

        assertEquals(listOf("b", "c", "a"), merged.map { it.promptId })
    }

    @Test
    fun `caps archive size`() {
        val local = (1..5).map { index ->
            entry("local-$index", timestamp = index.toLong())
        }

        val merged = mergeHistory(local, emptyList(), maxEntries = 3)

        assertEquals(listOf("local-5", "local-4", "local-3"), merged.map { it.promptId })
    }

    private fun entry(
        promptId: String,
        timestamp: Long?,
        status: String = "success",
    ): HistoryEntry = HistoryEntry(
        promptId = promptId,
        status = status,
        completed = status != "running",
        timestamp = timestamp,
        startedAt = null,
        finishedAt = timestamp,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )
}
