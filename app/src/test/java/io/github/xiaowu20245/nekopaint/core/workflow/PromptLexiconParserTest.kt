package io.github.xiaowu20245.nekopaint.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLexiconParserTest {

    @Test
    fun `parses standard csv with header and multiple aliases`() {
        val csv = """
            tag,category,count,zh_aliases
            fox ears,general,120,狐狸耳朵|兽耳
            masterpiece,quality,9999,杰作|高质量
        """.trimIndent()

        val entries = PromptLexiconParser.parse(csv).getOrThrow()

        assertEquals(2, entries.size)
        assertEquals("fox ears", entries[0].tag)
        assertEquals("狐狸耳朵", entries[0].zh)
        assertEquals(listOf("兽耳"), entries[0].aliases)
        assertEquals("masterpiece", entries[1].tag)
    }

    @Test
    fun `parses csv without header`() {
        val csv = "silver hair,general,10,银发|白发"

        val entry = PromptLexiconParser.parse(csv).getOrThrow().single()

        assertEquals("silver hair", entry.tag)
        assertEquals("general", entry.category)
        assertEquals("银发", entry.zh)
        assertEquals(listOf("白发"), entry.aliases)
    }

    @Test
    fun `parses quoted comma inside alias`() {
        val csv = "tag,category,count,zh_aliases\nsmile,general,1,\"微笑,笑容\""

        val entry = PromptLexiconParser.parse(csv).getOrThrow().single()

        assertEquals("smile", entry.tag)
        assertEquals("微笑", entry.zh)
        assertEquals(listOf("笑容"), entry.aliases)
    }

    @Test
    fun `parses json array and wrapped entries`() {
        val array = """
            [
              {"tag":"fox tail","zh":"狐狸尾巴","aliases":["尾巴"]},
              {"name":"red eyes","zh_aliases":["红瞳","红眼"]}
            ]
        """.trimIndent()
        val wrapped = """{"entries":[{"tag":"solo","zh":"单人"}]}"""

        val arrayEntries = PromptLexiconParser.parse(array, "tags.json").getOrThrow()
        val wrappedEntries = PromptLexiconParser.parse(wrapped, "tags.json").getOrThrow()

        assertEquals(2, arrayEntries.size)
        assertEquals("狐狸尾巴", arrayEntries[0].zh)
        assertEquals("红瞳", arrayEntries[1].zh)
        assertEquals(listOf("红眼"), arrayEntries[1].aliases)
        assertEquals("solo", wrappedEntries.single().tag)
    }

    @Test
    fun `parses tag to alias mapping json`() {
        val json = """{"dress":"连衣裙","smile":["微笑","笑容"]}"""

        val entries = PromptLexiconParser.parse(json, "mapping.json").getOrThrow()

        assertEquals(2, entries.size)
        assertEquals("连衣裙", entries.first { it.tag == "dress" }.zh)
        assertEquals("笑容", entries.first { it.tag == "smile" }.aliases.single())
    }

    @Test
    fun `parses tag only csv without chinese aliases`() {
        val csv = "tag,category,count\nglowing eyes,general,100"

        val entry = PromptLexiconParser.parse(csv).getOrThrow().single()

        assertEquals("glowing eyes", entry.tag)
        assertEquals("", entry.zh)
        assertTrue(entry.aliases.isEmpty())
    }

    @Test
    fun `empty content fails with readable error`() {
        val result = PromptLexiconParser.parse("   ")

        assertTrue(result.isFailure)
        assertEquals("文件为空", result.exceptionOrNull()?.message)
    }
}
