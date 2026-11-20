package io.github.xiaowu20245.nekopaint.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 网络参数集中管理，避免散落的魔法数字。
 */
object NetworkConfig {
    const val ConnectTimeoutSeconds = 5L
    const val ReadTimeoutSeconds = 15L
    const val WriteTimeoutSeconds = 15L
    const val ImageReadTimeoutSeconds = 30L
    const val AiConnectTimeoutSeconds = 20L
    const val AiReadTimeoutSeconds = 60L
    const val WebSocketPingSeconds = 20L
    const val StatusPollSeconds = 5L
    const val QueueFallbackPollSeconds = 2L

    /**
     * AI 请求使用更宽裕的连接超时，但保持 ComfyUI 的 5s 默认值不变。
     * 独立函数便于 JVM 测试直接断言 OkHttp 配置。
     */
    fun aiClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .connectTimeout(AiConnectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(AiReadTimeoutSeconds, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}
