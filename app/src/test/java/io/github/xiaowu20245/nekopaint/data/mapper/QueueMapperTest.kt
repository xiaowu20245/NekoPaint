package io.github.xiaowu20245.nekopaint.data.mapper

import io.github.xiaowu20245.nekopaint.core.network.HistoryItemDto
import io.github.xiaowu20245.nekopaint.core.network.QueueDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "fixture not found: $name"
        }.bufferedReader().readText()

    @Test
    fun mapsEmptyQueue() {
        val dto = json.decodeFromString<QueueDto>(fixture("queue_idle.json"))

        val snapshot = QueueMapper.toQueueSnapshot(dto, now = 123L)

        assertTrue(snapshot.running.isEmpty())
        assertTrue(snapshot.pending.isEmpty())
        assertEquals(123L, snapshot.updatedAt)
    }

    @Test
    fun mapsQueueItemMetadataAndNodeTitles() {
        val payload = """
            {
              "queue_running": [
                [7, "prompt-1", {
                  "3": {"class_type": "KSampler", "_meta": {"title": "采样器"}},
                  "9": {"class_type": "SaveImage"}
                }, {"client_id": "client-1"}]
              ],
              "queue_pending": []
            }
        """.trimIndent()

        val snapshot = QueueMapper.toQueueSnapshot(
            json.decodeFromString<QueueDto>(payload),
        )

        val item = snapshot.running.single()
        assertEquals(7, item.number)
        assertEquals("prompt-1", item.promptId)
        assertEquals("client-1", item.clientId)
        assertEquals("采样器", item.nodeTitles["3"])
        assertEquals("SaveImage", item.nodeTitles["9"])
    }

    @Test
    fun mapsSuccessfulHistoryWithTimingAndImages() {
        val payload = fixture("history_success.json")
        val dto = json.decodeFromString<Map<String, HistoryItemDto>>(payload)

        val entry = QueueMapper.toHistoryEntries(dto, BASE_URL).single()

        assertEquals("00000000-0000-4000-8000-000000000001", entry.promptId)
        assertEquals("success", entry.status)
        assertTrue(entry.completed)
        assertEquals(1, entry.images.size)
        assertEquals("nekopaint_verify_00001_.png", entry.images.first().filename)
        assertEquals(
            "http://127.0.0.1:8188/view?filename=nekopaint_verify_00001_.png&subfolder=&type=output",
            entry.images.first().url,
        )
        assertEquals(1793762353188L, entry.startedAt)
        assertEquals(1793762357025L, entry.finishedAt)
        assertEquals(1793762357025L, entry.timestamp)
        assertNull(entry.errorMessage)
    }

    @Test
    fun interruptedHistoryTakesPrecedenceOverErrorStatus() {
        val payload = """
            {
              "p1": {
                "outputs": {},
                "status": {
                  "status_str": "error",
                  "completed": false,
                  "messages": [
                    ["execution_start", {"prompt_id": "p1", "timestamp": 1000}],
                    ["execution_interrupted", {"prompt_id": "p1", "timestamp": 2000}]
                  ]
                }
              }
            }
        """.trimIndent()

        val entry = QueueMapper.toHistoryEntries(
            json.decodeFromString<Map<String, HistoryItemDto>>(payload),
            BASE_URL,
        ).single()

        assertEquals("interrupted", entry.status)
        assertEquals(1000L, entry.startedAt)
        assertEquals(2000L, entry.finishedAt)
    }

    @Test
    fun failedHistoryKeepsNodeAndReason() {
        val payload = """
            {
              "p2": {
                "outputs": {},
                "status": {
                  "status_str": "error",
                  "completed": false,
                  "messages": [
                    ["execution_error", {
                      "prompt_id": "p2",
                      "node_id": "12",
                      "exception_message": "CUDA out of memory",
                      "timestamp": 3000
                    }]
                  ]
                }
              }
            }
        """.trimIndent()

        val entry = QueueMapper.toHistoryEntries(
            json.decodeFromString<Map<String, HistoryItemDto>>(payload),
            BASE_URL,
        ).single()

        assertEquals("failed", entry.status)
        assertEquals("12", entry.errorNodeId)
        assertEquals("CUDA out of memory", entry.errorMessage)
        assertEquals(3000L, entry.timestamp)
    }

    @Test
    fun unknownHistoryStatusDoesNotCrash() {
        val payload = """
            {
              "p3": {
                "outputs": {},
                "status": {"status_str": "weird", "completed": null, "messages": []}
              }
            }
        """.trimIndent()

        val entry = QueueMapper.toHistoryEntries(
            json.decodeFromString<Map<String, HistoryItemDto>>(payload),
            BASE_URL,
        ).single()

        assertEquals("unknown", entry.status)
        assertTrue(entry.images.isEmpty())
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8188/"
    }
}
