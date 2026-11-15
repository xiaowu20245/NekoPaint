package io.github.xiaowu20245.nekopaint.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.datastore.ThemeMode
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons

@Composable
fun AppearanceSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            SettingsSection(title = stringResource(R.string.settings_theme_title)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                                ThemeMode.DARK -> R.string.settings_theme_dark
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_group_display)) {
                SettingsSwitchRow(
                    icon = NekoIcons.Tune,
                    title = stringResource(R.string.settings_dynamic_color_title),
                    summary = stringResource(R.string.settings_dynamic_color_desc),
                    checked = state.dynamicColor && dynamicColorSupported,
                    enabled = dynamicColorSupported,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    icon = NekoIcons.Stopwatch,
                    title = stringResource(R.string.settings_reduce_motion_title),
                    summary = stringResource(R.string.settings_reduce_motion_desc),
                    checked = state.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    icon = NekoIcons.ImageFrame,
                    title = stringResource(R.string.settings_show_temp_title),
                    summary = stringResource(R.string.settings_show_temp_desc),
                    checked = state.showTempPreviews,
                    onCheckedChange = viewModel::setShowTempPreviews,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.settings_appearance_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
