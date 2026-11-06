package io.github.xiaowu20245.nekopaint.feature.history

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryImageFilterTest {

    private val output = HistoryImage("out.png", "", "output")
    private val temp = HistoryImage("temp.png", "", "temp")

    private fun entryOf(vararg images: HistoryImage) = HistoryEntry(
        promptId = "prompt",
        status = "success",
        completed = true,
        timestamp = null,
        startedAt = null,
        finishedAt = null,
        images = images.toList(),
        errorNodeId = null,
        errorMessage = null,
    )

    @Test
    fun hidesTempImagesWhenDisabled() {
        val visible = entryOf(output, temp, output).visibleImages(showTempPreviews = false)

        assertEquals(listOf(output, output), visible)
    }

    @Test
    fun keepsAllImagesWhenEnabled() {
        val visible = entryOf(output, temp).visibleImages(showTempPreviews = true)

        assertEquals(listOf(output, temp), visible)
    }

    @Test
    fun preservesOutputOnlyEntries() {
        val visible = entryOf(output).visibleImages(showTempPreviews = false)

        assertEquals(listOf(output), visible)
    }
}
