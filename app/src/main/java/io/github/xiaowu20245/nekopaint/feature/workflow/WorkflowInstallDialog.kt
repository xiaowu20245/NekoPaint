package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.feature.common.asString

@Composable
internal fun NodeInstallDialog(
    state: NodeInstallUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onRecheck: () -> Unit,
    onRestartChange: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.installing) onDismiss() },
        title = { Text(stringResource(R.string.workflow_install_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.workflow_install_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.error?.let { error ->
                    Text(
                        text = error.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (state.plan == null &&
                        !state.installing &&
                        error.key != NekoMessageKey.NODE_INSTALL_MAPPING_MISSING_PACKAGES
                    ) {
                        Text(
                            text = stringResource(R.string.workflow_install_prerequisites),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.plan?.let { plan ->
                    Text(
                        text = stringResource(
                            R.string.workflow_install_missing_summary,
                            plan.missingNodeTypes.size,
                            plan.packageCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    plan.binding?.let { binding ->
                        Text(
                            text = stringResource(
                                R.string.workflow_install_target_server,
                                "${binding.host}:${binding.port}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.workflow_install_warning),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(9.dp),
                        )
                    }
                    plan.packages.forEach { pkg ->
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = pkg.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.workflow_install_package_source,
                                        pkg.sourceLabel,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (plan.unresolvedNodeTypes.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.workflow_install_unresolved,
                                plan.unresolvedNodeTypes.take(8).joinToString("、"),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(
                            checked = state.restartAfterInstall,
                            onCheckedChange = onRestartChange,
                            enabled = !state.installing,
                        )
                        Text(
                            text = stringResource(R.string.workflow_install_restart),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (state.installing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.progress?.let { progress ->
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.result?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.remainingMissing.isNotEmpty()) {
                        Text(
                            text = state.remainingMissing.take(10).joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = onRecheck,
                        enabled = !state.loading && !state.installing,
                    ) {
                        Text(stringResource(R.string.workflow_install_recheck))
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.loading || state.installing -> Unit
                state.result != null -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.workflow_close))
                    }
                }

                state.plan != null -> {
                    TextButton(onClick = onConfirm) {
                        Text(
                            stringResource(
                                if (state.restartAfterInstall) {
                                    R.string.workflow_install_confirm_restart
                                } else {
                                    R.string.workflow_install_confirm
                                },
                            ),
                        )
                    }
                }

                state.error != null -> {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.workflow_install_retry))
                    }
                }
            }
        },
        dismissButton = {
            if (!state.installing) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(
                            if (state.result != null) {
                                R.string.workflow_close
                            } else {
                                R.string.action_cancel
                            },
                        ),
                    )
                }
            }
        },
    )
}
