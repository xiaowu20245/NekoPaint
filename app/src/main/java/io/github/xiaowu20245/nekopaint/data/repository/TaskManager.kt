package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.core.network.ComfyEvent
import io.github.xiaowu20245.nekopaint.core.network.ComfyWebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskManager(
    private val taskStore: TaskStore,
    private val historyStore: HistoryStore,
    private val queueRepository: QueueRepository,
    private val webSocketManager: ComfyWebSocketManager,
    private val clientIdStore: ClientIdStore,
    private val serverConfigStore: ServerConfigStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    val tasks: StateFlow<List<TaskRecord>> = _tasks.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, TaskProgress>>(emptyMap())
    val progress: StateFlow<Map<String, TaskProgress>> = _progress.asStateFlow()

    private val _queue = MutableStateFlow<QueueSnapshot?>(null)
    val queue: StateFlow<QueueSnapshot?> = _queue.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _queueRemaining = MutableStateFlow(0)
    val queueRemaining: StateFlow<Int> = _queueRemaining.asStateFlow()

    private val _localClientId = MutableStateFlow<String?>(null)
    val localClientId: StateFlow<String?> = _localClientId.asStateFlow()

    private val _queueError = MutableStateFlow<String?>(null)
    val queueError: StateFlow<String?> = _queueError.asStateFlow()

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    private val _hasMoreHistory = MutableStateFlow(false)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory.asStateFlow()

    private val _loadingMoreHistory = MutableStateFlow(false)
    val loadingMoreHistory: StateFlow<Boolean> = _loadingMoreHistory.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val connection: StateFlow<ComfyConnectionState> = webSocketManager.connection

    private var refreshJob: Job? = null

    @Volatile
    private var historyFetched = false

    private var historyLimit = HISTORY_PAGE_SIZE

    init {
        scope.launch {
            _localClientId.value = runCatching { clientIdStore.getOrCreate() }.getOrNull()
        }
        scope.launch {
            serverConfigStore.activeServerId
                .distinctUntilChanged()
                .collectLatest { serverId ->
                    refreshJob?.cancel()
                    _refreshing.value = false
                    _tasks.value = emptyList()
                    _history.value = emptyList()
                    _queue.value = null
                    _progress.value = emptyMap()
                    _queueError.value = null
                    _historyError.value = null
                    _hasMoreHistory.value = false
                    _loadingMoreHistory.value = false
                    historyLimit = HISTORY_PAGE_SIZE
                    historyFetched = false
                    if (serverId == null) return@collectLatest

                    _tasks.value = runCatching { taskStore.list(serverId) }
                        .getOrDefault(emptyList())
                    _history.value = runCatching { historyStore.load(serverId) }
                        .getOrDefault(emptyList())
                    refresh()
                }
        }
        scope.launch {
            webSocketManager.events.collect { event -> handleEvent(event) }
        }
        scope.launch {
            // 重连成功后主动校准一次，避免断线期间的状态丢失。
            webSocketManager.connection.collect { state ->
                if (state == ComfyConnectionState.Connected) refresh()
            }
        }
        webSocketManager.start()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val serverId = serverConfigStore.activeServerId.first()
                ?: return@launch
            _refreshing.value = true
            reloadTasks(serverId)
            queueRepository.queue()
                .onSuccess { snapshot ->
                    _queue.value = snapshot
                    _queueError.value = null
                    syncTasksWithQueue(snapshot)
                }
                .onFailure { throwable ->
                    // 失败时保留上次队列，界面据此标记为过期。
                    _queueError.value = throwable.message ?: "无法刷新队列"
                }
            queueRepository.history(historyLimit + 1)
                .onSuccess { fetched ->
                    val hasMore = fetched.size > historyLimit
                    val entries = if (hasMore) fetched.take(historyLimit) else fetched
                    val previous = _history.value
                    historyFetched = true
                    _history.value = entries
                    _hasMoreHistory.value = hasMore
                    _historyError.value = null
                    syncTasksWithHistory(entries)
                    // 只有内容变化时才写盘，避免每 5 秒轮询都重复写。
                    if (previous != entries) {
                        historyStore.save(serverId, entries)
                    }
                }
                .onFailure { throwable ->
                    _historyError.value = throwable.message ?: "无法刷新历史"
                }
            _refreshing.value = false
        }
    }

    fun interrupt(promptId: String) {
        scope.launch {
            queueRepository.interrupt(promptId)
                .onFailure { throwable ->
                    _actionError.value = throwable.message ?: "服务器拒绝了中断请求"
                }
            refresh()
        }
    }

    fun deletePending(promptId: String) {
        scope.launch {
            queueRepository.deletePending(promptId)
                .onFailure { throwable ->
                    _actionError.value = throwable.message ?: "服务器拒绝了删除请求"
                }
            refresh()
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun loadMoreHistory() {
        if (_loadingMoreHistory.value) return
        scope.launch {
            _loadingMoreHistory.value = true
            val serverId = serverConfigStore.activeServerId.first()
            if (serverId == null) {
                _loadingMoreHistory.value = false
                return@launch
            }
            val newLimit = historyLimit + HISTORY_PAGE_STEP
            queueRepository.history(newLimit + 1)
                .onSuccess { fetched ->
                    val hasMore = fetched.size > newLimit
                    val entries = if (hasMore) fetched.take(newLimit) else fetched
                    historyLimit = newLimit
                    _history.value = entries
                    _hasMoreHistory.value = hasMore
                    _historyError.value = null
                    syncTasksWithHistory(entries)
                    historyStore.save(serverId, entries)
                }
                .onFailure { throwable ->
                    _historyError.value = throwable.message ?: "无法加载更多历史"
                }
            _loadingMoreHistory.value = false
        }
    }

    suspend fun deleteHistory(promptIds: Collection<String>): Result<Unit> {
        if (promptIds.isEmpty()) return Result.success(Unit)
        val result = queueRepository.deleteHistory(promptIds.toList())
        result.onSuccess {
            val serverId = serverConfigStore.activeServerId.first()
            _history.update { entries ->
                entries.filterNot { it.promptId in promptIds }
            }
            _historyError.value = null
            if (serverId != null) {
                historyStore.save(serverId, _history.value)
            }
        }.onFailure { throwable ->
            _historyError.value = throwable.message ?: "删除历史失败"
        }
        return result
    }

    suspend fun clearHistory(): Result<Unit> {
        val result = queueRepository.clearHistory()
        result.onSuccess {
            val serverId = serverConfigStore.activeServerId.first()
            _history.value = emptyList()
            _hasMoreHistory.value = false
            historyLimit = HISTORY_PAGE_SIZE
            _historyError.value = null
            if (serverId != null) {
                historyStore.save(serverId, emptyList())
            }
        }.onFailure { throwable ->
            _historyError.value = throwable.message ?: "清空历史失败"
        }
        return result
    }

    private suspend fun reloadTasks(serverId: String) {
        val stored = runCatching { taskStore.list(serverId) }.getOrNull() ?: return
        _tasks.update { current ->
            val currentById = current.associateBy { it.promptId }
            stored.map { record ->
                val live = currentById[record.promptId]
                // 内存中的状态可能比磁盘文件更新，取更靠后的状态但保留新提交的任务。
                if (live != null && statusRank(live.status) >= statusRank(record.status)) live else record
            }.sortedByDescending { it.submittedAt }
        }
    }

    private suspend fun handleEvent(event: ComfyEvent) {
        when (event) {
            is ComfyEvent.Status -> _queueRemaining.value = event.queueRemaining

            is ComfyEvent.ExecutionStart -> {
                updateTask(event.promptId) {
                    it.copy(
                        status = "running",
                        startedAt = it.startedAt ?: System.currentTimeMillis(),
                    )
                }
            }

            is ComfyEvent.Executing -> {
                // node 为 null 表示执行器正在收尾，保留上一个节点的进度即可。
                val nodeId = event.nodeId ?: return
                val label = nodeLabel(event.promptId, nodeId)
                _progress.update { current ->
                    val old = current[event.promptId]
                    current + (
                        event.promptId to TaskProgress(
                            promptId = event.promptId,
                            status = "running",
                            currentNode = nodeId,
                            currentNodeLabel = label,
                            value = old?.value,
                            max = old?.max,
                        )
                        )
                }
            }

            is ComfyEvent.Progress -> {
                val label = nodeLabel(event.promptId, event.nodeId)
                _progress.update { current ->
                    current + (
                        event.promptId to TaskProgress(
                            promptId = event.promptId,
                            status = "running",
                            currentNode = event.nodeId,
                            currentNodeLabel = label,
                            value = event.value,
                            max = event.max,
                        )
                        )
                }
            }

            is ComfyEvent.ProgressState -> {
                val running = event.nodes.firstOrNull { it.state == "running" }
                    ?: event.nodes.lastOrNull()
                if (running != null) {
                    val label = nodeLabel(event.promptId, running.nodeId)
                    _progress.update { current ->
                        current + (
                            event.promptId to TaskProgress(
                                promptId = event.promptId,
                                status = "running",
                                currentNode = running.nodeId,
                                currentNodeLabel = label,
                                value = running.value,
                                max = running.max,
                            )
                            )
                    }
                }
            }

            is ComfyEvent.Success -> {
                _progress.update { it - event.promptId }
                updateTask(event.promptId) {
                    it.copy(
                        status = "success",
                        finishedAt = System.currentTimeMillis(),
                    )
                }
                refresh()
            }

            is ComfyEvent.Error -> {
                _progress.update { it - event.promptId }
                updateTask(event.promptId) {
                    it.copy(
                        status = "failed",
                        finishedAt = System.currentTimeMillis(),
                        errorMessage = event.message,
                    )
                }
            }

            is ComfyEvent.Interrupted -> {
                _progress.update { it - event.promptId }
                updateTask(event.promptId) {
                    it.copy(
                        status = "interrupted",
                        finishedAt = System.currentTimeMillis(),
                    )
                }
            }

            is ComfyEvent.ExecutionCached,
            is ComfyEvent.Executed,
            -> Unit
        }
    }

    private fun syncTasksWithQueue(snapshot: QueueSnapshot) {
        val running = snapshot.running.map { it.promptId }.toSet()
        val pending = snapshot.pending.map { it.promptId }.toSet()
        _tasks.update { tasks ->
            tasks.map { task ->
                when (task.promptId) {
                    in running -> if (task.status != "running") {
                        task.copy(status = "running", startedAt = task.startedAt ?: System.currentTimeMillis())
                    } else {
                        task
                    }

                    in pending -> if (task.status == "queued") task else task.copy(status = "queued")
                    else -> task
                }
            }
        }
    }

    private fun syncTasksWithHistory(entries: List<HistoryEntry>) {
        val byId = entries.associateBy { it.promptId }
        _tasks.update { tasks ->
            tasks.map { task ->
                val entry = byId[task.promptId] ?: return@map task
                val status = when (entry.status) {
                    "success" -> "success"
                    "failed" -> "failed"
                    "interrupted" -> "interrupted"
                    else -> task.status
                }
                if (status == task.status) {
                    task
                } else {
                    task.copy(
                        status = status,
                        startedAt = task.startedAt ?: entry.startedAt,
                        finishedAt = entry.finishedAt ?: entry.timestamp ?: task.finishedAt,
                        errorMessage = entry.errorMessage ?: task.errorMessage,
                    )
                }
            }
        }
    }

    private fun updateTask(promptId: String, transform: (TaskRecord) -> TaskRecord) {
        var updated: TaskRecord? = null
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.promptId == promptId) {
                    transform(task).also { updated = it }
                } else {
                    task
                }
            }
        }
        updated?.let { record ->
            // 任务记录从创建起就带 serverId；旧数据兜底到默认服务器。
            val serverId = record.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID }
            scope.launch { taskStore.update(serverId, record) }
        }
    }

    private fun nodeLabel(promptId: String, nodeId: String?): String? {
        if (nodeId == null) return null
        val snapshot = _queue.value ?: return null
        return (snapshot.running + snapshot.pending)
            .firstOrNull { it.promptId == promptId }
            ?.nodeTitles
            ?.get(nodeId)
    }

    private fun statusRank(status: String): Int = when (status) {
        "running" -> 1
        "success", "failed", "interrupted" -> 2
        else -> 0
    }

    private companion object {
        const val HISTORY_PAGE_SIZE = 100
        const val HISTORY_PAGE_STEP = 50
    }
}
