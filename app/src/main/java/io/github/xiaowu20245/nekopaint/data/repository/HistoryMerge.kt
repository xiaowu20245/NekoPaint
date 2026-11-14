package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry

/**
 * 合并本地历史缓存与 ComfyUI 当前历史。
 *
 * ComfyUI 的 `/history` 只保存在进程内，重启后会清空。App 已拉取过的记录必须保留在本地，
 * 否则下一次刷新会把旧历史覆盖掉。同一个 promptId 以服务器最新数据为准。
 */
internal fun mergeHistory(
    local: List<HistoryEntry>,
    remote: List<HistoryEntry>,
    maxEntries: Int = MAX_HISTORY_ARCHIVE_ENTRIES,
): List<HistoryEntry> {
    val byId = LinkedHashMap<String, HistoryEntry>(local.size + remote.size)
    local.forEach { entry -> byId[entry.promptId] = entry }
    remote.forEach { entry -> byId[entry.promptId] = entry }
    return byId.values
        .sortedWith(
            compareByDescending<HistoryEntry> { entry ->
                entry.timestamp ?: entry.finishedAt ?: entry.startedAt ?: 0L
            },
        )
        .take(maxEntries)
}

internal const val MAX_HISTORY_ARCHIVE_ENTRIES = 200
