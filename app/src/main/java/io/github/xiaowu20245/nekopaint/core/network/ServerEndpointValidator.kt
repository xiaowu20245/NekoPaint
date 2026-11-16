package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A ComfyUI server endpoint that passed [ServerEndpointValidator].
 *
 * [host] is the canonical host returned by OkHttp (IPv6 without brackets);
 * [displayHost] keeps brackets for IPv6 so the normalized value can be stored
 * and shown in the UI.
 */
data class ServerEndpoint(
    val scheme: String,
    val host: String,
    val displayHost: String,
    val port: Int,
    val baseUrl: String,
) {
    /**
     * Builds the HTTP(S) URL used by OkHttp for the WebSocket upgrade.
     *
     * OkHttp's [HttpUrl] only accepts `http`/`https`; passing `ws`/`wss`
     * directly to [okhttp3.Request.Builder.url] throws. The WebSocket
     * handshake is performed by [okhttp3.OkHttpClient.newWebSocket] on top of
     * this URL.
     */
    fun webSocketUrl(clientId: String): HttpUrl {
        return baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.encodedPath("/ws")
            ?.query(null)
            ?.fragment(null)
            ?.addQueryParameter("clientId", clientId)
            ?.build()
            ?: throw InvalidServerConfigException("服务器地址无法生成 WebSocket URL")
    }
}

class InvalidServerConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Validates and normalizes ComfyUI server addresses.
 *
 * The validator is intentionally strict: the server form stores scheme, host
 * and port separately, so any path/query/fragment/userinfo would be silently
 * reinterpreted by Retrofit or OkHttp later. Bare IPv6 literals are accepted
 * for compatibility and normalized to bracketed form.
 */
object ServerEndpointValidator {

    private val ipv4Pattern = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun parse(config: ServerConfig): Result<ServerEndpoint> =
        parse(config.scheme, config.host, config.port)

    fun parse(scheme: String, rawHost: String, port: Int): Result<ServerEndpoint> {
        val normalizedScheme = scheme.trim().lowercase()
        if (normalizedScheme != "http" && normalizedScheme != "https") {
            return fail("服务器地址只支持 http 或 https")
        }
        if (port !in 1..65535) {
            return fail("端口必须在 1–65535 之间")
        }

        val raw = rawHost.trim()
        if (raw.isEmpty()) return fail("主机不能为空")
        if (raw.any { it.isWhitespace() }) return fail("主机不能包含空白")
        if (raw.contains('/') || raw.contains('\\')) return fail("主机不能包含路径分隔符")
        if (raw.contains('#') || raw.contains('?') || raw.contains('@')) {
            return fail("主机不能包含 #、? 或用户名")
        }
        if (raw.contains('%')) return fail("主机不能包含 zone id")

        val host = when {
            raw.startsWith("[") || raw.endsWith("]") -> {
                if (!raw.startsWith("[") || !raw.endsWith("]") || raw.length < 3) {
                    return fail("IPv6 方括号不完整")
                }
                val inner = raw.substring(1, raw.length - 1)
                if (inner.contains('[') || inner.contains(']')) {
                    return fail("IPv6 方括号格式无效")
                }
                if (!inner.contains(':')) {
                    return fail("方括号只用于 IPv6 地址")
                }
                inner
            }

            raw.contains('[') || raw.contains(']') -> return fail("IPv6 方括号格式无效")
            else -> raw
        }
        if (host.isEmpty()) return fail("主机不能为空")

        val bracketedHost = if (host.contains(':')) {
            if (host.count { it == ':' } < 2) return fail("主机包含非法冒号")
            "[$host]"
        } else {
            host
        }

        if (bracketedHost.none { it == ':' } && !isValidIpv4OrHost(bracketedHost)) {
            return fail("IPv4 地址无效")
        }

        val url = "$normalizedScheme://$bracketedHost:$port/"
            .toHttpUrlOrNull()
            ?: return fail("服务器地址格式无效")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return fail("服务器地址不能包含用户名或密码")
        }
        if (url.host.isBlank()) return fail("服务器地址缺少主机名")
        if (url.port != port) return fail("服务器端口无效")
        if (url.encodedPath != "/") return fail("服务器地址只支持根路径")
        if (url.query != null || url.fragment != null) {
            return fail("服务器地址不能包含 query 或 fragment")
        }

        val canonicalHost = url.host.removePrefix("[").removeSuffix("]")
        val displayHost = if (canonicalHost.contains(':')) {
            "[$canonicalHost]"
        } else {
            canonicalHost
        }
        return Result.success(
            ServerEndpoint(
                scheme = url.scheme,
                host = canonicalHost,
                displayHost = displayHost,
                port = url.port,
                baseUrl = "${url.scheme}://$displayHost:${url.port}/",
            ),
        )
    }

    fun parseBaseUrl(raw: String): Result<ServerEndpoint> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return fail("服务器地址不能为空")
        if (trimmed.any { it.isWhitespace() } || trimmed.contains('\\')) {
            return fail("服务器地址包含不允许的空白或反斜杠")
        }
        if (trimmed.contains('?') || trimmed.contains('#')) {
            return fail("服务器地址不能包含 query 或 fragment")
        }
        val url = trimmed.toHttpUrlOrNull()
            ?: return fail("服务器地址格式无效")
        if (url.scheme != "http" && url.scheme != "https") {
            return fail("服务器地址只支持 http 或 https")
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return fail("服务器地址不能包含用户名或密码")
        }
        if (url.host.isBlank()) return fail("服务器地址缺少主机名")
        if (url.port !in 1..65535) return fail("服务器端口无效")
        if (url.encodedPath != "/") return fail("服务器地址只支持根路径")
        return parse(url.scheme, url.host, url.port)
    }

    fun require(config: ServerConfig): ServerEndpoint =
        parse(config).getOrElse { throw asInvalid(it) }

    fun requireBaseUrl(raw: String): ServerEndpoint =
        parseBaseUrl(raw).getOrElse { throw asInvalid(it) }

    private fun fail(message: String): Result<ServerEndpoint> =
        Result.failure(InvalidServerConfigException(message))

    private fun asInvalid(cause: Throwable): InvalidServerConfigException =
        cause as? InvalidServerConfigException
            ?: InvalidServerConfigException(cause.message ?: "服务器地址无效", cause)

    private fun isValidIpv4OrHost(host: String): Boolean {
        if (!ipv4Pattern.matches(host)) return true
        return host.split('.').all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
