package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.common.toNekoMessage

object SettingsErrorPolicy {

    fun cacheClearFailed(cause: Throwable? = null): NekoMessage =
        cause?.toNekoMessage(NekoMessageKey.CACHE_CLEAR_FAILED)
            ?: NekoMessage(NekoMessageKey.CACHE_CLEAR_FAILED)
}
