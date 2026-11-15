package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEndpointPolicyTest {

    private fun endpoint(raw: String) = AiEndpointValidator.parse(raw).getOrThrow()

    @Test
    fun httpsIsAlwaysAllowed() {
        val result = AiEndpointPolicy.evaluate(
            endpoint("https://api.example.com/v1"),
            hasApiKey = true,
            consent = AiHttpConsent(),
        )

        assertTrue(result.allowed)
    }

    @Test
    fun publicHttpIsBlockedWithOrWithoutKeyOrTrust() {
        val public = endpoint("http://8.8.8.8/v1")
        val trusted = AiHttpConsent(
            trustedOrigins = setOf(public.origin),
            keyApprovedOrigins = setOf(public.origin),
        )

        assertFalse(
            AiEndpointPolicy.evaluate(public, hasApiKey = false, consent = AiHttpConsent()).allowed,
        )
        assertFalse(
            AiEndpointPolicy.evaluate(public, hasApiKey = true, consent = trusted).allowed,
        )
    }

    @Test
    fun privateHttpWithoutKeyWarnsButAllows() {
        val result = AiEndpointPolicy.evaluate(
            endpoint("http://192.168.1.10:8188/v1"),
            hasApiKey = false,
            consent = AiHttpConsent(),
        )

        assertTrue(result.allowed)
        assertTrue(result.warning != null)
    }

    @Test
    fun privateHttpWithKeyNeedsExplicitApproval() {
        val private = endpoint("http://192.168.1.10:8188/v1")
        val withoutApproval = AiEndpointPolicy.evaluate(
            private,
            hasApiKey = true,
            consent = AiHttpConsent(),
        )
        val withApproval = AiEndpointPolicy.evaluate(
            private,
            hasApiKey = true,
            consent = AiHttpConsent(keyApprovedOrigins = setOf(private.origin)),
        )

        assertFalse(withoutApproval.allowed)
        assertTrue(withoutApproval.requiresKeyApproval)
        assertTrue(withApproval.allowed)
    }

    @Test
    fun localDomainNeedsTrustAndThenKeyApproval() {
        val local = endpoint("http://my-ai.local/v1")
        val untrusted = AiEndpointPolicy.evaluate(
            local,
            hasApiKey = false,
            consent = AiHttpConsent(),
        )
        val trustedWithoutKey = AiEndpointPolicy.evaluate(
            local,
            hasApiKey = false,
            consent = AiHttpConsent(trustedOrigins = setOf(local.origin)),
        )
        val trustedWithKey = AiEndpointPolicy.evaluate(
            local,
            hasApiKey = true,
            consent = AiHttpConsent(trustedOrigins = setOf(local.origin)),
        )
        val fullyApproved = AiEndpointPolicy.evaluate(
            local,
            hasApiKey = true,
            consent = AiHttpConsent(
                trustedOrigins = setOf(local.origin),
                keyApprovedOrigins = setOf(local.origin),
            ),
        )

        assertTrue(untrusted.requiresLocalTrust)
        assertTrue(trustedWithoutKey.allowed)
        assertTrue(trustedWithKey.requiresKeyApproval)
        assertTrue(fullyApproved.allowed)
    }

    @Test
    fun trustForAnotherOriginDoesNotApply() {
        val local = endpoint("http://my-ai.local/v1")
        val result = AiEndpointPolicy.evaluate(
            local,
            hasApiKey = false,
            consent = AiHttpConsent(
                trustedOrigins = setOf("http://other.local:80"),
            ),
        )

        assertTrue(result.requiresLocalTrust)
    }
}
