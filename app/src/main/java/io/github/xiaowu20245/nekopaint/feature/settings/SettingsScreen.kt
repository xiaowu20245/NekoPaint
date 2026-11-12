package io.github.xiaowu20245.nekopaint.feature.settings

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.datastore.ThemeMode
import io.github.xiaowu20245.nekopaint.core.ui.format.formatFileSize
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenLexicon: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var exportingDiagnostics by remember { mutableStateOf(false) }
    val clearedMessage = stringResource(R.string.settings_cache_cleared)
    val errorMessage = state.error?.let { message ->
        stringResource(R.string.settings_cache_clear_failed, message)
    }
    val loadingLabel = stringResource(R.string.settings_loading)
    val diagnosticsSubject = stringResource(R.string.settings_diagnostics_subject)
    val diagnosticsChooser = stringResource(R.string.settings_diagnostics_chooser)
    val diagnosticsFailed = stringResource(R.string.settings_diagnostics_failed)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // 返回设置页时静默刷新，避免缓存卡片在“读取中”和数值之间闪烁。
        viewModel.refreshCacheSize(silent = true)
    }

    LaunchedEffect(state.cacheCleared) {
        if (state.cacheCleared) {
            snackbarHostState.showSnackbar(clearedMessage)
            viewModel.consumeCacheCleared()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val lexiconSummary = if (state.externalWords > 0) {
        stringResource(
            R.string.settings_lexicon_summary,
            state.builtInWords,
            state.externalWords,
        )
    } else {
        stringResource(R.string.settings_lexicon_summary_none, state.builtInWords)
    }
    val lexiconTotal = stringResource(
        R.string.settings_lexicon_total_badge,
        state.builtInWords + state.externalWords,
    )
    val aiProviderLabelRes = AiProviderPreset.fromId(state.aiConfig.providerId)?.labelRes
    val aiProviderLabel = if (aiProviderLabelRes != null) {
        stringResource(aiProviderLabelRes)
    } else {
        stringResource(R.string.ai_assistant_provider_custom)
    }
    val aiSummary = if (state.aiConfig.isConfigured) {
        stringResource(
            R.string.settings_ai_summary_configured,
            aiProviderLabel,
            state.aiConfig.textModel,
        )
    } else {
        stringResource(R.string.settings_ai_summary_unconfigured)
    }
    val aiBadge = stringResource(
        if (state.aiConfig.isConfigured) {
            R.string.settings_ai_badge_configured
        } else {
            R.string.settings_ai_badge_unconfigured
        },
    )
    val cacheTitle = if (state.loading) {
        stringResource(R.string.settings_cache_title)
    } else {
        "${stringResource(R.string.settings_cache_title)} (${formatFileSize(state.cacheBytes)})"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_prompt_ai),
                ) {
                    SettingsEntryRow(
                        icon = NekoIcons.Bookmark,
                        title = stringResource(R.string.settings_lexicon_entry),
                        summary = lexiconSummary,
                        badge = lexiconTotal,
                        onClick = onOpenLexicon,
                    )
                    SettingsEntryRow(
                        icon = NekoIcons.Sparkles,
                        title = stringResource(R.string.settings_ai_entry),
                        summary = aiSummary,
                        badge = aiBadge,
                        badgeAccent = state.aiConfig.isConfigured,
                        onClick = onOpenAiSettings,
                    )
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_appearance),
                ) {
                    InnerCard {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_theme_title),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    ThemePill(
                                        label = stringResource(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                                ThemeMode.DARK -> R.string.settings_theme_dark
                                            },
                                        ),
                                        selected = state.themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                    )
                                }
                            }
                        }
                    }
                    InnerCard {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_show_temp_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                Text(
                                    text = stringResource(R.string.settings_show_temp_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.showTempPreviews,
                                onCheckedChange = viewModel::setShowTempPreviews,
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroupCard(
                    title = stringResource(R.string.settings_group_storage),
                ) {
                    InnerCard {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = cacheTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                Text(
                                    text = if (state.loading) {
                                        loadingLabel
                                    } else {
                                        stringResource(R.string.settings_cache_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (state.loading || state.clearing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                CompactButton(
                                    label = stringResource(R.string.settings_clear_cache),
                                    enabled = state.cacheBytes > 0,
                                    onClick = { showClearConfirm = true },
                                )
                            }
                        }
                    }
                    InnerCard {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_diagnostics_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                Text(
                                    text = stringResource(R.string.settings_diagnostics_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (exportingDiagnostics) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                CompactButton(
                                    label = stringResource(R.string.settings_diagnostics_export),
                                    onClick = {
                                        if (!exportingDiagnostics) {
                                            exportingDiagnostics = true
                                            scope.launch {
                                                val report = runCatching {
                                                    viewModel.buildDiagnostics()
                                                }.getOrNull()
                                                exportingDiagnostics = false
                                                if (report == null) {
                                                    snackbarHostState.showSnackbar(
                                                        diagnosticsFailed,
                                                    )
                                                } else {
                                                    val send = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(
                                                            Intent.EXTRA_SUBJECT,
                                                            diagnosticsSubject,
                                                        )
                                                        putExtra(Intent.EXTRA_TEXT, report)
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            send,
                                                            diagnosticsChooser,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    InnerCard {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_about_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                Text(
                                    text = stringResource(R.string.settings_security_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.settings_version,
                                    BuildConfig.VERSION_NAME,
                                ),
                                style = NekoMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearCache()
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_cache))
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
internal fun InnerCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    badge: String,
    onClick: () -> Unit,
    badgeAccent: Boolean = false,
) {
    InnerCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = if (badgeAccent) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                contentColor = if (badgeAccent) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = badge,
                    style = NekoMonoSmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = NekoIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ThemePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (enabled) 1f else 0.45f,
        ),
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}
