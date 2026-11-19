package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskErrorPolicyTest {

    @Test
    fun queueAndHistoryRefreshFailuresUseStableKeys() {
        assertEquals(
            NekoMessageKey.QUEUE_REFRESH_FAILED,
            TaskErrorPolicy.queueRefreshFailed().key,
        )
        assertEquals(
            NekoMessageKey.HISTORY_REFRESH_FAILED,
            TaskErrorPolicy.historyRefreshFailed().key,
        )
        assertEquals(
            NekoMessageKey.HISTORY_LOAD_MORE_FAILED,
            TaskErrorPolicy.historyLoadMoreFailed().key,
        )
    }

    @Test
    fun serverBlocksDistinguishCacheUnknownAndEndpointChanged() {
        assertEquals(
            NekoMessageKey.SERVER_CACHE_UNKNOWN,
            TaskErrorPolicy.serverBlock(cacheUnknown = true).key,
        )
        assertEquals(
            NekoMessageKey.SERVER_ENDPOINT_CHANGED,
            TaskErrorPolicy.serverBlock(cacheUnknown = false).key,
        )
        assertEquals(
            NekoMessageKey.SERVER_CONFIG_INVALID,
            TaskErrorPolicy.serverConfigInvalid().key,
        )
        assertEquals(
            NekoMessageKey.SERVER_NOT_CONFIGURED,
            TaskErrorPolicy.serverNotConfigured().key,
        )
        assertEquals(
            NekoMessageKey.SERVER_SWITCHED,
            TaskErrorPolicy.serverSwitched().key,
        )
    }

    @Test
    fun historyDeleteAndClearFallbacksUseOperationKeys() {
        assertEquals(
            NekoMessageKey.HISTORY_DELETE_FAILED,
            TaskErrorPolicy.historyDeleteFailed(IllegalStateException("technical")).key,
        )
        assertEquals(
            NekoMessageKey.HISTORY_CLEAR_FAILED,
            TaskErrorPolicy.historyClearFailed(IllegalStateException("technical")).key,
        )
        assertEquals(
            NekoMessageKey.HISTORY_DELETE_FAILED,
            TaskErrorPolicy.historyDeleteFailed(null).key,
        )
    }

    @Test
    fun queueActionsUseSpecificKeys() {
        assertEquals(
            NekoMessageKey.QUEUE_INTERRUPT_FAILED,
            TaskErrorPolicy.queueInterruptFailed().key,
        )
        assertEquals(
            NekoMessageKey.QUEUE_DELETE_FAILED,
            TaskErrorPolicy.queueDeleteFailed().key,
        )
    }
}
