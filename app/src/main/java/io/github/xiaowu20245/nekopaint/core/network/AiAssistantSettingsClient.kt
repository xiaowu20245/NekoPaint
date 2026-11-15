package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig

interface AiAssistantSettingsClient {
    suspend fun testConnection(config: AiAssistantConfig): Result<Long>
}
