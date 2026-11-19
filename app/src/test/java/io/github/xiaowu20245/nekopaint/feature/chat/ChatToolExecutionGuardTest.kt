package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatToolExecutionGuardTest {

    @Test
    fun chatModeRejectsWriteToolWithoutProposal() {
        val rejection = ChatToolExecutionGuard.rejection(
            mode = ChatMode.CHAT,
            toolName = "propose_task_action",
        )

        assertNotNull(rejection)
        assertTrue(rejection!!.isError)
        assertNull(rejection.actionProposal)
    }

    @Test
    fun bothModesRejectUnknownTool() {
        assertTrue(ChatToolExecutionGuard.rejection(ChatMode.CHAT, "unknown_tool")!!.isError)
        assertTrue(ChatToolExecutionGuard.rejection(ChatMode.AGENT, "unknown_tool")!!.isError)
    }

    @Test
    fun readOnlyAndAgentWriteToolsEnterExecutionPath() {
        assertNull(ChatToolExecutionGuard.rejection(ChatMode.CHAT, "get_queue_snapshot"))
        assertNull(ChatToolExecutionGuard.rejection(ChatMode.AGENT, "propose_task_action"))
        assertFalse(
            ChatToolExecutionGuard.rejection(ChatMode.CHAT, "get_queue_snapshot")?.isError == true,
        )
    }
}
