package io.github.xiaowu20245.nekopaint.core.model

/**
 * 一台 ComfyUI 服务器的连接配置。
 *
 * [baseUrl] 始终以 `/` 结尾，满足 Retrofit 的要求。
 * IPv6 字面量会自动加方括号。
 */
data class ServerConfig(
    val name: String = "我的 ComfyUI",
    val scheme: String = "http",
    val host: String,
    val port: Int = DEFAULT_PORT,
    val avatarUri: String? = null,
) {

    val baseUrl: String
        get() {
            val normalizedHost = when {
                host.startsWith("[") -> host
                host.contains(':') -> "[$host]"
                else -> host
            }
            return "$scheme://$normalizedHost:$port/"
        }

    companion object {
        const val DEFAULT_PORT = 8188
        const val DEFAULT_NAME = "我的 ComfyUI"
    }
}
