package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.image.ImageDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageDecodePolicyTest {

    @Test
    fun twentyMegabyteLimitIsInclusive() {
        assertEquals(ChatImageDecodePolicy.MAX_FILE_BYTES, 20L * 1024 * 1024)
        assertFalse(ChatImageDecodePolicy.isTooLarge(20L * 1024 * 1024))
        assertTrue(ChatImageDecodePolicy.isTooLarge(20L * 1024 * 1024 + 1))
    }

    @Test
    fun sampleSizeKeepsDecodedImageAroundTwiceTheTarget() {
        assertEquals(4, ChatImageDecodePolicy.sampleSize(8160, 6120))
        assertEquals(2, ChatImageDecodePolicy.sampleSize(4096, 4096))
        assertEquals(1, ChatImageDecodePolicy.sampleSize(1024, 1024))
        assertEquals(1, ChatImageDecodePolicy.sampleSize(0, 1024))
    }

    @Test
    fun targetSizeFitsLongestSideTo1024() {
        assertEquals(
            ImageDimensions(1024, 768),
            ChatImageDecodePolicy.targetSize(8160, 6120),
        )
        assertEquals(
            ImageDimensions(1024, 51),
            ChatImageDecodePolicy.targetSize(5000, 250),
        )
        assertEquals(
            ImageDimensions(800, 600),
            ChatImageDecodePolicy.targetSize(800, 600),
        )
    }

    @Test
    fun alphaSourceKeepsPngAndOpaquePhotoUsesJpeg() {
        assertEquals(
            ChatImageOutputFormat.PNG,
            ChatImageDecodePolicy.outputFormat(sourceHasAlpha = true),
        )
        assertEquals(
            ChatImageOutputFormat.JPEG,
            ChatImageDecodePolicy.outputFormat(sourceHasAlpha = false),
        )
    }
}
