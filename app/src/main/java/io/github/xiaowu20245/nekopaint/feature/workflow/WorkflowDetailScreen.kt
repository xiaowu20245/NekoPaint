package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.BindingSource
import io.github.xiaowu20245.nekopaint.core.model.workflow.DetectionConfidence
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarAction
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun WorkflowDetailScreen(
    templateId: String,
    onTopBarActionChange: (NekoTopBarAction?) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: WorkflowDetailViewModel = viewModel(
        factory = WorkflowDetailViewModel.factory(app.container, templateId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val simpleListState = rememberLazyListState()
    val advancedListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val template = state.template
    val savedMessage = stringResource(R.string.workflow_saved)
    val errorMessage = state.errorRes?.let { stringResource(it) }
    val submitLabel = stringResource(R.string.workflow_submit)
    val isScrolled by remember {
        derivedStateOf {
            val listState = if (pagerState.currentPage == 1) {
                advancedListState
            } else {
                simpleListState
            }
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setAdvanced(pagerState.currentPage == 1)
    }

    LaunchedEffect(isScrolled, state.submitting, template?.selectedCount, submitLabel) {
        if (isScrolled && template != null) {
            onTopBarActionChange(
                NekoTopBarAction(
                    label = submitLabel,
                    enabled = !state.submitting && template.selectedCount > 0,
                    onClick = viewModel::submit,
                ),
            )
        } else {
            onTopBarActionChange(null)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onTopBarActionChange(null) }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.clearSaved()
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
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

            template == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.workflow_error_generic))
                }
            }

            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val advancedPage = page == 1
                    val simpleVisible = if (state.editing) {
                        template.candidates.filter { it.id in state.editSnapshotIds }
                    } else {
                        template.selectedCandidates
                    }
                    WorkflowPage(
                        template = template,
                        advanced = advancedPage,
                        editing = state.editing && !advancedPage,
                        dirty = state.dirty,
                        saving = state.saving,
                        submitting = state.submitting,
                        fieldErrors = state.fieldErrors,
                        submitError = state.submitError,
                        visibleCandidates = if (advancedPage) {
                            template.candidates
                        } else {
                            simpleVisible
                        },
                        listState = if (advancedPage) advancedListState else simpleListState,
                        onSelectMode = { targetPage ->
                            viewModel.setAdvanced(targetPage == 1)
                            scope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        },
                        onStartEditing = viewModel::startEditing,
                        onFinishEditing = viewModel::finishEditing,
                        onToggleCandidate = viewModel::toggleCandidate,
                        onValueChange = viewModel::onValueChange,
                        onSave = viewModel::save,
                        onSubmit = viewModel::submit,
                        onClearSubmitError = viewModel::clearSubmitError,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }

    state.submission?.let { submission ->
        SubmissionDialog(
            submission = submission,
            onDismiss = viewModel::clearSubmission,
        )
    }
}

@Composable
private fun WorkflowPage(
    template: WorkflowTemplate,
    advanced: Boolean,
    editing: Boolean,
    dirty: Boolean,
    saving: Boolean,
    submitting: Boolean,
    fieldErrors: Map<String, FieldError>,
    submitError: SubmitErrorUi?,
    visibleCandidates: List<ParameterCandidate>,
    listState: LazyListState,
    onSelectMode: (Int) -> Unit,
    onStartEditing: () -> Unit,
    onFinishEditing: () -> Unit,
    onToggleCandidate: (String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onSubmit: () -> Unit,
    onClearSubmitError: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SummaryCard(template)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = !advanced,
                        onClick = { onSelectMode(0) },
                        label = { Text(stringResource(R.string.workflow_simple_mode)) },
                    )
                    FilterChip(
                        selected = advanced,
                        onClick = { onSelectMode(1) },
                        label = { Text(stringResource(R.string.workflow_advanced_mode)) },
                    )
                    if (!advanced) {
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = if (editing) onFinishEditing else onStartEditing,
                        ) {
                            Text(
                                stringResource(
                                    if (editing) R.string.workflow_done else R.string.workflow_edit,
                                ),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dirty) {
                        OutlinedButton(
                            onClick = onSave,
                            enabled = !saving && !submitting,
                        ) {
                            Text(stringResource(R.string.workflow_save))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onSubmit,
                        enabled = !submitting && template.selectedCount > 0,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            stringResource(
                                if (submitting) {
                                    R.string.workflow_submitting
                                } else {
                                    R.string.workflow_submit
                                },
                            ),
                        )
                    }
                }
            }
        }

        submitError?.let { error ->
            item {
                SubmitErrorCard(
                    error = error,
                    onDismiss = onClearSubmitError,
                )
            }
        }

        if (visibleCandidates.isEmpty()) {
            item {
                Text(
                    text = stringResource(
                        if (advanced) {
                            R.string.workflow_no_candidates
                        } else {
                            R.string.workflow_no_selected
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        var lastGroup: ParameterGroup? = null
        visibleCandidates.forEach { candidate ->
            if (candidate.group != lastGroup) {
                item(key = "group-${candidate.group}") {
                    Text(
                        text = groupLabel(candidate.group),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                lastGroup = candidate.group
            }
            item(key = candidate.id) {
                CandidateRow(
                    candidate = candidate,
                    error = fieldErrors[candidate.id],
                    showCheckbox = advanced || editing,
                    onToggle = { onToggleCandidate(candidate.id) },
                    onValueChange = { onValueChange(candidate.id, it) },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.workflow_v02b_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SubmitErrorCard(
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
                    ?: error.message
                    ?: stringResource(R.string.workflow_submit_failed, "未知错误"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            error.nodeErrors.forEach { nodeError ->
                Text(
                    text = stringResource(
                        R.string.workflow_node_error_item,
                        nodeError.nodeId,
                        nodeError.message,
                    ),
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
private fun SubmissionDialog(
    submission: WorkflowSubmitResult.Success,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_submit_success)) },
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
private fun SummaryCard(template: WorkflowTemplate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = template.sourceFileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetaValue(stringResource(R.string.workflow_nodes), template.nodeCount.toString())
                MetaValue(stringResource(R.string.workflow_reachable), template.reachableNodeCount.toString())
                MetaValue(stringResource(R.string.workflow_selected), template.selectedCount.toString())
            }
            if (template.outputNodeIds.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.workflow_outputs)}: ${template.outputNodeIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (template.missingNodeTypes.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.workflow_missing_nodes,
                            template.missingNodeTypes.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = template.missingNodeTypes.joinToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!template.catalogChecked) {
                Text(
                    text = stringResource(R.string.workflow_catalog_not_checked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetaValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: ParameterCandidate,
    error: FieldError?,
    showCheckbox: Boolean,
    onToggle: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = candidate.selected,
                    onCheckedChange = { onToggle() },
                    enabled = !candidate.readOnly,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = candidate.label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    ConfidenceBadge(candidate.confidence)
                }
                Text(
                    text = stringResource(
                        R.string.workflow_node_label,
                        candidate.nodeId,
                        candidate.classType.ifBlank { candidate.inputName },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = typeLabel(candidate.type) +
                        if (candidate.source == BindingSource.UPSTREAM) {
                            " · " + stringResource(R.string.workflow_upstream)
                        } else {
                            ""
                        } +
                        if (candidate.readOnly) {
                            " · " + stringResource(R.string.workflow_read_only)
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidate.readOnly) {
                    Text(
                        text = candidate.defaultValue.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ParameterField(
                        candidate = candidate,
                        error = error,
                        onValueChange = onValueChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterField(
    candidate: ParameterCandidate,
    error: FieldError?,
    onValueChange: (String) -> Unit,
) {
    val errorText = fieldErrorText(error)
    when (candidate.type) {
        ParameterType.BOOLEAN -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        if (candidate.value.equals("true", ignoreCase = true)) {
                            R.string.workflow_on
                        } else {
                            R.string.workflow_off
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = candidate.value.equals("true", ignoreCase = true),
                    onCheckedChange = { onValueChange(if (it) "true" else "false") },
                )
            }
        }

        ParameterType.ENUM -> {
            if (candidate.constraints.options.isEmpty()) {
                OutlinedTextField(
                    value = candidate.value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                )
            } else {
                EnumField(
                    value = candidate.value,
                    options = candidate.constraints.options,
                    errorText = errorText,
                    onValueChange = onValueChange,
                )
            }
        }

        ParameterType.INT,
        ParameterType.FLOAT,
        -> {
            val presets = numericPresets(candidate)
            val isSeed = candidate.inputName in setOf("seed", "noise_seed")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = candidate.value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (candidate.type == ParameterType.INT) {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Decimal
                        },
                    ),
                )
                if (isSeed) {
                    TextButton(
                        onClick = {
                            onValueChange(Random.nextLong(0L, Long.MAX_VALUE).toString())
                        },
                    ) {
                        Text(stringResource(R.string.workflow_random))
                    }
                }
                if (presets.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = candidate.value == preset,
                                onClick = { onValueChange(preset) },
                                label = { Text(preset) },
                            )
                        }
                    }
                }
            }
        }

        ParameterType.MULTILINE_TEXT,
        ParameterType.TEXT,
        ParameterType.IMAGE,
        ParameterType.UNKNOWN,
        -> OutlinedTextField(
            value = candidate.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = if (candidate.type == ParameterType.MULTILINE_TEXT) 3 else 1,
            maxLines = if (candidate.type == ParameterType.MULTILINE_TEXT) 8 else 3,
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumField(
    value: String,
    options: List<String>,
    errorText: String?,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun fieldErrorText(error: FieldError?): String? = when (error) {
    null -> null
    is FieldError.Local -> stringResource(error.messageRes)
    is FieldError.Server -> error.message
}

@Composable
private fun ConfidenceBadge(confidence: DetectionConfidence) {
    val (label, color) = when (confidence) {
        DetectionConfidence.HIGH -> stringResource(R.string.workflow_confidence_high) to MaterialTheme.colorScheme.primary
        DetectionConfidence.MEDIUM -> stringResource(R.string.workflow_confidence_medium) to MaterialTheme.colorScheme.tertiary
        DetectionConfidence.LOW -> stringResource(R.string.workflow_confidence_low) to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun groupLabel(group: ParameterGroup): String = when (group) {
    ParameterGroup.PROMPT -> stringResource(R.string.workflow_group_prompt)
    ParameterGroup.IMAGE -> stringResource(R.string.workflow_group_image)
    ParameterGroup.SAMPLING -> stringResource(R.string.workflow_group_sampling)
    ParameterGroup.MODEL -> stringResource(R.string.workflow_group_model)
    ParameterGroup.ADVANCED -> stringResource(R.string.workflow_group_advanced)
    ParameterGroup.OTHER -> stringResource(R.string.workflow_group_other)
}

@Composable
private fun typeLabel(type: ParameterType): String = when (type) {
    ParameterType.TEXT -> stringResource(R.string.workflow_type_text)
    ParameterType.MULTILINE_TEXT -> stringResource(R.string.workflow_type_multiline)
    ParameterType.INT -> stringResource(R.string.workflow_type_int)
    ParameterType.FLOAT -> stringResource(R.string.workflow_type_float)
    ParameterType.BOOLEAN -> stringResource(R.string.workflow_type_boolean)
    ParameterType.ENUM -> stringResource(R.string.workflow_type_enum)
    ParameterType.IMAGE -> stringResource(R.string.workflow_type_image)
    ParameterType.UNKNOWN -> stringResource(R.string.workflow_type_unknown)
}

private fun numericPresets(candidate: ParameterCandidate): List<String> = when {
    candidate.inputName == "steps" -> listOf("10", "20", "30", "40")
    candidate.inputName == "cfg" -> listOf("1", "3", "5", "7", "8", "10")
    candidate.inputName == "denoise" -> listOf("0.2", "0.4", "0.6", "0.8", "1.0")
    candidate.inputName == "batch_size" -> listOf("1", "2", "4")
    candidate.inputName == "scale_by" -> listOf("1.0", "1.25", "1.5", "2.0")
    candidate.label.contains("宽度") -> listOf("512", "768", "1024", "1280", "1536", "1920")
    candidate.label.contains("高度") -> listOf("512", "768", "1024", "1280", "1536", "1920")
    else -> emptyList()
}
