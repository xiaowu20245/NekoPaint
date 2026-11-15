package io.github.xiaowu20245.nekopaint.core.model.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDisclosurePolicyTest {

    private val origin = "https://api.example.com:443"

    @Test
    fun unacknowledgedOriginIsBlocked() {
        assertFalse(AiDisclosurePolicy.isAcknowledged(origin, emptySet()))
    }

    @Test
    fun acknowledgedOriginIsAllowed() {
        assertTrue(
            AiDisclosurePolicy.isAcknowledged(
                origin,
                setOf(AiDisclosurePolicy.key(origin)),
            ),
        )
    }

    @Test
    fun anotherOriginIsNotAllowed() {
        assertFalse(
            AiDisclosurePolicy.isAcknowledged(
                origin,
                setOf(AiDisclosurePolicy.key("https://other.example.com:443")),
            ),
        )
    }

    @Test
    fun versionChangeRequiresAcknowledgementAgain() {
        val oldVersion = "v0|$origin"
        assertFalse(AiDisclosurePolicy.isAcknowledged(origin, setOf(oldVersion)))
    }
}
