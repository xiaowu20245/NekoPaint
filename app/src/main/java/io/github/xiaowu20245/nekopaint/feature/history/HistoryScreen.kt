package io.github.xiaowu20245.nekopaint.feature.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarIconAction
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarMenuItem
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConnectionStatus
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusMapper
import io.github.xiaowu20245.nekopaint.core.ui.component.icon
import io.github.xiaowu20245.nekopaint.core.ui.component.label
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.feature.common.asString
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenDetail: (String) -> Unit,
    onOpenWorkflow: (String) -> Unit = {},
    onAskNekoI: (String) -> Unit = {},
    onTopBarIconActionsChange: (List<NekoTopBarIconAction>) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var activeSheetEntry by remember { mutableStateOf<Pair<HistoryEntry, Int>?>(null) }
    var lightboxTarget by remember { mutableStateOf<Pair<HistoryEntry, HistoryImage>?>(null) }
    var actionTarget by remember { mutableStateOf<Pair<HistoryEntry, HistoryImage>?>(null) }
    var pendingDeletePromptId by remember { mutableStateOf<String?>(null) }

    val exportResultTemplate = stringResource(R.string.history_export_result)
    val refillFailedTemplate = stringResource(R.string.history_refill_failed)
    val promptCopiedMessage = stringResource(R.string.history_prompt_copied)
    val imageSavedTemplate = stringResource(R.string.image_saved)
    val imageSaveFailedTemplate = stringResource(R.string.image_save_failed)

    val editMessage = when (val notice = state.editNotice) {
        HistoryEditNotice.Deleted -> stringResource(R.string.history_deleted)
        HistoryEditNotice.Cleared -> stringResource(R.string.history_cleared)
        is HistoryEditNotice.Error -> stringResource(
            R.string.history_edit_failed,
            notice.message.asString(),
        )
        null -> null
    }

    LaunchedEffect(editMessage) {
        if (editMessage != null) {
            snackbarHostState.showSnackbar(editMessage)
            viewModel.consumeEditNotice()
        }
    }

    LaunchedEffect(state.saveNotice) {
        when (val notice = state.saveNotice) {
            is SaveNotice.Success -> {
                snackbarHostState.showSnackbar(
                    String.format(imageSavedTemplate, notice.displayName),
                )
                viewModel.consumeSaveNotice()
            }
            is SaveNotice.Failure -> {
                snackbarHostState.showSnackbar(
                    String.format(imageSaveFailedTemplate, notice.message),
                )
                viewModel.consumeSaveNotice()
            }
            null -> Unit
        }
    }

    val readOnlyNoticeTemplate = stringResource(R.string.history_host_readonly_notice)
    val activeHost = remember(state.hosts) { state.hosts.firstOrNull { it.active } }
    val activeServerIds = remember(activeHost) { activeHost?.serverIds.orEmpty() }
    val hostByServerId = remember(state.hosts) {
        buildMap<String, HistoryHost> {
            state.hosts.forEach { host ->
                host.serverIds.forEach { serverId -> put(serverId, host) }
            }
        }
    }
    val hostDisplayNames = remember(state.hosts) {
        buildMap<String, String> {
            state.hosts.forEach { host ->
                val displayName = if (host.aliasCount > 1) {
                    "${host.name} +${host.aliasCount - 1}"
                } else {
                    host.name
                }
                host.serverIds.forEach { serverId -> put(serverId, displayName) }
            }
        }
    }
    val hostSimpleName: (String) -> String = { serverId ->
        hostByServerId[serverId]?.name ?: serverId.take(8)
    }
    val hostDisplayName: (String) -> String = { serverId ->
        val host = hostByServerId[serverId]
        when {
            host == null -> serverId.take(8)
            host.aliasCount > 1 -> "${host.name} +${host.aliasCount - 1}"
            else -> host.name
        }
    }
    val activeHostName = activeHost?.name ?: hostSimpleName(state.activeServerId)
    val isOperable: (String) -> Boolean = { serverId ->
        serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } in activeServerIds
    }
    val notifyReadOnly: (String) -> Unit = { serverId ->
        scope.launch {
            snackbarHostState.showSnackbar(
                String.format(readOnlyNoticeTemplate, hostSimpleName(serverId)),
            )
        }
    }

    LaunchedEffect(state.activeServerId, state.selectedHostId) {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(lifecycleOwner, state.connection) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (state.connection == ComfyConnectionState.Unconfigured) {
                return@repeatOnLifecycle
            }
            delay(250)
            while (isActive) {
                viewModel.refresh()
                delay(10_000)
            }
        }
    }

    val selectedHost = remember(state.hosts, state.selectedHostId) {
        state.hosts.firstOrNull { it.id == state.selectedHostId }
    }
    // 先按主机组筛选，再按成图/草稿筛选。
    val hostFilteredEntries = remember(state.entries, selectedHost) {
        if (selectedHost == null) {
            state.entries
        } else {
            state.entries.filter { it.serverId in selectedHost.serverIds }
        }
    }
    val filteredEntries = remember(hostFilteredEntries, state.filter) {
        when (state.filter) {
            HistoryFilter.ALL -> hostFilteredEntries
            HistoryFilter.OUTPUTS -> hostFilteredEntries.filter { entry ->
                entry.images.any { it.type == "output" }
            }
            HistoryFilter.TEMP -> hostFilteredEntries.filter { entry ->
                entry.images.any { it.type == "temp" }
            }
        }
    }
    val showTempImages = HistoryVisibilityPolicy.effectiveShowTemp(
        filter = state.filter,
        globalShowTemp = state.showTempPreviews,
    )
    val tempSwitchEnabled = HistoryVisibilityPolicy.isTempSwitchEnabled(state.filter)
    val activeHostEntries = remember(state.entries, activeServerIds) {
        state.entries.filter { isOperable(it.serverId) }
    }
    val selectableEntries = remember(filteredEntries, activeServerIds) {
        filteredEntries.filter { isOperable(it.serverId) }
    }
    val selectableIds = remember(selectableEntries) {
        selectableEntries.map { it.promptId }.toSet()
    }

    LaunchedEffect(selectableIds) {
        selectedIds = HistorySelectionPolicy.prune(selectedIds, selectableIds)
        if (selectionMode && selectableIds.isEmpty()) {
            selectionMode = false
        }
    }

    val tasksById = remember(state.tasks) {
        state.tasks.associateBy { it.promptId }
    }
    val openLightbox: (HistoryEntry, Int) -> Unit = { entry, index ->
        entry.visibleImages(showTempImages)
            .getOrNull(index)
            ?.let { lightboxTarget = entry to it }
    }
    val enterSelection: (HistoryEntry) -> Unit = { entry ->
        if (!isOperable(entry.serverId)) {
            notifyReadOnly(entry.serverId)
        } else if (!selectionMode) {
            selectionMode = true
            selectedIds = setOf(entry.promptId)
        }
    }
    val toggleSelection: (HistoryEntry) -> Unit = { entry ->
        if (!isOperable(entry.serverId)) {
            notifyReadOnly(entry.serverId)
        } else {
            selectedIds = HistorySelectionPolicy.toggle(
                selected = selectedIds,
                promptId = entry.promptId,
                selectable = selectableIds,
            )
        }
    }
    val openDetail: (HistoryEntry) -> Unit = { entry ->
        if (!isOperable(entry.serverId)) {
            notifyReadOnly(entry.serverId)
        } else {
            onOpenDetail(entry.promptId)
        }
    }
    val refillWorkflow: (TaskRecord) -> Unit = { task ->
        if (!isOperable(task.serverId)) {
            notifyReadOnly(task.serverId)
        } else {
            viewModel.refillWorkflow(task) { result ->
                result.fold(
                    onSuccess = { templateId ->
                        onOpenWorkflow(templateId)
                    },
                    onFailure = { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                String.format(refillFailedTemplate, err.message),
                            )
                        }
                    },
                )
            }
        }
    }
    val askNekoI: (HistoryEntry) -> Unit = { entry ->
        if (!isOperable(entry.serverId)) {
            notifyReadOnly(entry.serverId)
        } else {
            activeSheetEntry = null
            onAskNekoI(entry.promptId)
        }
    }

    val canClearHistory = activeHostEntries.isNotEmpty()
    val canLoadMore = selectedHost == null || selectedHost.active
    val refreshDescription = stringResource(R.string.history_refresh)
    val moreDescription = stringResource(R.string.history_more_actions)
    val batchSelectLabel = stringResource(R.string.history_batch_select)
    val clearAllLabel = stringResource(R.string.history_clear_current)
    val exportDescription = stringResource(R.string.history_export_selected)
    val deleteDescription = stringResource(R.string.history_delete_selected)
    val doneLabel = stringResource(R.string.history_select_done)
    val doneDescription = stringResource(R.string.history_select_done_desc)
    val allSelected = HistorySelectionPolicy.allSelected(selectedIds, selectableIds)
    val selectAllDescription = stringResource(
        if (allSelected) R.string.history_deselect_all else R.string.history_select_all,
    )
    val deleteTint = if (selectedIds.isNotEmpty()) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val nextViewMode = when (state.viewMode) {
        HistoryViewMode.TIMELINE -> HistoryViewMode.GRID
        HistoryViewMode.GRID -> HistoryViewMode.COMPACT
        HistoryViewMode.COMPACT -> HistoryViewMode.TIMELINE
    }
    val viewSwitchIcon = when (nextViewMode) {
        HistoryViewMode.TIMELINE -> NekoIcons.Timeline
        HistoryViewMode.GRID -> NekoIcons.GridView
        HistoryViewMode.COMPACT -> NekoIcons.ViewCompact
    }
    val viewSwitchDescription = stringResource(
        when (nextViewMode) {
            HistoryViewMode.TIMELINE -> R.string.history_view_timeline
            HistoryViewMode.GRID -> R.string.history_view_grid
            HistoryViewMode.COMPACT -> R.string.history_view_compact
        },
    )
    DisposableEffect(
        selectionMode,
        selectedIds,
        filteredEntries,
        selectableEntries,
        state.exporting,
        state.deletingPromptIds,
        showTempImages,
        canClearHistory,
        state.refreshing,
        state.viewMode,
        viewSwitchDescription,
    ) {
        val actions = buildList {
            if (selectionMode) {
                // 多选操作全部收纳到顶栏右上角，底部不再有任何浮层。
                addAll(
                    historySelectionActions(
                        allSelected = allSelected,
                        selectedIds = selectedIds,
                        exporting = state.exporting,
                        deleting = state.deletingPromptIds.isNotEmpty(),
                        selectAllDescription = selectAllDescription,
                        exportDescription = exportDescription,
                        deleteDescription = deleteDescription,
                        deleteTint = deleteTint,
                        doneLabel = doneLabel,
                        doneDescription = doneDescription,
                        onToggleAll = {
                            selectedIds = HistorySelectionPolicy.toggleAll(
                                selected = selectedIds,
                                selectable = selectableIds,
                            )
                        },
                        onExport = {
                            val selectedImages = filteredEntries
                                .filter { it.promptId in selectedIds }
                                .flatMap { it.visibleImages(showTempImages) }
                            viewModel.exportImages(selectedImages) { success, _ ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        String.format(exportResultTemplate, success),
                                    )
                                }
                            }
                        },
                        onDelete = { showBatchDeleteConfirm = true },
                        onDone = {
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                    ),
                )
            } else {
                // 普通模式：单一视图循环 + 刷新 + 更多
                add(
                    NekoTopBarIconAction(
                        icon = viewSwitchIcon,
                        contentDescription = viewSwitchDescription,
                        onClick = { viewModel.setViewMode(nextViewMode) },
                    ),
                )
                add(
                    NekoTopBarIconAction(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = refreshDescription,
                        enabled = !state.refreshing,
                        onClick = viewModel::refresh,
                    ),
                )
                add(
                    NekoTopBarIconAction(
                        icon = Icons.Outlined.MoreVert,
                        contentDescription = moreDescription,
                        menuItems = buildList {
                            add(
                                NekoTopBarMenuItem(
                                    label = batchSelectLabel,
                                    enabled = selectableEntries.isNotEmpty(),
                                    icon = Icons.Outlined.Check,
                                    onClick = {
                                        selectionMode = true
                                        selectedIds = emptySet()
                                    },
                                ),
                            )
                            if (canClearHistory) {
                                add(
                                    NekoTopBarMenuItem(
                                        label = clearAllLabel,
                                        destructive = true,
                                        icon = Icons.Outlined.Delete,
                                        onClick = { showClearConfirm = true },
                                    ),
                                )
                            }
                        },
                    ),
                )
            }
        }
        onTopBarIconActionsChange(actions)
        onDispose { onTopBarIconActionsChange(emptyList()) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 极简画廊流：Sub-header 只保留一行通透的筛选胶囊
            HistorySubHeader(
                filter = state.filter,
                totalCount = hostFilteredEntries.size,
                selectedCount = if (selectionMode) selectedIds.size else null,
                hosts = state.hosts,
                selectedHostId = state.selectedHostId,
                showTempPreviews = showTempImages,
                tempSwitchEnabled = tempSwitchEnabled,
                onHostChange = viewModel::setHostFilter,
                onFilterChange = viewModel::setFilter,
                onShowTempChange = viewModel::setShowTempPreviews,
            )

            val errorMessage = state.error?.asString()
            val connectionStatus = NekoStatusMapper.connection(
                state = state.connection,
                hasError = state.error != null,
            )
            val statusNotice = when {
                connectionStatus == NekoConnectionStatus.UNCONFIGURED ->
                    stringResource(R.string.history_config_invalid)
                connectionStatus == NekoConnectionStatus.ERROR ->
                    stringResource(R.string.history_error, errorMessage.orEmpty())
                connectionStatus != NekoConnectionStatus.ONLINE ->
                    stringResource(R.string.history_stale)
                else -> null
            }
            if (statusNotice != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = statusNotice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 2,
                    )
                }
            }

            val selectedInactiveHost = selectedHost != null && !selectedHost.active
            if (selectedInactiveHost) {
                Text(
                    text = stringResource(
                        R.string.history_host_filter_readonly,
                        activeHostName,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
            if (filteredEntries.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        EmptyHistory(
                            loading = state.refreshing,
                        )
                    }
                }
            } else {
                key(state.selectedHostId, state.filter) {
                    when (state.viewMode) {
                        HistoryViewMode.TIMELINE -> {
                            TimelineBatchView(
                                entries = filteredEntries,
                                tasksById = tasksById,
                                hostNames = hostDisplayNames,
                                activeServerIds = activeServerIds,
                                showTempPreviews = showTempImages,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onToggleSelect = toggleSelection,
                                onLongClick = enterSelection,
                                onOpenSheet = { entry, imageIndex ->
                                    activeSheetEntry = entry to imageIndex
                                },
                                onOpenLightbox = openLightbox,
                                onOpenDetail = openDetail,
                                onRefillWorkflow = refillWorkflow,
                                hasMore = state.hasMoreHistory && canLoadMore,
                                loadingMore = state.loadingMoreHistory && canLoadMore,
                                onLoadMore = viewModel::loadMoreHistory,
                            )
                        }

                        HistoryViewMode.GRID -> {
                            GalleryGridView(
                                entries = filteredEntries,
                                tasksById = tasksById,
                                hostNames = hostDisplayNames,
                                activeServerIds = activeServerIds,
                                showTempPreviews = showTempImages,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onToggleSelect = toggleSelection,
                                onLongClick = enterSelection,
                                onOpenSheet = { entry, imageIndex ->
                                    activeSheetEntry = entry to imageIndex
                                },
                                onOpenLightbox = openLightbox,
                                hasMore = state.hasMoreHistory && canLoadMore,
                                loadingMore = state.loadingMoreHistory && canLoadMore,
                                onLoadMore = viewModel::loadMoreHistory,
                            )
                        }

                        HistoryViewMode.COMPACT -> {
                            CompactAlbumView(
                                entries = filteredEntries,
                                hostNames = hostDisplayNames,
                                activeServerIds = activeServerIds,
                                showTempPreviews = showTempImages,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onToggleSelect = toggleSelection,
                                onOpenLightbox = openLightbox,
                                onLongClick = enterSelection,
                                hasMore = state.hasMoreHistory && canLoadMore,
                                loadingMore = state.loadingMoreHistory && canLoadMore,
                                onLoadMore = viewModel::loadMoreHistory,
                            )
                        }
                    }
                }
            }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    // ModalBottomSheet Parameter Drawer
    activeSheetEntry?.let { (entry, imgIndex) ->
        val task = tasksById[entry.promptId]
        val images = entry.visibleImages(showTempImages)
        val selectedImage = images.getOrNull(imgIndex) ?: images.firstOrNull()

        ModalBottomSheet(
            onDismissRequest = { activeSheetEntry = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            HistoryDetailDrawerContent(
                entry = entry,
                task = task,
                image = selectedImage,
                hostName = hostDisplayName(entry.serverId),
                isActiveHost = isOperable(entry.serverId),
                onSaveImage = viewModel::saveImage,
                onImageClick = { image ->
                    lightboxTarget = entry to image
                },
                onImageLongPress = { image ->
                    actionTarget = entry to image
                },
                onRefillWorkflow = { t ->
                    activeSheetEntry = null
                    refillWorkflow(t)
                },
                onOpenFullDetail = {
                    activeSheetEntry = null
                    openDetail(entry)
                },
                onAskNekoI = {
                    askNekoI(entry)
                },
                onCopyPrompt = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("prompt", text))
                    scope.launch {
                        snackbarHostState.showSnackbar(promptCopiedMessage)
                    }
                },
            )
        }
    }

    lightboxTarget?.let { (entry, image) ->
        HistoryLightboxDialog(
            image = image,
            onDismiss = { lightboxTarget = null },
            onLongPress = { actionTarget = entry to image },
        )
    }

    actionTarget?.let { (entry, image) ->
        val task = tasksById[entry.promptId]
        val promptText = HistoryMetadataExtractor.extract(task).prompt
            ?: HistoryMetadataExtractor.extractPrompt(task, null).orEmpty()
        HistoryImageActionSheet(
            onDismiss = { actionTarget = null },
            canDelete = isOperable(entry.serverId),
            onSaveImage = {
                actionTarget = null
                viewModel.saveImage(image)
            },
            onShareImage = {
                actionTarget = null
                viewModel.shareImage(image)
            },
            onCopyPrompt = {
                actionTarget = null
                if (promptText.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("prompt", promptText))
                    scope.launch { snackbarHostState.showSnackbar(promptCopiedMessage) }
                }
            },
            onDelete = {
                actionTarget = null
                activeSheetEntry = null
                lightboxTarget = null
                pendingDeletePromptId = entry.promptId
            },
        )
    }

    pendingDeletePromptId?.let { promptId ->
        NekoConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            message = stringResource(R.string.history_delete_message),
            confirmLabel = stringResource(R.string.history_delete),
            onConfirm = {
                pendingDeletePromptId = null
                viewModel.deleteHistory(promptId)
            },
            onDismiss = { pendingDeletePromptId = null },
        )
    }

    if (showClearConfirm) {
        NekoConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(R.string.history_clear_message_host, activeHostName),
            confirmLabel = stringResource(R.string.history_clear),
            onConfirm = {
                showClearConfirm = false
                selectionMode = false
                selectedIds = emptySet()
                viewModel.clearHistory()
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    if (showBatchDeleteConfirm) {
        NekoConfirmDialog(
            title = stringResource(
                R.string.history_delete_selected_title,
                selectedIds.size,
            ),
            message = stringResource(R.string.history_delete_message),
            confirmLabel = stringResource(R.string.history_delete),
            onConfirm = {
                val ids = selectedIds
                showBatchDeleteConfirm = false
                selectionMode = false
                selectedIds = emptySet()
                viewModel.deleteHistory(ids)
            },
            onDismiss = { showBatchDeleteConfirm = false },
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun HistoryEmptyPreview() {
    NekoPaintTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyHistory(loading = false)
        }
    }
}
