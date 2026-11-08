package io.github.xiaowu20245.nekopaint.feature.queue

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskProgress
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

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
    val actionError = state.actionError?.let {
        stringResource(R.string.queue_action_error, it)
    }

    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.clearActionError()
        }
    }

    LaunchedEffect(lifecycleOwner, state.connection) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                // WebSocket 正常时 5 秒校准一次；断线时降到 2 秒轮询。
                delay(
                    if (state.connection == ComfyConnectionState.Connected) 5_000L else 2_000L,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                QueueHeader(
                    connection = state.connection,
                    queueRemaining = state.queueRemaining,
                    error = state.error,
                    refreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    onOpenHistory = onOpenHistory,
                )
            }

        if (state.queue == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            val running = state.queue?.running.orEmpty()
            val pending = state.queue?.pending.orEmpty()

            if (running.isEmpty() && pending.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.queue_empty),
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (running.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.queue_running),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(running, key = { it.promptId }) { item ->
                    RunningCard(
                        item = item,
                        task = state.tasks.firstOrNull { it.promptId == item.promptId },
                        progress = state.progress[item.promptId],
                        connection = state.connection,
                        isOwn = item.clientId != null && item.clientId == state.localClientId,
                        onInterrupt = { interruptTarget = item },
                    )
                }
            }

            if (pending.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.queue_pending),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(pending, key = { it.promptId }) { item ->
                    PendingCard(
                        item = item,
                        task = state.tasks.firstOrNull { it.promptId == item.promptId },
                        isOwn = item.clientId != null && item.clientId == state.localClientId,
                        onDelete = { deleteTarget = item },
                    )
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
        ConfirmDialog(
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
        ConfirmDialog(
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
private fun QueueHeader(
    connection: ComfyConnectionState,
    queueRemaining: Int,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val (label, color) = when (connection) {
        ComfyConnectionState.Connected -> stringResource(R.string.queue_connection_connected) to MaterialTheme.colorScheme.primary
        ComfyConnectionState.Connecting -> stringResource(R.string.queue_connection_connecting) to MaterialTheme.colorScheme.tertiary
        ComfyConnectionState.Reconnecting -> stringResource(R.string.queue_connection_reconnecting) to MaterialTheme.colorScheme.tertiary
        ComfyConnectionState.Disconnected -> stringResource(R.string.queue_connection_disconnected) to MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = color.copy(alpha = 0.12f),
                    contentColor = color,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.queue_refresh),
                    )
                }
            }
            Text(
                text = stringResource(R.string.queue_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.queue_remaining, queueRemaining),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (connection != ComfyConnectionState.Connected) {
                Text(
                    text = stringResource(R.string.queue_realtime_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            error?.let { message ->
                Text(
                    text = stringResource(R.string.queue_error, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.queue_stale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenHistory) {
                Text(stringResource(R.string.queue_open_history))
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
    val isLive = connection == ComfyConnectionState.Connected
    val staleHint = when (connection) {
        ComfyConnectionState.Connected -> null
        ComfyConnectionState.Connecting,
        ComfyConnectionState.Reconnecting,
        -> stringResource(R.string.queue_progress_reconnecting)

        ComfyConnectionState.Disconnected -> stringResource(R.string.queue_progress_disconnected)
    }
    val staleColor = if (connection == ComfyConnectionState.Disconnected) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task?.templateName ?: shortId(item.promptId),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onInterrupt) {
                    Text(stringResource(R.string.queue_interrupt))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.number?.let { number ->
                    SourceBadge(stringResource(R.string.queue_number, number))
                }
                SourceBadge(
                    text = stringResource(
                        if (isOwn) R.string.queue_task_own else R.string.queue_task_other,
                    ),
                    highlighted = isOwn,
                )
            }
            val nodeLabel = progress?.currentNodeLabel
                ?: progress?.currentNode
                ?: stringResource(R.string.queue_node_unknown)
            Text(
                text = stringResource(R.string.queue_current_node, nodeLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (staleHint != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = staleColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = staleHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = staleColor,
                    )
                }
            }
            val value = progress?.value
            val max = progress?.max
            if (value != null && max != null && max > 0) {
                Text(
                    text = stringResource(
                        R.string.queue_progress_value,
                        value.roundToInt(),
                        max.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { (value / max).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .alpha(if (isLive) 1f else 0.4f),
                )
            } else if (isLive) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
            Text(
                text = stringResource(R.string.history_prompt_id, shortId(item.promptId)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingCard(
    item: QueueItem,
    task: TaskRecord?,
    isOwn: Boolean,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task?.templateName ?: shortId(item.promptId),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.number?.let { number ->
                        SourceBadge(stringResource(R.string.queue_number, number))
                    }
                    SourceBadge(
                        text = stringResource(
                            if (isOwn) R.string.queue_task_own else R.string.queue_task_other,
                        ),
                        highlighted = isOwn,
                    )
                }
                Text(
                    text = stringResource(R.string.history_prompt_id, shortId(item.promptId)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.queue_delete))
            }
        }
    }
}

@Composable
private fun SourceBadge(
    text: String,
    highlighted: Boolean = false,
) {
    val color = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun shortId(promptId: String): String =
    if (promptId.length <= 8) promptId else promptId.take(8)
