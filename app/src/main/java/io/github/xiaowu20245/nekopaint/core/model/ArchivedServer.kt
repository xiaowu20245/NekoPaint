package io.github.xiaowu20245.nekopaint.core.model

import kotlinx.serialization.Serializable

/**
 * Metadata for a server endpoint that is no longer in the active configuration.
 *
 * Archived metadata never contains credentials. It keeps the old display name
 * and address so historical entries can still be grouped and labeled after the
 * profile was replaced or deleted.
 */
@Serializable
data class ArchivedServer(
    val id: String,
    val name: String = ServerConfig.DEFAULT_NAME,
    val scheme: String = "http",
    val host: String,
    val port: Int = ServerConfig.DEFAULT_PORT,
    val archivedAt: Long = System.currentTimeMillis(),
) {
    val endpointKey: String
        get() = ServerProfile(
            id = id,
            name = name,
            scheme = scheme,
            host = host,
            port = port,
        ).endpointKey

    companion object {
        fun from(profile: ServerProfile): ArchivedServer = ArchivedServer(
            id = profile.id,
            name = profile.name,
            scheme = profile.scheme,
            host = profile.host,
            port = profile.port,
            archivedAt = System.currentTimeMillis(),
        )
    }
}
