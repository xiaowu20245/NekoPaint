package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowGraph
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowNodeSummary

/** 由解析后的工作流图生成只读节点摘要；不读取也不保存完整 inputs。 */
object WorkflowNodeSummaryPolicy {

    fun from(
        graph: WorkflowGraph,
        missingNodeTypes: Collection<String>,
    ): List<WorkflowNodeSummary> {
        val missing = missingNodeTypes.toSet()
        return graph.nodes.values
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            .map { node ->
                WorkflowNodeSummary(
                    id = node.id,
                    classType = node.classType,
                    title = node.title,
                    reachable = node.id in graph.reachableNodeIds,
                    missing = node.classType in missing,
                )
            }
    }
}
