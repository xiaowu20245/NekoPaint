package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord

/**
 * 合并跨主机本地归档与当前主机实时数据。
 *
 * 以 serverId + promptId 为唯一键；实时数据在后，覆盖同一条归档记录。
 */
internal fun mergeHistoryArchives(
    archived: List<HistoryEntry>,
    live: List<HistoryEntry>,
): List<HistoryEntry> {
    val byKey = LinkedHashMap<Pair<String, String>, HistoryEntry>(archived.size + live.size)
    archived.forEach { entry -> byKey[historyKey(entry)] = entry }
    live.forEach { entry -> byKey[historyKey(entry)] = entry }
    return byKey.values.sortedByDescending { entry ->
        entry.timestamp ?: entry.finishedAt ?: entry.startedAt ?: 0L
    }
}

internal fun mergeTaskArchives(
    archived: List<TaskRecord>,
    live: List<TaskRecord>,
): List<TaskRecord> {
    val byKey = LinkedHashMap<Pair<String, String>, TaskRecord>(archived.size + live.size)
    archived.forEach { task -> byKey[taskKey(task)] = task }
    live.forEach { task -> byKey[taskKey(task)] = task }
    return byKey.values.sortedByDescending { it.submittedAt }
}

/**
 * 历史上同一任务只展示一次。
 *
 * 多个服务器配置可能指向同一个 ComfyUI 实例，此时本地归档会按 serverId
 * 各存一份，但 promptId 相同。统一历史列表以 promptId 去重，优先保留时间
 * 更新的记录；时间相同时保留当前主机记录。
 */
internal fun dedupeHistoryByPrompt(
    entries: List<HistoryEntry>,
    preferredServerId: String,
): List<HistoryEntry> {
    val byPrompt = LinkedHashMap<String, HistoryEntry>(entries.size)
    entries.forEach { entry ->
        val existing = byPrompt[entry.promptId]
        if (existing == null || preferHistory(entry, existing, preferredServerId)) {
            byPrompt[entry.promptId] = entry
        }
    }
    return byPrompt.values.sortedByDescending { entry ->
        entry.timestamp ?: entry.finishedAt ?: entry.startedAt ?: 0L
    }
}

internal fun dedupeTasksByPrompt(
    tasks: List<TaskRecord>,
    preferredServerId: String,
): List<TaskRecord> {
    val byPrompt = LinkedHashMap<String, TaskRecord>(tasks.size)
    tasks.forEach { task ->
        val existing = byPrompt[task.promptId]
        if (existing == null || preferTask(task, existing, preferredServerId)) {
            byPrompt[task.promptId] = task
        }
    }
    return byPrompt.values.sortedByDescending { it.submittedAt }
}

/**
 * 返回与当前服务器指向同一地址的所有配置 ID。
 *
 * 这些配置共享远端历史和图片地址，操作时可以复用当前主机的连接。
 */
internal fun sameEndpointServerIds(
    profiles: List<ServerProfile>,
    activeId: String,
): Set<String> {
    val active = profiles.firstOrNull { it.id == activeId }
        ?: return setOf(activeId)
    return profiles
        .filter { it.endpointKey == active.endpointKey }
        .map { it.id }
        .toSet()
        .ifEmpty { setOf(activeId) }
}

private fun preferHistory(
    candidate: HistoryEntry,
    current: HistoryEntry,
    preferredServerId: String,
): Boolean {
    val candidateTime = candidate.timestamp ?: candidate.finishedAt ?: candidate.startedAt ?: 0L
    val currentTime = current.timestamp ?: current.finishedAt ?: current.startedAt ?: 0L
    if (candidateTime != currentTime) return candidateTime > currentTime
    val candidateHost = serverKey(candidate.serverId)
    val currentHost = serverKey(current.serverId)
    if (candidateHost == preferredServerId && currentHost != preferredServerId) return true
    if (currentHost == preferredServerId && candidateHost != preferredServerId) return false
    return false
}

private fun preferTask(
    candidate: TaskRecord,
    current: TaskRecord,
    preferredServerId: String,
): Boolean {
    if (candidate.submittedAt != current.submittedAt) {
        return candidate.submittedAt > current.submittedAt
    }
    val candidateHost = serverKey(candidate.serverId)
    val currentHost = serverKey(current.serverId)
    if (candidateHost == preferredServerId && currentHost != preferredServerId) return true
    if (currentHost == preferredServerId && candidateHost != preferredServerId) return false
    return false
}

private fun historyKey(entry: HistoryEntry): Pair<String, String> =
    serverKey(entry.serverId) to entry.promptId

private fun taskKey(task: TaskRecord): Pair<String, String> =
    serverKey(task.serverId) to task.promptId

private fun serverKey(serverId: String): String =
    serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID }
