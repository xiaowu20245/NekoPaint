package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState

/**
 * Queue 离线轮询退避：2s → 5s → 10s → 30s，恢复 Online 后重置。
 * 纯策略便于 JVM 测试，避免在 Compose 里散落魔法数字。
 */
object QueueOfflineBackoffPolicy {

    const val ONLINE_DELAY_MS = 5_000L
    private val OFFLINE_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L, 30_000L)

    fun delayMs(connection: ComfyConnectionState, offlineAttempt: Int): Long =
        if (connection == ComfyConnectionState.Online) {
            ONLINE_DELAY_MS
        } else {
            OFFLINE_DELAYS_MS[offlineAttempt.coerceIn(0, OFFLINE_DELAYS_MS.lastIndex)]
        }

    fun nextAttempt(connection: ComfyConnectionState, offlineAttempt: Int): Int =
        if (connection == ComfyConnectionState.Online) {
            0
        } else {
            (offlineAttempt + 1).coerceAtMost(OFFLINE_DELAYS_MS.lastIndex)
        }
}
