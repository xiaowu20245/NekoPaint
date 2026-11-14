package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStartupPolicyTest {

    private val globalSession = ChatSessionSummary(
        id = "global-1",
        title = "上一次对话",
        promptId = null,
        updatedAt = 100L,
        messageCount = 4,
        pinned = true,
    )

    private val taskSession = ChatSessionSummary(
        id = "task-1",
        title = "任务对话",
        promptId = "task-1",
        updatedAt = 90L,
        messageCount = 2,
        mode = ChatMode.AGENT,
    )

    @Test
    fun `normal entry always starts a blank chat session`() {
        val session = ChatStartupPolicy.initialSession(
            contextPromptId = null,
            sessions = listOf(globalSession, taskSession),
        )

        assertNull(session)
        assertEquals(ChatMode.CHAT, ChatStartupPolicy.initialMode(session))
    }

    @Test
    fun `task entry also starts a blank chat session`() {
        val session = ChatStartupPolicy.initialSession(
            contextPromptId = "task-1",
            sessions = listOf(globalSession, taskSession),
        )

        assertNull(session)
        assertEquals(ChatMode.CHAT, ChatStartupPolicy.initialMode(session))
    }

    @Test
    fun `task entry without history starts a blank chat session`() {
        val session = ChatStartupPolicy.initialSession(
            contextPromptId = "task-2",
            sessions = listOf(globalSession, taskSession),
        )

        assertNull(session)
        assertEquals(ChatMode.CHAT, ChatStartupPolicy.initialMode(session))
    }
}
