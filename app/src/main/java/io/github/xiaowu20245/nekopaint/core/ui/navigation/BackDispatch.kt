package io.github.xiaowu20245.nekopaint.core.ui.navigation

/**
 * 顶栏返回与系统返回的统一分发。
 *
 * 屏幕注册的 handler 返回 true 表示已消费本次返回（例如动作执行中禁止离开）；
 * 只有未消费时才 pop。
 */
internal fun dispatchScreenBack(
    handler: (() -> Boolean)?,
    onUnhandled: () -> Unit,
) {
    if (handler?.invoke() == true) return
    onUnhandled()
}
