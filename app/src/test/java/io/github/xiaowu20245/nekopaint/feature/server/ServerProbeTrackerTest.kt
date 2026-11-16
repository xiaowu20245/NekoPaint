package io.github.xiaowu20245.nekopaint.feature.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProbeTrackerTest {

    @Test
    fun newEndpointInvalidatesOldGeneration() {
        val tracker = ServerProbeTracker()
        val oldGeneration = tracker.start("one", "http://old:8188/")
        val newGeneration = tracker.start("one", "http://new:8188/")

        assertTrue(oldGeneration != null)
        assertTrue(newGeneration != null)
        assertFalse(tracker.isCurrent("one", oldGeneration!!))
        assertTrue(tracker.isCurrent("one", newGeneration!!))
    }

    @Test
    fun duplicateSameEndpointDoesNotStartOrInvalidate() {
        val tracker = ServerProbeTracker()
        val generation = tracker.start("one", "http://same:8188/")

        assertNull(tracker.start("one", "http://same:8188/"))
        assertTrue(tracker.isCurrent("one", generation!!))
    }

    @Test
    fun invalidateDiscardsInFlightProbe() {
        val tracker = ServerProbeTracker()
        val generation = tracker.start("one", "http://old:8188/")
        tracker.invalidate("one")

        assertFalse(tracker.isCurrent("one", generation!!))
    }

    @Test
    fun finishAllowsSameEndpointToProbeAgain() {
        val tracker = ServerProbeTracker()
        val first = tracker.start("one", "http://same:8188/")
        tracker.finish("one", "http://same:8188/")
        val second = tracker.start("one", "http://same:8188/")

        assertTrue(first != null)
        assertTrue(second != null)
        assertTrue(tracker.isCurrent("one", second!!))
    }
}
