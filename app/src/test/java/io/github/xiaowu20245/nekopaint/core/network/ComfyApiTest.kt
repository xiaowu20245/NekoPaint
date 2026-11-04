package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ComfyApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun systemStats_usesRetrofitAndSerialization() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "system": { "comfyui_version": "0.27.0" },
                          "devices": [
                            { "name": "Test GPU", "vram_total": 1024, "vram_free": 512 }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )

            val factory = ComfyApiFactory(OkHttpClient(), json)
            val api = factory.create(server.url("/").toString())

            val stats = api.systemStats()

            assertEquals("0.27.0", stats.system?.comfyUiVersion)
            assertEquals(1024L, stats.devices.first().vramTotal)
            assertEquals("/system_stats", server.takeRequest().path)
        }
    }
}
