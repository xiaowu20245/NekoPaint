package io.github.xiaowu20245.nekopaint.core.ui

data class NekoTopBarAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)
