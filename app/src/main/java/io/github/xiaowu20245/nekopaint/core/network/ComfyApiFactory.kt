package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ComfyApiFactory(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    fun create(config: ServerConfig): ComfyApi = create(config.baseUrl)

    fun create(baseUrl: String): ComfyApi {
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/'" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ComfyApi::class.java)
    }
}
