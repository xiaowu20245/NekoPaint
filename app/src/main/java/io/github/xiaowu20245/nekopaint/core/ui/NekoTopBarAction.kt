package io.github.xiaowu20245.nekopaint.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 顶栏的纯文字操作，例如历史页的「选择」。 */
data class NekoTopBarAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** 顶栏「更多」菜单中的一项，破坏性操作统一用 destructive 标记为错误色。 */
data class NekoTopBarMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

/**
 * 顶栏操作项：默认渲染为图标按钮；
 * 提供 label 时渲染为文字按钮；提供 menuItems 时渲染为「更多」溢出菜单。
 */
data class NekoTopBarIconAction(
    val icon: ImageVector? = null,
    val contentDescription: String = "",
    val label: String? = null,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val menuItems: List<NekoTopBarMenuItem> = emptyList(),
    val onClick: () -> Unit = {},
)
