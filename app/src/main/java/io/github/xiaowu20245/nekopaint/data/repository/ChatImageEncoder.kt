package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import io.github.xiaowu20245.nekopaint.core.image.ImageHeaderParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 把用户选择的图片压缩成 OpenAI 兼容的多模态 data URL。
 *
 * 文件先 bounded 复制到 cache（不 readBytes 整图），再 inJustDecodeBounds/inSampleSize
 * 解码到约 2×目标，精确缩放到 1024；照片输出 JPEG，含 alpha 输出 PNG。
 * 临时文件与 Bitmap 在所有路径回收。
 */
class ChatImageEncoder(context: Context) : ChatImageEncoderApi {

    private val appContext = context.applicationContext

    override suspend fun toDataUrl(
        uri: Uri,
        maxDimension: Int,
        quality: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        // 复制阶段不响应取消，确保 temp 文件创建/清理成对完成。
        val temp = runCatching {
            withContext(NonCancellable) { copyBoundedToTemp(uri) }
        }.getOrElse { throwable ->
            return@withContext Result.failure(throwable)
        }
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val header = readHeader(temp)
            // 头损坏不影响解码；alpha 未知时按 PNG 保守处理。
            val dimensions = runCatching { ImageHeaderParser.parse(header) }.getOrNull()
            if (ChatImageDecodePolicy.isTooLarge(temp.length())) {
                error("图片超过 20 MB")
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                error("无法解析图片")
            }
            val sampleSize = ChatImageDecodePolicy.sampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                target = maxDimension,
            )
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = BitmapFactory.decodeFile(temp.path, options) ?: error("无法解析图片")
            val target = ChatImageDecodePolicy.targetSize(
                width = decoded.width,
                height = decoded.height,
                target = maxDimension,
            )
            scaled = if (target.width == decoded.width && target.height == decoded.height) {
                decoded
            } else {
                decoded.scale(target.width, target.height)
            }
            // 头解析失败时保守按 PNG 处理，避免透明图被强制转成 JPEG。
            val format = ChatImageDecodePolicy.outputFormat(dimensions?.hasAlpha ?: true)
            val output = ByteArrayOutputStream()
            val compressFormat = when (format) {
                ChatImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ChatImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            }
            val compressQuality = if (format == ChatImageOutputFormat.JPEG) quality else 100
            val outputBitmap = scaled
            if (!outputBitmap.compress(compressFormat, compressQuality, output)) {
                error("图片编码失败")
            }
            Result.success(
                "data:${format.mimeType};base64," +
                    Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
            )
        } catch (oom: OutOfMemoryError) {
            Result.failure(IllegalStateException("图片过大，内存不足", oom))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        } finally {
            if (scaled != null && scaled !== decoded) {
                scaled.recycle()
            }
            decoded?.recycle()
            temp.delete()
        }
    }

    private fun copyBoundedToTemp(uri: Uri): File {
        val temp = File.createTempFile("chat-image-", ".tmp", appContext.cacheDir)
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("无法读取图片")
            input.use { inputStream ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (ChatImageDecodePolicy.isTooLarge(total)) {
                            error("图片超过 20 MB")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return temp
        } catch (throwable: Throwable) {
            temp.delete()
            throw throwable
        }
    }

    private fun readHeader(file: File): ByteArray =
        file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var remaining = HEADER_BYTES
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.toByteArray()
        }

    private companion object {
        const val HEADER_BYTES = 256 * 1024
    }
}
