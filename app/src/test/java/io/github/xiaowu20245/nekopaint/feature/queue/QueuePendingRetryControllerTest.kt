package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatus
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatusSource
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueuePendingRetryControllerTest {

    @Test
    fun successNoticeIncludesLocalRecordWarning() = runTest {
        val controller = QueuePendingRetryController(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            retrySubmission = {
                WorkflowSubmitResult.Success(
                    promptId = "new-task-id-123",
                    queueNumber = 1,
                    localRecordWarning = NekoMessage(
                        NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED,
                    ),
                )
            },
            refresh = {},
        )

        controller.retry(task())

        val notice = controller.state.value.notice as QueueNotice.Submitted
        assertEquals("new-task-id-123", notice.promptId)
        assertEquals(
            NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED,
            notice.warning?.key,
        )
        assertNull(controller.state.value.retryingPromptId)
    }

    @Test
    fun unknownNoticeIsShownAfterRetry() = runTest {
        val controller = QueuePendingRetryController(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            retrySubmission = {
                WorkflowSubmitResult.Unknown(
                    "p1",
                    NekoMessage(NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN, listOf("网络断开")),
                )
            },
            refresh = {},
        )

        controller.retry(task())

        val notice = controller.state.value.notice as QueueNotice.Unknown
        assertEquals("p1", notice.promptId)
        assertEquals(NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN, notice.message.key)
        assertNull(controller.state.value.retryingPromptId)
    }

    @Test
    fun exceptionClearsRetryingInFinallyAndShowsFailure() = runTest {
        var refreshed = 0
        val controller = QueuePendingRetryController(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            retrySubmission = { error("磁盘异常") },
            refresh = { refreshed += 1 },
        )

        controller.retry(task())

        val notice = controller.state.value.notice as QueueNotice.Message
        assertEquals(NekoMessageKey.GENERIC_ERROR, notice.value.key)
        assertNull(controller.state.value.retryingPromptId)
        assertEquals(1, refreshed)
    }

    @Test
    fun inFlightRetryRejectsSecondTap() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val controller = QueuePendingRetryController(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            retrySubmission = {
                calls += 1
                gate.await()
                WorkflowSubmitResult.Success("p1", 1)
            },
            refresh = {},
        )

        controller.retry(task())
        controller.retry(task())
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertNull(controller.state.value.retryingPromptId)
    }

    @Test
    fun recheckFailureDoesNotSuggestReusingTheId() {
        val notice = describeRecheckResult(
            Result.failure(IllegalStateException("网络断开")),
        ) as QueueNotice.Message

        assertEquals(NekoMessageKey.GENERIC_ERROR, notice.value.key)
    }

    @Test
    fun recheckNoticeUnwrapsInnerFailureInsteadOfReportingNotFound() = runTest {
        val notice = recheckNotice {
            Result.failure(IllegalStateException("history 500"))
        } as QueueNotice.Message

        assertEquals(NekoMessageKey.GENERIC_ERROR, notice.value.key)
    }

    @Test
    fun recheckNoticeCatchesOuterExceptionTheSameWay() = runTest {
        val notice = recheckNotice { error("network down") } as QueueNotice.Message

        assertEquals(NekoMessageKey.GENERIC_ERROR, notice.value.key)
    }

    @Test
    fun recheckFoundAndNotFoundHaveDistinctNotices() {
        val found = describeRecheckResult(Result.success(true)) as QueueNotice.Message
        val notFound = describeRecheckResult(Result.success(false)) as QueueNotice.Message

        assertEquals(NekoMessageKey.RECHECK_FOUND, found.value.key)
        assertEquals(NekoMessageKey.RECHECK_NOT_FOUND, notFound.value.key)
    }

    private fun task() = TaskRecord(
        promptId = "p1",
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "client",
        submittedAt = 1L,
        status = TaskStatus.UNKNOWN,
        values = mapOf("1:seed" to "1"),
        requestedAt = 1L,
        requestedPromptId = "p1",
        statusUpdatedAt = 1L,
        statusSource = TaskStatusSource.LOCAL.name,
    )
}
