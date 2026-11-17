package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRecordRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var filesDir: File
    private lateinit var repository: TaskRecordRepository

    @Before
    fun setUp() {
        filesDir = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-task-record-${System.nanoTime()}",
        ).apply { mkdirs() }
        repository = TaskRecordRepository(TaskStore(filesDir, json))
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun upsertWritesAndNotifiesObserver() = runTest {
        val observed = mutableListOf<String>()
        repository.addObserver(object : TaskRecordObserver {
            override fun onTaskRecord(
                serverId: String,
                record: TaskRecord,
                sequence: Long,
            ) {
                observed += record.promptId
            }
        })

        val result = repository.upsert("server-a", record("p1"))

        assertTrue(result.isSuccess)
        assertNotNull(repository.get("server-a", "p1"))
        assertEquals(listOf("p1"), observed)
        assertTrue(repository.writeState.value is TaskRecordWriteState.Saved)
    }

    @Test
    fun blankServerIdIsNormalizedBeforeObserver() = runTest {
        var observedServerId: String? = null
        repository.addObserver(object : TaskRecordObserver {
            override fun onTaskRecord(
                serverId: String,
                record: TaskRecord,
                sequence: Long,
            ) {
                observedServerId = record.serverId
            }
        })

        repository.upsert("server-a", record("p1").copy(serverId = ""))

        assertEquals("server-a", observedServerId)
        assertEquals("server-a", repository.get("server-a", "p1")?.serverId)
    }

    @Test
    fun mismatchedServerIdIsNormalizedToStorageScope() = runTest {
        repository.upsert("server-a", record("p1").copy(serverId = "server-b"))

        assertEquals("server-a", repository.get("server-a", "p1")?.serverId)
        assertNull(repository.get("server-b", "p1"))
    }

    @Test
    fun deleteRemovesRecordAndMissingDeleteSucceeds() = runTest {
        var deleted: String? = null
        repository.addObserver(object : TaskRecordObserver {
            override fun onTaskRecordDeleted(
                serverId: String,
                promptId: String,
                sequence: Long,
            ) {
                deleted = promptId
            }
        })
        repository.upsert("server-a", record("p1"))

        assertTrue(repository.delete("server-a", "p1").isSuccess)
        assertNull(repository.get("server-a", "p1"))
        assertEquals("p1", deleted)
        assertTrue(repository.writeState.value is TaskRecordWriteState.Deleted)
        assertTrue(repository.delete("server-a", "missing").isSuccess)
    }

    @Test
    fun deleteBlocksStaleUpsertOfSameTask() = runTest {
        repository.upsert("server-a", record("p1"))
        assertTrue(repository.delete("server-a", "p1").isSuccess)

        val stale = repository.upsert("server-a", record("p1"))

        assertTrue(stale.isFailure)
        assertNull(repository.get("server-a", "p1"))
    }

    @Test
    fun differentPromptIdCanBeWrittenAfterDelete() = runTest {
        repository.upsert("server-a", record("old"))
        repository.delete("server-a", "old")

        assertTrue(repository.upsert("server-a", record("new")).isSuccess)
        assertNotNull(repository.get("server-a", "new"))
    }

    @Test
    fun prepareRetryRejectsRecordAlreadyQueued() = runTest {
        repository.upsert("server-a", record("p1"))
        repository.upsert("server-a", record("p1").copy(status = TaskStatus.QUEUED))

        val result = repository.prepareRetry("server-a", record("p1"))

        assertTrue(result.isFailure)
        assertEquals(TaskStatus.QUEUED, repository.get("server-a", "p1")?.status)
    }

    @Test
    fun prepareRetryRejectsSupersededOrMigratedRecord() = runTest {
        repository.upsert("server-a", record("old"))
        repository.upsert(
            "server-a",
            record("new").copy(
                status = TaskStatus.QUEUED,
                requestedPromptId = "old",
            ),
        )

        val result = repository.prepareRetry("server-a", record("old"))

        assertTrue(result.isFailure)
        assertEquals(TaskStatus.SUBMITTING, repository.get("server-a", "old")?.status)
        assertNull(repository.get("server-a", "old")?.supersededBy)
    }

    @Test
    fun prepareRetryRejectsPersistedSupersededMarker() = runTest {
        repository.upsert(
            "server-a",
            record("old").copy(
                status = TaskStatus.UNKNOWN,
                supersededBy = "new",
            ),
        )

        val result = repository.prepareRetry("server-a", record("old"))

        assertTrue(result.isFailure)
        assertEquals("new", repository.get("server-a", "old")?.supersededBy)
    }

    @Test
    fun prepareRetryDoesNotTreatMigratedNewRecordAsPointingBackToOld() = runTest {
        repository.upsert("server-a", record("old"))
        repository.upsert(
            "server-a",
            record("new").copy(
                status = TaskStatus.UNKNOWN,
                requestedPromptId = "old",
            ),
        )

        val result = repository.prepareRetry(
            "server-a",
            record("new").copy(
                status = TaskStatus.SUBMITTING,
                requestedPromptId = "old",
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(TaskStatus.SUBMITTING, repository.get("server-a", "new")?.status)
    }

    @Test
    fun prepareRetryAllowsUnknownRecord() = runTest {
        repository.upsert("server-a", record("p1").copy(status = TaskStatus.UNKNOWN))

        val result = repository.prepareRetry("server-a", record("p1"))

        assertTrue(result.isSuccess)
        assertEquals(TaskStatus.SUBMITTING, repository.get("server-a", "p1")?.status)
    }

    @Test
    fun reloadRecoveryPersistsNormalizedUnknown() = runTest {
        repository.upsert("server-a", record("p1"))
        val stored = repository.list("server-a")

        val reloaded = TaskListSyncPolicy.reloadWithRecovery(
            stored = stored,
            live = emptyList(),
            now = 40_000L,
        )
        reloaded.changed.forEach { record ->
            assertTrue(repository.upsert("server-a", record).isSuccess)
        }

        assertEquals(TaskStatus.UNKNOWN, repository.get("server-a", "p1")?.status)
    }

    @Test
    fun writeFailureIsVisibleInState() = runTest {
        val target = File(filesDir, "servers/server-a/tasks/p1.json")
        target.mkdirs()

        val result = repository.upsert("server-a", record("p1"))

        assertTrue(result.isFailure)
        val state = repository.writeState.value
        assertTrue(state is TaskRecordWriteState.Failed)
        assertEquals("p1", (state as TaskRecordWriteState.Failed).promptId)
    }

    private fun record(promptId: String) = TaskRecord(
        promptId = promptId,
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "client",
        submittedAt = 1L,
        status = TaskStatus.SUBMITTING,
        requestedAt = 1L,
        requestedPromptId = promptId,
        statusUpdatedAt = 1L,
        statusSource = TaskStatusSource.LOCAL.name,
    )
}
