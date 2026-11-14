package io.github.xiaowu20245.nekopaint.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerProfileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun convertsToAndFromConfig() {
        val config = ServerConfig(
            name = "工作机",
            scheme = "http",
            host = "192.168.1.10",
            port = 8188,
        )
        val profile = ServerProfile.fromConfig(config, id = "server-1")

        assertEquals("server-1", profile.id)
        assertEquals(config, profile.toConfig())
    }

    @Test
    fun roundTripsStoredProfile() {
        val profile = ServerProfile(
            id = "server-1",
            name = "工作机",
            scheme = "http",
            host = "192.168.1.10",
            port = 8188,
            createdAt = 1234L,
        )

        val restored = json.decodeFromString<ServerProfile>(
            json.encodeToString(profile),
        )

        assertEquals(profile, restored)
    }

    @Test
    fun aliasesWithSameAddressShareEndpointKey() {
        val first = ServerProfile(
            id = "server-1",
            name = "ComfyUI",
            scheme = "HTTP",
            host = " 127.0.0.1 ",
            port = 8188,
        )
        val second = ServerProfile(
            id = "server-2",
            name = "我的 ComfyUI",
            scheme = "http",
            host = "127.0.0.1",
            port = 8188,
        )
        val otherPort = second.copy(port = 8189)
        val loopback = second.copy(host = "localhost")

        assertEquals(first.endpointKey, second.endpointKey)
        assertEquals(first.endpointKey, loopback.endpointKey)
        assertNotEquals(first.endpointKey, otherPort.endpointKey)
    }
}
