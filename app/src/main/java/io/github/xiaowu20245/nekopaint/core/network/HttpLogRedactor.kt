package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.common.AppLog
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Debug 网络日志脱敏。
 *
 * 只记录 method、path、status、耗时；不记录 host/port、query、header、body，
 * 因此提示词、API Key、真实服务器地址都不会进入 logcat。
 */
object HttpLogRedactor {

    fun describe(
        method: String,
        url: HttpUrl,
        status: Int?,
        durationMs: Long,
    ): String {
        val statusText = status?.toString() ?: "failed"
        return "$method ${url.encodedPath} -> $statusText (${durationMs}ms)"
    }
}

/** 替换 HttpLoggingInterceptor 的 Debug 日志器。 */
class RedactingHttpLogger(
    private val sink: (String) -> Unit = AppLog::d,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val startedAt = System.nanoTime()
        val request = chain.request()
        return try {
            chain.proceed(request).also { response ->
                sink(
                    HttpLogRedactor.describe(
                        method = request.method,
                        url = request.url,
                        status = response.code,
                        durationMs = elapsedMs(startedAt),
                    ),
                )
            }
        } catch (error: IOException) {
            sink(
                HttpLogRedactor.describe(
                    method = request.method,
                    url = request.url,
                    status = null,
                    durationMs = elapsedMs(startedAt),
                ),
            )
            throw error
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
