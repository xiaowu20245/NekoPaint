package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantClientCredentialTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun missingKeyOriginIsRejectedBeforeNetwork() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val baseUrl = server.url("/v1").toString()
            val endpoint = AiEndpointValidator.parse(baseUrl).getOrThrow()
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                consentProvider = { AiHttpConsent(keyApprovedOrigins = setOf(endpoint.origin)) },
            )
            val config = AiAssistantConfig(
                baseUrl = baseUrl,
                apiKey = "sk-old",
                apiKeyOrigin = null,
                textModel = "model",
            )

            val result = client.chat(
                config = config,
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertFalse(result.isSuccess)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun mismatchedKeyOriginIsRejectedBeforeNetwork() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val baseUrl = server.url("/v1").toString()
            val endpoint = AiEndpointValidator.parse(baseUrl).getOrThrow()
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                consentProvider = { AiHttpConsent(keyApprovedOrigins = setOf(endpoint.origin)) },
            )
            val config = AiAssistantConfig(
                baseUrl = baseUrl,
                apiKey = "sk-old",
                apiKeyOrigin = "https://old.example.com:443",
                textModel = "model",
            )

            val result = client.chat(
                config = config,
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertFalse(result.isSuccess)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun matchingOriginAndApprovedHttpKeySendsAuthorization() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val baseUrl = server.url("/v1").toString()
            val endpoint = AiEndpointValidator.parse(baseUrl).getOrThrow()
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                consentProvider = { AiHttpConsent(keyApprovedOrigins = setOf(endpoint.origin)) },
            )
            val config = AiAssistantConfig(
                baseUrl = baseUrl,
                apiKey = "sk-new",
                apiKeyOrigin = endpoint.origin,
                textModel = "model",
            )

            val result = client.chat(
                config = config,
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertTrue(result.isSuccess)
            assertEquals("Bearer sk-new", server.takeRequest().getHeader("Authorization"))
        }
    }
}
