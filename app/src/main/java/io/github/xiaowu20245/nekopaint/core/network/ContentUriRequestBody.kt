package io.github.xiaowu20245.nekopaint.core.network

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * 直接从 content:// Uri 流式上传，避免把大图整张读进内存。
 *
 * contentLength 已知时交给 OkHttp 计算进度；未知时返回 -1 使用分块传输。
 */
class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val length: Long?,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = length ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        input.source().use { source ->
            sink.writeAll(source)
        }
    }
}
