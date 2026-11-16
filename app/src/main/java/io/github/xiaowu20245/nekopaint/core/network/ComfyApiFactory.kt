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

    fun create(config: ServerConfig): ComfyApi = retrofit(ServerEndpointValidator.require(config))

    fun create(baseUrl: String): ComfyApi = retrofit(ServerEndpointValidator.requireBaseUrl(baseUrl))

    fun createManager(config: ServerConfig): ComfyManagerApi =
        retrofitManager(ServerEndpointValidator.require(config))

    fun createManager(baseUrl: String): ComfyManagerApi =
        retrofitManager(ServerEndpointValidator.requireBaseUrl(baseUrl))

    private fun retrofit(endpoint: ServerEndpoint): ComfyApi =
        retrofitBuilder(endpoint).create(ComfyApi::class.java)

    private fun retrofitManager(endpoint: ServerEndpoint): ComfyManagerApi =
        retrofitBuilder(endpoint).create(ComfyManagerApi::class.java)

    private fun retrofitBuilder(endpoint: ServerEndpoint): Retrofit =
        Retrofit.Builder()
            .baseUrl(endpoint.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
