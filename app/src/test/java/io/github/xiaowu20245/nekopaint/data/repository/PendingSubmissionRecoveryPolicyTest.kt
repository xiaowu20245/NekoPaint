package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PendingSubmissionRecoveryPolicyTest {

    @Test
    fun staleSubmittingRecordBecomesUnknown() {
        val record = record(status = TaskStatus.SUBMITTING, requestedAt = 1_000L)

        val normalized = PendingSubmissionRecoveryPolicy.normalize(
            record = record,
            now = 1_000L + PendingSubmissionRecoveryPolicy.STALE_SUBMITTING_MS,
        )

        assertEquals(TaskStatus.UNKNOWN, normalized.status)
        assertEquals(1_000L + PendingSubmissionRecoveryPolicy.STALE_SUBMITTING_MS, normalized.statusUpdatedAt)
        assertEquals(TaskStatusSource.LOCAL.name, normalized.statusSource)
    }

    @Test
    fun freshSubmittingRecordIsLeftAlone() {
        val record = record(status = TaskStatus.SUBMITTING, requestedAt = 5_000L)

        val normalized = PendingSubmissionRecoveryPolicy.normalize(
            record = record,
            now = 5_000L + PendingSubmissionRecoveryPolicy.STALE_SUBMITTING_MS - 1,
        )

        assertSame(record, normalized)
    }

    @Test
    fun queuedAndUnknownRecordsAreNotRewritten() {
        val queued = record(status = TaskStatus.QUEUED, requestedAt = 1L)
        val unknown = record(status = TaskStatus.UNKNOWN, requestedAt = 1L)
        val now = Long.MAX_VALUE

        assertSame(queued, PendingSubmissionRecoveryPolicy.normalize(queued, now))
        assertSame(unknown, PendingSubmissionRecoveryPolicy.normalize(unknown, now))
    }

    @Test
    fun normalizeAllPreservesOrderAndOnlyChangesStaleSubmitting() {
        val stale = record(status = TaskStatus.SUBMITTING, requestedAt = 1L)
        val fresh = record(status = TaskStatus.SUBMITTING, requestedAt = 20_000L)
        val queued = record(status = TaskStatus.QUEUED, requestedAt = 1L)

        val normalized = PendingSubmissionRecoveryPolicy.normalizeAll(
            records = listOf(stale, fresh, queued),
            now = 40_000L,
        )

        assertEquals(
            listOf(TaskStatus.UNKNOWN, TaskStatus.SUBMITTING, TaskStatus.QUEUED),
            normalized.map { it.status },
        )
    }

    private fun record(status: String, requestedAt: Long) = TaskRecord(
        promptId = "p1",
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "client",
        submittedAt = 1L,
        status = status,
        requestedAt = requestedAt,
        requestedPromptId = "p1",
        statusUpdatedAt = 1L,
        statusSource = TaskStatusSource.LOCAL.name,
    )
}
