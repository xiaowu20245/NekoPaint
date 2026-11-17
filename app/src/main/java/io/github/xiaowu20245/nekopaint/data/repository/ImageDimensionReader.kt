package io.github.xiaowu20245.nekopaint.data.repository

import android.content.ContentResolver
import android.net.Uri
import io.github.xiaowu20245.nekopaint.core.image.ImageDimensions
import io.github.xiaowu20245.nekopaint.core.image.ImageHeaderParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * 只读取图片头部来获取尺寸/alpha，不整图下载或解码。
 *
 * HTTP 使用注入的 image client（独立超时）；content/file URI 通过 ContentResolver/File。
 */
class ImageDimensionReader(
    private val contentResolver: ContentResolver,
    private val httpClient: OkHttpClient,
    private val maxHeaderBytes: Int = DEFAULT_HEADER_BYTES,
) {

    suspend fun read(uri: Uri): Result<ImageDimensions> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = when (uri.scheme?.lowercase()) {
                "http", "https" -> readHttp(uri.toString())
                "file" -> uri.path?.let { path ->
                    File(path).inputStream().use(::readLimited)
                } ?: error("文件路径为空")

                else -> contentResolver.openInputStream(uri)?.use(::readLimited)
                    ?: error("无法读取图片")
            }
            ImageHeaderParser.parse(bytes) ?: error("无法解析图片尺寸")
        }
    }

    private fun readHttp(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val source = response.body.source()
            val buffer = Buffer()
            while (buffer.size < maxHeaderBytes) {
                val remaining = maxHeaderBytes - buffer.size
                if (source.read(buffer, remaining) == -1L) break
            }
            buffer.readByteArray()
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var remaining = maxHeaderBytes
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private companion object {
        const val DEFAULT_HEADER_BYTES = 256 * 1024
    }
}
