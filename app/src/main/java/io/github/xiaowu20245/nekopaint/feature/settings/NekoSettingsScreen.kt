package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoRadius
import io.github.xiaowu20245.nekopaint.feature.common.asString

@Composable
fun NekoSettingsScreen(
    onBack: () -> Unit = {},
    onBackHandlerChange: ((() -> Boolean)?) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: NekoSettingsViewModel = viewModel(
        factory = NekoSettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.settings_neko_saved)
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(state.dirty)
    val currentSaving by rememberUpdatedState(state.saving)

    DisposableEffect(Unit) {
        // 顶栏返回箭头与系统返回共用同一人设未保存门禁。
        onBackHandlerChange {
            when {
                // 保存进行中不允许再打开放弃/取消入口，避免迟到保存写入被放弃的草稿。
                currentSaving -> true
                currentDirty -> {
                    showUnsavedDialog = true
                    true
                }
                else -> false
            }
        }
        onDispose { onBackHandlerChange(null) }
    }
    BackHandler(enabled = state.saving || (state.dirty && !showUnsavedDialog)) {
        if (!state.saving) {
            showUnsavedDialog = true
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            if (exitAfterSave) {
                exitAfterSave = false
                onBack()
            } else {
                snackbarHostState.showSnackbar(savedMessage)
            }
            viewModel.consumeSaved()
        }
    }
    val errorText = state.error?.asString()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            exitAfterSave = false
            snackbarHostState.showSnackbar(errorText)
            viewModel.consumeError()
        }
    }

    if (showUnsavedDialog) {
        NekoConfirmDialog(
            title = stringResource(R.string.settings_unsaved_title),
            message = stringResource(R.string.settings_neko_unsaved_desc),
            confirmLabel = stringResource(R.string.workflow_unsaved_dialog_save),
            onConfirm = {
                showUnsavedDialog = false
                exitAfterSave = true
                viewModel.save()
            },
            onDismiss = {
                exitAfterSave = false
                showUnsavedDialog = false
            },
            destructive = false,
            secondaryLabel = stringResource(R.string.workflow_unsaved_dialog_discard),
            onSecondary = {
                exitAfterSave = false
                showUnsavedDialog = false
                viewModel.discardChanges()
                onBack()
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_neko_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.settings_neko_edit_semantics),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SettingsSection(title = stringResource(R.string.ai_assistant_persona)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.persona,
                                onValueChange = viewModel::onPersonaChange,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 8,
                                placeholder = {
                                    Text(stringResource(R.string.ai_assistant_persona_hint))
                                },
                                shape = RoundedCornerShape(NekoRadius.md),
                            )
                        }
                    }
                }
                item {
                    SettingsSection(title = stringResource(R.string.ai_assistant_pre_instruction)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.preInstruction,
                                onValueChange = viewModel::onPreInstructionChange,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 8,
                                placeholder = {
                                    Text(stringResource(R.string.ai_assistant_pre_instruction_hint))
                                },
                                shape = RoundedCornerShape(NekoRadius.md),
                            )
                            Text(
                                text = stringResource(R.string.settings_neko_pre_instruction_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SettingsSection(title = stringResource(R.string.settings_neko_default_mode)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_neko_default_mode_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !state.agentModeByDefault,
                                    onClick = { viewModel.setAgentModeByDefault(false) },
                                    label = { Text(stringResource(R.string.chat_mode_chat)) },
                                )
                                FilterChip(
                                    selected = state.agentModeByDefault,
                                    onClick = { viewModel.setAgentModeByDefault(true) },
                                    label = { Text(stringResource(R.string.chat_mode_agent)) },
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_neko_agent_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.saving,
                        shape = RoundedCornerShape(NekoRadius.pill),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(48.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.settings_neko_save))
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}
