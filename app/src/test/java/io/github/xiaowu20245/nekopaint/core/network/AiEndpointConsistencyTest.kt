package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEndpointConsistencyTest {

    @Test
    fun credentialAndTransportPoliciesUseTheSameOrigin() {
        val endpoint = AiEndpointValidator.parse("http://my-ai.local/v1").getOrThrow()

        assertEquals(
            AiCredentialState.MissingOrigin,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = null,
                endpoint = endpoint,
            ),
        )
        assertEquals(
            AiCredentialState.Bound,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = endpoint.origin,
                endpoint = endpoint,
            ),
        )

        val policy = AiEndpointPolicy.evaluate(
            endpoint = endpoint,
            hasApiKey = true,
            consent = AiHttpConsent(
                trustedOrigins = setOf(endpoint.origin),
                keyApprovedOrigins = setOf(endpoint.origin),
            ),
        )
        assertTrue(policy.allowed)
    }
}
