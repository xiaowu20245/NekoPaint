package io.github.xiaowu20245.nekopaint.core.model.workflow

import io.github.xiaowu20245.nekopaint.feature.chat.ChatActionProposal
import io.github.xiaowu20245.nekopaint.feature.chat.ChatTaskAction
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeInstallPlanSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val binding = ServerBinding(
        serverId = "server-1",
        endpointKey = "http:192.168.1.20:8188",
        scheme = "http",
        host = "192.168.1.20",
        port = 8188,
    )

    @Test
    fun planBindingSurvivesRoundTrip() {
        val plan = NodeInstallPlan(
            templateId = "template-1",
            templateName = "T2I",
            packages = listOf(
                NodeInstallPackage(id = "pack", title = "Pack", files = listOf("url")),
            ),
            binding = binding,
        )

        val decoded = json.decodeFromString<NodeInstallPlan>(json.encodeToString(plan))

        assertEquals(binding, decoded.binding)
        assertEquals("template-1", decoded.templateId)
    }

    @Test
    fun legacyPlanWithoutBindingDecodesToNull() {
        val decoded = json.decodeFromString<NodeInstallPlan>(
            """{"templateId":"old","templateName":"Old","packages":[]}""",
        )

        assertNull(decoded.binding)
    }

    @Test
    fun chatProposalBindingRoundTripsAndLegacyIsNull() {
        val proposal = ChatActionProposal(
            id = "proposal-1",
            action = ChatTaskAction.INSTALL_NODES,
            promptId = "",
            title = "T2I",
            status = "missing_nodes",
            workflowId = "template-1",
            binding = binding,
        )

        val decoded = json.decodeFromString<ChatActionProposal>(json.encodeToString(proposal))
        val legacy = json.decodeFromString<ChatActionProposal>(
            """{"id":"old","action":"INSTALL_NODES","promptId":"","title":"T","status":"missing_nodes"}""",
        )

        assertEquals(binding, decoded.binding)
        assertNull(legacy.binding)
    }
}
