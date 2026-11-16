package io.github.xiaowu20245.nekopaint.feature.server

import io.github.xiaowu20245.nekopaint.core.model.ConnectionSnapshot
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator

data class ServerForm(
    val name: String = ServerConfig.DEFAULT_NAME,
    val host: String = "",
    val port: String = ServerConfig.DEFAULT_PORT.toString(),
    val scheme: String = "http",
    val avatarUri: String? = null,
    val hostError: ServerError? = null,
    val portError: ServerError? = null,
) {
    fun toConfigOrNull(): ServerConfig? {
        val trimmedHost = host.trim()
        val parsedPort = port.trim().toIntOrNull()
        if (trimmedHost.isEmpty() || parsedPort == null || parsedPort !in 1..65535) {
            return null
        }
        val endpoint = ServerEndpointValidator.parse(scheme, trimmedHost, parsedPort)
            .getOrNull()
            ?: return null
        return ServerConfig(
            name = name.trim().ifEmpty { ServerConfig.DEFAULT_NAME },
            scheme = endpoint.scheme,
            host = endpoint.displayHost,
            port = endpoint.port,
            avatarUri = avatarUri,
        )
    }

    companion object {
        fun fromConfig(config: ServerConfig): ServerForm = ServerForm(
            name = config.name,
            host = config.host,
            port = config.port.toString(),
            scheme = config.scheme,
            avatarUri = config.avatarUri,
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

/**
 * 服务状态刷新的展示策略。
 *
 * 后台轮询只有在“从未得到任何结果”时才显示检测中；一旦已经在线或离线，
 * 静默刷新必须保留当前徽章，否则每个轮询周期都会在“检测中/离线”之间闪烁。
 */
internal object ServerRefreshPolicy {
    const val MIN_TESTING_VISIBLE_MS = 1_200L

    fun showTesting(card: ServerCardUi?, silent: Boolean): Boolean =
        !silent || (card != null && card.snapshot == null && card.error == null)

    fun remainingTestingMs(
        startedAt: Long,
        now: Long,
        minimumMs: Long = MIN_TESTING_VISIBLE_MS,
    ): Long = (startedAt + minimumMs - now).coerceAtLeast(0L)
}

/**
 * Merges a persisted profile into the existing card state.
 *
 * When the endpoint changes, errors and snapshots belong to the old endpoint
 * and must not survive; otherwise a corrected invalid configuration could keep
 * showing `InvalidConfig` and keep the 5-second probe disabled.
 */
internal fun mergeServerCard(
    existing: ServerCardUi?,
    profile: ServerProfile,
    isActive: Boolean,
): ServerCardUi {
    if (existing == null) {
        return ServerCardUi(profile = profile, isActive = isActive)
    }
    if (existing.profile.endpointKey != profile.endpointKey) {
        return existing.copy(
            profile = profile,
            isActive = isActive,
            isTesting = false,
            snapshot = null,
            error = null,
            lastUpdatedAt = null,
        )
    }
    return existing.copy(profile = profile, isActive = isActive)
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
    val formTargetId: String? = null,
    val form: ServerForm = ServerForm(),
    val formTesting: Boolean = false,
    val formSaving: Boolean = false,
    val formSnapshot: ConnectionSnapshot? = null,
    val formError: ServerError? = null,
    val pendingEndpointChange: PendingEndpointChange? = null,
) {
    val activeCard: ServerCardUi?
        get() = cards.firstOrNull { it.isActive }

    val isFormOpen: Boolean
        get() = formMode != ServerFormMode.None

    val isFormBusy: Boolean
        get() = formTesting || formSaving
}

data class PendingEndpointChange(
    val oldProfile: ServerProfile,
    val newProfile: ServerProfile,
    val activate: Boolean,
    val formTargetId: String?,
)
