package io.github.xiaowu20245.nekopaint.feature.chat

import android.net.Uri
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.network.AiChatCompletion
import io.github.xiaowu20245.nekopaint.core.network.AiChatMessage
import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import io.github.xiaowu20245.nekopaint.core.network.AiToolDefinition
import io.github.xiaowu20245.nekopaint.core.network.ChatCompletionClient
import io.github.xiaowu20245.nekopaint.core.network.ChatPrivacyGuard
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageEncoderApi
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * 会话操作 lease 的并发窗口回归。
 *
 * 这些测试直接构造生产 ChatViewModel，用可挂起的 fake store/client 复现
 * “保存/加载等待期间发送”“动作执行中切换/删除”等真实竞态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSessionTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendDuringSwitchAwaitIsRejectedAndTargetSessionWins() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = false)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val viewModel = createViewModel(store, scope = backgroundScope)

        viewModel.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-a", viewModel.state.value.currentSessionId)

        // 切换 B 前制造一个挂起的保存：switchMode 会 persist 当前会话。
        store.saveGate = CompletableDeferred()
        viewModel.switchMode(ChatMode.CHAT)
        advanceUntilIdle()
        val messagesBefore = viewModel.state.value.messages

        viewModel.switchSession("s-b")
        viewModel.send("must be rejected", null)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("s-a", viewModel.state.value.currentSessionId)
        assertEquals(messagesBefore, viewModel.state.value.messages)

        store.saveGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals("s-b", viewModel.state.value.currentSessionId)
        assertEquals(
            listOf("B hello", "reply"),
            viewModel.state.value.messages.map { it.text },
        )
    }

    @Test
    fun newSessionDuringSaveWaitsForSaveAndIgnoresOldSnapshot() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = false)
        }
        val viewModel = createViewModel(store, scope = backgroundScope)

        viewModel.switchSession("s-a")
        advanceUntilIdle()
        store.saveGate = CompletableDeferred()
        viewModel.switchMode(ChatMode.CHAT)
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        // 保存未完成前必须保持原会话，不能先清空再等待。
        assertEquals("s-a", viewModel.state.value.currentSessionId)

        store.saveGate?.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.state.value.currentSessionId)
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertTrue(store.entries.containsKey("s-a"))
        assertNull(viewModel.state.value.sessionError)
    }

    @Test
    fun sendLeaseBlocksSessionMutationAndReplyStaysInOriginSession() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val client = FakeCompletionClient()
        val viewModel = createViewModel(store, client = client, scope = backgroundScope)

        viewModel.switchSession("s-a")
        advanceUntilIdle()

        client.gate = CompletableDeferred()
        viewModel.send("new message", null)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sending)

        viewModel.switchSession("s-b")
        viewModel.deleteSession("s-a")
        advanceUntilIdle()

        assertEquals("s-a", viewModel.state.value.currentSessionId)
        assertEquals(0, store.deleteCalls)
        assertTrue(viewModel.state.value.sending)

        client.gate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("s-a", viewModel.state.value.currentSessionId)
        assertEquals("reply", viewModel.state.value.messages.last().text)
        assertTrue(
            store.entries.getValue("s-a").messages.any { it.text == "reply" },
        )
    }

    @Test
    fun executingActionBlocksSessionMutationUntilFinished() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val actions = FakeActionExecutor()
        val registry = ChatActionExecutionRegistry()
        val viewModel = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            scope = backgroundScope,
        )

        viewModel.switchSession("s-a")
        advanceUntilIdle()
        val messageId = viewModel.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id
        val messagesBefore = viewModel.state.value.messages

        actions.gate = CompletableDeferred()
        viewModel.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        assertTrue(registry.isActive("s-a", PROPOSAL_ID))
        assertEquals(
            ChatActionState.EXECUTING,
            viewModel.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )

        viewModel.newSession()
        viewModel.switchSession("s-b")
        viewModel.deleteSession("s-a")
        viewModel.switchMode(ChatMode.CHAT)
        viewModel.send("must be rejected", null)
        advanceUntilIdle()

        assertEquals("s-a", viewModel.state.value.currentSessionId)
        assertEquals(ChatMode.AGENT, viewModel.state.value.mode)
        assertEquals(messagesBefore.size, viewModel.state.value.messages.size)
        assertEquals(0, store.deleteCalls)
        assertFalse(viewModel.state.value.sending)

        actions.gate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(registry.isActive("s-a", PROPOSAL_ID))
        assertEquals(
            ChatActionState.SUCCESS,
            viewModel.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )
        assertEquals("done", viewModel.state.value.messages.last().text)

        viewModel.newSession()
        advanceUntilIdle()
        assertNull(viewModel.state.value.currentSessionId)
    }

    @Test
    fun deleteDuringLoadRejectsSendAndAction() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val actions = FakeActionExecutor()
        val viewModel = createViewModel(
            store = store,
            actions = actions,
            scope = backgroundScope,
        )

        viewModel.switchSession("s-a")
        advanceUntilIdle()
        val messageId = viewModel.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id

        store.loadGate = CompletableDeferred()
        viewModel.deleteSession("s-b")
        advanceUntilIdle()

        viewModel.send("must be rejected", null)
        viewModel.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertEquals(0, actions.calls)
        assertEquals(0, store.deleteCalls)
        assertEquals(
            ChatActionState.PENDING,
            viewModel.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )

        store.loadGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, store.deleteCalls)
        assertTrue(store.entries.getValue("s-b").deleted)
    }

    @Test
    fun oldInstanceSendBlocksNewInstanceSwitchAndDelete() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
        }
        val client = FakeCompletionClient()
        val sendRegistry = ChatSendExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(
            store = store,
            client = client,
            sendRegistry = sendRegistry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        client.gate = CompletableDeferred()
        first.send("new message", null)
        advanceUntilIdle()
        assertTrue(sendRegistry.isActive("s-a"))

        // 模拟离页后重建：新实例共享 send registry 和 operation gate。
        val second = createViewModel(
            store = store,
            sendRegistry = sendRegistry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        second.switchSession("s-a")
        second.deleteSession("s-a")
        advanceUntilIdle()

        assertNull(second.state.value.currentSessionId)
        assertEquals(0, store.deleteCalls)
        assertTrue(second.state.value.sessionError.orEmpty().isNotBlank())

        client.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(sendRegistry.isActive("s-a"))

        second.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-a", second.state.value.currentSessionId)
        assertTrue(second.state.value.messages.any { it.text == "reply" })
    }

    @Test
    fun sharedGateBlocksNewInstanceWhileActionRunsThenLoadsResult() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val actions = FakeActionExecutor()
        val registry = ChatActionExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        val messageId = first.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id
        actions.gate = CompletableDeferred()
        first.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()
        assertTrue(registry.isActive("s-a", PROPOSAL_ID))

        // 共享 gate 下，旧实例动作执行期间新实例不能切换/删除该会话。
        val second = createViewModel(
            store = store,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        second.switchSession("s-a")
        second.deleteSession("s-a")
        advanceUntilIdle()

        assertNull(second.state.value.currentSessionId)
        assertEquals(0, store.deleteCalls)
        assertFalse(second.state.value.sending)
        assertTrue(second.state.value.sessionError.orEmpty().isNotBlank())

        actions.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(registry.isActive("s-a", PROPOSAL_ID))

        second.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-a", second.state.value.currentSessionId)
        assertEquals(
            ChatActionState.SUCCESS,
            second.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )
        assertEquals("done", second.state.value.messages.last().text)
    }

    @Test
    fun actionExceptionMarksFailedAndReleasesLease() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val actions = FakeActionExecutor().apply {
            throwOnExecute = IllegalStateException("boom")
        }
        val registry = ChatActionExecutionRegistry()
        val viewModel = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            scope = backgroundScope,
        )
        viewModel.switchSession("s-a")
        advanceUntilIdle()
        val messageId = viewModel.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id

        viewModel.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        val proposal = viewModel.state.value.messages.first { it.id == messageId }
            .actionProposals.single()
        assertEquals(ChatActionState.FAILED, proposal.state)
        assertTrue(proposal.resultMessage.orEmpty().contains("boom"))
        assertFalse(registry.isActive("s-a", PROPOSAL_ID))

        viewModel.newSession()
        advanceUntilIdle()
        assertNull(viewModel.state.value.currentSessionId)
    }

    @Test
    fun saveFailureKeepsCurrentSessionAndShowsError() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = false)
        }
        val viewModel = createViewModel(store, scope = backgroundScope)
        viewModel.switchSession("s-a")
        advanceUntilIdle()

        store.nextSaveFailure = ChatSessionSaveResult.Failed("disk full")
        viewModel.switchMode(ChatMode.CHAT)
        viewModel.newSession()
        advanceUntilIdle()

        assertEquals("s-a", viewModel.state.value.currentSessionId)
        assertTrue(viewModel.state.value.sessionError.orEmpty().contains("disk full"))
    }

    @Test
    fun deleteFailureKeepsSessionAndShowsError() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val viewModel = createViewModel(store, scope = backgroundScope)
        advanceUntilIdle()

        store.nextDeleteFailure = ChatSessionSaveResult.Failed("delete failed")
        viewModel.deleteSession("s-b")
        advanceUntilIdle()

        assertFalse(store.entries.getValue("s-b").deleted)
        assertTrue(viewModel.state.value.sessionError.orEmpty().contains("delete failed"))
    }

    @Test
    fun loadFailureKeepsBlankSessionAndShowsError() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
        }
        val viewModel = createViewModel(store, scope = backgroundScope)
        advanceUntilIdle()

        store.nextLoadFailure = IllegalStateException("load failed")
        viewModel.switchSession("s-a")
        advanceUntilIdle()

        assertNull(viewModel.state.value.currentSessionId)
        assertTrue(viewModel.state.value.sessionError.orEmpty().contains("加载会话失败"))
    }

    @Test
    fun actionCancellationMarksFailedAndReleasesLease() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val actions = FakeActionExecutor().apply {
            gate = CompletableDeferred()
        }
        val registry = ChatActionExecutionRegistry()
        val actionScope = CoroutineScope(SupervisorJob() + mainDispatcher)
        val viewModel = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            scope = backgroundScope,
            operationScope = actionScope,
        )
        viewModel.switchSession("s-a")
        advanceUntilIdle()
        val messageId = viewModel.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id

        viewModel.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()
        assertEquals(
            ChatActionState.EXECUTING,
            viewModel.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )

        actionScope.cancel()
        advanceUntilIdle()

        val proposal = viewModel.state.value.messages.first { it.id == messageId }
            .actionProposals.single()
        assertEquals(ChatActionState.FAILED, proposal.state)
        assertTrue(proposal.resultMessage.orEmpty().contains("取消"))
        assertFalse(registry.isActive("s-a", PROPOSAL_ID))

        viewModel.newSession()
        advanceUntilIdle()
        assertNull(viewModel.state.value.currentSessionId)
    }

    @Test
    fun completionDuringSwitchLoadIsNotLost() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val actions = FakeActionExecutor()
        val registry = ChatActionExecutionRegistry()
        val first = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        val messageId = first.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id
        actions.gate = CompletableDeferred()
        first.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        val second = createViewModel(
            store = store,
            registry = registry,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        store.loadGate = CompletableDeferred()
        second.switchSession("s-a")
        advanceUntilIdle()
        assertNull(second.state.value.currentSessionId)

        // switch 的 load 已读取旧快照并挂起；此时动作完成，完成事件到达前 current 仍为空。
        actions.gate?.complete(Unit)
        advanceUntilIdle()
        assertNull(second.state.value.currentSessionId)

        store.loadGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals("s-a", second.state.value.currentSessionId)
        assertEquals(
            ChatActionState.SUCCESS,
            second.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )
        assertEquals("done", second.state.value.messages.last().text)
    }

    @Test
    fun actionOutcomeWaitsForPendingSaveAndUsesNextRevision() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val viewModel = createViewModel(store, scope = backgroundScope)
        viewModel.switchSession("s-a")
        advanceUntilIdle()

        // 先制造一个挂起的普通保存（switchMode 触发 persist）。
        store.saveGate = CompletableDeferred()
        viewModel.switchMode(ChatMode.CHAT)
        advanceUntilIdle()

        val job = launch {
            viewModel.persistActionOutcomeToSession(
                sessionId = "s-a",
                proposalId = PROPOSAL_ID,
                result = Result.success("done"),
            )
        }
        advanceUntilIdle()
        assertFalse(
            store.entries.getValue("s-a").messages.any { it.text == "done" },
        )

        store.saveGate?.complete(Unit)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        val saved = store.entries.getValue("s-a")
        assertTrue(saved.messages.any { it.text == "done" })
        assertTrue(saved.revision > 2L)
        assertEquals(
            ChatActionState.SUCCESS,
            saved.messages.flatMap { it.actionProposals }
                .first { it.id == PROPOSAL_ID }
                .state,
        )
    }

    @Test
    fun beginAfterApplyIsObservedByOtherInstance() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val registry = ChatActionExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val actions = FakeActionExecutor()
        val first = createViewModel(
            store = store,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        val second = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        second.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-a", first.state.value.currentSessionId)
        assertEquals("s-a", second.state.value.currentSessionId)
        assertEquals(
            ChatActionState.PENDING,
            first.state.value.messages.first { it.actionProposals.isNotEmpty() }
                .actionProposals.single().state,
        )

        val messageId = second.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id
        actions.gate = CompletableDeferred()
        second.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        // 动作在另一实例 begin 后，第一实例即使之前是 PENDING 也要同步为 EXECUTING。
        assertEquals(
            ChatActionState.EXECUTING,
            first.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )

        actions.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            ChatActionState.SUCCESS,
            first.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )
        assertEquals("done", first.state.value.messages.last().text)
    }

    @Test
    fun sendCompletionRefreshesOtherInstanceOnSameSession() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
        }
        val client = FakeCompletionClient()
        val sendRegistry = ChatSendExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(
            store = store,
            client = client,
            sendRegistry = sendRegistry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        val second = createViewModel(
            store = store,
            sendRegistry = sendRegistry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        second.switchSession("s-a")
        advanceUntilIdle()

        client.gate = CompletableDeferred()
        first.send("new message", null)
        advanceUntilIdle()
        assertTrue(sendRegistry.isActive("s-a"))
        second.deleteSession("s-a")
        advanceUntilIdle()
        assertEquals(0, store.deleteCalls)

        client.gate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(sendRegistry.isActive("s-a"))
        assertTrue(second.state.value.messages.any { it.text == "new message" })
        assertTrue(second.state.value.messages.any { it.text == "reply" })
        assertEquals("s-a", second.state.value.currentSessionId)
    }

    @Test
    fun refreshMergesActionStateWithoutLosingLocalProposalEdit() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val seeded = store.entries.getValue("s-a")
        val submitMessage = ChatUiMessage(
            id = 3L,
            role = ChatRole.ASSISTANT,
            text = "submit?",
            actionProposals = listOf(
                ChatActionProposal(
                    id = "submit-1",
                    sessionId = "s-a",
                    action = ChatTaskAction.SUBMIT,
                    promptId = "",
                    title = "template",
                    status = "ready",
                    promptOptions = listOf(
                        ChatPromptOption(id = "opt-1", label = "Positive", nodeId = "1"),
                        ChatPromptOption(id = "opt-2", label = "Other", nodeId = "2"),
                    ),
                ),
            ),
        )
        store.entries["s-a"] = seeded.copy(messages = seeded.messages + submitMessage)

        val viewModel = createViewModel(store, scope = backgroundScope)
        viewModel.switchSession("s-a")
        advanceUntilIdle()
        viewModel.selectPromptCandidate(3L, "submit-1", "opt-1")
        advanceUntilIdle()
        assertEquals(
            "opt-1",
            viewModel.state.value.messages.first { it.id == 3L }
                .actionProposals.single().promptCandidateId,
        )

        // 模拟动作所属实例保存了 SUCCESS，但本地提交建议仍是旧快照。
        val stale = store.entries.getValue("s-a")
        store.entries["s-a"] = stale.copy(
            messages = listOf(
                ChatUiMessage(id = 1L, role = ChatRole.USER, text = "A hello"),
                ChatUiMessage(
                    id = 2L,
                    role = ChatRole.ASSISTANT,
                    text = "reply",
                    actionProposals = listOf(
                        ChatActionProposal(
                            id = PROPOSAL_ID,
                            sessionId = "s-a",
                            action = ChatTaskAction.RETRY,
                            promptId = "p1",
                            title = "task",
                            status = "failed",
                            state = ChatActionState.SUCCESS,
                            resultMessage = "done",
                        ),
                    ),
                ),
                ChatUiMessage(id = 3L, role = ChatRole.ASSISTANT, text = "submit?"),
                ChatUiMessage(id = 4L, role = ChatRole.ASSISTANT, text = "done"),
            ),
            revision = stale.revision + 1L,
        )

        viewModel.refreshCurrentSessionFromStore("s-a")
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertEquals(
            ChatActionState.SUCCESS,
            messages.flatMap { it.actionProposals }
                .first { it.id == PROPOSAL_ID }
                .state,
        )
        assertEquals(
            "opt-1",
            messages.flatMap { it.actionProposals }
                .first { it.id == "submit-1" }
                .promptCandidateId,
        )
        assertEquals("done", messages.last().text)
    }

    @Test
    fun refreshKeepsLocalCancelledAndActiveExecutingStates() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val seeded = store.entries.getValue("s-a")
        store.entries["s-a"] = seeded.copy(
            messages = seeded.messages + ChatUiMessage(
                id = 3L,
                role = ChatRole.ASSISTANT,
                text = "submit?",
                actionProposals = listOf(
                    ChatActionProposal(
                        id = "submit-1",
                        sessionId = "s-a",
                        action = ChatTaskAction.SUBMIT,
                        promptId = "",
                        title = "template",
                        status = "ready",
                        promptOptions = listOf(
                            ChatPromptOption(id = "opt-1", label = "Positive", nodeId = "1"),
                        ),
                    ),
                ),
            ),
        )
        val registry = ChatActionExecutionRegistry()
        val viewModel = createViewModel(store, registry = registry, scope = backgroundScope)
        viewModel.switchSession("s-a")
        advanceUntilIdle()

        viewModel.cancelAction(3L, "submit-1")
        registry.begin("s-a", PROPOSAL_ID)
        advanceUntilIdle()
        assertEquals(
            ChatActionState.CANCELLED,
            viewModel.state.value.messages.first { it.id == 3L }
                .actionProposals.single().state,
        )
        assertEquals(
            ChatActionState.EXECUTING,
            viewModel.state.value.messages.first { it.id == 2L }
                .actionProposals.single().state,
        )

        // 远程仍是 PENDING：本地 CANCELLED 不能被回退，活跃 EXECUTING 不能被降级。
        store.entries["s-a"] = store.entries.getValue("s-a").copy(
            messages = listOf(
                ChatUiMessage(id = 1L, role = ChatRole.USER, text = "A hello"),
                ChatUiMessage(
                    id = 2L,
                    role = ChatRole.ASSISTANT,
                    text = "reply",
                    actionProposals = listOf(
                        ChatActionProposal(
                            id = PROPOSAL_ID,
                            sessionId = "s-a",
                            action = ChatTaskAction.RETRY,
                            promptId = "p1",
                            title = "task",
                            status = "failed",
                            state = ChatActionState.PENDING,
                        ),
                    ),
                ),
                ChatUiMessage(
                    id = 3L,
                    role = ChatRole.ASSISTANT,
                    text = "submit?",
                    actionProposals = listOf(
                        ChatActionProposal(
                            id = "submit-1",
                            sessionId = "s-a",
                            action = ChatTaskAction.SUBMIT,
                            promptId = "",
                            title = "template",
                            status = "ready",
                            state = ChatActionState.PENDING,
                        ),
                    ),
                ),
            ),
            revision = store.entries.getValue("s-a").revision + 1L,
        )

        viewModel.refreshCurrentSessionFromStore("s-a")
        advanceUntilIdle()

        assertEquals(
            ChatActionState.CANCELLED,
            viewModel.state.value.messages.first { it.id == 3L }
                .actionProposals.single().state,
        )
        assertEquals(
            ChatActionState.EXECUTING,
            viewModel.state.value.messages.first { it.id == 2L }
                .actionProposals.single().state,
        )
        registry.finish("s-a", PROPOSAL_ID)
    }

    @Test
    fun mutationOnOneSessionDoesNotBlockSendOnAnotherAtViewModelLevel() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val client = FakeCompletionClient()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(store, operationGate = operationGate, scope = backgroundScope)
        val second = createViewModel(
            store = store,
            client = client,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        store.loadGates["s-a"] = CompletableDeferred()
        first.switchSession("s-a")
        advanceUntilIdle()
        assertNull(first.state.value.currentSessionId)

        // s-a 的 switch mutation 不应阻塞 s-b 的 send。
        second.switchSession("s-b")
        advanceUntilIdle()
        assertEquals("s-b", second.state.value.currentSessionId)
        client.gate = CompletableDeferred()
        second.send("B message", null)
        advanceUntilIdle()
        assertTrue(second.state.value.sending)

        store.loadGates["s-a"]?.complete(Unit)
        client.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals("s-a", first.state.value.currentSessionId)
    }

    @Test
    fun sameSessionMutationBlocksSendAtViewModelLevel() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
        }
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(store, operationGate = operationGate, scope = backgroundScope)
        val second = createViewModel(store, operationGate = operationGate, scope = backgroundScope)
        second.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-a", second.state.value.currentSessionId)

        store.loadGates["s-a"] = CompletableDeferred()
        first.switchSession("s-a")
        advanceUntilIdle()

        second.send("blocked", null)
        advanceUntilIdle()
        assertFalse(second.state.value.sending)
        assertEquals("s-a", second.state.value.currentSessionId)

        store.loadGates["s-a"]?.complete(Unit)
        advanceUntilIdle()
        assertEquals("s-a", first.state.value.currentSessionId)
    }

    @Test
    fun disjointMutationsCanRunConcurrentlyAtViewModelLevel() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(store, operationGate = operationGate, scope = backgroundScope)
        val second = createViewModel(store, operationGate = operationGate, scope = backgroundScope)
        store.loadGates["s-a"] = CompletableDeferred()
        store.loadGates["s-b"] = CompletableDeferred()

        first.switchSession("s-a")
        second.switchSession("s-b")
        advanceUntilIdle()
        assertNull(first.state.value.currentSessionId)
        assertNull(second.state.value.currentSessionId)

        store.loadGates["s-a"]?.complete(Unit)
        store.loadGates["s-b"]?.complete(Unit)
        advanceUntilIdle()
        assertEquals("s-a", first.state.value.currentSessionId)
        assertEquals("s-b", second.state.value.currentSessionId)
    }

    @Test
    fun fastActionCompletionRefreshesPendingState() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
        }
        val registry = ChatActionExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(
            store = store,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        val second = createViewModel(
            store = store,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        second.switchSession("s-a")
        advanceUntilIdle()
        val messageId = second.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id

        // 不挂起动作，begin/finish 可能在 activeState 中被合并。
        second.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        assertEquals(
            ChatActionState.SUCCESS,
            first.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )
        assertEquals("done", first.state.value.messages.last().text)
    }

    @Test
    fun differentSessionsCanSendConcurrently() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.CHAT, proposal = false)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
        }
        val clientA = FakeCompletionClient()
        val clientB = FakeCompletionClient()
        val sendRegistry = ChatSendExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(
            store = store,
            client = clientA,
            sendRegistry = sendRegistry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        val second = createViewModel(
            store = store,
            client = clientB,
            sendRegistry = sendRegistry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        second.switchSession("s-b")
        advanceUntilIdle()

        clientA.gate = CompletableDeferred()
        clientB.gate = CompletableDeferred()
        first.send("A message", null)
        second.send("B message", null)
        advanceUntilIdle()

        assertTrue(first.state.value.sending)
        assertTrue(second.state.value.sending)
        assertTrue(sendRegistry.isActive("s-a"))
        assertTrue(sendRegistry.isActive("s-b"))

        clientA.gate?.complete(Unit)
        clientB.gate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(first.state.value.sending)
        assertFalse(second.state.value.sending)
        assertEquals("s-a", first.state.value.currentSessionId)
        assertEquals("s-b", second.state.value.currentSessionId)
        assertTrue(first.state.value.messages.any { it.text == "A message" })
        assertTrue(second.state.value.messages.any { it.text == "B message" })
    }

    @Test
    fun unrelatedSessionSwitchIsAllowedWhileActionRuns() = runChatTest {
        val store = FakeSessionStore().apply {
            seed("s-a", "A hello", ChatMode.AGENT, proposal = true)
            seed("s-b", "B hello", ChatMode.CHAT, proposal = false)
            seed("s-c", "C hello", ChatMode.CHAT, proposal = false)
        }
        val actions = FakeActionExecutor()
        val registry = ChatActionExecutionRegistry()
        val operationGate = ChatSessionOperationGate()
        val first = createViewModel(
            store = store,
            actions = actions,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        val second = createViewModel(
            store = store,
            registry = registry,
            operationGate = operationGate,
            scope = backgroundScope,
        )
        first.switchSession("s-a")
        advanceUntilIdle()
        val messageId = first.state.value.messages
            .first { it.actionProposals.isNotEmpty() }
            .id
        actions.gate = CompletableDeferred()
        first.confirmAction(messageId, PROPOSAL_ID)
        advanceUntilIdle()

        second.switchSession("s-b")
        advanceUntilIdle()
        assertEquals("s-b", second.state.value.currentSessionId)
        second.switchSession("s-c")
        advanceUntilIdle()
        assertEquals("s-c", second.state.value.currentSessionId)

        // 涉及 A 的切换仍被动作阻止。
        second.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-c", second.state.value.currentSessionId)

        actions.gate?.complete(Unit)
        advanceUntilIdle()
        second.switchSession("s-a")
        advanceUntilIdle()
        assertEquals("s-a", second.state.value.currentSessionId)
        assertEquals(
            ChatActionState.SUCCESS,
            second.state.value.messages.first { it.id == messageId }
                .actionProposals.single().state,
        )
    }

    private fun createViewModel(
        store: FakeSessionStore,
        actions: FakeActionExecutor = FakeActionExecutor(),
        client: FakeCompletionClient = FakeCompletionClient(),
        registry: ChatActionExecutionRegistry = ChatActionExecutionRegistry(),
        sendRegistry: ChatSendExecutionRegistry = ChatSendExecutionRegistry(),
        operationGate: ChatSessionOperationGate = ChatSessionOperationGate(),
        scope: CoroutineScope,
        operationScope: CoroutineScope = scope,
    ): ChatViewModel = ChatViewModel(
        configFlow = flowOf(CONFIGURED),
        defaultAgentModeFlow = flowOf(false),
        client = client,
        imageEncoder = FakeImageEncoder,
        imageStore = FakeImageStorage,
        contextBuilder = FakeContextSource,
        toolRegistry = FakeToolRuntime,
        historyStore = store,
        actionHandler = actions,
        appScope = scope,
        operationScope = operationScope,
        actionRegistry = registry,
        sendRegistry = sendRegistry,
        operationGate = operationGate,
        privacySanitizer = FakePrivacyGuard,
    )

    private fun runChatTest(block: suspend TestScope.() -> Unit) = runTest(mainDispatcher) {
        block()
    }

    private class FakeSessionStore : ChatSessionStore {
        data class Entry(
            val messages: List<ChatUiMessage>,
            val promptId: String?,
            val title: String,
            val mode: ChatMode,
            val pinned: Boolean,
            val revision: Long,
            val deleted: Boolean = false,
        )

        val entries = linkedMapOf<String, Entry>()
        var saveGate: CompletableDeferred<Unit>? = null
        var loadGate: CompletableDeferred<Unit>? = null
        val loadGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var deleteCalls = 0
        var nextSaveFailure: ChatSessionSaveResult? = null
        var nextDeleteFailure: ChatSessionSaveResult? = null
        var nextLoadFailure: Throwable? = null

        fun seed(
            sessionId: String,
            text: String,
            mode: ChatMode,
            proposal: Boolean,
        ) {
            val messages = buildList {
                add(ChatUiMessage(id = 1L, role = ChatRole.USER, text = text))
                add(
                    ChatUiMessage(
                        id = 2L,
                        role = ChatRole.ASSISTANT,
                        text = "reply",
                        actionProposals = if (proposal) {
                            listOf(
                                ChatActionProposal(
                                    id = PROPOSAL_ID,
                                    sessionId = sessionId,
                                    action = ChatTaskAction.RETRY,
                                    promptId = "p1",
                                    title = "task",
                                    status = "failed",
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    ),
                )
            }
            entries[sessionId] = Entry(
                messages = messages,
                promptId = null,
                title = sessionId,
                mode = mode,
                pinned = false,
                revision = 1L,
            )
        }

        override suspend fun listSessions(): List<ChatSessionSummary> =
            entries.filterValues { !it.deleted }.map { (id, entry) ->
                ChatSessionSummary(
                    id = id,
                    title = entry.title,
                    promptId = entry.promptId,
                    updatedAt = entry.revision,
                    messageCount = entry.messages.size,
                    pinned = entry.pinned,
                    mode = entry.mode,
                    revision = entry.revision,
                )
            }

        override suspend fun loadSession(
            sessionId: String,
            activeProposalIds: Set<String>,
        ): List<ChatUiMessage> {
            // 先读取快照再挂起，模拟“load 期间动作完成”的交错。
            val entry = entries[sessionId]?.takeIf { !it.deleted }
            nextLoadFailure?.let { failure ->
                nextLoadFailure = null
                throw failure
            }
            loadGate?.await()
            loadGates[sessionId]?.await()
            if (entry == null) return emptyList()
            return entry.messages.map { message ->
                message.copy(
                    actionProposals = message.actionProposals.map { proposal ->
                        when {
                            proposal.id in activeProposalIds -> proposal.copy(
                                state = ChatActionState.EXECUTING,
                                resultMessage = null,
                            )

                            proposal.state == ChatActionState.EXECUTING -> proposal.copy(
                                state = ChatActionState.FAILED,
                                resultMessage = "unknown",
                            )

                            else -> proposal
                        }
                    },
                )
            }
        }

        override suspend fun maxRevision(): Long =
            entries.values.maxOfOrNull { it.revision } ?: 0L

        override suspend fun saveSession(
            sessionId: String,
            promptId: String?,
            title: String,
            mode: ChatMode,
            messages: List<ChatUiMessage>,
            revision: Long,
        ): ChatSessionSaveResult {
            saveGate?.await()
            nextSaveFailure?.let { failure ->
                nextSaveFailure = null
                return failure
            }
            val existing = entries[sessionId]
            if (existing?.deleted == true) {
                return ChatSessionSaveResult.Rejected("会话已删除")
            }
            if (existing != null && revision <= existing.revision) {
                return ChatSessionSaveResult.Rejected("会话已有更新的版本")
            }
            entries[sessionId] = Entry(
                messages = messages.toList(),
                promptId = promptId,
                title = title,
                mode = mode,
                pinned = existing?.pinned ?: false,
                revision = revision,
            )
            return ChatSessionSaveResult.Saved(revision)
        }

        override suspend fun saveSessionWithNextRevision(
            sessionId: String,
            promptId: String?,
            title: String,
            mode: ChatMode,
            messages: List<ChatUiMessage>,
        ): ChatSessionSaveResult = saveSession(
            sessionId = sessionId,
            promptId = promptId,
            title = title,
            mode = mode,
            messages = messages,
            revision = maxRevision() + 1L,
        )

        override suspend fun deleteSession(sessionId: String): ChatSessionSaveResult {            deleteCalls++
            nextDeleteFailure?.let { failure ->
                nextDeleteFailure = null
                return failure
            }
            val existing = entries[sessionId]
                ?: return ChatSessionSaveResult.Unchanged(0L)
            entries[sessionId] = existing.copy(
                messages = emptyList(),
                deleted = true,
                revision = existing.revision + 1L,
            )
            return ChatSessionSaveResult.Saved(existing.revision + 1L)
        }

        override suspend fun renameSession(sessionId: String, title: String) {
            entries[sessionId]?.let { entry ->
                entries[sessionId] = entry.copy(title = title, revision = entry.revision + 1L)
            }
        }

        override suspend fun setPinned(sessionId: String, pinned: Boolean) {
            entries[sessionId]?.let { entry ->
                entries[sessionId] = entry.copy(pinned = pinned, revision = entry.revision + 1L)
            }
        }
    }

    private class FakeActionExecutor : ChatActionExecutor {
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        var result: Result<String> = Result.success("done")
        var throwOnExecute: Throwable? = null

        override suspend fun execute(proposal: ChatActionProposal): Result<String> {
            calls++
            gate?.await()
            throwOnExecute?.let { throw it }
            return result
        }

        override suspend fun savePreset(
            proposal: ChatActionProposal,
            name: String,
        ): Result<String> = Result.success("saved")
    }

    private class FakeCompletionClient : ChatCompletionClient {
        var gate: CompletableDeferred<Unit>? = null
        var reply: String = "reply"

        override suspend fun chat(
            config: AiAssistantConfig,
            messages: List<AiChatMessage>,
        ): Result<String> = Result.success(reply)

        override suspend fun chatWithTools(
            config: AiAssistantConfig,
            messages: List<AiChatMessage>,
            tools: List<AiToolDefinition>,
            toolChoice: String?,
        ): Result<AiChatCompletion> = Result.success(
            AiChatCompletion(content = reply, toolCalls = emptyList()),
        )

        override suspend fun chatWithToolsStream(
            config: AiAssistantConfig,
            messages: List<AiChatMessage>,
            tools: List<AiToolDefinition>,
            toolChoice: String?,
            onDelta: (String) -> Unit,
        ): Result<AiChatCompletion> {
            gate?.await()
            onDelta(reply)
            return Result.success(AiChatCompletion(content = reply, toolCalls = emptyList()))
        }
    }

    private object FakeImageEncoder : ChatImageEncoderApi {
        override suspend fun toDataUrl(
            uri: Uri,
            maxDimension: Int,
            quality: Int,
        ): Result<String> = Result.failure(IllegalStateException("unused"))
    }

    private object FakeImageStorage : ChatImageStorage {
        override suspend fun save(id: String, dataUrl: String) = Unit

        override suspend fun load(id: String): String? = null

        override suspend fun delete(ids: Collection<String>) = Unit
    }

    private object FakeContextSource : ChatContextSource {
        override suspend fun build(intent: ChatIntent, userText: String): String? = null

        override fun taskContext(promptId: String): ChatTaskContext? = null
    }

    private object FakeToolRuntime : ChatToolRuntime {
        override fun definitions(mode: ChatMode): List<AiToolDefinition> = emptyList()

        override suspend fun execute(
            call: AiToolCall,
            fallbackPromptId: String?,
            attachments: List<ChatImageAttachment>,
            direct: Boolean,
        ): ChatToolExecution = ChatToolExecution(content = "unused", isError = false)

        override fun describe(call: AiToolCall, fallbackPromptId: String?): ChatToolActivity =
            ChatToolActivity(name = call.name)
    }

    private object FakePrivacyGuard : ChatPrivacyGuard {
        override suspend fun awaitReady(): Boolean = true

        override fun sanitize(text: String): String = text
    }

    private companion object {
        const val PROPOSAL_ID = "proposal-1"
        val CONFIGURED = AiAssistantConfig(
            baseUrl = "http://127.0.0.1:8188",
            textModel = "test-model",
        )
    }
}
