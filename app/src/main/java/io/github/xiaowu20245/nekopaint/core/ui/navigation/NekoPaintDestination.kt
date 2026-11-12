package io.github.xiaowu20245.nekopaint.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons

enum class NekoPaintDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Server("server", R.string.nav_server, Icons.Outlined.Home),
    Workflows("workflows", R.string.nav_workflows, Icons.AutoMirrored.Outlined.List),
    Queue("queue", R.string.nav_queue, Icons.Outlined.Refresh),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),

    // 保留路由，后续由工作流详情 / 队列历史进入，不放在底部导航。
    Generate("generate", R.string.nav_generate, Icons.Outlined.PlayArrow),
    History("history", R.string.nav_history, Icons.Outlined.Star),
    Chat("chat", R.string.chat_title, NekoIcons.Sparkles),

    // 设置二级页：只从设置主页进入，复用全局返回箭头。
    PromptLexicon("settings/lexicon", R.string.prompt_lexicon_title, NekoIcons.Bookmark),
    AiAssistant("settings/ai", R.string.ai_assistant_title, NekoIcons.Sparkles),
    ;

    companion object {
        val mainDestinations: List<NekoPaintDestination> = listOf(
            Server,
            Workflows,
            Queue,
            Settings,
        )

        fun fromRoute(route: String?): NekoPaintDestination = when {
            route?.startsWith("workflow/") == true -> Workflows
            route?.startsWith("history/") == true -> History
            else -> entries.firstOrNull { it.route == route } ?: Server
        }
    }
}
