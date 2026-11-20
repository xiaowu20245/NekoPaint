package io.github.xiaowu20245.nekopaint.core.ui.component

import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateMapperTest {

    @Test
    fun unconfiguredWinsOverRefreshError() {
        assertEquals(
            NekoConnectionStatus.UNCONFIGURED,
            NekoStatusMapper.connection(ComfyConnectionState.Unconfigured, hasError = true),
        )
    }

    @Test
    fun offlineWithRefreshErrorShowsError() {
        assertEquals(
            NekoConnectionStatus.ERROR,
            NekoStatusMapper.connection(ComfyConnectionState.Offline, hasError = true),
        )
    }

    @Test
    fun connectingMapsToConnecting() {
        assertEquals(
            NekoConnectionStatus.CONNECTING,
            NekoStatusMapper.connection(ComfyConnectionState.Connecting, hasError = false),
        )
    }

    @Test
    fun reconnectingMapsToReconnecting() {
        assertEquals(
            NekoConnectionStatus.RECONNECTING,
            NekoStatusMapper.connection(ComfyConnectionState.Reconnecting, hasError = false),
        )
    }

    @Test
    fun onlineIgnoresStaleRefreshError() {
        assertEquals(
            NekoConnectionStatus.ONLINE,
            NekoStatusMapper.connection(ComfyConnectionState.Online, hasError = true),
        )
    }
}
