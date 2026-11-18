package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig

/**
 * Neko I 文本模型调用的最小契约。
 *
 * 由 [AiAssistantClient] 实现；ChatViewModel 依赖该接口，便于 JVM 并发测试
 * 注入可挂起的 fake，而不需要 Android Context 或真实网络。
 */
interface ChatCompletionClient {
    suspend fun chat(
        config: AiAssistantConfig,
        messages: List<AiChatMessage>,
    ): Result<String>

    suspend fun chatWithTools(
        config: AiAssistantConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition>,
        toolChoice: String? = "auto",
    ): Result<AiChatCompletion>

    suspend fun chatWithToolsStream(
        config: AiAssistantConfig,
        messages: List<AiChatMessage>,
        tools: List<AiToolDefinition>,
        toolChoice: String? = "auto",
        onDelta: (String) -> Unit,
    ): Result<AiChatCompletion>
}

/** 发送前等待隐私配置、并把模型可见文本中的敏感主机替换掉的契约。 */
interface ChatPrivacyGuard {
    suspend fun awaitReady(): Boolean

    fun sanitize(text: String): String
}
