package io.github.xiaowu20245.nekopaint.core.privacy

/**
 * Supplies server host names that must be redacted before text is sent to a
 * third-party AI service.
 *
 * Batch 2 only supplies currently configured servers. The archive-aware
 * implementation is extended in the server-isolation batch.
 */
fun interface SensitiveHostProvider {
    fun hosts(): Set<String>

    /**
     * Waits until the provider has loaded its first host snapshot.
     *
     * Returns false when the snapshot cannot be loaded (failure or timeout),
     * so callers can fail closed instead of sending unsanitized text.
     * Static providers always return true.
     */
    suspend fun awaitReady(): Boolean = true
}
