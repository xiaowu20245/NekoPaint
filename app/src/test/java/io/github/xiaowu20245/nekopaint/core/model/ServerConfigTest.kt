package io.github.xiaowu20245.nekopaint.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConfigTest {

    @Test
    fun baseUrl_endsWithSlash() {
        val config = ServerConfig(host = "192.168.1.10", port = 8188)

        assertEquals("http://192.168.1.10:8188/", config.baseUrl)
    }

    @Test
    fun baseUrl_supportsHttps() {
        val config = ServerConfig(scheme = "https", host = "comfy.local", port = 443)

        assertEquals("https://comfy.local:443/", config.baseUrl)
    }

    @Test
    fun baseUrl_wrapsIpv6Literal() {
        val config = ServerConfig(host = "fe80::1", port = 8188)

        assertEquals("http://[fe80::1]:8188/", config.baseUrl)
    }

    @Test
    fun baseUrl_keepsAlreadyBracketedIpv6() {
        val config = ServerConfig(host = "[fe80::1]", port = 8188)

        assertEquals("http://[fe80::1]:8188/", config.baseUrl)
    }
}
