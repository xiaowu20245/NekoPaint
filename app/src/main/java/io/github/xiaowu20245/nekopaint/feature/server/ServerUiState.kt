package io.github.xiaowu20245.nekopaint.feature.server

import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile

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

        fun fromProfile(profile: ServerProfile): ServerForm = fromConfig(profile.toConfig())
    }
}

data class ServerCardUi(
    val profile: ServerProfile,
    val isActive: Boolean,
    val snapshot: ConnectionSnapshot? = null,
    val error: ServerError? = null,
    val isTesting: Boolean = false,
    val lastUpdatedAt: Long? = null,
) {
    val isOnline: Boolean
        get() = snapshot != null && error == null
}

enum class ServerFormMode {
    None,
    Add,
    Edit,
}

data class ServerUiState(
    val isLoading: Boolean = true,
    val cards: List<ServerCardUi> = emptyList(),
    val expandedId: String? = null,
    val formMode: ServerFormMode = ServerFormMode.None,
    val editingId: String? = null,
    val form: ServerForm = ServerForm(),
    val formTesting: Boolean = false,
    val formSaving: Boolean = false,
    val formSnapshot: ConnectionSnapshot? = null,
    val formError: ServerError? = null,
) {
    val activeCard: ServerCardUi?
        get() = cards.firstOrNull { it.isActive }

    val isFormOpen: Boolean
        get() = formMode != ServerFormMode.None

    val isFormBusy: Boolean
        get() = formTesting || formSaving
}
