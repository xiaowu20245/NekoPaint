package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRecordEventDispatcherTest {

    @Test
    fun flushCompletesAfterPriorEvents() = runTest {
        val handled = mutableListOf<Long>()
        val dispatcher = TaskRecordEventDispatcher(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            handler = { event -> handled += event.sequence },
        )
        dispatcher.offer(TaskRecordEvent.Persisted("s", "p1", 1, record("p1")))

        assertTrue(dispatcher.flush())

        assertEquals(listOf(1L), handled)
    }

    @Test
    fun flushTimesOutWhenHandlerIsBlocked() = runTest {
        val dispatcher = TaskRecordEventDispatcher(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            handler = { delay(10_000) },
        )
        dispatcher.offer(TaskRecordEvent.Persisted("s", "p1", 1, record("p1")))

        assertFalse(dispatcher.flush(timeoutMs = 100))
    }

    @Test
    fun closedDispatcherRejectsEventsAndFlush() = runTest {
        val handled = mutableListOf<Long>()
        val dispatcher = TaskRecordEventDispatcher(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            handler = { event -> handled += event.sequence },
        )
        dispatcher.close()
        dispatcher.offer(TaskRecordEvent.Persisted("s", "p1", 1, record("p1")))

        assertFalse(dispatcher.flush())
        advanceUntilIdle()
        assertTrue(handled.isEmpty())
    }

    @Test
    fun preservesRepositoryWriteOrderIncludingDeletes() = runTest {
        val handled = mutableListOf<String>()
        val dispatcher = TaskRecordEventDispatcher(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            handler = { event ->
                handled += when (event) {
                    is TaskRecordEvent.Persisted -> "upsert:${event.promptId}:${event.sequence}"
                    is TaskRecordEvent.Deleted -> "delete:${event.promptId}:${event.sequence}"
                }
            },
        )

        dispatcher.offer(
            TaskRecordEvent.Persisted("s", "p1", 1, record("p1")),
        )
        dispatcher.offer(TaskRecordEvent.Deleted("s", "p1", 2))
        dispatcher.offer(
            TaskRecordEvent.Persisted("s", "p1", 3, record("p1")),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("upsert:p1:1", "delete:p1:2", "upsert:p1:3"),
            handled,
        )
    }

    private fun record(promptId: String) = TaskRecord(
        promptId = promptId,
        serverId = "s",
        templateId = "t",
        templateName = "template",
        clientId = "c",
        submittedAt = 1L,
    )
}
