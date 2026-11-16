package io.github.xiaowu20245.nekopaint.core.model

/**
 * Decides whether editing a server can keep its cache namespace.
 *
 * The server id is also the namespace for tasks, history and model catalogs.
 * Only a change of the physical endpoint (scheme + host + port) gets a new id;
 * renaming or changing the avatar keeps the existing caches.
 */
enum class ServerEditPlan {
    KEEP_ID,
    NEW_ID,
}

object ServerEditPlanner {

    fun plan(old: ServerProfile, newConfig: ServerConfig): ServerEditPlan {
        val candidate = ServerProfile.fromConfig(newConfig, id = old.id)
        return if (old.endpointKey == candidate.endpointKey) {
            ServerEditPlan.KEEP_ID
        } else {
            ServerEditPlan.NEW_ID
        }
    }
}
