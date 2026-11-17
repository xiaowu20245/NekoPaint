package io.github.xiaowu20245.nekopaint.feature.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskExportPolicyTest {

    @Test
    fun dimensionLimitScalesLongestSideTo4096() {
        assertEquals(
            MaskTargetSize(4096, 2458),
            MaskExportPolicy.targetSize(5000, 3000),
        )
        assertEquals(
            MaskTargetSize(4096, 512),
            MaskExportPolicy.targetSize(8000, 1000),
        )
    }

    @Test
    fun sixteenMegapixelBudgetAppliesAfterDimensionLimit() {
        assertEquals(
            MaskTargetSize(4000, 4000),
            MaskExportPolicy.targetSize(4096, 4096),
        )
        assertEquals(
            MaskTargetSize(4000, 4000),
            MaskExportPolicy.targetSize(10_000, 10_000),
        )
    }

    @Test
    fun tinyAndInvalidSourcesAreClamped() {
        assertEquals(MaskTargetSize(1, 1), MaskExportPolicy.targetSize(1, 1))
        assertEquals(MaskTargetSize(1, 1), MaskExportPolicy.targetSize(0, 0))
        assertEquals(MaskTargetSize(1, 1), MaskExportPolicy.targetSize(-10, 20))
    }

    @Test
    fun targetAlwaysFitsPixelAndByteBudget() {
        val sizes = listOf(
            4096 to 4096,
            50_000 to 50_000,
            20_000 to 200,
            200 to 20_000,
            8000 to 4000,
        )
        sizes.forEach { (width, height) ->
            val target = MaskExportPolicy.targetSize(width, height)
            assertTrue(target.width >= 1 && target.height >= 1)
            assertTrue(
                target.width.toLong() * target.height.toLong() <=
                    MaskExportPolicy.MAX_PIXELS,
            )
            assertTrue(
                MaskExportPolicy.estimatedBytes(target) <=
                    MaskExportPolicy.MAX_BITMAP_BYTES,
            )
        }
    }

    @Test
    fun formatIsAlwaysOpaqueRgb565() {
        assertEquals(MaskPixelFormat.RGB_565, MaskExportPolicy.pixelFormat)
    }
}
