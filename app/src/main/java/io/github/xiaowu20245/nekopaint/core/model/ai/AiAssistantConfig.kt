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
    val apiKeyOrigin: String? = null,
    val textModel: String = "",
    val visionModel: String = "",
    val persona: String = "",
    val preInstruction: String = "",
    /** 密文存在但无法解密时为 true；UI 应提示重新输入 Key，绝不回显密文。 */
    val apiKeyInvalid: Boolean = false,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && textModel.isNotBlank()

    companion object {
        const val PROVIDER_CUSTOM = "custom"
    }
}
