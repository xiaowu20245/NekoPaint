package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFileNamesTest {

    @Test
    fun keepsSafeNamesUnchanged() {
        val uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        assertEquals(uuid, SafeFileNames.encode(uuid))
        assertEquals("default", SafeFileNames.encode("default"))
        assertEquals("abc_123-xyz", SafeFileNames.encode("abc_123-xyz"))
    }

    @Test
    fun hashesTraversalSeparatorsAndInvalidCharacters() {
        listOf(
            "../evil",
            "..\\evil",
            "a/b",
            "a\\b",
            "a:b",
            "a b",
            "..",
            ".",
            "",
            "line\nbreak",
            "x".repeat(65),
            "中文",
        ).forEach { value ->
            val encoded = SafeFileNames.encode(value)
            assertTrue("$value -> $encoded", encoded.startsWith("sha256_"))
            assertEquals(71, encoded.length)
        }
    }

    @Test
    fun hashIsStableAndCollisionResistantForDifferentInputs() {
        val first = SafeFileNames.encode("../evil")
        assertEquals(first, SafeFileNames.encode("../evil"))
        assertNotEquals(first, SafeFileNames.encode("..\\evil"))
        assertNotEquals(first, SafeFileNames.encode("../other"))
    }

    @Test
    fun alreadyHashedNameIsNotHashedTwice() {
        val hashed = SafeFileNames.encode("../evil")
        assertEquals(hashed, SafeFileNames.encode(hashed))
    }
}
