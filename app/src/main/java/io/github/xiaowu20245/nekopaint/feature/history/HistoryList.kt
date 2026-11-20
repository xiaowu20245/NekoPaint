package io.github.xiaowu20245.nekopaint.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.ui.component.icon
import io.github.xiaowu20245.nekopaint.core.ui.component.label
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import java.util.Date

@Composable
internal fun TimelineBatchView(
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
internal fun GalleryGridView(
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
internal fun CompactAlbumView(
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

