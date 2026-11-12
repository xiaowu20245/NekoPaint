package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingWorkflowRefillStoreTest {

    @Test
    fun `refill values are consumed only once`() {
        val store = PendingWorkflowRefillStore()
        store.put("template-a", mapOf("1:text" to "fox ears"))

        assertEquals(
            mapOf("1:text" to "fox ears"),
            store.consume("template-a"),
        )
        assertNull(store.consume("template-a"))
    }

    @Test
    fun `empty values are ignored`() {
        val store = PendingWorkflowRefillStore()

        store.put("template-a", emptyMap())

        assertNull(store.consume("template-a"))
    }
}
