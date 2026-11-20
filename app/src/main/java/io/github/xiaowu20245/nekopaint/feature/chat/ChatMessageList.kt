package io.github.xiaowu20245.nekopaint.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoIconButton
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatEmptyState(
    taskContext: ChatTaskContext?,
    onAskContext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val slogan = AiSloganProvider.rememberSlogan()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = NekoIcons.Sparkles,
            // 装饰性品牌图标；下方标题已说明空会话状态。
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = slogan.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            slogan.kaomoji?.let { kaomoji ->
                Text(
                    text = kaomoji,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
        if (onOpenSettings != null) {
            Spacer(Modifier.height(22.dp))
            FilledTonalButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = NekoIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.chat_unconfigured_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (onAskContext != null) {
            Spacer(Modifier.height(22.dp))
            FilledTonalButton(
                onClick = onAskContext,
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = NekoIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (taskContext?.isFailure == true) {
                            R.string.chat_context_ask_failed
                        } else {
                            R.string.chat_context_ask_task
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun ChatTaskContextBanner(
    taskContext: ChatTaskContext?,
    promptId: String,
    onDetach: () -> Unit,
) {
    val statusLabel = when (taskContext?.status) {
        "success" -> stringResource(R.string.history_status_success)
        "failed" -> stringResource(R.string.history_status_failed)
        "interrupted" -> stringResource(R.string.history_status_interrupted)
        else -> null
    }
    val statusColor = when (taskContext?.status) {
        "success" -> MaterialTheme.colorScheme.primary
        "failed" -> MaterialTheme.colorScheme.error
        "interrupted" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = NekoIcons.Sparkles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_context_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = taskContext?.title
                            ?: stringResource(
                                R.string.chat_context_task_fallback,
                                promptId.take(8),
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (statusLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                }
            }
            NekoIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.chat_context_detach),
                onClick = onDetach,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ChatMessageBubble(
    message: ChatUiMessage,
    onConfirmAction: (Long, String) -> Unit,
    onCancelAction: (Long, String) -> Unit,
    onSelectPromptCandidate: (Long, String, String) -> Unit,
    onToggleInstallRestart: (Long, String) -> Unit,
    onRequestSavePreset: (Long, ChatActionProposal) -> Unit,
    actionsEnabled: Boolean,
    onOpenTags: (String, String) -> Unit,
    onOpenHistoryImage: (List<ChatHistoryImage>, Int) -> Unit,
) {
    if (message.role == ChatRole.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (message.isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val imageModel = message.imageDataUrl ?: message.imageUri
                    if (imageModel != null) {
                        SubcomposeAsyncImage(
                            model = imageModel,
                            // 装饰性附图：消息正文与操作按钮已提供可读语义。
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = NekoIcons.BrokenImage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                        )
                    } else if (message.imageId != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = NekoIcons.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.chat_image_missing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = NekoIcons.Sparkles,
                contentDescription = null,
                tint = if (message.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.actionProposals.forEach { proposal ->
                    ChatActionProposalCard(
                        proposal = proposal,
                        onConfirm = {
                            onConfirmAction(message.id, proposal.id)
                        },
                        onCancel = {
                            onCancelAction(message.id, proposal.id)
                        },
                        onSelectPrompt = { candidateId ->
                            onSelectPromptCandidate(message.id, proposal.id, candidateId)
                        },
                        onSavePreset = {
                            onRequestSavePreset(message.id, proposal)
                        },
                        onToggleInstallRestart = {
                            onToggleInstallRestart(message.id, proposal.id)
                        },
                        actionsEnabled = actionsEnabled,
                    )
                }
                if (message.toolActivities.isNotEmpty()) {
                    ChatToolActivityCard(activities = message.toolActivities)
                }
                val copyableTags = remember(message.text) {
                    extractCopyableTags(message.text)
                }
                val displayText = remember(message.text, copyableTags) {
                    if (copyableTags != null) stripTagsSection(message.text) else message.text
                }
                if (displayText.isNotBlank()) {
                    SelectionContainer {
                        ChatMarkdownText(
                            text = displayText,
                            color = if (message.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                if (copyableTags != null) {
                    ChatTagBlock(
                        tags = copyableTags,
                        onClick = { onOpenTags(copyableTags, displayText) },
                    )
                }
                if (message.historyImages.isNotEmpty()) {
                    ChatHistoryImageGrid(
                        images = message.historyImages,
                        onOpen = { index ->
                            onOpenHistoryImage(message.historyImages, index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTagBlock(
    tags: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(tags) { mutableStateOf(false) }
    val tagCount = remember(tags) { tags.split(',').count { it.isNotBlank() } }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = NekoIcons.Sparkles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.chat_tags_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.chat_tags_count, tagCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = tags,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chat_tags_open),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("tags", tags))
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = NekoIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (copied) R.string.chat_tags_copied else R.string.chat_copy_tags,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryImageGrid(
    images: List<ChatHistoryImage>,
    onOpen: (Int) -> Unit,
) {
    if (images.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (images.size == 1) {
            ChatHistoryImageTile(
                image = images.first(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f),
                onClick = { onOpen(0) },
            )
        } else {
            images.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEachIndexed { columnIndex, image ->
                        val index = rowIndex * 2 + columnIndex
                        ChatHistoryImageTile(
                            image = image,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onClick = { onOpen(index) },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryImageTile(
    image: ChatHistoryImage,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        SubcomposeAsyncImage(
            model = image.url,
            contentDescription = image.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
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
                        imageVector = NekoIcons.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (image.isOutput) {
                        R.string.history_tag_output
                    } else {
                        R.string.history_tag_temp
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ChatToolActivityCard(activities: List<ChatToolActivity>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val errorCount = activities.count { it.isError }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.chat_tool_activity_count,
                        activities.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (errorCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_tool_activity_errors, errorCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = NekoIcons.ChevronRight,
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.chat_tool_activity_collapse
                        } else {
                            R.string.chat_tool_activity_expand
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (expanded) 90f else 0f),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                activities.forEach { activity ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = toolActivityLabel(activity),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        toolActivityDetail(activity)?.let { detail ->
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (activity.isError) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.chat_tool_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun toolActivityLabel(activity: ChatToolActivity): String = when (activity.name) {
    "get_queue_snapshot" -> stringResource(R.string.chat_tool_queue)
    "get_recent_history" -> stringResource(R.string.chat_tool_history)
    "get_history_images" -> stringResource(R.string.chat_tool_images)
    "get_workflow_parameters" -> stringResource(R.string.chat_tool_parameters)
    "get_workflow_summary" -> stringResource(R.string.chat_tool_workflow)
    "diagnose_task" -> stringResource(R.string.chat_tool_diagnose)
    "analyze_image" -> stringResource(R.string.chat_tool_vision)
    else -> activity.name
}

@Composable
private fun toolActivityDetail(activity: ChatToolActivity): String? = when (activity.name) {
    "get_recent_history" -> {
        val status = when (activity.status) {
            "success" -> stringResource(R.string.history_status_success)
            "failed" -> stringResource(R.string.history_status_failed)
            "interrupted" -> stringResource(R.string.history_status_interrupted)
            else -> null
        }
        val count = stringResource(
            R.string.chat_tool_recent_count,
            activity.limit ?: 5,
        )
        listOfNotNull(status, count).joinToString(" · ")
    }

    "get_workflow_summary" -> activity.keyword
        ?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.chat_tool_keyword, it) }

    "get_history_images" -> {
        val count = stringResource(R.string.chat_tool_image_count, activity.limit ?: 4)
        activity.promptId
            ?.let { "${stringResource(R.string.chat_tool_task, it.take(8))} · $count" }
            ?: count
    }

    "diagnose_task" -> activity.promptId
        ?.let { stringResource(R.string.chat_tool_task, it.take(8)) }
        ?: stringResource(R.string.chat_tool_latest_failure)

    "analyze_image" -> {
        val mode = when (activity.mode) {
            "tags" -> stringResource(R.string.chat_tool_vision_tags)
            "describe" -> stringResource(R.string.chat_tool_vision_describe)
            "question" -> stringResource(R.string.chat_tool_vision_question)
            else -> null
        }
        val image = activity.imageId
            ?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.chat_tool_vision_image, it.takeLast(6)) }
        listOfNotNull(mode, image).joinToString(" · ").takeIf { it.isNotBlank() }
    }

    else -> null
}

