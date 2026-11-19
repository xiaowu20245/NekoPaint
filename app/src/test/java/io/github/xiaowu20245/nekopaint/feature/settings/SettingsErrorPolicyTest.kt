package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsErrorPolicyTest {

    @Test
    fun cacheClearFailureUsesDedicatedKey() {
        assertEquals(
            NekoMessageKey.CACHE_CLEAR_FAILED,
            SettingsErrorPolicy.cacheClearFailed(IllegalStateException("technical")).key,
        )
        assertEquals(
            NekoMessageKey.CACHE_CLEAR_FAILED,
            SettingsErrorPolicy.cacheClearFailed(null).key,
        )
    }
}
