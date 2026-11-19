package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.network.ManagerQueueStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeInstallQueuePolicyTest {

    @Test
    fun baselineBusyWhenQueueProcessing() {
        assertEquals(
            NodeInstallQueuePolicy.Baseline.BUSY,
            NodeInstallQueuePolicy.baseline(status(isProcessing = true)),
        )
        assertEquals(
            NodeInstallQueuePolicy.Baseline.BUSY,
            NodeInstallQueuePolicy.baseline(status(inProgress = 1)),
        )
        assertEquals(
            NodeInstallQueuePolicy.Baseline.BUSY,
            NodeInstallQueuePolicy.baseline(status(total = 2)),
        )
    }

    @Test
    fun baselineUnavailableWhenStatusMissing() {
        assertEquals(
            NodeInstallQueuePolicy.Baseline.UNAVAILABLE,
            NodeInstallQueuePolicy.baseline(null),
        )
    }

    @Test
    fun baselineReadyOnlyWhenIdle() {
        assertEquals(
            NodeInstallQueuePolicy.Baseline.READY,
            NodeInstallQueuePolicy.baseline(status()),
        )
    }

    @Test
    fun activeMustCoverQueuedPackages() {
        assertTrue(
            NodeInstallQueuePolicy.activeCoversQueued(
                status(isProcessing = true, total = 2),
                queuedCount = 2,
            ),
        )
        assertFalse(
            NodeInstallQueuePolicy.activeCoversQueued(
                status(isProcessing = true, total = 1),
                queuedCount = 2,
            ),
        )
        assertFalse(
            NodeInstallQueuePolicy.activeCoversQueued(
                status(),
                queuedCount = 0,
            ),
        )
    }

    @Test
    fun idleOnlyWhenNoActivity() {
        assertTrue(NodeInstallQueuePolicy.isIdle(status()))
        assertFalse(NodeInstallQueuePolicy.isIdle(status(inProgress = 1)))
    }

    private fun status(
        isProcessing: Boolean = false,
        inProgress: Int = 0,
        total: Int = 0,
        done: Int = 0,
    ) = ManagerQueueStatusDto(
        totalCount = total,
        doneCount = done,
        inProgressCount = inProgress,
        isProcessing = isProcessing,
    )
}
