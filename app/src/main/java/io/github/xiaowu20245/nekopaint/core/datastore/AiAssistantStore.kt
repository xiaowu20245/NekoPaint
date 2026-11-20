package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiDisclosurePolicy
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.security.SecretCipher
import io.github.xiaowu20245.nekopaint.core.security.SecretCipherPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiAssistantDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_assistant_settings",
)

/**
 * AI 助手配置存储。
 *
 * 字段中只有 API Key 是敏感数据，使用 [SecretCipher] 加密后写入 DataStore；
 * 其余字段保留明文以方便读取摘要。
 */
class AiAssistantStore(
    context: Context,
    private val cipher: SecretCipher = SecretCipher(),
) : AiAssistantSettingsStore {

    private val dataStore = context.applicationContext.aiAssistantDataStore

    override val config: Flow<AiAssistantConfig> = dataStore.data.map { preferences ->
        val encryptedKey = preferences[ApiKey] ?: ""
        val decryptedKey = if (encryptedKey.isBlank()) {
            ""
        } else {
            cipher.decrypt(encryptedKey)
        }
        AiAssistantConfig(
            providerId = preferences[ProviderId] ?: AiAssistantConfig.PROVIDER_CUSTOM,
            baseUrl = preferences[BaseUrl] ?: "",
            apiKey = decryptedKey.orEmpty(),
            apiKeyOrigin = preferences[ApiKeyOrigin],
            textModel = preferences[TextModel] ?: "",
            visionModel = preferences[VisionModel] ?: "",
            persona = preferences[Persona] ?: "",
            preInstruction = preferences[PreInstruction] ?: "",
            apiKeyInvalid = SecretCipherPolicy.isInvalid(encryptedKey, decryptedKey),
        )
    }

    override suspend fun current(): AiAssistantConfig = config.first()

    val httpConsent: Flow<AiHttpConsent> = dataStore.data.map { preferences ->
        AiHttpConsent(
            trustedOrigins = preferences[HttpTrustedOrigins].orEmpty(),
            keyApprovedOrigins = preferences[HttpKeyApprovedOrigins].orEmpty(),
        )
    }

    override suspend fun currentHttpConsent(): AiHttpConsent = httpConsent.first()

    override suspend fun currentDisclosureAcknowledgements(): Set<String> =
        dataStore.data.map { it[DisclosureAcknowledgements].orEmpty() }.first()

    override suspend fun acknowledgeDisclosure(origin: String) {
        if (origin.isBlank()) return
        dataStore.edit { preferences ->
            preferences[DisclosureAcknowledgements] =
                preferences[DisclosureAcknowledgements].orEmpty() +
                    AiDisclosurePolicy.key(origin)
        }
    }

    suspend fun isDisclosureAcknowledged(origin: String): Boolean =
        AiDisclosurePolicy.isAcknowledged(origin, currentDisclosureAcknowledgements())

    override suspend fun trustHttpOrigin(origin: String) {
        if (origin.isBlank()) return
        dataStore.edit { preferences ->
            preferences[HttpTrustedOrigins] =
                preferences[HttpTrustedOrigins].orEmpty() + origin
        }
    }

    override suspend fun approveHttpKeyOrigin(origin: String) {
        if (origin.isBlank()) return
        dataStore.edit { preferences ->
            preferences[HttpKeyApprovedOrigins] =
                preferences[HttpKeyApprovedOrigins].orEmpty() + origin
        }
    }

    override suspend fun updateServiceConfig(config: AiAssistantConfig): Result<Unit> = runCatching {
        val encryptedKey = if (config.apiKey.isBlank()) {
            ""
        } else {
            cipher.encrypt(config.apiKey)
                ?: error("无法加密 API Key，请重试")
        }
        dataStore.edit { preferences ->
            preferences[ProviderId] = config.providerId
            preferences[BaseUrl] = config.baseUrl.trim()
            preferences[TextModel] = config.textModel.trim()
            preferences[VisionModel] = config.visionModel.trim()
            preferences[ApiKey] = encryptedKey
            if (config.apiKey.isBlank() || config.apiKeyOrigin.isNullOrBlank()) {
                preferences.remove(ApiKeyOrigin)
            } else {
                preferences[ApiKeyOrigin] = config.apiKeyOrigin
            }
        }
    }

    override suspend fun updatePersonaInstruction(
        persona: String,
        preInstruction: String,
    ): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences[Persona] = persona.trim()
            preferences[PreInstruction] = preInstruction.trim()
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ProviderId)
            preferences.remove(BaseUrl)
            preferences.remove(TextModel)
            preferences.remove(VisionModel)
            preferences.remove(Persona)
            preferences.remove(PreInstruction)
            preferences.remove(ApiKey)
            preferences.remove(ApiKeyOrigin)
            preferences.remove(HttpTrustedOrigins)
            preferences.remove(HttpKeyApprovedOrigins)
            preferences.remove(DisclosureAcknowledgements)
        }
    }

    private companion object {
        val ProviderId = stringPreferencesKey("provider_id")
        val BaseUrl = stringPreferencesKey("base_url")
        val TextModel = stringPreferencesKey("text_model")
        val VisionModel = stringPreferencesKey("vision_model")
        val Persona = stringPreferencesKey("persona")
        val PreInstruction = stringPreferencesKey("pre_instruction")
        val ApiKey = stringPreferencesKey("api_key_encrypted")
        val ApiKeyOrigin = stringPreferencesKey("api_key_origin")
        val HttpTrustedOrigins = stringSetPreferencesKey("http_trusted_origins")
        val HttpKeyApprovedOrigins = stringSetPreferencesKey("http_key_approved_origins")
        val DisclosureAcknowledgements = stringSetPreferencesKey("ai_disclosure_acknowledgements")
    }
}
