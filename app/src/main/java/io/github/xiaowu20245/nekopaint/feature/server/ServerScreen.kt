package io.github.xiaowu20245.nekopaint.feature.server

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
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
    var deleteTarget by remember { mutableStateOf<ServerProfile?>(null) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refreshActive()
                delay(5_000)
            }
        }
    }
    LaunchedEffect(state.activeCard?.profile?.id) {
        state.activeCard?.let { viewModel.refreshCard(it.profile.id) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.isFormOpen) {
                    item {
                        ServerFormCard(
                            form = state.form,
                            isEdit = state.formMode == ServerFormMode.Edit,
                            isTesting = state.formTesting,
                            isSaving = state.formSaving,
                            snapshot = state.formSnapshot,
                            error = state.formError,
                            canCancel = state.cards.isNotEmpty(),
                            onNameChange = viewModel::onNameChange,
                            onHostChange = viewModel::onHostChange,
                            onPortChange = viewModel::onPortChange,
                            onSchemeChange = viewModel::onSchemeChange,
                            onTest = viewModel::testFormConnection,
                            onSave = viewModel::saveAndConnect,
                            onCancel = viewModel::cancelForm,
                        )
                    }
                }

                if (state.cards.isEmpty() && !state.isFormOpen) {
                    item {
                        EmptyServers(onAdd = viewModel::startAdd)
                    }
                } else {
                    items(state.cards, key = { it.profile.id }) { card ->
                        ServerListCard(
                            card = card,
                            expanded = state.expandedId == card.profile.id,
                            onToggle = { viewModel.toggleExpanded(card.profile.id) },
                            onRefresh = { viewModel.refreshCard(card.profile.id) },
                            onEdit = { viewModel.startEdit(card.profile) },
                            onSetActive = { viewModel.setActive(card.profile) },
                            onDelete = { deleteTarget = card.profile },
                        )
                    }
                }

                if (!state.isFormOpen) {
                    item { SecurityNotice() }
                }
            }

            if (!state.isFormOpen && state.cards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::startAdd,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.server_add)) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                )
            }
        }
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.server_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.server_delete_confirm_message,
                        profile.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(profile)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.server_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyServers(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.server_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.server_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.server_add))
        }
    }
}

@Composable
private fun ServerListCard(
    card: ServerCardUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.profile.name.ifBlank { stringResource(R.string.server_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (card.isActive) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = stringResource(R.string.server_current),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = stringResource(
                        if (expanded) R.string.server_collapse else R.string.server_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.profile.toConfig().baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (card.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                CardStatus(card)
            }

            if (expanded) {
                CardActions(
                    isActive = card.isActive,
                    isTesting = card.isTesting,
                    onRefresh = onRefresh,
                    onEdit = onEdit,
                    onSetActive = onSetActive,
                    onDelete = onDelete,
                )

                card.snapshot?.let { snapshot ->
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

                card.error?.let { error ->
                    Text(
                        text = stringResource(error.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (card.snapshot != null) {
                        Text(
                            text = stringResource(R.string.server_last_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                card.snapshot?.let { snapshot ->
                    GpuSection(snapshot)
                    SystemSection(snapshot.stats)
                }
            }
        }
    }
}

@Composable
private fun CardActions(
    isActive: Boolean,
    isTesting: Boolean,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!isActive) {
            TextButton(onClick = onSetActive, enabled = !isTesting) {
                Text(stringResource(R.string.server_set_current))
            }
        }
        TextButton(onClick = onRefresh, enabled = !isTesting) {
            Text(stringResource(R.string.server_refresh))
        }
        TextButton(onClick = onEdit, enabled = !isTesting) {
            Text(stringResource(R.string.server_edit))
        }
        TextButton(onClick = onDelete, enabled = !isTesting) {
            Text(
                text = stringResource(R.string.server_delete),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CardStatus(card: ServerCardUi) {
    val (label, color, icon) = when {
        card.isTesting -> Triple(
            stringResource(R.string.server_checking),
            MaterialTheme.colorScheme.tertiary,
            Icons.Outlined.Refresh,
        )

        card.isOnline -> Triple(
            stringResource(R.string.server_online),
            MaterialTheme.colorScheme.primary,
            Icons.Outlined.CheckCircle,
        )

        card.error != null -> Triple(
            stringResource(R.string.server_offline),
            MaterialTheme.colorScheme.error,
            Icons.Outlined.Warning,
        )

        else -> Triple(
            stringResource(R.string.server_not_checked),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.Warning,
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ServerFormCard(
    form: ServerForm,
    isEdit: Boolean,
    isTesting: Boolean,
    isSaving: Boolean,
    snapshot: ConnectionSnapshot?,
    error: ServerError?,
    canCancel: Boolean,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSchemeChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val busy = isTesting || isSaving
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (isEdit) R.string.server_edit_title else R.string.server_setup_title,
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
                enabled = !busy,
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
                enabled = !busy,
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
                enabled = !busy,
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
                        enabled = !busy,
                    )
                    FilterChip(
                        selected = form.scheme == "https",
                        onClick = { onSchemeChange("https") },
                        label = { Text(stringResource(R.string.server_https)) },
                        enabled = !busy,
                    )
                }
            }

            snapshot?.let {
                Text(
                    text = stringResource(R.string.server_version, it.stats.comfyUiVersion ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            error?.let {
                Text(
                    text = stringResource(it.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isTesting) {
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
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.server_save_connect))
                }
            }

            if (canCancel) {
                TextButton(onClick = onCancel, enabled = !busy) {
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
            Text(
                text = stringResource(R.string.server_no_gpu),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
