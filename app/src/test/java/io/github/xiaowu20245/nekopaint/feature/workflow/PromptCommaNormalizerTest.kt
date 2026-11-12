package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PromptCommaNormalizerTest {

    @Test
    fun `replaces full width comma with comma and space`() {
        assertEquals(
            "1girl, fox ears, smile",
            normalizePromptCommas("1girl，fox ears，smile"),
        )
    }

    @Test
    fun `moves cursor after inserted space`() {
        val input = TextFieldValue(
            text = "1girl，fox",
            selection = TextRange(6),
        )

        val result = normalizePromptTextFieldValue(input)

        assertEquals("1girl, fox", result.text)
        assertEquals(7, result.selection.end)
    }

    @Test
    fun `maps selection when multiple commas occur before cursor`() {
        val input = TextFieldValue(
            text = "a，b，c",
            selection = TextRange(3, 5),
        )

        val result = normalizePromptTextFieldValue(input)

        assertEquals("a, b, c", result.text)
        assertEquals(4, result.selection.start)
        assertEquals(7, result.selection.end)
    }

    @Test
    fun `keeps value unchanged when no full width comma exists`() {
        val input = TextFieldValue(
            text = "1girl, fox ears",
            selection = TextRange(3),
        )

        assertSame(input, normalizePromptTextFieldValue(input))
    }
}
