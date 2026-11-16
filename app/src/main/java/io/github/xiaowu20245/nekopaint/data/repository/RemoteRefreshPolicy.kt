package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator

/**
 * Decides whether a refresh may touch the remote ComfyUI server.
 *
 * Invalid persisted configurations stay visible and editable, but must not
 * generate HTTP or WebSocket traffic until the user fixes them.
 */
internal object RemoteRefreshPolicy {
    fun shouldRefreshRemotely(config: ServerConfig?): Boolean =
        config != null && ServerEndpointValidator.parse(config).isSuccess
}
