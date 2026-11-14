package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryCodecTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `round trips text and tool activity without image data`() {
        val activity = ChatToolActivity(
            name = "get_recent_history",
            status = "failed",
            limit = 3,
            isError = true,
        )
        val messages = listOf(
            ChatUiMessage(
                id = 1,
                role = ChatRole.USER,
                text = "看看最近的失败记录",
                imageDataUrl = "data:image/png;base64,AAAA",
            ),
            ChatUiMessage(
                id = 2,
                role = ChatRole.ASSISTANT,
                text = "最近没有失败记录。",
                toolActivities = listOf(activity),
            ),
        )

        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = "demo-hist-001",
            title = "任务 A",
            mode = ChatMode.CHAT,
            messages = messages,
        )

        assertFalse(encoded.orEmpty().contains("data:image"))
        val summaries = ChatHistoryCodec.listSessions(json, encoded)
        assertEquals(1, summaries.size)
        assertEquals("任务 A", summaries.single().title)
        assertEquals("demo-hist-001", summaries.single().promptId)
        assertEquals(2, summaries.single().messageCount)

        val decoded = ChatHistoryCodec.loadSession(json, encoded, "session-1")
        assertEquals(2, decoded.size)
        assertEquals("看看最近的失败记录", decoded[0].text)
        assertNull(decoded[0].imageDataUrl)
        assertEquals(listOf(activity), decoded[1].toolActivities)
    }

    @Test
    fun `sessions are stored and deleted independently`() {
        val global = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "global-1",
            promptId = null,
            title = "普通会话",
            mode = ChatMode.CHAT,
            messages = listOf(ChatUiMessage(1, ChatRole.USER, "global")),
        )
        val withTask = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = global,
            sessionId = "task-1",
            promptId = "task-1",
            title = "任务会话",
            mode = ChatMode.CHAT,
            messages = listOf(ChatUiMessage(1, ChatRole.USER, "task")),
        )

        assertEquals(2, ChatHistoryCodec.listSessions(json, withTask).size)
        assertEquals(
            "global",
            ChatHistoryCodec.loadSession(json, withTask, "global-1").single().text,
        )
        assertEquals(
            "task",
            ChatHistoryCodec.loadSession(json, withTask, "task-1").single().text,
        )

        val afterDelete = ChatHistoryCodec.deleteSession(json, withTask, "task-1")
        assertEquals(1, ChatHistoryCodec.listSessions(json, afterDelete).size)
        assertTrue(ChatHistoryCodec.loadSession(json, afterDelete, "task-1").isEmpty())
        assertEquals(
            "global",
            ChatHistoryCodec.loadSession(json, afterDelete, "global-1").single().text,
        )
    }

    @Test
    fun `renames and pins sessions`() {
        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = null,
            title = "旧标题",
            mode = ChatMode.CHAT,
            messages = listOf(ChatUiMessage(1, ChatRole.USER, "hi")),
        )

        val renamed = ChatHistoryCodec.renameSession(
            json = json,
            existingRaw = encoded,
            sessionId = "session-1",
            title = "新标题",
        )
        val pinned = ChatHistoryCodec.setPinned(
            json = json,
            existingRaw = renamed,
            sessionId = "session-1",
            pinned = true,
        )
        val resaved = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = pinned,
            sessionId = "session-1",
            promptId = null,
            title = "不应覆盖",
            mode = ChatMode.CHAT,
            messages = listOf(
                ChatUiMessage(1, ChatRole.USER, "hi"),
                ChatUiMessage(2, ChatRole.ASSISTANT, "hello"),
            ),
        )

        val summary = ChatHistoryCodec.listSessions(json, resaved).single()
        assertEquals("新标题", summary.title)
        assertTrue(summary.pinned)
        assertEquals(2, summary.messageCount)
    }

    @Test
    fun `persists action proposals and resets executing state`() {
        val proposal = ChatActionProposal(
            id = "proposal-1",
            action = ChatTaskAction.RETRY,
            promptId = "demo-hist-001",
            title = "任务 A",
            status = "failed",
            state = ChatActionState.EXECUTING,
        )
        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = "demo-hist-001",
            title = "任务 A",
            mode = ChatMode.CHAT,
            messages = listOf(
                ChatUiMessage(
                    id = 1,
                    role = ChatRole.ASSISTANT,
                    text = "需要我重试吗？",
                    actionProposals = listOf(proposal),
                ),
            ),
        )

        val restored = ChatHistoryCodec.loadSession(json, encoded, "session-1")
            .single()
            .actionProposals
            .single()

        assertEquals(ChatTaskAction.RETRY, restored.action)
        assertEquals("demo-hist-001", restored.promptId)
        assertEquals(ChatActionState.FAILED, restored.state)
        assertTrue(restored.resultMessage.orEmpty().isNotBlank())
    }

    @Test
    fun `persists node install proposal packages`() {
        val proposal = ChatActionProposal(
            id = "proposal-install",
            action = ChatTaskAction.INSTALL_NODES,
            promptId = "",
            title = "Anima T2I",
            status = "missing_nodes",
            workflowId = "workflow-1",
            installPackages = listOf(
                NodeInstallPackage(
                    id = "pack-a",
                    title = "Pack A",
                    files = listOf("https://github.com/a/a.git"),
                ),
            ),
            unresolvedNodeTypes = listOf("NodeX"),
            missingNodeTypeCount = 2,
            restartAfterInstall = false,
        )
        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = null,
            title = "安装",
            mode = ChatMode.AGENT,
            messages = listOf(
                ChatUiMessage(
                    id = 1,
                    role = ChatRole.ASSISTANT,
                    text = "需要安装节点。",
                    actionProposals = listOf(proposal),
                ),
            ),
        )

        val restored = ChatHistoryCodec.loadSession(json, encoded, "session-1")
            .single()
            .actionProposals
            .single()

        assertEquals(ChatTaskAction.INSTALL_NODES, restored.action)
        assertEquals("Pack A", restored.installPackages.single().title)
        assertEquals(listOf("NodeX"), restored.unresolvedNodeTypes)
        assertEquals(2, restored.missingNodeTypeCount)
        assertEquals(false, restored.restartAfterInstall)
    }

    @Test
    fun `persists history images`() {
        val image = ChatHistoryImage(
            url = "http://host/image.png",
            filename = "image.png",
            type = "output",
            promptId = "task-1",
        )
        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = "task-1",
            title = "任务 A",
            mode = ChatMode.CHAT,
            messages = listOf(
                ChatUiMessage(
                    id = 1,
                    role = ChatRole.ASSISTANT,
                    text = "这是生成的图片。",
                    historyImages = listOf(image),
                ),
            ),
        )

        val restored = ChatHistoryCodec.loadSession(json, encoded, "session-1")
            .single()
            .historyImages
            .single()

        assertEquals("image.png", restored.filename)
        assertEquals("task-1", restored.promptId)
        assertTrue(restored.isOutput)
    }

    @Test
    fun `persists session mode`() {
        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = null,
            title = "Agent 会话",
            mode = ChatMode.AGENT,
            messages = listOf(ChatUiMessage(1, ChatRole.USER, "hi")),
        )

        val summary = ChatHistoryCodec.listSessions(json, encoded).single()

        assertEquals(ChatMode.AGENT, summary.mode)
    }

    @Test
    fun `persists session mode changes`() {
        val chat = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = null,
            title = "会话",
            mode = ChatMode.CHAT,
            messages = listOf(ChatUiMessage(1, ChatRole.USER, "hi")),
        )
        val agent = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = chat,
            sessionId = "session-1",
            promptId = null,
            title = "会话",
            mode = ChatMode.AGENT,
            messages = listOf(
                ChatUiMessage(1, ChatRole.USER, "hi"),
                ChatUiMessage(2, ChatRole.ASSISTANT, "hello"),
            ),
        )

        assertEquals(ChatMode.AGENT, ChatHistoryCodec.listSessions(json, agent).single().mode)
        assertEquals(2, ChatHistoryCodec.loadSession(json, agent, "session-1").size)
    }

    @Test
    fun `caps stored messages to the newest hundred`() {
        val messages = (1..120).map { index ->
            ChatUiMessage(
                id = index.toLong(),
                role = ChatRole.USER,
                text = "message-$index",
            )
        }

        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = null,
            title = "",
            mode = ChatMode.CHAT,
            messages = messages,
        )
        val decoded = ChatHistoryCodec.loadSession(json, encoded, "session-1")

        assertEquals(100, decoded.size)
        assertEquals("message-21", decoded.first().text)
        assertEquals("message-120", decoded.last().text)
    }

    @Test
    fun `migrates legacy global conversation`() {
        val legacy = """
            {
              "version": 1,
              "promptId": null,
              "messages": [
                {"role": "USER", "text": "旧会话"}
              ]
            }
        """.trimIndent()

        val summaries = ChatHistoryCodec.listSessions(json, legacy)

        assertEquals(1, summaries.size)
        assertEquals("旧会话", summaries.single().title)
        assertEquals(
            "旧会话",
            ChatHistoryCodec.loadSession(json, legacy, "legacy-global").single().text,
        )
    }

    @Test
    fun `migrates legacy task conversations`() {
        val legacy = """
            {
              "version": 2,
              "items": {
                "__global__": {
                  "promptId": null,
                  "updatedAt": 1,
                  "messages": [{"role": "USER", "text": "全局"}]
                },
                "task-1": {
                  "promptId": "task-1",
                  "updatedAt": 2,
                  "messages": [{"role": "USER", "text": "任务"}]
                }
              }
            }
        """.trimIndent()

        val summaries = ChatHistoryCodec.listSessions(json, legacy)

        assertEquals(2, summaries.size)
        assertEquals("任务", summaries.first().title)
        assertEquals(
            "全局",
            ChatHistoryCodec.loadSession(json, legacy, "legacy-global").single().text,
        )
        assertEquals(
            "任务",
            ChatHistoryCodec.loadSession(json, legacy, "task-1").single().text,
        )
    }

    @Test
    fun `invalid json returns empty list`() {
        assertTrue(ChatHistoryCodec.listSessions(json, "not-json").isEmpty())
    }

    @Test
    fun `persists attached image id`() {
        val encoded = ChatHistoryCodec.saveSession(
            json = json,
            existingRaw = null,
            sessionId = "session-1",
            promptId = null,
            title = "看图",
            mode = ChatMode.CHAT,
            messages = listOf(
                ChatUiMessage(
                    id = 1,
                    role = ChatRole.USER,
                    text = "看看这张图",
                    imageId = "img-1",
                ),
            ),
        )

        val restored = ChatHistoryCodec.loadSession(json, encoded, "session-1").single()

        assertEquals("img-1", restored.imageId)
    }

    @Test
    fun `blank messages are not stored`() {
        assertNull(
            ChatHistoryCodec.saveSession(
                json = json,
                existingRaw = null,
                sessionId = "session-1",
                promptId = null,
                title = "空会话",
                mode = ChatMode.CHAT,
                messages = listOf(ChatUiMessage(1, ChatRole.ASSISTANT, text = "")),
            ),
        )
    }
}
