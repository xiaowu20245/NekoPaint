package io.github.xiaowu20245.nekopaint.feature.settings

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R

@Composable
fun NekoSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: NekoSettingsViewModel = viewModel(
        factory = NekoSettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.settings_neko_saved)

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
                    Text(
                        text = stringResource(R.string.settings_neko_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
                                shape = RoundedCornerShape(12.dp),
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
                                shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(50),
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
