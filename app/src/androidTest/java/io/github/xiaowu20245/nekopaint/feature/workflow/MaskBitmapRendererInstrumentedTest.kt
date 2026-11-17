package io.github.xiaowu20245.nekopaint.feature.workflow

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaskBitmapRendererInstrumentedTest {

    @Test
    fun rendersBinaryRgb565MaskWithinBudget() {
        val target = MaskExportPolicy.targetSize(4096, 4096)
        assertEquals(MaskTargetSize(4000, 4000), target)
        val stroke = MaskStroke(
            points = listOf(Offset(0.5f, 0.1f), Offset(0.5f, 0.9f)),
            widthFraction = 0.1f,
            eraser = false,
        )

        val bitmap = MaskBitmapRenderer.render(target, listOf(stroke))
        try {
            assertEquals(Bitmap.Config.RGB_565, bitmap.config)
            assertFalse(bitmap.hasAlpha())
            assertEquals(Color.BLACK, bitmap.getPixel(0, 0))
            assertEquals(Color.WHITE, bitmap.getPixel(2000, 2000))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun eraserKeepsMaskBlack() {
        val white = MaskStroke(
            points = listOf(Offset(0.5f, 0f), Offset(0.5f, 1f)),
            widthFraction = 0.2f,
            eraser = false,
        )
        val erase = white.copy(eraser = true)
        val bitmap = MaskBitmapRenderer.render(
            MaskTargetSize(200, 200),
            listOf(white, erase),
        )
        try {
            assertEquals(Color.BLACK, bitmap.getPixel(100, 100))
        } finally {
            bitmap.recycle()
        }
    }
}
