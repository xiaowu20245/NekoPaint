package io.github.xiaowu20245.nekopaint.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
