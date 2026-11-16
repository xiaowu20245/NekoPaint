package io.github.xiaowu20245.nekopaint.feature.server

/**
 * Tracks in-flight server probes and invalidates stale results.
 *
 * A probe is identified by `serverId + endpointKey`, while the generation is
 * per server id. Saving a new endpoint starts a new generation, so results
 * from an older endpoint are discarded even if they arrive later.
 */
internal class ServerProbeTracker {

    private val active = mutableSetOf<String>()
    private val generations = mutableMapOf<String, Long>()

    /**
     * Starts a probe for [serverId] and [endpointKey].
     *
     * Returns null when the same server/endpoint pair is already being probed;
     * callers should not start a duplicate request in that case.
     */
    fun start(serverId: String, endpointKey: String): Long? {
        val key = key(serverId, endpointKey)
        if (!active.add(key)) return null
        return nextGeneration(serverId)
    }

    /**
     * Invalidates any in-flight probe for [serverId] without starting a new
     * endpoint probe. Used when the current configuration is invalid.
     */
    fun invalidate(serverId: String): Long = nextGeneration(serverId)

    fun isCurrent(serverId: String, generation: Long): Boolean =
        generations[serverId] == generation

    fun finish(serverId: String, endpointKey: String) {
        active.remove(key(serverId, endpointKey))
    }

    private fun nextGeneration(serverId: String): Long {
        val next = (generations[serverId] ?: 0L) + 1L
        generations[serverId] = next
        return next
    }

    private fun key(serverId: String, endpointKey: String): String =
        "$serverId|$endpointKey"
}
