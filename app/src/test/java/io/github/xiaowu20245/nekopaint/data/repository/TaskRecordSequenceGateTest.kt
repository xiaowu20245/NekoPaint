package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRecordSequenceGateTest {

    @Test
    fun acceptsOnlyIncreasingSequences() {
        val gate = TaskRecordSequenceGate()

        assertTrue(gate.accept(1))
        assertFalse(gate.accept(1))
        assertFalse(gate.accept(0))
        assertTrue(gate.accept(2))
        assertTrue(gate.accept(3))
    }
}
