package io.github.xiaowu20245.nekopaint.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ServerProfile(
    val id: String,
    val name: String = ServerConfig.DEFAULT_NAME,
    val scheme: String = "http",
    val host: String,
    val port: Int = ServerConfig.DEFAULT_PORT,
    val createdAt: Long = System.currentTimeMillis(),
    val avatarUri: String? = null,
) {

    fun toConfig(): ServerConfig = ServerConfig(
        name = name,
        scheme = scheme,
        host = host,
        port = port,
        avatarUri = avatarUri,
    )

    companion object {
        fun fromConfig(
            config: ServerConfig,
            id: String = UUID.randomUUID().toString(),
        ): ServerProfile = ServerProfile(
            id = id,
            name = config.name,
            scheme = config.scheme,
            host = config.host,
            port = config.port,
            avatarUri = config.avatarUri,
        )
    }
}
