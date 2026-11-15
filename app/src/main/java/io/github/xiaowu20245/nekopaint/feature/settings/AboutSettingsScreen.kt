package io.github.xiaowu20245.nekopaint.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import kotlinx.coroutines.launch

@Composable
fun AboutSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app.container),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var exportingDiagnostics by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val diagnosticsSubject = stringResource(R.string.settings_diagnostics_subject)
    val diagnosticsChooser = stringResource(R.string.settings_diagnostics_chooser)
    val diagnosticsFailed = stringResource(R.string.settings_diagnostics_failed)
    val githubUrl = stringResource(R.string.settings_about_github_url)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_about_group_app)) {
                    SettingsValueRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.settings_about_version),
                        value = BuildConfig.VERSION_NAME,
                    )
                    SettingsRowDivider()
                    SettingsValueRow(
                        icon = NekoIcons.Code,
                        title = stringResource(R.string.settings_about_package),
                        value = BuildConfig.APPLICATION_ID,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_diagnostics_title)) {
                    SettingsActionRow(
                        icon = NekoIcons.Terminal,
                        title = stringResource(R.string.settings_diagnostics_export),
                        summary = stringResource(R.string.settings_diagnostics_desc),
                        actionLabel = stringResource(R.string.settings_diagnostics_export_action),
                        loading = exportingDiagnostics,
                        onAction = {
                            if (!exportingDiagnostics) {
                                exportingDiagnostics = true
                                scope.launch {
                                    val report = runCatching {
                                        viewModel.buildDiagnostics()
                                    }.getOrNull()
                                    exportingDiagnostics = false
                                    if (report == null) {
                                        snackbarHostState.showSnackbar(diagnosticsFailed)
                                    } else {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, diagnosticsSubject)
                                            putExtra(Intent.EXTRA_TEXT, report)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(send, diagnosticsChooser),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_about_privacy_title)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_about_privacy_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.settings_security_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_about_resources)) {
                    SettingsNavigationRow(
                        icon = NekoIcons.ContentCopy,
                        title = stringResource(R.string.settings_about_licenses_title),
                        summary = stringResource(R.string.settings_about_licenses_desc),
                        onClick = { showLicenses = true },
                    )
                    SettingsRowDivider()
                    SettingsNavigationRow(
                        icon = NekoIcons.Code,
                        title = stringResource(R.string.settings_about_github_title),
                        summary = stringResource(R.string.settings_about_github_desc),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, githubUrl.toUri()),
                                )
                            }
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_about_licenses_dialog_title)) },
            text = { Text(stringResource(R.string.settings_about_licenses_content)) },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}
