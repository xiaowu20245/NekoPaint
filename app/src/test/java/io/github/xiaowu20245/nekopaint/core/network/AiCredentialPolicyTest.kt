package io.github.xiaowu20245.nekopaint.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AiCredentialPolicyTest {

    private val endpoint = AiEndpointValidator.parse("https://api.example.com/v1").getOrThrow()

    @Test
    fun emptyKeyNeedsNoAction() {
        assertEquals(
            AiCredentialState.NoKey,
            AiCredentialPolicy.evaluate(
                apiKey = "",
                apiKeyOrigin = null,
                endpoint = endpoint,
            ),
        )
    }

    @Test
    fun matchingOriginIsBound() {
        assertEquals(
            AiCredentialState.Bound,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = endpoint.origin,
                endpoint = endpoint,
            ),
        )
    }

    @Test
    fun missingOriginIsFailClosed() {
        assertEquals(
            AiCredentialState.MissingOrigin,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = null,
                endpoint = endpoint,
            ),
        )
        assertEquals(
            AiCredentialState.MissingOrigin,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = " ",
                endpoint = endpoint,
            ),
        )
    }

    @Test
    fun changedOriginIsReportedRatherThanRebound() {
        assertEquals(
            AiCredentialState.OriginChanged("https://old.example.com:443"),
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = "https://old.example.com:443",
                endpoint = endpoint,
            ),
        )
    }

    @Test
    fun invalidEndpointIsReported() {
        assertEquals(
            AiCredentialState.InvalidEndpoint,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = endpoint.origin,
                endpoint = null,
            ),
        )
    }

    @Test
    fun pathChangesDoNotChangeOrigin() {
        val withPath = AiEndpointValidator.parse("https://api.example.com/v2").getOrThrow()

        assertEquals(
            AiCredentialState.Bound,
            AiCredentialPolicy.evaluate(
                apiKey = "sk-test",
                apiKeyOrigin = endpoint.origin,
                endpoint = withPath,
            ),
        )
    }
}
