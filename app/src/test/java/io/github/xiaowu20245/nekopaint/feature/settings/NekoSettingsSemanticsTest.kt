package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
import io.github.xiaowu20245.nekopaint.core.datastore.NekoSettingsPreferences
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NekoSettingsSemanticsTest {

    @Test
    fun textEditsAreDirtyWhileDefaultModeSavesImmediately() = runTest {
        dispatcherTest {
            val preferences = FakePreferences()
            val vm = NekoSettingsViewModel(
                aiAssistantStore = FakeStore(AiAssistantConfig(persona = "old")),
                settingsStore = preferences,
            )
            advanceUntilIdle()
            assertFalse(vm.uiState.value.dirty)

            vm.onPersonaChange("new persona")
            assertTrue(vm.uiState.value.dirty)

            vm.setAgentModeByDefault(true)
            advanceUntilIdle()

            assertTrue(preferences.value)
            assertTrue("默认模式即时保存不应清除人设 dirty", vm.uiState.value.dirty)
        }
    }

    @Test
    fun saveTrimsTextClearsDirtyAndKeepsServiceFields() = runTest {
        dispatcherTest {
            val store = FakeStore(
                AiAssistantConfig(
                    baseUrl = "https://api.example.com/v1",
                    textModel = "model",
                    persona = "old",
                ),
            )
            val vm = NekoSettingsViewModel(store, FakePreferences())
            advanceUntilIdle()

            vm.onPersonaChange("  new persona  ")
            vm.onPreInstructionChange("  answer briefly  ")
            vm.save()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.dirty)
            assertEquals("new persona", vm.uiState.value.persona)
            assertEquals("answer briefly", vm.uiState.value.preInstruction)
            assertEquals("new persona", store.config.value.persona)
            assertEquals("https://api.example.com/v1", store.config.value.baseUrl)
            assertEquals("model", store.config.value.textModel)
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.dispatcherTest(
        block: suspend kotlinx.coroutines.test.TestScope.() -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakePreferences : NekoSettingsPreferences {
        private val flow = MutableStateFlow(false)
        override val agentModeByDefault: StateFlow<Boolean> = flow
        val value: Boolean
            get() = flow.value

        override suspend fun setAgentModeByDefault(value: Boolean) {
            flow.value = value
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
}
