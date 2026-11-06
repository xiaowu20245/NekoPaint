package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowUploadNameTest {

    @Test
    fun stripsPathAndPreservesExtension() {
        val name = buildUploadFileName("C:\\Users\\cat\\photo.JPG", timestamp = 123L)

        assertEquals("nekopaint_123_photo.JPG", name)
    }

    @Test
    fun replacesUnsafeCharacters() {
        val name = buildUploadFileName("猫 图.png", timestamp = 456L)

        assertEquals("nekopaint_456____.png", name)
    }

    @Test
    fun fallsBackForBlankName() {
        val name = buildUploadFileName("   ", timestamp = 789L)

        assertEquals("nekopaint_789_image.png", name)
    }
}
