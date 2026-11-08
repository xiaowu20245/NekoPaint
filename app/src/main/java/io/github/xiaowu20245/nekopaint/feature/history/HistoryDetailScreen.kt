package io.github.xiaowu20245.nekopaint.feature.history

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import coil3.compose.SubcomposeAsyncImage
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun HistoryDetailScreen(
    promptId: String,
    onBack: () -> Unit,
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

    val entry = state.entries.firstOrNull { it.promptId == promptId }
    val task = state.tasks.firstOrNull { it.promptId == promptId }
    val images = entry?.visibleImages(state.showTempPreviews).orEmpty()

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
                savingImageUrl = state.savingImageUrl,
                sharingImageUrl = state.sharingImageUrl,
                onResubmit = { task?.let(viewModel::resubmit) },
                onDelete = { showDeleteConfirm = true },
                onOpenImage = { viewerIndex = it },
                onSaveImage = requestSaveImage,
                onShareImage = viewModel::shareImage,
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
    savingImageUrl: String?,
    sharingImageUrl: String?,
    onResubmit: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onSaveImage: (HistoryImage) -> Unit,
    onShareImage: (HistoryImage) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task?.templateName ?: shortId(entry.promptId),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(status = entry.status)
                    }
                    Text(
                        text = entry.promptId,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.timestamp?.let { timestamp ->
                        Text(
                            text = stringResource(
                                R.string.history_time,
                                formatTime(timestamp),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    durationText(
                        start = entry.startedAt ?: task?.startedAt,
                        end = entry.finishedAt ?: entry.timestamp ?: task?.finishedAt,
                    )?.let { duration ->
                        Text(
                            text = stringResource(R.string.history_duration, duration),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = onResubmit,
                        enabled = !resubmitting && !task?.values.isNullOrEmpty(),
                    ) {
                        Text(
                            stringResource(
                                if (resubmitting) {
                                    R.string.history_resubmitting
                                } else {
                                    R.string.history_resubmit
                                },
                            ),
                        )
                    }
                    if (task?.values.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.history_resubmit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !deleting,
                    ) {
                        if (deleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(stringResource(R.string.history_delete))
                    }
                }
            }
        }

        entry.errorMessage?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.history_detail_error),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        entry.errorNodeId?.let { nodeId ->
                            Text(
                                text = stringResource(
                                    R.string.history_detail_error_node,
                                    nodeId,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.history_detail_outputs),
                        style = MaterialTheme.typography.titleMedium,
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowImages.forEachIndexed { columnIndex, image ->
                                    val imageIndex = rowIndex * 2 + columnIndex
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = image.url,
                                            contentDescription = stringResource(
                                                R.string.image_open_desc,
                                                image.filename,
                                            ),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onOpenImage(imageIndex)
                                                },
                                            loading = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(22.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                }
                                            },
                                            error = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                            },
                                        )
                                        Text(
                                            text = image.filename,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            TextButton(
                                                onClick = { onSaveImage(image) },
                                                enabled = savingImageUrl == null &&
                                                    sharingImageUrl == null,
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

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.history_detail_params),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val values = task?.values.orEmpty()
                    if (values.isEmpty()) {
                        Text(
                            text = stringResource(R.string.history_detail_no_params),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        values.forEach { (key, value) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // 给系统手势条留白，避免最后一张卡片贴边。
            Box(modifier = Modifier.height(12.dp))
        }
    }
}
