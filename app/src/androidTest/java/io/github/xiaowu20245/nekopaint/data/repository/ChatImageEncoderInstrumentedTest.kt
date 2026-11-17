package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatImageEncoderInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val encoder = ChatImageEncoder(context)

    @Test
    fun largePhotoIsScaledTo1024AndEncodedAsJpeg() = runBlocking {
        val source = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        Canvas(source).drawColor(Color.RED)
        val file = File(context.cacheDir, "chat-photo-${System.nanoTime()}.jpg")
        try {
            file.outputStream().use { stream ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            }
        } finally {
            source.recycle()
        }

        try {
            val dataUrl = encoder.toDataUrl(Uri.fromFile(file)).getOrThrow()
            assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"))
            val decoded = decode(dataUrl)
            try {
                assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
            } finally {
                decoded.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun transparentPngStaysPng() = runBlocking {
        val source = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.TRANSPARENT)
        Canvas(source).drawCircle(
            800f,
            600f,
            300f,
            Paint().apply { color = Color.BLUE },
        )
        val file = File(context.cacheDir, "chat-alpha-${System.nanoTime()}.png")
        try {
            file.outputStream().use { stream ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            source.recycle()
        }

        try {
            val dataUrl = encoder.toDataUrl(Uri.fromFile(file)).getOrThrow()
            assertTrue(dataUrl.startsWith("data:image/png;base64,"))
            val decoded = decode(dataUrl)
            try {
                assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
            } finally {
                decoded.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun fiftyMegapixelPhotoIsSampledWithoutOom() = runBlocking {
        val source = Bitmap.createBitmap(8160, 6120, Bitmap.Config.RGB_565)
        Canvas(source).drawColor(Color.MAGENTA)
        val file = File(context.cacheDir, "chat-50mp-${System.nanoTime()}.jpg")
        try {
            file.outputStream().use { stream ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            }
        } finally {
            source.recycle()
        }

        try {
            val dataUrl = encoder.toDataUrl(Uri.fromFile(file)).getOrThrow()
            val decoded = decode(dataUrl)
            try {
                assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
            } finally {
                decoded.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun longScreenshotIsScaledWithin1024() = runBlocking {
        val source = Bitmap.createBitmap(8000, 1000, Bitmap.Config.ARGB_8888)
        Canvas(source).drawColor(Color.GREEN)
        val file = File(context.cacheDir, "chat-long-${System.nanoTime()}.jpg")
        try {
            file.outputStream().use { stream ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            }
        } finally {
            source.recycle()
        }

        try {
            val dataUrl = encoder.toDataUrl(Uri.fromFile(file)).getOrThrow()
            val decoded = decode(dataUrl)
            try {
                assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
                assertTrue(decoded.width > decoded.height * 5)
            } finally {
                decoded.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun fileOverTwentyMegabytesFails() = runBlocking {
        val file = File(context.cacheDir, "chat-large-${System.nanoTime()}.bin")
        try {
            RandomAccessFile(file, "rw").use { random ->
                random.setLength(ChatImageDecodePolicy.MAX_FILE_BYTES + 1)
            }
            val result = encoder.toDataUrl(Uri.fromFile(file))
            assertTrue(result.isFailure)
        } finally {
            file.delete()
        }
    }

    private fun decode(dataUrl: String): Bitmap {
        val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("无法解码编码结果")
    }
}
