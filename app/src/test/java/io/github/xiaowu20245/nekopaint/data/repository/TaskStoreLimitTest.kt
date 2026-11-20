package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskStoreLimitTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = File(System.getProperty("java.io.tmpdir"), "nekopaint-task-limit-${System.nanoTime()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun terminalRecordsAreTrimmedToLimitOldestFirst() = runTest {
        val store = TaskStore(filesDir, json, maxTerminalRecords = 3)
        (1..5).forEach { index ->
            store.save("server-a", record("p$index", TaskStatus.SUCCESS, submittedAt = index.toLong()))
        }

        val listed = store.list("server-a")

        assertEquals(listOf("p5", "p4", "p3"), listed.map { it.promptId })
        assertFalse(File(filesDir, "servers/server-a/tasks/p1.json").exists())
        assertTrue(File(filesDir, "servers/server-a/tasks/p5.json").exists())
    }

    @Test
    fun nonTerminalRecordsAreNeverTrimmed() = runTest {
        val store = TaskStore(filesDir, json, maxTerminalRecords = 1)
        store.save("server-a", record("running", TaskStatus.RUNNING, submittedAt = 1))
        store.save("server-a", record("queued", TaskStatus.QUEUED, submittedAt = 2))
        store.save("server-a", record("unknown", TaskStatus.UNKNOWN, submittedAt = 3))
        store.save("server-a", record("done-1", TaskStatus.SUCCESS, submittedAt = 4))
        store.save("server-a", record("done-2", TaskStatus.FAILED, submittedAt = 5))

        val ids = store.list("server-a").map { it.promptId }.toSet()

        assertTrue(ids.containsAll(listOf("running", "queued", "unknown")))
        assertEquals(4, ids.size)
        assertEquals(setOf("running", "queued", "unknown", "done-2"), ids)
    }

    @Test
    fun finishedAtDeterminesOldestTerminalRecord() = runTest {
        val store = TaskStore(filesDir, json, maxTerminalRecords = 1)
        store.save(
            "server-a",
            record("late-submit-old-finish", TaskStatus.SUCCESS, submittedAt = 100)
                .copy(finishedAt = 10),
        )
        store.save(
            "server-a",
            record("early-submit-new-finish", TaskStatus.SUCCESS, submittedAt = 1)
                .copy(finishedAt = 50),
        )

        assertEquals("early-submit-new-finish", store.list("server-a").single().promptId)
    }

    @Test
    fun indexHitReturnsWithoutReadingBackingFile() = runTest {
        val store = TaskStore(filesDir, json, maxTerminalRecords = 10)
        store.save("server-a", record("p1", TaskStatus.RUNNING, submittedAt = 1))
        val file = File(filesDir, "servers/server-a/tasks/p1.json")
        // 破坏 JSON 但保留文件：索引命中时不应再解析文件。
        file.writeText("{broken")

        assertEquals("p1", store.get("server-a", "p1")?.promptId)
    }

    @Test
    fun indexAndFilesStayConsistentAfterDeleteAndReload() = runTest {
        val store = TaskStore(filesDir, json, maxTerminalRecords = 10)
        store.save("server-a", record("p1", TaskStatus.SUCCESS, submittedAt = 1))
        store.save("server-a", record("p2", TaskStatus.QUEUED, submittedAt = 2))
        store.delete("server-a", "p1")

        assertEquals(listOf("p2"), store.list("server-a").map { it.promptId })
        val reloaded = TaskStore(filesDir, json, maxTerminalRecords = 10)
        assertEquals(listOf("p2"), reloaded.list("server-a").map { it.promptId })
        assertFalse(File(filesDir, "servers/server-a/tasks/p1.json").exists())
    }

    private fun record(promptId: String, status: String, submittedAt: Long) = TaskRecord(
        promptId = promptId,
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "c",
        submittedAt = submittedAt,
        status = status,
    )
}
