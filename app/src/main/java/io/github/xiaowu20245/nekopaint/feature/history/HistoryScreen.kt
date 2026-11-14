package io.github.xiaowu20245.nekopaint.feature.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarIconAction
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarMenuItem
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
            notice.message,
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

    LaunchedEffect(state.entries, state.activeServerId) {
        val selectable = state.entries
            .filter { isOperable(it.serverId) }
            .map { it.promptId }
            .toSet()
        selectedIds = selectedIds.intersect(selectable)
        if (selectionMode && selectable.isEmpty()) {
            selectionMode = false
        }
    }

    LaunchedEffect(state.activeServerId, state.selectedHostId) {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
    val activeHostEntries = remember(state.entries, activeServerIds) {
        state.entries.filter { isOperable(it.serverId) }
    }
    val selectableEntries = remember(hostFilteredEntries, activeServerIds) {
        hostFilteredEntries.filter { isOperable(it.serverId) }
    }
    val tasksById = remember(state.tasks) {
        state.tasks.associateBy { it.promptId }
    }
    val openLightbox: (HistoryEntry, Int) -> Unit = { entry, index ->
        entry.visibleImages(state.showTempPreviews)
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
            selectedIds = if (entry.promptId in selectedIds) {
                selectedIds - entry.promptId
            } else {
                selectedIds + entry.promptId
            }
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
    val allSelected = selectableEntries.isNotEmpty() &&
        selectableEntries.all { it.promptId in selectedIds }
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
        state.showTempPreviews,
        canClearHistory,
        state.refreshing,
        state.viewMode,
        viewSwitchDescription,
    ) {
        val actions = buildList {
            if (selectionMode) {
                // 多选操作全部收纳到顶栏右上角，底部不再有任何浮层。
                add(
                    NekoTopBarIconAction(
                        icon = if (allSelected) NekoIcons.Deselect else NekoIcons.SelectAll,
                        contentDescription = selectAllDescription,
                        onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                selectableEntries.map { it.promptId }.toSet()
                            }
                        },
                    ),
                )
                add(
                    NekoTopBarIconAction(
                        icon = NekoIcons.Download,
                        contentDescription = exportDescription,
                        enabled = selectedIds.isNotEmpty() && !state.exporting,
                        onClick = {
                            val selectedImages = filteredEntries
                                .filter { it.promptId in selectedIds }
                                .flatMap { it.visibleImages(state.showTempPreviews) }
                            viewModel.exportImages(selectedImages) { success, _ ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        String.format(exportResultTemplate, success),
                                    )
                                }
                            }
                        },
                    ),
                )
                add(
                    NekoTopBarIconAction(
                        icon = Icons.Outlined.Delete,
                        contentDescription = deleteDescription,
                        tint = deleteTint,
                        enabled = selectedIds.isNotEmpty() &&
                            state.deletingPromptIds.isEmpty(),
                        onClick = { showBatchDeleteConfirm = true },
                    ),
                )
                add(
                    NekoTopBarIconAction(
                        label = doneLabel,
                        contentDescription = doneDescription,
                        onClick = {
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
                onHostChange = viewModel::setHostFilter,
                onFilterChange = viewModel::setFilter,
            )

            val errorMessage = state.error
            val statusNotice = when {
                errorMessage != null -> stringResource(R.string.history_error, errorMessage)
                state.connection != ComfyConnectionState.Connected ->
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

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyHistory(
                        loading = state.refreshing,
                        onLoadDemo = viewModel::loadDemoHistory,
                    )
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
                                showTempPreviews = state.showTempPreviews,
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
                                showTempPreviews = state.showTempPreviews,
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
                                showTempPreviews = state.showTempPreviews,
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
        val images = entry.visibleImages(state.showTempPreviews)
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
        AlertDialog(
            onDismissRequest = { pendingDeletePromptId = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletePromptId = null
                        viewModel.deleteHistory(promptId)
                    },
                ) {
                    Text(stringResource(R.string.history_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePromptId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = {
                Text(stringResource(R.string.history_clear_message_host, activeHostName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                        viewModel.clearHistory()
                    },
                ) {
                    Text(stringResource(R.string.history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(
                    stringResource(
                        R.string.history_delete_selected_title,
                        selectedIds.size,
                    ),
                )
            },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds
                        showBatchDeleteConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                        viewModel.deleteHistory(ids)
                    },
                ) {
                    Text(stringResource(R.string.history_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistorySubHeader(
    filter: HistoryFilter,
    totalCount: Int,
    selectedCount: Int?,
    hosts: List<HistoryHost>,
    selectedHostId: String?,
    onHostChange: (String?) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
) {
    var hostMenuExpanded by remember { mutableStateOf(false) }
    val selectedHost = hosts.firstOrNull { it.id == selectedHostId }
    val hostButtonLabel = selectedHost?.let { host ->
        if (host.aliasCount > 1) {
            "${host.name} +${host.aliasCount - 1}"
        } else {
            host.name
        }
    } ?: stringResource(R.string.history_host_all)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FilterChip(
                selected = selectedHost != null,
                onClick = { hostMenuExpanded = true },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = hostButtonLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = NekoIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(90f),
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = NekoIcons.ServerRack,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedHost != null,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 0.dp,
                ),
                modifier = Modifier.widthIn(max = 176.dp),
            )

            DropdownMenu(
                expanded = hostMenuExpanded,
                onDismissRequest = { hostMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_host_all)) },
                    onClick = {
                        hostMenuExpanded = false
                        onHostChange(null)
                    },
                )
                hosts.forEach { host ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = host.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (host.active) FontWeight.SemiBold
                                        else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (host.active) {
                                        Text(
                                            text = stringResource(R.string.history_host_current),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (host.aliasCount > 1) {
                                    Text(
                                        text = host.aliasNames.joinToString(" / ") +
                                            " · " + stringResource(R.string.history_host_same_address),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            hostMenuExpanded = false
                            onHostChange(host.id)
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedCount != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        text = stringResource(R.string.history_selected_count, selectedCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            HistoryFilterChip(
                label = stringResource(R.string.history_filter_all_count, totalCount),
                selected = filter == HistoryFilter.ALL,
                onClick = { onFilterChange(HistoryFilter.ALL) },
            )
            HistoryFilterChip(
                label = stringResource(R.string.history_filter_output_label),
                selected = filter == HistoryFilter.OUTPUTS,
                onClick = { onFilterChange(HistoryFilter.OUTPUTS) },
            )
            HistoryFilterChip(
                label = stringResource(R.string.history_filter_temp_label),
                selected = filter == HistoryFilter.TEMP,
                onClick = { onFilterChange(HistoryFilter.TEMP) },
            )
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
            selectedBorderWidth = 0.dp,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryImageActionSheet(
    onDismiss: () -> Unit,
    canDelete: Boolean = true,
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit,
    onCopyPrompt: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.history_image_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HistoryActionRow(
                icon = NekoIcons.Download,
                label = stringResource(R.string.history_action_save),
                onClick = onSaveImage,
            )
            HistoryActionRow(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.history_action_share),
                onClick = onShareImage,
            )
            HistoryActionRow(
                icon = NekoIcons.ContentCopy,
                label = stringResource(R.string.history_action_copy_prompt),
                onClick = onCopyPrompt,
            )
            if (canDelete) {
                HistoryActionRow(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.history_action_delete),
                    danger = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HistoryActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryLightboxDialog(
    image: HistoryImage,
    onDismiss: () -> Unit,
    onLongPress: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = image.url,
                contentDescription = image.filename,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                    ),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = NekoIcons.BrokenImage,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = stringResource(R.string.history_image_expired),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.image_viewer_close),
                    tint = Color.White,
                )
            }
        }
    }
}

// ---------------- VIEW A: TIMELINE BATCH VIEW ----------------

@Composable
private fun TimelineBatchView(
    entries: List<HistoryEntry>,
    tasksById: Map<String, TaskRecord>,
    hostNames: Map<String, String>,
    activeServerIds: Set<String>,
    showTempPreviews: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (HistoryEntry) -> Unit,
    onLongClick: (HistoryEntry) -> Unit,
    onOpenSheet: (HistoryEntry, Int) -> Unit,
    onOpenLightbox: (HistoryEntry, Int) -> Unit,
    onOpenDetail: (HistoryEntry) -> Unit,
    onRefillWorkflow: (TaskRecord) -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val todayLabel = stringResource(R.string.history_group_today)
    val yesterdayLabel = stringResource(R.string.history_group_yesterday)
    val earlierLabel = stringResource(R.string.history_group_earlier)
    // Group entries by date (今天 / 昨天 / 日期)
    val grouped = remember(entries, todayLabel, yesterdayLabel, earlierLabel) {
        entries.groupBy {
            formatDateGroup(it.timestamp, todayLabel, yesterdayLabel, earlierLabel)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        grouped.forEach { (dateGroup, groupEntries) ->
            item(key = "header_$dateGroup") {
                Text(
                    text = dateGroup,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }

            items(groupEntries, key = { "${it.serverId}_${it.promptId}" }) { entry ->
                val task = tasksById[entry.promptId]
                val selected = entry.promptId in selectedIds
                val images = entry.visibleImages(showTempPreviews)
                val metadata = HistoryMetadataExtractor.extract(task)
                val isActiveHost = entry.serverId in activeServerIds

                TimelineBatchCard(
                    entry = entry,
                    task = task,
                    images = images,
                    metadata = metadata,
                    hostName = hostNames[entry.serverId] ?: entry.serverId.take(8),
                    isActiveHost = isActiveHost,
                    selected = selected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelect(entry)
                        } else {
                            onOpenSheet(entry, 0)
                        }
                    },
                    onLongClick = { onLongClick(entry) },
                    onImageClick = { index ->
                        if (selectionMode) {
                            onToggleSelect(entry)
                        } else {
                            onOpenLightbox(entry, index)
                        }
                    },
                    onRefillWorkflow = {
                        if (task != null) onRefillWorkflow(task)
                    },
                )
            }
        }

        if (hasMore || loadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !loadingMore,
                        shape = RoundedCornerShape(50),
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (loadingMore) R.string.history_loading_more else R.string.history_load_more,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineBatchCard(
    entry: HistoryEntry,
    task: TaskRecord?,
    images: List<HistoryImage>,
    metadata: ExtractedTaskMetadata,
    hostName: String,
    isActiveHost: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onImageClick: (Int) -> Unit,
    onRefillWorkflow: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Row: Title + Time/Duration + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task?.templateName ?: shortId(entry.promptId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HostBadge(
                            hostName = hostName,
                            active = isActiveHost,
                            modifier = Modifier.widthIn(max = 136.dp),
                        )
                        entry.timestamp?.let { ts ->
                            Text(
                                text = formatTimeShort(ts),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        durationText(
                            start = entry.startedAt ?: task?.startedAt,
                            end = entry.finishedAt ?: entry.timestamp ?: task?.finishedAt,
                        )?.let { duration ->
                            Text(
                                text = stringResource(R.string.history_duration_compact, duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                StatusBadge(status = entry.status)
            }

            // Horizontal Images Row
            if (images.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(images.size) { index ->
                        val image = images[index]
                        Box(
                            modifier = Modifier
                                .size(118.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            HistorySubcomposeImage(
                                url = image.url,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        onClick = { onImageClick(index) },
                                    ),
                            )

                            // Type tag: "成图" / "草稿"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (image.type == "output") Color.Black.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp),
                            ) {
                                Text(
                                    text = if (image.type == "output") stringResource(R.string.history_tag_output)
                                    else stringResource(R.string.history_tag_temp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (image.type == "output") Color.White else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Pure White Parameter Box（没有提示词和参数时不渲染空面板）
            val promptText = metadata.prompt ?: HistoryMetadataExtractor.extractPrompt(task, null)
            val hasParamContent = !promptText.isNullOrBlank() ||
                metadata.seed != null ||
                metadata.steps != null ||
                metadata.samplerText != null ||
                metadata.model != null ||
                metadata.sizeText != null
            if (hasParamContent) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Prompt excerpt
                    if (!promptText.isNullOrBlank()) {
                        Text(
                            text = promptText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Parameter tags row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        metadata.seed?.let {
                            ParamPill(stringResource(R.string.history_sheet_seed), it)
                        }
                        metadata.steps?.let {
                            ParamPill(
                                stringResource(R.string.history_sheet_steps),
                                stringResource(R.string.history_steps_value, it),
                            )
                        }
                        metadata.samplerText?.let {
                            ParamPill(stringResource(R.string.history_sheet_sampler), it)
                        }
                        metadata.model?.let {
                            ParamPill(stringResource(R.string.history_sheet_model), it)
                        }
                        metadata.sizeText?.let {
                            ParamPill(stringResource(R.string.history_sheet_size), it)
                        }
                    }
                }
            }
            }

            // Bottom Actions Row: Refill Workflow Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))

                // Prominent refill button: [回填工作流]
                FilledTonalButton(
                    onClick = onRefillWorkflow,
                    enabled = isActiveHost && task?.values?.isNotEmpty() == true,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(
                        imageVector = NekoIcons.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.history_refill_workflow),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParamPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------- VIEW B: MEDIA-FIRST GRID ----------------

@Composable
private fun GalleryGridView(
    entries: List<HistoryEntry>,
    tasksById: Map<String, TaskRecord>,
    hostNames: Map<String, String>,
    activeServerIds: Set<String>,
    showTempPreviews: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (HistoryEntry) -> Unit,
    onLongClick: (HistoryEntry) -> Unit,
    onOpenSheet: (HistoryEntry, Int) -> Unit,
    onOpenLightbox: (HistoryEntry, Int) -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { "${it.serverId}_${it.promptId}" }) { entry ->
            val task = tasksById[entry.promptId]
            val selected = entry.promptId in selectedIds
            val images = entry.visibleImages(showTempPreviews)
            val firstImage = images.firstOrNull()
            val metadata = HistoryMetadataExtractor.extract(task)

            GalleryGridCard(
                entry = entry,
                task = task,
                image = firstImage,
                metadata = metadata,
                hostName = hostNames[entry.serverId] ?: entry.serverId.take(8),
                isActiveHost = entry.serverId in activeServerIds,
                selected = selected,
                selectionMode = selectionMode,
                onClick = {
                    if (selectionMode) {
                        onToggleSelect(entry)
                    } else {
                        onOpenSheet(entry, 0)
                    }
                },
                onLongClick = { onLongClick(entry) },
                onImageClick = {
                    if (selectionMode) {
                        onToggleSelect(entry)
                    } else {
                        onOpenLightbox(entry, 0)
                    }
                },
            )
        }

        if (hasMore || loadingMore) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !loadingMore,
                        shape = RoundedCornerShape(50),
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (loadingMore) R.string.history_loading_more else R.string.history_load_more,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryGridCard(
    entry: HistoryEntry,
    task: TaskRecord?,
    image: HistoryImage?,
    metadata: ExtractedTaskMetadata,
    hostName: String,
    isActiveHost: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onImageClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            // Large Top Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onImageClick),
            ) {
                if (image?.url != null) {
                    HistorySubcomposeImage(
                        url = image.url,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.history_no_images),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Top Left Host Badge
                HostBadge(
                    hostName = hostName,
                    active = isActiveHost,
                    overlay = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .widthIn(max = 124.dp),
                )

                // Top Right Selection Checkbox
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = Color.White,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }

                // Bottom Right Tag: "成图" / "草稿"
                if (image != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (image.type == "output") Color.Black.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = if (image.type == "output") stringResource(R.string.history_tag_output)
                            else stringResource(R.string.history_tag_temp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (image.type == "output") Color.White else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Card Bottom Details
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = task?.templateName ?: shortId(entry.promptId),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val prompt = metadata.prompt ?: HistoryMetadataExtractor.extractPrompt(task, null)
                if (!prompt.isNullOrBlank()) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                entry.timestamp?.let { ts ->
                    Text(
                        text = formatDateTime(ts),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ---------------- VIEW C: COMPACT 3-COLUMN ALBUM ----------------

@Composable
private fun CompactAlbumView(
    entries: List<HistoryEntry>,
    hostNames: Map<String, String>,
    activeServerIds: Set<String>,
    showTempPreviews: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (HistoryEntry) -> Unit,
    onOpenLightbox: (HistoryEntry, Int) -> Unit,
    onLongClick: (HistoryEntry) -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    // Flatten entries to individual images for high-density album
    val flatImages = remember(entries, showTempPreviews) {
        entries.flatMap { entry ->
            entry.visibleImages(showTempPreviews).mapIndexed { idx, img ->
                Triple(entry, img, idx)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(flatImages, key = { "${it.first.serverId}_${it.first.promptId}_${it.second.filename}" }) { (entry, image, index) ->
            val selected = entry.promptId in selectedIds

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onToggleSelect(entry)
                            } else {
                                onOpenLightbox(entry, index)
                            }
                        },
                        onLongClick = { onLongClick(entry) },
                    ),
            ) {
                HistorySubcomposeImage(
                    url = image.url,
                    modifier = Modifier.fillMaxSize(),
                )

                HostBadge(
                    hostName = hostNames[entry.serverId] ?: entry.serverId.take(8),
                    active = entry.serverId in activeServerIds,
                    overlay = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .widthIn(max = 94.dp),
                )

                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect(entry) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = Color.White,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp),
                    )
                }
            }
        }

        if (hasMore || loadingMore) {
            item(span = { GridItemSpan(3) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !loadingMore,
                        shape = RoundedCornerShape(50),
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (loadingMore) R.string.history_loading_more else R.string.history_load_more,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ---------------- REUSABLE SUBCOMPOSE IMAGE WITH FAILURE DEGRADATION ----------------

@Composable
fun HistorySubcomposeImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NekoIcons.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
        return
    }

    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        },
        error = {
            // Polished graceful fallback: light gray container, broken image icon, and failure hint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(6.dp),
                ) {
                    Icon(
                        imageVector = NekoIcons.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = stringResource(R.string.history_image_expired),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

// ---------------- MODAL BOTTOM SHEET: PARAMETER DRAWER ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryDetailDrawerContent(
    entry: HistoryEntry,
    task: TaskRecord?,
    image: HistoryImage?,
    hostName: String,
    isActiveHost: Boolean,
    onSaveImage: (HistoryImage) -> Unit,
    onImageClick: (HistoryImage) -> Unit,
    onImageLongPress: (HistoryImage) -> Unit,
    onRefillWorkflow: (TaskRecord) -> Unit,
    onOpenFullDetail: () -> Unit,
    onAskNekoI: () -> Unit,
    onCopyPrompt: (String) -> Unit,
) {
    val metadata = HistoryMetadataExtractor.extract(task)
    val promptText = metadata.prompt ?: HistoryMetadataExtractor.extractPrompt(task, null).orEmpty()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Title Row: Drawer Title + Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.history_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HostBadge(
                        hostName = hostName,
                        active = isActiveHost,
                        modifier = Modifier.widthIn(max = 136.dp),
                    )
                    Text(
                        text = task?.templateName ?: shortId(entry.promptId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(
                onClick = onOpenFullDetail,
                enabled = isActiveHost,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_open_detail),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            StatusBadge(status = entry.status)
        }

        if (!isActiveHost) {
            Text(
                text = stringResource(R.string.history_host_readonly_hint, hostName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        // Image Preview Box
        if (image != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clipToBounds()
                    .transformable(transformableState)
                    .combinedClickable(
                        onClick = { onImageClick(image) },
                        onLongClick = { onImageLongPress(image) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HistorySubcomposeImage(
                    url = image.url,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }
        }

        // Prompt Card (Pure White)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_sheet_prompt),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (promptText.isNotBlank()) {
                        TextButton(
                            onClick = { onCopyPrompt(promptText) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = NekoIcons.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.history_copy_prompt),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Text(
                    text = if (promptText.isNotBlank()) promptText
                    else stringResource(R.string.history_no_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Sampling Parameters Card (Pure White)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_sheet_params),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        ParamDetailRow(
                            label = stringResource(R.string.history_sheet_seed),
                            value = metadata.seed ?: "-",
                        )
                        ParamDetailRow(
                            label = stringResource(R.string.history_sheet_steps),
                            value = metadata.steps?.let { "$it 步" } ?: "-",
                        )
                        ParamDetailRow(
                            label = stringResource(R.string.history_sheet_size),
                            value = metadata.sizeText ?: "-",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ParamDetailRow(
                            label = stringResource(R.string.history_sheet_sampler),
                            value = metadata.samplerText ?: "-",
                        )
                        ParamDetailRow(
                            label = stringResource(R.string.history_sheet_cfg),
                            value = metadata.cfg ?: "-",
                        )
                        ParamDetailRow(
                            label = stringResource(R.string.history_sheet_model),
                            value = metadata.model ?: "-",
                        )
                    }
                }
            }
        }

        // 从抽屉直接带着任务上下文进入 Neko I
        OutlinedButton(
            onClick = onAskNekoI,
            enabled = isActiveHost,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Icon(
                imageVector = NekoIcons.Sparkles,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.history_ask_neko_i),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Fixed Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (image != null) {
                OutlinedButton(
                    onClick = { onSaveImage(image) },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = NekoIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.history_sheet_save),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }

            Button(
                onClick = { task?.let(onRefillWorkflow) },
                enabled = isActiveHost && task?.values?.isNotEmpty() == true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1.3f),
            ) {
                Icon(
                    imageVector = NekoIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.history_refill_workflow),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ParamDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyHistory(
    loading: Boolean,
    onLoadDemo: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.history_detail_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.history_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = onLoadDemo,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.history_demo_records))
                }
            }
        }
    }
}

@Composable
private fun HostBadge(
    hostName: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
) {
    val containerColor = when {
        overlay -> Color.Black.copy(alpha = 0.62f)
        active -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        overlay -> Color.White
        active -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (active) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (overlay) Color.White else MaterialTheme.colorScheme.primary,
                        ),
                )
            }
            Text(
                text = hostName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "success" -> stringResource(R.string.history_status_success) to MaterialTheme.colorScheme.primary
        "failed" -> stringResource(R.string.history_status_failed) to MaterialTheme.colorScheme.error
        "interrupted" -> stringResource(R.string.history_status_interrupted) to MaterialTheme.colorScheme.tertiary
        else -> stringResource(R.string.history_status_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun durationText(start: Long?, end: Long?): String? {
    if (start == null || end == null || end < start) return null
    val totalSeconds = ((end - start) / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.history_duration_hours, hours, minutes)
        minutes > 0 -> stringResource(R.string.history_duration_minutes, minutes, seconds)
        else -> stringResource(R.string.history_duration_seconds, seconds)
    }
}

fun HistoryEntry.visibleImages(showTempPreviews: Boolean): List<HistoryImage> =
    if (showTempPreviews) images else images.filter { it.type == "output" }

fun shortId(promptId: String): String =
    if (promptId.length <= 8) promptId else promptId.take(8)

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

fun formatTimeShort(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDateGroup(
    timestamp: Long?,
    todayLabel: String,
    yesterdayLabel: String,
    earlierLabel: String,
): String {
    if (timestamp == null || timestamp <= 0) return earlierLabel
    val cal = Calendar.getInstance()
    val todayYear = cal.get(Calendar.YEAR)
    val todayDay = cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = timestamp
    val targetYear = cal.get(Calendar.YEAR)
    val targetDay = cal.get(Calendar.DAY_OF_YEAR)

    return when {
        todayYear == targetYear && todayDay == targetDay -> todayLabel
        todayYear == targetYear && todayDay - targetDay == 1 -> yesterdayLabel
        todayYear == targetYear -> SimpleDateFormat("MM-dd", Locale.getDefault())
            .format(Date(timestamp))
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
