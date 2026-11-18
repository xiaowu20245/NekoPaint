package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionExecutionRegistryTest {

    @Test
    fun beginIsIdempotentPerSessionAndProposal() {
        val registry = ChatActionExecutionRegistry()

        assertTrue(registry.begin("s1", "p1"))
        assertFalse(registry.begin("s1", "p1"))
        assertTrue(registry.isActive("s1", "p1"))
    }

    @Test
    fun finishClearsOnlyMatchingProposal() {
        val registry = ChatActionExecutionRegistry()
        registry.begin("s1", "p1")
        registry.begin("s1", "p2")

        registry.finish("s1", "p1")

        assertFalse(registry.isActive("s1", "p1"))
        assertTrue(registry.isActive("s1", "p2"))
        assertEquals(setOf("p2"), registry.activeProposalIds("s1"))
    }

    @Test
    fun sessionsAreIsolated() {
        val registry = ChatActionExecutionRegistry()
        registry.begin("s1", "p1")

        assertFalse(registry.isActive("s2", "p1"))
        assertEquals(emptySet<String>(), registry.activeProposalIds("s2"))
    }

    @Test
    fun activeStateTracksLifecycle() {
        val registry = ChatActionExecutionRegistry()
        registry.begin("s1", "p1", startedAt = 10L)
        registry.begin("s2", "p2", startedAt = 20L)

        assertEquals(
            setOf("s1" to "p1", "s2" to "p2"),
            registry.activeState.value.map { it.sessionId to it.proposalId }.toSet(),
        )

        registry.finish("s1", "p1")

        assertEquals(
            listOf("s2" to "p2"),
            registry.activeState.value.map { it.sessionId to it.proposalId },
        )
    }

    @Test
    fun versionAdvancesOnBeginAndFinish() {
        val registry = ChatActionExecutionRegistry()
        val initial = registry.version("s1")

        registry.begin("s1", "p1")
        val afterBegin = registry.version("s1")
        registry.finish("s1", "p1")
        val afterFinish = registry.version("s1")

        assertTrue(afterBegin > initial)
        assertTrue(afterFinish > afterBegin)
        assertEquals(initial, registry.version("s2"))
    }
}
