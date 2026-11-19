package io.github.xiaowu20245.nekopaint.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySelectionPolicyTest {

    @Test
    fun pruneAndToggleOnlyWithinSelectable() {
        val selectable = setOf("a", "b", "c")
        val pruned = HistorySelectionPolicy.prune(setOf("a", "hidden"), selectable)

        assertEquals(setOf("a"), pruned)
        assertEquals(
            setOf("a", "b"),
            HistorySelectionPolicy.toggle(pruned, "b", selectable),
        )
        assertEquals(
            setOf("a"),
            HistorySelectionPolicy.toggle(setOf("a", "b"), "b", selectable),
        )
        assertEquals(
            setOf("a"),
            HistorySelectionPolicy.toggle(setOf("a"), "hidden", selectable),
        )
    }

    @Test
    fun selectAllTogglesOnlyVisible() {
        val selectable = setOf("a", "b")
        val selected = HistorySelectionPolicy.toggleAll(emptySet(), selectable)

        assertEquals(setOf("a", "b"), selected)
        assertTrue(HistorySelectionPolicy.allSelected(selected, selectable))
        assertEquals(
            emptySet<String>(),
            HistorySelectionPolicy.toggleAll(selected, selectable),
        )
        assertFalse(HistorySelectionPolicy.allSelected(emptySet(), selectable))
        assertFalse(HistorySelectionPolicy.allSelected(emptySet(), emptySet()))
    }
}
