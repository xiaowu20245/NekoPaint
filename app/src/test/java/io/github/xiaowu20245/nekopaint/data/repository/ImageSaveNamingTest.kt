package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSaveNamingTest {

    @Test
    fun keepsOriginalNameWhenNoConflict() {
        val name = ImageSaveNaming.uniqueName("ComfyUI_00001_.png") { false }

        assertEquals("ComfyUI_00001_.png", name)
    }

    @Test
    fun addsSequenceWhenNameExists() {
        val existing = setOf("ComfyUI_00001_.png", "ComfyUI_00001_ (1).png")
        val name = ImageSaveNaming.uniqueName("ComfyUI_00001_.png") { it in existing }

        assertEquals("ComfyUI_00001_ (2).png", name)
    }

    @Test
    fun stripsPathSeparatorsFromServerName() {
        val name = ImageSaveNaming.uniqueName("../../evil.png") { false }

        assertEquals("evil.png", name)
    }

    @Test
    fun mapsCommonImageMimeTypes() {
        assertEquals("image/png", ImageSaveNaming.mimeFor("a.PNG"))
        assertEquals("image/jpeg", ImageSaveNaming.mimeFor("a.jpeg"))
        assertEquals("image/webp", ImageSaveNaming.mimeFor("a.webp"))
        assertEquals("image/png", ImageSaveNaming.mimeFor("no-extension"))
    }
}
