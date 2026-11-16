package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsonStorePersistenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-json-store-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun historyRoundTripIsScopedByServer() = runTest {
        val store = HistoryStore(filesDir, json)
        val entries = listOf(entry("p1", "success"), entry("p2", "failed"))

        store.save("server-a", entries)

        val loaded = store.load("server-a")
        assertEquals(listOf("p1", "p2"), loaded.map { it.promptId })
        assertTrue(loaded.all { it.serverId == "server-a" })
        assertTrue(store.load("server-b").isEmpty())
    }

    @Test
    fun historyLegacyDirectoryIsMigrated() = runTest {
        val legacy = File(filesDir, "history").apply { mkdirs() }
        File(legacy, "history.json").writeText(
            json.encodeToString(listOf(entry("legacy", "success"))),
        )
        val store = HistoryStore(filesDir, json)

        val loaded = store.load("default")

        assertEquals(listOf("legacy"), loaded.map { it.promptId })
        assertTrue(
            File(filesDir, "servers/default/history/history.json").exists(),
        )
    }

    @Test
    fun corruptHistoryFileReturnsEmptyInsteadOfCrashing() = runTest {
        val file = File(filesDir, "servers/server-a/history/history.json")
        file.parentFile?.mkdirs()
        file.writeText("{broken")

        assertTrue(HistoryStore(filesDir, json).load("server-a").isEmpty())
    }

    @Test
    fun modelCatalogRoundTripAndCorruptFallback() = runTest {
        val store = ModelCatalogStore(filesDir, json)
        val catalog = mapOf(
            "checkpoints" to listOf("a.safetensors", "b.safetensors"),
            "loras" to listOf("style.safetensors"),
        )

        store.save("server-a", catalog)
        assertEquals(catalog, store.load("server-a"))

        val file = File(filesDir, "servers/server-a/models/catalog.json")
        file.writeText("[]")
        assertTrue(store.load("server-a").isEmpty())
    }

    @Test
    fun modelCatalogLegacyDirectoryIsMigrated() = runTest {
        val legacy = File(filesDir, "models").apply { mkdirs() }
        File(legacy, "catalog.json").writeText(
            json.encodeToString(mapOf("vae" to listOf("vae.safetensors"))),
        )

        val loaded = ModelCatalogStore(filesDir, json).load("default")

        assertEquals(listOf("vae.safetensors"), loaded["vae"])
        assertTrue(File(filesDir, "servers/default/models/catalog.json").exists())
    }

    @Test
    fun failedAtomicReplaceKeepsPreviousHistory() = runTest {
        val store = HistoryStore(filesDir, json)
        store.save("server-a", listOf(entry("p1", "success")))
        val file = File(filesDir, "servers/server-a/history/history.json")

        val failure = runCatching {
            io.github.xiaowu20245.nekopaint.core.common.AtomicFileWriter.writeText(
                target = file,
                text = "[]",
            ) { _, _ -> throw java.io.IOException("simulated move failure") }
        }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertEquals(
            listOf("p1"),
            store.load("server-a").map { it.promptId },
        )
    }

    @Test
    fun concurrentHistorySavesRemainParseable() = runTest {
        val store = HistoryStore(filesDir, json)
        coroutineScope {
            (1..24).map { index ->
                async(Dispatchers.Default) {
                    store.save(
                        "server-a",
                        listOf(entry("p$index", "success")),
                    )
                }
            }.awaitAll()
        }

        val loaded = store.load("server-a")
        assertEquals(1, loaded.size)
        assertTrue(loaded.single().promptId.startsWith("p"))
        assertFalse(File(filesDir, "servers/server-a/history/history.json").readText().isBlank())
    }

    private fun entry(promptId: String, status: String) = HistoryEntry(
        promptId = promptId,
        status = status,
        completed = status == "success",
        timestamp = 1L,
        startedAt = 0L,
        finishedAt = 2L,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )
}
