package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry

internal sealed interface HistoryLookupOutcome {
    data object NotFound : HistoryLookupOutcome
    data class Found(val entry: HistoryEntry) : HistoryLookupOutcome
    data class Failed(val message: String) : HistoryLookupOutcome
}

/**
 * 定向 `/history/{promptId}` 的语义：
 * - 404 / 空 map / 条目 promptId 不匹配 -> NotFound（可以进入同 ID 重试）；
 * - 非 2xx -> Failed（检查失败，不能据此重试）；
 * - 只接受与查询 promptId 完全匹配的条目，避免把服务器返回的其他条目当成命中。
 */
internal object HistoryLookupPolicy {

    fun classify(
        code: Int,
        entries: List<HistoryEntry>,
        promptId: String,
    ): HistoryLookupOutcome = when {
        code == 404 -> HistoryLookupOutcome.NotFound
        code !in 200..299 -> HistoryLookupOutcome.Failed("HTTP $code")
        else -> entries.firstOrNull { it.promptId == promptId }
            ?.let(HistoryLookupOutcome::Found)
            ?: HistoryLookupOutcome.NotFound
    }
}
