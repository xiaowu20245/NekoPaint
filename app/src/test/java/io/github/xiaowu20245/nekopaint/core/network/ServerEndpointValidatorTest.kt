package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEndpointValidatorTest {

    @Test
    fun acceptsIpv4AndNormalizesBaseUrl() {
        val endpoint = valid("http", "192.168.1.10", 8188)

        assertEquals("http", endpoint.scheme)
        assertEquals("192.168.1.10", endpoint.displayHost)
        assertEquals(8188, endpoint.port)
        assertEquals("http://192.168.1.10:8188/", endpoint.baseUrl)
    }

    @Test
    fun acceptsDomainLocalHostAndLocalhost() {
        assertEquals(
            "https://comfy.local:443/",
            valid("https", "comfy.local", 443).baseUrl,
        )
        assertEquals(
            "http://localhost:8188/",
            valid("http", "localhost", 8188).baseUrl,
        )
    }

    @Test
    fun normalizesBareIpv6ToBrackets() {
        val endpoint = valid("http", "::1", 8188)

        assertEquals("::1", endpoint.host)
        assertEquals("[::1]", endpoint.displayHost)
        assertEquals("http://[::1]:8188/", endpoint.baseUrl)
    }

    @Test
    fun keepsAlreadyBracketedIpv6() {
        val endpoint = valid("http", "[fe80::1]", 8188)

        assertEquals("[fe80::1]", endpoint.displayHost)
        assertEquals("http://[fe80::1]:8188/", endpoint.baseUrl)
    }

    @Test
    fun rejectsInvalidSchemePortAndHostCharacters() {
        assertInvalid("ftp", "127.0.0.1", 8188)
        assertInvalid("http", "127.0.0.1", 0)
        assertInvalid("http", "127.0.0.1", 65536)
        assertInvalid("http", "", 8188)
        assertInvalid("http", "foo bar", 8188)
        assertInvalid("http", "foo/bar", 8188)
        assertInvalid("http", "foo\\bar", 8188)
        assertInvalid("http", "foo#bar", 8188)
        assertInvalid("http", "foo?bar", 8188)
        assertInvalid("http", "user@host", 8188)
    }

    @Test
    fun rejectsBrokenIpv6BracketsAndSingleColonHost() {
        assertInvalid("http", "[", 8188)
        assertInvalid("http", "[::1", 8188)
        assertInvalid("http", "::1]", 8188)
        assertInvalid("http", "[foo]", 8188)
        assertInvalid("http", "foo:bar", 8188)
        assertInvalid("http", "[::1]]", 8188)
    }

    @Test
    fun rejectsInvalidIpv4Octets() {
        assertInvalid("http", "999.1.1.1", 8188)
        assertInvalid("http", "192.168.1.999", 8188)
    }

    @Test
    fun parseBaseUrlRejectsPathQueryAndFragment() {
        assertTrue(
            ServerEndpointValidator.parseBaseUrl("http://127.0.0.1:8188/").isSuccess,
        )
        assertFalse(
            ServerEndpointValidator.parseBaseUrl("http://127.0.0.1:8188/v1/").isSuccess,
        )
        assertFalse(
            ServerEndpointValidator.parseBaseUrl("http://127.0.0.1:8188/?x=1").isSuccess,
        )
        assertFalse(
            ServerEndpointValidator.parseBaseUrl("http://127.0.0.1:8188/#x").isSuccess,
        )
        assertFalse(
            ServerEndpointValidator.parseBaseUrl("ftp://127.0.0.1:8188/").isSuccess,
        )
    }

    @Test
    fun requireRejectsInvalidConfigWithControlledException() {
        val error = runCatching {
            ServerEndpointValidator.require(ServerConfig(host = "foo#bar"))
        }.exceptionOrNull()

        assertTrue(error is InvalidServerConfigException)
    }

    @Test
    fun buildWebSocketUrlUsesHttpUpgradeAndEncodesClientId() {
        val endpoint = valid("http", "192.168.1.10", 8188)
        val url = endpoint.webSocketUrl("client id&1").toString()

        assertTrue(url.startsWith("http://192.168.1.10:8188/ws?"))
        assertTrue(url.contains("clientId=client%20id%261"))
    }

    @Test
    fun buildWebSocketUrlUsesHttpsForSecureEndpoint() {
        val endpoint = valid("https", "comfy.local", 443)
        val url = endpoint.webSocketUrl("abc").toString()

        assertTrue(url.startsWith("https://comfy.local"))
        assertTrue(url.contains("/ws?clientId=abc"))
    }

    private fun valid(scheme: String, host: String, port: Int): ServerEndpoint =
        ServerEndpointValidator.parse(scheme, host, port).getOrThrow()

    private fun assertInvalid(scheme: String, host: String, port: Int) {
        assertTrue(
            "expected invalid: $scheme://$host:$port",
            ServerEndpointValidator.parse(scheme, host, port).isFailure,
        )
    }
}
