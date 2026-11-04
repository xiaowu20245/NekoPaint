package io.github.xiaowu20245.nekopaint.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.coroutines.isActive

@Composable
fun HistoryDetailScreen(promptId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

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

    val entry = state.entries.firstOrNull { it.promptId == promptId }
    val task = state.tasks.firstOrNull { it.promptId == promptId }

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
                resubmitting = state.resubmitting,
                onResubmit = { task?.let(viewModel::resubmit) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun HistoryDetailContent(
    entry: HistoryEntry,
    task: TaskRecord?,
    resubmitting: Boolean,
    onResubmit: () -> Unit,
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
                    if (entry.images.isEmpty()) {
                        Text(
                            text = stringResource(R.string.history_no_images),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        entry.images.forEach { image ->
                            Text(
                                text = buildString {
                                    append(image.filename)
                                    if (image.subfolder.isNotBlank()) {
                                        append(" · ")
                                        append(image.subfolder)
                                    }
                                    if (image.type.isNotBlank() && image.type != "output") {
                                        append(" · ")
                                        append(image.type)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
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
