package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryEntrySerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun roundTripsCachedHistoryEntry() {
        val entry = HistoryEntry(
            promptId = "prompt-1",
            serverId = "autodl",
            status = "success",
            completed = true,
            timestamp = 1000L,
            startedAt = 900L,
            finishedAt = 1000L,
            images = listOf(
                HistoryImage(
                    filename = "结果 1.png",
                    subfolder = "Anima",
                    type = "output",
                    url = "http://127.0.0.1:8188/view?filename=%E7%BB%93%E6%9E%9C%201.png&subfolder=Anima&type=output",
                ),
            ),
            errorNodeId = null,
            errorMessage = null,
        )

        val restored = json.decodeFromString<HistoryEntry>(
            json.encodeToString(entry),
        )

        assertEquals(entry, restored)
    }

    @Test
    fun roundTripsFailedEntryWithoutImages() {
        val entry = HistoryEntry(
            promptId = "prompt-2",
            status = "failed",
            completed = false,
            timestamp = 2000L,
            startedAt = 1900L,
            finishedAt = 2000L,
            images = emptyList(),
            errorNodeId = "1",
            errorMessage = "PIL.UnidentifiedImageError",
        )

        val restored = json.decodeFromString<HistoryEntry>(
            json.encodeToString(entry),
        )

        assertEquals(entry, restored)
    }
}
