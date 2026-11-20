package io.github.xiaowu20245.nekopaint.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons

@Composable
internal fun ChatActionProposalCard(
    proposal: ChatActionProposal,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSelectPrompt: (String) -> Unit = {},
    onSavePreset: () -> Unit = {},
    onToggleInstallRestart: () -> Unit = {},
    actionsEnabled: Boolean = true,
) {
    val accent = when (proposal.action) {
        ChatTaskAction.RETRY,
        ChatTaskAction.SUBMIT,
        ChatTaskAction.BATCH_RETRY,
        ChatTaskAction.INSTALL_NODES -> MaterialTheme.colorScheme.primary

        ChatTaskAction.INTERRUPT,
        ChatTaskAction.DELETE,
        ChatTaskAction.BATCH_DELETE -> MaterialTheme.colorScheme.error
    }
    val actionLabel = stringResource(
        when (proposal.action) {
            ChatTaskAction.RETRY -> R.string.chat_action_retry
            ChatTaskAction.INTERRUPT -> R.string.chat_action_interrupt
            ChatTaskAction.DELETE -> R.string.chat_action_delete
            ChatTaskAction.SUBMIT -> R.string.chat_action_submit
            ChatTaskAction.BATCH_RETRY -> R.string.chat_action_batch_retry
            ChatTaskAction.BATCH_DELETE -> R.string.chat_action_batch_delete
            ChatTaskAction.INSTALL_NODES -> R.string.chat_action_install_nodes
        },
    )
    val isSubmit = proposal.action == ChatTaskAction.SUBMIT
    val isBatch = proposal.action == ChatTaskAction.BATCH_RETRY ||
        proposal.action == ChatTaskAction.BATCH_DELETE
    val isInstall = proposal.action == ChatTaskAction.INSTALL_NODES
    val statusLabel = chatStatusLabel(proposal.status)
    var showOtherClientConfirm by remember(proposal.id, proposal.state) {
        mutableStateOf(false)
    }
    val confirmLabel = stringResource(
        when {
            isSubmit -> R.string.chat_action_confirm_submit
            isInstall && proposal.restartAfterInstall ->
                R.string.chat_action_confirm_install_restart
            isInstall -> R.string.chat_action_confirm_install
            else -> R.string.chat_action_confirm
        },
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (proposal.action) {
                        ChatTaskAction.RETRY -> Icons.Outlined.Refresh
                        ChatTaskAction.INTERRUPT -> NekoIcons.StopSquare
                        ChatTaskAction.DELETE -> Icons.Outlined.Delete
                        ChatTaskAction.SUBMIT -> NekoIcons.PlayFilled
                        ChatTaskAction.BATCH_RETRY -> Icons.Outlined.Refresh
                        ChatTaskAction.BATCH_DELETE -> Icons.Outlined.Delete
                        ChatTaskAction.INSTALL_NODES -> Icons.Outlined.Build
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = proposal.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isBatch) {
                        stringResource(
                            R.string.chat_action_batch_count,
                            proposal.batchTargets.size,
                        )
                    } else if (isInstall) {
                        stringResource(
                            R.string.chat_action_install_summary,
                            proposal.missingNodeTypeCount,
                            proposal.installPackages.size,
                        )
                    } else if (isSubmit) {
                        stringResource(R.string.chat_action_text_to_image)
                    } else {
                        "$statusLabel · ${proposal.shortId}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isSubmit && !isInstall) {
                    Text(
                        text = stringResource(
                            when (proposal.source) {
                                ChatTaskSource.LOCAL -> R.string.chat_action_source_local
                                ChatTaskSource.OTHER -> R.string.chat_action_source_other
                                ChatTaskSource.UNKNOWN -> R.string.chat_action_source_unknown
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (proposal.hasOtherClientSource) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (isBatch && proposal.batchTargets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    proposal.batchTargets.take(5).forEach { target ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = target.promptId.take(8),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (proposal.batchTargets.size > 5) {
                        Text(
                            text = "…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isInstall && proposal.installPackages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    proposal.binding?.let { binding ->
                        Text(
                            text = stringResource(
                                R.string.chat_action_install_target_server,
                                "${binding.host}:${binding.port}",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    proposal.installPackages.take(5).forEach { pkg ->
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = pkg.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.chat_action_install_source,
                                    pkg.sourceLabel,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (proposal.installPackages.size > 5) {
                        Text(
                            text = "…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (proposal.unresolvedNodeTypes.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.chat_action_install_unresolved,
                                proposal.unresolvedNodeTypes.take(4).joinToString("、"),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (proposal.state == ChatActionState.PENDING ||
                        proposal.state == ChatActionState.EXECUTING
                    ) {
                        Text(
                            text = stringResource(R.string.chat_action_install_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Checkbox(
                                checked = proposal.restartAfterInstall,
                                onCheckedChange = { onToggleInstallRestart() },
                                enabled = actionsEnabled &&
                                    proposal.state == ChatActionState.PENDING,
                                modifier = Modifier.size(30.dp),
                            )
                            Text(
                                text = stringResource(R.string.chat_action_install_restart),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (isSubmit && !proposal.promptText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = proposal.promptText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            if (isSubmit) {
                var fieldMenuExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.chat_action_prompt_field,
                            proposal.promptCandidateLabel ?: "提示词",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (proposal.promptOptions.size > 1 &&
                        proposal.state == ChatActionState.PENDING
                    ) {
                        Box {
                            TextButton(
                                onClick = { fieldMenuExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_action_change_field),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            DropdownMenu(
                                expanded = fieldMenuExpanded,
                                onDismissRequest = { fieldMenuExpanded = false },
                            ) {
                                proposal.promptOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${option.label}  #${option.nodeId}")
                                        },
                                        onClick = {
                                            fieldMenuExpanded = false
                                            onSelectPrompt(option.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSubmit && proposal.parameterOverrides.isNotEmpty()) {
                var parametersExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.chat_action_parameter_overrides,
                            proposal.parameterOverrides.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { parametersExpanded = !parametersExpanded },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (parametersExpanded) {
                                    R.string.chat_action_hide_parameters
                                } else {
                                    R.string.chat_action_show_parameters
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (parametersExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        proposal.parameterOverrides.forEach { parameter ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = parameter.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${parameter.oldValue} → ${parameter.newValue}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            when (proposal.state) {
                ChatActionState.PENDING -> if (actionsEnabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (proposal.requiresOtherClientConfirmation) {
                                    showOtherClientConfirm = true
                                } else {
                                    onConfirm()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(
                                text = confirmLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_mode_read_only_action),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                ChatActionState.EXECUTING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.chat_action_executing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ChatActionState.SUCCESS -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = proposal.resultMessage
                            ?: stringResource(R.string.chat_action_success),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ChatActionState.FAILED -> Text(
                    text = proposal.resultMessage
                        ?: stringResource(R.string.chat_action_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )

                ChatActionState.CANCELLED -> Text(
                    text = stringResource(R.string.chat_action_cancelled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isSubmit && proposal.state == ChatActionState.SUCCESS) {
                when {
                    proposal.presetSaved -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.chat_action_preset_saved,
                                proposal.presetName.orEmpty(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    proposal.presetSaving -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.chat_action_preset_saving),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        proposal.presetError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (actionsEnabled) {
                            TextButton(
                                onClick = onSavePreset,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_action_save_preset),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.chat_mode_read_only_action),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOtherClientConfirm) {
        AlertDialog(
            onDismissRequest = { showOtherClientConfirm = false },
            title = { Text(stringResource(R.string.chat_action_other_client_title)) },
            text = { Text(stringResource(R.string.chat_action_other_client_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOtherClientConfirm = false
                        onConfirm()
                    },
                ) {
                    Text(stringResource(R.string.chat_action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOtherClientConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun ChatMarkdownText(text: String, color: Color) {
    val blocks = remember(text) { parseChatMarkdown(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ChatMarkdownBlock.Heading -> Text(
                    text = buildChatMarkdownInline(block.text, codeBackground, linkColor),
                    style = when (block.level) {
                        1, 2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.Bold,
                    color = color,
                )

                is ChatMarkdownBlock.Paragraph -> Text(
                    text = buildChatMarkdownInline(block.text, codeBackground, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )

                is ChatMarkdownBlock.Quote -> Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildChatMarkdownInline(block.text, codeBackground, linkColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color.copy(alpha = 0.82f),
                    )
                }

                is ChatMarkdownBlock.CodeBlock -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = block.code,
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        softWrap = false,
                    )
                }

                is ChatMarkdownBlock.ListBlock -> Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    block.items.forEachIndexed { itemIndex, item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (block.ordered) {
                                    "${block.start + itemIndex}."
                                } else {
                                    "•"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = color.copy(alpha = 0.7f),
                                modifier = Modifier.widthIn(min = 22.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = buildChatMarkdownInline(
                                    item,
                                    codeBackground,
                                    linkColor,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = color,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                ChatMarkdownBlock.Divider -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

