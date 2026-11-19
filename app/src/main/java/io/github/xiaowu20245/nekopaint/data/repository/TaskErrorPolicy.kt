package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.common.toNekoMessage

/**
 * TaskManager 用户可见错误的纯映射策略。
 * 生产赋值与测试共用，避免测试只验证 Mapper 而漏掉场景 key。
 */
object TaskErrorPolicy {

    fun serverBlock(cacheUnknown: Boolean): NekoMessage =
        NekoMessage(
            if (cacheUnknown) {
                NekoMessageKey.SERVER_CACHE_UNKNOWN
            } else {
                NekoMessageKey.SERVER_ENDPOINT_CHANGED
            },
        )

    fun serverNotConfigured(): NekoMessage =
        NekoMessage(NekoMessageKey.SERVER_NOT_CONFIGURED)

    fun serverSwitched(): NekoMessage =
        NekoMessage(NekoMessageKey.SERVER_SWITCHED)

    fun serverConfigInvalid(): NekoMessage =
        NekoMessage(NekoMessageKey.SERVER_CONFIG_INVALID)

    fun queueRefreshFailed(): NekoMessage =
        NekoMessage(NekoMessageKey.QUEUE_REFRESH_FAILED)

    fun historyRefreshFailed(): NekoMessage =
        NekoMessage(NekoMessageKey.HISTORY_REFRESH_FAILED)

    fun historyLoadMoreFailed(): NekoMessage =
        NekoMessage(NekoMessageKey.HISTORY_LOAD_MORE_FAILED)

    fun historyDeleteFailed(cause: Throwable? = null): NekoMessage =
        cause.toUserMessage(NekoMessageKey.HISTORY_DELETE_FAILED)

    fun historyClearFailed(cause: Throwable? = null): NekoMessage =
        cause.toUserMessage(NekoMessageKey.HISTORY_CLEAR_FAILED)

    fun queueInterruptFailed(): NekoMessage =
        NekoMessage(NekoMessageKey.QUEUE_INTERRUPT_FAILED)

    fun queueDeleteFailed(): NekoMessage =
        NekoMessage(NekoMessageKey.QUEUE_DELETE_FAILED)

    private fun Throwable?.toUserMessage(fallback: NekoMessageKey): NekoMessage =
        this?.toNekoMessage(fallback) ?: NekoMessage(fallback)
}
