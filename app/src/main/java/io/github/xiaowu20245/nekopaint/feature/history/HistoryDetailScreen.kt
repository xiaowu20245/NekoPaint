package io.github.xiaowu20245.nekopaint.feature.history

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun HistoryDetailScreen(
    promptId: String,
    onBack: () -> Unit,
    onOpenWorkflow: (String) -> Unit = {},
    onAskNekoI: (String) -> Unit = {},
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
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSaveImage by remember { mutableStateOf<HistoryImage?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val permissionDeniedMessage = stringResource(R.string.image_save_permission_denied)
    val openSettingsLabel = stringResource(R.string.image_save_open_settings)
    val refillFailedTemplate = stringResource(R.string.history_refill_failed)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val image = pendingSaveImage
        pendingSaveImage = null
        if (granted && image != null) {
            viewModel.saveImage(image)
        } else if (!granted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = permissionDeniedMessage,
                    actionLabel = openSettingsLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        },
                    )
                }
            }
        }
    }

    val requestSaveImage: (HistoryImage) -> Unit = { image ->
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingSaveImage = image
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.saveImage(image)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(250)
            viewModel.refresh()
            // 页面可见期间保持刷新，直到找到记录或用户离开。
            while (isActive) {
                kotlinx.coroutines.delay(10_000)
                viewModel.refresh()
            }
        }
    }

    val noticeText = state.notice?.let { notice ->
        when (notice) {
            is HistoryNotice.Success -> stringResource(
                R.string.history_resubmit_success,
                notice.promptId.take(8),
            )

            HistoryNotice.NoServer -> stringResource(R.string.workflow_submit_no_server)
            is HistoryNotice.Error -> stringResource(
                R.string.workflow_submit_failed,
                notice.message,
            )
        }
    }

    LaunchedEffect(noticeText) {
        noticeText?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    val saveNotice = state.saveNotice
    val saveSuccessMessage = (saveNotice as? SaveNotice.Success)?.let { notice ->
        stringResource(R.string.image_saved, notice.displayName)
    }
    val saveFailureMessage = (saveNotice as? SaveNotice.Failure)?.let { notice ->
        stringResource(R.string.image_save_failed, notice.message)
    }
    val savedViewLabel = stringResource(R.string.image_saved_view)
    val shareNotice = state.shareNotice
    val shareFailureMessage = (shareNotice as? ShareNotice.Failure)?.let { notice ->
        stringResource(R.string.image_share_failed, notice.message)
    }
    val shareChooserTitle = stringResource(R.string.image_share_chooser)

    LaunchedEffect(saveNotice) {
        when (saveNotice) {
            is SaveNotice.Success -> {
                if (saveSuccessMessage != null) {
                    val result = snackbarHostState.showSnackbar(
                        message = saveSuccessMessage,
                        actionLabel = savedViewLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(saveNotice.uri, "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
                viewModel.consumeSaveNotice()
            }

            is SaveNotice.Failure -> {
                if (saveFailureMessage != null) {
                    snackbarHostState.showSnackbar(saveFailureMessage)
                }
                viewModel.consumeSaveNotice()
            }

            null -> Unit
        }
    }

    LaunchedEffect(shareNotice) {
        when (shareNotice) {
            is ShareNotice.Success -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = shareNotice.mimeType
                    putExtra(Intent.EXTRA_STREAM, shareNotice.uri)
                    clipData = ClipData.newRawUri("image", shareNotice.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, shareChooserTitle).apply {
                    // Chooser 预览也会读取 URI，必须把授权标志带到外层 Intent。
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(chooser) }
                viewModel.consumeShareNotice()
            }

            is ShareNotice.Failure -> {
                if (shareFailureMessage != null) {
                    snackbarHostState.showSnackbar(shareFailureMessage)
                }
                viewModel.consumeShareNotice()
            }

            null -> Unit
        }
    }

    val editErrorMessage = (state.editNotice as? HistoryEditNotice.Error)?.let { notice ->
        stringResource(R.string.history_edit_failed, notice.message)
    }
    LaunchedEffect(state.editNotice) {
        when (val notice = state.editNotice) {
            HistoryEditNotice.Deleted -> {
                viewModel.consumeEditNotice()
                onBack()
            }

            is HistoryEditNotice.Error -> {
                snackbarHostState.showSnackbar(editErrorMessage.orEmpty())
                viewModel.consumeEditNotice()
            }

            HistoryEditNotice.Cleared, null -> Unit
        }
    }

    val activeServerIds = state.hosts.firstOrNull { it.active }?.serverIds.orEmpty()
    val entry = state.entries.firstOrNull {
        it.promptId == promptId && it.serverId in activeServerIds
    } ?: state.entries.firstOrNull { it.promptId == promptId }
    val task = state.tasks.firstOrNull {
        it.promptId == promptId && it.serverId == entry?.serverId
    } ?: state.tasks.firstOrNull { it.promptId == promptId }
    val images = entry?.visibleImages(state.showTempPreviews).orEmpty()
    val isReadOnly = entry != null && entry.serverId !in activeServerIds
    val hostName = entry?.let { current ->
        state.hosts.firstOrNull { current.serverId in it.serverIds }?.name
            ?: current.serverId.take(8)
    }.orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        if (entry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.refreshing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.history_detail_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.history_detail_missing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.history_detail_retry))
                        }
                    }
                }
            }
        } else {
            HistoryDetailContent(
                entry = entry,
                task = task,
                images = images,
                resubmitting = state.resubmitting,
                deleting = promptId in state.deletingPromptIds,
                readOnly = isReadOnly,
                readOnlyHostName = hostName,
                savingImageUrl = state.savingImageUrl,
                sharingImageUrl = state.sharingImageUrl,
                onResubmit = { task?.let(viewModel::resubmit) },
                onRefillWorkflow = {
                    task?.let { t ->
                        viewModel.refillWorkflow(t) { result ->
                            result.fold(
                                onSuccess = { templateId -> onOpenWorkflow(templateId) },
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
                },
                onDelete = { showDeleteConfirm = true },
                onOpenImage = { viewerIndex = it },
                onSaveImage = requestSaveImage,
                onShareImage = viewModel::shareImage,
                onAskNekoI = { onAskNekoI(promptId) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    viewerIndex?.let { index ->
        ImagePreviewDialog(
            images = images,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteHistory(promptId)
                    },
                ) {
                    Text(stringResource(R.string.history_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryDetailContent(
    entry: HistoryEntry,
    task: TaskRecord?,
    images: List<HistoryImage>,
    resubmitting: Boolean,
    deleting: Boolean,
    readOnly: Boolean,
    readOnlyHostName: String,
    savingImageUrl: String?,
    sharingImageUrl: String?,
    onResubmit: () -> Unit,
    onRefillWorkflow: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onSaveImage: (HistoryImage) -> Unit,
    onShareImage: (HistoryImage) -> Unit,
    onAskNekoI: () -> Unit,
) {
    val metadata = HistoryMetadataExtractor.extract(task)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (readOnly) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(
                            R.string.history_host_readonly_detail,
                            readOnlyHostName,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item {
            DetailHeaderCard(
                entry = entry,
                task = task,
                resubmitting = resubmitting,
                deleting = deleting,
                readOnly = readOnly,
                onResubmit = onResubmit,
                onRefillWorkflow = onRefillWorkflow,
                onDelete = onDelete,
                onAskNekoI = onAskNekoI,
            )
        }

        entry.errorMessage?.let { message ->
            item {
                DetailErrorCard(
                    message = message,
                    errorNodeId = entry.errorNodeId,
                )
            }
        }

        item {
            DetailOutputsCard(
                entry = entry,
                images = images,
                savingImageUrl = savingImageUrl,
                sharingImageUrl = sharingImageUrl,
                onOpenImage = onOpenImage,
                onSaveImage = onSaveImage,
                onShareImage = onShareImage,
            )
        }

        item {
            DetailParamsCard(task = task, metadata = metadata)
        }

        item {
            // 给系统手势条留白，避免最后一张卡片贴边。
            Box(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailHeaderCard(
    entry: HistoryEntry,
    task: TaskRecord?,
    resubmitting: Boolean,
    deleting: Boolean,
    readOnly: Boolean,
    onResubmit: () -> Unit,
    onRefillWorkflow: () -> Unit,
    onDelete: () -> Unit,
    onAskNekoI: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = task?.templateName ?: shortId(entry.promptId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        entry.timestamp?.let { timestamp ->
                            Text(
                                text = stringResource(R.string.history_time, formatTime(timestamp)),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onResubmit,
                    enabled = !readOnly && !resubmitting && !task?.values.isNullOrEmpty(),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (resubmitting) {
                                R.string.history_resubmitting
                            } else {
                                R.string.history_resubmit
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }

                Button(
                    onClick = onRefillWorkflow,
                    enabled = !readOnly && task?.values?.isNotEmpty() == true,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp),
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
                        maxLines = 1,
                    )
                }
            }

            OutlinedButton(
                onClick = onAskNekoI,
                enabled = !readOnly,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
            ) {
                Icon(
                    imageVector = NekoIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.history_ask_neko_i),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            if (task?.values.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.history_resubmit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_id_label, shortId(entry.promptId)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDelete,
                    enabled = !readOnly && !deleting,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = stringResource(R.string.history_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (deleting) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailErrorCard(message: String, errorNodeId: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.history_detail_error),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            errorNodeId?.let { nodeId ->
                Text(
                    text = stringResource(R.string.history_detail_error_node, nodeId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailOutputsCard(
    entry: HistoryEntry,
    images: List<HistoryImage>,
    savingImageUrl: String?,
    sharingImageUrl: String?,
    onOpenImage: (Int) -> Unit,
    onSaveImage: (HistoryImage) -> Unit,
    onShareImage: (HistoryImage) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.history_detail_outputs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (images.size < entry.images.size) {
                Text(
                    text = stringResource(
                        R.string.history_detail_hidden_temp,
                        entry.images.size - images.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (images.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_no_images),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                images.chunked(2).forEachIndexed { rowIndex, rowImages ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowImages.forEachIndexed { columnIndex, image ->
                            val imageIndex = rowIndex * 2 + columnIndex
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { onOpenImage(imageIndex) },
                                ) {
                                    HistorySubcomposeImage(
                                        url = image.url,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    DetailImageTag(
                                        type = image.type,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp),
                                    )
                                }
                                Text(
                                    text = image.filename,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(
                                        onClick = { onSaveImage(image) },
                                        enabled = savingImageUrl == null &&
                                            sharingImageUrl == null,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (savingImageUrl == image.url) {
                                                    R.string.image_saving
                                                } else {
                                                    R.string.image_save
                                                },
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    TextButton(
                                        onClick = { onShareImage(image) },
                                        enabled = sharingImageUrl == null &&
                                            savingImageUrl == null,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (sharingImageUrl == image.url) {
                                                    R.string.image_sharing
                                                } else {
                                                    R.string.image_share
                                                },
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                        if (rowImages.size == 1) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailImageTag(type: String?, modifier: Modifier = Modifier) {
    val isOutput = type == "output"
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isOutput) {
            Color.Black.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        },
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                if (isOutput) R.string.history_tag_output else R.string.history_tag_temp,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isOutput) Color.White else MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DetailParamsCard(task: TaskRecord?, metadata: ExtractedTaskMetadata) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.history_detail_params),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            val values = task?.values.orEmpty()
            if (values.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_detail_no_params),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val hasMetadata = listOfNotNull(
                    metadata.seed,
                    metadata.steps,
                    metadata.samplerText,
                    metadata.model,
                    metadata.sizeText,
                ).isNotEmpty()
                if (hasMetadata) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        metadata.seed?.let {
                            DetailParamPill(stringResource(R.string.history_sheet_seed), it)
                        }
                        metadata.steps?.let {
                            DetailParamPill(
                                stringResource(R.string.history_sheet_steps),
                                stringResource(R.string.history_steps_value, it),
                            )
                        }
                        metadata.samplerText?.let {
                            DetailParamPill(stringResource(R.string.history_sheet_sampler), it)
                        }
                        metadata.model?.let {
                            DetailParamPill(stringResource(R.string.history_sheet_model), it)
                        }
                        metadata.sizeText?.let {
                            DetailParamPill(stringResource(R.string.history_sheet_size), it)
                        }
                    }
                }
                // 与三视图一致的纯白参数面板
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        values.forEach { (key, value) ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailParamPill(label: String, value: String) {
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
