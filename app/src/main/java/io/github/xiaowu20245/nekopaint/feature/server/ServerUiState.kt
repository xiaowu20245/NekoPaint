package io.github.xiaowu20245.nekopaint.feature.server

import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig

data class ServerForm(
    val name: String = ServerConfig.DEFAULT_NAME,
    val host: String = "",
    val port: String = ServerConfig.DEFAULT_PORT.toString(),
    val scheme: String = "http",
    val hostError: ServerError? = null,
    val portError: ServerError? = null,
) {
    fun toConfigOrNull(): ServerConfig? {
        val trimmedHost = host.trim()
        val parsedPort = port.trim().toIntOrNull()
        if (trimmedHost.isEmpty() || parsedPort == null || parsedPort !in 1..65535) {
            return null
        }
        return ServerConfig(
            name = name.trim().ifEmpty { ServerConfig.DEFAULT_NAME },
            scheme = scheme,
            host = trimmedHost,
            port = parsedPort,
        )
    }

    companion object {
        fun fromConfig(config: ServerConfig): ServerForm = ServerForm(
            name = config.name,
            host = config.host,
            port = config.port.toString(),
            scheme = config.scheme,
        )
    }
}

data class ServerUiState(
    val isLoading: Boolean = true,
    val config: ServerConfig? = null,
    val form: ServerForm = ServerForm(),
    val isEditing: Boolean = true,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val snapshot: ConnectionSnapshot? = null,
    val error: ServerError? = null,
    val lastUpdatedAt: Long? = null,
) {
    val isBusy: Boolean
        get() = isTesting || isSaving

    val isOnline: Boolean
        get() = snapshot != null && error == null
}
