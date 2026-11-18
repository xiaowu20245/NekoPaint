package io.github.xiaowu20245.nekopaint.core.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BackDispatchTest {

    @Test
    fun consumedBackDoesNotPop() {
        var pops = 0

        dispatchScreenBack(handler = { true }) { pops++ }

        assertEquals(0, pops)
    }

    @Test
    fun unhandledOrMissingHandlerPops() {
        var pops = 0

        dispatchScreenBack(handler = { false }) { pops++ }
        dispatchScreenBack(handler = null) { pops++ }

        assertEquals(2, pops)
    }
}
