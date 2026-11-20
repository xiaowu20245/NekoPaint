package io.github.xiaowu20245.nekopaint.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarIconAction
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarMenuItem
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
import io.github.xiaowu20245.nekopaint.feature.history.ImagePreviewDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext

/**
 * Chat 返回门禁：系统返回和顶栏返回共用同一判定。
 *
 * 动作执行中消费返回并提示；其余情况交回外部 pop。
 */
@Composable
fun ChatBackHandler(
    actionExecuting: Boolean,
    onBackHandlerChange: ((() -> Boolean)?) -> Unit,
    blockedMessage: String,
) {
    val context = LocalContext.current
    val currentActionExecuting by rememberUpdatedState(actionExecuting)
    BackHandler(enabled = actionExecuting) {
        Toast.makeText(context, blockedMessage, Toast.LENGTH_SHORT).show()
    }
    DisposableEffect(Unit) {
        onBackHandlerChange {
            if (currentActionExecuting) {
                Toast.makeText(context, blockedMessage, Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }
        onDispose { onBackHandlerChange(null) }
    }
}

@Composable
fun ChatScreen(
    onOpenAiSettings: () -> Unit,
    onTopBarIconActionsChange: (List<NekoTopBarIconAction>) -> Unit = {},
    onBackHandlerChange: ((() -> Boolean)?) -> Unit = {},
    promptId: String? = null,
    viewModelOverride: ChatViewModel? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: ChatViewModel = viewModelOverride ?: viewModel(
        factory = ChatViewModel.factory(app.container, promptId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.sessionError) {
        state.sessionError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearSessionError()
        }
    }
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
    val actionExecuting = state.messages.any { message ->
        message.actionProposals.any { it.state == ChatActionState.EXECUTING }
    }
    val actionRunningMessage = stringResource(R.string.chat_action_leave_blocked)
    ChatBackHandler(
        actionExecuting = actionExecuting,
        onBackHandlerChange = onBackHandlerChange,
        blockedMessage = actionRunningMessage,
    )
    DisposableEffect(
        state.messages.isNotEmpty(),
        state.currentSessionId,
        state.sending,
        actionExecuting,
        currentSession?.title,
        state.mode,
    ) {
        val actions = buildList {
            add(
                NekoTopBarIconAction(
                    label = modeLabel,
                    enabled = !state.sending && !actionExecuting,
                    onClick = { showModeDialog = true },
                ),
            )
            if (!isNewSession) {
                add(
                    NekoTopBarIconAction(
                        icon = Icons.Outlined.Add,
                        contentDescription = newSessionLabel,
                        enabled = !state.sending && !actionExecuting,
                        onClick = viewModel::newSession,
                    ),
                )
            }
            add(
                NekoTopBarIconAction(
                    icon = Icons.AutoMirrored.Outlined.List,
                    contentDescription = sessionsLabel,
                    enabled = !actionExecuting,
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
                                enabled = currentSession != null && !state.sending && !actionExecuting,
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
                                enabled = currentSession != null && !state.sending && !actionExecuting,
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
            actionExecuting = actionExecuting,
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
        NekoConfirmDialog(
            title = stringResource(R.string.chat_session_delete_title),
            message = stringResource(R.string.chat_session_delete_message),
            confirmLabel = stringResource(R.string.chat_session_delete),
            onConfirm = {
                deleteTarget = null
                viewModel.deleteSession(session.id)
            },
            onDismiss = { deleteTarget = null },
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
@Preview(showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun ChatEmptyPreview() {
    NekoPaintTheme {
        ChatEmptyState(
            taskContext = null,
            onAskContext = null,
            onOpenSettings = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun ChatMessagesPreview() {
    NekoPaintTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChatMessageBubble(
                message = ChatUiMessage(
                    id = 1,
                    role = ChatRole.USER,
                    text = "帮我把这句提示词润色得更适合 SDXL。",
                ),
                onConfirmAction = { _, _ -> },
                onCancelAction = { _, _ -> },
                onSelectPromptCandidate = { _, _, _ -> },
                onToggleInstallRestart = { _, _ -> },
                onRequestSavePreset = { _, _ -> },
                actionsEnabled = false,
                onOpenTags = { _, _ -> },
                onOpenHistoryImage = { _, _ -> },
            )
            ChatMessageBubble(
                message = ChatUiMessage(
                    id = 2,
                    role = ChatRole.ASSISTANT,
                    text = "可以试试：1girl, silver hair, soft rim lighting, " +
                        "detailed eyes, cinematic composition.",
                ),
                onConfirmAction = { _, _ -> },
                onCancelAction = { _, _ -> },
                onSelectPromptCandidate = { _, _, _ -> },
                onToggleInstallRestart = { _, _ -> },
                onRequestSavePreset = { _, _ -> },
                actionsEnabled = false,
                onOpenTags = { _, _ -> },
                onOpenHistoryImage = { _, _ -> },
            )
        }
    }
}
