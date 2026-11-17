package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.data.repository.PromptSubmissionPolicy.HttpClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSubmissionPolicyTest {

    @Test
    fun httpCodesAreClassifiedByWhetherTheServerMayHaveAcceptedThePrompt() {
        assertEquals(HttpClassification.ACCEPTED, PromptSubmissionPolicy.classify(200))
        assertEquals(HttpClassification.ACCEPTED, PromptSubmissionPolicy.classify(201))
        assertEquals(HttpClassification.REJECTED, PromptSubmissionPolicy.classify(400))
        assertEquals(HttpClassification.REJECTED, PromptSubmissionPolicy.classify(401))
        assertEquals(HttpClassification.REJECTED, PromptSubmissionPolicy.classify(403))
        assertEquals(HttpClassification.REJECTED, PromptSubmissionPolicy.classify(413))
        assertEquals(HttpClassification.REJECTED, PromptSubmissionPolicy.classify(422))
        assertEquals(HttpClassification.UNKNOWN_RETRYABLE, PromptSubmissionPolicy.classify(429))
        assertEquals(HttpClassification.UNKNOWN_RETRYABLE, PromptSubmissionPolicy.classify(500))
        assertEquals(HttpClassification.UNKNOWN_RETRYABLE, PromptSubmissionPolicy.classify(503))
    }

    @Test
    fun retryableResponsesRetryOnceWithTheSamePromptIdBudget() {
        assertTrue(PromptSubmissionPolicy.shouldRetry(429, attempt = 1))
        assertTrue(PromptSubmissionPolicy.shouldRetry(503, attempt = 1))
        assertFalse(PromptSubmissionPolicy.shouldRetry(429, attempt = 2))
        assertFalse(PromptSubmissionPolicy.shouldRetry(500, attempt = 2))
        assertFalse(PromptSubmissionPolicy.shouldRetry(400, attempt = 1))
        assertFalse(PromptSubmissionPolicy.shouldRetry(200, attempt = 1))
    }
}
