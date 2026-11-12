package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.annotation.StringRes
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig

/** 兼容 OpenAI 协议的常用服务商预设。 */
enum class AiProviderPreset(
    val id: String,
    val baseUrl: String,
    val textModel: String,
    val visionModel: String,
    @param:StringRes val labelRes: Int,
) {
    DeepSeek(
        id = "deepseek",
        baseUrl = "https://api.deepseek.com/v1",
        textModel = "deepseek-chat",
        visionModel = "",
        labelRes = R.string.ai_assistant_provider_deepseek,
    ),
    SiliconFlow(
        id = "siliconflow",
        baseUrl = "https://api.siliconflow.cn/v1",
        textModel = "Qwen/Qwen2.5-7B-Instruct",
        visionModel = "Qwen/Qwen2-VL-72B-Instruct",
        labelRes = R.string.ai_assistant_provider_silicon,
    ),
    OpenAi(
        id = "openai",
        baseUrl = "https://api.openai.com/v1",
        textModel = "gpt-4o-mini",
        visionModel = "gpt-4o-mini",
        labelRes = R.string.ai_assistant_provider_openai,
    ),
    Ollama(
        id = "ollama",
        // 本机回环地址只作为占位，用户需要改成电脑在局域网中的地址。
        baseUrl = "http://127.0.0.1:11434/v1",
        textModel = "qwen2.5:7b",
        visionModel = "llava:7b",
        labelRes = R.string.ai_assistant_provider_ollama,
    ),
    ;

    fun toConfig(currentKey: String): AiAssistantConfig = AiAssistantConfig(
        providerId = id,
        baseUrl = baseUrl,
        apiKey = currentKey,
        textModel = textModel,
        visionModel = visionModel,
    )

    companion object {
        fun fromId(id: String): AiProviderPreset? = entries.firstOrNull { it.id == id }
    }
}
