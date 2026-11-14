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

    /**
     * 同一物理 ComfyUI 的轻量标识。
     *
     * 多个配置可以起不同名字但指向同一地址；历史操作以此判断是否可复用当前主机连接。
     */
    val endpointKey: String
        get() = "${scheme.trim().lowercase()}:${normalizedHost()}:$port"

    private fun normalizedHost(): String {
        val value = host.trim().lowercase().removeSuffix(".")
        return when (value) {
            "localhost", "::1", "[::1]" -> "127.0.0.1"
            else -> value
        }
    }

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
