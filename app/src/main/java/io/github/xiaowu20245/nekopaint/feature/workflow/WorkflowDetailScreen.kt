package io.github.xiaowu20245.nekopaint.feature.workflow

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.feature.common.asString
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCompatibility
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarAction
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
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
    val nodeInstallState by viewModel.nodeInstallState.collectAsStateWithLifecycle()
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
    var showManualBinding by rememberSaveable { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(state.dirty)
    val backgroundSubmissionMessage = stringResource(R.string.workflow_submit_background_running)
    val currentBackgroundMessage by rememberUpdatedState(backgroundSubmissionMessage)

    DisposableEffect(Unit) {
        // 外部 TopAppBar 的返回箭头也走同一套未保存拦截。
        onBackHandlerChange {
            when {
                currentDirty -> {
                    showUnsavedDialog = true
                    true
                }

                viewModel.hasActiveSubmission() -> {
                    // 非阻断：允许离开，但提示结果仍可在工作流列表恢复。
                    Toast.makeText(
                        context,
                        currentBackgroundMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                    false
                }

                else -> false
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
    val imageUploadErrorMessage = state.imageUploadError?.asString()
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
                                    checkingNodes = state.checkingNodes,
                                    onToggleExpanded = { overviewExpanded = !overviewExpanded },
                                    onShowRawJson = viewModel::showRawJson,
                                    onInstallMissingNodes = viewModel::openNodeInstall,
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

                    if (template.repairRequired) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.workflow_repair_required),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                state.repairError?.let { message ->
                                    Text(
                                        text = message.asString(),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(
                                    onClick = viewModel::repairTemplate,
                                    enabled = !state.repairing,
                                ) {
                                    Text(stringResource(R.string.workflow_repair_action))
                                }
                            }
                        }
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
                            onSetPrimaryPrompt = viewModel::setPrimaryPrompt,
                            onOpenManualBinding = { showManualBinding = true },
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
        val maskSourceImages by viewModel.maskSourceImages.collectAsStateWithLifecycle()
        val maskExportState by viewModel.maskExportState.collectAsStateWithLifecycle()
        LaunchedEffect(maskExportState.success) {
            if (maskExportState.success) {
                maskEditorCandidateId = null
                viewModel.resetMaskExportState()
            }
        }
        MaskEditorDialog(
            images = maskSourceImages,
            uploading = maskExportState.busy,
            uploadError = maskExportState.error,
            dimensionReader = app.container.imageDimensionReader,
            onDismiss = {
                maskEditorCandidateId = null
                viewModel.clearImageUploadNotice()
                viewModel.resetMaskExportState()
            },
            onUpload = { strokes, sourceSize, inverted ->
                viewModel.exportMask(
                    candidateId = candidateId,
                    strokes = strokes,
                    sourceWidth = sourceSize.width,
                    sourceHeight = sourceSize.height,
                    inverted = inverted,
                )
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

    if (showManualBinding) {
        ManualBindingDialog(
            onDismiss = { showManualBinding = false },
            onConfirm = { nodeId, inputName, label, type, group, value ->
                viewModel.addManualCandidate(
                    nodeId = nodeId,
                    inputName = inputName,
                    label = label,
                    type = type,
                    group = group,
                    value = value,
                )
                showManualBinding = false
            },
        )
    }

    if (nodeInstallState.visible) {
        NodeInstallDialog(
            state = nodeInstallState,
            onDismiss = viewModel::dismissNodeInstall,
            onConfirm = viewModel::confirmNodeInstall,
            onRetry = viewModel::openNodeInstall,
            onRecheck = viewModel::recheckNodeInstall,
            onRestartChange = viewModel::setRestartAfterInstall,
        )
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
                checkingNodes = false,
                onToggleExpanded = {},
                onShowRawJson = {},
                onInstallMissingNodes = {},
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
