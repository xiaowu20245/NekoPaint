package io.github.xiaowu20245.nekopaint.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTagCatalogExternalTest {

    @Test
    fun `external entries extend search and local translation`() {
        try {
            PromptTagCatalog.setExternalSuggestions(
                listOf(
                    PromptTagSuggestion(
                        tag = "glowing eyes",
                        zh = "发光眼睛",
                        aliases = listOf("眼睛发光"),
                    ),
                ),
            )

            assertEquals(1, PromptTagCatalog.externalCount)
            assertTrue(
                PromptTagCatalog.search("glowing").any { it.tag == "glowing eyes" },
            )
            assertEquals(
                "glowing eyes",
                PromptTagCatalog.translateChinese("眼睛发光"),
            )
        } finally {
            PromptTagCatalog.setExternalSuggestions(emptyList())
        }
    }

    @Test
    fun `built in tag wins over duplicate external tag`() {
        try {
            PromptTagCatalog.setExternalSuggestions(
                listOf(
                    PromptTagSuggestion(
                        tag = "fox ears",
                        zh = "外部翻译",
                    ),
                ),
            )

            assertEquals(1, PromptTagCatalog.externalCount)
            assertEquals(
                "狐狸耳朵",
                PromptTagCatalog.zhFor("fox ears"),
            )
        } finally {
            PromptTagCatalog.setExternalSuggestions(emptyList())
        }
    }
}
