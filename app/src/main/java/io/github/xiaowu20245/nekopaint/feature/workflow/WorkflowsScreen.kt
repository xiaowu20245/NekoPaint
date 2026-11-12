package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCompatibility
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowInputSlot
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowIssueSeverity
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCapability
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowSlotKind
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoEmptyState
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkflowsScreen(
    onOpenTemplate: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: WorkflowsViewModel = viewModel(
        factory = WorkflowsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.errorRes?.let { stringResource(it) } ?: state.errorDetail
    val actionMessage = when (val notice = state.actionNotice) {
        is WorkflowActionNotice.Renamed -> stringResource(R.string.workflow_renamed, notice.name)
        is WorkflowActionNotice.Copied -> stringResource(R.string.workflow_copied, notice.name)
        WorkflowActionNotice.Deleted -> stringResource(R.string.workflow_deleted)
        is WorkflowActionNotice.QuickSubmitted -> stringResource(
            R.string.workflow_quick_submitted,
            notice.name,
            notice.count,
        )
        null -> null
    }
    var renameTarget by remember { mutableStateOf<WorkflowTemplate?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<WorkflowTemplate?>(null) }
    val listState = rememberLazyListState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshList()
    }

    LaunchedEffect(state.importedTemplateId) {
        state.importedTemplateId?.let { id ->
            viewModel.consumeImportedTemplate()
            onOpenTemplate(id)
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            if (state.actionNotice is WorkflowActionNotice.Copied) {
                listState.animateScrollToItem(0)
            }
            snackbarHostState.showSnackbar(actionMessage)
            viewModel.consumeActionNotice()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.templates.isEmpty() -> {
                NekoEmptyState(
                    icon = NekoIcons.WorkflowNodes,
                    title = stringResource(R.string.workflow_empty_title),
                    subtitle = stringResource(R.string.workflow_empty_desc),
                    actionLabel = stringResource(R.string.workflow_import),
                    onAction = { filePicker.launch(JSON_MIME_TYPES) },
                )
            }

            else -> {
                val availableModes = remember(state.templates) {
                    state.templates
                        .mapNotNull { it.compatibility?.mode }
                        .filter { it != WorkflowMode.UNKNOWN }
                        .distinct()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (availableModes.size > 1) {
                        item(key = "mode-filters") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.modeFilter == null,
                                    onClick = { viewModel.setModeFilter(null) },
                                    label = {
                                        Text("${stringResource(R.string.workflow_filter_all)} (${state.templates.size})")
                                    },
                                )
                                availableModes.forEach { mode ->
                                    FilterChip(
                                        selected = state.modeFilter == mode,
                                        onClick = { viewModel.setModeFilter(mode) },
                                        label = { Text(workflowModeLabel(mode)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = workflowModeIcon(mode),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    items(state.filteredTemplates, key = { it.id }) { template ->
                        WorkflowCard(
                            template = template,
                            busy = state.busyTemplateId == template.id,
                            running = state.runningTemplateId == template.id,
                            quickRunBlocked = state.runningTemplateId != null &&
                                state.runningTemplateId != template.id,
                            onClick = { onOpenTemplate(template.id) },
                            onQuickRun = { count -> viewModel.quickRun(template.id, count) },
                            onRename = {
                                renameTarget = template
                                renameName = template.name
                            },
                            onDuplicate = { viewModel.copyTemplate(template.id) },
                            onDelete = { deleteTarget = template },
                        )
                    }
                }
            }
        }

        if (state.importing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        if (!state.importing && state.templates.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { filePicker.launch(JSON_MIME_TYPES) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.workflow_import)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.workflow_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.workflow_rename_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameTemplate(target.id, renameName)
                        renameTarget = null
                    },
                    enabled = renameName.isNotBlank(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.workflow_delete_title)) },
            text = {
                Text(stringResource(R.string.workflow_delete_message, target.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTemplate(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.workflow_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkflowCard(
    template: WorkflowTemplate,
    busy: Boolean,
    running: Boolean,
    quickRunBlocked: Boolean,
    onClick: () -> Unit,
    onQuickRun: (Int) -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var batchDrawerExpanded by rememberSaveable(template.id) { mutableStateOf(false) }
    var quickBatchCount by rememberSaveable(template.id) { mutableIntStateOf(1) }
    val mode = template.compatibility?.mode ?: WorkflowMode.UNKNOWN
    val imageCount = template.compatibility?.imageSlots?.size ?: 0
    val maskCount = template.compatibility?.maskSlots?.size ?: 0
    val issueCount = template.compatibility?.issues?.count {
        it.severity == WorkflowIssueSeverity.ERROR || it.severity == WorkflowIssueSeverity.WARNING
    } ?: 0
    val hasMissingNodes = template.missingNodeTypes.isNotEmpty()
    val statusPillLabel = when {
        hasMissingNodes -> stringResource(
            R.string.workflow_missing_nodes,
            template.missingNodeTypes.size,
        )
        issueCount > 0 -> stringResource(R.string.workflow_compat_warn_short, issueCount)
        else -> null
    }
    val modePillColor = when {
        hasMissingNodes -> NekoTheme.colors.dangerContainer
        issueCount > 0 -> NekoTheme.colors.warningContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val modePillContentColor = when {
        hasMissingNodes -> NekoTheme.colors.onDangerContainer
        issueCount > 0 -> NekoTheme.colors.onWarningContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val promptPreview = template.primaryPromptPreview

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 第一层：左 48dp 模式图标锚点 + 中间标题与正向提示词/文件名摘要 + 右上角更多菜单
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = workflowModeIcon(mode),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!promptPreview.isNullOrBlank()) {
                        Text(
                            text = "\"$promptPreview\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = "${template.sourceFileName} · ${formatWorkflowTime(template.importedAt)}",
                            style = NekoMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 6.dp, end = 4.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.workflow_more_actions),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workflow_rename)) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workflow_duplicate)) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.workflow_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            // 第二层：左侧模式胶囊与精简指标 + 右侧分体式【▶ 运行 | ▾】按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = modePillColor,
                        contentColor = modePillContentColor,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            if (statusPillLabel != null) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Text(
                                text = workflowModeLabel(mode),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            if (statusPillLabel != null) {
                                Text(
                                    text = "· $statusPillLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Text(
                        text = "${stringResource(R.string.workflow_meta_selected_short, template.selectedCount)} · ${
                            stringResource(R.string.workflow_meta_nodes_short, template.nodeCount)
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val slotText = when {
                        imageCount > 0 && maskCount > 0 -> stringResource(
                            R.string.workflow_meta_image_mask_short,
                            imageCount,
                            maskCount,
                        )
                        imageCount > 0 -> stringResource(R.string.workflow_meta_image_only_short, imageCount)
                        maskCount > 0 -> stringResource(R.string.workflow_meta_mask_only_short, maskCount)
                        else -> null
                    }
                    if (slotText != null) {
                        Text(
                            text = "· $slotText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // 分体式【▶ 运行 | ▾】胶囊按钮
                SplitQuickRunButton(
                    running = running,
                    enabled = !running && !quickRunBlocked && !busy && template.selectedCount > 0,
                    batchCount = quickBatchCount,
                    drawerExpanded = batchDrawerExpanded,
                    onRun = { onQuickRun(quickBatchCount) },
                    onToggleDrawer = { batchDrawerExpanded = !batchDrawerExpanded },
                )
            }

            // 卡片内联快捷连发抽屉（仅连发次数与随机种子说明，不放预设切换，保持清爽）
            AnimatedVisibility(visible = batchDrawerExpanded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_quick_run_batch_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            listOf(1, 2, 4).forEach { count ->
                                val selected = quickBatchCount == count
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { quickBatchCount = count },
                                ) {
                                    Text(
                                        text = "×$count",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        if (template.hasSeedCandidate) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = NekoIcons.Dice,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stringResource(R.string.workflow_quick_run_auto_seed),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitQuickRunButton(
    running: Boolean,
    enabled: Boolean,
    batchCount: Int,
    drawerExpanded: Boolean,
    onRun: () -> Unit,
    onToggleDrawer: () -> Unit,
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onRun)
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = NekoIcons.PlayFilled,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = if (batchCount > 1) {
                        stringResource(R.string.workflow_quick_run_count, batchCount)
                    } else {
                        stringResource(R.string.workflow_quick_run)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(contentColor.copy(alpha = 0.25f)),
            )

            Box(
                modifier = Modifier
                    .clickable(onClick = onToggleDrawer)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (drawerExpanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = stringResource(R.string.workflow_quick_run_expand),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
internal fun workflowModeLabel(mode: WorkflowMode): String = when (mode) {
    WorkflowMode.TEXT_TO_IMAGE -> stringResource(R.string.workflow_mode_text_to_image)
    WorkflowMode.IMAGE_TO_IMAGE -> stringResource(R.string.workflow_mode_image_to_image)
    WorkflowMode.MASK_INPAINT -> stringResource(R.string.workflow_mode_mask_inpaint)
    WorkflowMode.AUTO_DETAIL -> stringResource(R.string.workflow_mode_auto_detail)
    WorkflowMode.VIDEO -> stringResource(R.string.workflow_mode_video)
    WorkflowMode.UNKNOWN -> stringResource(R.string.workflow_mode_unknown)
}

@Composable
internal fun workflowCapabilityLabel(capability: WorkflowCapability): String = when (capability) {
    WorkflowCapability.CONTROL_NET -> stringResource(R.string.workflow_capability_control_net)
    WorkflowCapability.UPSCALE -> stringResource(R.string.workflow_capability_upscale)
    WorkflowCapability.DETAILER -> stringResource(R.string.workflow_capability_detailer)
    WorkflowCapability.VIDEO -> stringResource(R.string.workflow_capability_video)
}

internal fun workflowModeIcon(mode: WorkflowMode): ImageVector = when (mode) {
    WorkflowMode.TEXT_TO_IMAGE -> NekoIcons.Sparkles
    WorkflowMode.IMAGE_TO_IMAGE -> NekoIcons.ImageFrame
    WorkflowMode.MASK_INPAINT -> NekoIcons.BrushMask
    WorkflowMode.AUTO_DETAIL -> NekoIcons.TargetCrosshair
    WorkflowMode.VIDEO -> NekoIcons.PlayFilled
    WorkflowMode.UNKNOWN -> NekoIcons.WorkflowNodes
}

private val JSON_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
)

private fun formatWorkflowTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun WorkflowCardPreview() {
    NekoPaintTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            WorkflowCard(
                template = workflowPreviewTemplate(),
                busy = false,
                running = false,
                quickRunBlocked = false,
                onClick = {},
                onQuickRun = {},
                onRename = {},
                onDuplicate = {},
                onDelete = {},
            )
        }
    }
}

private fun workflowPreviewTemplate(): WorkflowTemplate {
    val selectedCandidates = listOf(
        ParameterCandidate(
            id = "6:text",
            nodeId = "6",
            inputName = "text",
            label = "正向提示词",
            type = ParameterType.MULTILINE_TEXT,
            group = ParameterGroup.PROMPT,
            defaultValue = "cute fox girl",
            value = "1girl, silver hair, soft lighting, detailed eyes",
            selected = true,
            classType = "CLIPTextEncode",
        ),
        ParameterCandidate(
            id = "3:steps",
            nodeId = "3",
            inputName = "steps",
            label = "采样步数",
            type = ParameterType.INT,
            group = ParameterGroup.SAMPLING,
            defaultValue = "20",
            value = "28",
            selected = true,
            classType = "KSampler",
        ),
        ParameterCandidate(
            id = "3:seed",
            nodeId = "3",
            inputName = "seed",
            label = "随机种子",
            type = ParameterType.INT,
            group = ParameterGroup.SAMPLING,
            defaultValue = "1",
            value = "123456789",
            selected = true,
            classType = "KSampler",
        ),
    )
    return WorkflowTemplate(
        id = "preview",
        name = "局部蒙版重绘 Inpaint",
        sourceFileName = "inpaint_sdxl_api.json",
        sourceHash = "preview",
        importedAt = 1_760_000_000_000L,
        nodeCount = 12,
        reachableNodeCount = 12,
        outputNodeIds = listOf("9"),
        catalogChecked = true,
        candidates = selectedCandidates,
        compatibility = WorkflowCompatibility(
            mode = WorkflowMode.MASK_INPAINT,
            imageSlots = listOf(
                WorkflowInputSlot(
                    nodeId = "11",
                    inputName = "image",
                    title = "输入图片",
                    kind = WorkflowSlotKind.IMAGE,
                    currentValue = "example.png",
                ),
            ),
            maskSlots = listOf(
                WorkflowInputSlot(
                    nodeId = "14",
                    inputName = "mask",
                    title = "输入蒙版",
                    kind = WorkflowSlotKind.MASK,
                    currentValue = "mask_01.png",
                ),
            ),
            catalogChecked = true,
            analyzerVersion = 1,
        ),
        presets = listOf(
            WorkflowPreset(id = "preset-preview", name = "白发少女光影"),
        ),
        activePresetId = "preset-preview",
    )
}
