package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.image.ImageDimensions

internal enum class ChatImageOutputFormat(val mimeType: String) {
    JPEG("image/jpeg"),
    PNG("image/png"),
}

/**
 * Chat 图片压缩纯策略：20MB 文件上限、1024 最长边、采样约 2× 目标。
 */
internal object ChatImageDecodePolicy {

    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val TARGET_DIMENSION = 1024

    fun isTooLarge(bytes: Long): Boolean = bytes > MAX_FILE_BYTES

    fun sampleSize(
        width: Int,
        height: Int,
        target: Int = TARGET_DIMENSION,
    ): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) > target) {
            sample *= 2
        }
        return sample
    }

    fun targetSize(
        width: Int,
        height: Int,
        target: Int = TARGET_DIMENSION,
    ): ImageDimensions {
        if (width <= 0 || height <= 0) return ImageDimensions(1, 1)
        val longest = maxOf(width, height)
        if (longest <= target) return ImageDimensions(width, height)
        val scale = target.toFloat() / longest.toFloat()
        return ImageDimensions(
            width = (width * scale).toInt().coerceAtLeast(1),
            height = (height * scale).toInt().coerceAtLeast(1),
        )
    }

    fun outputFormat(sourceHasAlpha: Boolean): ChatImageOutputFormat =
        if (sourceHasAlpha) ChatImageOutputFormat.PNG else ChatImageOutputFormat.JPEG
}
