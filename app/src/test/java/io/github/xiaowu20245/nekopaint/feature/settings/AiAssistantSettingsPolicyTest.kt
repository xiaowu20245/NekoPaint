package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantSettingsPolicyTest {

    private fun endpoint(raw: String) = AiEndpointValidator.parse(raw).getOrThrow()

    @Test
    fun invalidBaseUrlDoesNotRebindNewKeyToOldOrigin() {
        val oldOrigin = endpoint("https://old.example.com/v1").origin

        assertNull(
            AiAssistantSettingsPolicy.apiKeyOriginForNewKey(
                value = "sk-new",
                endpoint = null,
            ),
        )
        assertEquals(
            oldOrigin,
            AiAssistantSettingsPolicy.apiKeyOriginForNewKey(
                value = "sk-new",
                endpoint = endpoint("https://old.example.com/v1"),
            ),
        )
    }

    @Test
    fun sameOriginPresetKeepsUnboundKeyButCrossOriginRequiresClear() {
        val current = endpoint("https://api.example.com/v1")
        val same = endpoint("https://api.example.com/v2")
        val other = endpoint("https://other.example.com/v1")

        assertFalse(
            AiAssistantSettingsPolicy.presetRequiresKeyClear(
                currentEndpoint = current,
                presetEndpoint = same,
                apiKey = "sk-test",
                apiKeyOrigin = null,
            ),
        )
        assertTrue(
            AiAssistantSettingsPolicy.presetRequiresKeyClear(
                currentEndpoint = current,
                presetEndpoint = other,
                apiKey = "sk-test",
                apiKeyOrigin = null,
            ),
        )
        assertTrue(
            AiAssistantSettingsPolicy.presetRequiresKeyClear(
                currentEndpoint = null,
                presetEndpoint = same,
                apiKey = "sk-test",
                apiKeyOrigin = null,
            ),
        )
    }

    @Test
    fun boundKeyOnlyKeepsPresetWhenOriginMatches() {
        val origin = endpoint("https://api.example.com/v1").origin

        assertFalse(
            AiAssistantSettingsPolicy.presetRequiresKeyClear(
                currentEndpoint = endpoint("https://api.example.com/v1"),
                presetEndpoint = endpoint("https://api.example.com/v2"),
                apiKey = "sk-test",
                apiKeyOrigin = origin,
            ),
        )
        assertTrue(
            AiAssistantSettingsPolicy.presetRequiresKeyClear(
                currentEndpoint = endpoint("https://api.example.com/v1"),
                presetEndpoint = endpoint("https://other.example.com/v1"),
                apiKey = "sk-test",
                apiKeyOrigin = origin,
            ),
        )
    }

    @Test
    fun publicHttpIsHardBlockedAndDisablesSubmission() {
        val state = AiAssistantFormState(
            baseUrl = "http://8.8.8.8/v1",
            textModel = "model",
        )
        val derived = AiAssistantSettingsPolicy.derive(state, AiHttpConsent())
        val next = state.copy(
            baseUrlError = derived.baseUrlError,
            policyBlocked = derived.policyBlocked,
        )

        assertTrue(derived.policyBlocked)
        assertFalse(next.canSubmit)
    }

    @Test
    fun localDomainTrustNeedsConfirmationButDoesNotHardBlockButtons() {
        val state = AiAssistantFormState(
            baseUrl = "http://my-ai.local/v1",
            textModel = "model",
        )
        val derived = AiAssistantSettingsPolicy.derive(state, AiHttpConsent())
        val next = state.copy(
            baseUrlError = derived.baseUrlError,
            policyBlocked = derived.policyBlocked,
        )

        assertFalse(derived.policyBlocked)
        assertTrue(next.canSubmit)
        assertTrue(derived.policyMessage != null)
    }

    @Test
    fun missingOriginIsExplainedButStillRequiresExplicitBinding() {
        val state = AiAssistantFormState(
            baseUrl = "https://api.example.com/v1",
            apiKey = "sk-old",
            textModel = "model",
        )
        val derived = AiAssistantSettingsPolicy.derive(state, AiHttpConsent())

        assertTrue(derived.credentialMessage != null)
    }

    @Test
    fun legacyKeyCannotBeBoundWhenOriginalBaseUrlWasInvalid() {
        assertFalse(
            AiAssistantSettingsPolicy.canBindLegacyKey(
                originalBaseUrlValid = false,
                keyEdited = false,
            ),
        )
        assertTrue(
            AiAssistantSettingsPolicy.canBindLegacyKey(
                originalBaseUrlValid = true,
                keyEdited = false,
            ),
        )
    }

    @Test
    fun newlyEditedKeyCanBeBoundWhenOriginalBaseUrlWasInvalid() {
        assertTrue(
            AiAssistantSettingsPolicy.canBindLegacyKey(
                originalBaseUrlValid = false,
                keyEdited = true,
            ),
        )
    }
}
