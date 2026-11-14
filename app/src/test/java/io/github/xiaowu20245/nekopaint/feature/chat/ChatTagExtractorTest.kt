package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTagExtractorTest {

    @Test
    fun `extracts tags from fenced tags block`() {
        val text = """
            画面是一个白发少女。
            标签：
            ```tags
            1girl, solo, long_hair, blue_eyes
            ```
        """.trimIndent()

        assertEquals(
            "1girl, solo, long_hair, blue_eyes",
            extractCopyableTags(text),
        )
    }

    @Test
    fun `normalizes multiline and bullet tags`() {
        val text = """
            ```tags
            - 1girl
            - solo
            - long_hair
            ```
        """.trimIndent()

        assertEquals("1girl, solo, long_hair", extractCopyableTags(text))
    }

    @Test
    fun `falls back to inline label line`() {
        val text = "画面描述。\n标签：1girl, solo, smile"

        assertEquals("1girl, solo, smile", extractCopyableTags(text))
    }

    @Test
    fun `returns null without tags`() {
        assertNull(extractCopyableTags("这是一段普通回答。"))
    }

    @Test
    fun `strips fenced tags and keeps description`() {
        val text = """
            画面是一个白发少女。
            标签：
            ```tags
            1girl, solo, long_hair
            ```
        """.trimIndent()

        assertEquals("画面是一个白发少女。", stripTagsSection(text))
    }

    @Test
    fun `strips inline tags`() {
        assertEquals(
            "画面描述。",
            stripTagsSection("画面描述。\n标签：1girl, solo, smile"),
        )
    }

    @Test
    fun `strips tags-only response to empty text`() {
        assertEquals(
            "",
            stripTagsSection("```tags\n1girl, solo\n```"),
        )
    }

    @Test
    fun `leaves normal text unchanged`() {
        assertEquals("这是一段普通回答。", stripTagsSection("这是一段普通回答。"))
    }
}
