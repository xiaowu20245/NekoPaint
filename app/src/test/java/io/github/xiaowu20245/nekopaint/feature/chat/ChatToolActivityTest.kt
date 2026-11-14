package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatToolActivityTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `reads diagnose prompt id from arguments`() {
        val activity = describeToolCall(
            json = json,
            call = call("diagnose_task", """{"promptId":"abc123456789"}"""),
        )

        assertEquals("abc123456789", activity.promptId)
        assertEquals("diagnose_task", activity.name)
    }

    @Test
    fun `uses linked task when diagnose arguments are empty`() {
        val activity = describeToolCall(
            json = json,
            call = call("diagnose_task", "{}"),
            fallbackPromptId = "linked-task",
        )

        assertEquals("linked-task", activity.promptId)
    }

    @Test
    fun `reads history filters`() {
        val activity = describeToolCall(
            json = json,
            call = call("get_recent_history", """{"limit":3,"status":"failed"}"""),
        )

        assertEquals(3, activity.limit)
        assertEquals("failed", activity.status)
        assertNull(activity.promptId)
    }

    @Test
    fun `invalid arguments fall back to defaults`() {
        val activity = describeToolCall(
            json = json,
            call = call("get_workflow_summary", "not-json"),
        )

        assertEquals("get_workflow_summary", activity.name)
        assertNull(activity.keyword)
        assertNull(activity.limit)
    }

    @Test
    fun `reads image analysis arguments`() {
        val activity = describeToolCall(
            json = json,
            call = call("analyze_image", """{"imageId":"img-1","mode":"tags"}"""),
        )

        assertEquals("analyze_image", activity.name)
        assertEquals("img-1", activity.imageId)
        assertEquals("tags", activity.mode)
    }

    @Test
    fun `parses supported task actions`() {
        assertEquals(ChatTaskAction.RETRY, ChatTaskAction.fromApiValue("retry"))
        assertEquals(ChatTaskAction.INTERRUPT, ChatTaskAction.fromApiValue("interrupt"))
        assertEquals(ChatTaskAction.DELETE, ChatTaskAction.fromApiValue("delete"))
        assertEquals(ChatTaskAction.SUBMIT, ChatTaskAction.fromApiValue("submit"))
        assertEquals(ChatTaskAction.INSTALL_NODES, ChatTaskAction.fromApiValue("install_nodes"))
        assertNull(ChatTaskAction.fromApiValue("unknown"))
    }

    private fun call(name: String, arguments: String): AiToolCall = AiToolCall(
        id = "call-1",
        name = name,
        arguments = arguments,
    )
}
