package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.data.repository.TaskRecordWriteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueDiagnosticsPolicyTest {

    @Test
    fun failedWriteStateIsVisible() {
        assertEquals(
            "磁盘满",
            queueRecordWriteError(TaskRecordWriteState.Failed("p1", "磁盘满")),
        )
    }

    @Test
    fun successOrDeleteStateClearsTheBanner() {
        assertNull(queueRecordWriteError(TaskRecordWriteState.Idle))
        assertNull(queueRecordWriteError(TaskRecordWriteState.Saved("p1")))
        assertNull(queueRecordWriteError(TaskRecordWriteState.Deleted("p1")))
    }
}
