package io.github.xiaowu20245.nekopaint.feature.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey

/**
 * NekoMessageKey → strings.xml 的唯一映射入口。
 * data/network 层不引用 Android 资源；UI/feature 层只从这里取文案。
 */
object NekoMessageMapper {

    @StringRes
    fun stringRes(key: NekoMessageKey): Int = when (key) {
        NekoMessageKey.GENERIC_ERROR -> R.string.error_generic
        NekoMessageKey.SERVER_NOT_CONFIGURED -> R.string.error_server_not_configured
        NekoMessageKey.SERVER_CACHE_UNKNOWN -> R.string.error_cache_identity_unknown
        NekoMessageKey.SERVER_ENDPOINT_CHANGED -> R.string.error_server_endpoint_changed
        NekoMessageKey.SERVER_CONFIG_INVALID -> R.string.error_invalid_config
        NekoMessageKey.QUEUE_REFRESH_FAILED -> R.string.error_queue_refresh
        NekoMessageKey.HISTORY_REFRESH_FAILED -> R.string.error_history_refresh
        NekoMessageKey.HISTORY_LOAD_MORE_FAILED -> R.string.error_history_load_more
        NekoMessageKey.HISTORY_DELETE_FAILED -> R.string.error_history_delete
        NekoMessageKey.HISTORY_CLEAR_FAILED -> R.string.error_history_clear
        NekoMessageKey.HISTORY_DELETE_CURRENT_HOST -> R.string.error_history_delete_current_host
        NekoMessageKey.QUEUE_INTERRUPT_FAILED -> R.string.error_queue_interrupt
        NekoMessageKey.QUEUE_DELETE_FAILED -> R.string.error_queue_delete
        NekoMessageKey.CACHE_CLEAR_FAILED -> R.string.error_cache_clear
        NekoMessageKey.SERVER_SWITCHED -> R.string.error_server_switched
        NekoMessageKey.RECHECK_FOUND -> R.string.queue_recheck_found
        NekoMessageKey.RECHECK_NOT_FOUND -> R.string.queue_recheck_not_found
    }
}

@Composable
fun NekoMessage.asString(): String {
    val id = NekoMessageMapper.stringRes(key)
    return if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }
}
