package io.github.xiaowu20245.nekopaint.feature.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.data.repository.HistoryStore
import io.github.xiaowu20245.nekopaint.data.repository.ImageSaver
import io.github.xiaowu20245.nekopaint.data.repository.ImageShareManager
import io.github.xiaowu20245.nekopaint.data.repository.PendingWorkflowRefillStore
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.TaskStore
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.data.repository.dedupeHistoryByPrompt
import io.github.xiaowu20245.nekopaint.data.repository.dedupeTasksByPrompt
import io.github.xiaowu20245.nekopaint.data.repository.mergeHistoryArchives
import io.github.xiaowu20245.nekopaint.data.repository.mergeTaskArchives
import io.github.xiaowu20245.nekopaint.data.repository.sameEndpointServerIds
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HistoryNotice {
    data class Success(val promptId: String) : HistoryNotice
    data object NoServer : HistoryNotice
    data class Error(val message: String) : HistoryNotice
}

sealed interface SaveNotice {
    data class Success(val uri: Uri, val displayName: String) : SaveNotice
    data class Failure(val message: String) : SaveNotice
}

sealed interface ShareNotice {
    data class Success(val uri: Uri, val mimeType: String, val displayName: String) : ShareNotice
    data class Failure(val message: String) : ShareNotice
}

sealed interface HistoryEditNotice {
    data object Deleted : HistoryEditNotice
    data object Cleared : HistoryEditNotice
    data class Error(val message: String) : HistoryEditNotice
}

enum class HistoryViewMode {
    TIMELINE,
    GRID,
    COMPACT,
}

enum class HistoryFilter {
    ALL,
    OUTPUTS,
    TEMP,
}

data class HistoryHost(
    val id: String,
    val name: String,
    val active: Boolean,
    val serverIds: Set<String> = emptySet(),
    val aliasNames: List<String> = listOf(name),
    val aliasCount: Int = 1,
)

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val tasks: List<TaskRecord> = emptyList(),
    val hosts: List<HistoryHost> = emptyList(),
    val activeServerId: String = ServerConfigStore.DEFAULT_SERVER_ID,
    val selectedHostId: String? = null,
    val connection: ComfyConnectionState = ComfyConnectionState.Disconnected,
    val error: String? = null,
    val refreshing: Boolean = false,
    val resubmitting: Boolean = false,
    val notice: HistoryNotice? = null,
    val savingImageUrl: String? = null,
    val saveNotice: SaveNotice? = null,
    val sharingImageUrl: String? = null,
    val shareNotice: ShareNotice? = null,
    val showTempPreviews: Boolean = false,
    val hasMoreHistory: Boolean = false,
    val loadingMoreHistory: Boolean = false,
    val deletingPromptIds: Set<String> = emptySet(),
    val clearingHistory: Boolean = false,
    val editNotice: HistoryEditNotice? = null,
    val viewMode: HistoryViewMode = HistoryViewMode.TIMELINE,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val exporting: Boolean = false,
    val exportNotice: String? = null,
    val refillNotice: String? = null,
)

class HistoryViewModel(
    private val taskManager: TaskManager,
    private val taskStore: TaskStore,
    private val historyStore: HistoryStore,
    private val serverConfigStore: ServerConfigStore,
    private val workflowRepository: WorkflowRepository,
    private val pendingRefillStore: PendingWorkflowRefillStore,
    private val imageSaver: ImageSaver,
    private val imageShareManager: ImageShareManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val demoEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val demoTasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    private val archivedEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val archivedTasks = MutableStateFlow<List<TaskRecord>>(emptyList())

    private val liveState = combine(
        taskManager.history,
        taskManager.tasks,
        taskManager.connection,
    ) { entries, tasks, connection ->
        Triple(entries, tasks, connection)
    }

    private val archiveState = combine(archivedEntries, archivedTasks) { entries, tasks ->
        entries to tasks
    }

    private val demoState = combine(demoEntries, demoTasks) { entries, tasks ->
        entries to tasks
    }

    private val serverState = combine(
        serverConfigStore.servers,
        serverConfigStore.activeServerId,
    ) { servers, activeId ->
        ServerState(
            servers = servers,
            activeId = activeId
                ?: servers.firstOrNull()?.id
                ?: ServerConfigStore.DEFAULT_SERVER_ID,
        )
    }

    private val base = combine(liveState, archiveState, demoState, serverState) { live, archive, demo, servers ->
        val (liveEntries, liveTasks, connection) = live
        val mergedEntries = mergeHistoryArchives(
            archived = archive.first,
            live = liveEntries,
        ).let { entries ->
            if (demo.first.isEmpty()) entries else mergeHistoryArchives(entries, demo.first)
        }.map { it.withHostId() }
        val mergedTasks = mergeTaskArchives(
            archived = archive.second,
            live = liveTasks,
        ).let { tasks ->
            if (demo.second.isEmpty()) tasks else mergeTaskArchives(tasks, demo.second)
        }.map { it.withHostId() }
        HistoryUiState(
            entries = dedupeHistoryByPrompt(mergedEntries, servers.activeId),
            tasks = dedupeTasksByPrompt(mergedTasks, servers.activeId),
            connection = connection,
        )
    }

    private val resubmitState = MutableStateFlow(ResubmitState())
    private val saveState = MutableStateFlow(SaveState())
    private val shareState = MutableStateFlow(ShareState())

    private val operationState = combine(
        resubmitState,
        saveState,
        shareState,
    ) { resubmit, save, share ->
        OperationState(resubmit = resubmit, save = save, share = share)
    }

    private val runtimeState = combine(
        taskManager.historyError,
        taskManager.refreshing,
        taskManager.hasMoreHistory,
        taskManager.loadingMoreHistory,
    ) { error, refreshing, hasMore, loadingMore ->
        RuntimeState(
            error = error,
            refreshing = refreshing,
            hasMore = hasMore,
            loadingMore = loadingMore,
        )
    }

    private val editState = MutableStateFlow(EditState())
    private val customViewState = MutableStateFlow(CustomViewState())

    private val combinedEditState = combine(editState, customViewState) { edit, custom ->
        edit to custom
    }

    private val controlsState = combine(
        runtimeState,
        operationState,
        combinedEditState,
    ) { runtime, operation, editAndCustom ->
        ControlsState(
            runtime = runtime,
            operation = operation,
            edit = editAndCustom.first,
            custom = editAndCustom.second,
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        base,
        controlsState,
        settingsStore.showTempPreviews,
        serverState,
    ) { state, controls, showTempPreviews, servers ->
        val runtime = controls.runtime
        val operation = controls.operation
        val edit = controls.edit
        val custom = controls.custom
        val hosts = buildHosts(
            servers = servers,
            entries = state.entries,
            tasks = state.tasks,
        )
        state.copy(
            hosts = hosts,
            activeServerId = servers.activeId,
            selectedHostId = custom.selectedHostId
                ?.takeIf { id -> hosts.any { it.id == id } },
            error = runtime.error,
            refreshing = runtime.refreshing,
            resubmitting = operation.resubmit.submitting,
            notice = operation.resubmit.notice,
            savingImageUrl = operation.save.savingUrl,
            saveNotice = operation.save.notice,
            sharingImageUrl = operation.share.sharingUrl,
            shareNotice = operation.share.notice,
            showTempPreviews = showTempPreviews,
            hasMoreHistory = runtime.hasMore,
            loadingMoreHistory = runtime.loadingMore,
            deletingPromptIds = edit.deletingPromptIds,
            clearingHistory = edit.clearing,
            editNotice = edit.notice,
            viewMode = custom.viewMode,
            filter = custom.filter,
            exporting = custom.exporting,
            exportNotice = custom.exportNotice,
            refillNotice = custom.refillNotice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    init {
        taskManager.refresh()
        refreshArchive()
        viewModelScope.launch {
            serverConfigStore.activeServerId
                .distinctUntilChanged()
                .collect { refreshArchive() }
        }
    }

    fun setHostFilter(hostId: String?) {
        customViewState.value = customViewState.value.copy(selectedHostId = hostId)
    }

    private fun refreshArchive() {
        viewModelScope.launch {
            archivedEntries.value = runCatching { historyStore.loadAll() }
                .getOrDefault(emptyList())
            archivedTasks.value = runCatching { taskStore.listAll() }
                .getOrDefault(emptyList())
        }
    }

    private fun buildHosts(
        servers: ServerState,
        entries: List<HistoryEntry>,
        tasks: List<TaskRecord>,
    ): List<HistoryHost> {
        val profiles = servers.servers
        val activeProfile = profiles.firstOrNull { it.id == servers.activeId }
        val activeEndpoint = activeProfile?.endpointKey

        // 同一地址的多个配置归为一个主机组，避免同一台电脑重复出现在筛选里。
        val groups = LinkedHashMap<String, MutableList<ServerProfile>>()
        profiles.forEach { profile ->
            groups.getOrPut(profile.endpointKey) { mutableListOf() } += profile
        }

        val hosts = groups.map { (endpoint, members) ->
            val active = endpoint == activeEndpoint
            val primary = if (active) {
                members.firstOrNull { it.id == servers.activeId } ?: members.first()
            } else {
                members.first()
            }
            HistoryHost(
                id = endpoint,
                name = primary.name,
                active = active,
                serverIds = members.map { it.id }.toSet(),
                aliasNames = members.map { it.name },
                aliasCount = members.size,
            )
        }.toMutableList()

        val knownIds = profiles.map { it.id }.toSet()
        val unknownIds = LinkedHashSet<String>()
        entries.mapTo(unknownIds) { it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } }
        tasks.mapTo(unknownIds) { it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } }
        unknownIds.removeAll(knownIds)
        unknownIds.forEach { id ->
            val fallbackName = if (id == ServerConfigStore.DEFAULT_SERVER_ID) {
                DEFAULT_HOST_NAME
            } else {
                id.take(8)
            }
            hosts += HistoryHost(
                id = "server:$id",
                name = fallbackName,
                active = id == servers.activeId,
                serverIds = setOf(id),
                aliasNames = listOf(fallbackName),
            )
        }

        return hosts.sortedWith(
            compareByDescending<HistoryHost> { it.active }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun HistoryEntry.withHostId(): HistoryEntry =
        if (serverId.isBlank()) copy(serverId = ServerConfigStore.DEFAULT_SERVER_ID) else this

    private fun TaskRecord.withHostId(): TaskRecord =
        if (serverId.isBlank()) copy(serverId = ServerConfigStore.DEFAULT_SERVER_ID) else this

    fun loadDemoHistory() {
        val now = System.currentTimeMillis()
        val p1 = "demo-hist-001"
        val p2 = "demo-hist-002"
        val p3 = "demo-hist-003"

        demoEntries.value = listOf(
            HistoryEntry(
                promptId = p1,
                status = "success",
                completed = true,
                timestamp = now - 1000 * 60 * 12,
                startedAt = now - 1000 * 60 * 12 - 18000,
                finishedAt = now - 1000 * 60 * 12,
                images = listOf(
                    HistoryImage("flux_out_01.png", "", "output", "https://picsum.photos/512/512?random=1"),
                    HistoryImage("flux_out_02.png", "", "output", "https://picsum.photos/512/512?random=2"),
                ),
                errorNodeId = null,
                errorMessage = null,
            ),
            HistoryEntry(
                promptId = p2,
                status = "success",
                completed = true,
                timestamp = now - 1000 * 60 * 95,
                startedAt = now - 1000 * 60 * 95 - 24000,
                finishedAt = now - 1000 * 60 * 95,
                images = listOf(
                    HistoryImage("anime_01.png", "", "output", "https://picsum.photos/512/512?random=3"),
                    HistoryImage("anime_preview_temp.png", "", "temp", "https://picsum.photos/512/512?random=4"),
                ),
                errorNodeId = null,
                errorMessage = null,
            ),
            HistoryEntry(
                promptId = p3,
                status = "success",
                completed = true,
                timestamp = now - 1000 * 60 * 60 * 28,
                startedAt = now - 1000 * 60 * 60 * 28 - 15000,
                finishedAt = now - 1000 * 60 * 60 * 28,
                images = listOf(
                    HistoryImage("scenic_lake.png", "", "output", "https://picsum.photos/512/512?random=5"),
                ),
                errorNodeId = null,
                errorMessage = null,
            ),
        )

        demoTasks.value = listOf(
            TaskRecord(
                promptId = p1,
                templateId = "flux-dev",
                templateName = "Flux 快速生图",
                clientId = "local-client",
                submittedAt = now - 1000 * 60 * 12 - 20000,
                startedAt = now - 1000 * 60 * 12 - 18000,
                finishedAt = now - 1000 * 60 * 12,
                status = "success",
                values = mapOf(
                    "6:text" to "masterpiece, 1girl, cat ears, neon cyberpunk city, cinematic lighting, 8k",
                    "7:text" to "worst quality, low quality",
                    "3:seed" to "42819024",
                    "3:steps" to "20",
                    "3:cfg" to "7.0",
                    "3:sampler_name" to "euler",
                    "3:scheduler" to "normal",
                    "4:ckpt_name" to "flux1-dev-fp8.safetensors",
                    "5:width" to "1024",
                    "5:height" to "1024",
                ),
            ),
            TaskRecord(
                promptId = p2,
                templateId = "anime-sdxl",
                templateName = "动漫插画精细生成",
                clientId = "local-client",
                submittedAt = now - 1000 * 60 * 95 - 26000,
                startedAt = now - 1000 * 60 * 95 - 24000,
                finishedAt = now - 1000 * 60 * 95,
                status = "success",
                values = mapOf(
                    "6:text" to "1girl, silver hair, starry sky, floating glowing petals, beautiful eyes",
                    "3:seed" to "987654321",
                    "3:steps" to "28",
                    "3:cfg" to "6.5",
                    "3:sampler_name" to "euler_ancestral",
                    "3:scheduler" to "karras",
                    "4:ckpt_name" to "animagineXLV31.safetensors",
                    "5:width" to "1024",
                    "5:height" to "1024",
                ),
            ),
            TaskRecord(
                promptId = p3,
                templateId = "landscape-v1",
                templateName = "自然风景与建筑",
                clientId = "local-client",
                submittedAt = now - 1000 * 60 * 60 * 28 - 18000,
                startedAt = now - 1000 * 60 * 60 * 28 - 15000,
                finishedAt = now - 1000 * 60 * 60 * 28,
                status = "success",
                values = mapOf(
                    "6:text" to "scenic mountain lake at dawn, reflection in crystal clear water, pine trees",
                    "3:seed" to "554433221",
                    "3:steps" to "20",
                    "3:cfg" to "7.0",
                    "4:ckpt_name" to "sd_xl_base_1.0.safetensors",
                    "5:width" to "1024",
                    "5:height" to "1024",
                ),
            ),
        )
        bindDemoTasksToLocalTemplate()
    }

    /**
     * 调试用演示数据在本地存在工作流时，自动映射到真实模板 ID 与候选参数，
     * 便于在模拟器验证“回填至工作流”闭环；正式包不显示入口。
     */
    private fun bindDemoTasksToLocalTemplate() {
        viewModelScope.launch {
            val template = runCatching { workflowRepository.listTemplates() }
                .getOrDefault(emptyList())
                .firstOrNull() ?: return@launch
            demoTasks.value = demoTasks.value.map { task ->
                val mapped = buildMap {
                    task.values.forEach { (key, value) ->
                        val inputName = key.substringAfter(':', key)
                        val negative = key.endsWith(":text") && key.startsWith("7:")
                        val candidate = template.candidates.firstOrNull {
                            !it.readOnly && it.inputName == inputName &&
                                (negative == it.label.contains("负"))
                        }
                        if (candidate != null) put(candidate.id, value)
                    }
                }
                task.copy(
                    templateId = template.id,
                    templateName = template.name,
                    values = mapped,
                )
            }
        }
    }

    fun refresh() = taskManager.refresh()

    fun resubmit(task: TaskRecord) {
        if (resubmitState.value.submitting) return
        resubmitState.value = ResubmitState(submitting = true)
        viewModelScope.launch {
            val result = workflowRepository.resubmit(task)
            val notice = when (result) {
                is WorkflowSubmitResult.Success -> HistoryNotice.Success(result.promptId)
                is WorkflowSubmitResult.NoServer -> HistoryNotice.NoServer
                is WorkflowSubmitResult.Validation -> HistoryNotice.Error(result.summary)
                is WorkflowSubmitResult.Error -> HistoryNotice.Error(result.message)
            }
            resubmitState.value = ResubmitState(submitting = false, notice = notice)
            if (result is WorkflowSubmitResult.Success) {
                taskManager.refresh()
            }
        }
    }

    fun consumeNotice() {
        resubmitState.value = resubmitState.value.copy(notice = null)
    }

    fun saveImage(image: HistoryImage) {
        if (saveState.value.savingUrl != null) return
        val url = image.url
        if (url.isNullOrBlank()) {
            saveState.value = SaveState(notice = SaveNotice.Failure("图片地址无效"))
            return
        }
        saveState.value = SaveState(savingUrl = url)
        viewModelScope.launch {
            val result = imageSaver.save(url, image.filename)
            saveState.value = SaveState(
                notice = result.fold(
                    onSuccess = { SaveNotice.Success(it.uri, it.displayName) },
                    onFailure = { SaveNotice.Failure(it.message ?: "保存失败") },
                ),
            )
        }
    }

    fun consumeSaveNotice() {
        saveState.value = saveState.value.copy(notice = null)
    }

    fun shareImage(image: HistoryImage) {
        if (shareState.value.sharingUrl != null) return
        val url = image.url
        if (url.isNullOrBlank()) {
            shareState.value = ShareState(notice = ShareNotice.Failure("图片地址无效"))
            return
        }
        shareState.value = ShareState(sharingUrl = url)
        viewModelScope.launch {
            val result = imageShareManager.share(url, image.filename)
            shareState.value = ShareState(
                notice = result.fold(
                    onSuccess = {
                        ShareNotice.Success(it.uri, it.mimeType, it.displayName)
                    },
                    onFailure = { ShareNotice.Failure(it.message ?: "分享准备失败") },
                ),
            )
        }
    }

    fun consumeShareNotice() {
        shareState.value = shareState.value.copy(notice = null)
    }

    fun setShowTempPreviews(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setShowTempPreviews(value)
        }
    }

    fun loadMoreHistory() {
        taskManager.loadMoreHistory()
    }

    fun deleteHistory(promptId: String) {
        deleteHistory(setOf(promptId))
    }

    fun deleteHistory(promptIds: Set<String>) {
        if (promptIds.isEmpty() || editState.value.deletingPromptIds.isNotEmpty()) return
        viewModelScope.launch {
            val activeServerIds = activeHostServerIds()
            val activeServerId = serverConfigStore.activeServerId.first()
                ?: ServerConfigStore.DEFAULT_SERVER_ID
            // 同一地址的多个配置共享同一个远端历史；允许删除其中任一配置归档过的记录。
            val targetPromptIds = buildSet {
                taskManager.history.value.mapTo(this) { it.promptId }
                archivedEntries.value
                    .filter { it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } in activeServerIds }
                    .mapTo(this) { it.promptId }
            }
            val deletable = promptIds intersect targetPromptIds
            if (deletable.isEmpty()) {
                editState.value = editState.value.copy(
                    notice = HistoryEditNotice.Error("只能删除当前主机的历史"),
                )
                return@launch
            }
            editState.value = editState.value.copy(deletingPromptIds = deletable)
            val result = taskManager.deleteHistory(deletable)
            editState.value = editState.value.copy(
                deletingPromptIds = emptySet(),
                notice = result.fold(
                    onSuccess = {
                        val archivedSnapshot = archivedEntries.value
                        archivedEntries.update { entries ->
                            entries.filterNot {
                                it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } in
                                    activeServerIds && it.promptId in deletable
                            }
                        }
                        // 活跃主机由 TaskManager 写盘；其他同地址别名同步清理本地归档。
                        activeServerIds
                            .filter { it != activeServerId }
                            .forEach { serverId ->
                                val remaining = archivedSnapshot.filter {
                                    it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } ==
                                        serverId && it.promptId !in deletable
                                }
                                runCatching { historyStore.save(serverId, remaining) }
                            }
                        refreshArchive()
                        HistoryEditNotice.Deleted
                    },
                    onFailure = {
                        HistoryEditNotice.Error(it.message ?: "删除历史失败")
                    },
                ),
            )
        }
    }

    fun clearHistory() {
        if (editState.value.clearing) return
        editState.value = editState.value.copy(clearing = true)
        viewModelScope.launch {
            val activeServerIds = activeHostServerIds()
            val result = taskManager.clearHistory()
            editState.value = editState.value.copy(
                clearing = false,
                notice = result.fold(
                    onSuccess = {
                        archivedEntries.update { entries ->
                            entries.filterNot {
                                it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } in
                                    activeServerIds
                            }
                        }
                        // 同一地址的别名配置也清空本地归档，避免清空后旧卡片重新出现。
                        activeServerIds.forEach { serverId ->
                            runCatching { historyStore.save(serverId, emptyList()) }
                        }
                        refreshArchive()
                        HistoryEditNotice.Cleared
                    },
                    onFailure = {
                        HistoryEditNotice.Error(it.message ?: "清空历史失败")
                    },
                ),
            )
        }
    }

    private suspend fun activeHostServerIds(): Set<String> {
        val servers = runCatching { serverConfigStore.servers.first() }.getOrDefault(emptyList())
        val activeId = runCatching { serverConfigStore.activeServerId.first() }.getOrNull()
            ?: servers.firstOrNull()?.id
            ?: ServerConfigStore.DEFAULT_SERVER_ID
        val activeProfile = servers.firstOrNull { it.id == activeId }
        if (activeProfile == null) return setOf(activeId)
        return sameEndpointServerIds(servers, activeProfile.id)
    }

    fun consumeEditNotice() {
        editState.value = editState.value.copy(notice = null)
    }

    fun setViewMode(mode: HistoryViewMode) {
        customViewState.value = customViewState.value.copy(viewMode = mode)
    }

    fun setFilter(filter: HistoryFilter) {
        customViewState.value = customViewState.value.copy(filter = filter)
    }

    fun refillWorkflow(task: TaskRecord, onComplete: (Result<String>) -> Unit) {
        if (task.values.isEmpty()) {
            onComplete(Result.failure(IllegalStateException("该记录没有保存参数快照")))
            return
        }
        viewModelScope.launch {
            val template = runCatching { workflowRepository.getTemplate(task.templateId) }.getOrNull()
            if (template == null) {
                onComplete(Result.failure(IllegalStateException("找不到原工作流模板")))
                return@launch
            }
            // 只暂存参数，到详情页由用户确认后保存，避免直接覆盖已保存模板。
            pendingRefillStore.put(template.id, task.values)
            onComplete(Result.success(template.id))
        }
    }

    fun exportImages(images: List<HistoryImage>, onComplete: (success: Int, failed: Int) -> Unit) {
        if (images.isEmpty() || customViewState.value.exporting) return
        customViewState.value = customViewState.value.copy(exporting = true)
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            for (img in images) {
                val url = img.url ?: continue
                val result = imageSaver.save(url, img.filename)
                if (result.isSuccess) successCount++ else failCount++
            }
            customViewState.value = customViewState.value.copy(exporting = false)
            onComplete(successCount, failCount)
        }
    }

    fun clearRefillNotice() {
        customViewState.value = customViewState.value.copy(refillNotice = null)
    }

    fun clearExportNotice() {
        customViewState.value = customViewState.value.copy(exportNotice = null)
    }

    private data class CustomViewState(
        val viewMode: HistoryViewMode = HistoryViewMode.TIMELINE,
        val filter: HistoryFilter = HistoryFilter.ALL,
        val selectedHostId: String? = null,
        val exporting: Boolean = false,
        val exportNotice: String? = null,
        val refillNotice: String? = null,
    )

    private data class ServerState(
        val servers: List<ServerProfile>,
        val activeId: String,
    )

    private data class ControlsState(
        val runtime: RuntimeState,
        val operation: OperationState,
        val edit: EditState,
        val custom: CustomViewState,
    )

    private data class ResubmitState(
        val submitting: Boolean = false,
        val notice: HistoryNotice? = null,
    )

    private data class SaveState(
        val savingUrl: String? = null,
        val notice: SaveNotice? = null,
    )

    private data class ShareState(
        val sharingUrl: String? = null,
        val notice: ShareNotice? = null,
    )

    private data class OperationState(
        val resubmit: ResubmitState,
        val save: SaveState,
        val share: ShareState,
    )

    private data class RuntimeState(
        val error: String? = null,
        val refreshing: Boolean = false,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
    )

    private data class EditState(
        val deletingPromptIds: Set<String> = emptySet(),
        val clearing: Boolean = false,
        val notice: HistoryEditNotice? = null,
    )

    companion object {
        private const val DEFAULT_HOST_NAME = "本机 ComfyUI"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    taskManager = container.taskManager,
                    taskStore = container.taskStore,
                    historyStore = container.historyStore,
                    serverConfigStore = container.serverConfigStore,
                    workflowRepository = container.workflowRepository,
                    pendingRefillStore = container.pendingWorkflowRefillStore,
                    imageSaver = container.imageSaver,
                    imageShareManager = container.imageShareManager,
                    settingsStore = container.settingsStore,
                )
            }
        }
    }
}
