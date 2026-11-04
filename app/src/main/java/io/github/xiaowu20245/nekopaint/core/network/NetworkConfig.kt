package io.github.xiaowu20245.nekopaint.core.network

/**
 * 网络参数集中管理，避免散落的魔法数字。
 */
object NetworkConfig {
    const val ConnectTimeoutSeconds = 5L
    const val ReadTimeoutSeconds = 15L
    const val WriteTimeoutSeconds = 15L
    const val ImageReadTimeoutSeconds = 30L
    const val WebSocketPingSeconds = 20L
    const val StatusPollSeconds = 5L
    const val QueueFallbackPollSeconds = 2L
}
