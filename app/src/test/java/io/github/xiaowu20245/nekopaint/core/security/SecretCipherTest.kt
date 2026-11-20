package io.github.xiaowu20245.nekopaint.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretCipherTest {

    @Test
    fun blankCipherIsUnconfiguredAndFailedDecryptMapsToInvalidKey() {
        assertFalse(SecretCipherPolicy.isInvalid("", null))
        assertFalse(SecretCipherPolicy.isInvalid("", "sk-test"))
        assertFalse(SecretCipherPolicy.isInvalid("encrypted", "sk-test"))
        assertTrue(SecretCipherPolicy.isInvalid("encrypted", null))
    }
}
