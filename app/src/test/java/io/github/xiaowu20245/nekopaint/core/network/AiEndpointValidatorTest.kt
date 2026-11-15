package io.github.xiaowu20245.nekopaint.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEndpointValidatorTest {

    @Test
    fun acceptsOpenAiCompatibleBaseUrls() {
        val endpoint = AiEndpointValidator.parse("https://api.deepseek.com/v1").getOrThrow()

        assertEquals("https", endpoint.scheme)
        assertEquals("api.deepseek.com", endpoint.host)
        assertEquals(443, endpoint.port)
        assertEquals(AiAddressClass.PUBLIC, endpoint.addressClass)
        assertEquals("https://api.deepseek.com:443", endpoint.origin)
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            endpoint.chatCompletionsUrl().toString(),
        )
    }

    @Test
    fun acceptsLoopbackPrivateAndLocalDomainAddresses() {
        val loopback = AiEndpointValidator.parse("http://127.0.0.1:11434/v1").getOrThrow()
        val private = AiEndpointValidator.parse("http://192.168.1.10:8188/v1").getOrThrow()
        val localDomain = AiEndpointValidator.parse("http://my-ai.local/v1").getOrThrow()

        assertEquals(AiAddressClass.LOOPBACK, loopback.addressClass)
        assertEquals(AiAddressClass.PRIVATE, private.addressClass)
        assertEquals(AiAddressClass.LOCAL_DOMAIN, localDomain.addressClass)
    }

    @Test
    fun acceptsBracketedIpv6Loopback() {
        val endpoint = AiEndpointValidator.parse("http://[::1]:11434/v1").getOrThrow()

        assertEquals("::1", endpoint.host)
        assertEquals(AiAddressClass.LOOPBACK, endpoint.addressClass)
        assertTrue(endpoint.origin.contains("[::1]"))
    }

    @Test
    fun normalizesTrailingSlashAndCompleteEndpoint() {
        val base = AiEndpointValidator.parse("https://api.example.com/v1/").getOrThrow()
        val complete = AiEndpointValidator.parse(
            "https://api.example.com/v1/chat/completions",
        ).getOrThrow()

        assertEquals(
            "https://api.example.com/v1/chat/completions",
            base.chatCompletionsUrl().toString(),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            complete.chatCompletionsUrl().toString(),
        )
    }

    @Test
    fun rejectsMalformedOrUnsafeUrls() {
        listOf(
            "foo",
            "http://",
            "https://exa mple.com",
            "https://example.com/v1?token=1",
            "https://example.com/v1#fragment",
            "https://user:pass@example.com/v1",
            "https://example.com\\v1",
            "http://[::1/v1",
            "http://[::1]:0/v1",
            "http://999.999.999.999/v1",
        ).forEach { raw ->
            assertTrue("expected invalid: $raw", AiEndpointValidator.parse(raw).isFailure)
        }
    }

    @Test
    fun rejectsQueryAndFragmentEvenWhenHttpUrlCouldNormalizeThem() {
        assertFalse(AiEndpointValidator.parse("https://example.com/v1?x=1").isSuccess)
        assertFalse(AiEndpointValidator.parse("https://example.com/v1#x").isSuccess)
    }

    @Test
    fun normalizesDefaultPortsInOrigin() {
        val explicit = AiEndpointValidator.parse("https://example.com:443/v1").getOrThrow()
        val implicit = AiEndpointValidator.parse("https://example.com/v1").getOrThrow()

        assertEquals(implicit.origin, explicit.origin)
    }
}
