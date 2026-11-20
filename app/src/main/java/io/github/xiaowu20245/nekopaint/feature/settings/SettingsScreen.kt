package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme

@Composable
fun SettingsScreen(
    onOpenLexicon: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenNekoSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCacheSize(silent = true)
        viewModel.refreshChatImageStats()
    }

    val lexiconSummary = stringResource(
        R.string.settings_lexicon_summary,
        state.builtInWords,
        state.externalWords,
        state.customWords,
    )
    val lexiconBadge = stringResource(
        R.string.settings_lexicon_total_badge,
        state.builtInWords + state.externalWords + state.customWords,
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
    val nekoSummary = stringResource(
        R.string.settings_neko_summary,
        stringResource(
            if (state.aiConfig.persona.isNotBlank() || state.aiConfig.preInstruction.isNotBlank()) {
                R.string.settings_neko_persona_custom
            } else {
                R.string.settings_neko_persona_default
            },
        ),
        stringResource(
            if (state.agentModeByDefault) R.string.chat_mode_agent else R.string.chat_mode_chat,
        ),
    )
    val themeSummary = stringResource(
        when (state.themeMode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )
    val autoSaveSummary = stringResource(
        if (state.autoSaveHistoryImages) {
            R.string.settings_auto_save_on
        } else {
            R.string.settings_auto_save_off
        },
    )
    val storageSummary = stringResource(
        R.string.settings_storage_summary,
        autoSaveSummary,
        formatFileSize(state.cacheBytes),
        state.chatImageCount,
    )
    val aboutSummary = stringResource(
        R.string.settings_about_summary,
        BuildConfig.VERSION_NAME,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            SettingsSection(title = stringResource(R.string.settings_group_ai_neko)) {
                SettingsNavigationRow(
                    icon = NekoIcons.Sparkles,
                    title = stringResource(R.string.settings_ai_service_title),
                    summary = aiSummary,
                    badge = aiBadge,
                    badgeAccent = state.aiConfig.isConfigured,
                    onClick = onOpenAiSettings,
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    icon = NekoIcons.Toolbox,
                    title = stringResource(R.string.settings_neko_title),
                    summary = nekoSummary,
                    onClick = onOpenNekoSettings,
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    icon = NekoIcons.Bookmark,
                    title = stringResource(R.string.settings_lexicon_entry),
                    summary = lexiconSummary,
                    badge = lexiconBadge,
                    onClick = onOpenLexicon,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_group_appearance_interaction)) {
                SettingsNavigationRow(
                    icon = NekoIcons.Tune,
                    title = stringResource(R.string.settings_theme_title),
                    summary = themeSummary,
                    onClick = onOpenAppearance,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_group_images_storage)) {
                SettingsNavigationRow(
                    icon = NekoIcons.Download,
                    title = stringResource(R.string.settings_storage_title),
                    summary = storageSummary,
                    onClick = onOpenStorage,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_group_about_diagnostics)) {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about_destination),
                    summary = aboutSummary,
                    onClick = onOpenAbout,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun SettingsRootPreview() {
    NekoPaintTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            SettingsSection(title = "AI 与 Neko") {
                SettingsNavigationRow(
                    icon = NekoIcons.Sparkles,
                    title = "AI 服务",
                    summary = "DeepSeek · deepseek-chat",
                    badge = "已配置",
                    badgeAccent = true,
                    onClick = {},
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    icon = NekoIcons.Toolbox,
                    title = "Neko I 助手",
                    summary = "自定义人设 · 默认 Chat",
                    onClick = {},
                )
                SettingsRowDivider()
                SettingsNavigationRow(
                    icon = NekoIcons.Bookmark,
                    title = "提示词词库",
                    summary = "内置 120 · 外置 8 · 自定义 2",
                    badge = "130",
                    onClick = {},
                )
            }
            SettingsSection(title = "外观与交互") {
                SettingsNavigationRow(
                    icon = NekoIcons.Tune,
                    title = "主题",
                    summary = "跟随系统",
                    onClick = {},
                )
            }
            SettingsSection(title = "图片与存储") {
                SettingsNavigationRow(
                    icon = NekoIcons.Download,
                    title = "存储",
                    summary = "自动保存开启 · 12.4 MB · 3 张",
                    onClick = {},
                )
            }
            SettingsSection(title = "关于与诊断") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Info,
                    title = "关于 NekoPaint",
                    summary = "版本 1.0.0",
                    onClick = {},
                )
            }
        }
    }
}
