package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyApiFactoryTest {

    private val factory = ComfyApiFactory(
        okHttpClient = OkHttpClient(),
        json = Json { ignoreUnknownKeys = true },
    )

    @Test
    fun invalidConfigThrowsControlledException() {
        val error = runCatching {
            factory.create(ServerConfig(host = "foo#bar"))
        }.exceptionOrNull()

        assertTrue(error is InvalidServerConfigException)
    }

    @Test
    fun invalidManagerConfigThrowsControlledException() {
        val error = runCatching {
            factory.createManager(ServerConfig(host = "[::1", port = 8188))
        }.exceptionOrNull()

        assertTrue(error is InvalidServerConfigException)
    }

    @Test
    fun validConfigStillBuildsApi() {
        val api = factory.create(ServerConfig(host = "127.0.0.1", port = 8188))
        val manager = factory.createManager(ServerConfig(host = "127.0.0.1", port = 8188))

        assertNotNull(api)
        assertNotNull(manager)
    }

    @Test
    fun invalidBaseUrlStringFailsClosed() {
        val error = runCatching {
            factory.create("ftp://127.0.0.1:8188/")
        }.exceptionOrNull()

        assertTrue(error is InvalidServerConfigException)
    }

    @Test
    fun sameEndpointReusesRetrofitAndLruCapsAtEight() {
        val first = ServerConfig(host = "127.0.0.1", port = 8188)
        factory.create(first)
        factory.create(first)
        assertEquals(1, factory.cachedApiCount())

        (1..9).forEach { port ->
            factory.create(ServerConfig(host = "127.0.0.1", port = 8000 + port))
        }

        assertEquals(8, factory.cachedApiCount())
    }

    @Test
    fun coreAndManagerCachesAreIndependent() {
        val config = ServerConfig(host = "127.0.0.1", port = 8188)
        factory.create(config)
        factory.createManager(config)

        assertEquals(1, factory.cachedApiCount())
        assertEquals(1, factory.cachedManagerCount())

        factory.invalidate(ServerEndpointValidator.require(config).endpointKey)
        assertEquals(0, factory.cachedApiCount())
        assertEquals(0, factory.cachedManagerCount())
    }
}
