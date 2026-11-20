package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMetricsPolicyTest {

    @Test
    fun singleOrMissingBatchHidesBatchBadge() {
        assertFalse(QueueMetricsPolicy.forTask(task(), progress(), 1_000L).showBatch)
        assertFalse(
            QueueMetricsPolicy.forTask(
                task(batchIndex = null, batchCount = null),
                progress(),
                1_000L,
            ).showBatch,
        )
        assertFalse(
            QueueMetricsPolicy.forTask(
                task(batchIndex = 3, batchCount = 2),
                progress(),
                1_000L,
            ).showBatch,
        )
    }

    @Test
    fun validMultiBatchShowsZeroBasedIndexAndCount() {
        val metrics = QueueMetricsPolicy.forTask(
            task(batchIndex = 1, batchCount = 3),
            progress(),
            1_000L,
        )

        assertTrue(metrics.showBatch)
        assertEquals(1, metrics.batchIndex)
        assertEquals(3, metrics.batchCount)
    }

    @Test
    fun etaRequiresNodeStartAndHidesWhenMissing() {
        assertNull(
            QueueMetricsPolicy.etaMillis(
                progress(value = 10.0, max = 20.0, nodeStartedAt = null),
                11_000L,
            ),
        )
        assertEquals(
            10_000L,
            QueueMetricsPolicy.etaMillis(
                progress(value = 10.0, max = 20.0, nodeStartedAt = 1_000L),
                11_000L,
            ) ?: -1L,
        )
    }

    @Test
    fun overallAndNodeProgressHideUntrustworthyValues() {
        val empty = QueueMetricsPolicy.forTask(
            task(),
            progress(value = null, max = null, totalNodes = 0),
            1_000L,
        )
        assertFalse(empty.showOverall)
        assertFalse(empty.showNodeProgress)
        assertEquals(NekoTaskStatus.RUNNING, empty.status)

        val zeroProgress = QueueMetricsPolicy.forTask(
            task(),
            progress(value = 0.0, max = 0.0, totalNodes = 0),
            1_000L,
        )
        assertFalse(zeroProgress.showOverall)
        assertFalse(zeroProgress.showNodeProgress)

        val valid = QueueMetricsPolicy.forTask(
            task(),
            progress(value = 3.0, max = 10.0, totalNodes = 5),
            1_000L,
        )
        assertTrue(valid.showOverall)
        assertTrue(valid.showNodeProgress)
    }

    private fun task(
        batchIndex: Int? = null,
        batchCount: Int? = null,
    ) = TaskRecord(
        promptId = "p1",
        serverId = "server-a",
        templateId = "t",
        templateName = "template",
        clientId = "client",
        batchIndex = batchIndex,
        batchCount = batchCount,
        submittedAt = 1L,
        status = "running",
        requestedAt = 1L,
    )

    private fun progress(
        value: Double? = 1.0,
        max: Double? = 10.0,
        totalNodes: Int = 1,
        nodeStartedAt: Long? = null,
    ) = TaskProgress(
        promptId = "p1",
        status = "running",
        currentNode = "1",
        currentNodeLabel = "KSampler",
        value = value,
        max = max,
        completedNodes = 0,
        totalNodes = totalNodes,
        nodeStartedAt = nodeStartedAt,
    )
}
