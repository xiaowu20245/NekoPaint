package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListSyncPolicyTest {

    @Test
    fun reloadKeepsLiveOnlyTasksAndNewerLiveStatus() {
        val stored = listOf(record("p1", TaskStatus.QUEUED, 100))
        val live = listOf(
            record("p1", TaskStatus.RUNNING, 200),
            record("p2", TaskStatus.RUNNING, 300),
        )

        val merged = TaskListSyncPolicy.reload(stored, live)

        assertEquals(setOf("p1", "p2"), merged.map { it.promptId }.toSet())
        assertEquals(TaskStatus.RUNNING, merged.first { it.promptId == "p1" }.status)
    }

    @Test
    fun reloadWithRecoveryMarksStaleSubmittingUnknownAndReportsChanged() {
        val stored = listOf(record("p1", TaskStatus.SUBMITTING, 1_000))

        val result = TaskListSyncPolicy.reloadWithRecovery(
            stored = stored,
            live = emptyList(),
            now = 1_000 + PendingSubmissionRecoveryPolicy.STALE_SUBMITTING_MS,
        )

        assertEquals(TaskStatus.UNKNOWN, result.tasks.single().status)
        assertEquals(1, result.changed.size)
        assertEquals(TaskStatus.UNKNOWN, result.changed.single().status)
    }

    @Test
    fun queueUpdateOnlyReportsChangedTasks() {
        val initial = listOf(record("p1", TaskStatus.QUEUED, 100))
        val snapshot = QueueSnapshot(
            running = listOf(queueItem("p1")),
            pending = emptyList(),
            updatedAt = 200,
        )

        val first = TaskListSyncPolicy.applyQueue(initial, snapshot, now = 200)
        assertEquals(TaskStatus.RUNNING, first.tasks.single().status)
        assertEquals(1, first.changed.size)

        val second = TaskListSyncPolicy.applyQueue(first.tasks, snapshot, now = 300)
        assertTrue(second.changed.isEmpty())
        assertEquals(TaskStatus.RUNNING, second.tasks.single().status)
    }

    @Test
    fun unknownTaskFoundInQueueBecomesQueued() {
        val initial = listOf(record("p1", TaskStatus.UNKNOWN, 100))
        val snapshot = QueueSnapshot(
            running = emptyList(),
            pending = listOf(queueItem("p1")),
            updatedAt = 200,
        )

        val result = TaskListSyncPolicy.applyQueue(initial, snapshot, now = 200)

        assertEquals(TaskStatus.QUEUED, result.tasks.single().status)
        assertEquals(1, result.changed.size)
    }

    @Test
    fun unknownTaskFoundInHistoryBecomesTerminal() {
        val initial = listOf(record("p1", TaskStatus.UNKNOWN, 100))
        val entries = listOf(
            history(
                promptId = "p1",
                status = TaskStatus.SUCCESS,
                finishedAt = 300,
            ),
        )

        val result = TaskListSyncPolicy.applyHistory(initial, entries, now = 400)

        assertEquals(TaskStatus.SUCCESS, result.tasks.single().status)
        assertEquals(1, result.changed.size)
    }

    @Test
    fun historySuccessUpdatesTerminalFieldsAndClearsError() {
        val initial = listOf(
            record(
                "p1",
                TaskStatus.RUNNING,
                100,
                errorMessage = "old",
                startedAt = 50,
            ),
        )
        val entries = listOf(
            history(
                promptId = "p1",
                status = TaskStatus.SUCCESS,
                finishedAt = 300,
            ),
        )

        val result = TaskListSyncPolicy.applyHistory(initial, entries, now = 400)

        val task = result.tasks.single()
        assertEquals(TaskStatus.SUCCESS, task.status)
        assertEquals(300L, task.finishedAt)
        assertNull(task.errorMessage)
        assertEquals(1, result.changed.size)

        val repeat = TaskListSyncPolicy.applyHistory(result.tasks, entries, now = 500)
        assertTrue(repeat.changed.isEmpty())
    }

    @Test
    fun persistedSupersededMarkerSurvivesMerge() {
        val local = record("old", TaskStatus.UNKNOWN, 100)
        val incoming = local.copy(supersededBy = "new", statusUpdatedAt = 50)

        val merged = TaskListSyncPolicy.applyPersisted(listOf(local), incoming)

        assertEquals("new", merged.single().supersededBy)
    }

    @Test
    fun persistedOlderRecordDoesNotOverwriteRunningTask() {
        val tasks = listOf(record("p1", TaskStatus.RUNNING, 200))
        val old = record("p1", TaskStatus.QUEUED, 100)

        val merged = TaskListSyncPolicy.applyPersisted(tasks, old)

        assertEquals(TaskStatus.RUNNING, merged.single().status)
    }

    @Test
    fun webSocketEventBeforeMemoryTaskUpsertsFallback() {
        val fallback = record("p1", TaskStatus.QUEUED, 100)

        val merged = TaskListSyncPolicy.applyEvent(
            tasks = emptyList(),
            promptId = "p1",
            source = TaskStatusSource.WS,
            now = 200,
            fallback = fallback,
            transform = { it.copy(status = TaskStatus.RUNNING) },
        )

        assertEquals(1, merged.size)
        assertEquals(TaskStatus.RUNNING, merged.single().status)
        assertEquals(TaskStatusSource.WS.name, merged.single().statusSource)
    }

    private fun record(
        promptId: String,
        status: String,
        time: Long,
        errorMessage: String? = null,
        startedAt: Long? = null,
    ) = TaskRecord(
        promptId = promptId,
        serverId = "server",
        templateId = "template",
        templateName = "Template",
        clientId = "client",
        submittedAt = time,
        status = status,
        statusUpdatedAt = time,
        statusSource = TaskStatusSource.LOCAL.name,
        errorMessage = errorMessage,
        startedAt = startedAt,
    )

    private fun queueItem(promptId: String) = QueueItem(
        number = 1,
        promptId = promptId,
        clientId = "client",
        prompt = null,
        nodeTitles = emptyMap(),
    )

    private fun history(
        promptId: String,
        status: String,
        finishedAt: Long,
    ) = HistoryEntry(
        promptId = promptId,
        status = status,
        completed = status == TaskStatus.SUCCESS,
        timestamp = finishedAt,
        startedAt = 0L,
        finishedAt = finishedAt,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )
}
