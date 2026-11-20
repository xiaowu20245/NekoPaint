package io.github.xiaowu20245.nekopaint.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.ui.component.icon
import io.github.xiaowu20245.nekopaint.core.ui.component.label
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryImageActionSheet(
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
internal fun HistoryLightboxDialog(
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
        // 装饰性缩略图：外层卡片/网格已提供语义描述，避免重复朗读。
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
internal fun HistoryDetailDrawerContent(
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

