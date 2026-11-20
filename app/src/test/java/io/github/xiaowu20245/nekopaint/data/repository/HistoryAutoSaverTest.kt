package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoryAutoSaverTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(System.getProperty("java.io.tmpdir"), "nekopaint-autosave-${System.nanoTime()}.json")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun savesAtMostThreeImagesPerRoundAndSkipsSavedKeys() = runTest {
        val calls = AtomicInteger()
        val saver = saver { _, _ ->
            calls.incrementAndGet()
            Result.success(Unit)
        }
        val entry = entry("p1", (1..7).map { image("img$it.png") })

        assertEquals(AutoSaveState.Saved(3), saver.saveNew("server-a", listOf(entry)))
        assertEquals(3, calls.get())
        assertEquals(AutoSaveState.Saved(3), saver.saveNew("server-a", listOf(entry)))
        assertEquals(6, calls.get())
        assertEquals(AutoSaveState.Saved(1), saver.saveNew("server-a", listOf(entry)))
        assertEquals(AutoSaveState.Idle, saver.saveNew("server-a", listOf(entry)))
        assertEquals(7, calls.get())
    }

    @Test
    fun duplicateImageInSameEntryIsDownloadedOnce() = runTest {
        val calls = AtomicInteger()
        val saver = saver { _, _ ->
            calls.incrementAndGet()
            Result.success(Unit)
        }
        val image = image("same.png")
        val entry = entry("p1", listOf(image, image))

        assertEquals(AutoSaveState.Saved(1), saver.saveNew("server-a", listOf(entry)))
        assertEquals(1, calls.get())
    }

    @Test
    fun failuresAreSummarizedAndRetriedNextRound() = runTest {
        val attempts = AtomicInteger()
        val saver = saver { url, _ ->
            val attempt = attempts.incrementAndGet()
            if (url.endsWith("bad.png") && attempt < 3) {
                Result.failure(IllegalStateException("network"))
            } else {
                Result.success(Unit)
            }
        }

        val first = saver.saveNew("server-a", listOf(entry("p1", listOf(image("good.png"), image("bad.png")))))
        assertEquals(AutoSaveState.Failed(saved = 1, failed = 1), first)

        val retry = saver.saveNew("server-a", listOf(entry("p1", listOf(image("good.png"), image("bad.png")))))
        assertEquals(AutoSaveState.Saved(1), retry)
        assertEquals(3, attempts.get())
    }

    @Test
    fun cancelStopsAfterCurrentImage() = runTest {
        val calls = AtomicInteger()
        lateinit var saver: HistoryAutoSaver
        saver = saver { _, _ ->
            calls.incrementAndGet()
            saver.cancel()
            Result.success(Unit)
        }

        val result = saver.saveNew("server-a", listOf(entry("p1", (1..3).map { image("img$it.png") })))

        assertEquals(AutoSaveState.Cancelled(saved = 1, failed = 0), result)
        assertEquals(1, calls.get())
    }

    private fun saver(writer: suspend (String, String) -> Result<Unit>): HistoryAutoSaver =
        HistoryAutoSaver(
            file = file,
            json = json,
            enabledProvider = AutoSaveEnabledProvider { true },
            imageWriter = HistoryImageWriter { url, name -> writer(url, name) },
        )

    private fun entry(promptId: String, images: List<HistoryImage>) = HistoryEntry(
        promptId = promptId,
        serverId = "server-a",
        status = "success",
        completed = true,
        timestamp = 1L,
        startedAt = 1L,
        finishedAt = 2L,
        images = images,
        errorNodeId = null,
        errorMessage = null,
    )

    private fun image(filename: String) = HistoryImage(
        filename = filename,
        subfolder = "",
        type = "output",
        url = "http://192.0.2.10/view/$filename",
    )
}
