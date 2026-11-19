package io.github.xiaowu20245.nekopaint.feature.settings

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.network.AiCredentialPolicy
import io.github.xiaowu20245.nekopaint.core.network.AiCredentialState
import io.github.xiaowu20245.nekopaint.core.network.AiEndpoint
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointPolicy
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointPolicyResult
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointValidator

data class AiSettingsDerivedState(
    val baseUrlError: NekoMessage?,
    val credentialMessage: String?,
    val policyMessage: String?,
    val policyBlocked: Boolean,
)

/**
 * Pure settings-page decisions, kept free of Android dependencies so the
 * fail-closed credential and HTTP flows can be tested without ViewModel.
 */
object AiAssistantSettingsPolicy {

    fun derive(state: AiAssistantFormState, consent: AiHttpConsent): AiSettingsDerivedState {
        val endpoint = AiEndpointValidator.parse(state.baseUrl).getOrNull()
        val baseUrlError = if (state.baseUrl.isBlank() || endpoint != null) {
            null
        } else {
            NekoMessage(NekoMessageKey.SETTINGS_AI_BASE_URL_INVALID)
        }
        val credentialMessage = when (val credential = AiCredentialPolicy.evaluate(
            apiKey = state.apiKey,
            apiKeyOrigin = state.apiKeyOrigin,
            endpoint = endpoint,
        )) {
            AiCredentialState.MissingOrigin -> "此 API Key 尚未绑定当前地址，保存或测试前需确认。"
            is AiCredentialState.OriginChanged ->
                "API Key 属于 ${credential.boundOrigin}，当前地址已改变。"
            AiCredentialState.NoKey,
            AiCredentialState.Bound,
            AiCredentialState.InvalidEndpoint,
            -> null
        }
        if (endpoint == null) {
            return AiSettingsDerivedState(
                baseUrlError = baseUrlError,
                credentialMessage = credentialMessage,
                policyMessage = null,
                policyBlocked = false,
            )
        }
        return when (
            val policy = AiEndpointPolicy.evaluate(
                endpoint = endpoint,
                hasApiKey = state.apiKey.isNotBlank(),
                consent = consent,
            )
        ) {
            is AiEndpointPolicyResult.Allowed -> AiSettingsDerivedState(
                baseUrlError = baseUrlError,
                credentialMessage = credentialMessage,
                policyMessage = policy.warning,
                policyBlocked = false,
            )

            is AiEndpointPolicyResult.Blocked -> AiSettingsDerivedState(
                baseUrlError = baseUrlError,
                credentialMessage = credentialMessage,
                policyMessage = policy.message,
                policyBlocked = true,
            )

            is AiEndpointPolicyResult.NeedsKeyApproval -> AiSettingsDerivedState(
                baseUrlError = baseUrlError,
                credentialMessage = credentialMessage,
                policyMessage = policy.message,
                policyBlocked = false,
            )

            is AiEndpointPolicyResult.NeedsLocalTrust -> AiSettingsDerivedState(
                baseUrlError = baseUrlError,
                credentialMessage = credentialMessage,
                policyMessage = policy.message,
                policyBlocked = false,
            )
        }
    }

    /**
     * A legacy key without an origin must not inherit an old origin when the
     * URL is temporarily invalid. A newly entered key belongs to the current
     * valid endpoint only; otherwise it stays unbound.
     */
    fun apiKeyOriginForNewKey(value: String, endpoint: AiEndpoint?): String? =
        if (value.isBlank()) null else endpoint?.origin

    fun presetRequiresKeyClear(
        currentEndpoint: AiEndpoint?,
        presetEndpoint: AiEndpoint?,
        apiKey: String,
        apiKeyOrigin: String?,
    ): Boolean {
        if (apiKey.isBlank() || presetEndpoint == null) return false
        val boundOrigin = apiKeyOrigin?.trim().orEmpty()
        if (boundOrigin.isNotEmpty()) {
            return boundOrigin != presetEndpoint.origin
        }
        // Missing origin: only preserve the key when the current and target
        // origins are known to be the same. Otherwise the key has no safe
        // destination and must be resolved by the user.
        return currentEndpoint == null || currentEndpoint.origin != presetEndpoint.origin
    }

    fun canBindLegacyKey(
        originalBaseUrlValid: Boolean,
        keyEdited: Boolean,
    ): Boolean = originalBaseUrlValid || keyEdited
}
