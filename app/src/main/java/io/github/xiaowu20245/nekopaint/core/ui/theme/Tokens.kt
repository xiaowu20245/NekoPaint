package io.github.xiaowu20245.nekopaint.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 设计 Token：间距、圆角、高度、图标和动效时长。
 *
 * 数值来源：design/tokens.json。
 */
object NekoSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val bottomFabClearance = 96.dp
    val pagePaddingHorizontal = 16.dp
    val pagePaddingVertical = 12.dp
    val cardGap = 12.dp
    val cardPadding = 16.dp
    val cardPaddingCompact = 12.dp
    val sectionGap = 20.dp
}

object NekoRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}

object NekoElevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
}

object NekoIconSize {
    val xs = 14.dp
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val emptyStateHero = 48.dp
}

object NekoMotion {
    const val FastMs = 150
    const val StandardMs = 220
    const val EmphasizedMs = 280
}
