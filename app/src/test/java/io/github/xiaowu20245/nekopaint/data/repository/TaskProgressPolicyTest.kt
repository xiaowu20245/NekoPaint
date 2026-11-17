package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.network.NodeProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskProgressPolicyTest {

    @Test
    fun finishedNodesAreCompleted() {
        val completed = mutableSetOf<String>()
        val nodes = listOf(
            NodeProgress("1", 0.0, 0.0, "finished"),
            NodeProgress("2", 0.0, 0.0, "running"),
            NodeProgress("3", 0.0, 0.0, "finished"),
        )

        TaskProgressPolicy.recordProgressState(completed, nodes)

        assertEquals(setOf("1", "3"), completed)
    }

    @Test
    fun finishedOnlyStateHasNoRunningNode() {
        val nodes = listOf(
            NodeProgress("1", 0.0, 0.0, "finished"),
            NodeProgress("2", 0.0, 0.0, "finished"),
        )

        assertNull(TaskProgressPolicy.runningNode(nodes))
        assertEquals(
            "3",
            TaskProgressPolicy.runningNode(
                nodes + NodeProgress("3", 1.0, 2.0, "running"),
            )?.nodeId,
        )
        val finishing = TaskProgressPolicy.finishing(
            TaskProgress(
                promptId = "p1",
                status = "running",
                currentNode = "2",
                currentNodeLabel = "Node 2",
                value = 1.0,
                max = 2.0,
            ),
        )
        assertNull(finishing.currentNode)
        assertNull(finishing.currentNodeLabel)
        assertNull(finishing.value)
        assertNull(finishing.max)
    }

    @Test
    fun overallPercentIncludesCurrentNodeFraction() {
        assertEquals(
            0.625f,
            TaskProgressPolicy.overallPercent(
                completed = 5,
                runningFraction = 0.0,
                total = 8,
            ),
        )
        assertEquals(
            0.6875f,
            TaskProgressPolicy.overallPercent(
                completed = 5,
                runningFraction = 0.5,
                total = 8,
            ),
        )
    }

    @Test
    fun unknownTotalReturnsNull() {
        assertNull(TaskProgressPolicy.overallPercent(0, 0.5, 0))
    }

    @Test
    fun completedAndFractionAreClamped() {
        assertEquals(
            1f,
            TaskProgressPolicy.overallPercent(
                completed = 20,
                runningFraction = 1.5,
                total = 8,
            ),
        )
    }
}
