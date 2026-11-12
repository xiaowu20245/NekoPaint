package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.security.SecretCipher
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
) {

    private val dataStore = context.applicationContext.aiAssistantDataStore

    val config: Flow<AiAssistantConfig> = dataStore.data.map { preferences ->
        val encryptedKey = preferences[ApiKey] ?: ""
        AiAssistantConfig(
            providerId = preferences[ProviderId] ?: AiAssistantConfig.PROVIDER_CUSTOM,
            baseUrl = preferences[BaseUrl] ?: "",
            apiKey = if (encryptedKey.isBlank()) "" else cipher.decrypt(encryptedKey).orEmpty(),
            textModel = preferences[TextModel] ?: "",
            visionModel = preferences[VisionModel] ?: "",
        )
    }

    suspend fun current(): AiAssistantConfig = config.first()

    suspend fun save(config: AiAssistantConfig): Result<Unit> = runCatching {
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
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ProviderId)
            preferences.remove(BaseUrl)
            preferences.remove(TextModel)
            preferences.remove(VisionModel)
            preferences.remove(ApiKey)
        }
    }

    private companion object {
        val ProviderId = stringPreferencesKey("provider_id")
        val BaseUrl = stringPreferencesKey("base_url")
        val TextModel = stringPreferencesKey("text_model")
        val VisionModel = stringPreferencesKey("vision_model")
        val ApiKey = stringPreferencesKey("api_key_encrypted")
    }
}
