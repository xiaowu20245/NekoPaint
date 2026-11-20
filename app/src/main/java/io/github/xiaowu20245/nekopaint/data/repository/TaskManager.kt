package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.AppLog
import io.github.xiaowu20245.nekopaint.core.common.NekoException
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.core.network.ComfyEvent
import io.github.xiaowu20245.nekopaint.core.network.ComfyEventEnvelope
import io.github.xiaowu20245.nekopaint.core.network.ComfyWebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskManager(
    private val taskStore: TaskStore,
    private val taskRecordRepository: TaskRecordRepository,
    private val historyStore: HistoryStore,
    private val queueRepository: QueueRepository,
    private val webSocketManager: ComfyWebSocketManager,
    private val clientIdStore: ClientIdStore,
    private val serverConfigStore: ServerConfigStore,
    private val historyAutoSaver: HistoryAutoSaver,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    val tasks: StateFlow<List<TaskRecord>> = _tasks.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, TaskProgress>>(emptyMap())
    val progress: StateFlow<Map<String, TaskProgress>> = _progress.asStateFlow()

    // 每个 prompt 已完成的节点集合，用于计算工作流总进度。
    private val executedNodeIds = mutableMapOf<String, MutableSet<String>>()

    private val _queue = MutableStateFlow<QueueSnapshot?>(null)
    val queue: StateFlow<QueueSnapshot?> = _queue.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _queueRemaining = MutableStateFlow(0)
    val queueRemaining: StateFlow<Int> = _queueRemaining.asStateFlow()

    private val _localClientId = MutableStateFlow<String?>(null)
    val localClientId: StateFlow<String?> = _localClientId.asStateFlow()

    private val _queueError = MutableStateFlow<NekoMessage?>(null)
    val queueError: StateFlow<NekoMessage?> = _queueError.asStateFlow()

    private val _historyError = MutableStateFlow<NekoMessage?>(null)
    val historyError: StateFlow<NekoMessage?> = _historyError.asStateFlow()

    private val _hasMoreHistory = MutableStateFlow(false)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory.asStateFlow()

    private val _loadingMoreHistory = MutableStateFlow(false)
    val loadingMoreHistory: StateFlow<Boolean> = _loadingMoreHistory.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _actionError = MutableStateFlow<NekoMessage?>(null)
    val actionError: StateFlow<NekoMessage?> = _actionError.asStateFlow()

    val connection: StateFlow<ComfyConnectionState> = webSocketManager.connection

    private var refreshJob: Job? = null

    private var historyLimit = HISTORY_PAGE_SIZE

    @Volatile
    private var currentSnapshot: ServerIdentitySnapshot? = null

    private val blockedIdentities =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var identityGeneration = 0L

    private val stateMutex = Mutex()
    val taskRecordWriteState: StateFlow<TaskRecordWriteState> = taskRecordRepository.writeState
    private val taskEventCoordinator = TaskRecordEventCoordinator(
        scope = scope,
        repository = taskRecordRepository,
        stateMutex = stateMutex,
        tasks = _tasks,
        currentServerId = { currentSnapshot?.identity?.serverId },
        localClientId = { _localClientId.value },
        queueSnapshot = { _queue.value },
        persist = ::persistTask,
    )
    private val taskPersistenceQueue = TaskPersistenceQueue(
        scope = scope,
        isCurrent = ::isCurrent,
        writer = { serverId, record ->
            taskRecordRepository.upsert(serverId, record).getOrThrow()
        },
        onWriteError = { serverId, record, throwable ->
            AppLog.w("task persist failed: $serverId/${record.promptId}", throwable)
        },
    )

    private val currentIdentity: ServerIdentity?
        get() = currentSnapshot?.identity

    init {
        scope.launch {
            _localClientId.value = runCatching { clientIdStore.getOrCreate() }.getOrNull()
            taskEventCoordinator.flushPendingUpdates()
        }
        scope.launch {
            combine(
                serverConfigStore.activeServer,
                serverConfigStore.cacheIdentities,
            ) { profile, cache -> profile to cache }
                .collectLatest { (profile, cache) ->
                    val snapshot = profile?.let {
                        ServerIdentitySnapshot(
                            identity = ServerIdentity(it.id, it.endpointKey),
                            config = it.toConfig(),
                        )
                    }
                    val identity = snapshot?.identity
                    if (identity == currentSnapshot?.identity) {
                        stateMutex.withLock {
                            currentSnapshot = snapshot
                            if (identity != null) {
                                val cached = cache[identity.serverId]
                                if (cached == null || cached != identity.endpointKey) {
                                    blockedIdentities += identity.cacheKey()
                                    val message = TaskErrorPolicy.serverBlock(cacheUnknown = true)
                                    _queueError.value = message
                                    _historyError.value = message
                                }
                            }
                        }
                        return@collectLatest
                    }
                    val previous = currentSnapshot?.identity
                    taskPersistenceQueue.flush()
                    var blockReason: NekoMessage? = null
                    val generation = stateMutex.withLock {
                        identityGeneration += 1
                        currentSnapshot = snapshot
                        clearIsolationState()
                        if (identity != null) {
                            val persistedEndpoint = cache[identity.serverId]
                            val cacheUnknown = persistedEndpoint == null
                            if (cacheUnknown || persistedEndpoint != identity.endpointKey) {
                                blockedIdentities += identity.cacheKey()
                            }
                            val endpointChangedWithSameId =
                                previous != null &&
                                    previous.serverId == identity.serverId &&
                                    previous.endpointKey != identity.endpointKey
                            if (endpointChangedWithSameId ||
                                blockedIdentities.contains(identity.cacheKey())
                            ) {
                                blockReason = TaskErrorPolicy.serverBlock(cacheUnknown)
                                _queueError.value = blockReason
                                _historyError.value = blockReason
                            }
                        }
                        identityGeneration
                    }
                    // 第一次 flush 之后的并发入队仍可能带旧 generation；切换 token 后
                    // 再强制落盘一次，确保旧服务器最后一批快照不会滞留。
                    taskPersistenceQueue.flush()
                    if (identity == null) return@collectLatest
                    if (blockReason != null) {
                        // Same id but a different/unknown endpoint means legacy
                        // data or a caller bypassed replaceServerEndpoint.
                        return@collectLatest
                    }

                    val serverId = identity.serverId
                    val tasks = try {
                        taskStore.list(serverId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    val history = try {
                        historyStore.load(serverId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    stateMutex.withLock {
                        if (currentSnapshot?.identity == identity &&
                            identityGeneration == generation
                        ) {
                            _tasks.value = tasks
                            _history.value = history
                        }
                    }
                    refresh()
                }
        }
        scope.launch {
            webSocketManager.events.collect { envelope ->
                stateMutex.withLock {
                    if (!webSocketManager.isCurrentConnection(
                            envelope.connectionToken,
                            envelope.socketToken,
                        )
                    ) {
                        return@withLock
                    }
                    val identity = currentSnapshot?.identity ?: return@withLock
                    if (!TaskEventGuard.accepts(
                            envelope.endpointKey,
                            identity,
                            blockedIdentities,
                        )
                    ) {
                        return@withLock
                    }
                    if (isCurrent(identity, identityGeneration)) {
                        handleEvent(envelope.event)
                    }
                }
            }
        }
        scope.launch {
            // 重连成功后主动校准一次，避免断线期间的状态丢失。
            webSocketManager.connection.collect { state ->
                if (state == ComfyConnectionState.Online) refresh()
            }
        }
        webSocketManager.start()
    }

    private fun clearIsolationState() {
        refreshJob?.cancel()
        _refreshing.value = false
        _tasks.value = emptyList()
        _history.value = emptyList()
        _queue.value = null
        _queueRemaining.value = 0
        _progress.value = emptyMap()
        executedNodeIds.clear()
        taskEventCoordinator.clearPendingUpdates()
        _queueError.value = null
        _historyError.value = null
        _actionError.value = null
        _hasMoreHistory.value = false
        _loadingMoreHistory.value = false
        historyLimit = HISTORY_PAGE_SIZE
    }

    /**
     * Flushes queued task snapshots without stopping the manager scope.
     * 宿主进入后台时调用，缩小进程被回收时最后状态丢失的窗口。
     */
    fun flushPending() {
        scope.launch { taskPersistenceQueue.flush() }
    }

    /**
     * Flushes the final task snapshots and stops the manager scope.
     *
     * Android 无法保证进程死亡回调；正常生命周期退出时应由宿主显式调用。
     */
    suspend fun shutdown() {
        taskPersistenceQueue.shutdown()
        taskEventCoordinator.close()
        scope.cancel()
    }

    /**
     * 定向检查一个本地 pending prompt：队列 + `/history/{promptId}`。
     *
     * 返回 true 表示服务器队列或历史已命中；false 表示未找到，可进入同 ID 重试。
     * 不依赖最近一页 history，避免 unknown 恢复被分页遗漏。
     */
    suspend fun recheckPending(promptId: String): Result<Boolean> {
        val snapshot = currentSnapshot
            ?: return Result.failure(NekoException(TaskErrorPolicy.serverNotConfigured()))
        val identity = snapshot.identity
        if (isBlocked(identity)) {
            return Result.failure(
                NekoException(TaskErrorPolicy.serverBlock(cacheUnknown = false)),
            )
        }
        val generation = identityGeneration
        val serverId = identity.serverId
        val config = snapshot.config

        return PendingRecheckPolicy.recheck(
            promptId = promptId,
            loadQueue = { queueRepository.queue(config) },
            loadHistory = { queueRepository.historyItem(promptId, config) },
            applyQueue = { queueSnapshot ->
                withCurrentState(identity, generation) {
                    _queue.value = queueSnapshot
                    _queueError.value = null
                    syncTasksWithQueue(queueSnapshot)
                }
            },
            applyHistory = { entry ->
                val scoped = entry.copy(serverId = serverId)
                withCurrentState(identity, generation) {
                    val previous = _history.value
                    val merged = mergeHistory(previous, listOf(scoped))
                    _history.value = merged
                    syncTasksWithHistory(listOf(scoped))
                    if (previous != merged) {
                        historyStore.save(serverId, merged)
                    }
                }
            },
            markNotFound = {
                // 定向查询也未找到：若仍是刚预写的 submitting，且已超过阈值，则降级 unknown。
                withCurrentState(identity, generation) {
                    val stored = taskStore.get(serverId, promptId)
                    if (stored != null && stored.status == TaskStatus.SUBMITTING) {
                        val normalized = PendingSubmissionRecoveryPolicy.normalize(
                            record = stored,
                            now = System.currentTimeMillis(),
                        )
                        if (normalized != stored) {
                            taskRecordRepository.upsert(serverId, normalized)
                        }
                    }
                }
            },
        ).also {
            taskEventCoordinator.flushPendingUpdates()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val snapshot = currentSnapshot ?: return@launch
            val identity = snapshot.identity
            if (isBlocked(identity)) return@launch
            val generation = identityGeneration
            val serverId = identity.serverId
            val config = snapshot.config
            if (!RemoteRefreshPolicy.shouldRefreshRemotely(config)) {
                if (!isCurrent(identity, generation)) return@launch
                reloadTasks(serverId, identity, generation)
                withCurrentState(identity, generation) {
                    val message = TaskErrorPolicy.serverConfigInvalid()
                    _queueError.value = message
                    _historyError.value = message
                    _refreshing.value = false
                }
                return@launch
            }
            if (withCurrentState(identity, generation) { _refreshing.value = true } == null) {
                return@launch
            }
            reloadTasks(serverId, identity, generation)
            queueRepository.queue(config)
                .onSuccess { snapshot ->
                    withCurrentState(identity, generation) {
                        _queue.value = snapshot
                        _queueError.value = null
                        syncTasksWithQueue(snapshot)
                        // 队列快照到达后才知道工作流节点总数，重算一次总进度。
                        refreshOverallProgress()
                    }
                    taskEventCoordinator.flushPendingUpdates()
                }
                .onFailure { throwable ->
                    withCurrentState(identity, generation) {
                        // 失败时保留上次队列，界面据此标记为过期。
                        _queueError.value = TaskErrorPolicy.queueRefreshFailed()
                    }
                }
            queueRepository.history(historyLimit + 1, config)
                .onSuccess { fetched ->
                    withCurrentState(identity, generation) {
                        val hasMore = fetched.size > historyLimit
                        val entries = (if (hasMore) fetched.take(historyLimit) else fetched)
                            .map { entry -> entry.copy(serverId = serverId) }
                        val previous = _history.value
                        val merged = mergeHistory(previous, entries)
                        _history.value = merged
                        _hasMoreHistory.value = hasMore
                        _historyError.value = null
                        syncTasksWithHistory(entries)
                        // 只有内容变化时才写盘，避免每 5 秒轮询都重复写。
                        if (previous != merged) {
                            historyStore.save(serverId, merged)
                        }
                        // 自动保存设置开启时，补齐历史中尚未保存到相册的 output 图片。
                        scope.launch { historyAutoSaver.saveNew(serverId, merged) }
                    }
                }
                .onFailure { throwable ->
                    withCurrentState(identity, generation) {
                        _historyError.value = TaskErrorPolicy.historyRefreshFailed()
                    }
                }
            withCurrentState(identity, generation) {
                _refreshing.value = false
            }
        }
    }

    fun interrupt(promptId: String) {
        scope.launch {
            val snapshot = currentSnapshot ?: return@launch
            val identity = snapshot.identity
            if (isBlocked(identity)) return@launch
            val generation = identityGeneration
            if (!isCurrent(identity, generation)) return@launch
            queueRepository.interrupt(promptId, snapshot.config)
                .onFailure { throwable ->
                    withCurrentState(identity, generation) {
                        _actionError.value = TaskErrorPolicy.queueInterruptFailed()
                    }
                }
            if (isCurrent(identity, generation)) {
                refresh()
            }
        }
    }

    fun deletePending(promptId: String) {
        scope.launch {
            val snapshot = currentSnapshot ?: return@launch
            val identity = snapshot.identity
            if (isBlocked(identity)) return@launch
            val generation = identityGeneration
            if (!isCurrent(identity, generation)) return@launch
            queueRepository.deletePending(promptId, snapshot.config)
                .onFailure { throwable ->
                    withCurrentState(identity, generation) {
                        _actionError.value = TaskErrorPolicy.queueDeleteFailed()
                    }
                }
            if (isCurrent(identity, generation)) {
                refresh()
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun loadMoreHistory() {
        if (_loadingMoreHistory.value) return
        scope.launch {
            val snapshot = currentSnapshot ?: return@launch
            val identity = snapshot.identity
            if (isBlocked(identity)) return@launch
            val generation = identityGeneration
            val config = snapshot.config
            if (withCurrentState(identity, generation) {
                    _loadingMoreHistory.value = true
                } == null
            ) {
                return@launch
            }
            val serverId = identity.serverId
            val newLimit = historyLimit + HISTORY_PAGE_STEP
            queueRepository.history(newLimit + 1, config)
                .onSuccess { fetched ->
                    withCurrentState(identity, generation) {
                        val hasMore = fetched.size > newLimit
                        val entries = (if (hasMore) fetched.take(newLimit) else fetched)
                            .map { entry -> entry.copy(serverId = serverId) }
                        historyLimit = newLimit
                        val merged = mergeHistory(_history.value, entries)
                        _history.value = merged
                        _hasMoreHistory.value = hasMore
                        _historyError.value = null
                        syncTasksWithHistory(entries)
                        historyStore.save(serverId, merged)
                        scope.launch { historyAutoSaver.saveNew(serverId, merged) }
                    }
                }
                .onFailure { throwable ->
                    withCurrentState(identity, generation) {
                        _historyError.value = TaskErrorPolicy.historyLoadMoreFailed()
                    }
                }
            withCurrentState(identity, generation) {
                _loadingMoreHistory.value = false
            }
        }
    }

    suspend fun deleteHistory(promptIds: Collection<String>): Result<Unit> {
        if (promptIds.isEmpty()) return Result.success(Unit)
        val snapshot = currentSnapshot
            ?: return Result.failure(NekoException(TaskErrorPolicy.serverNotConfigured()))
        val identity = snapshot.identity
        if (isBlocked(identity)) {
            return Result.failure(
                NekoException(TaskErrorPolicy.serverBlock(cacheUnknown = false)),
            )
        }
        val generation = identityGeneration
        if (!isCurrent(identity, generation)) {
            return Result.failure(NekoException(TaskErrorPolicy.serverSwitched()))
        }
        val result = queueRepository.deleteHistory(promptIds.toList(), snapshot.config)
        result.onSuccess {
            val applied = withCurrentState(identity, generation) {
                _history.update { entries ->
                    entries.filterNot { it.promptId in promptIds }
                }
                _historyError.value = null
                historyStore.save(identity.serverId, _history.value)
                true
            } ?: false
            if (!applied) {
                // The user switched servers while the remote call was in
                // flight. Update the old server's archive, never the new one.
                scope.launch {
                    val stored = runCatching { historyStore.load(identity.serverId) }
                        .getOrDefault(emptyList())
                    historyStore.save(
                        identity.serverId,
                        stored.filterNot { it.promptId in promptIds },
                    )
                }
            }
        }.onFailure { throwable ->
            withCurrentState(identity, generation) {
                _historyError.value = TaskErrorPolicy.historyDeleteFailed(throwable)
            }
        }
        return result
    }

    suspend fun clearHistory(): Result<Unit> {
        val snapshot = currentSnapshot
            ?: return Result.failure(NekoException(TaskErrorPolicy.serverNotConfigured()))
        val identity = snapshot.identity
        if (isBlocked(identity)) {
            return Result.failure(
                NekoException(TaskErrorPolicy.serverBlock(cacheUnknown = false)),
            )
        }
        val generation = identityGeneration
        if (!isCurrent(identity, generation)) {
            return Result.failure(NekoException(TaskErrorPolicy.serverSwitched()))
        }
        val result = queueRepository.clearHistory(snapshot.config)
        result.onSuccess {
            val applied = withCurrentState(identity, generation) {
                _history.value = emptyList()
                _hasMoreHistory.value = false
                historyLimit = HISTORY_PAGE_SIZE
                _historyError.value = null
                historyStore.save(identity.serverId, emptyList())
                true
            } ?: false
            if (!applied) {
                // Do not clear the newly active server's local history.
                scope.launch {
                    historyStore.save(identity.serverId, emptyList())
                }
            }
        }.onFailure { throwable ->
            withCurrentState(identity, generation) {
                _historyError.value = TaskErrorPolicy.historyClearFailed(throwable)
            }
        }
        return result
    }

    private suspend fun <T> withCurrentState(
        identity: ServerIdentity,
        generation: Long,
        block: suspend () -> T,
    ): T? = stateMutex.withLock {
        if (isCurrent(identity, generation)) block() else null
    }

    private fun isBlocked(identity: ServerIdentity): Boolean =
        blockedIdentities.contains(identity.cacheKey())

    private fun ServerIdentity.cacheKey(): String = "$serverId|$endpointKey"

    private fun isCurrent(identity: ServerIdentity, generation: Long): Boolean =
        TaskRequestGuard.isCurrent(
            token = TaskRequestToken(identity, generation),
            currentIdentity = currentIdentity,
            currentGeneration = identityGeneration,
        )

    private suspend fun reloadTasks(
        serverId: String,
        identity: ServerIdentity,
        generation: Long,
    ) {
        val stored = runCatching { taskStore.list(serverId) }.getOrNull() ?: return
        val reloaded = TaskListSyncPolicy.reloadWithRecovery(
            stored = stored,
            live = _tasks.value,
            now = System.currentTimeMillis(),
        )
        withCurrentState(identity, generation) {
            _tasks.value = reloaded.tasks
        }
        if (isCurrent(identity, generation)) {
            reloaded.changed.forEach { record ->
                taskRecordRepository.upsert(serverId, record)
            }
        }
    }

    private suspend fun handleEvent(event: ComfyEvent) {
        when (event) {
            is ComfyEvent.Status -> _queueRemaining.value = event.queueRemaining

            is ComfyEvent.ExecutionStart -> {
                // 新任务开始，重置节点完成集合，避免 promptId 复用时串数据。
                executedNodeIds[event.promptId] = mutableSetOf()
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
                        event.promptId to buildProgress(
                            promptId = event.promptId,
                            status = "running",
                            currentNode = nodeId,
                            currentNodeLabel = label,
                            value = old?.value,
                            max = old?.max,
                            nodeStartedAt = nodeStartedAt(old, nodeId),
                        )
                        )
                }
            }

            is ComfyEvent.Progress -> {
                val label = nodeLabel(event.promptId, event.nodeId)
                _progress.update { current ->
                    val old = current[event.promptId]
                    current + (
                        event.promptId to buildProgress(
                            promptId = event.promptId,
                            status = "running",
                            currentNode = event.nodeId,
                            currentNodeLabel = label,
                            value = event.value,
                            max = event.max,
                            nodeStartedAt = nodeStartedAt(old, event.nodeId),
                        )
                        )
                }
            }

            is ComfyEvent.ProgressState -> {
                val completed = executedNodeIds.getOrPut(event.promptId) { mutableSetOf() }
                TaskProgressPolicy.recordProgressState(completed, event.nodes)
                val running = TaskProgressPolicy.runningNode(event.nodes)
                if (running != null) {
                    val label = nodeLabel(event.promptId, running.nodeId)
                    _progress.update { current ->
                        val old = current[event.promptId]
                        current + (
                            event.promptId to buildProgress(
                                promptId = event.promptId,
                                status = "running",
                                currentNode = running.nodeId,
                                currentNodeLabel = label,
                                value = running.value,
                                max = running.max,
                                nodeStartedAt = nodeStartedAt(old, running.nodeId),
                            )
                            )
                    }
                } else {
                    // 全部节点已 finished 时不要继续显示旧 currentNode；
                    // 保留完成数/百分比，等待 Success 事件移除进度。
                    _progress.update { current ->
                        val old = current[event.promptId] ?: return@update current
                        current + (
                            event.promptId to TaskProgressPolicy.finishing(
                                buildProgress(
                                    promptId = event.promptId,
                                    status = old.status,
                                    currentNode = null,
                                    currentNodeLabel = null,
                                    value = old.value,
                                    max = old.max,
                                    nodeStartedAt = null,
                                ),
                            )
                            )
                    }
                }
            }

            is ComfyEvent.Success -> {
                _progress.update { it - event.promptId }
                executedNodeIds.remove(event.promptId)
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
                executedNodeIds.remove(event.promptId)
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
                executedNodeIds.remove(event.promptId)
                updateTask(event.promptId) {
                    it.copy(
                        status = "interrupted",
                        finishedAt = System.currentTimeMillis(),
                    )
                }
            }

            is ComfyEvent.ExecutionCached -> {
                // 命中缓存的节点不会再执行，直接计入已完成。
                executedNodeIds.getOrPut(event.promptId) { mutableSetOf() }
                    .addAll(event.nodes)
                updateOverallProgress(event.promptId)
            }

            is ComfyEvent.Executed -> {
                executedNodeIds.getOrPut(event.promptId) { mutableSetOf() }
                    .add(event.nodeId)
                updateOverallProgress(event.promptId)
            }
        }
    }

    private fun buildProgress(
        promptId: String,
        status: String,
        currentNode: String?,
        currentNodeLabel: String?,
        value: Double?,
        max: Double?,
        nodeStartedAt: Long? = null,
    ): TaskProgress {
        val completed = executedNodeIds[promptId]?.size ?: 0
        val total = totalNodeCount(promptId)
        val nodeFraction = if (max != null && max > 0) {
            ((value ?: 0.0) / max).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return TaskProgress(
            promptId = promptId,
            status = status,
            currentNode = currentNode,
            currentNodeLabel = currentNodeLabel,
            value = value,
            max = max,
            completedNodes = completed,
            totalNodes = total,
            overallPercent = TaskProgressPolicy.overallPercent(
                completed = completed,
                runningFraction = nodeFraction,
                total = total,
            ),
            nodeStartedAt = nodeStartedAt,
        )
    }

    /** 同一节点重复收到进度事件时保留起点；切换节点才重新计时。 */
    private fun nodeStartedAt(old: TaskProgress?, nodeId: String?): Long? = when {
        nodeId == null -> null
        old?.currentNode == nodeId -> old.nodeStartedAt
        else -> System.currentTimeMillis()
    }

    private fun totalNodeCount(promptId: String): Int {
        val snapshot = _queue.value ?: return 0
        return (snapshot.running + snapshot.pending)
            .firstOrNull { it.promptId == promptId }
            ?.prompt
            ?.size
            ?: 0
    }

    private fun updateOverallProgress(promptId: String) {
        _progress.update { current ->
            val old = current[promptId] ?: return@update current
            current + (
                promptId to buildProgress(
                    promptId = promptId,
                    status = old.status,
                    currentNode = old.currentNode,
                    currentNodeLabel = old.currentNodeLabel,
                    value = old.value,
                    max = old.max,
                    nodeStartedAt = old.nodeStartedAt,
                )
                )
        }
    }

    private fun refreshOverallProgress() {
        _progress.update { current ->
            current.mapValues { (promptId, old) ->
                buildProgress(
                    promptId = promptId,
                    status = old.status,
                    currentNode = old.currentNode,
                    currentNodeLabel = old.currentNodeLabel,
                    value = old.value,
                    max = old.max,
                    nodeStartedAt = old.nodeStartedAt,
                )
            }
        }
    }

    private fun syncTasksWithQueue(snapshot: QueueSnapshot) {
        val result = TaskListSyncPolicy.applyQueue(
            tasks = _tasks.value,
            snapshot = snapshot,
            now = System.currentTimeMillis(),
        )
        _tasks.value = result.tasks
        result.changed.forEach(::persistTask)
    }

    private fun syncTasksWithHistory(entries: List<HistoryEntry>) {
        val result = TaskListSyncPolicy.applyHistory(
            tasks = _tasks.value,
            entries = entries,
            now = System.currentTimeMillis(),
        )
        _tasks.value = result.tasks
        result.changed.forEach(::persistTask)
    }

    private suspend fun updateTask(
        promptId: String,
        source: TaskStatusSource = TaskStatusSource.WS,
        transform: (TaskRecord) -> TaskRecord,
    ) {
        // 调用方（WS events.collect）已持有 stateMutex；使用无锁版本避免非重入死锁。
        taskEventCoordinator.updateTaskLocked(
            promptId = promptId,
            source = source,
            transform = transform,
        )
    }

    private fun persistTask(record: TaskRecord) {
        val identity = currentSnapshot?.identity ?: return
        val generation = identityGeneration
        val serverId = record.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID }
        taskPersistenceQueue.enqueue(serverId, record, identity, generation)
    }

    suspend fun remove(promptId: String, serverId: String): Result<Unit> =
        taskEventCoordinator.remove(serverId, promptId)

    suspend fun replace(
        serverId: String,
        oldPromptId: String,
        record: TaskRecord,
    ): Result<Unit> = taskEventCoordinator.replace(serverId, oldPromptId, record)

    private fun nodeLabel(promptId: String, nodeId: String?): String? {
        if (nodeId == null) return null
        val snapshot = _queue.value ?: return null
        return (snapshot.running + snapshot.pending)
            .firstOrNull { it.promptId == promptId }
            ?.nodeTitles
            ?.get(nodeId)
    }

    private companion object {
        const val HISTORY_PAGE_SIZE = 100
        const val HISTORY_PAGE_STEP = 50
    }
}
