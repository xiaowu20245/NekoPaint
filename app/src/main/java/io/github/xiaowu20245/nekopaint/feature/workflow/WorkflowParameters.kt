package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall

@Composable
internal fun WorkflowPage(
    template: WorkflowTemplate,
    advanced: Boolean,
    editing: Boolean,
    fieldErrors: Map<String, FieldError>,
    submitError: SubmitErrorUi?,
    visibleCandidates: List<ParameterCandidate>,
    listState: LazyListState,
    onToggleCandidate: (String) -> Unit,
    onValueChange: (String, String) -> Unit,
    promptTagGroups: List<PromptTagGroup>,
    onOpenPromptEditor: (String) -> Unit,
    onSetPrimaryPrompt: (String?) -> Unit = {},
    uploadingCandidateId: String?,
    onPickImage: (String) -> Unit,
    onDrawMask: (String) -> Unit,
    onClearSubmitError: () -> Unit,
    onOpenManualBinding: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<ParameterGroup?>(null) }
    var quickFilter by remember { mutableStateOf(ParameterQuickFilter.ALL) }

    val filteredCandidates = if (advanced) {
        val queryMatched = visibleCandidates.filter { candidate ->
            val matchesGroup = groupFilter == null || candidate.group == groupFilter
            val query = searchQuery.trim()
            val matchesQuery = query.isEmpty() ||
                candidate.label.contains(query, ignoreCase = true) ||
                candidate.nodeId.contains(query, ignoreCase = true) ||
                candidate.classType.contains(query, ignoreCase = true) ||
                candidate.inputName.contains(query, ignoreCase = true) ||
                candidate.value.contains(query, ignoreCase = true)
            matchesGroup && matchesQuery
        }
        ParameterFilterPolicy.apply(queryMatched, fieldErrors, quickFilter)
    } else {
        visibleCandidates
    }
    val maskNodeIds = template.compatibility
        ?.maskSlots
        ?.map { it.nodeId }
        ?.toSet()
        .orEmpty()
    val groupedCandidates = remember(filteredCandidates) {
        filteredCandidates.groupBy { it.group }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (advanced) {
            item(key = "advanced-filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.workflow_search_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = groupFilter == null,
                            onClick = { groupFilter = null },
                            label = { Text(stringResource(R.string.workflow_filter_all)) },
                        )
                        ParameterGroup.entries.forEach { group ->
                            FilterChip(
                                selected = groupFilter == group,
                                onClick = { groupFilter = group },
                                label = { Text(groupLabel(group)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuickFilterChip(
                            label = stringResource(R.string.workflow_filter_selected),
                            selected = quickFilter == ParameterQuickFilter.SELECTED,
                            onClick = {
                                quickFilter = if (quickFilter == ParameterQuickFilter.SELECTED) {
                                    ParameterQuickFilter.ALL
                                } else {
                                    ParameterQuickFilter.SELECTED
                                }
                            },
                        )
                        QuickFilterChip(
                            label = stringResource(R.string.workflow_filter_read_only),
                            selected = quickFilter == ParameterQuickFilter.READ_ONLY,
                            onClick = {
                                quickFilter = if (quickFilter == ParameterQuickFilter.READ_ONLY) {
                                    ParameterQuickFilter.ALL
                                } else {
                                    ParameterQuickFilter.READ_ONLY
                                }
                            },
                        )
                        QuickFilterChip(
                            label = stringResource(R.string.workflow_filter_errors),
                            selected = quickFilter == ParameterQuickFilter.ERROR,
                            onClick = {
                                quickFilter = if (quickFilter == ParameterQuickFilter.ERROR) {
                                    ParameterQuickFilter.ALL
                                } else {
                                    ParameterQuickFilter.ERROR
                                }
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenManualBinding,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.workflow_manual_binding_open))
                    }
                }
            }
        }

        submitError?.let { error ->
            item(key = "submit-error") {
                SubmitErrorCard(
                    error = error,
                    onDismiss = onClearSubmitError,
                )
            }
        }

        if (filteredCandidates.isEmpty()) {
            item(key = "empty-candidates") {
                Text(
                    text = stringResource(
                        if (advanced) {
                            if (visibleCandidates.isEmpty()) {
                                R.string.workflow_no_candidates
                            } else {
                                R.string.workflow_filter_no_match
                            }
                        } else {
                            R.string.workflow_no_selected
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }

        // 4. 分组大容器 + 短参数 2x2 双列 Bento 网格
        groupedCandidates.forEach { (group, candidatesInGroup) ->
            item(key = "group-section-${group.name}") {
                ParameterGroupSection(
                    group = group,
                    candidates = candidatesInGroup,
                    fieldErrors = fieldErrors,
                    showCheckbox = advanced || editing,
                    maskNodeIds = maskNodeIds,
                    uploadingCandidateId = uploadingCandidateId,
                    promptTagGroups = promptTagGroups,
                    onOpenPromptEditor = onOpenPromptEditor,
                    primaryPromptCandidateId = template.primaryPromptCandidateId,
                    onSetPrimaryPrompt = onSetPrimaryPrompt,
                    onToggleCandidate = onToggleCandidate,
                    onValueChange = onValueChange,
                    onPickImage = onPickImage,
                    onDrawMask = onDrawMask,
                )
            }
        }
    }
}

/**
 * 方案 B：分组大容器 + 短数值参数 2x2 双列 Bento 网格。
 */
@Composable
internal fun ParameterGroupSection(
    group: ParameterGroup,
    candidates: List<ParameterCandidate>,
    fieldErrors: Map<String, FieldError>,
    showCheckbox: Boolean,
    maskNodeIds: Set<String>,
    uploadingCandidateId: String?,
    promptTagGroups: List<PromptTagGroup>,
    onOpenPromptEditor: (String) -> Unit,
    primaryPromptCandidateId: String? = null,
    onSetPrimaryPrompt: (String?) -> Unit = {},
    onToggleCandidate: (String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onPickImage: (String) -> Unit,
    onDrawMask: (String) -> Unit,
) {
    val nodeSummary = remember(candidates) {
        candidates.map { "#${it.nodeId}" }.distinct().take(3).joinToString(", ")
    }

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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = groupLabel(group),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = nodeSummary,
                    style = NekoMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!showCheckbox && group == ParameterGroup.PROMPT) {
                candidates.forEach { promptCandidate ->
                    val isPrimary = promptCandidate.id == primaryPromptCandidateId
                    PromptSummaryCard(
                        candidate = promptCandidate,
                        groups = promptTagGroups,
                        isPrimary = isPrimary,
                        canSetPrimary = !promptCandidate.label.contains("负"),
                        onTogglePrimary = {
                            onSetPrimaryPrompt(
                                if (isPrimary) null else promptCandidate.id,
                            )
                        },
                        onOpen = { onOpenPromptEditor(promptCandidate.id) },
                    )
                }
            } else if (!showCheckbox) {
                val compactNumerics = candidates.filter { isCompactNumericCandidate(it) }
                val fullWidthItems = candidates.filterNot { isCompactNumericCandidate(it) }

                compactNumerics.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { numericCandidate ->
                            Box(modifier = Modifier.weight(1f)) {
                                CompactNumericCell(
                                    candidate = numericCandidate,
                                    error = fieldErrors[numericCandidate.id],
                                    onValueChange = { onValueChange(numericCandidate.id, it) },
                                )
                            }
                        }
                    }
                }

                fullWidthItems.forEach { current ->
                    CandidateRow(
                        candidate = current,
                        error = fieldErrors[current.id],
                        showCheckbox = false,
                        onToggle = { onToggleCandidate(current.id) },
                        onValueChange = { onValueChange(current.id, it) },
                        uploading = uploadingCandidateId == current.id,
                        onPickImage = { onPickImage(current.id) },
                        isMaskSlot = current.nodeId in maskNodeIds ||
                            current.classType == "LoadImageMask",
                        onDrawMask = { onDrawMask(current.id) },
                    )
                }
            } else {
                candidates.forEach { current ->
                    CandidateRow(
                        candidate = current,
                        error = fieldErrors[current.id],
                        showCheckbox = true,
                        onToggle = { onToggleCandidate(current.id) },
                        onValueChange = { onValueChange(current.id, it) },
                        uploading = uploadingCandidateId == current.id,
                        onPickImage = { onPickImage(current.id) },
                        isMaskSlot = current.nodeId in maskNodeIds ||
                            current.classType == "LoadImageMask",
                        onDrawMask = { onDrawMask(current.id) },
                    )
                }
            }
        }
    }
}

private fun isCompactNumericCandidate(candidate: ParameterCandidate): Boolean =
    !candidate.readOnly &&
        (candidate.type == ParameterType.INT || candidate.type == ParameterType.FLOAT) &&
        candidate.inputName !in WorkflowTemplate.SEED_INPUT_NAMES

@Composable
private fun CompactNumericCell(
    candidate: ParameterCandidate,
    error: FieldError?,
    onValueChange: (String) -> Unit,
) {
    val errorText = fieldErrorText(error)
    val presets = numericPresets(candidate)

    ParameterControlShell(
        label = candidate.label,
        nodeLabel = "#${candidate.nodeId}",
        errorText = errorText,
        compact = true,
    ) {
        OutlinedTextField(
            value = candidate.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = NekoMonoSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (candidate.type == ParameterType.INT) {
                    KeyboardType.Number
                } else {
                    KeyboardType.Decimal
                },
            ),
            trailingIcon = if (presets.isNotEmpty()) {
                {
                    NumericPresetDropdown(
                        presets = presets,
                        onSelect = onValueChange,
                    )
                }
            } else {
                null
            },
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
    uploading: Boolean,
    onPickImage: () -> Unit,
    isMaskSlot: Boolean,
    onDrawMask: () -> Unit,
) {
    val errorText = fieldErrorText(error)
    ParameterControlShell(
        label = candidate.label,
        nodeLabel = "#${candidate.nodeId} · " +
            candidate.classType.ifBlank { candidate.inputName },
        errorText = errorText,
        readOnlyValue = if (candidate.readOnly) {
            candidate.defaultValue.ifBlank { "—" }
        } else {
            null
        },
        leading = if (showCheckbox) {
            {
                Checkbox(
                    checked = candidate.selected,
                    onCheckedChange = { onToggle() },
                    enabled = !candidate.readOnly,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        } else {
            null
        },
    ) {
        ParameterField(
            candidate = candidate,
            error = error,
            onValueChange = onValueChange,
            uploading = uploading,
            onPickImage = onPickImage,
            isMaskSlot = isMaskSlot,
            onDrawMask = onDrawMask,
        )
    }
}

