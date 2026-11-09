package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTagExpanderTest {

    @Test
    fun `expand replaces group placeholders in order`() {
        val groups = listOf(
            PromptTagGroup("1", "起手画质组", listOf("masterpiece", "best quality")),
            PromptTagGroup("2", "白发狐娘组", listOf("1girl", "silver hair")),
        )

        val expanded = PromptTagExpander.expand(
            "1girl, #起手画质组, #白发狐娘组, smile",
            groups,
        )

        assertEquals(
            "1girl, masterpiece, best quality, 1girl, silver hair, smile",
            expanded,
        )
    }

    @Test
    fun `expand prefers longer group names`() {
        val groups = listOf(
            PromptTagGroup("1", "画质", listOf("quality")),
            PromptTagGroup("2", "起手画质组", listOf("masterpiece")),
        )

        assertEquals(
            "masterpiece, smile",
            PromptTagExpander.expand("#起手画质组, smile", groups),
        )
    }

    @Test
    fun `expand keeps unknown placeholders and cleans empty group commas`() {
        val groups = listOf(PromptTagGroup("1", "空组", emptyList()))

        assertEquals("1girl, smile", PromptTagExpander.expand("1girl, #空组, smile", groups))
        assertEquals("#未知组, smile", PromptTagExpander.expand("#未知组, smile", groups))
    }

    @Test
    fun `referencedGroups returns only groups present in prompt`() {
        val groups = listOf(
            PromptTagGroup("1", "起手画质组", listOf("masterpiece")),
            PromptTagGroup("2", "白发狐娘组", listOf("1girl")),
        )

        val referenced = PromptTagExpander.referencedGroups(
            "#白发狐娘组, smile",
            groups,
        )

        assertEquals(listOf("2"), referenced.map { it.id })
        assertEquals(3, PromptTagExpander.promptPartCount("#白发狐娘组, smile, 1girl"))
        assertEquals(2, PromptTagExpander.expandedItemCount("#白发狐娘组, smile", groups))
    }

    @Test
    fun `catalog searches english chinese and aliases`() {
        assertTrue(PromptTagCatalog.search("银").any { it.tag == "silver hair" })
        assertTrue(PromptTagCatalog.search("fox").any { it.tag == "fox ears" })
        assertTrue(PromptTagCatalog.search("白丝").any { it.tag == "white thighhighs" })
    }

    @Test
    fun `catalog translates known chinese tags and rejects unknown sentences`() {
        assertEquals(
            "fox ears, masterpiece",
            PromptTagCatalog.translateChinese("狐耳、杰作"),
        )
        assertNull(PromptTagCatalog.translateChinese("神社台阶，飘落的樱花"))
    }
}
