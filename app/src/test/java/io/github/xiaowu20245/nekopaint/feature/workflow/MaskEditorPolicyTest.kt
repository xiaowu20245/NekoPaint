package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskEditorPolicyTest {

    private fun stroke(x: Float) = MaskStroke(
        points = listOf(Offset(x, 0.5f)),
        widthFraction = 0.05f,
        eraser = false,
    )

    @Test
    fun undoMovesLastStrokeToRedoAndEmptyUndoIsNoOp() {
        val history = MaskEditorPolicy.undo(
            strokes = listOf(stroke(0.1f), stroke(0.2f)),
            redo = emptyList(),
        )
        assertEquals(1, history.strokes.size)
        assertEquals(listOf(stroke(0.2f)), history.redo)

        val empty = MaskEditorPolicy.undo(emptyList(), history.redo)
        assertEquals(emptyList<MaskStroke>(), empty.strokes)
        assertEquals(history.redo, empty.redo)
    }

    @Test
    fun redoRestoresStrokeAndNewStrokeClearsRedo() {
        val undone = MaskEditorPolicy.undo(listOf(stroke(0.1f)), emptyList())
        val redone = MaskEditorPolicy.redo(undone.strokes, undone.redo)
        assertEquals(listOf(stroke(0.1f)), redone.strokes)
        assertTrue(redone.redo.isEmpty())

        val pushed = MaskEditorPolicy.pushStroke(undone.strokes, stroke(0.3f))
        assertTrue(pushed.redo.isEmpty())
    }

    @Test
    fun redoStackIsCappedAtTwenty() {
        var strokes = (1..25).map { stroke(it / 100f) }
        var redo = emptyList<MaskStroke>()
        repeat(25) {
            val undone = MaskEditorPolicy.undo(strokes, redo)
            strokes = undone.strokes
            redo = undone.redo
        }
        assertEquals(MaskEditorPolicy.MAX_REDO, redo.size)
    }

    @Test
    fun zoomIsClampedBetweenOneAndFour() {
        assertEquals(1f, MaskEditorPolicy.clampZoom(0.2f))
        assertEquals(4f, MaskEditorPolicy.clampZoom(9f))
        assertEquals(2.5f, MaskEditorPolicy.clampZoom(2.5f))
    }

    @Test
    fun panIsLimitedToImageBoundsAndCenteredAtOneX() {
        val atOne = MaskEditorPolicy.clampPan(
            pan = Offset(200f, -200f),
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            baseWidth = 800f,
            baseHeight = 600f,
            zoom = 1f,
        )
        assertEquals(0f, atOne.x, 0.001f)
        assertEquals(0f, atOne.y, 0.001f)

        val zoomed = MaskEditorPolicy.clampPan(
            pan = Offset(1000f, 1000f),
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            baseWidth = 800f,
            baseHeight = 600f,
            zoom = 2f,
        )
        assertEquals(300f, zoomed.x)
        assertEquals(100f, zoomed.y)
    }

    @Test
    fun invertAndExitConfirmationFollowDocumentState() {
        assertTrue(MaskEditorPolicy.toggleInverted(false))
        assertFalse(MaskEditorPolicy.toggleInverted(true))
        assertFalse(MaskEditorPolicy.requiresExitConfirmation(emptyList(), false))
        assertTrue(MaskEditorPolicy.requiresExitConfirmation(listOf(stroke(0.1f)), false))
        assertTrue(MaskEditorPolicy.requiresExitConfirmation(emptyList(), true))
        assertEquals(emptyList<MaskStroke>(), MaskEditorPolicy.clear().strokes)
    }
}
