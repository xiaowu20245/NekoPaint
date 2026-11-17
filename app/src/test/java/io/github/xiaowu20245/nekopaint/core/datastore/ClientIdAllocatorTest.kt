package io.github.xiaowu20245.nekopaint.core.datastore

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClientIdAllocatorTest {

    @Test
    fun concurrentFirstUseCreatesOneId() = runTest {
        val stored = AtomicReference<String?>(null)
        val writes = AtomicInteger(0)
        val allocator = ClientIdAllocator(
            read = { stored.get() },
            write = { value ->
                writes.incrementAndGet()
                stored.set(value)
            },
            generate = { "id-${UUID.randomUUID()}" },
        )

        val ids = coroutineScope {
            (1..32).map {
                async(Dispatchers.Default) { allocator.getOrCreate() }
            }.awaitAll()
        }

        assertEquals(1, ids.toSet().size)
        assertEquals(1, writes.get())
        assertEquals(ids.first(), stored.get())
    }

    @Test
    fun existingIdIsReturnedWithoutWriting() = runTest {
        val stored = AtomicReference("existing-id")
        val writes = AtomicInteger(0)
        val allocator = ClientIdAllocator(
            read = { stored.get() },
            write = { writes.incrementAndGet() },
        )

        assertEquals("existing-id", allocator.getOrCreate())
        assertEquals(0, writes.get())
    }

    @Test
    fun blankIdIsRegenerated() = runTest {
        val stored = AtomicReference("  ")
        val allocator = ClientIdAllocator(
            read = { stored.get() },
            write = { stored.set(it) },
            generate = { "new-id" },
        )

        assertEquals("new-id", allocator.getOrCreate())
        assertNotEquals("  ", stored.get())
    }

    @Test
    fun writeFailureIsPropagated() = runTest {
        val allocator = ClientIdAllocator(
            read = { null },
            write = { error("disk full") },
            generate = { "new-id" },
        )

        val failure = runCatching { allocator.getOrCreate() }.exceptionOrNull()

        assertEquals("disk full", failure?.message)
    }
}
