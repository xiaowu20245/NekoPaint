package io.github.xiaowu20245.nekopaint.feature.server

import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.ServerStats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRefreshPolicyTest {

    private val profile = ServerProfile(
        id = "server",
        host = "127.0.0.1",
    )

    @Test
    fun manualRefreshAlwaysShowsTesting() {
        assertTrue(
            ServerRefreshPolicy.showTesting(
                card(error = ServerError.Unreachable),
                silent = false,
            ),
        )
    }

    @Test
    fun firstSilentProbeShowsTesting() {
        assertTrue(
            ServerRefreshPolicy.showTesting(
                card(),
                silent = true,
            ),
        )
    }

    @Test
    fun silentRefreshKeepsOfflineBadge() {
        assertFalse(
            ServerRefreshPolicy.showTesting(
                card(error = ServerError.Unreachable),
                silent = true,
            ),
        )
        assertFalse(
            ServerRefreshPolicy.showTesting(
                card(error = ServerError.InvalidConfig),
                silent = true,
            ),
        )
    }

    @Test
    fun silentRefreshKeepsOnlineBadge() {
        assertFalse(
            ServerRefreshPolicy.showTesting(
                card(snapshot = snapshot()),
                silent = true,
            ),
        )
    }

    @Test
    fun missingCardDoesNotEnterTestingDuringSilentRefresh() {
        assertFalse(ServerRefreshPolicy.showTesting(null, silent = true))
    }

    private fun card(
        snapshot: ConnectionSnapshot? = null,
        error: ServerError? = null,
    ) = ServerCardUi(
        profile = profile,
        isActive = true,
        snapshot = snapshot,
        error = error,
    )

    private fun snapshot() = ConnectionSnapshot(
        stats = ServerStats(
            comfyUiVersion = null,
            os = null,
            pythonVersion = null,
            pytorchVersion = null,
            ramTotal = null,
            ramFree = null,
            devices = emptyList(),
        ),
        latencyMs = 1,
        checkedAt = 1L,
    )
}
