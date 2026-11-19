package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatToolPolicyTest {

    @Test
    fun chatModeAllowsReadOnlyAndRejectsWriteTools() {
        assertTrue(ChatToolPolicy.allowsExecution("get_queue_snapshot", ChatMode.CHAT))
        assertTrue(ChatToolPolicy.allowsExecution("analyze_image", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allowsExecution("propose_task_action", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allowsExecution("propose_submit_task", ChatMode.CHAT))
    }

    @Test
    fun agentModeAllowsReadOnlyAndWriteTools() {
        assertTrue(ChatToolPolicy.allowsExecution("get_queue_snapshot", ChatMode.AGENT))
        assertTrue(ChatToolPolicy.allowsExecution("propose_task_action", ChatMode.AGENT))
        assertTrue(ChatToolPolicy.allowsExecution("propose_install_missing_nodes", ChatMode.AGENT))
    }

    @Test
    fun unknownToolIsRejectedInBothModes() {
        assertFalse(ChatToolPolicy.allowsExecution("unknown_tool", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allowsExecution("unknown_tool", ChatMode.AGENT))
        assertFalse(ChatToolPolicy.allows("unknown_tool", ChatMode.AGENT))
    }

    @Test
    fun allowsMatchesAllowsExecution() {
        ChatToolPolicy.readOnlyTools.forEach { name ->
            assertTrue(ChatToolPolicy.allows(name, ChatMode.CHAT))
            assertTrue(ChatToolPolicy.allows(name, ChatMode.AGENT))
        }
        ChatToolPolicy.writeTools.forEach { name ->
            assertFalse(ChatToolPolicy.allows(name, ChatMode.CHAT))
            assertTrue(ChatToolPolicy.allows(name, ChatMode.AGENT))
        }
    }
}
