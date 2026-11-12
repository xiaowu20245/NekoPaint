package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall

@Composable
fun PromptLexiconScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: PromptLexiconViewModel = viewModel(
        factory = PromptLexiconViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    val exportFileName = stringResource(R.string.prompt_lexicon_export_file)

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::import)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(viewModel::exportTagGroups)
    }

    val noticeText = when (val notice = state.notice) {
        is PromptLexiconNotice.Imported -> stringResource(
            R.string.prompt_lexicon_import_success,
            notice.fileName,
            notice.count,
        )
        PromptLexiconNotice.Cleared -> stringResource(R.string.prompt_lexicon_cleared)
        is PromptLexiconNotice.Exported -> stringResource(
            R.string.prompt_lexicon_export_success,
            notice.count,
        )
        is PromptLexiconNotice.Failed -> notice.message
        null -> null
    }

    LaunchedEffect(state.notice) {
        noticeText?.let { text ->
            snackbarHostState.showSnackbar(text)
            viewModel.consumeNotice()
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
                    text = stringResource(R.string.prompt_lexicon_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.prompt_lexicon_status_title),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_lexicon_total_badge, state.builtInCount + state.externalCount),
                            style = NekoMonoSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatCard(
                            title = stringResource(R.string.prompt_lexicon_builtin_title),
                            count = stringResource(
                                R.string.prompt_lexicon_builtin_count,
                                state.builtInCount,
                            ),
                            caption = stringResource(R.string.prompt_lexicon_builtin_desc),
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = stringResource(R.string.prompt_lexicon_external_title),
                            count = stringResource(
                                R.string.prompt_lexicon_external_count,
                                state.externalCount,
                            ),
                            caption = state.fileName.ifBlank {
                                stringResource(R.string.prompt_lexicon_external_none)
                            },
                            accent = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.prompt_lexicon_import_title),
                ) {
                    Text(
                        text = stringResource(R.string.prompt_lexicon_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    ImportButton(
                        label = stringResource(
                            if (state.importing) {
                                R.string.prompt_lexicon_importing
                            } else {
                                R.string.prompt_lexicon_import
                            },
                        ),
                        loading = state.importing,
                        enabled = !state.importing && !state.clearing,
                        onClick = { importLauncher.launch("*/*") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SecondaryButton(
                            label = stringResource(
                                if (state.exporting) {
                                    R.string.prompt_lexicon_exporting
                                } else {
                                    R.string.prompt_lexicon_export_groups
                                },
                            ),
                            loading = state.exporting,
                            enabled = !state.exporting && !state.importing,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                exportLauncher.launch(exportFileName)
                            },
                        )
                        SecondaryButton(
                            label = stringResource(
                                if (state.clearing) {
                                    R.string.prompt_lexicon_clearing
                                } else {
                                    R.string.prompt_lexicon_clear_external
                                },
                            ),
                            loading = state.clearing,
                            enabled = !state.clearing && !state.importing &&
                                state.externalCount > 0,
                            modifier = Modifier.weight(1f),
                            onClick = { showClearConfirm = true },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.prompt_lexicon_clear_confirm_title)) },
            text = { Text(stringResource(R.string.prompt_lexicon_clear_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearExternal()
                    },
                ) {
                    Text(stringResource(R.string.prompt_lexicon_clear_external))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    count: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = count,
                style = NekoMonoSmall.copy(fontWeight = FontWeight.Bold),
                color = if (accent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ImportButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (enabled) 1f else 0.5f,
        ),
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                )
            } else {
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
}
