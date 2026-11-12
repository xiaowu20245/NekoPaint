package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiChatResponseParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses first choice message content`() {
        val body = """
            {
              "choices": [
                {"message": {"role": "assistant", "content": "  fox ears, smile  "}}
              ]
            }
        """.trimIndent()

        assertEquals("fox ears, smile", AiChatResponseParser.parseContent(json, body))
    }

    @Test
    fun `parses openai error message`() {
        val body = """{"error":{"message":"Incorrect API key provided","type":"auth"}}"""

        assertEquals(
            "Incorrect API key provided",
            AiChatResponseParser.parseErrorMessage(json, body),
        )
    }

    @Test
    fun `returns null for malformed response`() {
        assertNull(AiChatResponseParser.parseContent(json, "not-json"))
        assertNull(AiChatResponseParser.parseContent(json, """{"choices":[]}"""))
        assertNull(AiChatResponseParser.parseContent(json, """{"choices":[1]}"""))
        assertNull(AiChatResponseParser.parseContent(json, """{"choices":[{"message":null}]}"""))
        assertNull(AiChatResponseParser.parseErrorMessage(json, """{"choices":[]}"""))
    }
}
