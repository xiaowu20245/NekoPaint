package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val MAX_CACHED_RETROFITS = 8

class ComfyApiFactory(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    // Core 与 Manager 分开缓存，避免同一 endpoint 的两个 Retrofit 互相挤占 LRU。
    private val apiCache = LruRetrofitCache(MAX_CACHED_RETROFITS)
    private val managerCache = LruRetrofitCache(MAX_CACHED_RETROFITS)

    fun create(config: ServerConfig): ComfyApi =
        retrofit(ServerEndpointValidator.require(config)).create(ComfyApi::class.java)

    fun create(baseUrl: String): ComfyApi =
        retrofit(ServerEndpointValidator.requireBaseUrl(baseUrl)).create(ComfyApi::class.java)

    fun createManager(config: ServerConfig): ComfyManagerApi =
        managerRetrofit(ServerEndpointValidator.require(config))
            .create(ComfyManagerApi::class.java)

    fun createManager(baseUrl: String): ComfyManagerApi =
        managerRetrofit(ServerEndpointValidator.requireBaseUrl(baseUrl))
            .create(ComfyManagerApi::class.java)

    /** 服务器配置被删除/替换时释放对应 Retrofit，防止旧的 OkHttp 连接继续被复用。 */
    @Synchronized
    fun invalidate(endpointKey: String) {
        apiCache.remove(endpointKey)
        managerCache.remove(endpointKey)
    }

    @Synchronized
    fun clear() {
        apiCache.clear()
        managerCache.clear()
    }

    internal fun cachedApiCount(): Int = apiCache.size()

    internal fun cachedManagerCount(): Int = managerCache.size()

    private fun retrofit(endpoint: ServerEndpoint): Retrofit =
        apiCache.getOrCreate(endpoint.endpointKey) { retrofitBuilder(endpoint) }

    private fun managerRetrofit(endpoint: ServerEndpoint): Retrofit =
        managerCache.getOrCreate(endpoint.endpointKey) { retrofitBuilder(endpoint) }

    private fun retrofitBuilder(endpoint: ServerEndpoint): Retrofit =
        Retrofit.Builder()
            .baseUrl(endpoint.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}

private class LruRetrofitCache(private val maxSize: Int) {
    private val entries = object : LinkedHashMap<String, Retrofit>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Retrofit>?): Boolean =
            size > maxSize
    }

    @Synchronized
    fun getOrCreate(key: String, create: () -> Retrofit): Retrofit =
        entries[key] ?: create().also { entries[key] = it }

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}
