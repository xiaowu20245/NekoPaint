package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCompatibility
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowIssueSeverity
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme

@Composable
internal fun ManualBindingDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        nodeId: String,
        inputName: String,
        label: String,
        type: ParameterType,
        group: ParameterGroup,
        value: String,
    ) -> Unit,
) {
    var nodeId by rememberSaveable { mutableStateOf("") }
    var inputName by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(ParameterType.UNKNOWN.name) }
    var groupName by rememberSaveable { mutableStateOf(ParameterGroup.OTHER.name) }
    val type = ParameterType.entries.firstOrNull { it.name == typeName } ?: ParameterType.UNKNOWN
    val group = ParameterGroup.entries.firstOrNull { it.name == groupName } ?: ParameterGroup.OTHER

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_manual_binding_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = nodeId,
                    onValueChange = { nodeId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.workflow_manual_binding_node)) },
                )
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.workflow_manual_binding_input)) },
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.workflow_manual_binding_label)) },
                )
                Text(
                    text = stringResource(R.string.workflow_manual_binding_type),
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ParameterType.entries.forEach { option ->
                        FilterChip(
                            selected = typeName == option.name,
                            onClick = { typeName = option.name },
                            label = { Text(option.name) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.workflow_manual_binding_group),
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ParameterGroup.entries.forEach { option ->
                        FilterChip(
                            selected = groupName == option.name,
                            onClick = { groupName = option.name },
                            label = { Text(groupLabel(option)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.workflow_manual_binding_value)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nodeId, inputName, label, type, group, value) },
                enabled = nodeId.isNotBlank() && inputName.isNotBlank() && label.isNotBlank(),
            ) {
                Text(stringResource(R.string.workflow_manual_binding_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun QuickFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
internal fun CompactOverviewStrip(
    template: WorkflowTemplate,
    expanded: Boolean,
    checkingNodes: Boolean,
    onToggleExpanded: () -> Unit,
    onShowRawJson: () -> Unit,
    onInstallMissingNodes: () -> Unit,
) {
    val compatibility = template.compatibility
    val mode = compatibility?.mode ?: WorkflowMode.UNKNOWN
    val imageCount = compatibility?.imageSlots?.size ?: 0
    val maskCount = compatibility?.maskSlots?.size ?: 0
    val issues = compatibility?.issues.orEmpty()
    val hasError = template.missingNodeTypes.isNotEmpty() ||
        issues.any { it.severity == WorkflowIssueSeverity.ERROR }
    val hasWarning = issues.any { it.severity == WorkflowIssueSeverity.WARNING }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggleExpanded),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = workflowModeIcon(mode),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = workflowModeLabel(mode),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }

                    val statusSummary = buildString {
                        if (template.missingNodeTypes.isNotEmpty()) {
                            append(
                                stringResource(
                                    R.string.workflow_missing_nodes,
                                    template.missingNodeTypes.size,
                                ),
                            )
                        } else if (hasError || hasWarning) {
                            append(stringResource(R.string.workflow_compat_warn_short, issues.size))
                        } else {
                            append(stringResource(R.string.workflow_meta_nodes_short, template.nodeCount))
                        }
                        if (imageCount > 0 || maskCount > 0) {
                            append(" · ")
                            when {
                                imageCount > 0 && maskCount > 0 -> append(
                                    stringResource(
                                        R.string.workflow_meta_image_mask_short,
                                        imageCount,
                                        maskCount,
                                    ),
                                )
                                imageCount > 0 -> append(
                                    stringResource(R.string.workflow_meta_image_only_short, imageCount),
                                )
                                else -> append(
                                    stringResource(R.string.workflow_meta_mask_only_short, maskCount),
                                )
                            }
                        }
                    }

                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            hasError -> MaterialTheme.colorScheme.error
                            hasWarning -> NekoTheme.colors.onWarningContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (checkingNodes) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleExpanded),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_overview_toggle),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Outlined.KeyboardArrowUp
                                } else {
                                    Icons.Outlined.KeyboardArrowDown
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShowRawJson),
                    ) {
                        Text(
                            text = "{ } ${stringResource(R.string.workflow_json_short)}",
                            style = NekoMonoSmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (template.missingNodeTypes.isNotEmpty()) {
                OutlinedButton(
                    onClick = onInstallMissingNodes,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.workflow_install_missing_nodes))
                }
            }

            AnimatedVisibility(visible = expanded) {
                OverviewDetailPanel(
                    template = template,
                    compatibility = compatibility,
                )
            }
        }
    }
}

@Composable
private fun OverviewDetailPanel(
    template: WorkflowTemplate,
    compatibility: WorkflowCompatibility?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Text(
            text = "${template.name} (${template.sourceFileName})",
            style = NekoMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${stringResource(R.string.workflow_nodes)} ${template.nodeCount} · ${
                stringResource(R.string.workflow_reachable)
            } ${template.reachableNodeCount} · ${
                stringResource(R.string.workflow_selected)
            } ${template.selectedCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (template.outputNodeIds.isNotEmpty()) {
            Text(
                text = "${stringResource(R.string.workflow_outputs)}: #${template.outputNodeIds.joinToString(", #")}",
                style = NekoMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (template.missingNodeTypes.isNotEmpty()) {
            Text(
                text = template.missingNodeTypes.joinToString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (template.nodeSummaries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.workflow_node_list_title),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            template.nodeSummaries.forEach { node ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "#${node.id}",
                        style = NekoMonoSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (node.missing) {
                            MaterialTheme.colorScheme.error
                        } else if (!node.reachable) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (node.missing) {
                        Text(
                            text = stringResource(R.string.workflow_node_missing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (!node.reachable) {
                        Text(
                            text = stringResource(R.string.workflow_node_unreachable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        compatibility?.capabilities?.takeIf { it.isNotEmpty() }?.let { capabilities ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                capabilities.forEach { capability ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = workflowCapabilityLabel(capability),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        compatibility?.imageSlots?.forEach { slot ->
            Text(
                text = "• 图片槽 #${slot.nodeId} · ${slot.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        compatibility?.maskSlots?.forEach { slot ->
            Text(
                text = "• 蒙版槽 #${slot.nodeId} · ${slot.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        compatibility?.issues?.take(5)?.forEach { issue ->
            Text(
                text = "• ${issue.message}",
                style = MaterialTheme.typography.labelSmall,
                color = when (issue.severity) {
                    WorkflowIssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                    WorkflowIssueSeverity.WARNING -> NekoTheme.colors.onWarningContainer
                    WorkflowIssueSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

