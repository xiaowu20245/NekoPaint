package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMigrationPolicyTest {

    @Test
    fun persistedSupersededByWinsWithoutReadModelSibling() {
        val old = task(promptId = "old", status = TaskStatus.UNKNOWN, supersededBy = "new-long-id")

        val migratedTo = QueueMigrationPolicy.migratedTo(old, tasks = emptyList())

        assertEquals("new-long-id", migratedTo)
        assertFalse(QueueMigrationPolicy.canRetry(old, migratedTo))
    }

    @Test
    fun requestedPromptIdSiblingIsUsedAsFallback() {
        val old = task(promptId = "old", status = TaskStatus.UNKNOWN)
        val migrated = task(
            promptId = "new",
            status = TaskStatus.QUEUED,
            requestedPromptId = "old",
        )

        val migratedTo = QueueMigrationPolicy.migratedTo(old, listOf(old, migrated))

        assertEquals("new", migratedTo)
        assertFalse(QueueMigrationPolicy.canRetry(old, migratedTo))
    }

    @Test
    fun migratedNewRecordDoesNotShowMigratedBackToOldId() {
        val old = task(promptId = "old", status = TaskStatus.UNKNOWN)
        val migratedNew = task(
            promptId = "new",
            status = TaskStatus.UNKNOWN,
            requestedPromptId = "old",
        )

        assertNull(QueueMigrationPolicy.migratedTo(migratedNew, listOf(old, migratedNew)))
        assertTrue(QueueMigrationPolicy.canRetry(migratedNew, null))
    }

    @Test
    fun normalUnknownRecordCanRetry() {
        val task = task(promptId = "p1", status = TaskStatus.UNKNOWN)

        assertNull(QueueMigrationPolicy.migratedTo(task, listOf(task)))
        assertTrue(QueueMigrationPolicy.canRetry(task, null))
    }

    @Test
    fun queuedRecordCannotRetryEvenWithoutMigration() {
        val task = task(promptId = "p1", status = TaskStatus.QUEUED)

        assertFalse(QueueMigrationPolicy.canRetry(task, null))
    }

    private fun task(
        promptId: String,
        status: String,
        requestedPromptId: String = promptId,
        supersededBy: String? = null,
    ) = TaskRecord(
        promptId = promptId,
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "client",
        submittedAt = 1L,
        status = status,
        requestedAt = 1L,
        requestedPromptId = requestedPromptId,
        statusUpdatedAt = 1L,
        statusSource = "LOCAL",
        supersededBy = supersededBy,
    )
}
