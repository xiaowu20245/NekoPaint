package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerIsolationCoordinatorTest {

    @Test
    fun serverIdChangeRequiresReset() {
        assertTrue(
            ServerIsolationCoordinator.requiresReset(
                ServerIdentity("one", "http://a:8188"),
                ServerIdentity("two", "http://a:8188"),
            ),
        )
    }

    @Test
    fun endpointChangeWithSameIdRequiresReset() {
        assertTrue(
            ServerIsolationCoordinator.requiresReset(
                ServerIdentity("one", "http://a:8188"),
                ServerIdentity("one", "http://b:8188"),
            ),
        )
    }

    @Test
    fun sameIdentityDoesNotReset() {
        assertFalse(
            ServerIsolationCoordinator.requiresReset(
                ServerIdentity("one", "http://a:8188"),
                ServerIdentity("one", "http://a:8188"),
            ),
        )
    }

    @Test
    fun firstAndLastIdentityReset() {
        assertTrue(ServerIsolationCoordinator.requiresReset(null, ServerIdentity("one", "x")))
        assertTrue(ServerIsolationCoordinator.requiresReset(ServerIdentity("one", "x"), null))
    }
}
