package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.privacy.SensitiveHostProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantClientPolicyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun client(callCounter: IntArray): AiAssistantClient {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCounter[0]++
                chain.proceed(chain.request())
            }
            .build()
        return AiAssistantClient(
            client = http,
            json = json,
            privacySanitizer = AiPrivacySanitizer(SensitiveHostProvider { emptySet() }),
        )
    }

    @Test
    fun defaultDisclosureProviderFailsClosed() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val result = client(intArrayOf(0)).chat(
                config = AiAssistantConfig(
                    baseUrl = server.url("/v1").toString(),
                    textModel = "model",
                ),
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertFalse(result.isSuccess)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun publicHttpIsRejectedBeforeTransport() = runTest {
        val calls = intArrayOf(0)
        val result = client(calls).chat(
            config = AiAssistantConfig(baseUrl = "http://8.8.8.8/v1", textModel = "model"),
            messages = listOf(AiChatMessage("user", "hi")),
        )

        assertFalse(result.isSuccess)
        assertEquals(0, calls[0])
    }

    @Test
    fun untrustedLocalDomainIsRejectedBeforeTransport() = runTest {
        val calls = intArrayOf(0)
        val result = client(calls).chat(
            config = AiAssistantConfig(baseUrl = "http://my-ai.local/v1", textModel = "model"),
            messages = listOf(AiChatMessage("user", "hi")),
        )

        assertFalse(result.isSuccess)
        assertEquals(0, calls[0])
    }

    @Test
    fun privateHttpWithUnapprovedKeyIsRejectedBeforeTransport() = runTest {
        val calls = intArrayOf(0)
        val baseUrl = "http://192.168.1.10:8188/v1"
        val origin = AiEndpointValidator.parse(baseUrl).getOrThrow().origin
        val result = client(calls).chat(
            config = AiAssistantConfig(
                baseUrl = baseUrl,
                apiKey = "sk-test",
                apiKeyOrigin = origin,
                textModel = "model",
            ),
            messages = listOf(AiChatMessage("user", "hi")),
        )

        assertFalse(result.isSuccess)
        assertEquals(0, calls[0])
    }

    @Test
    fun unacknowledgedDisclosureIsRejectedBeforeTransport() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val baseUrl = server.url("/v1").toString()
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                disclosureProvider = { false },
                privacySanitizer = AiPrivacySanitizer(SensitiveHostProvider { emptySet() }),
            )

            val result = client.chat(
                config = AiAssistantConfig(
                    baseUrl = baseUrl,
                    textModel = "model",
                ),
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertFalse(result.isSuccess)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun approvedHttpKeyCanReachTransport() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val baseUrl = server.url("/v1").toString()
            val origin = AiEndpointValidator.parse(baseUrl).getOrThrow().origin
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                consentProvider = { AiHttpConsent(keyApprovedOrigins = setOf(origin)) },
                disclosureProvider = { true },
                privacySanitizer = AiPrivacySanitizer(SensitiveHostProvider { emptySet() }),
            )

            val result = client.chat(
                config = AiAssistantConfig(
                    baseUrl = baseUrl,
                    apiKey = "sk-test",
                    apiKeyOrigin = origin,
                    textModel = "model",
                ),
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertTrue(result.isSuccess)
            assertEquals(1, server.requestCount)
        }
    }
}
