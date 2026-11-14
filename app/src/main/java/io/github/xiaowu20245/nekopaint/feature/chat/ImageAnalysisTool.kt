package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * analyze_image 工具的执行逻辑。
 *
 * 文本模型只拿到图片 ID；真正调用视觉模型时使用 App 缓存的图片。分析结果按
 * 图片 + 模式 + 问题缓存，同一会话内追问优先复用。
 */
internal class ImageAnalysisTool(
    private val visionAnalyzer: VisionAnalyzer?,
) {

    private val cache = LinkedHashMap<String, String>()

    suspend fun analyze(
        args: JsonObject,
        attachments: List<ChatImageAttachment>,
        direct: Boolean = false,
    ): String {
        val analyzer = visionAnalyzer
            ?: return "工具执行失败：视觉分析未配置"
        if (attachments.isEmpty()) {
            return "工具执行失败：当前对话没有可用图片"
        }
        val imageId = args["imageId"]?.jsonPrimitive?.contentOrNull?.trim()
        val image = if (imageId.isNullOrBlank()) {
            attachments.last()
        } else {
            attachments.firstOrNull { it.id == imageId }
                ?: return "工具执行失败：找不到图片 $imageId"
        }
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "describe"
        if (mode !in MODES) {
            return "工具执行失败：不支持的图片分析模式 $mode"
        }
        val question = args["question"]?.jsonPrimitive?.contentOrNull?.trim()
        if (mode == "question" && question.isNullOrBlank()) {
            return "工具执行失败：question 不能为空"
        }
        val cacheKey = buildString {
            append(image.id)
            append('|')
            append(image.dataUrl.hashCode())
            append('|')
            append(mode)
            append('|')
            append(question.orEmpty())
        }
        cache[cacheKey]?.let { cached ->
            return if (direct) {
                cached
            } else {
                "工具结果（本会话缓存）：\n$cached\n" +
                    "请基于以上结果回答用户；不要声称自己直接看到了图片。"
            }
        }
        val result = analyzer.analyze(
            ChatVisionRequest(
                imageDataUrl = image.dataUrl,
                mode = mode,
                question = question,
            ),
        ).getOrElse { error ->
            return "工具执行失败：${error.message ?: "视觉分析失败"}"
        }
        cache[cacheKey] = result
        while (cache.size > MAX_CACHE_ENTRIES) {
            cache.remove(cache.keys.first())
        }
        return if (direct) {
            result
        } else {
            "工具结果：视觉模型分析（$mode）\n$result\n" +
                "请基于以上结果回答用户；不要声称自己直接看到了图片。" +
                "如果结果包含 ```tags 代码块，最终回答必须原样保留。"
        }
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 16
        val MODES = setOf("tags", "describe", "question")
    }
}
