package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme

@Composable
fun AiAssistantSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: AiAssistantSettingsViewModel = viewModel(
        factory = AiAssistantSettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.ai_assistant_saved)
    val testLatencyMs = state.testLatencyMs

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.consumeSaved()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.ai_assistant_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.ai_assistant_preset_title),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
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
                SettingsGroupCard(
                    title = stringResource(R.string.ai_assistant_neko_title),
                ) {
                    Text(
                        text = stringResource(R.string.ai_assistant_neko_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    LabeledMultilineInput(
                        label = stringResource(R.string.ai_assistant_persona),
                        value = state.persona,
                        onValueChange = viewModel::onPersonaChange,
                        placeholder = stringResource(R.string.ai_assistant_persona_hint),
                    )
                    LabeledMultilineInput(
                        label = stringResource(R.string.ai_assistant_pre_instruction),
                        value = state.preInstruction,
                        onValueChange = viewModel::onPreInstructionChange,
                        placeholder = stringResource(R.string.ai_assistant_pre_instruction_hint),
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.ai_assistant_params_title),
                ) {
                    LabeledInput(
                        label = stringResource(R.string.ai_assistant_base_url),
                        value = state.baseUrl,
                        onValueChange = viewModel::onBaseUrlChange,
                        placeholder = stringResource(R.string.ai_assistant_base_url_hint),
                        keyboardType = KeyboardType.Uri,
                    )
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

                    if (testLatencyMs != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NekoTheme.colors.successContainer,
                            contentColor = NekoTheme.colors.onSuccessContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                                text = stringResource(
                                    R.string.ai_assistant_test_failed,
                                    state.testError.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                            enabled = !state.testing && !state.saving,
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
                            enabled = !state.testing && !state.saving,
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::save,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.ai_assistant_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
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
            .clip(RoundedCornerShape(9.dp))
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
private fun LabeledMultilineInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 3,
                    maxLines = 6,
                )
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

@Composable
internal fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            content()
        }
    }
}
