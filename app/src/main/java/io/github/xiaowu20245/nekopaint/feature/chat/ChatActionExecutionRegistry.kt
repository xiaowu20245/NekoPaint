package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨 ChatViewModel 的后台动作登记表。
 *
 * 页面销毁/重建后，新实例仍可据此判断某 session+proposal 的动作是否仍在执行，
 * 并在动作完成时收到事件刷新原会话。
 */
class ChatActionExecutionRegistry {

    data class Active(
        val sessionId: String,
        val proposalId: String,
        val startedAt: Long,
    )

    data class Completed(
        val sessionId: String,
        val proposalId: String,
    )

    private data class Key(val sessionId: String, val proposalId: String)

    private val lock = Any()
    private val active = mutableMapOf<Key, Long>()
    private val versions = mutableMapOf<String, Long>()
    private val _activeState = MutableStateFlow<List<Active>>(emptyList())
    val activeState: StateFlow<List<Active>> = _activeState.asStateFlow()
    private val _completions = MutableSharedFlow<Completed>(extraBufferCapacity = 16)
    val completions: SharedFlow<Completed> = _completions.asSharedFlow()

    fun begin(
        sessionId: String,
        proposalId: String,
        startedAt: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        val key = Key(sessionId, proposalId)
        if (active.containsKey(key)) return false
        active[key] = startedAt
        bumpVersionLocked(sessionId)
        publishLocked()
        true
    }

    fun finish(sessionId: String, proposalId: String) {
        val removed = synchronized(lock) {
            if (active.remove(Key(sessionId, proposalId)) == null) {
                false
            } else {
                bumpVersionLocked(sessionId)
                publishLocked()
                true
            }
        }
        if (removed) {
            _completions.tryEmit(Completed(sessionId, proposalId))
        }
    }

    /** 会话动作集合的单调版本；切换会话时用于检测 load 期间是否有完成事件。 */
    fun version(sessionId: String): Long = synchronized(lock) {
        versions[sessionId] ?: 0L
    }

    fun isActive(sessionId: String, proposalId: String): Boolean = synchronized(lock) {
        active.containsKey(Key(sessionId, proposalId))
    }

    fun activeProposalIds(sessionId: String): Set<String> = synchronized(lock) {
        active.keys
            .filter { it.sessionId == sessionId }
            .mapTo(mutableSetOf()) { it.proposalId }
    }

    fun clear() {
        synchronized(lock) {
            active.clear()
            versions.clear()
            publishLocked()
        }
    }

    private fun bumpVersionLocked(sessionId: String) {
        versions[sessionId] = (versions[sessionId] ?: 0L) + 1L
    }

    private fun publishLocked() {
        _activeState.value = active.map { (key, startedAt) ->
            Active(key.sessionId, key.proposalId, startedAt)
        }
    }
}
