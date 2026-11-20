package io.github.xiaowu20245.nekopaint.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class AiTimeoutTest {

    @Test
    fun aiClientUsesLongerConnectTimeoutWithoutChangingComfyDefaults() {
        val comfy = OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.ConnectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.ReadTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        val ai = NetworkConfig.aiClient(comfy)

        assertEquals(20_000, ai.connectTimeoutMillis)
        assertEquals(60_000, ai.readTimeoutMillis)
        assertEquals(5_000, comfy.connectTimeoutMillis)
        assertEquals(15_000, comfy.readTimeoutMillis)
    }
}
