package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.network.PromptRequestDto
import io.github.xiaowu20245.nekopaint.core.network.PromptResponseDto
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ComfyApi {

    @GET("system_stats")
    suspend fun systemStats(): SystemStatsDto

    @GET("object_info")
    suspend fun objectInfo(): Map<String, NodeInfoDto>

    @GET("models")
    suspend fun modelFolders(): JsonElement

    @GET("models/{folder}")
    suspend fun models(@Path("folder") folder: String): JsonElement

    @POST("prompt")
    suspend fun submitPrompt(@Body body: PromptRequestDto): Response<PromptResponseDto>

    @GET("queue")
    suspend fun queue(): QueueDto

    @GET("history")
    suspend fun history(@Query("max_items") maxItems: Int? = null): Map<String, HistoryItemDto>

    @POST("interrupt")
    suspend fun interrupt(@Body body: InterruptRequestDto): Response<Unit>

    @POST("queue")
    suspend fun updateQueue(@Body body: QueueOperationDto): Response<Unit>
}
