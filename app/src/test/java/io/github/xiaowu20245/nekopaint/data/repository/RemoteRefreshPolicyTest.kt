package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRefreshPolicyTest {

    @Test
    fun allowsValidConfig() {
        assertTrue(
            RemoteRefreshPolicy.shouldRefreshRemotely(
                ServerConfig(host = "192.168.1.10", port = 8188),
            ),
        )
    }

    @Test
    fun blocksInvalidConfig() {
        assertFalse(
            RemoteRefreshPolicy.shouldRefreshRemotely(
                ServerConfig(host = "foo#bar", port = 8188),
            ),
        )
    }

    @Test
    fun blocksMissingConfig() {
        assertFalse(RemoteRefreshPolicy.shouldRefreshRemotely(null))
    }
}
