package io.github.xiaowu20245.nekopaint.feature.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.GpuInfo
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun ServerScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: ServerViewModel = viewModel(
        factory = ServerViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refreshIfConfigured()
                delay(5_000)
            }
        }
    }

    ServerContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onSchemeChange = viewModel::onSchemeChange,
        onTest = viewModel::testConnection,
        onSave = viewModel::saveAndConnect,
        onEdit = viewModel::edit,
        onCancel = viewModel::cancelEdit,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun ServerContent(
    state: ServerUiState,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSchemeChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val displayConfig = state.config
    val address = if (state.isEditing) {
        state.form.toConfigOrNull()?.baseUrl ?: ""
    } else {
        displayConfig?.baseUrl ?: ""
    }
    val displayName = if (state.isEditing) {
        state.form.name.ifBlank { ServerConfig.DEFAULT_NAME }
    } else {
        displayConfig?.name ?: ServerConfig.DEFAULT_NAME
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.config != null || state.snapshot != null || state.error != null || state.isTesting) {
            item {
                ConnectionCard(
                    state = state,
                    name = displayName,
                    address = address,
                    onEdit = onEdit,
                    onRefresh = onRefresh,
                )
            }
        }

        if (state.isEditing || state.config == null) {
            item {
                ServerFormCard(
                    state = state,
                    onNameChange = onNameChange,
                    onHostChange = onHostChange,
                    onPortChange = onPortChange,
                    onSchemeChange = onSchemeChange,
                    onTest = onTest,
                    onSave = onSave,
                    onCancel = onCancel,
                )
            }
        }

        if (state.snapshot != null && !state.isEditing) {
            item {
                GpuSection(state.snapshot)
            }
            item {
                SystemSection(state.snapshot.stats)
            }
        }

        item {
            SecurityNotice()
        }
    }
}

@Composable
private fun ConnectionCard(
    state: ServerUiState,
    name: String,
    address: String,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(state)
                Spacer(Modifier.weight(1f))
                if (state.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = state.config != null && !state.isBusy,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.server_refresh),
                    )
                }
                if (state.config != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.server_edit),
                        )
                    }
                }
            }

            Text(
                text = name.ifBlank { stringResource(R.string.server_unknown) },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val snapshot = state.snapshot
            if (snapshot != null) {
                Text(
                    text = snapshot.stats.comfyUiVersion?.let {
                        stringResource(R.string.server_version, it)
                    } ?: stringResource(R.string.server_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.server_latency, snapshot.latencyMs) +
                        " · " + stringResource(
                        R.string.server_last_updated,
                        formatTime(snapshot.checkedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (snapshot != null) {
                    Text(
                        text = stringResource(R.string.server_last_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: ServerUiState) {
    val (label, color, icon) = when {
        state.isTesting -> Triple(
            stringResource(R.string.server_checking),
            MaterialTheme.colorScheme.tertiary,
            Icons.Outlined.Refresh,
        )

        state.isOnline -> Triple(
            stringResource(R.string.server_online),
            MaterialTheme.colorScheme.primary,
            Icons.Outlined.CheckCircle,
        )

        else -> Triple(
            stringResource(R.string.server_offline),
            MaterialTheme.colorScheme.error,
            Icons.Outlined.Warning,
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ServerFormCard(
    state: ServerUiState,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSchemeChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val form = state.form
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (state.config == null) {
                        R.string.server_setup_title
                    } else {
                        R.string.server_edit_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.server_setup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server_name)) },
                singleLine = true,
                enabled = !state.isBusy,
            )
            OutlinedTextField(
                value = form.host,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server_host)) },
                placeholder = { Text(stringResource(R.string.server_host_hint)) },
                singleLine = true,
                isError = form.hostError != null,
                supportingText = form.hostError?.let { error ->
                    { Text(stringResource(error.messageRes)) }
                },
                enabled = !state.isBusy,
            )
            OutlinedTextField(
                value = form.port,
                onValueChange = onPortChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server_port)) },
                singleLine = true,
                isError = form.portError != null,
                supportingText = form.portError?.let { error ->
                    { Text(stringResource(error.messageRes)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isBusy,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.server_scheme),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.scheme == "http",
                        onClick = { onSchemeChange("http") },
                        label = { Text(stringResource(R.string.server_http)) },
                        enabled = !state.isBusy,
                    )
                    FilterChip(
                        selected = form.scheme == "https",
                        onClick = { onSchemeChange("https") },
                        label = { Text(stringResource(R.string.server_https)) },
                        enabled = !state.isBusy,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.server_test))
                }
                Button(
                    onClick = onSave,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.server_save_connect))
                }
            }

            if (state.config != null) {
                TextButton(
                    onClick = onCancel,
                    enabled = !state.isBusy,
                ) {
                    Text(stringResource(R.string.server_cancel))
                }
            }
        }
    }
}

@Composable
private fun GpuSection(snapshot: ConnectionSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.server_gpu),
            style = MaterialTheme.typography.titleMedium,
        )
        if (snapshot.stats.devices.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.server_no_gpu),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            snapshot.stats.devices.forEach { gpu ->
                GpuCard(gpu)
            }
        }
    }
}

@Composable
private fun GpuCard(gpu: GpuInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = gpu.name ?: stringResource(R.string.server_unknown),
                style = MaterialTheme.typography.titleSmall,
            )
            Row {
                Text(
                    text = stringResource(R.string.server_vram),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(gpu.vramUsedFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LinearProgressIndicator(
                progress = { gpu.vramUsedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            InfoRow(
                label = stringResource(R.string.server_vram_total),
                value = formatBytes(gpu.vramTotal),
            )
            InfoRow(
                label = stringResource(R.string.server_vram_free),
                value = formatBytes(gpu.vramFree),
            )
            InfoRow(
                label = stringResource(R.string.server_vram_used),
                value = formatBytes(gpu.vramUsed),
            )
        }
    }
}

@Composable
private fun SystemSection(stats: ServerStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.server_os),
                style = MaterialTheme.typography.titleMedium,
            )
            InfoRow(
                label = "OS",
                value = stats.os ?: stringResource(R.string.server_unknown),
            )
            InfoRow(
                label = "ComfyUI",
                value = stats.comfyUiVersion ?: stringResource(R.string.server_unknown),
            )
            InfoRow(
                label = stringResource(R.string.server_python),
                value = stats.pythonVersion ?: stringResource(R.string.server_unknown),
            )
            InfoRow(
                label = stringResource(R.string.server_pytorch),
                value = stats.pytorchVersion ?: stringResource(R.string.server_unknown),
            )
            if (stats.ramTotal != null) {
                InfoRow(
                    label = stringResource(R.string.server_ram),
                    value = stringResource(
                        R.string.server_ram_value,
                        formatBytes(stats.ramFree ?: 0L),
                        formatBytes(stats.ramTotal),
                    ),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SecurityNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.server_security_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.server_security_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
