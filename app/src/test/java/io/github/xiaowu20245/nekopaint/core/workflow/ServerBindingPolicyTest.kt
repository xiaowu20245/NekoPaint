package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerBindingPolicyTest {

    private val profile = ServerProfile(
        id = "server-1",
        name = "Home",
        scheme = "http",
        host = "192.168.1.20",
        port = 8188,
    )

    @Test
    fun bindingMatchesSameServerAndEndpoint() {
        val binding = ServerBindingPolicy.fromProfile(profile)

        assertTrue(ServerBindingPolicy.matches(profile, binding))
        assertEquals(profile.endpointKey, binding.endpointKey)
        assertEquals("192.168.1.20", ServerBindingPolicy.toConfig(binding).host)
    }

    @Test
    fun bindingRejectsDifferentServerIdOrEndpoint() {
        val binding = ServerBindingPolicy.fromProfile(profile)

        assertFalse(
            ServerBindingPolicy.matches(profile.copy(id = "server-2"), binding),
        )
        assertFalse(
            ServerBindingPolicy.matches(profile.copy(host = "192.168.1.21"), binding),
        )
        assertFalse(
            ServerBindingPolicy.matches(profile.copy(port = 8189), binding),
        )
    }

    @Test
    fun legacyMissingBindingNeverMatches() {
        assertFalse(ServerBindingPolicy.matches(profile, null))
        assertFalse(ServerBindingPolicy.matches(null, ServerBindingPolicy.fromProfile(profile)))
    }

    @Test
    fun bindingOnlyRebuildsConnectionFields() {
        val binding = ServerBinding(
            serverId = "s",
            endpointKey = "http:localhost:8188",
            scheme = "https",
            host = "localhost",
            port = 8443,
        )

        val config = ServerBindingPolicy.toConfig(binding)

        assertEquals("https", config.scheme)
        assertEquals("localhost", config.host)
        assertEquals(8443, config.port)
    }
}
