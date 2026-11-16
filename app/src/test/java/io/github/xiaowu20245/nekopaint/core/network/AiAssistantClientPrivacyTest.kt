package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.privacy.SensitiveHostProvider
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantClientPrivacyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun requestBodySanitizesSystemAndToolMessagesButKeepsParameters() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val sanitizer = AiPrivacySanitizer(
                SensitiveHostProvider { setOf("comfy.local") },
            )
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                disclosureProvider = { true },
                privacySanitizer = sanitizer,
            )

            val result = client.chat(
                config = AiAssistantConfig(
                    baseUrl = server.url("/v1").toString(),
                    textModel = "model",
                ),
                messages = listOf(
                    AiChatMessage(
                        role = "system",
                        content = "路径 C:\\Users\\alice\\a.png seed=123 steps=20",
                    ),
                    AiChatMessage(
                        role = "tool",
                        content = "主机 comfy.local，状态 failed，cfg=7",
                        toolCallId = "call_1",
                    ),
                    AiChatMessage(
                        role = "assistant",
                        content = "旧回答路径 C:\\Users\\alice\\old.png",
                    ),
                    AiChatMessage(
                        role = "assistant",
                        content = "调用工具",
                        toolCalls = listOf(
                            AiToolCall(
                                id = "call_2",
                                name = "inspect_file",
                                arguments = """{"path":"C:\\Users\\alice\\secret.png","host":"comfy.local","cfg":7,"url":"https://example.com/a"}""",
                            ),
                        ),
                    ),
                    AiChatMessage(role = "user", content = "用户原文 C:\\Users\\keep-me"),
                ),
            )

            assertTrue(result.isSuccess)
            val body = server.takeRequest().body.readUtf8()
            assertFalse(body.contains("C:\\Users\\alice"))
            assertFalse(body.contains("comfy.local"))
            assertFalse(body.contains("old.png"))
            assertTrue(body.contains("seed=123"))
            assertTrue(body.contains("steps=20"))
            assertTrue(body.contains("status") || body.contains("状态"))
            assertTrue(body.contains("cfg=7"))
            assertTrue(body.contains("C:\\\\Users\\\\keep-me") || body.contains("C:\\Users\\keep-me"))

            val toolArguments = json.parseToJsonElement(body)
                .jsonObject["messages"]!!
                .jsonArray
                .mapNotNull { it.jsonObject["tool_calls"]?.jsonArray }
                .first()
                .first()
                .jsonObject["function"]!!
                .jsonObject["arguments"]!!
                .jsonPrimitive.content
            assertFalse(toolArguments.contains("alice"))
            assertFalse(toolArguments.contains("secret.png"))
            assertFalse(toolArguments.contains("comfy.local"))
            assertTrue(toolArguments.contains("\"cfg\":7"))
            val nestedArguments = json.parseToJsonElement(toolArguments).jsonObject
            assertEquals("<链接>", nestedArguments["url"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun waitsForHostSnapshotBeforeSendingRequest() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
            )
            val gate = CompletableDeferred<Boolean>()
            val awaitStarted = CompletableDeferred<Unit>()
            val provider = object : SensitiveHostProvider {
                override fun hosts(): Set<String> = setOf("comfy.local")

                override suspend fun awaitReady(): Boolean {
                    awaitStarted.complete(Unit)
                    return gate.await()
                }
            }
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                disclosureProvider = { true },
                privacySanitizer = AiPrivacySanitizer(provider),
            )

            val chat = async {
                client.chat(
                    config = AiAssistantConfig(
                        baseUrl = server.url("/v1").toString(),
                        textModel = "model",
                    ),
                    messages = listOf(
                        AiChatMessage(role = "system", content = "主机 comfy.local"),
                    ),
                )
            }
            awaitStarted.await()

            assertEquals(0, server.requestCount)

            gate.complete(true)
            val result = chat.await()

            assertTrue(result.isSuccess)
            val body = server.takeRequest().body.readUtf8()
            assertFalse(body.contains("comfy.local"))
        }
    }

    @Test
    fun unavailableHostSnapshotFailsWithoutSendingRequest() = runTest {
        MockWebServer().use { server ->
            val provider = object : SensitiveHostProvider {
                override fun hosts(): Set<String> = emptySet()

                override suspend fun awaitReady(): Boolean = false
            }
            val client = AiAssistantClient(
                client = OkHttpClient(),
                json = json,
                disclosureProvider = { true },
                privacySanitizer = AiPrivacySanitizer(provider),
            )

            val result = client.chat(
                config = AiAssistantConfig(
                    baseUrl = server.url("/v1").toString(),
                    textModel = "model",
                ),
                messages = listOf(
                    AiChatMessage(role = "system", content = "主机 comfy.local"),
                ),
            )

            assertTrue(result.isFailure)
            assertEquals(0, server.requestCount)
        }
    }
}
