package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨 ChatViewModel 的后台发送登记表。
 *
 * send/runTool/runImageTool 运行在全局 appScope，页面销毁后仍会写回原会话；
 * 新实例据此拒绝删除/切换仍在发送的会话，避免旧回复落进已删除或已切换的会话。
 */
class ChatSendExecutionRegistry {

    data class Completed(val sessionId: String)

    private val lock = Any()
    private val active = mutableMapOf<String, Long>()
    private val _activeSessions = MutableStateFlow<Set<String>>(emptySet())
    val activeSessions: StateFlow<Set<String>> = _activeSessions.asStateFlow()
    private val _completions = MutableSharedFlow<Completed>(extraBufferCapacity = 16)
    val completions: SharedFlow<Completed> = _completions.asSharedFlow()

    fun begin(
        sessionId: String,
        startedAt: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        if (active.containsKey(sessionId)) return false
        active[sessionId] = startedAt
        _activeSessions.value = active.keys.toSet()
        true
    }

    fun finish(sessionId: String) {
        val removed = synchronized(lock) {
            if (active.remove(sessionId) == null) {
                false
            } else {
                _activeSessions.value = active.keys.toSet()
                true
            }
        }
        if (removed) {
            _completions.tryEmit(Completed(sessionId))
        }
    }

    fun isActive(sessionId: String): Boolean = synchronized(lock) {
        active.containsKey(sessionId)
    }

    fun clear() {
        synchronized(lock) {
            active.clear()
            _activeSessions.value = emptySet()
        }
    }
}
