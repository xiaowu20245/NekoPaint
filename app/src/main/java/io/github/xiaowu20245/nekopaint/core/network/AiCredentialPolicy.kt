package io.github.xiaowu20245.nekopaint.core.network

sealed interface AiCredentialState {
    data object NoKey : AiCredentialState
    data object Bound : AiCredentialState
    data object MissingOrigin : AiCredentialState
    data class OriginChanged(val boundOrigin: String) : AiCredentialState
    data object InvalidEndpoint : AiCredentialState
}

object AiCredentialPolicy {

    /**
     * Determines whether the saved API key may be used for [endpoint].
     *
     * A legacy config without [apiKeyOrigin] is deliberately fail-closed:
     * the key is never silently rebound to the current address.
     */
    fun evaluate(
        apiKey: String,
        apiKeyOrigin: String?,
        endpoint: AiEndpoint?,
    ): AiCredentialState {
        if (apiKey.isBlank()) return AiCredentialState.NoKey
        if (endpoint == null) return AiCredentialState.InvalidEndpoint
        val boundOrigin = apiKeyOrigin?.trim().orEmpty()
        if (boundOrigin.isEmpty()) return AiCredentialState.MissingOrigin
        return if (boundOrigin == endpoint.origin) {
            AiCredentialState.Bound
        } else {
            AiCredentialState.OriginChanged(boundOrigin)
        }
    }
}
