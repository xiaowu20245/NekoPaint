package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterConstraints
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCompatibility
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.NodeInstallOutcome
import io.github.xiaowu20245.nekopaint.data.repository.NodeInstaller
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTaskActionHandlerTest {

    @Test
    fun `retry requires a saved parameter snapshot`() {
        val source = FakeSource()
        source.task = task(values = emptyMap())
        val handler = ChatTaskActionHandler(source)

        val result = handler.propose(ChatTaskAction.RETRY, "task-1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `proposes retry with local task metadata`() {
        val source = FakeSource()
        source.task = task(values = mapOf("1:seed" to "42"))
        source.history = history(status = "failed")
        val handler = ChatTaskActionHandler(source)

        val proposal = handler.propose(ChatTaskAction.RETRY, "task-1").getOrThrow()

        assertEquals("任务 A", proposal.title)
        assertEquals("failed", proposal.status)
        assertEquals(ChatTaskAction.RETRY, proposal.action)
    }

    @Test
    fun `interrupt requires a running task`() {
        val source = FakeSource()
        source.task = task()
        source.history = history(status = "running")
        val handler = ChatTaskActionHandler(source)

        assertTrue(handler.propose(ChatTaskAction.INTERRUPT, "task-1").isFailure)

        source.running = true
        val proposal = handler.propose(ChatTaskAction.INTERRUPT, "task-1").getOrThrow()
        assertEquals("running", proposal.status)
    }

    @Test
    fun `delete requires a pending task`() {
        val source = FakeSource()
        source.task = task()
        source.history = history(status = "queued")
        val handler = ChatTaskActionHandler(source)

        assertTrue(handler.propose(ChatTaskAction.DELETE, "task-1").isFailure)

        source.pending = true
        val proposal = handler.propose(ChatTaskAction.DELETE, "task-1").getOrThrow()
        assertEquals("queued", proposal.status)
    }

    @Test
    fun `executes retry and refreshes tasks`() = runTest {
        val source = FakeSource()
        source.task = task(values = mapOf("1:seed" to "42"))
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.propose(ChatTaskAction.RETRY, "task-1").getOrThrow()

        val message = handler.execute(proposal).getOrThrow()

        assertTrue(message.contains("new-prom"))
        assertEquals(1, source.resubmitCalls)
        assertEquals(1, source.refreshCalls)
    }

    @Test
    fun `executes interrupt only while still running`() = runTest {
        val source = FakeSource()
        source.task = task()
        source.running = true
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.propose(ChatTaskAction.INTERRUPT, "task-1").getOrThrow()

        handler.execute(proposal).getOrThrow()

        assertEquals(1, source.interruptCalls)
        assertEquals(1, source.refreshCalls)

        source.running = false
        assertTrue(handler.execute(proposal).isFailure)
        assertEquals(1, source.interruptCalls)
    }

    @Test
    fun `executes delete only while still pending`() = runTest {
        val source = FakeSource()
        source.task = task()
        source.pending = true
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.propose(ChatTaskAction.DELETE, "task-1").getOrThrow()

        handler.execute(proposal).getOrThrow()

        assertEquals(1, source.deleteCalls)
        assertEquals(1, source.refreshCalls)

        source.pending = false
        assertTrue(handler.execute(proposal).isFailure)
        assertEquals(1, source.deleteCalls)
    }

    @Test
    fun `proposes text to image submission with prompt`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template())
        source.template = template()
        val handler = ChatTaskActionHandler(source)

        val proposal = handler.proposeSubmit("anima", "1girl, fox ears").getOrThrow()

        assertEquals(ChatTaskAction.SUBMIT, proposal.action)
        assertEquals("workflow-1", proposal.workflowId)
        assertEquals("1girl, fox ears", proposal.promptText)
        assertEquals("Anima T2I", proposal.title)
        assertEquals("310:positive", proposal.promptCandidateId)
        assertEquals(3, proposal.promptOptions.size)
    }

    @Test
    fun `uses primary prompt marked on template`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template(primaryPromptId = "1:text"))
        val handler = ChatTaskActionHandler(source)

        val proposal = handler.proposeSubmit("anima", "1girl").getOrThrow()

        assertEquals("1:text", proposal.promptCandidateId)
        assertEquals("文本", proposal.promptCandidateLabel)
    }

    @Test
    fun `falls back when primary prompt marker is stale`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template(primaryPromptId = "missing"))
        val handler = ChatTaskActionHandler(source)

        val proposal = handler.proposeSubmit("anima", "1girl").getOrThrow()

        assertEquals("310:positive", proposal.promptCandidateId)
    }

    @Test
    fun `rejects image workflows for chat submission`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template(mode = WorkflowMode.IMAGE_TO_IMAGE))
        val handler = ChatTaskActionHandler(source)

        assertTrue(handler.proposeSubmit(null, "1girl").isFailure)
    }

    @Test
    fun `executes text to image submission`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template())
        source.template = template()
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.proposeSubmit("anima", "1girl, fox ears").getOrThrow()

        val message = handler.execute(proposal).getOrThrow()

        assertTrue(message.contains("new-prom"))
        assertEquals(1, source.submitCalls)
        assertEquals(1, source.refreshCalls)
        assertEquals("1girl, fox ears", source.submittedValues["310:positive"])
    }

    @Test
    fun `applies parameter overrides without changing template`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template())
        source.template = template()
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.proposeSubmit(
            workflowKeyword = "anima",
            prompt = "1girl",
            parameters = listOf(ChatSubmissionParameter("63:steps", "30")),
        ).getOrThrow()

        handler.execute(proposal).getOrThrow()

        assertEquals(1, proposal.parameterOverrides.size)
        assertEquals("30", source.submittedValues["63:steps"])
        assertEquals("13", source.template?.candidates
            ?.firstOrNull { it.id == "63:steps" }
            ?.value)
    }

    @Test
    fun `saves submission as preset without changing defaults`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template())
        source.template = template()
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.proposeSubmit(
            workflowKeyword = "anima",
            prompt = "1girl",
            parameters = listOf(ChatSubmissionParameter("63:steps", "30")),
        ).getOrThrow().copy(state = ChatActionState.SUCCESS)

        val message = handler.savePreset(proposal, "快速测试").getOrThrow()

        assertTrue(message.contains("快速测试"))
        assertEquals(1, source.updateCalls)
        val saved = source.updatedTemplate ?: error("template not saved")
        assertEquals("快速测试", saved.presets.single().name)
        assertEquals(
            "30",
            saved.presets.single().entries.first { it.candidateId == "63:steps" }.value,
        )
        assertEquals(
            "13",
            saved.candidates.first { it.id == "63:steps" }.value,
        )
        assertEquals(null, saved.activePresetId)
    }

    @Test
    fun `rejects duplicate preset names`() = runTest {
        val source = FakeSource()
        val existing = WorkflowPreset(
            id = "preset-1",
            name = "快速测试",
            updatedAt = 0L,
        )
        source.templates = listOf(template().copy(presets = listOf(existing)))
        source.template = template().copy(presets = listOf(existing))
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.proposeSubmit("anima", "1girl")
            .getOrThrow()
            .copy(state = ChatActionState.SUCCESS)

        assertTrue(handler.savePreset(proposal, "快速测试").isFailure)
        assertEquals(0, source.updateCalls)
    }

    @Test
    fun `proposes batch retry for failed and interrupted tasks`() = runTest {
        val source = FakeSource()
        source.tasksById = mapOf(
            "t1" to task("t1", status = "failed"),
            "t2" to task("t2", status = "interrupted"),
        )
        source.historyById = mapOf(
            "t1" to history("t1", "failed"),
            "t2" to history("t2", "interrupted"),
        )
        val handler = ChatTaskActionHandler(source)

        val proposal = handler.proposeBatch(
            ChatTaskAction.BATCH_RETRY,
            listOf("t1", "t2"),
        ).getOrThrow()

        assertEquals(ChatTaskAction.BATCH_RETRY, proposal.action)
        assertEquals(2, proposal.batchTargets.size)
    }

    @Test
    fun `rejects batch retry when any task is not failed`() = runTest {
        val source = FakeSource()
        source.tasksById = mapOf("t1" to task("t1", status = "success"))
        source.historyById = mapOf("t1" to history("t1", "success"))
        val handler = ChatTaskActionHandler(source)

        assertTrue(
            handler.proposeBatch(ChatTaskAction.BATCH_RETRY, listOf("t1")).isFailure,
        )
    }

    @Test
    fun `proposes batch delete only for pending tasks`() = runTest {
        val source = FakeSource()
        source.pendingIds = setOf("t1", "t2")
        source.tasksById = mapOf(
            "t1" to task("t1"),
            "t2" to task("t2"),
        )
        val handler = ChatTaskActionHandler(source)

        val proposal = handler.proposeBatch(
            ChatTaskAction.BATCH_DELETE,
            listOf("t1", "t2"),
        ).getOrThrow()
        assertEquals(2, proposal.batchTargets.size)

        assertTrue(
            handler.proposeBatch(ChatTaskAction.BATCH_DELETE, listOf("t3")).isFailure,
        )
    }

    @Test
    fun `executes batch retry and refreshes once`() = runTest {
        val source = FakeSource()
        source.tasksById = mapOf(
            "t1" to task("t1", status = "failed"),
            "t2" to task("t2", status = "failed"),
        )
        source.historyById = mapOf(
            "t1" to history("t1", "failed"),
            "t2" to history("t2", "failed"),
        )
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.proposeBatch(
            ChatTaskAction.BATCH_RETRY,
            listOf("t1", "t2"),
        ).getOrThrow()

        val message = handler.execute(proposal).getOrThrow()

        assertTrue(message.contains("2 个任务"))
        assertEquals(listOf("t1", "t2"), source.resubmittedIds)
        assertEquals(1, source.refreshCalls)
    }

    @Test
    fun `executes batch delete`() = runTest {
        val source = FakeSource()
        source.pendingIds = setOf("t1", "t2")
        val handler = ChatTaskActionHandler(source)
        val proposal = handler.proposeBatch(
            ChatTaskAction.BATCH_DELETE,
            listOf("t1", "t2"),
        ).getOrThrow()

        val message = handler.execute(proposal).getOrThrow()

        assertTrue(message.contains("2 个任务"))
        assertEquals(listOf("t1", "t2"), source.deletedIds)
        assertEquals(1, source.refreshCalls)
    }

    @Test
    fun `proposes node install for workflow with missing nodes`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template().copy(missingNodeTypes = listOf("NodeA")))
        val installer = FakeInstaller()
        val handler = ChatTaskActionHandler(source, installer)

        val proposal = handler.proposeNodeInstall(null).getOrThrow()

        assertEquals(ChatTaskAction.INSTALL_NODES, proposal.action)
        assertEquals(ChatActionState.PENDING, proposal.state)
        assertEquals(1, proposal.installPackages.size)
        assertEquals(1, proposal.missingNodeTypeCount)
        assertEquals("workflow-1", proposal.workflowId)
    }

    @Test
    fun `rejects node install when workflow has no missing nodes`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template())
        val installer = FakeInstaller().apply {
            prepareResult = Result.failure(
                IllegalStateException("该工作流当前没有记录到缺失节点"),
            )
        }
        val handler = ChatTaskActionHandler(source, installer)

        assertTrue(handler.proposeNodeInstall(null).isFailure)
    }

    @Test
    fun `executes node install with current restart choice`() = runTest {
        val source = FakeSource()
        source.templates = listOf(template().copy(missingNodeTypes = listOf("NodeA")))
        val installer = FakeInstaller()
        val handler = ChatTaskActionHandler(source, installer)
        val proposal = handler.proposeNodeInstall(null)
            .getOrThrow()
            .copy(restartAfterInstall = false)

        val message = handler.execute(proposal).getOrThrow()

        assertEquals(1, installer.installCalls)
        assertEquals(false, installer.restartAfterInstall)
        assertTrue(message.contains("1 个节点包"))
    }

    private fun task(
        promptId: String = "task-1",
        values: Map<String, String> = mapOf("1:seed" to "42"),
        status: String = "queued",
    ): TaskRecord =
        TaskRecord(
            promptId = promptId,
            templateId = "template-1",
            templateName = "任务 A",
            clientId = "client-1",
            submittedAt = 0L,
            status = status,
            values = values,
        )

    private fun history(promptId: String = "task-1", status: String): HistoryEntry = HistoryEntry(
        promptId = promptId,
        status = status,
        completed = status != "running",
        timestamp = 0L,
        startedAt = 0L,
        finishedAt = null,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )

    private fun template(
        mode: WorkflowMode = WorkflowMode.TEXT_TO_IMAGE,
        primaryPromptId: String? = null,
    ): WorkflowTemplate =
        WorkflowTemplate(
            id = "workflow-1",
            name = "Anima T2I",
            sourceFileName = "anima.json",
            sourceHash = "hash",
            importedAt = 0L,
            nodeCount = 8,
            reachableNodeCount = 8,
            compatibility = WorkflowCompatibility(mode = mode),
            primaryPromptCandidateId = primaryPromptId,
            candidates = listOf(
                ParameterCandidate(
                    id = "1:text",
                    nodeId = "1",
                    inputName = "text",
                    label = "文本",
                    type = ParameterType.MULTILINE_TEXT,
                    group = ParameterGroup.PROMPT,
                    value = "style tags",
                    selected = true,
                ),
                ParameterCandidate(
                    id = "103:text",
                    nodeId = "103",
                    inputName = "text",
                    label = "画师",
                    type = ParameterType.MULTILINE_TEXT,
                    group = ParameterGroup.PROMPT,
                    value = "artist tags",
                    selected = true,
                ),
                ParameterCandidate(
                    id = "310:positive",
                    nodeId = "310",
                    inputName = "positive",
                    label = "正面提示词",
                    type = ParameterType.MULTILINE_TEXT,
                    group = ParameterGroup.PROMPT,
                    value = "default prompt",
                    selected = true,
                ),
                ParameterCandidate(
                    id = "2:text",
                    nodeId = "2",
                    inputName = "text",
                    label = "负面提示词",
                    type = ParameterType.MULTILINE_TEXT,
                    group = ParameterGroup.PROMPT,
                    value = "worst quality",
                    selected = true,
                ),
                ParameterCandidate(
                    id = "63:steps",
                    nodeId = "63",
                    inputName = "steps",
                    label = "采样 1 步数",
                    type = ParameterType.INT,
                    group = ParameterGroup.SAMPLING,
                    value = "13",
                    selected = true,
                    constraints = ParameterConstraints(min = 1.0, max = 100.0),
                ),
            ),
        )

    private class FakeInstaller : NodeInstaller {
        var installCalls = 0
        var restartAfterInstall: Boolean? = null
        var prepareResult: Result<NodeInstallPlan>? = null
        var plan = NodeInstallPlan(
            templateId = "workflow-1",
            templateName = "Anima T2I",
            missingNodeTypes = listOf("NodeA"),
            packages = listOf(
                NodeInstallPackage(
                    id = "pack-a",
                    title = "Pack A",
                    files = listOf("https://github.com/a/a.git"),
                ),
            ),
        )

        override suspend fun preparePlan(
            templateId: String,
            reanalyze: Boolean,
        ): Result<NodeInstallPlan> = prepareResult ?: Result.success(plan)

        override suspend fun install(
            plan: NodeInstallPlan,
            restartAfterInstall: Boolean,
            onProgress: (String) -> Unit,
        ): Result<NodeInstallOutcome> {
            installCalls++
            this.restartAfterInstall = restartAfterInstall
            return Result.success(
                NodeInstallOutcome(
                    queuedCount = plan.packages.size,
                    restartRequested = restartAfterInstall,
                    serverReconnected = false,
                ),
            )
        }

        override suspend fun recheck(templateId: String): Result<WorkflowTemplate> =
            Result.success(
                WorkflowTemplate(
                    id = templateId,
                    name = "Anima T2I",
                    sourceFileName = "anima.json",
                    sourceHash = "hash",
                    importedAt = 0L,
                    nodeCount = 1,
                    reachableNodeCount = 1,
                ),
            )
    }

    private class FakeSource : ChatTaskActionDataSource {
        var task: TaskRecord? = null
        var history: HistoryEntry? = null
        var running: Boolean = false
        var pending: Boolean = false
        var templates: List<WorkflowTemplate> = emptyList()
        var template: WorkflowTemplate? = null
        var resubmitResult: WorkflowSubmitResult =
            WorkflowSubmitResult.Success("new-prompt-id-12345678", null)
        var submitResult: WorkflowSubmitResult =
            WorkflowSubmitResult.Success("new-prompt-id-12345678", null)
        var interruptResult: Result<Unit> = Result.success(Unit)
        var deleteResult: Result<Unit> = Result.success(Unit)
        var resubmitCalls = 0
        var interruptCalls = 0
        var deleteCalls = 0
        var submitCalls = 0
        var updateCalls = 0
        var refreshCalls = 0
        var submittedPrompt: String? = null
        var submittedValues: Map<String, String> = emptyMap()
        var updatedTemplate: WorkflowTemplate? = null
        var tasksById: Map<String, TaskRecord> = emptyMap()
        var historyById: Map<String, HistoryEntry> = emptyMap()
        var runningIds: Set<String> = emptySet()
        var pendingIds: Set<String> = emptySet()
        val resubmittedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()

        override fun findTask(promptId: String): TaskRecord? =
            tasksById[promptId] ?: task?.takeIf { promptId == "task-1" }

        override fun findHistory(promptId: String): HistoryEntry? =
            historyById[promptId] ?: history?.takeIf { promptId == "task-1" }

        override fun isRunning(promptId: String): Boolean =
            promptId in runningIds || (running && promptId == "task-1")

        override fun isPending(promptId: String): Boolean =
            promptId in pendingIds || (pending && promptId == "task-1")

        override suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult {
            resubmitCalls++
            resubmittedIds += task.promptId
            return resubmitResult
        }

        override suspend fun interrupt(promptId: String): Result<Unit> {
            interruptCalls++
            return interruptResult
        }

        override suspend fun deletePending(promptId: String): Result<Unit> {
            deleteCalls++
            deletedIds += promptId
            return deleteResult
        }

        override suspend fun listTemplates(): List<WorkflowTemplate> = templates

        override suspend fun getTemplate(templateId: String): WorkflowTemplate? = template

        override suspend fun submit(template: WorkflowTemplate): WorkflowSubmitResult {
            submitCalls++
            submittedValues = template.candidates.associate { it.id to it.value }
            submittedPrompt = template.candidates.firstOrNull {
                it.group == ParameterGroup.PROMPT && !it.label.contains("负")
            }?.value
            return submitResult
        }

        override suspend fun updateTemplate(template: WorkflowTemplate): Result<Unit> {
            updateCalls++
            updatedTemplate = template
            return Result.success(Unit)
        }

        override fun refresh() {
            refreshCalls++
        }
    }
}
