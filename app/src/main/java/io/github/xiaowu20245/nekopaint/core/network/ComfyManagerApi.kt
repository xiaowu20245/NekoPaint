package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * ComfyUI-Manager 的 HTTP 接口。
 *
 * 这些接口不是 ComfyUI 核心能力：主机必须安装并启用 ComfyUI-Manager，
 * 且安全等级允许安装自定义节点。接口缺失时返回 404，权限不足时返回 403。
 */
interface ComfyManagerApi {

    @GET("manager/version")
    suspend fun version(): Response<ResponseBody>

    @GET("customnode/getmappings")
    suspend fun mappings(
        @Query("mode") mode: String = "cache",
    ): Response<JsonObject>

    @POST("manager/queue/install")
    suspend fun install(@Body body: JsonObject): Response<Unit>

    @GET("manager/queue/status")
    suspend fun queueStatus(): Response<ManagerQueueStatusDto>

    @POST("manager/queue/start")
    suspend fun startQueue(): Response<Unit>

    @POST("manager/reboot")
    suspend fun reboot(): Response<Unit>
}

@Serializable
data class ManagerQueueStatusDto(
    @SerialName("total_count")
    val totalCount: Int = 0,
    @SerialName("done_count")
    val doneCount: Int = 0,
    @SerialName("in_progress_count")
    val inProgressCount: Int = 0,
    @SerialName("is_processing")
    val isProcessing: Boolean = false,
)
