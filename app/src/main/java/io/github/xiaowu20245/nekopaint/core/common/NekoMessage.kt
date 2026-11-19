package io.github.xiaowu20245.nekopaint.core.common

/**
 * 用户可见错误的稳定 key。
 *
 * data/network 层只返回 key + 参数，不返回中文 UI 文案；
 * feature 层通过 [io.github.xiaowu20245.nekopaint.feature.common.NekoMessageMapper] 映射到 strings.xml。
 */
enum class NekoMessageKey {
    GENERIC_ERROR,
    SERVER_NOT_CONFIGURED,
    SERVER_CACHE_UNKNOWN,
    SERVER_ENDPOINT_CHANGED,
    SERVER_CONFIG_INVALID,
    QUEUE_REFRESH_FAILED,
    HISTORY_REFRESH_FAILED,
    HISTORY_LOAD_MORE_FAILED,
    HISTORY_DELETE_FAILED,
    HISTORY_CLEAR_FAILED,
    HISTORY_DELETE_CURRENT_HOST,
    QUEUE_INTERRUPT_FAILED,
    QUEUE_DELETE_FAILED,
    CACHE_CLEAR_FAILED,
    SERVER_SWITCHED,
    RECHECK_FOUND,
    RECHECK_NOT_FOUND,
}

data class NekoMessage(
    val key: NekoMessageKey,
    val args: List<String> = emptyList(),
)

/**
 * 只用于跨 repository 边界的“用户可见且可行动”错误。
 * 内部技术异常继续保留原始异常类型与 cause。
 */
class NekoException(
    val nekoMessage: NekoMessage,
    cause: Throwable? = null,
) : Exception(nekoMessage.key.name, cause)

fun Throwable.toNekoMessage(
    fallback: NekoMessageKey = NekoMessageKey.GENERIC_ERROR,
): NekoMessage = (this as? NekoException)?.nekoMessage ?: NekoMessage(fallback)
