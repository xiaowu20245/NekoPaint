package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantSettingsClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantSettingsViewModelTest {

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        store: FakeStore,
        client: FakeClient = FakeClient(),
    ): AiAssistantSettingsViewModel =
        AiAssistantSettingsViewModel(store = store, client = client)

    @Test
    fun newKeyWithInvalidOriginalUrlCanBindAfterUrlBecomesValid() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(baseUrl = "", textModel = "model"),
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.onApiKeyChange("sk-new")
        vm.onBaseUrlChange("https://api.example.com/v1")
        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.confirmationMessage)
        assertNull(store.saved)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals("sk-new", store.saved?.apiKey)
        assertEquals("https://api.example.com:443", store.saved?.apiKeyOrigin)
    }

    @Test
    fun legacyKeyWithInvalidOriginalUrlIsClearedRatherThanBoundToNewAddress() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "not-a-valid-url",
                apiKey = "sk-old",
                textModel = "model",
            ),
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.onBaseUrlChange("https://api.example.com/v1")
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.confirmationMessage)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals("", store.saved?.apiKey)
        assertNull(store.saved?.apiKeyOrigin)
    }

    @Test
    fun originChangeCanBeCancelledAndKeepsKey() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://old.example.com/v1",
                apiKey = "sk-old",
                apiKeyOrigin = "https://old.example.com:443",
                textModel = "model",
            ),
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.onBaseUrlChange("https://new.example.com/v1")
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.confirmationMessage)

        vm.cancelPendingAction()
        advanceUntilIdle()

        assertEquals("sk-old", vm.uiState.value.apiKey)
        assertNull(store.saved)
    }

    @Test
    fun publicHttpDisablesSubmissionAndDoesNotSave() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "http://8.8.8.8/v1",
                textModel = "model",
            ),
        )
        val client = FakeClient()
        val vm = createViewModel(store, client)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.policyBlocked)
        assertFalse(vm.uiState.value.canSubmit)

        vm.save()
        advanceUntilIdle()

        assertNull(store.saved)
        assertEquals(0, client.calls)
        assertEquals(NekoMessageKey.SETTINGS_AI_BLOCKED, vm.uiState.value.error?.key)
        assertTrue(vm.uiState.value.error?.args?.any { it.contains("公网") } == true)
    }

    @Test
    fun consentWriteFailureShowsErrorAndDoesNotContinueRequest() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "http://127.0.0.1:8188/v1",
                apiKey = "sk-test",
                apiKeyOrigin = "http://127.0.0.1:8188",
                textModel = "model",
            ),
            failConsentWrite = true,
        )
        val client = FakeClient()
        val vm = createViewModel(store, client)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.confirmationMessage)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(
            NekoMessageKey.SETTINGS_HTTP_CONFIRM_SAVE_FAILED,
            vm.uiState.value.error?.key,
        )
        assertNull(store.saved)
        assertEquals(0, client.calls)
    }

    @Test
    fun disclosureAcknowledgementFailureShowsError() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://api.example.com/v1",
                textModel = "model",
            ),
            failDisclosure = true,
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.acknowledgeDisclosure()
        advanceUntilIdle()

        assertEquals(
            NekoMessageKey.SETTINGS_DISCLOSURE_SAVE_FAILED,
            vm.uiState.value.error?.key,
        )
        assertFalse(vm.uiState.value.disclosureAcknowledged)
    }

    @Test
    fun testConnectionRequiresDisclosureAcknowledgement() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                apiKeyOrigin = "https://api.example.com:443",
                textModel = "model",
            ),
        )
        val client = FakeClient()
        val vm = createViewModel(store, client)
        advanceUntilIdle()

        vm.testConnection()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.confirmationMessage)
        assertEquals(0, client.calls)

        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(1, client.calls)
        assertTrue(store.disclosures.contains("v1|https://api.example.com:443"))
    }

    @Test
    fun clearConfigCanBeCancelled() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                apiKeyOrigin = "https://api.example.com:443",
                textModel = "model",
            ),
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.requestClearConfig()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.confirmationMessage)

        vm.cancelPendingAction()
        advanceUntilIdle()

        assertEquals(0, store.clearCalls)
        assertEquals("sk-test", vm.uiState.value.apiKey)
    }

    @Test
    fun clearConfigConfirmationResetsStateAndCallsStore() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                apiKeyOrigin = "https://api.example.com:443",
                textModel = "model",
            ),
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.requestClearConfig()
        advanceUntilIdle()
        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(1, store.clearCalls)
        assertTrue(vm.uiState.value.baseUrl.isBlank())
        assertTrue(vm.uiState.value.apiKey.isBlank())
        assertFalse(vm.uiState.value.disclosureAcknowledged)
    }

    @Test
    fun clearConfigFailureKeepsExistingState() = runViewModelTest {
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                apiKeyOrigin = "https://api.example.com:443",
                textModel = "model",
            ),
            failClear = true,
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.requestClearConfig()
        advanceUntilIdle()
        vm.confirmPendingAction()
        advanceUntilIdle()

        assertEquals(NekoMessageKey.SETTINGS_AI_CLEAR_FAILED, vm.uiState.value.error?.key)
        assertEquals("sk-test", vm.uiState.value.apiKey)
    }

    @Test
    fun successfulSavePersistsApiKeyOrigin() = runViewModelTest {
        val origin = "https://api.example.com:443"
        val store = FakeStore(
            initialConfig = AiAssistantConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                apiKeyOrigin = origin,
                textModel = "model",
            ),
        )
        val vm = createViewModel(store)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals("sk-test", store.saved?.apiKey)
        assertEquals(origin, store.saved?.apiKeyOrigin)
    }

    private class FakeStore(
        initialConfig: AiAssistantConfig,
        var consent: AiHttpConsent = AiHttpConsent(),
        private val failConsentWrite: Boolean = false,
        var disclosures: Set<String> = emptySet(),
        var failClear: Boolean = false,
        private val failDisclosure: Boolean = false,
    ) : AiAssistantSettingsStore {

        var saved: AiAssistantConfig? = null
        var clearCalls: Int = 0
        private val configFlow = MutableStateFlow(initialConfig)

        override val config: StateFlow<AiAssistantConfig> = configFlow

        override suspend fun current(): AiAssistantConfig = configFlow.value

        override suspend fun currentHttpConsent(): AiHttpConsent = consent

        override suspend fun currentDisclosureAcknowledgements(): Set<String> = disclosures

        override suspend fun acknowledgeDisclosure(origin: String) {
            if (failDisclosure) throw IOException("disclosure write failed")
            disclosures = disclosures + "v1|$origin"
        }

        override suspend fun trustHttpOrigin(origin: String) {
            if (failConsentWrite) throw IOException("consent write failed")
            consent = consent.copy(trustedOrigins = consent.trustedOrigins + origin)
        }

        override suspend fun approveHttpKeyOrigin(origin: String) {
            if (failConsentWrite) throw IOException("consent write failed")
            consent = consent.copy(keyApprovedOrigins = consent.keyApprovedOrigins + origin)
        }

        override suspend fun updateServiceConfig(config: AiAssistantConfig): Result<Unit> {
            saved = config
            configFlow.value = configFlow.value.copy(
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
            configFlow.value = configFlow.value.copy(
                persona = persona.trim(),
                preInstruction = preInstruction.trim(),
            )
            return Result.success(Unit)
        }

        override suspend fun clear() {
            clearCalls++
            if (failClear) throw IOException("clear failed")
            configFlow.value = AiAssistantConfig()
            saved = null
        }
    }

    private class FakeClient : AiAssistantSettingsClient {
        var calls: Int = 0

        override suspend fun testConnection(config: AiAssistantConfig): Result<Long> {
            calls++
            return Result.success(1L)
        }
    }
}
