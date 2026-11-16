package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowRepairFlagTest {

    @Test
    fun anyStoredOrViewFlagKeepsRepairRequired() {
        assertTrue(WorkflowRepairFlag.effective(true, false, false))
        assertTrue(WorkflowRepairFlag.effective(false, true, false))
        assertTrue(WorkflowRepairFlag.effective(false, false, true))
    }

    @Test
    fun matchesOnlyWhenAllSourcesAreClean() {
        assertFalse(WorkflowRepairFlag.effective(false, false, false))
    }
}
