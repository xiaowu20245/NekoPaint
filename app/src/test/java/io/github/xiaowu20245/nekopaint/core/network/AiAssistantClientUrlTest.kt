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

class AiAssistantClientUrlTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val successBody = """
        {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
    """.trimIndent()

    @Test
    fun invalidBaseUrlReturnsFailureWithoutThrowing() = runTest {
        val client = AiAssistantClient(
            client = OkHttpClient(),
            json = json,
        )
        val result = client.chat(
            config = AiAssistantConfig(baseUrl = "foo", textModel = "model"),
            messages = listOf(AiChatMessage("user", "hi")),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Base URL"))
    }

    @Test
    fun chatUsesChatCompletionsPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(successBody),
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
                apiKey = "sk-test",
                apiKeyOrigin = endpoint.origin,
                textModel = "model",
            )

            val result = client.chat(
                config = config,
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertTrue(result.isSuccess)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        }
    }

    @Test
    fun completeEndpointIsNotRepeated() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(successBody),
            )
            val baseUrl = server.url("/v1/chat/completions").toString()
            val endpoint = AiEndpointValidator.parse(baseUrl).getOrThrow()
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                consentProvider = { AiHttpConsent(keyApprovedOrigins = setOf(endpoint.origin)) },
            )
            val config = AiAssistantConfig(
                baseUrl = baseUrl,
                apiKey = "sk-test",
                apiKeyOrigin = endpoint.origin,
                textModel = "model",
            )

            val result = client.chat(
                config = config,
                messages = listOf(AiChatMessage("user", "hi")),
            )

            assertTrue(result.isSuccess)
            assertEquals("/v1/chat/completions", server.takeRequest().path)
        }
    }

    @Test
    fun redirectsAreNotFollowed() = runTest {
        MockWebServer().use { target ->
            MockWebServer().use { source ->
                target.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(successBody),
                )
                source.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", target.url("/v1/chat/completions").toString()),
                )
                val baseUrl = source.url("/v1").toString()
                val endpoint = AiEndpointValidator.parse(baseUrl).getOrThrow()
                val client = AiAssistantClient(
                    client = OkHttpClient(),
                    json = json,
                    consentProvider = { AiHttpConsent(keyApprovedOrigins = setOf(endpoint.origin)) },
                )
                val config = AiAssistantConfig(
                    baseUrl = baseUrl,
                    apiKey = "sk-test",
                    apiKeyOrigin = endpoint.origin,
                    textModel = "model",
                )

                val result = client.chat(
                    config = config,
                    messages = listOf(AiChatMessage("user", "hi")),
                )

                assertFalse(result.isSuccess)
                assertEquals(0, target.requestCount)
            }
        }
    }
}
