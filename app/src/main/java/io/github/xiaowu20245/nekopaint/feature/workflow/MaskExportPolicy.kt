package io.github.xiaowu20245.nekopaint.feature.workflow

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class MaskTargetSize(
    val width: Int,
    val height: Int,
)

internal enum class MaskPixelFormat {
    RGB_565,
}

/**
 * 蒙版导出尺寸/格式策略（纯 JVM）。
 *
 * 优先级：最长边 4096 -> 16MP 像素预算 -> 单张 32MB（RGB_565）。
 */
internal object MaskExportPolicy {

    const val MAX_DIMENSION = 4096
    const val MAX_PIXELS = 16_000_000
    const val MAX_BITMAP_BYTES = 32 * 1024 * 1024
    const val BYTES_PER_PIXEL = 2

    val pixelFormat: MaskPixelFormat = MaskPixelFormat.RGB_565

    fun targetSize(sourceWidth: Int, sourceHeight: Int): MaskTargetSize {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return MaskTargetSize(1, 1)
        }
        val longest = maxOf(sourceWidth, sourceHeight)
        val dimensionScale = min(1.0f, MAX_DIMENSION.toFloat() / longest.toFloat())
        var width = (sourceWidth * dimensionScale).roundToInt().coerceAtLeast(1)
        var height = (sourceHeight * dimensionScale).roundToInt().coerceAtLeast(1)

        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_PIXELS) {
            val pixelScale = sqrt(MAX_PIXELS.toDouble() / pixels.toDouble()).toFloat()
            width = (width * pixelScale).roundToInt().coerceAtLeast(1)
            height = (height * pixelScale).roundToInt().coerceAtLeast(1)
        }
        // 二次收敛：极端宽高比在两次取整后仍可能略超预算。
        while (width.toLong() * height.toLong() > MAX_PIXELS) {
            if (width >= height && width > 1) {
                width -= 1
            } else if (height > 1) {
                height -= 1
            } else {
                break
            }
        }
        return MaskTargetSize(width, height)
    }

    fun estimatedBytes(size: MaskTargetSize): Long =
        size.width.toLong() * size.height.toLong() * BYTES_PER_PIXEL

    fun scale(sourceWidth: Int, sourceHeight: Int): Float {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1f
        val target = targetSize(sourceWidth, sourceHeight)
        return target.width.toFloat() / sourceWidth.toFloat()
    }
}
