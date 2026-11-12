package io.github.xiaowu20245.nekopaint.feature.server

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.GpuInfo
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.ServerStats
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoEmptyState
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusBadge
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoStatusTone
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoIconSize
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMotion
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoRadius
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoSpacing
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
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
    var avatarForForm by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var argvDialog by remember { mutableStateOf<List<String>?>(null) }
    var showSecurityNotice by remember { mutableStateOf(false) }
    val launchArgsCopiedMessage = stringResource(R.string.server_launch_args_copied)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
        } else {
            avatarForForm = false
        }
    }

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
                if (state.cards.isEmpty() && !state.isFormOpen) {
                    item {
                        EmptyServers(
                            onAdd = {
                                viewModel.startAdd()
                                showSecurityNotice = true
                            },
                        )
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
                            onShowLaunchArgs = { argvDialog = it },
                        )
                    }
                }
            }

            if (!state.isFormOpen && state.cards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.startAdd()
                        showSecurityNotice = true
                    },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.server_add)) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                )
            }
        }
    }

    if (state.isFormOpen) {
        Dialog(onDismissRequest = viewModel::cancelForm) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
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
                    onPickAvatar = {
                        avatarForForm = true
                        avatarPicker.launch("image/*")
                    },
                    onClearAvatar = viewModel::clearFormAvatar,
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
                Button(
                    onClick = {
                        viewModel.delete(profile)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.server_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    argvDialog?.let { args ->
        val formatted = formatArgv(args)
        AlertDialog(
            onDismissRequest = { argvDialog = null },
            title = { Text(stringResource(R.string.server_launch_args_title)) },
            text = {
                Text(
                    text = formatted.ifBlank {
                        stringResource(R.string.server_launch_args_empty)
                    },
                    style = NekoMonoSmall,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("server_args", formatted),
                        )
                        Toast.makeText(
                            context,
                            launchArgsCopiedMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.server_launch_args_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { argvDialog = null }) {
                    Text(stringResource(R.string.server_close))
                }
            },
        )
    }

    if (showSecurityNotice) {
        AlertDialog(
            onDismissRequest = { showSecurityNotice = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = NekoTheme.colors.warning,
                )
            },
            title = { Text(stringResource(R.string.server_security_title)) },
            text = { Text(stringResource(R.string.server_security_message)) },
            confirmButton = {
                TextButton(onClick = { showSecurityNotice = false }) {
                    Text(stringResource(R.string.server_security_ack))
                }
            },
        )
    }

    pendingCropUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onDismiss = {
                pendingCropUri = null
                avatarForForm = false
            },
            onCropped = { bitmap ->
                val forForm = avatarForForm
                pendingCropUri = null
                avatarForForm = false
                if (forForm) {
                    viewModel.setFormAvatar(bitmap)
                }
            },
        )
    }
}

@Composable
private fun EmptyServers(onAdd: () -> Unit) {
    NekoEmptyState(
        icon = NekoIcons.ServerRack,
        title = stringResource(R.string.server_empty_title),
        subtitle = stringResource(R.string.server_empty_desc),
        actionLabel = stringResource(R.string.server_add),
        onAction = onAdd,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerListCard(
    card: ServerCardUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    onShowLaunchArgs: (List<String>) -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (card.isActive) {
                lerp(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.colorScheme.primaryContainer,
                    0.22f,
                )
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = null,
    ) {
        Column(
            modifier = Modifier
                .padding(NekoSpacing.cardPadding)
                .animateContentSize(
                    animationSpec = tween(NekoMotion.StandardMs),
                ),
            verticalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ServerAvatar(
                    avatarUri = card.profile.avatarUri,
                    name = card.profile.name,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = card.profile.name.ifBlank {
                                stringResource(R.string.server_unknown)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (card.isActive) {
                            Spacer(Modifier.width(NekoSpacing.sm))
                            NekoStatusBadge(
                                text = stringResource(R.string.server_current),
                                tone = NekoStatusTone.NEUTRAL,
                                icon = null,
                            )
                        }
                    }
                    Text(
                        text = card.profile.toConfig().baseUrl,
                        style = NekoMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    card.snapshot?.let { snapshot ->
                        if (expanded) {
                            // 延迟与更新时间是两个不可拆分的原子项；窄屏时更新时间整行下移。
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.wrapContentWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(NekoSpacing.xxs),
                                ) {
                                    Icon(
                                        imageVector = NekoIcons.Stopwatch,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(NekoIconSize.xs),
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.server_latency,
                                            snapshot.latencyMs,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.server_last_updated,
                                        formatTime(snapshot.checkedAt),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        } else {
                            CollapsedMetrics(snapshot)
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NekoSpacing.xs),
                ) {
                    ServerStatusColumn(card)
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.server_collapse
                            } else {
                                R.string.server_expand
                            },
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = NekoSpacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                CardActions(
                    isActive = card.isActive,
                    isTesting = card.isTesting,
                    onRefresh = onRefresh,
                    onEdit = onEdit,
                    onSetActive = onSetActive,
                    onDelete = onDelete,
                )

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
                    SystemSection(
                        stats = snapshot.stats,
                        onShowLaunchArgs = {
                            onShowLaunchArgs(snapshot.stats.argv.orEmpty())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerAvatar(
    avatarUri: String?,
    name: String,
    onClick: (() -> Unit)? = null,
) {
    val avatarDescription = stringResource(R.string.server_avatar_desc, name)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.server_choose_icon),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = avatarDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = NekoIcons.ServerRack,
                contentDescription = avatarDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
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
            TextButton(
                onClick = onSetActive,
                enabled = !isTesting,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(NekoIconSize.sm),
                )
                Spacer(Modifier.width(NekoSpacing.xs))
                Text(
                    text = stringResource(R.string.server_set_current),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        TextButton(
            onClick = onRefresh,
            enabled = !isTesting,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(NekoIconSize.sm),
            )
            Spacer(Modifier.width(NekoSpacing.xs))
            Text(stringResource(R.string.server_refresh))
        }
        TextButton(
            onClick = onEdit,
            enabled = !isTesting,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(NekoIconSize.sm),
            )
            Spacer(Modifier.width(NekoSpacing.xs))
            Text(stringResource(R.string.server_edit))
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDelete,
            enabled = !isTesting,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                modifier = Modifier.size(NekoIconSize.sm),
            )
            Spacer(Modifier.width(NekoSpacing.xs))
            Text(text = stringResource(R.string.server_delete))
        }
    }
}

@Composable
private fun ServerStatusBadge(card: ServerCardUi) {
    val text = when {
        card.isTesting -> stringResource(R.string.server_checking)
        card.isOnline -> stringResource(R.string.server_online)
        card.error != null -> stringResource(R.string.server_offline)
        else -> stringResource(R.string.server_not_checked)
    }
    val tone = when {
        card.isTesting -> NekoStatusTone.WARNING
        card.isOnline -> NekoStatusTone.SUCCESS
        card.error != null -> NekoStatusTone.DANGER
        else -> NekoStatusTone.WARNING
    }
    NekoStatusBadge(
        text = text,
        tone = tone,
        icon = if (card.isTesting) Icons.Outlined.Refresh else defaultStatusIcon(tone),
    )
}

@Composable
private fun ServerStatusColumn(card: ServerCardUi) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(NekoSpacing.xxs),
    ) {
        ServerStatusBadge(card)
        if (card.isOnline) {
            val activeCount =
                (card.snapshot?.runningCount ?: 0) + (card.snapshot?.pendingCount ?: 0)
            NekoStatusBadge(
                text = stringResource(R.string.server_task_count, activeCount),
                tone = if (activeCount > 0) {
                    NekoStatusTone.INFO
                } else {
                    NekoStatusTone.NEUTRAL
                },
                icon = Icons.Outlined.PlayArrow,
            )
        }
    }
}

private fun defaultStatusIcon(tone: NekoStatusTone) = when (tone) {
    NekoStatusTone.SUCCESS -> Icons.Outlined.CheckCircle
    NekoStatusTone.WARNING -> Icons.Outlined.Warning
    NekoStatusTone.DANGER -> Icons.Outlined.Warning
    NekoStatusTone.INFO,
    NekoStatusTone.NEUTRAL,
    -> Icons.Outlined.Warning
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
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
) {
    val busy = isTesting || isSaving
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (isEdit) R.string.server_edit_title else R.string.server_add,
            ),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = stringResource(R.string.server_setup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NekoRadius.md))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.md),
        ) {
            ServerAvatar(
                avatarUri = form.avatarUri,
                name = form.name.ifBlank { ServerConfig.DEFAULT_NAME },
                onClick = onPickAvatar,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.server_choose_icon),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = stringResource(R.string.server_avatar_support_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onPickAvatar,
                enabled = !busy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.server_avatar_pick_crop))
            }
        }
        if (form.avatarUri != null) {
            TextButton(
                onClick = onClearAvatar,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.server_remove_icon))
            }
        }

        OutlinedTextField(
            value = form.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.server_name)) },
            singleLine = true,
            enabled = !busy,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
        ) {
            OutlinedTextField(
                value = form.host,
                onValueChange = onHostChange,
                modifier = Modifier.weight(2f),
                label = { Text(stringResource(R.string.server_host)) },
                placeholder = { Text(stringResource(R.string.server_host_hint)) },
                singleLine = true,
                isError = form.hostError != null,
                supportingText = form.hostError?.let { hostError ->
                    { Text(stringResource(hostError.messageRes)) }
                },
                enabled = !busy,
            )
            OutlinedTextField(
                value = form.port,
                onValueChange = onPortChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.server_port)) },
                singleLine = true,
                isError = form.portError != null,
                supportingText = form.portError?.let { portError ->
                    { Text(stringResource(portError.messageRes)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !busy,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.server_scheme),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm)) {
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

        snapshot?.let { successful ->
            ConnectionTestSuccessBanner(successful)
        }
        if (snapshot == null) {
            error?.let { failure ->
                ConnectionTestFailureBanner(failure)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NekoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canCancel) {
                TextButton(onClick = onCancel, enabled = !busy) {
                    Text(stringResource(R.string.server_cancel))
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onTest,
                enabled = !busy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(NekoSpacing.xs))
                }
                Text(stringResource(R.string.server_test))
            }
            Spacer(Modifier.width(NekoSpacing.sm))
            Button(
                onClick = onSave,
                enabled = !busy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(NekoSpacing.xs))
                }
                Text(stringResource(R.string.server_save_connect))
            }
        }
    }
}

@Composable
private fun ConnectionTestSuccessBanner(snapshot: ConnectionSnapshot) {
    val colors = NekoTheme.colors
    val version = snapshot.stats.comfyUiVersion ?: stringResource(R.string.server_unknown)
    val gpu = snapshot.stats.devices.firstOrNull()?.let { parseGpuName(it.name).model }
    val versionText = stringResource(R.string.server_test_success_version, version)
    val detail = if (gpu != null) {
        stringResource(R.string.server_test_success_detail, versionText, gpu)
    } else {
        versionText
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NekoRadius.md),
        color = colors.successContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(NekoIconSize.sm),
            )
            Column(verticalArrangement = Arrangement.spacedBy(NekoSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.server_test_success, snapshot.latencyMs),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSuccessContainer,
                )
                Text(
                    text = detail,
                    style = NekoMonoSmall,
                    color = colors.onSuccessContainer,
                )
            }
        }
    }
}

@Composable
private fun ConnectionTestFailureBanner(error: ServerError) {
    val colors = NekoTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NekoRadius.md),
        color = colors.dangerContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(NekoIconSize.sm),
            )
            Column(verticalArrangement = Arrangement.spacedBy(NekoSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.server_test_failure),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onDangerContainer,
                )
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDangerContainer,
                )
            }
        }
    }
}

@Composable
private fun CollapsedMetrics(snapshot: ConnectionSnapshot) {
    val gpu = snapshot.stats.devices.firstOrNull()
    BoxWithConstraints {
        // 列宽不足时只缩小字号与图标，仍保持三项单行，避免撑高卡片。
        val compact = maxWidth < 160.dp
        val style = if (compact) {
            MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
        } else {
            MaterialTheme.typography.bodySmall
        }
        val iconSize = if (compact) 12.dp else NekoIconSize.xs
        val itemGap = if (compact) 1.dp else 2.dp
        val rowSpacing = if (compact) 2.dp else 4.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            gpu?.let {
                val parts = parseGpuName(it.name)
                MetricIconText(
                    icon = NekoIcons.Chip,
                    text = parts.model,
                    iconTint = MaterialTheme.colorScheme.primary,
                    style = style,
                    iconSize = iconSize,
                    itemGap = itemGap,
                )
                MetricIconText(
                    icon = NekoIcons.MemoryStick,
                    text = stringResource(
                        R.string.server_vram_percent_short,
                        (it.vramUsedFraction.coerceIn(0f, 1f) * 100).roundToInt(),
                    ),
                    iconTint = NekoTheme.colors.success,
                    style = style,
                    iconSize = iconSize,
                    itemGap = itemGap,
                )
            }
            MetricIconText(
                icon = NekoIcons.Stopwatch,
                text = stringResource(R.string.server_latency, snapshot.latencyMs),
                style = style,
                iconSize = iconSize,
                itemGap = itemGap,
            )
        }
    }
}

@Composable
private fun MetricIconText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    style: TextStyle,
    iconSize: Dp,
    itemGap: Dp,
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(itemGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun GpuSection(snapshot: ConnectionSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(NekoSpacing.sm)) {
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
    val usedFraction = gpu.vramUsedFraction.coerceIn(0f, 1f)
    val percent = (usedFraction * 100).roundToInt()
    val semantic = NekoTheme.colors
    val progressColor = when {
        usedFraction < 0.7f -> semantic.success
        usedFraction <= 0.9f -> semantic.warning
        else -> semantic.danger
    }
    val nameParts = parseGpuName(gpu.name)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NekoRadius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(NekoSpacing.cardPaddingCompact),
            verticalArrangement = Arrangement.spacedBy(NekoSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = NekoIcons.Chip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(NekoIconSize.sm),
                )
                Spacer(Modifier.width(NekoSpacing.xxs))
                Text(
                    text = stringResource(
                        R.string.server_gpu_header,
                        nameParts.full.removeSuffix(" GPU"),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                nameParts.cuda?.let { cuda ->
                    Spacer(Modifier.width(NekoSpacing.xs))
                    Text(
                        text = cuda,
                        style = NekoMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = NekoIcons.MemoryStick,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(NekoIconSize.xs),
                )
                Spacer(Modifier.width(NekoSpacing.xxs))
                Text(
                    text = stringResource(
                        R.string.server_vram_detail,
                        formatBytes(gpu.vramUsed),
                        formatBytes(gpu.vramTotal),
                        formatBytes(gpu.vramFree),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(NekoSpacing.xs))
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleSmall,
                    color = progressColor,
                )
            }
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(NekoRadius.xs)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

private data class GpuNameParts(
    val full: String,
    val model: String,
    val cuda: String?,
)

private fun parseGpuName(raw: String?): GpuNameParts {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) {
        return GpuNameParts(
            full = "GPU",
            model = "GPU",
            cuda = null,
        )
    }
    val prefix = Regex("^(cuda:\\d+)").find(value)?.value
    val full = value
        .replace(Regex("^cuda:\\d+\\s*"), "")
        .replace(Regex("\\s*:\\s*cudaMallocAsync\\s*$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { value }
    val short = full
        .removePrefix("NVIDIA GeForce ")
        .removeSuffix(" GPU")
        .trim()
        .ifBlank { full }
    val model = Regex("RTX\\s*\\d{4}").find(full)?.value ?: short
    return GpuNameParts(full = full, model = model, cuda = prefix)
}

@Composable
private fun SystemSection(
    stats: ServerStats,
    onShowLaunchArgs: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NekoRadius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(NekoSpacing.cardPaddingCompact),
            verticalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = NekoIcons.Display,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(NekoIconSize.sm),
                )
                Spacer(Modifier.width(NekoSpacing.xxs))
                Text(
                    text = stringResource(R.string.server_system_title),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onShowLaunchArgs,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.8f,
                        ),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Icon(
                        imageVector = NekoIcons.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(NekoIconSize.xs),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.server_launch_args),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SystemMetric(
                    icon = NekoIcons.Display,
                    label = "OS",
                    value = stats.os ?: stringResource(R.string.server_unknown),
                    modifier = Modifier.weight(1f),
                )
                SystemMetric(
                    icon = NekoIcons.Terminal,
                    label = "ComfyUI",
                    value = stats.comfyUiVersion ?: stringResource(R.string.server_unknown),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SystemMetric(
                    icon = NekoIcons.Code,
                    label = "Python",
                    value = shortPythonVersion(stats.pythonVersion)
                        ?: stringResource(R.string.server_unknown),
                    modifier = Modifier.weight(1f),
                )
                SystemMetric(
                    icon = NekoIcons.Chip,
                    label = "PyTorch",
                    value = stats.pytorchVersion ?: stringResource(R.string.server_unknown),
                    modifier = Modifier.weight(1f),
                )
            }
            stats.ramTotal?.let { total ->
                val used = (total - (stats.ramFree ?: 0L)).coerceAtLeast(0L)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NekoSpacing.xs),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.xs),
                        ) {
                            Icon(
                                imageVector = NekoIcons.MemoryStick,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(NekoIconSize.xs),
                            )
                            Text(
                                text = stringResource(R.string.server_system_memory),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${formatBytes(used)} / ${formatBytes(total)}",
                            style = NekoMonoSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NekoSpacing.xs),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(NekoIconSize.xs),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = NekoMonoSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun shortPythonVersion(value: String?): String? = value
    ?.substringBefore(' ')
    ?.substringBefore('(')
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun formatArgv(argv: List<String>): String = argv
    .mapIndexed { index, arg ->
        if (index == 0) {
            arg.substringAfterLast('/').substringAfterLast('\\')
        } else {
            arg
        }
    }
    .joinToString("\n")
