package io.github.xiaowu20245.nekopaint.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLexiconIndexTest {

    @Test
    fun blankQueryReturnsNothingAndLimitIsCappedAtFifty() {
        val suggestions = (1..80).map { index ->
            PromptTagSuggestion("tag$index", "标签$index")
        }
        val index = PromptLexiconIndex(suggestions)

        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
        assertEquals(50, index.search("tag", limit = 100).size)
    }

    @Test
    fun prefixMatchesTagChineseAndAliases() {
        val index = PromptLexiconIndex(
            listOf(
                PromptTagSuggestion("silver hair", "银发", listOf("白发", "电影光")),
                PromptTagSuggestion("smile", "微笑"),
                PromptTagSuggestion("school uniform", "校服"),
                PromptTagSuggestion("kemomimi-chan \\(naga u\\)", "兽耳酱"),
            ),
        )

        assertTrue(index.search("silver").first().tag == "silver hair")
        assertTrue(index.search("银").any { it.tag == "silver hair" })
        assertTrue(index.search("白").any { it.tag == "silver hair" })
        assertTrue(index.search("hair").any { it.tag == "silver hair" })
        assertTrue(index.search("发").any { it.tag == "silver hair" })
        assertEquals(
            setOf("silver hair", "smile", "school uniform"),
            index.search("s").map { it.tag }.toSet(),
        )
        assertTrue(index.search("naga").any { it.tag.startsWith("kemomimi-chan") })
        assertTrue(index.search("影").any { it.tag == "silver hair" })
    }
}
