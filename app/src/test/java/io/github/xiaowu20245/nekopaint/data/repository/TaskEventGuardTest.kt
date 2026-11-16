package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEventGuardTest {

    private val identity = ServerIdentity("one", "http://a:8188")

    @Test
    fun acceptsOnlyEventsFromCurrentEndpoint() {
        assertTrue(TaskEventGuard.accepts("http://a:8188", identity, emptySet()))
        assertFalse(TaskEventGuard.accepts("http://b:8188", identity, emptySet()))
    }

    @Test
    fun rejectsEventsForBlockedIdentityOrMissingIdentity() {
        assertFalse(
            TaskEventGuard.accepts(
                "http://a:8188",
                identity,
                setOf("one|http://a:8188"),
            ),
        )
        assertFalse(TaskEventGuard.accepts("http://a:8188", null, emptySet()))
    }
}
