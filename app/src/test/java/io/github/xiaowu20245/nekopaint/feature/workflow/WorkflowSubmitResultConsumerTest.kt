package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageFormatter
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.operation.OperationCenter
import io.github.xiaowu20245.nekopaint.data.operation.OperationStatus
import io.github.xiaowu20245.nekopaint.data.operation.SubmissionOperationState
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatus
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.feature.chat.ChatActionProposal
import io.github.xiaowu20245.nekopaint.feature.chat.ChatBatchTarget
import io.github.xiaowu20245.nekopaint.feature.chat.ChatTaskAction
import io.github.xiaowu20245.nekopaint.feature.chat.ChatTaskActionDataSource
import io.github.xiaowu20245.nekopaint.feature.chat.ChatTaskActionHandler
import io.github.xiaowu20245.nekopaint.feature.history.HistoryNotice
import io.github.xiaowu20245.nekopaint.feature.history.historyNoticeFor
import io.github.xiaowu20245.nekopaint.feature.queue.QueueNotice
import io.github.xiaowu20245.nekopaint.feature.queue.queueRetryNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkflowSubmitResultConsumerTest {

    private val template = WorkflowTemplate(
        id = "t1",
        name = "template",
        sourceFileName = "api.json",
        sourceHash = "hash",
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
    )

    private val warning = NekoMessage(NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED)
    private val validation = NekoMessage(
        NekoMessageKey.WORKFLOW_VALIDATION_FAILED,
        listOf("bad params"),
    )
    private val error = NekoMessage(NekoMessageKey.WORKFLOW_FAILED)
    private val unknown = NekoMessage(
        NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN,
        listOf("timeout"),
    )

    private val successResult = WorkflowSubmitResult.Success(
        promptId = "p1",
        queueNumber = 2,
        localRecordWarning = warning,
    )
    private val validationResult = WorkflowSubmitResult.Validation(validation, emptyList())
    private val errorResult = WorkflowSubmitResult.Error(error)
    private val unknownResult = WorkflowSubmitResult.Unknown("p1", unknown)

    // 1-5: OperationCenter
    @Test
    fun operationCenterSuccess() = runTest {
        val state = runOperationCenter(successResult)
        assertEquals(OperationStatus.SUCCESS, state.status)
        assertEquals("p1", state.lastSuccess?.promptId)
        assertEquals(warning, state.localRecordWarning)
    }

    @Test
    fun operationCenterValidation() = runTest {
        val state = runOperationCenter(validationResult)
        assertEquals(OperationStatus.FAILED, state.status)
        assertEquals(validation, state.message)
        assertEquals(validation, state.lastValidation?.summary)
    }

    @Test
    fun operationCenterError() = runTest {
        val state = runOperationCenter(errorResult)
        assertEquals(OperationStatus.FAILED, state.status)
        assertEquals(error, state.message)
    }

    @Test
    fun operationCenterUnknown() = runTest {
        val state = runOperationCenter(unknownResult)
        assertEquals(OperationStatus.UNKNOWN, state.status)
        assertEquals(unknown, state.message)
        assertEquals(1, state.unknown)
    }

    @Test
    fun operationCenterNoServer() = runTest {
        val state = runOperationCenter(WorkflowSubmitResult.NoServer)
        assertEquals(OperationStatus.FAILED, state.status)
        assertEquals(NekoMessageKey.WORKFLOW_NO_SERVER, state.message?.key)
    }

    // 6-10: WorkflowDetailOperationPolicy
    @Test
    fun detailPolicySuccess() {
        val ui = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation(OperationStatus.SUCCESS, success = 1, lastSuccess = successResult),
        )
        assertEquals("p1", ui.state.submission?.promptId)
        assertEquals(
            NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED,
            ui.state.submitError?.extraMessages?.single()?.key,
        )
    }

    @Test
    fun detailPolicyValidation() {
        val ui = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation(
                OperationStatus.PARTIAL,
                failed = 1,
                lastValidation = validationResult,
                message = validation,
            ),
        )
        assertEquals(validation, ui.state.submitError?.message)
        assertEquals(1, ui.state.submitError?.batchStats?.failed)
    }

    @Test
    fun detailPolicyError() {
        val ui = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation(OperationStatus.FAILED, failed = 1, message = error),
        )
        assertEquals(error, ui.state.submitError?.message)
    }

    @Test
    fun detailPolicyUnknown() {
        val ui = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation(OperationStatus.UNKNOWN, unknown = 1, message = unknown),
        )
        assertEquals(unknown, ui.state.submitError?.message)
        assertTrue(ui.state.submitError?.batchStats?.unknownOutcome == true)
    }

    @Test
    fun detailPolicyNoServer() {
        val ui = WorkflowDetailOperationPolicy.apply(
            WorkflowDetailUiState(template = template),
            operation(
                OperationStatus.FAILED,
                failed = 1,
                message = NekoMessage(NekoMessageKey.WORKFLOW_NO_SERVER),
            ),
        )
        assertEquals(NekoMessageKey.WORKFLOW_NO_SERVER, ui.state.submitError?.message?.key)
    }

    // 11-15: WorkflowsViewModel error mapping
    @Test
    fun workflowsSuccessHasNoError() {
        val operation = operation(
            OperationStatus.SUCCESS,
            success = 1,
            lastSuccess = successResult,
            localRecordWarning = warning,
        )
        assertNull(operation.operationErrorRes())
        assertEquals(warning, operation.operationErrorDetail())
    }

    @Test
    fun workflowsValidationShowsDetail() {
        val operation = operation(
            OperationStatus.PARTIAL,
            failed = 1,
            message = validation,
        )
        assertNull(operation.operationErrorRes())
        assertEquals(validation, operation.operationErrorDetail())
    }

    @Test
    fun workflowsErrorShowsDetail() {
        val operation = operation(OperationStatus.FAILED, failed = 1, message = error)
        assertNull(operation.operationErrorRes())
        assertEquals(error, operation.operationErrorDetail())
    }

    @Test
    fun workflowsUnknownShowsDetail() {
        val operation = operation(OperationStatus.UNKNOWN, unknown = 1, message = unknown)
        assertNull(operation.operationErrorRes())
        assertEquals(unknown, operation.operationErrorDetail())
    }

    @Test
    fun workflowsNoServerMapsToDedicatedResource() {
        val operation = operation(
            OperationStatus.FAILED,
            failed = 1,
            message = NekoMessage(NekoMessageKey.WORKFLOW_NO_SERVER),
        )
        assertEquals(R.string.workflow_submit_no_server, operation.operationErrorRes())
    }

    // 16-20: ChatTaskAction
    @Test
    fun chatSuccess() = runTest {
        val text = chatText(successResult)
        assertTrue(text.contains("已批量重试 1 个任务"))
        assertTrue(text.contains("WORKFLOW_OLD_RECORD_CLEANUP_FAILED"))
    }

    @Test
    fun chatValidation() = runTest {
        val text = chatText(validationResult)
        assertTrue(text.contains("WORKFLOW_VALIDATION_FAILED"))
    }

    @Test
    fun chatError() = runTest {
        val text = chatText(errorResult)
        assertTrue(text.contains("WORKFLOW_FAILED"))
    }

    @Test
    fun chatUnknown() = runTest {
        val text = chatText(unknownResult)
        assertTrue(text.contains("WORKFLOW_SUBMIT_UNKNOWN"))
        assertTrue(text.contains("p1"))
    }

    @Test
    fun chatNoServer() = runTest {
        val text = chatText(WorkflowSubmitResult.NoServer)
        assertTrue(text.contains("WORKFLOW_NO_SERVER"))
    }

    @Test
    fun chatRetryUnknownUsesTypedMessage() = runTest {
        val text = chatRetryText(unknownResult)
        assertTrue(text.contains("WORKFLOW_SUBMIT_UNKNOWN"))
        assertTrue(text.contains("p1"))
    }

    @Test
    fun chatRetryNoServerUsesTypedMessage() = runTest {
        val text = chatRetryText(WorkflowSubmitResult.NoServer)
        assertTrue(text.contains("WORKFLOW_NO_SERVER"))
    }

    // 21-25: HistoryViewModel
    @Test
    fun historySuccess() {
        val notice = historyNoticeFor(successResult)
        assertTrue(notice is HistoryNotice.Success)
        assertEquals("p1", (notice as HistoryNotice.Success).promptId)
        assertEquals(warning, notice.warning)
    }

    @Test
    fun historyValidation() {
        val notice = historyNoticeFor(validationResult)
        assertEquals(validation, (notice as HistoryNotice.Error).message)
    }

    @Test
    fun historyError() {
        val notice = historyNoticeFor(errorResult)
        assertEquals(error, (notice as HistoryNotice.Error).message)
    }

    @Test
    fun historyUnknown() {
        val notice = historyNoticeFor(unknownResult) as HistoryNotice.Unknown
        assertEquals("p1", notice.promptId)
        assertEquals(unknown, notice.message)
    }

    @Test
    fun historyNoServer() {
        assertEquals(HistoryNotice.NoServer, historyNoticeFor(WorkflowSubmitResult.NoServer))
    }

    // 26-30: QueuePendingRetryController
    @Test
    fun queueSuccess() {
        val notice = queueRetryNotice(successResult)
        assertTrue(notice is QueueNotice.Submitted)
        assertEquals(warning, (notice as QueueNotice.Submitted).warning)
    }

    @Test
    fun queueValidation() {
        val notice = queueRetryNotice(validationResult)
        assertEquals(validation, (notice as QueueNotice.Message).value)
    }

    @Test
    fun queueError() {
        val notice = queueRetryNotice(errorResult)
        assertEquals(error, (notice as QueueNotice.Message).value)
    }

    @Test
    fun queueUnknown() {
        val notice = queueRetryNotice(unknownResult)
        assertEquals("p1", (notice as QueueNotice.Unknown).promptId)
        assertEquals(unknown, notice.message)
    }

    @Test
    fun queueNoServer() {
        val notice = queueRetryNotice(WorkflowSubmitResult.NoServer)
        assertEquals(
            NekoMessageKey.WORKFLOW_NO_SERVER,
            (notice as QueueNotice.Message).value.key,
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.runOperationCenter(
        result: WorkflowSubmitResult,
    ): SubmissionOperationState {
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = { result },
        )
        val id = center.start(template, 1, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()
        return center.states.value.getValue(id)
    }

    private fun operation(
        status: OperationStatus,
        success: Int = 0,
        failed: Int = 0,
        unknown: Int = 0,
        message: NekoMessage? = null,
        localRecordWarning: NekoMessage? = null,
        lastSuccess: WorkflowSubmitResult.Success? = null,
        lastValidation: WorkflowSubmitResult.Validation? = null,
    ) = SubmissionOperationState(
        operationId = "op",
        templateId = template.id,
        status = status,
        total = 1,
        processed = 1,
        success = success,
        failed = failed,
        skipped = 0,
        unknown = unknown,
        lastSuccess = lastSuccess,
        lastValidation = lastValidation,
        lastTemplate = template,
        localRecordWarning = localRecordWarning,
        message = message,
        updatedAt = 1L,
    )

    private suspend fun chatText(result: WorkflowSubmitResult): String =
        chatExecute(result, batch = true)

    private suspend fun chatRetryText(result: WorkflowSubmitResult): String =
        chatExecute(result, batch = false)

    private suspend fun chatExecute(result: WorkflowSubmitResult, batch: Boolean): String {
        val task = TaskRecord(
            promptId = "p1",
            serverId = "server-a",
            templateId = "t",
            templateName = "template",
            clientId = "client",
            submittedAt = 1L,
            status = TaskStatus.FAILED,
            values = mapOf("1:seed" to "1"),
            requestedAt = 1L,
            requestedPromptId = "p1",
            statusUpdatedAt = 1L,
            statusSource = "LOCAL",
        )
        val handler = ChatTaskActionHandler(
            dataSource = FakeChatSource(task, result),
            messageFormatter = NekoMessageFormatter { it.key.name },
        )
        val proposal = if (batch) {
            ChatActionProposal(
                id = "proposal",
                action = ChatTaskAction.BATCH_RETRY,
                promptId = "",
                title = "批量重试",
                status = "",
                batchTargets = listOf(
                    ChatBatchTarget(
                        promptId = "p1",
                        title = "任务一",
                        status = TaskStatus.FAILED,
                    ),
                ),
            )
        } else {
            ChatActionProposal(
                id = "proposal",
                action = ChatTaskAction.RETRY,
                promptId = "p1",
                title = "任务一",
                status = TaskStatus.FAILED,
            )
        }
        return handler.execute(proposal).getOrElse { it.message.orEmpty() }
    }

    private class FakeChatSource(
        private val task: TaskRecord,
        private val result: WorkflowSubmitResult,
    ) : ChatTaskActionDataSource {
        override val localClientId: String = "client"

        override fun findTask(promptId: String): TaskRecord? =
            task.takeIf { it.promptId == promptId }

        override fun findHistory(promptId: String): HistoryEntry? = null

        override fun isRunning(promptId: String): Boolean = false

        override fun isPending(promptId: String): Boolean = false

        override fun queueClientId(promptId: String): String? = "client"

        override suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult = result

        override suspend fun interrupt(promptId: String): Result<Unit> = Result.success(Unit)

        override suspend fun deletePending(promptId: String): Result<Unit> = Result.success(Unit)

        override suspend fun listTemplates(): List<WorkflowTemplate> = emptyList()

        override suspend fun getTemplate(templateId: String): WorkflowTemplate? = null

        override suspend fun submit(template: WorkflowTemplate): WorkflowSubmitResult = result

        override suspend fun updateTemplate(template: WorkflowTemplate): Result<Unit> =
            Result.success(Unit)

        override fun refresh() = Unit
    }
}
