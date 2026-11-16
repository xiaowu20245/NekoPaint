package io.github.xiaowu20245.nekopaint.core.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicFileWriterTest {

    @Test
    fun writesTextAndReplacesExistingFile() {
        val dir = createTempDir()
        try {
            val target = File(dir, "data.json")
            target.writeText("old")
            AtomicFileWriter.writeText(target, "new")
            assertEquals("new", target.readText())
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun createsParentDirectories() {
        val dir = createTempDir()
        try {
            val target = File(dir, "nested/data.json")
            AtomicFileWriter.writeText(target, "value")
            assertEquals("value", target.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failedReplaceLeavesTargetAndCleansTempFiles() {
        val dir = createTempDir()
        try {
            val target = File(dir, "data.json")
            target.mkdirs()
            runCatching { AtomicFileWriter.writeText(target, "new") }
            assertTrue(target.isDirectory)
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun moveFailureKeepsExistingContentAndCleansTempFiles() {
        val dir = createTempDir()
        try {
            val target = File(dir, "data.json")
            target.writeText("old")
            val payload = "new".repeat(1024)
            assertThrows(java.io.IOException::class.java) {
                AtomicFileWriter.writeText(target, payload) { _, _ ->
                    throw java.io.IOException("simulated move failure")
                }
            }
            assertEquals("old", target.readText())
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun concurrentWritesLeaveOneCompletePayload() {
        val dir = createTempDir()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val target = File(dir, "data.json")
            target.writeText("initial")
            val payloads = (1..24).map { index -> "payload-$index-".repeat(4096) }
            val futures = payloads.map { payload ->
                executor.submit { AtomicFileWriter.writeText(target, payload) }
            }
            futures.forEach { it.get() }

            val finalText = target.readText()
            assertTrue(payloads.contains(finalText))
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            executor.shutdownNow()
            dir.deleteRecursively()
        }
    }

    private fun createTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "nekopaint-atomic-${System.nanoTime()}")
            .apply { mkdirs() }
}
