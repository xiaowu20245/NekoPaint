package io.github.xiaowu20245.nekopaint.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoEmptyState
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusBadge
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusMapper
import io.github.xiaowu20245.nekopaint.core.ui.component.icon
import io.github.xiaowu20245.nekopaint.core.ui.component.label
import io.github.xiaowu20245.nekopaint.core.ui.component.tone
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun HistorySubHeader(
    filter: HistoryFilter,
    totalCount: Int,
    selectedCount: Int?,
    hosts: List<HistoryHost>,
    selectedHostId: String?,
    showTempPreviews: Boolean,
    tempSwitchEnabled: Boolean,
    onHostChange: (String?) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onShowTempChange: (Boolean) -> Unit,
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
            FilterChip(
                selected = showTempPreviews,
                enabled = tempSwitchEnabled,
                onClick = { onShowTempChange(!showTempPreviews) },
                label = {
                    Text(
                        text = stringResource(R.string.history_show_temp_switch),
                        style = MaterialTheme.typography.labelMedium,
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
                    enabled = tempSwitchEnabled,
                    selected = showTempPreviews,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
            if (!tempSwitchEnabled) {
                Text(
                    text = stringResource(R.string.history_temp_forced_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
internal fun EmptyHistory(
    loading: Boolean,
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
            NekoEmptyState(
                icon = NekoIcons.Timeline,
                title = stringResource(R.string.history_empty),
                subtitle = stringResource(R.string.history_empty_desc),
            )
        }
    }
}

@Composable
internal fun HostBadge(
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
    val mapped = NekoStatusMapper.task(status)
    NekoStatusBadge(
        text = mapped.label(),
        tone = mapped.tone(),
        icon = mapped.icon(),
    )
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

internal fun formatDateGroup(
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
