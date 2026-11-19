package io.github.xiaowu20245.nekopaint.feature.common

import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoException
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.common.toNekoMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NekoMessageMapperTest {

    @Test
    fun mapsCoreKeysToStableResources() {
        assertEquals(
            R.string.error_queue_refresh,
            NekoMessageMapper.stringRes(NekoMessageKey.QUEUE_REFRESH_FAILED),
        )
        assertEquals(
            R.string.error_history_refresh,
            NekoMessageMapper.stringRes(NekoMessageKey.HISTORY_REFRESH_FAILED),
        )
        assertEquals(
            R.string.error_invalid_config,
            NekoMessageMapper.stringRes(NekoMessageKey.SERVER_CONFIG_INVALID),
        )
        assertNotEquals(
            NekoMessageMapper.stringRes(NekoMessageKey.QUEUE_REFRESH_FAILED),
            NekoMessageMapper.stringRes(NekoMessageKey.HISTORY_REFRESH_FAILED),
        )
    }

    @Test
    fun everyKeyMapsToNonZeroResource() {
        NekoMessageKey.entries.forEach { key ->
            assertNotEquals(0, NekoMessageMapper.stringRes(key))
        }
    }

    @Test
    fun cacheClearHasDedicatedResource() {
        assertEquals(
            R.string.error_cache_clear,
            NekoMessageMapper.stringRes(NekoMessageKey.CACHE_CLEAR_FAILED),
        )
    }

    @Test
    fun nekoExceptionKeepsSpecificMessage() {
        val message = NekoMessage(NekoMessageKey.HISTORY_DELETE_FAILED)
        val throwable = NekoException(message, IllegalStateException("technical"))

        assertEquals(message, throwable.toNekoMessage())
    }

    @Test
    fun genericThrowableFallsBackToRequestedKey() {
        val mapped = IllegalStateException("technical").toNekoMessage(
            fallback = NekoMessageKey.QUEUE_INTERRUPT_FAILED,
        )

        assertEquals(NekoMessageKey.QUEUE_INTERRUPT_FAILED, mapped.key)
    }
}
