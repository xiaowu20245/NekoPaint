package io.github.xiaowu20245.nekopaint.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeTest {

    @Test
    fun formatsBytesAndKibibytes() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.5 KiB", formatFileSize(1536))
    }

    @Test
    fun formatsMebibytesAndGibibytes() {
        assertEquals("2.0 MiB", formatFileSize(2L * 1024 * 1024))
        assertEquals("1.50 GiB", formatFileSize(3L * 512 * 1024 * 1024))
    }
}
