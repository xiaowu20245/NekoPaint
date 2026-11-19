package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageFormatter
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatus
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTaskActionBatchRetryTest {

    @Test
    fun batchRetrySurfacesLocalRecordWarningWithoutCountingFailure() = runTest {
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
        val dataSource = FakeDataSource(
            task = task,
            result = WorkflowSubmitResult.Success(
                promptId = "new-id",
                queueNumber = 1,
                localRecordWarning = NekoMessage(NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED),
            ),
        )
        val handler = ChatTaskActionHandler(
            dataSource = dataSource,
            messageFormatter = NekoMessageFormatter { "本地存在一条旧请求记录" },
        )
        val proposal = ChatActionProposal(
            id = "proposal",
            action = ChatTaskAction.BATCH_RETRY,
            promptId = "",
            title = "批量重试",
            status = "",
            batchTargets = listOf(
                ChatBatchTarget(promptId = "p1", title = "任务一", status = TaskStatus.FAILED),
            ),
        )

        val text = handler.execute(proposal).getOrThrow()

        assertTrue(text.contains("1 个警告"))
        assertTrue(text.contains("本地存在一条旧请求记录"))
        assertFalse(text.contains("失败"))
        assertTrue(dataSource.refreshed)
    }

    private class FakeDataSource(
        private val task: TaskRecord,
        private val result: WorkflowSubmitResult,
    ) : ChatTaskActionDataSource {
        var refreshed = false

        override val localClientId: String? = null

        override fun queueClientId(promptId: String): String? = null

        override fun findTask(promptId: String): TaskRecord? =
            task.takeIf { it.promptId == promptId }

        override fun findHistory(promptId: String): HistoryEntry? = null

        override fun isRunning(promptId: String): Boolean = false

        override fun isPending(promptId: String): Boolean = false

        override suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult = result

        override suspend fun interrupt(promptId: String): Result<Unit> = Result.success(Unit)

        override suspend fun deletePending(promptId: String): Result<Unit> = Result.success(Unit)

        override suspend fun listTemplates(): List<WorkflowTemplate> = emptyList()

        override suspend fun getTemplate(templateId: String): WorkflowTemplate? = null

        override suspend fun submit(template: WorkflowTemplate): WorkflowSubmitResult = result

        override suspend fun updateTemplate(template: WorkflowTemplate): Result<Unit> =
            Result.success(Unit)

        override fun refresh() {
            refreshed = true
        }
    }
}
