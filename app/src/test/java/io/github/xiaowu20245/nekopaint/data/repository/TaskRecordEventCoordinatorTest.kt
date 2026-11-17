package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRecordEventCoordinatorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var filesDir: File
    private lateinit var repository: TaskRecordRepository
    private lateinit var tasks: MutableStateFlow<List<TaskRecord>>
    private lateinit var coordinator: TaskRecordEventCoordinator
    private val stateMutex = Mutex()
    private var serverId = "server-a"
    private var localClientId: String? = "client"
    private var queue: QueueSnapshot? = null
    private val persisted = mutableListOf<TaskRecord>()

    @Before
    fun setUp() {
        filesDir = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-event-coordinator-${System.nanoTime()}",
        ).apply { mkdirs() }
        repository = TaskRecordRepository(TaskStore(filesDir, json))
        tasks = MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    private fun createCoordinator(scope: CoroutineScope) {
        coordinator = TaskRecordEventCoordinator(
            scope = scope,
            repository = repository,
            stateMutex = stateMutex,
            tasks = tasks,
            currentServerId = { serverId },
            localClientId = { localClientId },
            queueSnapshot = { queue },
            persist = { persisted += it },
            cleanupEnabled = false,
        )
    }

    @Test
    fun updateTaskUpsertsStoredRecordAndPersists() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repository.upsert("server-a", record("p1", TaskStatus.QUEUED))

        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }

        assertEquals(TaskStatus.RUNNING, tasks.value.single().status)
        assertEquals(1, persisted.size)
    }

    @Test
    fun unknownLocalQueueEventIsDeferredUntilClientIdLoads() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        localClientId = null
        queue = QueueSnapshot(
            running = listOf(queueItem("p1", clientId = "client")),
            pending = emptyList(),
            updatedAt = 1L,
        )

        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }

        assertTrue(tasks.value.isEmpty())
        localClientId = "client"
        coordinator.flushPendingUpdates()

        assertEquals(TaskStatus.RUNNING, tasks.value.single().status)
        assertNull(repository.get("server-a", "p1"))
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun updateTaskLockedDoesNotReacquireHeldMutex() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repository.upsert("server-a", record("p1", TaskStatus.QUEUED))

        stateMutex.withLock {
            coordinator.updateTaskLocked("p1", TaskStatusSource.WS) {
                it.copy(status = TaskStatus.RUNNING)
            }
        }

        assertEquals(TaskStatus.RUNNING, tasks.value.single().status)
    }

    @Test
    fun removeFlushesMemoryBeforeReturning() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        createCoordinator(scope)
        repository.upsert("server-a", record("p1", TaskStatus.QUEUED))
        advanceUntilIdle()
        assertEquals(1, tasks.value.size)

        val result = coordinator.remove("server-a", "p1")

        assertTrue(result.isSuccess)
        assertTrue(tasks.value.isEmpty())
        assertNull(repository.get("server-a", "p1"))
    }

    @Test
    fun replaceFlushesNewRecordBeforeReturning() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        createCoordinator(scope)
        repository.upsert("server-a", record("old", TaskStatus.QUEUED))
        advanceUntilIdle()

        val result = coordinator.replace(
            serverId = "server-a",
            oldPromptId = "old",
            record = record("new", TaskStatus.QUEUED),
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("new"), tasks.value.map { it.promptId })
        assertNull(repository.get("server-a", "old"))
        assertEquals("new", repository.get("server-a", "new")?.promptId)
    }

    @Test
    fun samePromptIdReplaceIsRejected() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))

        val result = coordinator.replace(
            serverId = "server-a",
            oldPromptId = "same",
            record = record("same", TaskStatus.QUEUED),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun oldServerEventIsIgnoredAfterSwitch() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        createCoordinator(scope)
        repository.upsert("server-a", record("p1", TaskStatus.QUEUED))
        serverId = "server-b"

        advanceUntilIdle()

        assertTrue(tasks.value.isEmpty())
    }

    @Test
    fun confirmedExternalEventIsDroppedNotBuffered() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        localClientId = "client"
        queue = QueueSnapshot(
            running = listOf(queueItem("p1", clientId = "other")),
            pending = emptyList(),
            updatedAt = 1L,
        )

        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }
        localClientId = "other"
        coordinator.flushPendingUpdates()

        assertTrue(tasks.value.isEmpty())
    }

    @Test
    fun removeDropsPendingEventAndTombstonePreventsReplay() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        localClientId = null
        queue = QueueSnapshot(
            running = listOf(queueItem("p1", clientId = "client")),
            pending = emptyList(),
            updatedAt = 1L,
        )
        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }

        coordinator.remove("server-a", "p1")
        localClientId = "client"
        coordinator.flushPendingUpdates()

        assertTrue(tasks.value.isEmpty())
    }

    @Test
    fun expiredPendingEventIsDropped() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        localClientId = null
        queue = QueueSnapshot(
            running = listOf(queueItem("p1", clientId = "client")),
            pending = emptyList(),
            updatedAt = 1L,
        )
        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }
        localClientId = "client"

        coordinator.flushPendingUpdates(now = Long.MAX_VALUE)

        assertTrue(tasks.value.isEmpty())
    }

    @Test
    fun externalEventIsIgnoredEvenWhenStoredRecordExists() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repository.upsert("server-a", record("p1", TaskStatus.QUEUED))
        localClientId = "client"
        queue = QueueSnapshot(
            running = listOf(queueItem("p1", clientId = "other")),
            pending = emptyList(),
            updatedAt = 1L,
        )

        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }

        assertTrue(tasks.value.isEmpty())
    }

    @Test
    fun prunePendingDropsExpiredEvents() = runTest {
        createCoordinator(CoroutineScope(StandardTestDispatcher(testScheduler)))
        localClientId = null
        queue = QueueSnapshot(
            running = listOf(queueItem("p1", clientId = "client")),
            pending = emptyList(),
            updatedAt = 1L,
        )
        coordinator.updateTask("p1", TaskStatusSource.WS) {
            it.copy(status = TaskStatus.RUNNING)
        }
        localClientId = "client"

        coordinator.prunePending(now = Long.MAX_VALUE)
        coordinator.flushPendingUpdates(now = Long.MAX_VALUE)

        assertTrue(tasks.value.isEmpty())
    }

    @Test
    fun clientIdRulesOnlyAcceptConfirmedLocalEvents() {
        createCoordinator(CoroutineScope(StandardTestDispatcher()))
        val item = queueItem("p1", clientId = "other")

        assertTrue(!coordinator.isConfirmedLocal(null, "client"))
        assertTrue(!coordinator.isConfirmedLocal(queueItem("p1", null), "client"))
        assertTrue(!coordinator.isConfirmedLocal(item, null))
        assertTrue(coordinator.isConfirmedLocal(item, "other"))
        assertTrue(!coordinator.isConfirmedLocal(item, "client"))
    }

    private fun record(promptId: String, status: String) = TaskRecord(
        promptId = promptId,
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "client",
        submittedAt = 1L,
        status = status,
        statusUpdatedAt = 1L,
        statusSource = TaskStatusSource.LOCAL.name,
        requestedAt = 1L,
    )

    private fun queueItem(promptId: String, clientId: String?) = QueueItem(
        number = 1,
        promptId = promptId,
        clientId = clientId,
        prompt = null,
        nodeTitles = emptyMap(),
    )
}
