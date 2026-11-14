package io.github.xiaowu20245.nekopaint.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Neko I 的轻量 Markdown 解析。
 *
 * 只支持聊天里常见的块级与行内语法，不引入第三方依赖：
 * 标题、段落、引用、围栏代码块、有序/无序列表、分割线；
 * 行内支持加粗、斜体、删除线、行内代码和链接。
 */
sealed interface ChatMarkdownBlock {
    data class Heading(val level: Int, val text: String) : ChatMarkdownBlock

    data class Paragraph(val text: String) : ChatMarkdownBlock

    data class Quote(val text: String) : ChatMarkdownBlock

    data class CodeBlock(val language: String?, val code: String) : ChatMarkdownBlock

    data class ListBlock(
        val ordered: Boolean,
        val start: Int = 1,
        val items: List<String>,
    ) : ChatMarkdownBlock

    data object Divider : ChatMarkdownBlock
}

private val bulletPattern = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
private val orderedPattern = Regex("""^\s{0,3}(\d{1,9})[.)]\s+(.*)$""")
private val quotePattern = Regex("""^\s{0,3}>\s?(.*)$""")
private val dividerPattern = Regex("""^\s{0,3}([-*_])(?:\s*\1){2,}\s*$""")

internal fun parseChatMarkdown(source: String): List<ChatMarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val blocks = mutableListOf<ChatMarkdownBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks += ChatMarkdownBlock.Paragraph(paragraph.joinToString("\n").trim())
        paragraph.clear()
    }

    var index = 0
    while (index < lines.size) {
        val rawLine = lines[index]
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            index++
            continue
        }

        val fence = fenceMarker(rawLine)
        if (fence != null) {
            flushParagraph()
            val (marker, language) = fence
            val closing = marker.toString().repeat(3)
            // markdown 源码示例里经常再套一层 ```python，内层围栏不能结束外层代码块。
            val tracksNestedFences = language.lowercase() in setOf("markdown", "md")
            var nestedDepth = 1
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size) {
                val candidate = lines[index].trimStart()
                if (candidate.startsWith(closing)) {
                    val info = candidate.removePrefix(closing).trim()
                    if (tracksNestedFences && nestedDepth == 1 && info.isNotEmpty()) {
                        nestedDepth = 2
                        codeLines += lines[index]
                        index++
                        continue
                    }
                    if (tracksNestedFences && nestedDepth > 1) {
                        nestedDepth--
                        codeLines += lines[index]
                        index++
                        continue
                    }
                    index++
                    break
                }
                codeLines += lines[index]
                index++
            }
            blocks += ChatMarkdownBlock.CodeBlock(
                language = language.takeIf { it.isNotBlank() },
                code = codeLines.joinToString("\n"),
            )
            continue
        }

        val heading = parseHeading(rawLine)
        if (heading != null) {
            flushParagraph()
            blocks += heading
            index++
            continue
        }

        if (dividerPattern.matches(trimmed)) {
            flushParagraph()
            blocks += ChatMarkdownBlock.Divider
            index++
            continue
        }

        if (quotePattern.matches(trimmed)) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (index < lines.size) {
                val match = quotePattern.matchEntire(lines[index].trim()) ?: break
                quoteLines += match.groupValues[1]
                index++
            }
            blocks += ChatMarkdownBlock.Quote(quoteLines.joinToString("\n"))
            continue
        }

        val firstBullet = bulletPattern.matchEntire(trimmed)
        val firstOrdered = orderedPattern.matchEntire(trimmed)
        if (firstBullet != null || firstOrdered != null) {
            flushParagraph()
            val ordered = firstOrdered != null
            val start = firstOrdered?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val current = lines[index]
                val item = if (ordered) {
                    orderedPattern.matchEntire(current.trim())
                } else {
                    bulletPattern.matchEntire(current.trim())
                }
                if (item != null) {
                    items += if (ordered) item.groupValues[2] else item.groupValues[1]
                    index++
                } else if (
                    items.isNotEmpty() &&
                    current.isNotBlank() &&
                    current.firstOrNull()?.isWhitespace() == true
                ) {
                    // 列表项的续行并入上一条，避免拆成新段落。
                    items[items.lastIndex] = items.last() + " " + current.trim()
                    index++
                } else {
                    break
                }
            }
            blocks += ChatMarkdownBlock.ListBlock(
                ordered = ordered,
                start = start,
                items = items,
            )
            continue
        }

        paragraph += rawLine.trimEnd()
        index++
    }
    flushParagraph()
    return blocks
}

private fun parseHeading(line: String): ChatMarkdownBlock.Heading? {
    val trimmedStart = line.trimStart()
    val level = trimmedStart.takeWhile { it == '#' }.length
    if (level !in 1..6) return null
    if (trimmedStart.length <= level || !trimmedStart[level].isWhitespace()) return null
    val text = trimmedStart
        .drop(level)
        .trim()
        .replace(Regex("""\s+#+\s*$"""), "")
    return ChatMarkdownBlock.Heading(level = level, text = text)
}

private fun fenceMarker(line: String): Pair<Char, String>? {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("```") -> '`' to trimmed.removePrefix("```").trim()
        trimmed.startsWith("~~~") -> '~' to trimmed.removePrefix("~~~").trim()
        else -> null
    }
}

private val inlinePattern = Regex(
    "`([^`\\n]+)`" +
        "|\\*\\*\\*([^*\\n]+)\\*\\*\\*" +
        "|\\*\\*([^*\\n]+)\\*\\*" +
        "|~~([^~\\n]+)~~" +
        "|\\[([^\\]]+)\\]\\(([^)\\s]+)\\)" +
        "|\\*([^*\\n]+)\\*",
)

internal fun buildChatMarkdownInline(
    text: String,
    codeBackground: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
    appendInlineMarkdown(text, codeBackground, linkColor)
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    codeBackground: Color,
    linkColor: Color,
) {
    var index = 0
    while (index < text.length) {
        val match = inlinePattern.find(text, index)
        if (match == null) {
            append(text.substring(index))
            return
        }
        if (match.range.first > index) {
            append(text.substring(index, match.range.first))
        }

        val groups = match.groups
        val content = match.groupValues
        when {
            groups[1] != null -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                ),
            ) {
                append(content[1])
            }

            groups[2] != null -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
            ) {
                appendInlineMarkdown(content[2], codeBackground, linkColor)
            }

            groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlineMarkdown(content[3], codeBackground, linkColor)
            }

            groups[4] != null -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
            ) {
                appendInlineMarkdown(content[4], codeBackground, linkColor)
            }

            groups[5] != null && groups[6] != null -> {
                val style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                )
                pushLink(
                    LinkAnnotation.Url(
                        url = content[6],
                        styles = TextLinkStyles(style = style),
                    ),
                )
                withStyle(style) {
                    appendInlineMarkdown(content[5], codeBackground, linkColor)
                }
                pop()
            }

            groups[7] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlineMarkdown(content[7], codeBackground, linkColor)
            }
        }
        index = match.range.last + 1
    }
}
