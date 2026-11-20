package io.github.xiaowu20245.nekopaint.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HttpLogRedactorTest {

    @Test
    fun logOmitsHostPortQueryAndBody() {
        val url = "http://192.0.2.10:8188/object_info?token=secret&prompt=cat".toHttpUrl()

        val line = HttpLogRedactor.describe(
            method = "GET",
            url = url,
            status = 200,
            durationMs = 42,
        )

        assertEquals("GET /object_info -> 200 (42ms)", line)
        assertFalse(line.contains("192.0.2.10"))
        assertFalse(line.contains("8188"))
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("cat"))
    }

    @Test
    fun failedRequestStillOnlyLogsMethodPathAndDuration() {
        val url = "https://api.example.com/v1/chat/completions".toHttpUrl()

        val line = HttpLogRedactor.describe(
            method = "POST",
            url = url,
            status = null,
            durationMs = 7,
        )

        assertEquals("POST /v1/chat/completions -> failed (7ms)", line)
        assertFalse(line.contains("api.example.com"))
    }
}
