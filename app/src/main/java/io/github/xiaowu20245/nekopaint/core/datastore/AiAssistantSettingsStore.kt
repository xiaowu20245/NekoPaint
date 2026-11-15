package io.github.xiaowu20245.nekopaint.core.datastore

import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent

/**
 * The settings screen only needs this narrow store surface. Keeping it behind
 * an interface lets the credential/consent flow be tested with a fake store.
 */
interface AiAssistantSettingsStore {
    suspend fun current(): AiAssistantConfig
    suspend fun currentHttpConsent(): AiHttpConsent
    suspend fun trustHttpOrigin(origin: String)
    suspend fun approveHttpKeyOrigin(origin: String)
    suspend fun save(config: AiAssistantConfig): Result<Unit>
}
