package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord

/**
 * 合并本地任务与服务器/磁盘来源的状态。
 *
 * 不使用单一 rank：本地 submitting/unknown 不能被旧磁盘 queued 覆盖，
 * 终态则按 statusUpdatedAt + 来源优先级决定，避免旧 history 覆盖新的 WS 结果。
 */
object TaskStatusSyncPolicy {

    fun merge(
        local: TaskRecord?,
        incoming: TaskRecord,
        source: TaskStatusSource,
    ): TaskRecord {
        if (local == null) {
            return incoming.copy(
                statusUpdatedAt = incoming.statusUpdatedAt ?: recordTime(incoming),
                statusSource = source.name,
            )
        }
        val localTime = recordTime(local)
        val incomingTime = incoming.statusUpdatedAt ?: recordTime(incoming)
        val useIncoming = shouldUseIncoming(local, incoming, source, localTime, incomingTime)
        val chosenStatus = if (useIncoming) incoming.status else local.status
        val chosenTime = if (useIncoming) incomingTime else localTime
        val chosenSource = if (useIncoming) {
            source
        } else {
            localSource(local)
        }
        val startedAt = listOfNotNull(local.startedAt, incoming.startedAt).minOrNull()
        return local.copy(
            serverId = incoming.serverId.ifBlank { local.serverId },
            status = chosenStatus,
            startedAt = startedAt,
            finishedAt = if (useIncoming) {
                incoming.finishedAt ?: local.finishedAt
            } else {
                local.finishedAt ?: incoming.finishedAt
            },
            errorMessage = when (chosenStatus) {
                TaskStatus.SUCCESS -> null
                TaskStatus.FAILED,
                TaskStatus.INTERRUPTED,
                -> if (useIncoming) {
                    incoming.errorMessage ?: local.errorMessage
                } else {
                    local.errorMessage ?: incoming.errorMessage
                }

                else -> local.errorMessage
            },
            currentNode = if (useIncoming) {
                incoming.currentNode ?: local.currentNode
            } else {
                local.currentNode ?: incoming.currentNode
            },
            progressValue = if (useIncoming) {
                incoming.progressValue ?: local.progressValue
            } else {
                local.progressValue ?: incoming.progressValue
            },
            progressMax = if (useIncoming) {
                incoming.progressMax ?: local.progressMax
            } else {
                local.progressMax ?: incoming.progressMax
            },
            requestedAt = incoming.requestedAt ?: local.requestedAt,
            requestedPromptId = incoming.requestedPromptId ?: local.requestedPromptId,
            supersededBy = incoming.supersededBy ?: local.supersededBy,
            statusUpdatedAt = chosenTime,
            statusSource = chosenSource.name,
        )
    }

    private fun shouldUseIncoming(
        local: TaskRecord,
        incoming: TaskRecord,
        source: TaskStatusSource,
        localTime: Long,
        incomingTime: Long,
    ): Boolean {
        val localStatus = local.status
        val incomingStatus = incoming.status
        if (localStatus == incomingStatus) {
            return when {
                incomingTime > localTime -> true
                incomingTime < localTime -> false
                else -> source.priority > localSource(local).priority
            }
        }

        val localTerminal = TaskStatus.isTerminal(localStatus)
        val incomingTerminal = TaskStatus.isTerminal(incomingStatus)
        return when {
            localTerminal && !incomingTerminal -> false
            !localTerminal && incomingTerminal -> true
            localTerminal && incomingTerminal -> when {
                incomingTime > localTime -> true
                incomingTime < localTime -> false
                else -> source.priority >= localSource(local).priority
            }

            else -> {
                if (TaskStatus.isLocalPending(localStatus) &&
                    incomingStatus == TaskStatus.QUEUED &&
                    source == TaskStatusSource.DISK &&
                    incomingTime <= localTime
                ) {
                    false
                } else if (progression(incomingStatus) < progression(localStatus)) {
                    false
                } else {
                    when {
                        incomingTime > localTime -> true
                        incomingTime < localTime -> false
                        else -> source.priority >= localSource(local).priority
                    }
                }
            }
        }
    }

    private fun localSource(record: TaskRecord): TaskStatusSource =
        TaskStatusSource.from(record.statusSource) ?: TaskStatusSource.LOCAL

    private fun recordTime(record: TaskRecord): Long =
        record.statusUpdatedAt
            ?: record.finishedAt
            ?: record.startedAt
            ?: record.requestedAt
            ?: record.submittedAt

    private fun progression(status: String): Int = when (status) {
        TaskStatus.SUBMITTING -> 0
        TaskStatus.UNKNOWN -> 1
        TaskStatus.QUEUED -> 2
        TaskStatus.RUNNING -> 3
        else -> 4
    }
}
