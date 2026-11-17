package io.github.xiaowu20245.nekopaint.core.image

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageHeaderParserTest {

    @Test
    fun parsesPngDimensionsAndAlpha() {
        val opaque = ImageHeaderParser.parse(png(640, 480, colorType = 2))
        assertEquals(ImageDimensions(640, 480, hasAlpha = false), opaque)

        val rgba = ImageHeaderParser.parse(png(800, 600, colorType = 6))
        assertEquals(ImageDimensions(800, 600, hasAlpha = true), rgba)

        val withTrnsChunk = ImageHeaderParser.parse(
            png(10, 20, colorType = 2, transparencyChunk = true),
        )
        assertTrue(withTrnsChunk?.hasAlpha == true)
    }

    @Test
    fun parsesJpegSofDimensions() {
        val dimensions = ImageHeaderParser.parse(jpeg(32, 16))

        assertEquals(ImageDimensions(32, 16, hasAlpha = false), dimensions)
    }

    @Test
    fun parsesWebpVp8xAlphaAndDimensions() {
        val dimensions = ImageHeaderParser.parse(webpVp8x(1024, 768, alpha = true))

        assertEquals(ImageDimensions(1024, 768, hasAlpha = true), dimensions)
        assertFalse(
            ImageHeaderParser.parse(webpVp8x(100, 50, alpha = false))?.hasAlpha == true,
        )
    }

    @Test
    fun parsesWebpVp8lDimensionsAndAlpha() {
        val dimensions = ImageHeaderParser.parse(webpVp8l(100, 50, alpha = true))

        assertEquals(ImageDimensions(100, 50, hasAlpha = true), dimensions)
    }

    @Test
    fun unsupportedOrTruncatedDataReturnsNull() {
        assertNull(ImageHeaderParser.parse(byteArrayOf(1, 2, 3)))
        assertNull(ImageHeaderParser.parse(ByteArray(0)))
        assertNull(ImageHeaderParser.parse(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun truncatedWebpHeadersReturnNullInsteadOfThrowing() {
        assertNull(ImageHeaderParser.parse(webpVp8x(1024, 768, true).copyOf(26)))
        assertNull(ImageHeaderParser.parse(webpVp8x(1024, 768, true).copyOf(29)))
        assertNull(ImageHeaderParser.parse(webpVp8l(100, 50, true).copyOf(24)))
        assertNull(ImageHeaderParser.parse(webpVp8(640, 480).copyOf(28)))
    }

    private fun png(
        width: Int,
        height: Int,
        colorType: Int,
        transparencyChunk: Boolean = false,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArray(13)
        writeIntBe(ihdr, 0, width)
        writeIntBe(ihdr, 4, height)
        ihdr[8] = 8
        ihdr[9] = colorType.toByte()
        writeChunk(output, "IHDR", ihdr)
        if (transparencyChunk) writeChunk(output, "tRNS", byteArrayOf(0))
        return output.toByteArray()
    }

    private fun writeChunk(output: ByteArrayOutputStream, type: String, data: ByteArray) {
        output.write(byteArrayOf(
            (data.size ushr 24).toByte(),
            (data.size ushr 16).toByte(),
            (data.size ushr 8).toByte(),
            data.size.toByte(),
        ))
        output.write(type.toByteArray(Charsets.US_ASCII))
        output.write(data)
        output.write(ByteArray(4))
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        output.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00))
        output.write(byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            (height ushr 8).toByte(), height.toByte(),
            (width ushr 8).toByte(), width.toByte(),
            0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
        ))
        output.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return output.toByteArray()
    }

    private fun webpVp8x(width: Int, height: Int, alpha: Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.write(ByteArray(4))
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write("VP8X".toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(0x0A, 0x00, 0x00, 0x00))
        output.write((if (alpha) 0x10 else 0x00))
        output.write(ByteArray(3))
        write24Le(output, width - 1)
        write24Le(output, height - 1)
        return output.toByteArray()
    }

    private fun webpVp8(width: Int, height: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.write(ByteArray(4))
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write("VP8 ".toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(0x0A, 0x00, 0x00, 0x00))
        output.write(byteArrayOf(0x00, 0x00, 0x00, 0x9D.toByte(), 0x01, 0x2A))
        output.write(byteArrayOf(
            width.toByte(),
            (width ushr 8).toByte(),
            height.toByte(),
            (height ushr 8).toByte(),
        ))
        return output.toByteArray()
    }

    private fun webpVp8l(width: Int, height: Int, alpha: Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.write(ByteArray(4))
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write("VP8L".toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x00))
        output.write(0x2F)
        val bits = (width - 1) or
            ((height - 1) shl 14) or
            ((if (alpha) 1 else 0) shl 28)
        output.write(byteArrayOf(
            bits.toByte(),
            (bits ushr 8).toByte(),
            (bits ushr 16).toByte(),
            (bits ushr 24).toByte(),
        ))
        return output.toByteArray()
    }

    private fun write24Le(output: ByteArrayOutputStream, value: Int) {
        output.write(byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
        ))
    }

    private fun writeIntBe(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
