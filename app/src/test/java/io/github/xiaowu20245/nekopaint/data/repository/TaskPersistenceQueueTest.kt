package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskPersistenceQueueTest {

    private val identity = ServerIdentity("server", "http://a:8188")

    @Test
    fun coalescesLatestRecordPerTaskAndWritesInOrder() = runTest {
        val written = mutableListOf<TaskRecord>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> true },
            writer = { _, record -> written += record },
        )

        queue.enqueue("server", record("p1", "queued"), identity, 1)
        queue.enqueue("server", record("p1", "running"), identity, 1)
        queue.enqueue("server", record("p2", "queued"), identity, 1)
        advanceUntilIdle()

        assertEquals(2, written.size)
        assertEquals("running", written.first { it.promptId == "p1" }.status)
        assertEquals("queued", written.first { it.promptId == "p2" }.status)
    }

    @Test
    fun staleGenerationIsNotWritten() = runTest {
        val written = mutableListOf<TaskRecord>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, generation -> generation == 2L },
            writer = { _, record -> written += record },
        )

        queue.enqueue("server", record("p1", "running"), identity, 1)
        advanceUntilIdle()

        assertEquals(0, written.size)
    }

    @Test
    fun writerFailureDoesNotStopLaterTasks() = runTest {
        val written = mutableListOf<String>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> true },
            writer = { _, record ->
                if (record.promptId == "bad") error("disk full")
                written += record.promptId
            },
        )

        queue.enqueue("server", record("bad", "running"), identity, 1)
        queue.enqueue("server", record("good", "running"), identity, 1)
        advanceUntilIdle()

        assertEquals(listOf("good"), written)
    }

    @Test
    fun flushWritesStaleGenerationRecords() = runTest {
        val written = mutableListOf<String>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> false },
            writer = { _, record -> written += record.promptId },
        )

        queue.enqueue("server", record("p1", "running"), identity, 1)
        advanceUntilIdle()
        assertEquals(0, written.size)

        queue.flush()

        assertEquals(listOf("p1"), written)
    }

    @Test
    fun shutdownFlushesFinalSnapshotAndRejectsNewRecords() = runTest {
        val written = mutableListOf<String>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> false },
            writer = { _, record -> written += "${record.promptId}:${record.status}" },
        )

        queue.enqueue("server", record("p1", "running"), identity, 1)
        queue.enqueue("server", record("p1", "success"), identity, 1)
        queue.shutdown()

        assertEquals(listOf("p1:success"), written)
        queue.enqueue("server", record("p2", "running"), identity, 1)
        queue.flush()
        assertEquals(listOf("p1:success"), written)
    }

    @Test
    fun shutdownFlushesEvenWhenConsumerScopeWasCancelled() = runTest {
        val written = mutableListOf<String>()
        val consumer = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = TaskPersistenceQueue(
            scope = consumer,
            isCurrent = { _, _ -> true },
            writer = { _, record -> written += record.promptId },
        )

        queue.enqueue("server", record("p1", "running"), identity, 1)
        consumer.cancel()
        queue.shutdown()

        assertEquals(listOf("p1"), written)
    }

    @Test
    fun retriesWriterOnceAndReportsOnlyPermanentFailure() = runTest {
        val attempts = mutableMapOf<String, Int>()
        val failures = mutableListOf<String>()
        val written = mutableListOf<String>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> true },
            writer = { _, record ->
                val attempt = (attempts[record.promptId] ?: 0) + 1
                attempts[record.promptId] = attempt
                when {
                    record.promptId == "flaky" && attempt == 1 -> error("transient")
                    record.promptId == "broken" -> error("disk full")
                    else -> written += record.promptId
                }
            },
            onWriteError = { _, record, _ -> failures += record.promptId },
        )

        queue.enqueue("server", record("flaky", "success"), identity, 1)
        queue.enqueue("server", record("broken", "success"), identity, 1)
        queue.enqueue("server", record("good", "success"), identity, 1)
        advanceUntilIdle()

        assertEquals(2, attempts["flaky"])
        assertEquals(2, attempts["broken"])
        assertEquals(listOf("flaky", "good"), written)
        assertEquals(listOf("broken"), failures)
    }

    @Test
    fun maxPendingEvictsOldestOnlyForNewKeys() = runTest {
        val written = mutableListOf<String>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> true },
            writer = { _, record -> written += record.promptId },
        )

        (1..600).forEach { index ->
            queue.enqueue("server", record("p$index", "running"), identity, 1)
        }
        queue.flush()

        assertEquals(512, written.size)
        assertEquals("p89", written.first())
        assertEquals("p600", written.last())
    }

    @Test
    fun updatingExistingKeyAtCapacityDoesNotEvictOtherTasks() = runTest {
        val written = mutableListOf<TaskRecord>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> true },
            writer = { _, record -> written += record },
        )

        (1..512).forEach { index ->
            queue.enqueue("server", record("p$index", "queued"), identity, 1)
        }
        queue.enqueue("server", record("p1", "success"), identity, 1)
        queue.flush()

        assertEquals(512, written.size)
        assertEquals("success", written.first { it.promptId == "p1" }.status)
        assertEquals("queued", written.first { it.promptId == "p512" }.status)
    }

    @Test
    fun flushRacingWithEnqueueKeepsLatestRecordPerTask() = runTest {
        val written = mutableListOf<TaskRecord>()
        val queue = TaskPersistenceQueue(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            isCurrent = { _, _ -> true },
            writer = { _, record -> written += record },
        )

        coroutineScope {
            (1..50).map { index ->
                async(Dispatchers.Default) {
                    queue.enqueue("server", record("p$index", "running"), identity, 1)
                    queue.flush()
                    queue.enqueue("server", record("p$index", "success"), identity, 1)
                }
            }.awaitAll()
        }
        queue.flush()

        val latest = written.groupBy { it.promptId }.mapValues { it.value.last().status }
        (1..50).forEach { index ->
            assertEquals("success", latest["p$index"])
        }
    }

    private fun record(promptId: String, status: String) = TaskRecord(
        promptId = promptId,
        serverId = "server",
        templateId = "t",
        templateName = "template",
        clientId = "c",
        submittedAt = 1L,
        status = status,
    )
}
