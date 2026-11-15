package io.github.xiaowu20245.nekopaint.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

enum class AiAddressClass {
    LOOPBACK,
    PRIVATE,
    LOCAL_DOMAIN,
    PUBLIC,
}

data class AiEndpoint(
    val baseUrl: String,
    val origin: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val addressClass: AiAddressClass,
) {
    /**
     * Returns the OpenAI-compatible chat completions URL.
     *
     * The base URL is parsed only after [AiEndpointValidator] has accepted it.
     * Keeping the path operation here makes `/v1`, `/v1/` and a complete
     * `/v1/chat/completions` endpoint produce the same result.
     */
    fun chatCompletionsUrl(): HttpUrl {
        val url = baseUrl.toHttpUrlOrNull()
            ?: error("AI endpoint is no longer valid: $baseUrl")
        val path = url.encodedPath
        val targetPath = if (path.endsWith("/chat/completions")) {
            path
        } else {
            path.trimEnd('/') + "/chat/completions"
        }
        return url.newBuilder().encodedPath(targetPath).build()
    }
}

object AiEndpointValidator {

    private val ipv4Pattern = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun parse(raw: String): Result<AiEndpoint> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Base URL 不能为空"))
        }
        if (trimmed.any { it.isWhitespace() } || trimmed.contains('\\')) {
            return Result.failure(IllegalArgumentException("Base URL 包含不允许的空白或反斜杠"))
        }
        if (trimmed.contains('?') || trimmed.contains('#')) {
            return Result.failure(IllegalArgumentException("Base URL 不能包含 query 或 fragment"))
        }

        val url = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Base URL 格式无效"))
        if (url.scheme != "http" && url.scheme != "https") {
            return Result.failure(IllegalArgumentException("Base URL 只支持 http 或 https"))
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Base URL 不能包含用户名或密码"))
        }
        if (url.host.isBlank()) {
            return Result.failure(IllegalArgumentException("Base URL 缺少主机名"))
        }
        if (url.port !in 1..65535) {
            return Result.failure(IllegalArgumentException("Base URL 端口无效"))
        }

        val host = url.host.removePrefix("[").removeSuffix("]")
        if (ipv4Pattern.matches(host) &&
            runCatching { InetAddress.getByName(host) }.getOrNull() == null
        ) {
            return Result.failure(IllegalArgumentException("Base URL 的 IPv4 地址无效"))
        }
        val addressClass = classify(host)
        val displayHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        val originHost = if (host.equals("localhost", ignoreCase = true)) "127.0.0.1" else displayHost
        val origin = "${url.scheme}://$originHost:${url.port}"
        val baseUrl = url.toString().trimEnd('/')

        return Result.success(
            AiEndpoint(
                baseUrl = baseUrl,
                origin = origin,
                scheme = url.scheme,
                host = host,
                port = url.port,
                addressClass = addressClass,
            ),
        )
    }

    private fun classify(rawHost: String): AiAddressClass {
        val host = rawHost.lowercase().removeSuffix(".")
        if (host == "localhost" || host == "::1") return AiAddressClass.LOOPBACK
        if (host.startsWith("127.")) return AiAddressClass.LOOPBACK

        if (ipv4Pattern.matches(host) || host.contains(':')) {
            val address = runCatching {
                InetAddress.getByName(host.removePrefix("[").removeSuffix("]"))
            }.getOrNull()
            if (address != null) {
                if (address.isLoopbackAddress) return AiAddressClass.LOOPBACK
                if (address.isSiteLocalAddress || address.isLinkLocalAddress) {
                    return AiAddressClass.PRIVATE
                }
                if (host.startsWith("fc") || host.startsWith("fd")) {
                    return AiAddressClass.PRIVATE
                }
                return AiAddressClass.PUBLIC
            }
        }

        return if (host.endsWith(".local") || !host.contains('.')) {
            AiAddressClass.LOCAL_DOMAIN
        } else {
            AiAddressClass.PUBLIC
        }
    }
}
