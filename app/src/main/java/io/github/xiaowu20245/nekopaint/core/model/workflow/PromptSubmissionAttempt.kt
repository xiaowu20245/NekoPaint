package io.github.xiaowu20245.nekopaint.core.model.workflow

/**
 * 一次用户提交尝试的稳定幂等键。
 *
 * 网络结果未知时复用同一个 promptId；只有明确校验失败或用户主动重新提交才创建新 attempt。
 */
data class PromptSubmissionAttempt(
    val promptId: String,
    val templateId: String,
    val serverId: String,
    val values: Map<String, String>,
    val createdAt: Long,
    val state: AttemptState,
)

enum class AttemptState {
    PREPARED,
    SUBMITTING,
    UNKNOWN,
    ACCEPTED,
    REJECTED,
}
