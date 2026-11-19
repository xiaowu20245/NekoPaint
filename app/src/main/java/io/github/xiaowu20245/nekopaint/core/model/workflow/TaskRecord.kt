package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.Serializable

@Serializable
data class TaskRecord(
    val promptId: String,
    val serverId: String = "",
    val templateId: String,
    val templateName: String,
    val clientId: String,
    val queueNumber: Int? = null,
    /** 批量提交中的序号；单次提交为 null。 */
    val batchIndex: Int? = null,
    /** 批量提交总数；单次提交为 null。 */
    val batchCount: Int? = null,
    val submittedAt: Long,
    val status: String = "queued",
    val values: Map<String, String> = emptyMap(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val errorMessage: String? = null,
    val currentNode: String? = null,
    val progressValue: Double? = null,
    val progressMax: Double? = null,
    val requestedAt: Long? = null,
    val requestedPromptId: String? = null,
    val statusUpdatedAt: Long? = null,
    val statusSource: String? = null,
    /** 响应 ID 迁移后旧记录指向的新 promptId；非空时禁止再复用旧 ID 重试。 */
    val supersededBy: String? = null,
)
