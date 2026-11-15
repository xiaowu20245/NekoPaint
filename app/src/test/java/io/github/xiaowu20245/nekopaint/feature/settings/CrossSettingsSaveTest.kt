package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
import io.github.xiaowu20245.nekopaint.core.datastore.NekoSettingsPreferences
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantSettingsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrossSettingsSaveTest {

    @Test
    fun aiServiceSaveDoesNotOverwriteNewerPersona() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = SharedStore(
                AiAssistantConfig(
                    baseUrl = "https://api.example.com/v1",
                    textModel = "old-model",
                    persona = "old-persona",
                ),
            )
            val nekoStore = FakeNekoPreferences()
            val neko = NekoSettingsViewModel(store, nekoStore)
            advanceUntilIdle()
            neko.onPersonaChange("new-persona")
            neko.onPreInstructionChange("new-instruction")
            neko.save()
            advanceUntilIdle()

            val ai = AiAssistantSettingsViewModel(store, NoopClient())
            advanceUntilIdle()
            ai.onTextModelChange("new-model")
            ai.save()
            advanceUntilIdle()

            assertEquals("new-persona", store.config.value.persona)
            assertEquals("new-instruction", store.config.value.preInstruction)
            assertEquals("new-model", store.config.value.textModel)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun nekoSaveDoesNotOverwriteNewerServiceFields() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = SharedStore(
                AiAssistantConfig(
                    baseUrl = "https://api.example.com/v1",
                    textModel = "old-model",
                    persona = "old-persona",
                ),
            )
            val neko = NekoSettingsViewModel(store, FakeNekoPreferences())
            advanceUntilIdle()
            val ai = AiAssistantSettingsViewModel(store, NoopClient())
            advanceUntilIdle()
            ai.onTextModelChange("new-model")
            ai.save()
            advanceUntilIdle()

            neko.onPersonaChange("new-persona")
            neko.save()
            advanceUntilIdle()

            assertEquals("new-model", store.config.value.textModel)
            assertEquals("https://api.example.com/v1", store.config.value.baseUrl)
            assertEquals("new-persona", store.config.value.persona)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class SharedStore(initial: AiAssistantConfig) : AiAssistantSettingsStore {
        private val flow = MutableStateFlow(initial)
        override val config: StateFlow<AiAssistantConfig> = flow

        override suspend fun current(): AiAssistantConfig = flow.value

        override suspend fun currentHttpConsent(): AiHttpConsent = AiHttpConsent()

        override suspend fun trustHttpOrigin(origin: String) = Unit

        override suspend fun approveHttpKeyOrigin(origin: String) = Unit

        override suspend fun currentDisclosureAcknowledgements(): Set<String> = emptySet()

        override suspend fun acknowledgeDisclosure(origin: String) = Unit

        override suspend fun updateServiceConfig(config: AiAssistantConfig): Result<Unit> {
            flow.value = flow.value.copy(
                providerId = config.providerId,
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                apiKeyOrigin = config.apiKeyOrigin,
                textModel = config.textModel,
                visionModel = config.visionModel,
            )
            return Result.success(Unit)
        }

        override suspend fun updatePersonaInstruction(
            persona: String,
            preInstruction: String,
        ): Result<Unit> {
            flow.value = flow.value.copy(
                persona = persona.trim(),
                preInstruction = preInstruction.trim(),
            )
            return Result.success(Unit)
        }

        override suspend fun clear() {
            flow.value = AiAssistantConfig()
        }
    }

    private class FakeNekoPreferences : NekoSettingsPreferences {
        private val agentMode = MutableStateFlow(false)
        override val agentModeByDefault: StateFlow<Boolean> = agentMode

        override suspend fun setAgentModeByDefault(value: Boolean) {
            agentMode.value = value
        }
    }

    private class NoopClient : AiAssistantSettingsClient {
        override suspend fun testConnection(config: AiAssistantConfig): Result<Long> =
            Result.success(0L)
    }
}
