package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRequestGuardTest {

    private val a = ServerIdentity("a", "http://a:8188")
    private val b = ServerIdentity("b", "http://b:8188")

    @Test
    fun matchingIdentityAndGenerationIsCurrent() {
        assertTrue(
            TaskRequestGuard.isCurrent(
                token = TaskRequestToken(a, 2),
                currentIdentity = a,
                currentGeneration = 2,
            ),
        )
    }

    @Test
    fun identityChangeInvalidatesInFlightRequest() {
        assertFalse(
            TaskRequestGuard.isCurrent(
                token = TaskRequestToken(a, 2),
                currentIdentity = b,
                currentGeneration = 3,
            ),
        )
    }

    @Test
    fun generationChangeInvalidatesSameIdentityRequest() {
        assertFalse(
            TaskRequestGuard.isCurrent(
                token = TaskRequestToken(a, 2),
                currentIdentity = a,
                currentGeneration = 3,
            ),
        )
    }
}
