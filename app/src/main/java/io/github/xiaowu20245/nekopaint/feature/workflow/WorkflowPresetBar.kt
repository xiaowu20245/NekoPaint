package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.feature.common.asString
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlin.random.Random

@Composable
internal fun WorkflowPresetSection(
    template: WorkflowTemplate,
    dirty: Boolean,
    onSelectPreset: (String?) -> Unit,
    onRequestAddPreset: () -> Unit,
    onRequestRenamePreset: (String) -> Unit,
    onDeleteActivePreset: () -> Unit,
) {
    val activePreset = template.presets.firstOrNull { it.id == template.activePresetId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = NekoIcons.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = stringResource(R.string.workflow_preset_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (dirty) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = NekoTheme.colors.warningContainer,
                            contentColor = NekoTheme.colors.onWarningContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_preset_modified),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (activePreset != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRequestRenamePreset(activePreset.name) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.workflow_rename),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = stringResource(R.string.workflow_rename),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NekoTheme.colors.dangerContainer.copy(alpha = 0.7f),
                            contentColor = NekoTheme.colors.onDangerContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onDeleteActivePreset),
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_preset_delete),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRequestAddPreset),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = stringResource(R.string.workflow_preset_add),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PresetChip(
                    label = stringResource(R.string.workflow_preset_default),
                    selected = template.activePresetId == null,
                    onClick = { onSelectPreset(null) },
                )
                template.presets.forEach { preset ->
                    PresetChip(
                        label = preset.name,
                        selected = template.activePresetId == preset.id,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun ModeControlRow(
    selectedCount: Int,
    totalCount: Int,
    advanced: Boolean,
    editing: Boolean,
    compactPresetName: String? = null,
    onSelectMode: (Int) -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(modifier = Modifier.padding(2.dp)) {
                SegmentedTabItem(
                    label = stringResource(R.string.workflow_mode_simple_count, selectedCount),
                    selected = !advanced,
                    onClick = { onSelectMode(0) },
                )
                SegmentedTabItem(
                    label = stringResource(R.string.workflow_mode_advanced_count, totalCount),
                    selected = advanced,
                    onClick = { onSelectMode(1) },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (compactPresetName != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = stringResource(
                            R.string.workflow_preset_current_short,
                            compactPresetName,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            if (!advanced) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            onClick = if (editing) onFinishEditing else onStartEditing,
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(
                                if (editing) {
                                    R.string.workflow_done
                                } else {
                                    R.string.workflow_manage_selected
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * 方案 B：底部常驻单行一体式步进器底栏。
 *
 * - 左侧：[保存/更新预设] + [🎲 每次随机 / 递增+1 / 固定种子]（无种子节点的工作流自动隐藏种子策略胶囊）
 * - 右侧：[ − | N次 | ＋ | ▶ 提交生成 ] 一体式步进主按钮
 */
@Composable
internal fun StickyBatchBottomBar(
    template: WorkflowTemplate,
    dirty: Boolean,
    saving: Boolean,
    submitting: Boolean,
    batchCount: Int,
    seedStrategy: SeedStrategy,
    onSave: () -> Unit,
    onCycleSeedStrategy: () -> Unit,
    onStepBatch: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    val submitEnabled = !submitting && !saving &&
    !template.repairRequired && template.selectedCount > 0
    val hasActiveCustomPreset = template.activePresetId != null
    val batchDecLabel = stringResource(R.string.workflow_batch_dec)
    val batchIncLabel = stringResource(R.string.workflow_batch_inc)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 左侧：保存按钮 + 种子策略切换胶囊（仅含种子节点时显示）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (dirty) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (dirty) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !saving && !submitting, onClick = onSave),
                ) {
                    Text(
                        text = stringResource(
                            if (hasActiveCustomPreset && dirty) {
                                R.string.workflow_update_preset_short
                            } else {
                                R.string.workflow_save_short
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }

                if (template.hasSeedCandidate) {
                    val strategyLabel = when (seedStrategy) {
                        SeedStrategy.RANDOM -> stringResource(R.string.workflow_seed_random)
                        SeedStrategy.INCREMENT -> stringResource(R.string.workflow_seed_increment)
                        SeedStrategy.FIXED -> stringResource(R.string.workflow_seed_fixed)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onCycleSeedStrategy),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = NekoIcons.Dice,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = strategyLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }

            // 右侧：一体式【 − | N次 | ＋ | ▶ 提交生成 】胶囊主按钮
            val mainBg = if (submitEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val mainContent = if (submitEnabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = mainBg,
                contentColor = mainContent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                enabled = !submitting && batchCount > 1,
                                onClick = { onStepBatch(-1) },
                            )
                            .semantics { contentDescription = batchDecLabel }
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Text(
                        text = stringResource(R.string.workflow_batch_count_format, batchCount),
                        style = NekoMonoSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = mainContent,
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )

                    Box(
                        modifier = Modifier
                            .clickable(
                                enabled = !submitting && batchCount < 16,
                                onClick = { onStepBatch(1) },
                            )
                            .semantics { contentDescription = batchIncLabel }
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "＋",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(mainContent.copy(alpha = 0.25f)),
                    )

                    Row(
                        modifier = Modifier
                            .clickable(enabled = submitEnabled, onClick = onSubmit)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = mainContent,
                            )
                        } else {
                            Icon(
                                imageVector = NekoIcons.PlayFilled,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = stringResource(
                                if (submitting) {
                                    R.string.workflow_submitting
                                } else {
                                    R.string.workflow_submit
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubmitErrorCard(
    error: SubmitErrorUi,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = error.messageRes?.let { stringResource(it) }
                    ?: error.message?.asString()
                    ?: stringResource(R.string.error_workflow_failed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            error.batchStats?.let { stats ->
                Text(
                    text = stringResource(
                        R.string.workflow_batch_stats,
                        stats.success,
                        stats.failed,
                        stats.unknown,
                        stats.skipped,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (stats.unknownOutcome) {
                    Text(
                        text = stringResource(R.string.error_workflow_unknown_confirm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            error.extraMessages.forEach { message ->
                Text(
                    text = message.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            error.nodeErrors.forEach { nodeError ->
                Text(
                    text = if (nodeError.inputName != null) {
                        stringResource(
                            R.string.workflow_node_input_error_item,
                            nodeError.nodeId,
                            nodeError.inputName,
                            nodeError.message,
                        )
                    } else {
                        stringResource(
                            R.string.workflow_node_error_item,
                            nodeError.nodeId,
                            nodeError.message,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        }
    }
}

@Composable
internal fun SubmissionDialog(
    submission: WorkflowSubmitResult.Success,
    submittedCount: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (submittedCount > 1) {
                    stringResource(R.string.workflow_submit_batch_success, submittedCount)
                } else {
                    stringResource(R.string.workflow_submit_success)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.workflow_prompt_id, submission.promptId))
                submission.queueNumber?.let {
                    Text(stringResource(R.string.workflow_queue_number, it))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
internal fun RawJsonDialog(
    json: String?,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_raw_json_title)) },
        text = {
            if (json == null) {
                Text(stringResource(R.string.workflow_raw_json_missing))
            } else {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { json?.let(onCopy) },
                enabled = json != null,
            ) {
                Text(stringResource(R.string.workflow_raw_json_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workflow_close))
            }
        },
    )
}

