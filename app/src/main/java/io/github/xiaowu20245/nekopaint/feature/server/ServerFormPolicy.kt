package io.github.xiaowu20245.nekopaint.feature.server

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator

data class ServerFormValidation(
    val config: ServerConfig?,
    val hostError: ServerError? = null,
    val portError: ServerError? = null,
)

/** 服务器表单校验的唯一生产/测试入口。 */
object ServerFormPolicy {

    fun validate(form: ServerForm): ServerFormValidation {
        val host = form.host.trim()
        val port = form.port.trim().toIntOrNull()
        val validPort = port != null && port in 1..65535
        val hostCheck = if (host.isNotEmpty()) {
            ServerEndpointValidator.parse(
                scheme = form.scheme,
                rawHost = host,
                port = port?.takeIf { it in 1..65535 } ?: ServerConfig.DEFAULT_PORT,
            )
        } else {
            null
        }
        val hostError = when {
            host.isEmpty() -> ServerError.EmptyHost
            hostCheck?.isFailure == true -> ServerError.InvalidHost
            else -> null
        }
        val portError = if (!validPort) ServerError.InvalidPort else null
        if (hostError != null || portError != null) {
            return ServerFormValidation(null, hostError, portError)
        }
        val endpoint = ServerEndpointValidator.parse(form.scheme, host, port!!)
            .getOrNull()
            ?: return ServerFormValidation(null, ServerError.InvalidHost, portError)
        return ServerFormValidation(
            config = ServerConfig(
                name = form.name.trim().ifEmpty { ServerConfig.DEFAULT_NAME },
                scheme = endpoint.scheme,
                host = endpoint.displayHost,
                port = endpoint.port,
                avatarUri = form.avatarUri,
            ),
            hostError = hostError,
            portError = portError,
        )
    }
}
