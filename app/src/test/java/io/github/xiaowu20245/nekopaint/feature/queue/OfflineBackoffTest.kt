package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineBackoffTest {

    @Test
    fun offlineDelaysProgressAndCapAtThirtySeconds() {
        assertEquals(2_000L, QueueOfflineBackoffPolicy.delayMs(ComfyConnectionState.Offline, 0))
        assertEquals(5_000L, QueueOfflineBackoffPolicy.delayMs(ComfyConnectionState.Offline, 1))
        assertEquals(10_000L, QueueOfflineBackoffPolicy.delayMs(ComfyConnectionState.Offline, 2))
        assertEquals(30_000L, QueueOfflineBackoffPolicy.delayMs(ComfyConnectionState.Offline, 3))
        assertEquals(30_000L, QueueOfflineBackoffPolicy.delayMs(ComfyConnectionState.Offline, 99))
    }

    @Test
    fun onlineResetsBackoffToFiveSecondPolling() {
        assertEquals(0, QueueOfflineBackoffPolicy.nextAttempt(ComfyConnectionState.Online, 3))
        assertEquals(5_000L, QueueOfflineBackoffPolicy.delayMs(ComfyConnectionState.Online, 3))
        assertEquals(1, QueueOfflineBackoffPolicy.nextAttempt(ComfyConnectionState.Reconnecting, 0))
    }
}
