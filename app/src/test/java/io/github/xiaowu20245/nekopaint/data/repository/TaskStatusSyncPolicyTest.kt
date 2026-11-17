package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskStatusSyncPolicyTest {

    @Test
    fun runningIsNotOverwrittenByOlderDiskQueued() {
        val local = record(TaskStatus.RUNNING, time = 200, source = TaskStatusSource.WS)
        val disk = record(TaskStatus.QUEUED, time = 100, source = TaskStatusSource.DISK)

        val merged = TaskStatusSyncPolicy.merge(local, disk, TaskStatusSource.DISK)

        assertEquals(TaskStatus.RUNNING, merged.status)
    }

    @Test
    fun unknownIsNotOverwrittenByOldDiskQueued() {
        val local = record(
            TaskStatus.UNKNOWN,
            time = 200,
            source = TaskStatusSource.LOCAL,
            requestedAt = 200,
        )
        val disk = record(TaskStatus.QUEUED, time = 100, source = TaskStatusSource.DISK)

        val merged = TaskStatusSyncPolicy.merge(local, disk, TaskStatusSource.DISK)

        assertEquals(TaskStatus.UNKNOWN, merged.status)
    }

    @Test
    fun unknownAdvancesWhenQueueSeesTaskLater() {
        val local = record(
            TaskStatus.UNKNOWN,
            time = 200,
            source = TaskStatusSource.LOCAL,
            requestedAt = 200,
        )
        val queue = record(TaskStatus.QUEUED, time = 300, source = TaskStatusSource.QUEUE)

        val merged = TaskStatusSyncPolicy.merge(local, queue, TaskStatusSource.QUEUE)

        assertEquals(TaskStatus.QUEUED, merged.status)
    }

    @Test
    fun queuedAdvancesToRunningFromWebSocket() {
        val local = record(TaskStatus.QUEUED, time = 100, source = TaskStatusSource.LOCAL)
        val ws = record(TaskStatus.RUNNING, time = 200, source = TaskStatusSource.WS)

        val merged = TaskStatusSyncPolicy.merge(local, ws, TaskStatusSource.WS)

        assertEquals(TaskStatus.RUNNING, merged.status)
    }

    @Test
    fun terminalIsNotOverwrittenByNonTerminal() {
        val local = record(TaskStatus.SUCCESS, time = 100, source = TaskStatusSource.WS)
        val queue = record(TaskStatus.RUNNING, time = 200, source = TaskStatusSource.QUEUE)

        val merged = TaskStatusSyncPolicy.merge(local, queue, TaskStatusSource.QUEUE)

        assertEquals(TaskStatus.SUCCESS, merged.status)
    }

    @Test
    fun newerHistoryTerminalWins() {
        val local = record(TaskStatus.SUCCESS, time = 100, source = TaskStatusSource.WS)
        val history = record(
            TaskStatus.FAILED,
            time = 200,
            source = TaskStatusSource.HISTORY,
            errorMessage = "failed",
        )

        val merged = TaskStatusSyncPolicy.merge(local, history, TaskStatusSource.HISTORY)

        assertEquals(TaskStatus.FAILED, merged.status)
        assertEquals("failed", merged.errorMessage)
    }

    @Test
    fun olderHistoryTerminalDoesNotOverwriteNewerLocalTerminal() {
        val local = record(TaskStatus.FAILED, time = 300, source = TaskStatusSource.WS)
        val history = record(TaskStatus.SUCCESS, time = 100, source = TaskStatusSource.HISTORY)

        val merged = TaskStatusSyncPolicy.merge(local, history, TaskStatusSource.HISTORY)

        assertEquals(TaskStatus.FAILED, merged.status)
    }

    @Test
    fun successClearsPreviousError() {
        val local = record(
            TaskStatus.RUNNING,
            time = 100,
            source = TaskStatusSource.WS,
            errorMessage = "old error",
        )
        val success = record(TaskStatus.SUCCESS, time = 200, source = TaskStatusSource.HISTORY)

        val merged = TaskStatusSyncPolicy.merge(local, success, TaskStatusSource.HISTORY)

        assertEquals(TaskStatus.SUCCESS, merged.status)
        assertNull(merged.errorMessage)
    }

    @Test
    fun equalTimestampDiskDoesNotReplaceLocalView() {
        val local = record(
            TaskStatus.RUNNING,
            time = 100,
            source = TaskStatusSource.LOCAL,
            errorMessage = "local",
        )
        val disk = record(
            TaskStatus.RUNNING,
            time = 100,
            source = TaskStatusSource.DISK,
            errorMessage = "disk",
        )

        val merged = TaskStatusSyncPolicy.merge(local, disk, TaskStatusSource.DISK)

        assertEquals("local", merged.errorMessage)
        assertEquals(TaskStatusSource.LOCAL.name, merged.statusSource)
    }

    @Test
    fun newerLocalTerminalKeepsItsFinishedTimeAndError() {
        val local = record(
            TaskStatus.FAILED,
            time = 300,
            source = TaskStatusSource.WS,
            errorMessage = "new error",
            finishedAt = 300,
        )
        val disk = record(
            TaskStatus.FAILED,
            time = 100,
            source = TaskStatusSource.DISK,
            errorMessage = "old error",
            finishedAt = 100,
        )

        val merged = TaskStatusSyncPolicy.merge(local, disk, TaskStatusSource.DISK)

        assertEquals(300L, merged.finishedAt)
        assertEquals("new error", merged.errorMessage)
        assertEquals(TaskStatusSource.WS.name, merged.statusSource)
    }

    @Test
    fun newerLocalSuccessKeepsItsFinishedTime() {
        val local = record(
            TaskStatus.SUCCESS,
            time = 300,
            source = TaskStatusSource.WS,
            finishedAt = 300,
        )
        val disk = record(
            TaskStatus.SUCCESS,
            time = 100,
            source = TaskStatusSource.DISK,
            finishedAt = 100,
        )

        val merged = TaskStatusSyncPolicy.merge(local, disk, TaskStatusSource.DISK)

        assertEquals(300L, merged.finishedAt)
        assertEquals(TaskStatus.SUCCESS, merged.status)
    }

    private fun record(
        status: String,
        time: Long,
        source: TaskStatusSource,
        requestedAt: Long? = null,
        errorMessage: String? = null,
        finishedAt: Long? = null,
    ) = TaskRecord(
        promptId = "p1",
        serverId = "server",
        templateId = "template",
        templateName = "Template",
        clientId = "client",
        submittedAt = time,
        status = status,
        requestedAt = requestedAt,
        statusUpdatedAt = time,
        statusSource = source.name,
        errorMessage = errorMessage,
        finishedAt = finishedAt,
    )
}
