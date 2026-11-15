package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantSettingsClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            config = AiAssistantConfig(baseUrl = "", textModel = "model"),
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
            config = AiAssistantConfig(
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
            config = AiAssistantConfig(
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
            config = AiAssistantConfig(
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
        assertTrue(vm.uiState.value.error.orEmpty().contains("公网"))
    }

    @Test
    fun consentWriteFailureShowsErrorAndDoesNotContinueRequest() = runViewModelTest {
        val store = FakeStore(
            config = AiAssistantConfig(
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

        assertTrue(vm.uiState.value.error.orEmpty().contains("无法保存"))
        assertNull(store.saved)
        assertEquals(0, client.calls)
    }

    @Test
    fun successfulSavePersistsApiKeyOrigin() = runViewModelTest {
        val origin = "https://api.example.com:443"
        val store = FakeStore(
            config = AiAssistantConfig(
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
        var config: AiAssistantConfig,
        var consent: AiHttpConsent = AiHttpConsent(),
        private val failConsentWrite: Boolean = false,
    ) : AiAssistantSettingsStore {

        var saved: AiAssistantConfig? = null

        override suspend fun current(): AiAssistantConfig = config

        override suspend fun currentHttpConsent(): AiHttpConsent = consent

        override suspend fun trustHttpOrigin(origin: String) {
            if (failConsentWrite) throw IOException("consent write failed")
            consent = consent.copy(trustedOrigins = consent.trustedOrigins + origin)
        }

        override suspend fun approveHttpKeyOrigin(origin: String) {
            if (failConsentWrite) throw IOException("consent write failed")
            consent = consent.copy(keyApprovedOrigins = consent.keyApprovedOrigins + origin)
        }

        override suspend fun save(config: AiAssistantConfig): Result<Unit> {
            saved = config
            return Result.success(Unit)
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
