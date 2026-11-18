package io.github.xiaowu20245.nekopaint.feature.chat

/**
 * 会话操作 lease 门禁（跨 ChatViewModel 共享）。
 *
 * SEND/ACTION 按 sessionId 互斥，不同会话可以并行发送/执行；
 * SWITCH/DELETE 需要一个覆盖“当前会话 + 目标会话”的 mutation lease，
 * 不同会话的 mutation 也可以并行。这样既保证同一会话的原子性，
 * 也不会让一个会话的后台操作长期阻塞无关会话。
 *
 * lease 带 token，旧协程 finally 不能释放后来者的 lease。
 */
class ChatSessionOperationGate {

    enum class Kind {
        SEND,
        ACTION,
        SWITCH,
        DELETE,
    }

    data class Lease(
        val token: Long,
        val kind: Kind,
        val sessionId: String?,
    )

    private data class Mutation(
        val lease: Lease,
        val sessions: Set<String>,
    )

    private val lock = Any()
    private var nextToken = 1L
    private val mutations = mutableListOf<Mutation>()
    private val active = mutableMapOf<String, Lease>()

    fun tryAcquire(kind: Kind, sessionId: String?): Lease? =
        tryAcquire(kind, setOfNotNull(sessionId))

    fun tryAcquire(kind: Kind, sessionIds: Set<String>): Lease? = synchronized(lock) {
        when (kind) {
            Kind.SEND,
            Kind.ACTION,
            -> {
                val sessionId = sessionIds.singleOrNull() ?: return null
                if (active.containsKey(sessionId)) return null
                if (mutations.any { sessionId in it.sessions }) return null
                Lease(nextToken++, kind, sessionId).also { active[sessionId] = it }
            }

            Kind.SWITCH,
            Kind.DELETE,
            -> {
                if (sessionIds.any { active.containsKey(it) }) return null
                if (mutations.any { it.sessions.any { session -> session in sessionIds } }) {
                    return null
                }
                Lease(nextToken++, kind, sessionIds.firstOrNull()).also { lease ->
                    mutations += Mutation(lease, sessionIds)
                }
            }
        }
    }

    fun isCurrent(lease: Lease): Boolean = synchronized(lock) {
        if (mutations.any { it.lease.token == lease.token }) return true
        active.values.any { it.token == lease.token }
    }

    fun release(lease: Lease) {
        synchronized(lock) {
            mutations.removeAll { it.lease.token == lease.token }
            val entry = active.entries.firstOrNull { it.value.token == lease.token }
            if (entry != null) {
                active.remove(entry.key)
            }
        }
    }

    fun activeKind(): Kind? = synchronized(lock) {
        mutations.firstOrNull()?.lease?.kind
            ?: active.values.firstOrNull()?.kind
    }
}
