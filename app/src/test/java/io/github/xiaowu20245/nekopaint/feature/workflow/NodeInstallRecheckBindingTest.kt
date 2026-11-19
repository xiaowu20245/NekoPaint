package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeInstallRecheckBindingTest {

    private val binding = ServerBinding(
        serverId = "s",
        endpointKey = "http:localhost:8188",
        scheme = "http",
        host = "localhost",
        port = 8188,
    )

    @Test
    fun installResultKeepsBindingAfterPlanCleared() {
        val state = NodeInstallUiState(
            visible = true,
            plan = null,
            binding = binding,
            result = "done",
        )

        assertEquals(binding, state.recheckBinding())
    }

    @Test
    fun legacyStateWithoutBindingCannotRecheck() {
        assertNull(NodeInstallUiState(visible = true, plan = null).recheckBinding())
    }

    @Test
    fun planBindingIsFallbackBeforeInstall() {
        val state = NodeInstallUiState(
            visible = true,
            plan = io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan(
                templateId = "t",
                templateName = "T",
                binding = binding,
            ),
        )

        assertEquals(binding, state.recheckBinding())
    }
}
