package io.github.xiaowu20245.nekopaint.feature.server

import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ServerCardMergeTest {

    @Test
    fun endpointChangeClearsStaleInvalidConfig() {
        val old = ServerCardUi(
            profile = ServerProfile(id = "one", host = "foo#bar"),
            isActive = true,
            error = ServerError.InvalidConfig,
            isTesting = true,
            lastUpdatedAt = 123L,
        )

        val merged = mergeServerCard(
            existing = old,
            profile = ServerProfile(id = "one", host = "127.0.0.1"),
            isActive = true,
        )

        assertEquals("127.0.0.1", merged.profile.host)
        assertNull(merged.error)
        assertNull(merged.lastUpdatedAt)
        assertFalse(merged.isTesting)
    }

    @Test
    fun sameEndpointKeepsNetworkError() {
        val old = ServerCardUi(
            profile = ServerProfile(id = "one", host = "127.0.0.1"),
            isActive = true,
            error = ServerError.Unreachable,
        )

        val merged = mergeServerCard(
            existing = old,
            profile = old.profile.copy(name = "renamed"),
            isActive = true,
        )

        assertEquals(ServerError.Unreachable, merged.error)
        assertEquals("renamed", merged.profile.name)
    }
}
