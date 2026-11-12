package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 把用户选择的图片压缩成 OpenAI 兼容的多模态 data URL。
 *
 * 只读取一次流并限制最长边，避免大图直接进入内存和请求体。
 */
class ChatImageEncoder(context: Context) {

    private val appContext = context.applicationContext

    suspend fun toDataUrl(
        uri: Uri,
        maxDimension: Int = 1024,
        quality: Int = 85,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = appContext.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() }
                ?: error("无法读取图片")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("无法解析图片")
            val scaled = scaleDown(bitmap, maxDimension)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            "data:image/jpeg;base64," +
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        return bitmap.scale(
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
        )
    }
}
