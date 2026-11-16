package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig

/**
 * Identity of the active cache namespace plus the config that belongs to it.
 *
 * Reading both from the same activeServer emission avoids sending a request
 * with server A's identity but server B's base URL.
 */
data class ServerIdentity(
    val serverId: String,
    val endpointKey: String,
)

data class ServerIdentitySnapshot(
    val identity: ServerIdentity,
    val config: ServerConfig,
)

object ServerIsolationCoordinator {
    fun requiresReset(previous: ServerIdentity?, next: ServerIdentity?): Boolean =
        previous != next
}
