package io.github.xiaowu20245.nekopaint.feature.queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.data.repository.TaskStatus
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoEmptyState
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoIconButton
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusBadge
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusMapper
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusTone
import io.github.xiaowu20245.nekopaint.core.ui.component.icon
import io.github.xiaowu20245.nekopaint.core.ui.component.label
import io.github.xiaowu20245.nekopaint.core.ui.component.tone
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
import io.github.xiaowu20245.nekopaint.feature.common.asString
import io.github.xiaowu20245.nekopaint.feature.history.HistoryMetadataExtractor
import io.github.xiaowu20245.nekopaint.feature.history.durationText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun QueueScreen(
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var interruptTarget by remember { mutableStateOf<QueueItem?>(null) }
    var deleteTarget by remember { mutableStateOf<QueueItem?>(null) }
    val actionErrorText = state.actionError?.asString()
    val actionError = actionErrorText?.let {
        stringResource(R.string.queue_action_error, it)
    }
    val queueErrorMessage = state.error?.asString()

    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.clearActionError()
        }
    }

    val localNoticeText = when (val notice = state.localNotice) {
        is QueueNotice.Text -> notice.value
        is QueueNotice.Message -> notice.value.asString()
        is QueueNotice.Submitted -> buildString {
            append(
                stringResource(
                    R.string.queue_retry_submitted,
                    notice.promptId.take(8),
                ),
            )
            notice.warning?.let { warning ->
                append("；").append(warning.asString())
            }
        }
        is QueueNotice.Unknown -> stringResource(
            R.string.queue_retry_unknown,
            notice.message.asString(),
            notice.promptId.take(8),
        )
        null -> null
    }
    LaunchedEffect(localNoticeText) {
        if (localNoticeText != null) {
            snackbarHostState.showSnackbar(localNoticeText)
            viewModel.clearLocalNotice()
        }
    }

    val currentConnection by rememberUpdatedState(state.connection)
    LaunchedEffect(lifecycleOwner, state.configured) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!state.configured) {
                return@repeatOnLifecycle
            }
            delay(250)
            var offlineAttempt = 0
            while (isActive) {
                viewModel.refresh()
                val connection = currentConnection
                delay(QueueOfflineBackoffPolicy.delayMs(connection, offlineAttempt))
                offlineAttempt = QueueOfflineBackoffPolicy.nextAttempt(connection, offlineAttempt)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                QueueStatusHeader(
                    connection = state.connection,
                    queueRemaining = state.queueRemaining,
                    error = state.error,
                    refreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    onOpenHistory = onOpenHistory,
                )
            }

            state.recordWriteError?.let { writeError ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.queue_record_write_error,
                                writeError,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            val localPending = state.tasks.filter { TaskStatus.isLocalPending(it.status) }
            if (localPending.isNotEmpty()) {
                item {
                    Text(
                        text = "${stringResource(R.string.queue_local_pending)} " +
                            "(${localPending.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                items(localPending, key = { it.promptId }) { task ->
                    val migratedTo = QueueMigrationPolicy.migratedTo(task, state.tasks)
                    LocalPendingCard(
                        task = task,
                        migratedTo = migratedTo,
                        retrying = state.retryingPromptId == task.promptId,
                        onRecheck = { viewModel.recheckUnknown(task) },
                        onRetry = { viewModel.retryUnknown(task) },
                    )
                }
            }

            when (state.loadState) {
                QueueLoadState.Unconfigured -> {
                    if (localPending.isEmpty()) {
                        item {
                            NekoEmptyState(
                                icon = Icons.Outlined.Warning,
                                title = stringResource(R.string.queue_config_invalid_title),
                                subtitle = stringResource(R.string.queue_config_invalid_desc),
                            )
                        }
                    }
                }

                QueueLoadState.Loading -> {
                    if (localPending.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }

                QueueLoadState.Error -> {
                    if (localPending.isEmpty()) {
                        item {
                            NekoEmptyState(
                                icon = Icons.Outlined.Warning,
                                title = stringResource(R.string.queue_load_failed_title),
                                subtitle = queueErrorMessage
                                    ?: stringResource(R.string.queue_load_failed_desc),
                                actionLabel = stringResource(R.string.queue_refresh),
                                onAction = viewModel::refresh,
                            )
                        }
                    }
                }

                QueueLoadState.Refreshing,
                QueueLoadState.LoadedWithError,
                QueueLoadState.Empty,
                QueueLoadState.Loaded,
                -> {
                    val running = state.queue?.running.orEmpty()
                    val pending = state.queue?.pending.orEmpty()

                    if (running.isEmpty() && pending.isEmpty() && localPending.isEmpty()) {
                        item {
                            QueueEmptyState(
                                onOpenHistory = onOpenHistory,
                            )
                        }
                    }

                    if (running.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.queue_running),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        itemsIndexed(running, key = { _, item -> item.promptId }) { _, item ->
                            val task = state.tasks.firstOrNull { it.promptId == item.promptId }
                            val progress = state.progress[item.promptId]
                            RunningCard(
                                item = item,
                                task = task,
                                progress = progress,
                                connection = state.connection,
                                isOwn = item.clientId != null && item.clientId == state.localClientId,
                                onInterrupt = { interruptTarget = item },
                            )
                        }
                    }

                    if (pending.isNotEmpty()) {
                        item {
                            Text(
                                text = "${stringResource(R.string.queue_pending)} (${pending.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        itemsIndexed(pending, key = { _, item -> item.promptId }) { index, item ->
                            val task = state.tasks.firstOrNull { it.promptId == item.promptId }
                            PendingCard(
                                index = index + 1,
                                item = item,
                                task = task,
                                isOwn = item.clientId != null && item.clientId == state.localClientId,
                                onDelete = { deleteTarget = item },
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

    interruptTarget?.let { item ->
        val isOwn = item.clientId != null && item.clientId == state.localClientId
        NekoConfirmDialog(
            title = stringResource(R.string.queue_interrupt),
            message = stringResource(
                if (isOwn) {
                    R.string.queue_interrupt_confirm
                } else {
                    R.string.queue_interrupt_other_confirm
                },
            ),
            confirmLabel = stringResource(R.string.queue_interrupt),
            onConfirm = {
                viewModel.interrupt(item.promptId)
                interruptTarget = null
            },
            onDismiss = { interruptTarget = null },
        )
    }

    deleteTarget?.let { item ->
        val isOwn = item.clientId != null && item.clientId == state.localClientId
        NekoConfirmDialog(
            title = stringResource(R.string.queue_delete),
            message = stringResource(
                if (isOwn) {
                    R.string.queue_delete_confirm
                } else {
                    R.string.queue_delete_other_confirm
                },
            ),
            confirmLabel = stringResource(R.string.queue_delete),
            onConfirm = {
                viewModel.deletePending(item.promptId)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun QueueStatusHeader(
    connection: ComfyConnectionState,
    queueRemaining: Int,
    error: NekoMessage?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val errorText = error?.asString()
    val status = NekoStatusMapper.connection(connection, hasError = error != null)

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
                // 连接状态胶囊：可压缩、单行省略，优先保证右侧按钮不换行
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NekoStatusBadge(
                        text = status.label(),
                        tone = status.tone(),
                        icon = status.icon(),
                    )
                }

                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }

                NekoIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.queue_refresh),
                    onClick = onRefresh,
                )

                FilledTonalButton(
                    onClick = onOpenHistory,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(
                        imageVector = NekoIcons.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.queue_open_history),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.queue_remaining, queueRemaining),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.queue_description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (connection != ComfyConnectionState.Online &&
                connection != ComfyConnectionState.Unconfigured
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.queue_realtime_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (connection != ComfyConnectionState.Unconfigured) {
                errorText?.let { message ->
                    Text(
                        text = stringResource(R.string.queue_error, message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningCard(
    item: QueueItem,
    task: TaskRecord?,
    progress: TaskProgress?,
    connection: ComfyConnectionState,
    isOwn: Boolean,
    onInterrupt: () -> Unit,
) {
    val isLive = connection == ComfyConnectionState.Online
    val promptSubtitle = HistoryMetadataExtractor.extractPrompt(task, item)
    // 用本地秒表心跳驱动耗时，避免长时间模型加载时界面像冻结。
    val currentTick by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = task?.startedAt,
        key2 = isLive,
    ) {
        if (task?.startedAt != null) {
            while (true) {
                delay(1000L)
                value = System.currentTimeMillis()
            }
        }
    }
    val metrics = QueueMetricsPolicy.forTask(task, progress, currentTick)

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
            // Header Row: 44dp Icon Square + Title/Subtitle + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = NekoIcons.PlayFilled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task?.templateName ?: shortId(item.promptId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!promptSubtitle.isNullOrBlank()) {
                        Text(
                            text = promptSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                val source = NekoStatusMapper.source(isOwn)
                NekoStatusBadge(
                    text = source.label(),
                    tone = source.tone(),
                    icon = null,
                )
            }

            // Core Data Panel: Pure White Card
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
                    // 工作流总进度（宏观）
                    val completedNodes = progress?.completedNodes ?: 0
                    val totalNodes = progress?.totalNodes ?: 0
                    val overallPercent = progress?.overallPercent ?: if (totalNodes > 0) {
                        (completedNodes.toFloat() / totalNodes).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    if (metrics.showOverall) {
                        val pct = ((overallPercent ?: 0f) * 100).roundToInt()
                        val overallDescription = stringResource(
                            R.string.queue_overall_nodes_with_pct,
                            completedNodes,
                            totalNodes,
                            pct,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (totalNodes > 0) {
                                    stringResource(
                                        R.string.queue_overall_nodes,
                                        completedNodes,
                                        totalNodes,
                                    )
                                } else {
                                    stringResource(R.string.queue_overall_progress)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.queue_overall_percent, pct),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (overallPercent ?: 0f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .alpha(if (isLive) 1f else 0.45f)
                                .semantics { contentDescription = overallDescription },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // 当前节点与步数进度（微观）
                    val nodeLabel = progress?.currentNodeLabel ?: progress?.currentNode
                    val value = progress?.value
                    val max = progress?.max

                    if (nodeLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.queue_current_node, nodeLabel),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (metrics.showNodeProgress && value != null && max != null) {
                            val percent = ((value / max) * 100).roundToInt()
                            Text(
                                text = stringResource(
                                    R.string.queue_progress_steps,
                                    value.roundToInt(),
                                    max.roundToInt(),
                                    percent,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Text(
                                text = metrics.status.label(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }

                    // 当前节点细进度条（次级色）
                    if (metrics.showNodeProgress && value != null && max != null) {
                        LinearProgressIndicator(
                            progress = { (value / max).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .alpha(if (isLive) 1f else 0.45f),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                    }

                    // Performance Metrics / ETA / Rate
                    val startedAt = task?.startedAt
                    val speedText = if (startedAt != null && value != null && value > 0) {
                        val elapsedSeconds = ((currentTick - startedAt) / 1000.0).coerceAtLeast(0.1)
                        stringResource(R.string.queue_rate_label, value / elapsedSeconds)
                    } else null
                    val etaText = metrics.etaMillis?.let { etaMillis ->
                        durationText(0L, etaMillis)?.let {
                            stringResource(R.string.queue_eta_label, it)
                        }
                    }
                    val elapsedText = startedAt?.let { start ->
                        durationText(start, currentTick)?.let {
                            stringResource(R.string.queue_elapsed, it)
                        }
                    }
                    val telemetry = buildAnnotatedString {
                        if (speedText != null) append(speedText)
                        if (etaText != null) {
                            if (length > 0) append(" · ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(etaText)
                            }
                        }
                        if (elapsedText != null) {
                            if (length > 0) append(" · ")
                            append(elapsedText)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (telemetry.length > 0) {
                            Text(
                                text = telemetry,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            text = stringResource(
                                R.string.queue_prompt_label,
                                shortId(item.promptId),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 100.dp)
                                .padding(start = 6.dp),
                        )
                    }
                }
            }

            // Bottom Action Row: Batch Pill & Interrupt Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (metrics.showBatch) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.queue_batch_info,
                                (metrics.batchIndex ?: 0) + 1,
                                metrics.batchCount ?: 1,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Elegant error pill interrupt button
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.errorContainer,
                    onClick = onInterrupt,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = NekoIcons.StopSquare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(R.string.queue_interrupt_action),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPendingCard(
    task: TaskRecord,
    migratedTo: String?,
    retrying: Boolean,
    onRecheck: () -> Unit,
    onRetry: () -> Unit,
) {
    val unknown = task.status == TaskStatus.UNKNOWN
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.templateName.ifBlank { task.templateId },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "任务 ID：${task.promptId.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val (labelRes, tone) = when {
                    migratedTo != null -> R.string.queue_local_migrated to NekoStatusTone.INFO
                    unknown -> R.string.queue_local_unknown to NekoStatusTone.WARNING
                    else -> R.string.queue_local_submitting to NekoStatusTone.NEUTRAL
                }
                NekoStatusBadge(
                    text = stringResource(labelRes),
                    tone = tone,
                    icon = null,
                )
            }
            if (migratedTo != null) {
                Text(
                    text = stringResource(
                        R.string.queue_local_migrated_hint,
                        migratedTo.take(8),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (unknown) {
                Text(
                    text = stringResource(R.string.queue_local_unknown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRecheck) {
                    Text(stringResource(R.string.queue_local_check))
                }
                if (QueueMigrationPolicy.canRetry(task, migratedTo)) {
                    FilledTonalButton(
                        onClick = onRetry,
                        enabled = !retrying,
                        modifier = Modifier.height(36.dp),
                    ) {
                        if (retrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            stringResource(
                                if (retrying) {
                                    R.string.queue_local_retrying
                                } else {
                                    R.string.queue_local_retry
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCard(
    index: Int,
    item: QueueItem,
    task: TaskRecord?,
    isOwn: Boolean,
    onDelete: () -> Unit,
) {
    val promptSubtitle = HistoryMetadataExtractor.extractPrompt(task, item)
    val metadata = HistoryMetadataExtractor.extract(task)
    val sizeText = metadata.sizeText
    val stepsText = metadata.steps
    val specText = when {
        sizeText != null && stepsText != null -> stringResource(
            R.string.queue_pending_specs,
            sizeText,
            stepsText,
        )
        sizeText != null -> sizeText
        stepsText != null -> stringResource(R.string.history_steps_value, stepsText)
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Index block
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isOwn) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = task?.templateName ?: shortId(item.promptId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!promptSubtitle.isNullOrBlank()) {
                    Text(
                        text = promptSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!specText.isNullOrBlank()) {
                    Text(
                        text = specText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val source = NekoStatusMapper.source(isOwn)
                    NekoStatusBadge(
                        text = source.label(),
                        tone = source.tone(),
                        icon = null,
                    )
                    Text(
                        text = "ID: ${shortId(item.promptId)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            NekoIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.queue_delete),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueEmptyState(
    onOpenHistory: () -> Unit,
) {
    NekoEmptyState(
        icon = NekoIcons.PlayFilled,
        title = stringResource(R.string.queue_empty_title),
        subtitle = stringResource(R.string.queue_empty_desc),
        actionLabel = stringResource(R.string.queue_open_history),
        onAction = onOpenHistory,
        modifier = Modifier.fillMaxWidth(),
    )
}



private fun shortId(promptId: String): String =
    if (promptId.length <= 8) promptId else promptId.take(8)

private fun durationText(start: Long?, end: Long?): String? {
    if (start == null || end == null || end < start) return null
    val totalSeconds = ((end - start) / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

@Preview(showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun QueueScreenPreview() {
    NekoPaintTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QueueStatusHeader(
                connection = ComfyConnectionState.Offline,
                queueRemaining = 0,
                error = null,
                refreshing = false,
                onRefresh = {},
                onOpenHistory = {},
            )
            QueueEmptyState(onOpenHistory = {})
        }
    }
}
