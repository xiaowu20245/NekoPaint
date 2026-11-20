package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.feature.common.asString
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoShape
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme

@Composable
fun AiAssistantSettingsScreen(
    onBack: () -> Unit = {},
    onBackHandlerChange: ((() -> Boolean)?) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: AiAssistantSettingsViewModel = viewModel(
        factory = AiAssistantSettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.ai_assistant_saved)
    val testLatencyMs = state.testLatencyMs
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(state.dirty)
    val currentSaving by rememberUpdatedState(state.saving)

    DisposableEffect(Unit) {
        // 顶栏返回箭头与系统返回共用同一未保存门禁。
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ai_assistant_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.ai_assistant_edit_semantics),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.baseUrl.isBlank() && state.apiKey.isBlank()) {
                        Text(
                            text = stringResource(R.string.ai_assistant_local_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = NekoTheme.colors.success,
                        )
                    }
                }
            }

            if (!state.disclosureAcknowledged) {
                item {
                    SettingsSection(
                        title = stringResource(R.string.ai_assistant_disclosure_title),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ai_assistant_disclosure_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionButton(
                                label = stringResource(R.string.ai_assistant_disclosure_confirm),
                                loading = false,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                enabled = state.baseUrl.isNotBlank() && state.baseUrlError == null,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = viewModel::acknowledgeDisclosure,
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.ai_assistant_preset_title),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AiProviderPreset.entries.forEach { preset ->
                            PresetChip(
                                label = stringResource(preset.labelRes),
                                selected = state.providerId == preset.id,
                                onClick = { viewModel.applyPreset(preset) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.ai_assistant_params_title),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LabeledInput(
                            label = stringResource(R.string.ai_assistant_base_url),
                            value = state.baseUrl,
                            onValueChange = viewModel::onBaseUrlChange,
                            placeholder = stringResource(R.string.ai_assistant_base_url_hint),
                            keyboardType = KeyboardType.Uri,
                        )
                        state.baseUrlError?.let { message ->
                            Text(
                                text = message.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        LabeledInput(
                            label = stringResource(R.string.ai_assistant_api_key),
                            value = state.apiKey,
                            onValueChange = viewModel::onApiKeyChange,
                            placeholder = stringResource(R.string.ai_assistant_api_key_hint),
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (state.keyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing = {
                                Text(
                                    text = stringResource(
                                        if (state.keyVisible) {
                                            R.string.ai_assistant_hide_key
                                        } else {
                                            R.string.ai_assistant_show_key
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = viewModel::toggleKeyVisible)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LabeledInput(
                                label = stringResource(R.string.ai_assistant_text_model),
                                value = state.textModel,
                                onValueChange = viewModel::onTextModelChange,
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Text,
                            )
                            LabeledInput(
                                label = stringResource(R.string.ai_assistant_vision_model),
                                value = state.visionModel,
                                onValueChange = viewModel::onVisionModelChange,
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Text,
                            )
                        }

                        state.credentialMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = NekoTheme.colors.warning,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        state.policyMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.policyBlocked) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    NekoTheme.colors.warning
                                },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }

                        if (testLatencyMs != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NekoTheme.colors.successContainer,
                                contentColor = NekoTheme.colors.onSuccessContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 7.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.ai_assistant_test_success,
                                            state.textModel,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.ai_assistant_test_latency,
                                            testLatencyMs,
                                        ),
                                        style = NekoMonoSmall.copy(fontSize = 11.sp),
                                    )
                                }
                            }
                        } else if (state.testError != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = state.testError?.asString().orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 7.dp,
                                    ),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(
                                label = stringResource(
                                    if (state.testing) {
                                        R.string.ai_assistant_testing
                                    } else {
                                        R.string.ai_assistant_test
                                    },
                                ),
                                loading = state.testing,
                                icon = Icons.Outlined.Refresh,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.primary,
                                enabled = !state.testing && !state.saving && state.canSubmit,
                                modifier = Modifier.weight(1f),
                                onClick = viewModel::testConnection,
                            )
                            ActionButton(
                                label = stringResource(
                                    if (state.saving) {
                                        R.string.ai_assistant_saving
                                    } else {
                                        R.string.ai_assistant_save
                                    },
                                ),
                                loading = state.saving,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                enabled = !state.testing && !state.saving && state.canSubmit,
                                modifier = Modifier.weight(1f),
                                onClick = viewModel::save,
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ai_assistant_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.baseUrl.isNotBlank() ||
                state.apiKey.isNotBlank() ||
                state.textModel.isNotBlank()
            ) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ActionButton(
                            label = stringResource(R.string.ai_assistant_clear_config),
                            loading = false,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            enabled = !state.saving && !state.testing,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = viewModel::requestClearConfig,
                        )
                    }
                }
            }
        }

        state.confirmationMessage?.let { message ->
            NekoConfirmDialog(
                title = stringResource(R.string.ai_assistant_confirm_title),
                message = message,
                confirmLabel = stringResource(R.string.ai_assistant_confirm_action),
                onConfirm = viewModel::confirmPendingAction,
                onDismiss = {
                    exitAfterSave = false
                    viewModel.cancelPendingAction()
                },
                destructive = false,
            )
        }

        if (showUnsavedDialog) {
            NekoConfirmDialog(
                title = stringResource(R.string.settings_unsaved_title),
                message = stringResource(R.string.ai_assistant_unsaved_desc),
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = NekoShape.small,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(NekoShape.small)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = NekoMonoSmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.55f,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = NekoMonoSmall.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        visualTransformation = visualTransformation,
                    )
                }
                trailing?.invoke()
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    loading: Boolean,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor.copy(alpha = if (enabled) 1f else 0.5f),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Box(Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    // 装饰性图标：按钮文字已提供操作语义。
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Box(Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}
