package io.github.xiaowu20245.nekopaint.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarIconAction
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarMenuItem
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.feature.history.ImagePreviewDialog
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    onOpenAiSettings: () -> Unit,
    onTopBarIconActionsChange: (List<NekoTopBarIconAction>) -> Unit = {},
    promptId: String? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(app.container, promptId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contextQuestionText = stringResource(
        if (state.taskContext?.isFailure == true) {
            R.string.chat_context_ask_failed
        } else {
            R.string.chat_context_ask_task
        },
    )
    var input by rememberSaveable { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<Uri?>(null) }
    var showSessions by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var imageQuestion by remember { mutableStateOf("") }
    var showModeDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var presetTarget by remember { mutableStateOf<Pair<Long, ChatActionProposal>?>(null) }
    var presetNameText by remember { mutableStateOf("") }
    var previewImages by remember { mutableStateOf<List<HistoryImage>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }
    var tagDetail by remember { mutableStateOf<ChatTagDetail?>(null) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) attachedImage = uri
    }

    val newSessionLabel = stringResource(R.string.chat_session_new)
    val sessionsLabel = stringResource(R.string.chat_sessions)
    val moreLabel = stringResource(R.string.chat_more)
    val deleteLabel = stringResource(R.string.chat_delete_current)
    val settingsLabel = stringResource(R.string.chat_open_ai_settings)
    val renameLabel = stringResource(R.string.chat_session_rename_current)
    val modeLabel = stringResource(
        if (state.mode == ChatMode.AGENT) {
            R.string.chat_mode_agent
        } else {
            R.string.chat_mode_chat
        },
    )
    val currentSession = state.sessions.firstOrNull { it.id == state.currentSessionId }
    val isNewSession = state.currentSessionId == null && state.messages.isEmpty()
    DisposableEffect(
        state.messages.isNotEmpty(),
        state.currentSessionId,
        state.sending,
        currentSession?.title,
        state.mode,
    ) {
        val actions = buildList {
            add(
                NekoTopBarIconAction(
                    label = modeLabel,
                    enabled = !state.sending,
                    onClick = { showModeDialog = true },
                ),
            )
            if (!isNewSession) {
                add(
                    NekoTopBarIconAction(
                        icon = Icons.Outlined.Add,
                        contentDescription = newSessionLabel,
                        enabled = !state.sending,
                        onClick = viewModel::newSession,
                    ),
                )
            }
            add(
                NekoTopBarIconAction(
                    icon = Icons.AutoMirrored.Outlined.List,
                    contentDescription = sessionsLabel,
                    onClick = { showSessions = true },
                ),
            )
            add(
                NekoTopBarIconAction(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = moreLabel,
                    menuItems = buildList {
                        add(
                            NekoTopBarMenuItem(
                                label = renameLabel,
                                enabled = currentSession != null && !state.sending,
                                onClick = {
                                    currentSession?.let { session ->
                                        renameTarget = session
                                        renameText = session.title
                                    }
                                },
                            ),
                        )
                        add(
                            NekoTopBarMenuItem(
                                label = deleteLabel,
                                icon = Icons.Outlined.Delete,
                                destructive = true,
                                enabled = currentSession != null && !state.sending,
                                onClick = {
                                    currentSession?.let { deleteTarget = it }
                                },
                            ),
                        )
                        add(
                            NekoTopBarMenuItem(
                                label = settingsLabel,
                                icon = NekoIcons.Sparkles,
                                onClick = onOpenAiSettings,
                            ),
                        )
                    },
                ),
            )
        }
        onTopBarIconActionsChange(actions)
        onDispose { onTopBarIconActionsChange(emptyList()) }
    }

    LaunchedEffect(
        state.messages.size,
        state.sending,
        state.streamingMessageId,
        state.messages.lastOrNull()?.text?.length,
    ) {
        val typingVisible = state.sending && state.streamingMessageId == null
        val anchorIndex = state.messages.size + if (typingVisible) 1 else 0
        if (anchorIndex > 0) {
            if (state.streamingMessageId != null) {
                listState.scrollToItem(anchorIndex)
            } else {
                listState.animateScrollToItem(anchorIndex)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!state.configured) {
            ChatEmptyState(
                taskContext = null,
                onAskContext = null,
                onOpenSettings = onOpenAiSettings,
                modifier = Modifier.weight(1f),
            )
        } else {
            val contextPromptId = state.contextPromptId
            if (contextPromptId != null) {
                ChatTaskContextBanner(
                    taskContext = state.taskContext,
                    promptId = contextPromptId,
                    onDetach = viewModel::clearTaskContext,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty() && !state.sending) {
                    ChatEmptyState(
                        taskContext = state.taskContext,
                        onAskContext = if (state.contextPromptId != null) {
                            {
                                viewModel.send(
                                    text = contextQuestionText,
                                    imageUri = null,
                                )
                            }
                        } else {
                            null
                        },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            ChatMessageBubble(
                                message = message,
                                onConfirmAction = viewModel::confirmAction,
                                onCancelAction = viewModel::cancelAction,
                                onSelectPromptCandidate = viewModel::selectPromptCandidate,
                                onToggleInstallRestart = viewModel::toggleInstallRestart,
                                onRequestSavePreset = { messageId, proposal ->
                                    presetTarget = messageId to proposal
                                    presetNameText = "${proposal.title} 预设"
                                },
                                actionsEnabled = state.mode == ChatMode.AGENT,
                                onOpenTags = { tags, description ->
                                    tagDetail = ChatTagDetail(tags, description)
                                },
                                onOpenHistoryImage = { images, index ->
                                    previewImages = images.map { image ->
                                        HistoryImage(
                                            filename = image.filename,
                                            subfolder = "",
                                            type = image.type,
                                            url = image.url,
                                        )
                                    }
                                    previewIndex = index
                                },
                            )
                        }
                        if (state.sending && state.streamingMessageId == null) {
                            item(key = "typing") {
                                ChatTypingBubble(status = state.status)
                            }
                        }
                        item(key = "bottom_anchor") {
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                }
            }

            ChatComposer(
                input = input,
                onInputChange = { input = it },
                attachedImage = attachedImage,
                visionConfigured = state.visionConfigured,
                inputHint = stringResource(
                    if (state.contextPromptId != null) {
                        R.string.chat_context_input_hint
                    } else {
                        R.string.chat_input_hint
                    },
                ),
                sending = state.sending,
                onRemoveImage = { attachedImage = null },
                onOpenTools = { showTools = true },
                onSend = {
                    viewModel.send(input, attachedImage)
                    input = ""
                    attachedImage = null
                },
            )
        }
    }

    if (showSessions) {
        ChatSessionSheet(
            sessions = state.sessions,
            currentSessionId = state.currentSessionId,
            sending = state.sending,
            isNewSession = isNewSession,
            onDismiss = { showSessions = false },
            onNewSession = {
                showSessions = false
                viewModel.newSession()
            },
            onSelect = { sessionId ->
                showSessions = false
                viewModel.switchSession(sessionId)
            },
            onRename = { session ->
                renameTarget = session
                renameText = session.title
            },
            onTogglePin = { session ->
                viewModel.togglePinned(session.id, !session.pinned)
            },
            onDelete = { session ->
                deleteTarget = session
            },
        )
    }

    if (showTools) {
        ChatToolsSheet(
            mode = state.mode,
            sending = state.sending,
            attachedImage = attachedImage,
            question = imageQuestion,
            onQuestionChange = { imageQuestion = it },
            onPickImage = { imagePicker.launch("image/*") },
            onRemoveImage = { attachedImage = null },
            onDismiss = { showTools = false },
            onRunImageTool = { mode, question ->
                val uri = attachedImage
                if (uri != null) {
                    showTools = false
                    viewModel.runImageTool(uri, mode, question)
                    attachedImage = null
                    imageQuestion = ""
                }
            },
            onRunTool = { name, label ->
                showTools = false
                viewModel.runTool(name, label)
            },
        )
    }

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_session_delete_title)) },
            text = { Text(stringResource(R.string.chat_session_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteSession(session.id)
                    },
                ) {
                    Text(stringResource(R.string.chat_session_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    renameTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.chat_session_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.chat_session_rename_hint))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        viewModel.renameSession(session.id, renameText)
                        renameTarget = null
                    },
                ) {
                    Text(stringResource(R.string.chat_session_rename_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    previewImages?.let { images ->
        ImagePreviewDialog(
            images = images,
            initialIndex = previewIndex,
            onDismiss = { previewImages = null },
        )
    }

    presetTarget?.let { (messageId, proposal) ->
        AlertDialog(
            onDismissRequest = { presetTarget = null },
            title = { Text(stringResource(R.string.chat_action_preset_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = presetNameText,
                    onValueChange = { presetNameText = it },
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.chat_action_preset_name))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = presetNameText.isNotBlank(),
                    onClick = {
                        viewModel.savePreset(messageId, proposal.id, presetNameText)
                        presetTarget = null
                    },
                ) {
                    Text(stringResource(R.string.chat_action_preset_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { presetTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showModeDialog) {
        val targetMode = if (state.mode == ChatMode.AGENT) {
            ChatMode.CHAT
        } else {
            ChatMode.AGENT
        }
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text(stringResource(R.string.chat_mode_switch_title)) },
            text = { Text(stringResource(R.string.chat_mode_switch_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showModeDialog = false
                        viewModel.switchMode(targetMode)
                    },
                ) {
                    Text(
                        stringResource(
                            if (targetMode == ChatMode.AGENT) {
                                R.string.chat_mode_switch_to_agent
                            } else {
                                R.string.chat_mode_switch_to_chat
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    tagDetail?.let { detail ->
        TagDetailDialog(
            detail = detail,
            onDismiss = { tagDetail = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatToolsSheet(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSessionSheet(
    sessions: List<ChatSessionSummary>,
    currentSessionId: String?,
    sending: Boolean,
    isNewSession: Boolean,
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (ChatSessionSummary) -> Unit,
    onTogglePin: (ChatSessionSummary) -> Unit,
    onDelete: (ChatSessionSummary) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_sessions),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (!isNewSession) {
                    TextButton(onClick = onNewSession, enabled = !sending) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_session_new))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chat_session_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(
                                    R.string.chat_session_search_clear,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.height(8.dp))

            when {
                sessions.isEmpty() -> Text(
                    text = stringResource(R.string.chat_sessions_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.chat_session_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        val selected = session.id == currentSessionId
                        var menuExpanded by remember { mutableStateOf(false) }
                        val kind = stringResource(
                            if (session.promptId != null) {
                                R.string.chat_session_task
                            } else {
                                R.string.chat_session_global
                            },
                        )
                        val sessionModeLabel = stringResource(
                            if (session.mode == ChatMode.AGENT) {
                                R.string.chat_mode_agent
                            } else {
                                R.string.chat_mode_chat
                            },
                        )
                        val count = stringResource(
                            R.string.chat_session_count,
                            session.messageCount,
                        )
                        Surface(
                            onClick = { onSelect(session.id) },
                            enabled = !sending,
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                    end = 4.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (session.pinned) {
                                            Icon(
                                                imageVector = Icons.Outlined.Star,
                                                contentDescription = stringResource(
                                                    R.string.chat_session_pinned,
                                                ),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = "$sessionModeLabel · $kind · $count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = stringResource(
                                            R.string.chat_session_current,
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        enabled = !sending,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = stringResource(
                                                R.string.chat_more,
                                            ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.chat_session_rename))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onRename(session)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (session.pinned) {
                                                            R.string.chat_session_unpin
                                                        } else {
                                                            R.string.chat_session_pin
                                                        },
                                                    ),
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onTogglePin(session)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(
                                                        R.string.chat_session_delete,
                                                    ),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onDelete(session)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = slogan,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
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
private fun ChatTaskContextBanner(
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
            IconButton(
                onClick = onDetach,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.chat_context_detach),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
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

private data class ChatTagDetail(
    val tags: String,
    val description: String,
)

@Composable
private fun TagDetailDialog(
    detail: ChatTagDetail,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(detail.tags) { mutableStateOf(false) }
    val tagCount = remember(detail.tags) {
        detail.tags.split(',').count { it.isNotBlank() }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_tags_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (detail.description.isNotBlank()) {
                    ChatMarkdownText(
                        text = detail.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            text = detail.tags,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.chat_tags_count, tagCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("tags", detail.tags))
                    copied = true
                },
            ) {
                Icon(
                    imageVector = NekoIcons.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(
                        if (copied) {
                            R.string.chat_tags_copied
                        } else {
                            R.string.chat_tags_copy_all
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_tags_close))
            }
        },
    )
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
private fun ChatActionProposalCard(
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
                            onClick = onConfirm,
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

@Composable
private fun ChatMarkdownText(text: String, color: Color) {
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

@Composable
private fun ChatTypingBubble(status: ChatStatus?) {
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
private fun ChatComposer(
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
