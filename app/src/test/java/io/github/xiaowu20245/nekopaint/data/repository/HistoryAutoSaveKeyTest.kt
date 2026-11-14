package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HistoryAutoSaveKeyTest {

    private val image = HistoryImage(
        filename = "nekopaint_001.png",
        subfolder = "batch",
        type = "output",
    )

    @Test
    fun `same image identity produces the same key`() {
        assertEquals(
            autoSaveImageKey("server-1", "prompt-1", image),
            autoSaveImageKey("server-1", "prompt-1", image.copy()),
        )
    }

    @Test
    fun `server and prompt id isolate saved images`() {
        val base = autoSaveImageKey("server-1", "prompt-1", image)

        assertNotEquals(base, autoSaveImageKey("server-2", "prompt-1", image))
        assertNotEquals(base, autoSaveImageKey("server-1", "prompt-2", image))
    }

    @Test
    fun `type subfolder and filename isolate saved images`() {
        val base = autoSaveImageKey("server-1", "prompt-1", image)

        assertNotEquals(base, autoSaveImageKey("server-1", "prompt-1", image.copy(type = "temp")))
        assertNotEquals(base, autoSaveImageKey("server-1", "prompt-1", image.copy(subfolder = "")))
        assertNotEquals(
            base,
            autoSaveImageKey("server-1", "prompt-1", image.copy(filename = "other.png")),
        )
    }
}
