package io.github.xiaowu20245.nekopaint.feature.chat

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatToolsSheet(
    mode: ChatMode,
    sending: Boolean,
    attachedImage: Uri?,
    question: String,
    onQuestionChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onDismiss: () -> Unit,
    onRunImageTool: (String, String?) -> Unit,
    onRunTool: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val enabled = !sending
    val imageEnabled = enabled && attachedImage != null
    val questionEnabled = imageEnabled && question.isNotBlank()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_tools_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.chat_tools_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = NekoIcons.Toolbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
            }

            ChatToolsAttachmentCard(
                attachedImage = attachedImage,
                enabled = enabled,
                onPickImage = onPickImage,
                onRemoveImage = onRemoveImage,
            )

            ChatToolsSectionTitle(stringResource(R.string.chat_tools_image_section))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_tags_title),
                    description = stringResource(R.string.chat_tools_tags_desc),
                    icon = NekoIcons.Sparkles,
                    enabled = imageEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onRunImageTool("tags", null) },
                )
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_describe_title),
                    description = stringResource(R.string.chat_tools_describe_desc),
                    icon = NekoIcons.ImageFrame,
                    enabled = imageEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onRunImageTool("describe", null) },
                )
            }
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                placeholder = {
                    Text(stringResource(R.string.chat_tools_question_hint))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_question_title),
                    description = stringResource(
                        if (question.isBlank()) {
                            R.string.chat_tools_question_need_input
                        } else {
                            R.string.chat_tools_question_desc
                        },
                    ),
                    icon = Icons.Outlined.Search,
                    enabled = questionEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onRunImageTool("question", question) },
                )
                Spacer(Modifier.weight(1f))
            }

            ChatToolsSectionTitle(stringResource(R.string.chat_tools_query_section))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_queue_title),
                    description = stringResource(R.string.chat_tools_queue_desc),
                    icon = NekoIcons.Stopwatch,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRunTool("get_queue_snapshot", "队列状态")
                    },
                )
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_history_title),
                    description = stringResource(R.string.chat_tools_history_desc),
                    icon = NekoIcons.Timeline,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRunTool("get_recent_history", "最近历史")
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_workflow_title),
                    description = stringResource(R.string.chat_tools_workflow_desc),
                    icon = NekoIcons.WorkflowNodes,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRunTool("get_workflow_summary", "工作流概览")
                    },
                )
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_diagnose_title),
                    description = stringResource(R.string.chat_tools_diagnose_desc),
                    icon = NekoIcons.Terminal,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRunTool("diagnose_task", "诊断任务")
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatToolCard(
                    title = stringResource(R.string.chat_tools_images_title),
                    description = stringResource(R.string.chat_tools_images_desc),
                    icon = NekoIcons.ImageFrame,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRunTool("get_history_images", "历史图片")
                    },
                )
                Spacer(Modifier.weight(1f))
            }

            if (mode == ChatMode.AGENT) {
                Text(
                    text = stringResource(R.string.chat_tools_agent_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatToolsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ChatToolsAttachmentCard(
    attachedImage: Uri?,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (attachedImage == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = enabled, onClick = onPickImage)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = NekoIcons.ImageFrame,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_tools_pick_image),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.chat_tools_pick_image_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = NekoIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = attachedImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_tools_selected_image),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.chat_tools_selected_image_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onPickImage,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(stringResource(R.string.chat_tools_replace))
                }
                IconButton(
                    onClick = onRemoveImage,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.chat_tools_remove),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = contentAlpha),
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = contentAlpha),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ChatTypingBubble(status: ChatStatus?) {
    val statusText = stringResource(
        when (status) {
            ChatStatus.QUEUE -> R.string.chat_status_queue
            ChatStatus.HISTORY -> R.string.chat_status_history
            ChatStatus.WORKFLOW -> R.string.chat_status_workflow
            ChatStatus.DIAGNOSE -> R.string.chat_status_diagnose
            ChatStatus.ACTION -> R.string.chat_status_action
            ChatStatus.VISION -> R.string.chat_status_vision
            ChatStatus.FINALIZE -> R.string.chat_status_finalize
            ChatStatus.THINKING, null -> R.string.chat_thinking
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = NekoIcons.Sparkles,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    attachedImage: Uri?,
    visionConfigured: Boolean,
    inputHint: String,
    sending: Boolean,
    onOpenTools: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column {
            if (attachedImage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = attachedImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_image_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!visionConfigured) {
                            Text(
                                text = stringResource(R.string.chat_vision_missing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = onRemoveImage) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.chat_remove_image),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = onOpenTools,
                    enabled = !sending,
                ) {
                    Icon(
                        imageVector = NekoIcons.Toolbox,
                        contentDescription = stringResource(R.string.chat_tools_title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(inputHint)
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(26.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                val canSend = !sending && (input.isNotBlank() || attachedImage != null)
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                ) {
                    Icon(
                        imageVector = NekoIcons.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}
