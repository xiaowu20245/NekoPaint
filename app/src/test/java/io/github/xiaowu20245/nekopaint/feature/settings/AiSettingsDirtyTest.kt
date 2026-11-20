package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsDirtyTest {

    private val baseline = AiServiceDraft(
        providerId = AiProviderPreset.DeepSeek.id,
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "sk-test",
        textModel = "deepseek-chat",
        visionModel = "",
    )

    @Test
    fun eachServiceFieldChangeMarksDirty() {
        assertFalse(AiAssistantSettingsPolicy.isDirty(baseline, baseline))
        assertTrue(
            AiAssistantSettingsPolicy.isDirty(
                baseline.copy(providerId = AiAssistantConfig.PROVIDER_CUSTOM),
                baseline,
            ),
        )
        assertTrue(
            AiAssistantSettingsPolicy.isDirty(
                baseline.copy(baseUrl = "https://api.example.com/v1"),
                baseline,
            ),
        )
        assertTrue(
            AiAssistantSettingsPolicy.isDirty(baseline.copy(apiKey = "sk-new"), baseline),
        )
        assertTrue(
            AiAssistantSettingsPolicy.isDirty(baseline.copy(textModel = "new-model"), baseline),
        )
        assertTrue(
            AiAssistantSettingsPolicy.isDirty(baseline.copy(visionModel = "vision-model"), baseline),
        )
    }

    @Test
    fun transientUiStateDoesNotAffectServiceDraft() {
        val state = AiAssistantFormState(
            providerId = baseline.providerId,
            baseUrl = baseline.baseUrl,
            apiKey = baseline.apiKey,
            textModel = baseline.textModel,
            visionModel = baseline.visionModel,
            keyVisible = true,
            testing = true,
            saving = true,
            testLatencyMs = 120L,
            saved = true,
        )

        assertEquals(baseline, AiAssistantSettingsPolicy.serviceDraft(state))
    }

    @Test
    fun successfulSaveClearsDirty() = runViewModelTest {
        val store = FakeStore(
            AiAssistantConfig(
                providerId = AiProviderPreset.DeepSeek.id,
                baseUrl = "https://api.example.com/v1",
                textModel = "old-model",
            ),
        )
        val vm = AiAssistantSettingsViewModel(store, NoopClient())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.dirty)

        vm.onTextModelChange("new-model")
        assertTrue(vm.uiState.value.dirty)

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.dirty)
        assertEquals("new-model", store.config.value.textModel)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun discardChangesRestoresApiKeyOriginAndAvoidsMixedCredentialState() = runViewModelTest {
        val oldOrigin = "https://old.example.com:443"
        val store = FakeStore(
            AiAssistantConfig(
                baseUrl = "https://old.example.com/v1",
                apiKey = "sk-old",
                apiKeyOrigin = oldOrigin,
                textModel = "model",
            ),
        )
        val vm = AiAssistantSettingsViewModel(store, NoopClient())
        advanceUntilIdle()

        vm.onBaseUrlChange("https://new.example.com/v1")
        vm.onApiKeyChange("sk-new")
        assertTrue(vm.uiState.value.dirty)

        vm.discardChanges()

        assertEquals("https://old.example.com/v1", vm.uiState.value.baseUrl)
        assertEquals("sk-old", vm.uiState.value.apiKey)
        assertEquals(oldOrigin, vm.uiState.value.apiKeyOrigin)
        assertNull(vm.uiState.value.credentialMessage)
        assertFalse(vm.uiState.value.dirty)

        vm.save()
        advanceUntilIdle()

        assertEquals("sk-old", store.config.value.apiKey)
        assertEquals(oldOrigin, store.config.value.apiKeyOrigin)
    }

    @Test
    fun discardChangesRestoresSavedServiceFields() = runViewModelTest {
        val store = FakeStore(
            AiAssistantConfig(
                providerId = AiProviderPreset.DeepSeek.id,
                baseUrl = "https://api.example.com/v1",
                textModel = "model",
            ),
        )
        val vm = AiAssistantSettingsViewModel(store, NoopClient())
        advanceUntilIdle()

        vm.onBaseUrlChange("https://other.example.com/v1")
        vm.onTextModelChange("changed")
        assertTrue(vm.uiState.value.dirty)

        vm.discardChanges()

        assertEquals("https://api.example.com/v1", vm.uiState.value.baseUrl)
        assertEquals("model", vm.uiState.value.textModel)
        assertFalse(vm.uiState.value.dirty)
    }

    private fun runViewModelTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class FakeStore(initial: AiAssistantConfig) : AiAssistantSettingsStore {
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

    private class NoopClient : AiAssistantSettingsClient {
        override suspend fun testConnection(config: AiAssistantConfig): Result<Long> =
            Result.success(0L)
    }
}
