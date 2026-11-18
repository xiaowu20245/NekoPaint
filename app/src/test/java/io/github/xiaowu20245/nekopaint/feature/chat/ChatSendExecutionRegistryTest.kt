package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendExecutionRegistryTest {

    @Test
    fun beginIsExclusivePerSession() {
        val registry = ChatSendExecutionRegistry()

        assertTrue(registry.begin("s1"))
        assertFalse(registry.begin("s1"))
        assertTrue(registry.begin("s2"))
    }

    @Test
    fun finishOnlyClearsMatchingSession() {
        val registry = ChatSendExecutionRegistry()
        registry.begin("s1")
        registry.begin("s2")

        registry.finish("s1")

        assertFalse(registry.isActive("s1"))
        assertTrue(registry.isActive("s2"))
        assertEquals(setOf("s2"), registry.activeSessions.value)
    }

    @Test
    fun clearRemovesAllSessions() {
        val registry = ChatSendExecutionRegistry()
        registry.begin("s1")
        registry.begin("s2")

        registry.clear()

        assertTrue(registry.activeSessions.value.isEmpty())
    }
}
