package io.github.xiaowu20245.nekopaint.feature.history

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onOpenDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(10_000)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HistoryHeader(
                connection = state.connection,
                error = state.error,
                refreshing = state.refreshing,
                showTempPreviews = state.showTempPreviews,
                onToggleTempPreviews = viewModel::setShowTempPreviews,
                onRefresh = viewModel::refresh,
            )
        }

        if (state.entries.isEmpty()) {
            item {
                EmptyHistory(loading = state.refreshing)
            }
        } else {
            items(state.entries, key = { it.promptId }) { entry ->
                HistoryCard(
                    entry = entry,
                    task = state.tasks.firstOrNull { it.promptId == entry.promptId },
                    showTempPreviews = state.showTempPreviews,
                    onClick = { onOpenDetail(entry.promptId) },
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    connection: ComfyConnectionState,
    error: String?,
    refreshing: Boolean,
    showTempPreviews: Boolean,
    onToggleTempPreviews: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (connection) {
                        ComfyConnectionState.Connected -> stringResource(R.string.queue_connection_connected)
                        ComfyConnectionState.Connecting -> stringResource(R.string.queue_connection_connecting)
                        ComfyConnectionState.Reconnecting -> stringResource(R.string.queue_connection_reconnecting)
                        ComfyConnectionState.Disconnected -> stringResource(R.string.queue_connection_disconnected)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.history_refresh),
                    )
                }
            }
            Text(
                text = stringResource(R.string.history_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showTempPreviews,
                    onClick = { onToggleTempPreviews(false) },
                    label = { Text(stringResource(R.string.history_filter_output)) },
                )
                FilterChip(
                    selected = showTempPreviews,
                    onClick = { onToggleTempPreviews(true) },
                    label = { Text(stringResource(R.string.history_filter_all)) },
                )
            }
            Text(
                text = stringResource(R.string.history_filter_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { message ->
                Text(
                    text = stringResource(R.string.history_error, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.history_stale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(loading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.padding(20.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.history_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    task: TaskRecord?,
    showTempPreviews: Boolean,
    onClick: () -> Unit,
) {
    val images = entry.visibleImages(showTempPreviews)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task?.templateName ?: shortId(entry.promptId),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = entry.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                entry.timestamp?.let { timestamp ->
                    Text(
                        text = stringResource(
                            R.string.history_time,
                            formatTime(timestamp),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                durationText(
                    start = entry.startedAt ?: task?.startedAt,
                    end = entry.finishedAt ?: entry.timestamp ?: task?.finishedAt,
                )?.let { duration ->
                    Text(
                        text = stringResource(R.string.history_duration, duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.take(3).forEach { image ->
                        AsyncImage(
                            model = image.url,
                            contentDescription = stringResource(
                                R.string.history_thumbnail_desc,
                                image.filename,
                            ),
                            contentScale = ContentScale.Crop,
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
                            fallback = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            Text(
                text = if (images.isEmpty()) {
                    stringResource(R.string.history_no_images)
                } else {
                    stringResource(R.string.history_images, images.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
            Text(
                text = stringResource(R.string.history_prompt_id, shortId(entry.promptId)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

fun shortId(promptId: String): String =
    if (promptId.length <= 8) promptId else promptId.take(8)
