package io.github.xiaowu20245.nekopaint.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {

    @Test
    fun `parses heading and paragraph`() {
        val blocks = parseChatMarkdown("# 标题\n\n第一行\n第二行")

        assertEquals(
            listOf(
                ChatMarkdownBlock.Heading(1, "标题"),
                ChatMarkdownBlock.Paragraph("第一行\n第二行"),
            ),
            blocks,
        )
    }

    @Test
    fun `parses unordered list`() {
        val blocks = parseChatMarkdown("- 一\n- 二\n  续行\n+ 三")

        assertEquals(
            listOf(
                ChatMarkdownBlock.ListBlock(
                    ordered = false,
                    items = listOf("一", "二 续行", "三"),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `parses ordered list with start number`() {
        val blocks = parseChatMarkdown("3. 三\n4. 四")

        assertEquals(
            listOf(
                ChatMarkdownBlock.ListBlock(
                    ordered = true,
                    start = 3,
                    items = listOf("三", "四"),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `parses fenced code without inline formatting`() {
        val source = "```kotlin\nval x = **1**\n```"

        assertEquals(
            listOf(ChatMarkdownBlock.CodeBlock("kotlin", "val x = **1**")),
            parseChatMarkdown(source),
        )
    }

    @Test
    fun `markdown fence keeps nested code block`() {
        val source = "```markdown\n## 标题\n\n```python\nprint(1)\n```\n\n> 引用\n```"

        val block = parseChatMarkdown(source).single() as ChatMarkdownBlock.CodeBlock

        assertEquals("markdown", block.language)
        assertTrue(block.code.contains("```python"))
        assertTrue(block.code.contains("print(1)"))
        assertTrue(block.code.contains("> 引用"))
    }

    @Test
    fun `parses quote and divider`() {
        val blocks = parseChatMarkdown("> 第一行\n> 第二行\n\n---")

        assertEquals(
            listOf(
                ChatMarkdownBlock.Quote("第一行\n第二行"),
                ChatMarkdownBlock.Divider,
            ),
            blocks,
        )
    }

    @Test
    fun `dash list is not a divider`() {
        val blocks = parseChatMarkdown("- 项目")

        assertEquals(
            listOf(ChatMarkdownBlock.ListBlock(ordered = false, items = listOf("项目"))),
            blocks,
        )
    }

    @Test
    fun `builds inline styles`() {
        val annotated = buildChatMarkdownInline(
            text = "**粗体** *斜体* `code` ~~删除~~",
            codeBackground = Color.Gray,
            linkColor = Color.Blue,
        )

        assertEquals("粗体 斜体 code 删除", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(annotated.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(annotated.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertTrue(
            annotated.spanStyles.any {
                it.item.textDecoration == TextDecoration.LineThrough
            },
        )
    }

    @Test
    fun `builds bold italic and link`() {
        val annotated = buildChatMarkdownInline(
            text = "***重点*** [文档](https://example.com)",
            linkColor = Color.Blue,
        )

        assertEquals("重点 文档", annotated.text)
        assertTrue(
            annotated.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold &&
                    it.item.fontStyle == FontStyle.Italic
            },
        )
        assertEquals(1, annotated.getLinkAnnotations(0, annotated.length).size)
    }

    @Test
    fun `leaves snake case untouched`() {
        val annotated = buildChatMarkdownInline("foo_bar_baz")

        assertEquals("foo_bar_baz", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }
}
