package io.github.xiaowu20245.nekopaint.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.format.formatFileSize
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import kotlinx.coroutines.launch

@Composable
fun StorageSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearImageCacheConfirm by remember { mutableStateOf(false) }
    var showClearChatImagesConfirm by remember { mutableStateOf(false) }

    val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)
    val chatImagesClearedMessage = stringResource(R.string.settings_chat_images_cleared)
    val errorMessage = state.error?.let { message ->
        stringResource(R.string.settings_cache_clear_failed, message)
    }
    val permissionDeniedMessage = stringResource(R.string.image_save_permission_denied)
    val openSettingsLabel = stringResource(R.string.image_save_open_settings)

    val autoSavePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setAutoSaveHistoryImages(true)
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = permissionDeniedMessage,
                    actionLabel = openSettingsLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        },
                    )
                }
            }
        }
    }

    val requestAutoSaveToggle: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setAutoSaveHistoryImages(false)
        } else {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                autoSavePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                viewModel.setAutoSaveHistoryImages(true)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCacheSize(silent = true)
        viewModel.refreshChatImageStats()
    }

    LaunchedEffect(state.cacheCleared) {
        if (state.cacheCleared) {
            snackbarHostState.showSnackbar(cacheClearedMessage)
            viewModel.consumeCacheCleared()
        }
    }
    LaunchedEffect(state.chatImagesCleared) {
        if (state.chatImagesCleared) {
            snackbarHostState.showSnackbar(chatImagesClearedMessage)
            viewModel.consumeChatImagesCleared()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val cacheTitle = if (state.loading) {
        stringResource(R.string.settings_cache_title)
    } else {
        stringResource(
            R.string.settings_cache_title_with_size,
            formatFileSize(state.cacheBytes),
        )
    }
    val chatImagesSummary = stringResource(
        R.string.settings_chat_images_desc,
        state.chatImageCount,
        formatFileSize(state.chatImageBytes),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_group_image_save)) {
                    SettingsSwitchRow(
                        icon = NekoIcons.ImageFrame,
                        title = stringResource(R.string.settings_auto_save_title),
                        summary = stringResource(R.string.settings_auto_save_desc),
                        checked = state.autoSaveHistoryImages,
                        onCheckedChange = requestAutoSaveToggle,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_group_cache)) {
                    SettingsActionRow(
                        icon = NekoIcons.Download,
                        title = cacheTitle,
                        summary = if (state.loading) {
                            stringResource(R.string.settings_loading)
                        } else {
                            stringResource(R.string.settings_cache_desc)
                        },
                        actionLabel = stringResource(R.string.settings_clear_cache),
                        enabled = state.cacheBytes > 0 && !state.clearing,
                        loading = state.clearing,
                        onAction = { showClearImageCacheConfirm = true },
                    )
                    SettingsRowDivider()
                    SettingsActionRow(
                        icon = NekoIcons.Toolbox,
                        title = stringResource(R.string.settings_chat_images_title),
                        summary = chatImagesSummary,
                        actionLabel = stringResource(R.string.settings_chat_images_clear),
                        enabled = state.chatImageCount > 0 && !state.chatImagesClearing,
                        loading = state.chatImagesClearing,
                        onAction = { showClearChatImagesConfirm = true },
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_group_history_archive)) {
                    SettingsValueRow(
                        icon = NekoIcons.Timeline,
                        title = stringResource(R.string.settings_history_archive_title),
                        value = stringResource(R.string.settings_history_archive_value),
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_storage_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showClearImageCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearImageCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearImageCacheConfirm = false
                        viewModel.clearCache()
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_cache))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageCacheConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearChatImagesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatImagesConfirm = false },
            title = { Text(stringResource(R.string.settings_chat_images_clear_title)) },
            text = { Text(stringResource(R.string.settings_chat_images_clear_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatImagesConfirm = false
                        viewModel.clearChatImages()
                    },
                ) {
                    Text(stringResource(R.string.settings_chat_images_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatImagesConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
