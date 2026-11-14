package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModeTest {

    @Test
    fun `chat mode only allows read only tools`() {
        assertTrue(ChatToolPolicy.allows("get_queue_snapshot", ChatMode.CHAT))
        assertTrue(ChatToolPolicy.allows("get_history_images", ChatMode.CHAT))
        assertTrue(ChatToolPolicy.allows("get_workflow_parameters", ChatMode.CHAT))
        assertTrue(ChatToolPolicy.allows("analyze_image", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allows("propose_task_action", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allows("propose_submit_task", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allows("propose_batch_task_action", ChatMode.CHAT))
        assertFalse(ChatToolPolicy.allows("propose_install_missing_nodes", ChatMode.CHAT))
    }

    @Test
    fun `agent mode allows write tools`() {
        assertTrue(ChatToolPolicy.allows("propose_task_action", ChatMode.AGENT))
        assertTrue(ChatToolPolicy.allows("propose_submit_task", ChatMode.AGENT))
        assertTrue(ChatToolPolicy.allows("propose_batch_task_action", ChatMode.AGENT))
        assertTrue(ChatToolPolicy.allows("propose_install_missing_nodes", ChatMode.AGENT))
    }

    @Test
    fun `read only and write tool sets do not overlap`() {
        assertTrue(ChatToolPolicy.readOnlyTools.intersect(ChatToolPolicy.writeTools).isEmpty())
    }
}
