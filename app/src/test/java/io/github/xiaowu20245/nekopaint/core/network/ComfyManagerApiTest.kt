package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.workflow.NodeInstallPlanner
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyManagerApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun version_readsTextResponse() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("3.32.3"),
            )
            val api = managerApi(server)

            val response = api.version()

            assertTrue(response.isSuccessful)
            assertEquals("3.32.3", response.body()?.string())
            assertEquals("/manager/version", server.takeRequest().path)
        }
    }

    @Test
    fun version_returnsNotFoundWhenManagerMissing() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val api = managerApi(server)

            val response = api.version()

            assertEquals(404, response.code())
            assertEquals("/manager/version", server.takeRequest().path)
        }
    }

    @Test
    fun mappings_usesCacheModeAndParsesPackages() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "pack-a": [
                            ["NodeA"],
                            {"title": "Pack A", "files": ["https://github.com/a/a.git"]}
                          ]
                        }
                        """.trimIndent(),
                    ),
            )
            val api = managerApi(server)

            val response = api.mappings()

            assertTrue(response.isSuccessful)
            assertTrue(response.body()?.containsKey("pack-a") == true)
            assertEquals("/customnode/getmappings?mode=cache", server.takeRequest().path)
        }
    }

    @Test
    fun mappings_canUseLocalMode() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}"),
            )
            val api = managerApi(server)

            api.mappings("local")

            assertEquals("/customnode/getmappings?mode=local", server.takeRequest().path)
        }
    }

    @Test
    fun queueStatus_parsesCounters() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"total_count":3,"done_count":2,"in_progress_count":1,"is_processing":true}""",
                    ),
            )
            val api = managerApi(server)

            val status = api.queueStatus().body()

            assertEquals(3, status?.totalCount)
            assertEquals(2, status?.doneCount)
            assertEquals(1, status?.inProgressCount)
            assertEquals(true, status?.isProcessing)
        }
    }

    @Test
    fun install_postsPackageMetadataAndHandlesSecurityErrors() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(403))
            val api = managerApi(server)
            val request = NodeInstallPlanner.buildInstallRequest(
                NodeInstallPackage(
                    id = "pack-a",
                    title = "Pack A",
                    files = listOf("https://github.com/a/a.git"),
                ),
            )

            val first = api.install(request)
            val second = api.install(request)

            assertTrue(first.isSuccessful)
            assertEquals(403, second.code())
            val recorded = server.takeRequest()
            assertEquals("/manager/queue/install", recorded.path)
            val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals("pack-a", body["id"].toString().trim('"'))
            assertEquals("Pack A", body["title"].toString().trim('"'))
        }
    }

    @Test
    fun startAndReboot_useManagerEndpoints() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(200))
            val api = managerApi(server)

            val start = api.startQueue()
            val reboot = api.reboot()

            assertTrue(start.isSuccessful)
            assertTrue(reboot.isSuccessful)
            assertEquals("/manager/queue/start", server.takeRequest().path)
            assertEquals("/manager/reboot", server.takeRequest().path)
        }
    }

    private fun managerApi(server: MockWebServer): ComfyManagerApi =
        ComfyApiFactory(OkHttpClient(), json)
            .createManager(server.url("/").toString())
}
