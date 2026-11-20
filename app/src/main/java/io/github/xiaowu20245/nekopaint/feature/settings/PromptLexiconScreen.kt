package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoIconButton

@OptIn(ExperimentalLayoutApi::class)
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
    var showAddTag by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    var newTagZh by remember { mutableStateOf("") }
    var tagDialogError by remember { mutableStateOf<String?>(null) }
    val exportFileName = stringResource(R.string.prompt_lexicon_export_file)
    val tagRequiredMessage = stringResource(R.string.prompt_lexicon_custom_required)
    val tagDuplicateMessage = stringResource(R.string.prompt_lexicon_custom_duplicate)

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
        is PromptLexiconNotice.CustomTagAdded -> stringResource(
            R.string.prompt_lexicon_custom_added,
            notice.tag,
        )
        is PromptLexiconNotice.CustomTagRemoved -> stringResource(
            R.string.prompt_lexicon_custom_removed,
            notice.tag,
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
                LexiconStatsCard(
                    builtIn = state.builtInCount,
                    external = state.externalCount,
                    custom = state.customCount,
                )
            }

            item {
                SettingsSection(title = stringResource(R.string.prompt_lexicon_custom_title)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_lexicon_custom_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.customTags.isEmpty()) {
                            Text(
                                text = stringResource(R.string.prompt_lexicon_custom_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                state.customTags.forEach { tag ->
                                    CustomTagChip(
                                        tag = tag,
                                        removing = state.removingCustomTag == tag,
                                        onRemove = { viewModel.removeCustomTag(tag) },
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                newTag = ""
                                newTagZh = ""
                                tagDialogError = null
                                showAddTag = true
                            },
                            enabled = !state.addingCustom,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.prompt_lexicon_custom_add),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.prompt_lexicon_import_title)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_lexicon_import_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { importLauncher.launch("*/*") },
                            enabled = !state.importing && !state.clearing,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                        ) {
                            if (state.importing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.prompt_lexicon_import))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch(exportFileName) },
                                enabled = !state.exporting && !state.importing,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                if (state.exporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.prompt_lexicon_export_groups),
                                        maxLines = 1,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showClearConfirm = true },
                                enabled = !state.clearing && !state.importing &&
                                    state.externalCount > 0,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                if (state.clearing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.prompt_lexicon_clear_external),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
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
        NekoConfirmDialog(
            title = stringResource(R.string.prompt_lexicon_clear_confirm_title),
            message = stringResource(R.string.prompt_lexicon_clear_confirm_msg),
            confirmLabel = stringResource(R.string.prompt_lexicon_clear_external),
            onConfirm = {
                showClearConfirm = false
                viewModel.clearExternal()
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    if (showAddTag) {
        AlertDialog(
            onDismissRequest = {
                if (!state.addingCustom) showAddTag = false
            },
            title = { Text(stringResource(R.string.prompt_lexicon_custom_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = {
                            if (it.length <= 64) {
                                newTag = it
                                tagDialogError = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.prompt_lexicon_custom_tag_label)) },
                        placeholder = {
                            Text(stringResource(R.string.prompt_lexicon_custom_tag_hint))
                        },
                        isError = tagDialogError != null,
                        supportingText = tagDialogError?.let { message ->
                            { Text(message) }
                        },
                    )
                    OutlinedTextField(
                        value = newTagZh,
                        onValueChange = { newTagZh = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.prompt_lexicon_custom_zh_label)) },
                        placeholder = {
                            Text(stringResource(R.string.prompt_lexicon_custom_zh_hint))
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tag = newTag.trim()
                        val error = when {
                            tag.isBlank() -> tagRequiredMessage
                            state.customTags.any { it.equals(tag, ignoreCase = true) } ->
                                tagDuplicateMessage
                            else -> null
                        }
                        if (error != null) {
                            tagDialogError = error
                        } else {
                            viewModel.addCustomTag(tag, newTagZh)
                            showAddTag = false
                        }
                    },
                    enabled = !state.addingCustom,
                ) {
                    Text(stringResource(R.string.prompt_lexicon_custom_add_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddTag = false },
                    enabled = !state.addingCustom,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LexiconStatsCard(
    builtIn: Int,
    external: Int,
    custom: Int,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LexiconStat(
                count = builtIn,
                label = stringResource(R.string.prompt_lexicon_stats_builtin),
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            LexiconStat(
                count = external,
                label = stringResource(R.string.prompt_lexicon_stats_external),
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            LexiconStat(
                count = custom,
                label = stringResource(R.string.prompt_lexicon_stats_custom),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LexiconStat(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun CustomTagChip(
    tag: String,
    removing: Boolean,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.widthIn(max = 220.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (removing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(2.dp),
                    strokeWidth = 1.5.dp,
                )
            } else {
                NekoIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(
                        R.string.prompt_lexicon_custom_delete,
                        tag,
                    ),
                    onClick = onRemove,
                )
            }
        }
    }
}
