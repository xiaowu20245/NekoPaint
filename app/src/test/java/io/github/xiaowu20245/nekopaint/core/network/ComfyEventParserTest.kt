package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyEventParserTest {

    private val parser = ComfyEventParser(
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    )

    private fun fixtureLines(name: String): List<String> =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "fixture not found: $name"
        }.bufferedReader().readLines().filter { it.isNotBlank() }

    @Test
    fun parsesVerifiedSuccessEventSequence() {
        val events = fixtureLines("ws_events_success.jsonl")
            .mapNotNull(parser::parse)

        assertEquals(8, events.size)
        assertTrue(events[0] is ComfyEvent.Status)
        assertEquals(0, (events[0] as ComfyEvent.Status).queueRemaining)
        assertEquals(
            "00000000-0000-4000-8000-000000000001",
            (events[1] as ComfyEvent.ExecutionStart).promptId,
        )
        assertTrue(events[2] is ComfyEvent.ExecutionCached)

        val progressState = events[3] as ComfyEvent.ProgressState
        assertEquals(1, progressState.nodes.size)
        assertEquals("5", progressState.nodes.first().nodeId)
        assertEquals("running", progressState.nodes.first().state)
        assertEquals(1.0, progressState.nodes.first().max, 0.0)

        assertEquals("5", (events[4] as ComfyEvent.Executing).nodeId)
        assertEquals(1.0, (events[5] as ComfyEvent.Progress).value, 0.0)
        assertTrue(events[6] is ComfyEvent.Executed)
        assertTrue(events[7] is ComfyEvent.Success)
    }

    @Test
    fun parsesExecutionErrorWithNodeAndMessage() {
        val event = parser.parse(
            """
            {
              "type": "execution_error",
              "data": {
                "prompt_id": "p1",
                "node_id": "12",
                "exception_type": "RuntimeError",
                "exception_message": "CUDA out of memory"
              }
            }
            """.trimIndent(),
        ) as ComfyEvent.Error

        assertEquals("p1", event.promptId)
        assertEquals("12", event.nodeId)
        assertEquals("CUDA out of memory", event.message)
    }

    @Test
    fun ignoresUnknownAndMalformedFrames() {
        assertNull(parser.parse("""{"type":"new_future_event","data":{}}"""))
        assertNull(parser.parse("not-json"))
        assertNull(parser.parse("""{"data":{}}"""))
    }
}
