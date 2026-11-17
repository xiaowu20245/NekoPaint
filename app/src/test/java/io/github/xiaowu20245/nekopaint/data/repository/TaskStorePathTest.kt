package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
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

class TaskStorePathTest {

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
            "nekopaint-task-store-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun safePromptIdRoundTripsWithOriginalName() = runTest {
        val store = TaskStore(filesDir, json)
        val record = record("prompt-123", "server-a")

        store.save("server-a", record)

        val loaded = store.get("server-a", "prompt-123")
        assertEquals("prompt-123", loaded?.promptId)
        assertTrue(File(filesDir, "servers/server-a/tasks/prompt-123.json").exists())
    }

    @Test
    fun traversalPromptIdStaysInsideTaskDirectory() = runTest {
        val store = TaskStore(filesDir, json)
        val record = record("../../evil", "server-a")

        store.save("server-a", record)

        val encoded = SafeFileNames.encode("../../evil")
        assertTrue(File(filesDir, "servers/server-a/tasks/$encoded.json").exists())
        assertFalse(File(filesDir.parentFile, "evil.json").exists())
        assertFalse(File(filesDir, "evil.json").exists())
        assertEquals("../../evil", store.get("server-a", "../../evil")?.promptId)
        assertEquals("../../evil", store.list("server-a").single().promptId)
    }

    @Test
    fun traversalServerIdIsHashedAndContentKeepsOriginalServer() = runTest {
        val store = TaskStore(filesDir, json)
        val record = record("p1", "../server")

        store.save("../server", record)

        val encoded = SafeFileNames.encode("../server")
        assertTrue(File(filesDir, "servers/$encoded/tasks/p1.json").exists())
        assertEquals("../server", store.get("../server", "p1")?.serverId)
        assertEquals("../server", store.listAll().single().serverId)
    }

    @Test
    fun listReadsPromptIdFromContentNotFileName() = runTest {
        val dir = File(filesDir, "servers/server-a/tasks").apply { mkdirs() }
        File(dir, "${SafeFileNames.encode("../../evil")}.json").writeText(
            json.encodeToString(record("../../evil", "server-a")),
        )

        val listed = TaskStore(filesDir, json).list("server-a").single()

        assertEquals("../../evil", listed.promptId)
    }

    @Test
    fun legacyDefaultTasksAreMigrated() = runTest {
        val legacy = File(filesDir, "tasks").apply { mkdirs() }
        File(legacy, "legacy-prompt.json").writeText(
            json.encodeToString(record("legacy-prompt", "default")),
        )

        val store = TaskStore(filesDir, json)
        val loaded = store.get("default", "legacy-prompt")

        assertEquals("legacy-prompt", loaded?.promptId)
        assertTrue(
            File(filesDir, "servers/default/tasks/legacy-prompt.json").exists(),
        )
    }

    @Test
    fun corruptAndInvalidFilesAreIgnored() = runTest {
        val dir = File(filesDir, "servers/server-a/tasks").apply { mkdirs() }
        File(dir, "broken.json").writeText("{broken")
        File(dir, "note.txt").writeText("ignored")
        File(dir, "good.json").writeText(
            json.encodeToString(record("good", "server-a")),
        )

        val listed = TaskStore(filesDir, json).list("server-a")

        assertEquals(listOf("good"), listed.map { it.promptId })
    }

    @Test
    fun legacyUuidFileNameRemainsReadable() = runTest {
        val uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        val dir = File(filesDir, "servers/server-a/tasks").apply { mkdirs() }
        File(dir, "$uuid.json").writeText(
            json.encodeToString(record(uuid, "server-a")),
        )

        val loaded = TaskStore(filesDir, json).get("server-a", uuid)

        assertEquals(uuid, loaded?.promptId)
        assertTrue(File(dir, "$uuid.json").exists())
    }

    @Test
    fun oldUnsafeFileNameIsNotUsedAsFallback() = runTest {
        val dir = File(filesDir, "servers/server-a/tasks").apply { mkdirs() }
        File(dir, "bad prompt.json").writeText(
            json.encodeToString(record("bad prompt", "server-a")),
        )

        val store = TaskStore(filesDir, json)

        assertEquals(null, store.get("server-a", "bad prompt"))
        // list 只依赖 JSON 内容，不反推文件名；因此仍能看到该记录。
        assertEquals("bad prompt", store.list("server-a").single().promptId)
    }

    @Test
    fun listRejectsMismatchedServerIdButListAllKeepsContentValue() = runTest {
        val dir = File(filesDir, "servers/server-a/tasks").apply { mkdirs() }
        File(dir, "wrong-server.json").writeText(
            json.encodeToString(record("wrong-server", "server-b")),
        )

        val store = TaskStore(filesDir, json)

        assertTrue(store.list("server-a").isEmpty())
        assertEquals("server-b", store.listAll().single().serverId)
    }

    @Test
    fun hashedDirectoryDoesNotLeakIntoBlankServerIdFallback() = runTest {
        val hashed = SafeFileNames.encode("../server")
        val dir = File(filesDir, "servers/$hashed/tasks").apply { mkdirs() }
        File(dir, "blank.json").writeText(
            json.encodeToString(record("blank", "")),
        )

        val loaded = TaskStore(filesDir, json).listAll().single()

        assertEquals("", loaded.serverId)
    }

    @Test
    fun requestedPromptFieldsSurviveRoundTrip() = runTest {
        val store = TaskStore(filesDir, json)
        val record = record("p1", "server-a").copy(
            status = TaskStatus.UNKNOWN,
            requestedAt = 11L,
            requestedPromptId = "requested-p1",
            statusUpdatedAt = 12L,
            statusSource = TaskStatusSource.LOCAL.name,
        )

        store.save("server-a", record)
        val loaded = store.get("server-a", "p1")

        assertEquals(TaskStatus.UNKNOWN, loaded?.status)
        assertEquals(11L, loaded?.requestedAt)
        assertEquals("requested-p1", loaded?.requestedPromptId)
        assertEquals(12L, loaded?.statusUpdatedAt)
        assertEquals(TaskStatusSource.LOCAL.name, loaded?.statusSource)
    }

    @Test
    fun deleteRemovesRecordAndMissingDeleteSucceeds() = runTest {
        val store = TaskStore(filesDir, json)
        store.save("server-a", record("p1", "server-a"))

        assertTrue(store.delete("server-a", "p1").isSuccess)
        assertEquals(null, store.get("server-a", "p1"))
        assertTrue(store.delete("server-a", "missing").isSuccess)
    }

    @Test
    fun concurrentUpdateAndDeleteDoNotCorruptRecord() = runTest {
        val store = TaskStore(filesDir, json)
        coroutineScope {
            (1..24).map { index ->
                async(Dispatchers.Default) {
                    if (index % 2 == 0) {
                        store.save("server-a", record("p1", "server-a"))
                    } else {
                        store.delete("server-a", "p1")
                    }
                }
            }.awaitAll()
        }

        val loaded = store.get("server-a", "p1")
        if (loaded != null) {
            assertEquals("p1", loaded.promptId)
        }
        assertTrue(store.list("server-a").all { it.promptId == "p1" })
        assertTrue(
            File(filesDir, "servers/server-a/tasks")
                .listFiles()
                .orEmpty()
                .none { it.name.endsWith(".tmp") },
        )
    }

    private fun record(promptId: String, serverId: String) = TaskRecord(
        promptId = promptId,
        serverId = serverId,
        templateId = "t",
        templateName = "template",
        clientId = "c",
        submittedAt = 1L,
        status = "queued",
    )
}
