package io.github.xiaowu20245.nekopaint.feature.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import io.github.xiaowu20245.nekopaint.core.network.AiToolDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScreenBackInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatScreenRegistersActionBackGate() {
        val app = ApplicationProvider.getApplicationContext<NekoPaintApplication>()
        val store = FakeStore()
        val registry = ChatActionExecutionRegistry()
        val viewModel = ChatViewModel(
            configFlow = flowOf(
                AiAssistantConfig(
                    baseUrl = "http://127.0.0.1:8188",
                    textModel = "test-model",
                ),
            ),
            defaultAgentModeFlow = flowOf(false),
            client = app.container.aiAssistantClient,
            imageEncoder = app.container.chatImageEncoder,
            imageStore = app.container.chatImageStore,
            contextBuilder = FakeContextSource,
            toolRegistry = FakeToolRuntime,
            historyStore = store,
            actionHandler = FakeActionExecutor,
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            actionRegistry = registry,
            sendRegistry = ChatSendExecutionRegistry(),
            operationGate = ChatSessionOperationGate(),
            privacySanitizer = app.container.aiPrivacySanitizer,
        )

        runBlocking {
            viewModel.switchSession("s-a")
            withTimeout(5_000) {
                viewModel.state.first { it.currentSessionId == "s-a" }
            }
        }
        assertTrue(
            viewModel.state.value.messages
                .flatMap { it.actionProposals }
                .any { it.state == ChatActionState.EXECUTING },
        )

        var handler: (() -> Boolean)? = null
        compose.setContent {
            ChatScreen(
                onOpenAiSettings = {},
                onBackHandlerChange = { handler = it },
                viewModelOverride = viewModel,
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) { handler != null }
        assertNotNull(handler)
        compose.runOnIdle { assertTrue(handler?.invoke() == true) }

        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertFalse(compose.activity.isFinishing)
    }

    private class FakeStore : ChatSessionStore {
        private val message = ChatUiMessage(
            id = 1L,
            role = ChatRole.ASSISTANT,
            text = "需要我重试吗？",
            actionProposals = listOf(
                ChatActionProposal(
                    id = "proposal-1",
                    sessionId = "s-a",
                    action = ChatTaskAction.RETRY,
                    promptId = "p1",
                    title = "task",
                    status = "failed",
                    state = ChatActionState.EXECUTING,
                ),
            ),
        )

        override suspend fun listSessions(): List<ChatSessionSummary> = listOf(
            ChatSessionSummary(
                id = "s-a",
                title = "A",
                promptId = null,
                updatedAt = 1L,
                messageCount = 1,
                pinned = false,
                mode = ChatMode.AGENT,
                revision = 1L,
            ),
        )

        override suspend fun loadSession(
            sessionId: String,
            activeProposalIds: Set<String>,
        ): List<ChatUiMessage> = if (sessionId == "s-a") listOf(message) else emptyList()

        override suspend fun maxRevision(): Long = 1L

        override suspend fun saveSession(
            sessionId: String,
            promptId: String?,
            title: String,
            mode: ChatMode,
            messages: List<ChatUiMessage>,
            revision: Long,
        ): ChatSessionSaveResult = ChatSessionSaveResult.Saved(revision)

        override suspend fun saveSessionWithNextRevision(
            sessionId: String,
            promptId: String?,
            title: String,
            mode: ChatMode,
            messages: List<ChatUiMessage>,
        ): ChatSessionSaveResult = ChatSessionSaveResult.Saved(2L)

        override suspend fun deleteSession(sessionId: String): ChatSessionSaveResult =
            ChatSessionSaveResult.Saved(2L)

        override suspend fun renameSession(sessionId: String, title: String) = Unit

        override suspend fun setPinned(sessionId: String, pinned: Boolean) = Unit
    }

    private object FakeActionExecutor : ChatActionExecutor {
        override suspend fun execute(proposal: ChatActionProposal): Result<String> =
            Result.success("done")

        override suspend fun savePreset(
            proposal: ChatActionProposal,
            name: String,
        ): Result<String> = Result.success("saved")
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
}
