package io.github.xiaowu20245.nekopaint.feature.workflow

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCompatibility
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowIssueSeverity
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarAction
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun WorkflowDetailScreen(
    templateId: String,
    onTopBarActionChange: (NekoTopBarAction?) -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onBack: () -> Unit = {},
    onBackHandlerChange: ((() -> Boolean)?) -> Unit = {},
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
    val currentListState = if (pagerState.currentPage == 1) advancedListState else simpleListState
    val headerCollapsed by remember(currentListState) {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 ||
                currentListState.firstVisibleItemScrollOffset > 24
        }
    }
    val template = state.template
    val savedMessage = stringResource(R.string.workflow_saved)
    val errorMessage = state.errorRes?.let { stringResource(it) }
    val rawJsonCopiedMessage = stringResource(R.string.workflow_raw_json_copied)
    val presetNoticeMessage = when (val notice = state.presetNotice) {
        is PresetNotice.Saved -> stringResource(R.string.workflow_preset_saved_notice, notice.name)
        is PresetNotice.Updated -> stringResource(R.string.workflow_preset_updated_notice, notice.name)
        PresetNotice.Deleted -> stringResource(R.string.workflow_preset_deleted_notice)
        null -> null
    }
    var pendingImageCandidateId by remember { mutableStateOf<String?>(null) }
    var maskEditorCandidateId by remember { mutableStateOf<String?>(null) }
    var promptEditorCandidateId by remember { mutableStateOf<String?>(null) }
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var showRenamePresetDialog by remember { mutableStateOf(false) }
    var renamePresetName by remember { mutableStateOf("") }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(state.dirty)

    DisposableEffect(Unit) {
        // 外部 TopAppBar 的返回箭头也走同一套未保存拦截。
        onBackHandlerChange {
            if (currentDirty) {
                showUnsavedDialog = true
                true
            } else {
                false
            }
        }
        onDispose { onBackHandlerChange(null) }
    }

    BackHandler(enabled = state.dirty && !showUnsavedDialog) {
        showUnsavedDialog = true
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val candidateId = pendingImageCandidateId
        pendingImageCandidateId = null
        if (uri != null && candidateId != null) {
            viewModel.uploadImage(candidateId, uri)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setAdvanced(pagerState.currentPage == 1)
    }

    // 底部已常驻一体式提交栏，不再需要在 TopAppBar 重复塞提交按钮
    DisposableEffect(Unit) {
        onTopBarActionChange(null)
        onDispose { onTopBarActionChange(null) }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.clearSaved()
        }
    }
    LaunchedEffect(presetNoticeMessage) {
        if (presetNoticeMessage != null) {
            snackbarHostState.showSnackbar(presetNoticeMessage)
            viewModel.clearPresetNotice()
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    val uploadedImageMessage = state.uploadedImageName?.let { fileName ->
        stringResource(R.string.workflow_image_uploaded, fileName)
    }
    val imageUploadErrorMessage = state.imageUploadError?.let { message ->
        stringResource(R.string.workflow_image_upload_failed, message)
    }
    LaunchedEffect(uploadedImageMessage) {
        if (uploadedImageMessage != null) {
            if (maskEditorCandidateId != null) {
                maskEditorCandidateId = null
            }
            snackbarHostState.showSnackbar(uploadedImageMessage)
            viewModel.clearImageUploadNotice()
        }
    }
    LaunchedEffect(imageUploadErrorMessage) {
        if (imageUploadErrorMessage != null) {
            snackbarHostState.showSnackbar(imageUploadErrorMessage)
            viewModel.clearImageUploadNotice()
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
                var overviewExpanded by rememberSaveable(template.id) {
                    mutableStateOf(false)
                }
                val activePresetName = template.presets
                    .firstOrNull { it.id == template.activePresetId }
                    ?.name
                    ?: stringResource(R.string.workflow_preset_default)
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部固定区：未滚动时完整展示；向下滚动后自动收起概览与预设。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedVisibility(visible = !headerCollapsed) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CompactOverviewStrip(
                                    template = template,
                                    expanded = overviewExpanded,
                                    onToggleExpanded = { overviewExpanded = !overviewExpanded },
                                    onShowRawJson = viewModel::showRawJson,
                                )
                                WorkflowPresetSection(
                                    template = template,
                                    dirty = state.dirty,
                                    onSelectPreset = viewModel::selectPreset,
                                    onRequestAddPreset = {
                                        newPresetName = ""
                                        showAddPresetDialog = true
                                    },
                                    onRequestRenamePreset = { currentName ->
                                        renamePresetName = currentName
                                        showRenamePresetDialog = true
                                    },
                                    onDeleteActivePreset = viewModel::deleteActivePreset,
                                )
                            }
                        }
                        ModeControlRow(
                            selectedCount = template.selectedCount,
                            totalCount = template.candidates.size,
                            advanced = state.advanced,
                            editing = state.editing,
                            compactPresetName = activePresetName.takeIf { headerCollapsed },
                            onSelectMode = { targetPage ->
                                viewModel.setAdvanced(targetPage == 1)
                                scope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            },
                            onStartEditing = viewModel::startEditing,
                            onFinishEditing = viewModel::finishEditing,
                        )
                    }

                    // 仅参数列表区域可左右滑动切换简单 / 高级模式。
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                            fieldErrors = state.fieldErrors,
                            submitError = state.submitError,
                            visibleCandidates = if (advancedPage) {
                                template.candidates
                            } else {
                                simpleVisible
                            },
                            listState = if (advancedPage) advancedListState else simpleListState,
                            onToggleCandidate = viewModel::toggleCandidate,
                            onValueChange = viewModel::onValueChange,
                            uploadingCandidateId = state.uploadingCandidateId,
                            onPickImage = { candidateId ->
                                pendingImageCandidateId = candidateId
                                imagePicker.launch("image/*")
                            },
                            onDrawMask = { candidateId -> maskEditorCandidateId = candidateId },
                            onClearSubmitError = viewModel::clearSubmitError,
                            promptTagGroups = template.promptTagGroups,
                            onOpenPromptEditor = { candidateId ->
                                promptEditorCandidateId = candidateId
                            },
                        )
                    }

                    // 方案 B 底部常驻单行一体式步进器底栏
                    StickyBatchBottomBar(
                        template = template,
                        dirty = state.dirty,
                        saving = state.saving,
                        submitting = state.submitting,
                        batchCount = state.batchCount,
                        seedStrategy = state.seedStrategy,
                        onSave = { viewModel.save() },
                        onCycleSeedStrategy = viewModel::cycleSeedStrategy,
                        onStepBatch = viewModel::stepBatchCount,
                        onSubmit = viewModel::submit,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
        )
    }

    if (showAddPresetDialog) {
        AlertDialog(
            onDismissRequest = { showAddPresetDialog = false },
            title = { Text(stringResource(R.string.workflow_preset_save_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.workflow_preset_save_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.workflow_preset_name_hint)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveNewPreset(newPresetName)
                        showAddPresetDialog = false
                    },
                    enabled = newPresetName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.workflow_preset_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRenamePresetDialog) {
        AlertDialog(
            onDismissRequest = { showRenamePresetDialog = false },
            title = { Text(stringResource(R.string.workflow_preset_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renamePresetName,
                    onValueChange = { renamePresetName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.workflow_preset_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameActivePreset(renamePresetName)
                        showRenamePresetDialog = false
                    },
                    enabled = renamePresetName.isNotBlank(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePresetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.workflow_unsaved_dialog_title)) },
            text = { Text(stringResource(R.string.workflow_unsaved_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.save {
                            showUnsavedDialog = false
                            onBack()
                        }
                    },
                    enabled = !state.saving,
                ) {
                    Text(stringResource(R.string.workflow_unsaved_dialog_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            onBack()
                        },
                    ) {
                        Text(stringResource(R.string.workflow_unsaved_dialog_discard))
                    }
                }
            },
        )
    }

    template?.let { current ->
        promptEditorCandidateId?.let { candidateId ->
            current.candidates.firstOrNull { it.id == candidateId }?.let { candidate ->
                PromptEditorDialog(
                    candidate = candidate,
                    groups = current.promptTagGroups,
                    onValueChange = { value ->
                        viewModel.onValueChange(candidate.id, value)
                    },
                    onSaveTagGroup = viewModel::addPromptTagGroup,
                    onDeleteTagGroup = viewModel::deletePromptTagGroup,
                    onOpenAiSettings = onOpenAiSettings,
                    onDismiss = { promptEditorCandidateId = null },
                )
            }
        }
    }

    state.submission?.let { submission ->
        SubmissionDialog(
            submission = submission,
            submittedCount = state.submittedBatchCount,
            onDismiss = viewModel::clearSubmission,
        )
    }

    maskEditorCandidateId?.let { candidateId ->
        MaskEditorDialog(
            images = state.maskSourceImages,
            uploading = state.uploadingCandidateId == candidateId,
            uploadError = state.imageUploadError,
            onDismiss = {
                maskEditorCandidateId = null
                viewModel.clearImageUploadNotice()
            },
            onUpload = { bitmap ->
                viewModel.uploadMask(candidateId, bitmap)
            },
        )
    }

    if (state.rawJsonVisible) {
        RawJsonDialog(
            json = state.rawJson,
            onDismiss = viewModel::dismissRawJson,
            onCopy = { text ->
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("workflow", text))
                scope.launch {
                    snackbarHostState.showSnackbar(rawJsonCopiedMessage)
                }
            },
        )
    }
}

@Composable
private fun WorkflowPage(
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
    uploadingCandidateId: String?,
    onPickImage: (String) -> Unit,
    onDrawMask: (String) -> Unit,
    onClearSubmitError: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<ParameterGroup?>(null) }

    val filteredCandidates = if (advanced) {
        visibleCandidates.filter { candidate ->
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
                    onToggleCandidate = onToggleCandidate,
                    onValueChange = onValueChange,
                    onPickImage = onPickImage,
                    onDrawMask = onDrawMask,
                )
            }
        }
    }
}

@Composable
private fun CompactOverviewStrip(
    template: WorkflowTemplate,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onShowRawJson: () -> Unit,
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

@Composable
private fun WorkflowPresetSection(
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
private fun ModeControlRow(
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
 * 方案 B：分组大容器 + 短数值参数 2x2 双列 Bento 网格。
 */
@Composable
private fun ParameterGroupSection(
    group: ParameterGroup,
    candidates: List<ParameterCandidate>,
    fieldErrors: Map<String, FieldError>,
    showCheckbox: Boolean,
    maskNodeIds: Set<String>,
    uploadingCandidateId: String?,
    promptTagGroups: List<PromptTagGroup>,
    onOpenPromptEditor: (String) -> Unit,
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
                    PromptSummaryCard(
                        candidate = promptCandidate,
                        groups = promptTagGroups,
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

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (errorText != null) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "#${candidate.nodeId}",
                    style = NekoMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (errorText != null) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = candidate.selected,
                    onCheckedChange = { onToggle() },
                    enabled = !candidate.readOnly,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 精简为单行清爽头部：左参数名 + 右侧等宽节点胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = candidate.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = "#${candidate.nodeId} · ${candidate.classType.ifBlank { candidate.inputName }}",
                            style = NekoMonoSmall.copy(fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

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
                        uploading = uploading,
                        onPickImage = onPickImage,
                        isMaskSlot = isMaskSlot,
                        onDrawMask = onDrawMask,
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
    uploading: Boolean,
    onPickImage: () -> Unit,
    isMaskSlot: Boolean,
    onDrawMask: () -> Unit,
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
            val isSeed = candidate.inputName in WorkflowTemplate.SEED_INPUT_NAMES
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = candidate.value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = NekoMonoSmall.copy(fontSize = 13.sp),
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
                    if (isSeed) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onValueChange(Random.nextLong(1L, 999_999_999_999_999L).toString())
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = NekoIcons.Dice,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = stringResource(R.string.workflow_random),
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

        ParameterType.IMAGE -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = candidate.value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = NekoMonoSmall.copy(fontSize = 12.sp),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onPickImage,
                        enabled = !uploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(
                            text = stringResource(
                                if (uploading) {
                                    R.string.workflow_image_uploading
                                } else {
                                    R.string.workflow_image_pick_short
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (isMaskSlot) {
                        Button(
                            onClick = onDrawMask,
                            enabled = !uploading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = NekoIcons.BrushMask,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.workflow_mask_draw_short),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        ParameterType.MULTILINE_TEXT,
        ParameterType.TEXT,
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

/**
 * 方案 B：底部常驻单行一体式步进器底栏。
 *
 * - 左侧：[保存/更新预设] + [🎲 每次随机 / 递增+1 / 固定种子]（无种子节点的工作流自动隐藏种子策略胶囊）
 * - 右侧：[ − | N次 | ＋ | ▶ 提交生成 ] 一体式步进主按钮
 */
@Composable
private fun StickyBatchBottomBar(
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
    val submitEnabled = !submitting && !saving && template.selectedCount > 0
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
private fun RawJsonDialog(
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
private fun groupLabel(group: ParameterGroup): String = when (group) {
    ParameterGroup.PROMPT -> stringResource(R.string.workflow_group_prompt)
    ParameterGroup.IMAGE -> stringResource(R.string.workflow_group_image)
    ParameterGroup.SAMPLING -> stringResource(R.string.workflow_group_sampling)
    ParameterGroup.MODEL -> stringResource(R.string.workflow_group_model)
    ParameterGroup.ADVANCED -> stringResource(R.string.workflow_group_advanced)
    ParameterGroup.OTHER -> stringResource(R.string.workflow_group_other)
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

@Composable
private fun NumericPresetDropdown(
    presets: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.workflow_numeric_presets),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = preset,
                            style = NekoMonoSmall,
                        )
                    },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun WorkflowDetailControlsPreview() {
    val template = previewDetailTemplate()
    NekoPaintTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactOverviewStrip(
                template = template,
                expanded = false,
                onToggleExpanded = {},
                onShowRawJson = {},
            )
            WorkflowPresetSection(
                template = template,
                dirty = true,
                onSelectPreset = {},
                onRequestAddPreset = {},
                onRequestRenamePreset = {},
                onDeleteActivePreset = {},
            )
            ParameterGroupSection(
                group = ParameterGroup.PROMPT,
                candidates = template.candidates.filter { it.group == ParameterGroup.PROMPT },
                fieldErrors = emptyMap(),
                showCheckbox = false,
                maskNodeIds = emptySet(),
                uploadingCandidateId = null,
                promptTagGroups = template.promptTagGroups,
                onOpenPromptEditor = {},
                onToggleCandidate = {},
                onValueChange = { _, _ -> },
                onPickImage = {},
                onDrawMask = {},
            )
            ModeControlRow(
                selectedCount = template.selectedCount,
                totalCount = template.candidates.size,
                advanced = false,
                editing = false,
                onSelectMode = {},
                onStartEditing = {},
                onFinishEditing = {},
            )
            ParameterGroupSection(
                group = ParameterGroup.SAMPLING,
                candidates = template.candidates.filter { it.group == ParameterGroup.SAMPLING },
                fieldErrors = emptyMap(),
                showCheckbox = false,
                maskNodeIds = emptySet(),
                uploadingCandidateId = null,
                promptTagGroups = emptyList(),
                onOpenPromptEditor = {},
                onToggleCandidate = {},
                onValueChange = { _, _ -> },
                onPickImage = {},
                onDrawMask = {},
            )
            Spacer(Modifier.weight(1f))
            StickyBatchBottomBar(
                template = template,
                dirty = true,
                saving = false,
                submitting = false,
                batchCount = 2,
                seedStrategy = SeedStrategy.RANDOM,
                onSave = {},
                onCycleSeedStrategy = {},
                onStepBatch = {},
                onSubmit = {},
            )
        }
    }
}

private fun previewDetailTemplate(): WorkflowTemplate {
    val candidates = listOf(
        ParameterCandidate(
            id = "6:text",
            nodeId = "6",
            inputName = "text",
            label = "正向提示词",
            type = ParameterType.MULTILINE_TEXT,
            group = ParameterGroup.PROMPT,
            defaultValue = "cute fox girl",
            value = "1girl, #起手画质组, silver hair, soft lighting, detailed eyes",
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
            id = "3:cfg",
            nodeId = "3",
            inputName = "cfg",
            label = "引导系数",
            type = ParameterType.FLOAT,
            group = ParameterGroup.SAMPLING,
            defaultValue = "7.0",
            value = "6.5",
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
        candidates = candidates,
        compatibility = WorkflowCompatibility(
            mode = WorkflowMode.MASK_INPAINT,
            catalogChecked = true,
            analyzerVersion = 1,
        ),
        presets = listOf(
            WorkflowPreset(id = "preset-preview", name = "白发少女光影"),
        ),
        activePresetId = "preset-preview",
        promptTagGroups = listOf(
            PromptTagGroup(
                id = "group-preview",
                name = "起手画质组",
                tags = listOf("masterpiece", "best quality", "very aesthetic"),
            ),
        ),
    )
}
