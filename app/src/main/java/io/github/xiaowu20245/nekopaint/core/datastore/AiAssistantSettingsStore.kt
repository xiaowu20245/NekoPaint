package io.github.xiaowu20245.nekopaint.core.datastore

import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import kotlinx.coroutines.flow.Flow

/**
 * The settings screen only needs this narrow store surface. Keeping it behind
 * an interface lets the credential/consent flow be tested with a fake store.
 */
interface AiAssistantSettingsStore {
    val config: Flow<AiAssistantConfig>

    suspend fun current(): AiAssistantConfig
    suspend fun currentHttpConsent(): AiHttpConsent
    suspend fun trustHttpOrigin(origin: String)
    suspend fun approveHttpKeyOrigin(origin: String)
    suspend fun currentDisclosureAcknowledgements(): Set<String>
    suspend fun acknowledgeDisclosure(origin: String)
    suspend fun updateServiceConfig(config: AiAssistantConfig): Result<Unit>
    suspend fun updatePersonaInstruction(persona: String, preInstruction: String): Result<Unit>
    suspend fun clear()
}
