package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.network.NodeProgress

/** 任务总进度计算：finished 节点计入完成，总数未知时不返回假百分比。 */
object TaskProgressPolicy {

    fun recordProgressState(
        completedNodeIds: MutableSet<String>,
        nodes: List<NodeProgress>,
    ) {
        nodes.forEach { node ->
            if (node.state == "finished") {
                completedNodeIds.add(node.nodeId)
            }
        }
    }

    fun runningNode(nodes: List<NodeProgress>): NodeProgress? =
        nodes.firstOrNull { it.state == "running" }

    fun finishing(progress: TaskProgress): TaskProgress =
        progress.copy(
            currentNode = null,
            currentNodeLabel = null,
            value = null,
            max = null,
            nodeStartedAt = null,
        )

    fun overallPercent(
        completed: Int,
        runningFraction: Double,
        total: Int,
    ): Float? {
        if (total <= 0) return null
        val completedFraction = completed.toDouble().coerceIn(0.0, total.toDouble())
        val nodeFraction = runningFraction.coerceIn(0.0, 1.0)
        return ((completedFraction + nodeFraction) / total)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
