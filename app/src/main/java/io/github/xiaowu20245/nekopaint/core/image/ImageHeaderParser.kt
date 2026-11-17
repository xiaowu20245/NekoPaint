package io.github.xiaowu20245.nekopaint.core.image

/** 从图片字节头解析出的尺寸与 alpha 信息。 */
data class ImageDimensions(
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean = false,
)

/**
 * 纯 JVM 图片头解析器：不依赖 Bitmap/Canvas，不整图解码。
 *
 * 支持 PNG IHDR/tRNS、JPEG SOF、WebP VP8/VP8L/VP8X。
 */
object ImageHeaderParser {

    fun parse(bytes: ByteArray): ImageDimensions? = when {
        isPng(bytes) -> parsePng(bytes)
        isJpeg(bytes) -> parseJpeg(bytes)
        isWebP(bytes) -> parseWebP(bytes)
        else -> null
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()

    private fun parsePng(bytes: ByteArray): ImageDimensions? {
        var offset = 8
        var width = 0
        var height = 0
        var colorType = -1
        var hasTransparencyChunk = false
        while (offset + 12 <= bytes.size) {
            val length = readIntBe(bytes, offset)
            if (length < 0 || offset + 12L + length > bytes.size) break
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            when (type) {
                "IHDR" -> {
                    if (length < 13) return null
                    width = readIntBe(bytes, offset + 8)
                    height = readIntBe(bytes, offset + 12)
                    colorType = bytes[offset + 17].toInt() and 0xFF
                }

                "tRNS" -> hasTransparencyChunk = true
            }
            offset += 12 + length
        }
        if (width <= 0 || height <= 0) return null
        val hasAlpha = colorType == 4 || colorType == 6 || hasTransparencyChunk
        return ImageDimensions(width, height, hasAlpha)
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte()

    private fun parseJpeg(bytes: ByteArray): ImageDimensions? {
        var index = 2
        while (index + 1 < bytes.size) {
            if (bytes[index] != 0xFF.toByte()) {
                index += 1
                continue
            }
            var marker = bytes[index + 1].toInt() and 0xFF
            index += 2
            while (marker == 0xFF && index < bytes.size) {
                marker = bytes[index].toInt() and 0xFF
                index += 1
            }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) continue
            if (marker == 0xD9) break
            if (index + 2 > bytes.size) break
            val length = readU16Be(bytes, index)
            if (length < 2 || index + length > bytes.size) break
            if (marker in SOF_MARKERS) {
                if (index + 7 > bytes.size) break
                val height = readU16Be(bytes, index + 3)
                val width = readU16Be(bytes, index + 5)
                if (width <= 0 || height <= 0) return null
                return ImageDimensions(width, height, hasAlpha = false)
            }
            index += length
        }
        return null
    }

    private fun isWebP(bytes: ByteArray): Boolean =
        bytes.size >= 16 &&
            ascii(bytes, 0, 4) == "RIFF" &&
            ascii(bytes, 8, 4) == "WEBP"

    private fun parseWebP(bytes: ByteArray): ImageDimensions? {
        if (bytes.size < 25) return null
        return when (val chunk = ascii(bytes, 12, 4)) {
            "VP8X" -> {
                if (bytes.size < 30) return null
                val flags = bytes[20].toInt() and 0xFF
                val width = 1 + read24Le(bytes, 24)
                val height = 1 + read24Le(bytes, 27)
                if (width <= 0 || height <= 0) null
                else ImageDimensions(width, height, hasAlpha = flags and 0x10 != 0)
            }

            "VP8L" -> {
                if (bytes[20] != 0x2F.toByte()) return null
                val b1 = bytes[21].toInt() and 0xFF
                val b2 = bytes[22].toInt() and 0xFF
                val b3 = bytes[23].toInt() and 0xFF
                val b4 = bytes[24].toInt() and 0xFF
                val bits = b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
                val width = 1 + (bits and 0x3FFF)
                val height = 1 + ((bits ushr 14) and 0x3FFF)
                val hasAlpha = ((bits ushr 28) and 1) == 1
                if (width <= 0 || height <= 0) null
                else ImageDimensions(width, height, hasAlpha)
            }

            "VP8 " -> {
                if (bytes.size < 30) return null
                if (bytes[23] != 0x9D.toByte() ||
                    bytes[24] != 0x01.toByte() ||
                    bytes[25] != 0x2A.toByte()
                ) {
                    return null
                }
                val width = readU16Le(bytes, 26) and 0x3FFF
                val height = readU16Le(bytes, 28) and 0x3FFF
                if (width <= 0 || height <= 0) null
                else ImageDimensions(width, height, hasAlpha = false)
            }

            else -> null
        }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        if (offset + length <= bytes.size) {
            String(bytes, offset, length, Charsets.US_ASCII)
        } else {
            ""
        }

    private fun readIntBe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readU16Be(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readU16Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun read24Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    }

    private val SOF_MARKERS = setOf(
        0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
        0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
    )
}
