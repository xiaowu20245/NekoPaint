package io.github.xiaowu20245.nekopaint.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyWebSocketUrlTest {

    @Test
    fun httpEndpointUsesHttpUpgradeUrl() {
        val endpoint = ServerEndpointValidator.parse("http", "192.168.1.10", 8188)
            .getOrThrow()

        val url = endpoint.webSocketUrl("client-1").toString()

        assertTrue(url.startsWith("http://192.168.1.10:8188/ws?"))
        assertTrue(url.contains("clientId=client-1"))
    }

    @Test
    fun httpsEndpointUsesHttpsUpgradeUrl() {
        val endpoint = ServerEndpointValidator.parse("https", "comfy.local", 443)
            .getOrThrow()

        val url = endpoint.webSocketUrl("client-1").toString()

        assertTrue(url.startsWith("https://comfy.local"))
        assertTrue(url.contains("/ws?clientId=client-1"))
    }

    @Test
    fun clientIdIsEncodedAsQueryParameter() {
        val endpoint = ServerEndpointValidator.parse("http", "127.0.0.1", 8188)
            .getOrThrow()

        val url = endpoint.webSocketUrl("a b&c").toString()

        assertTrue(url.contains("clientId=a%20b%26c"))
    }

    @Test
    fun invalidEndpointCannotBuildWebSocketUrl() {
        assertFalse(
            ServerEndpointValidator.parse("http", "foo#bar", 8188).isSuccess,
        )
    }
}
