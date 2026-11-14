package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatStreamParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `extracts sse payload`() {
        assertEquals(
            """{"choices":[]}""",
            AiChatStreamParser.extractPayload("""data: {"choices":[]}"""),
        )
        assertEquals(
            """{"choices":[]}""",
            AiChatStreamParser.extractPayload("""{"choices":[]}"""),
        )
        assertNull(AiChatStreamParser.extractPayload("event: message"))
        assertNull(AiChatStreamParser.extractPayload(": ping"))
        assertTrue(AiChatStreamParser.isDone("[DONE]"))
    }

    @Test
    fun `parses content delta`() {
        val delta = AiChatStreamParser.parseDelta(
            json,
            """{"choices":[{"delta":{"content":"你好"},"finish_reason":null}]}""",
        )

        assertEquals("你好", delta?.content)
        assertTrue(delta?.toolCalls.orEmpty().isEmpty())
    }

    @Test
    fun `parses tool call fragments`() {
        val first = AiChatStreamParser.parseDelta(
            json,
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"get_queue_snapshot","arguments":""}}]}}]}""",
        )
        val second = AiChatStreamParser.parseDelta(
            json,
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}""",
        )

        assertEquals("call_1", first?.toolCalls?.single()?.id)
        assertEquals("get_queue_snapshot", first?.toolCalls?.single()?.name)
        assertEquals("{}", second?.toolCalls?.single()?.arguments)
        assertNull(second?.toolCalls?.single()?.id)
    }

    @Test
    fun `parses finish only chunk`() {
        val delta = AiChatStreamParser.parseDelta(
            json,
            """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
        )

        assertEquals("stop", delta?.finishReason)
        assertNull(delta?.content)
    }

    @Test
    fun `parses non streaming fallback`() {
        val delta = AiChatStreamParser.parseDelta(
            json,
            """{"choices":[{"message":{"role":"assistant","content":"完整回答"},"finish_reason":"stop"}]}""",
        )

        assertEquals("完整回答", delta?.content)
        assertEquals("stop", delta?.finishReason)
    }

    @Test
    fun `returns null for invalid payload`() {
        assertNull(AiChatStreamParser.parseDelta(json, "not-json"))
    }
}
