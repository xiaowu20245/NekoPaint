package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionOperationGateTest {

    @Test
    fun onlyOneOperationCanHoldLease() {
        val gate = ChatSessionOperationGate()
        val send = gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s1")
        assertNotNull(send)
        assertNull(gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s1"))
        assertNull(gate.tryAcquire(ChatSessionOperationGate.Kind.ACTION, "s1"))
    }

    @Test
    fun staleLeaseCannotReleaseNewOperation() {
        val gate = ChatSessionOperationGate()
        val first = gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s1")!!
        gate.release(first)
        val second = gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s1")!!

        gate.release(first)

        assertTrue(gate.isCurrent(second))
        assertEquals(ChatSessionOperationGate.Kind.SWITCH, gate.activeKind())
    }

    @Test
    fun releaseRestoresIdle() {
        val gate = ChatSessionOperationGate()
        val lease = gate.tryAcquire(ChatSessionOperationGate.Kind.ACTION, "s1")!!

        gate.release(lease)

        assertNull(gate.activeKind())
        assertFalse(gate.isCurrent(lease))
    }

    @Test
    fun newOperationCanAcquireAfterRelease() {
        val gate = ChatSessionOperationGate()
        val first = gate.tryAcquire(ChatSessionOperationGate.Kind.DELETE, "s1")!!
        gate.release(first)

        val second = gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s2")

        assertNotNull(second)
        assertEquals(ChatSessionOperationGate.Kind.SWITCH, gate.activeKind())
    }

    @Test
    fun differentSessionsCanHoldSendLeases() {
        val gate = ChatSessionOperationGate()

        val first = gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s1")
        val second = gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s2")

        assertNotNull(first)
        assertNotNull(second)
        assertNull(gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s1"))
    }

    @Test
    fun mutationIsBlockedOnlyByInvolvedSessions() {
        val gate = ChatSessionOperationGate()
        val send = gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s1")!!

        assertNull(gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s1"))
        val unrelated = gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s2")
        assertNotNull(unrelated)
        // 不同会话的 mutation 可以并行；只有涉及 s2/s3 的冲突才会被拒绝。
        val another = gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s3")
        assertNotNull(another)

        gate.release(another!!)
        gate.release(unrelated!!)
        gate.release(send)
        assertNull(gate.activeKind())
    }

    @Test
    fun mutationOnOneSessionDoesNotBlockSendOnAnother() {
        val gate = ChatSessionOperationGate()
        val mutation = gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s1")!!

        val otherSend = gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s2")
        assertNotNull(otherSend)
        assertNull(gate.tryAcquire(ChatSessionOperationGate.Kind.SEND, "s1"))

        gate.release(otherSend!!)
        gate.release(mutation)
        assertNull(gate.activeKind())
    }

    @Test
    fun disjointMutationsCanRunConcurrently() {
        val gate = ChatSessionOperationGate()

        val first = gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s1")
        val second = gate.tryAcquire(ChatSessionOperationGate.Kind.DELETE, "s2")

        assertNotNull(first)
        assertNotNull(second)
        assertNull(gate.tryAcquire(ChatSessionOperationGate.Kind.SWITCH, "s1"))

        gate.release(first!!)
        gate.release(second!!)
        assertNull(gate.activeKind())
    }
}
