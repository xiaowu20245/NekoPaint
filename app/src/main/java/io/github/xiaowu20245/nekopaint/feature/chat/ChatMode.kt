package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.serialization.Serializable

@Serializable
enum class ChatMode {
    CHAT,
    AGENT,
}

/**
 * 工具权限策略。Chat 只允许只读工具；Agent 才注册写操作建议工具。
 *
 * 权限在工具注册层执行，不依赖模型是否遵守提示词。
 */
object ChatToolPolicy {

    val readOnlyTools = setOf(
        "get_queue_snapshot",
        "get_recent_history",
        "get_history_images",
        "get_workflow_summary",
        "get_workflow_parameters",
        "diagnose_task",
        "analyze_image",
    )

    val writeTools = setOf(
        "propose_task_action",
        "propose_submit_task",
        "propose_batch_task_action",
        "propose_install_missing_nodes",
    )

    fun allows(toolName: String, mode: ChatMode): Boolean =
        allowsExecution(toolName, mode)

    /**
     * 执行层白名单：只读工具两模式放行；写工具仅 Agent；未知工具一律拒绝。
     * 不依赖模型是否遵守提示词，也不依赖注册层是否遗漏。
     */
    fun allowsExecution(toolName: String, mode: ChatMode): Boolean =
        toolName in readOnlyTools ||
            (mode == ChatMode.AGENT && toolName in writeTools)
}
