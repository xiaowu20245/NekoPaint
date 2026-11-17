package io.github.xiaowu20245.nekopaint.feature.queue

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
import org.junit.Assert.assertFalse
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
                    localRecordWarning = "本地存在一条旧请求记录",
                )
            },
            refresh = {},
        )

        controller.retry(task())

        val notice = controller.state.value.notice.orEmpty()
        assertTrue(notice.contains("new-task"))
        assertTrue(notice.contains("旧请求记录"))
        assertNull(controller.state.value.retryingPromptId)
    }

    @Test
    fun unknownNoticeIsShownAfterRetry() = runTest {
        val controller = QueuePendingRetryController(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            retrySubmission = {
                WorkflowSubmitResult.Unknown("p1", "网络断开")
            },
            refresh = {},
        )

        controller.retry(task())

        assertTrue(controller.state.value.notice.orEmpty().contains("结果仍未知"))
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

        assertTrue(controller.state.value.notice.orEmpty().contains("重试失败"))
        assertTrue(controller.state.value.notice.orEmpty().contains("磁盘异常"))
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
        )

        assertTrue(notice.contains("检查失败"))
        assertTrue(notice.contains("网络断开"))
        assertFalse(notice.contains("可复用"))
    }

    @Test
    fun recheckNoticeUnwrapsInnerFailureInsteadOfReportingNotFound() = runTest {
        val notice = recheckNotice {
            Result.failure(IllegalStateException("history 500"))
        }

        assertTrue(notice.contains("检查失败"))
        assertTrue(notice.contains("history 500"))
        assertFalse(notice.contains("可复用"))
    }

    @Test
    fun recheckNoticeCatchesOuterExceptionTheSameWay() = runTest {
        val notice = recheckNotice { error("network down") }

        assertTrue(notice.contains("检查失败"))
        assertTrue(notice.contains("network down"))
        assertFalse(notice.contains("可复用"))
    }

    @Test
    fun recheckFoundAndNotFoundHaveDistinctNotices() {
        val found = describeRecheckResult(Result.success(true))
        val notFound = describeRecheckResult(Result.success(false))

        assertTrue(found.contains("找到"))
        assertFalse(found.contains("可复用"))
        assertTrue(notFound.contains("未找到"))
        assertTrue(notFound.contains("可复用"))
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
