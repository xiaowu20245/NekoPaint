package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class AiChatMessage(
    val role: String,
    val content: String,
    val imageDataUrl: String? = null,
)

/**
 * OpenAI 兼容的 chat/completions 客户端。
 *
 * 只实现本项目需要的三个能力：连接测试、文本翻译、提示词润色。
 * 不记录请求体，避免提示词或 API Key 进入日志。
 */
class AiAssistantClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val sessionIdProvider: suspend () -> String = { "" },
) {

    suspend fun testConnection(config: AiAssistantConfig): Result<Long> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("请填写 Base URL 和文本模型"))
        }
        val startedAt = System.nanoTime()
        val result = executeChat(
            config = config,
            messages = listOf(AiChatMessage("user", "hi")),
            maxTokens = 8,
        )
        result.map { (System.nanoTime() - startedAt) / 1_000_000 }
    }

    suspend fun translate(config: AiAssistantConfig, input: String): Result<String> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("尚未配置 AI 助手"))
        }
        return executeChat(
            config = config,
            messages = listOf(
                AiChatMessage(
                    role = "system",
                    content = "你是 Danbooru 标签翻译助手。把用户给出的中文提示词翻译成英文 " +
                        "Danbooru 标签，只输出用英文逗号分隔的标签，不要解释，不要编号。",
                ),
                AiChatMessage(role = "user", content = input.trim()),
            ),
        )
    }

    suspend fun polish(config: AiAssistantConfig, prompt: String): Result<String> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("尚未配置 AI 助手"))
        }
        return executeChat(
            config = config,
            messages = listOf(
                AiChatMessage(
                    role = "system",
                    content = "你是 Stable Diffusion 提示词润色助手。把用户提示词改写为更清晰、具体的" +
                        "英文 Danbooru 标签序列，保留原意和已有标签，只输出用英文逗号分隔的标签，" +
                        "不要解释，不要编号。",
                ),
                AiChatMessage(role = "user", content = prompt.trim()),
            ),
        )
    }

    /**
     * 通用多轮对话；带图片时使用配置中的视觉模型。
     */
    suspend fun chat(
        config: AiAssistantConfig,
        messages: List<AiChatMessage>,
    ): Result<String> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("尚未配置 AI 助手"))
        }
        val hasImage = messages.any { !it.imageDataUrl.isNullOrBlank() }
        val model = if (hasImage) config.visionModel.trim() else config.textModel.trim()
        if (model.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    if (hasImage) "请先在设置中填写视觉模型" else "请先在设置中填写文本模型",
                ),
            )
        }
        return executeChat(
            config = config,
            messages = messages,
            maxTokens = 1024,
            model = model,
        )
    }

    private suspend fun executeChat(
        config: AiAssistantConfig,
        messages: List<AiChatMessage>,
        maxTokens: Int = 512,
        model: String = config.textModel,
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = buildEndpoint(config.baseUrl)
        val body = buildJsonObject {
            put("model", model.trim())
            putJsonArray("messages") {
                messages.forEach { message ->
                    addJsonObject {
                        put("role", message.role)
                        val image = message.imageDataUrl
                        if (image.isNullOrBlank()) {
                            put("content", message.content)
                        } else {
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", message.content)
                                }
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", image)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            put("stream", false)
            put("max_tokens", maxTokens)
        }.toString()

        val sessionId = runCatching { sessionIdProvider() }.getOrDefault("")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NekoPaint/${BuildConfig.VERSION_NAME}")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (config.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
                // OpenCode Go 用该头做路由与提示缓存；其他兼容服务会忽略未知头。
                if (sessionId.isNotBlank()) {
                    header("x-opencode-session", sessionId)
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body.string()
                if (!response.isSuccessful) {
                    val detail = AiChatResponseParser.parseErrorMessage(json, rawBody)
                    return@withContext Result.failure(
                        IllegalStateException(
                            friendlyHttpError(response.code, detail),
                        ),
                    )
                }
                val content = AiChatResponseParser.parseContent(json, rawBody)
                if (content.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("服务已响应，但未返回可用文本"),
                    )
                }
                Result.success(content)
            }
        } catch (timeout: SocketTimeoutException) {
            Result.failure(timeout)
        } catch (unknown: UnknownHostException) {
            Result.failure(IllegalStateException("无法解析服务器地址，请检查 Base URL"))
        } catch (io: IOException) {
            Result.failure(IllegalStateException("网络请求失败：${io.message ?: "请检查网络和地址"}"))
        }
    }

    private fun buildEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun friendlyHttpError(code: Int, detail: String?): String {
        val reason = when (code) {
            401, 403 -> "API Key 无效或没有权限"
            404 -> "接口地址不存在，请检查 Base URL 是否包含 /v1"
            429 -> "请求过于频繁或额度不足"
            in 500..599 -> "服务端暂时不可用"
            else -> "HTTP $code"
        }
        val extra = detail?.trim()?.take(160)
        return if (extra.isNullOrBlank()) reason else "$reason（$extra）"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
