package io.github.xiaowu20245.nekopaint.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class NekoStatusMapperTest {

    @Test
    fun connectionMappingHasSixUiStatesWithoutErrorEnum() {
        assertEquals(
            NekoConnectionStatus.UNCONFIGURED,
            NekoStatusMapper.connection(ComfyConnectionState.Unconfigured, hasError = true),
        )
        assertEquals(
            NekoConnectionStatus.OFFLINE,
            NekoStatusMapper.connection(ComfyConnectionState.Offline, hasError = false),
        )
        assertEquals(
            NekoConnectionStatus.ERROR,
            NekoStatusMapper.connection(ComfyConnectionState.Offline, hasError = true),
        )
        assertEquals(
            NekoConnectionStatus.CONNECTING,
            NekoStatusMapper.connection(ComfyConnectionState.Connecting, hasError = false),
        )
        assertEquals(
            NekoConnectionStatus.RECONNECTING,
            NekoStatusMapper.connection(ComfyConnectionState.Reconnecting, hasError = false),
        )
        assertEquals(
            NekoConnectionStatus.ONLINE,
            NekoStatusMapper.connection(ComfyConnectionState.Online, hasError = true),
        )
    }

    @Test
    fun taskStatusMappingIsCaseInsensitive() {
        assertEquals(NekoTaskStatus.QUEUED, NekoStatusMapper.task("queued"))
        assertEquals(NekoTaskStatus.RUNNING, NekoStatusMapper.task("RUNNING"))
        assertEquals(NekoTaskStatus.SUCCESS, NekoStatusMapper.task("success"))
        assertEquals(NekoTaskStatus.FAILED, NekoStatusMapper.task("failed"))
        assertEquals(NekoTaskStatus.INTERRUPTED, NekoStatusMapper.task("interrupted"))
        assertEquals(NekoTaskStatus.SUBMITTING, NekoStatusMapper.task("submitting"))
        assertEquals(NekoTaskStatus.UNKNOWN, NekoStatusMapper.task(null))
        assertEquals(NekoTaskStatus.UNKNOWN, NekoStatusMapper.task("other-client-status"))
    }

    @Test
    fun sourceMappingDistinguishesOwnOtherAndUnknown() {
        assertEquals(NekoTaskSource.LOCAL, NekoStatusMapper.source(true))
        assertEquals(NekoTaskSource.OTHER, NekoStatusMapper.source(false))
        assertEquals(NekoTaskSource.UNKNOWN, NekoStatusMapper.source(null))
    }

    @Test
    fun serverCardMappingPrefersTestingThenInvalidConfigThenError() {
        assertEquals(
            NekoServerStatus.CHECKING,
            NekoStatusMapper.server(
                isTesting = true,
                isOnline = false,
                hasInvalidConfig = true,
                hasError = true,
            ),
        )
        assertEquals(
            NekoServerStatus.INVALID_CONFIG,
            NekoStatusMapper.server(
                isTesting = false,
                isOnline = false,
                hasInvalidConfig = true,
                hasError = true,
            ),
        )
        assertEquals(
            NekoServerStatus.OFFLINE,
            NekoStatusMapper.server(
                isTesting = false,
                isOnline = false,
                hasInvalidConfig = false,
                hasError = true,
            ),
        )
        assertEquals(
            NekoServerStatus.NOT_CHECKED,
            NekoStatusMapper.server(
                isTesting = false,
                isOnline = false,
                hasInvalidConfig = false,
                hasError = false,
            ),
        )
        assertEquals(
            NekoServerStatus.ONLINE,
            NekoStatusMapper.server(
                isTesting = false,
                isOnline = true,
                hasInvalidConfig = false,
                hasError = false,
            ),
        )
        assertEquals(NekoStatusTone.SUCCESS, NekoServerStatus.ONLINE.tone())
        assertEquals(Icons.Outlined.CheckCircle, NekoServerStatus.ONLINE.icon())
    }
}
