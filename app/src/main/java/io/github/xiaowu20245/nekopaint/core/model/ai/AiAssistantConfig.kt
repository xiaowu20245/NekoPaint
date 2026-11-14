package io.github.xiaowu20245.nekopaint.core.model.ai

/**
 * OpenAI 兼容大模型助手的本地配置。
 *
 * API Key 只在仓库层解密后短暂使用；日志、诊断和备份都不得输出它。
 */
data class AiAssistantConfig(
    val providerId: String = PROVIDER_CUSTOM,
    val baseUrl: String = "",
    val apiKey: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    val persona: String = "",
    val preInstruction: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && textModel.isNotBlank()

    companion object {
        const val PROVIDER_CUSTOM = "custom"
    }
}
