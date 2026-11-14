package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantClient
import io.github.xiaowu20245.nekopaint.core.network.AiChatMessage
import kotlinx.coroutines.flow.first

data class ChatImageAttachment(
    val id: String,
    val dataUrl: String,
)

data class ChatVisionRequest(
    val imageDataUrl: String,
    val mode: String,
    val question: String? = null,
)

/**
 * 把视觉模型包装成可被文本模型调用的工具。
 */
fun interface VisionAnalyzer {
    suspend fun analyze(request: ChatVisionRequest): Result<String>
}

class ChatVisionAnalyzer(
    private val client: AiAssistantClient,
    private val store: AiAssistantStore,
) : VisionAnalyzer {

    override suspend fun analyze(request: ChatVisionRequest): Result<String> {
        val config = runCatching { store.config.first() }.getOrElse { error ->
            return Result.failure(
                IllegalStateException("读取 AI 配置失败：${error.message ?: "未知错误"}"),
            )
        }
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("尚未配置 AI 助手"))
        }
        if (config.visionModel.isBlank()) {
            return Result.failure(IllegalStateException("请先在设置中填写视觉模型"))
        }
        return client.chat(
            config = config,
            messages = listOf(
                AiChatMessage(
                    role = "system",
                    content = VISION_ANALYSIS_SYSTEM_PROMPT,
                ),
                AiChatMessage(
                    role = "user",
                    content = visionUserPrompt(request),
                    imageDataUrl = request.imageDataUrl,
                ),
            ),
        )
    }
}

internal fun visionUserPrompt(request: ChatVisionRequest): String = when (request.mode) {
    "tags" -> "请分析这张图片并提取英文 Danbooru 风格标签。" +
        "先给 1-2 句中文描述，再输出 ```tags 代码块；代码块内只放标签。"

    "describe" -> "请用中文描述这张图片，不要输出标签代码块。"
    "question" -> "请根据这张图片回答：" + request.question.orEmpty()
    else -> "请描述这张图片。"
}

internal fun imageAvailabilityPrompt(attachments: List<ChatImageAttachment>): String = buildString {
    appendLine("当前对话有 ${attachments.size} 张用户发送的图片可用，但图片内容没有直接展示给你：")
    attachments.forEachIndexed { index, image ->
        append("- imageId=${image.id}")
        if (index == attachments.lastIndex) append("（最新）")
        appendLine()
    }
    appendLine("需要看图、提取标签或回答图片问题时，必须调用 analyze_image。")
    appendLine("不要猜测图片内容，也不要声称自己已经看到了图片。")
    append("如果用户只发了图片没有文字，默认调用 analyze_image，mode=tags。")
}

private const val VISION_ANALYSIS_SYSTEM_PROMPT =
    "你是 Neko I 的图片分析助手。只根据图片中确实可见的内容回答，不要编造。" +
        "如果要求 tags：先给 1-2 句中文描述，再输出 ```tags 代码块；" +
        "代码块内只放英文 Danbooru 风格标签，逗号加空格分隔，多词标签用下划线，例如 long_hair。" +
        "标签按重要性排序，覆盖人数、角色/作品（确定才写）、发型、眼睛、表情、服装、动作、构图、背景、光照和画风。" +
        "不要重复标签，不确定的内容不要写。" +
        "如果要求 describe：只输出中文画面描述。" +
        "如果要求 question：直接回答用户问题。"
