package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class SharedImage(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
)

/**
 * 下载图片到 cacheDir/share，再通过 FileProvider 生成可分享的 content URI。
 *
 * 只暴露 share 子目录；分享前清理旧临时文件，避免缓存无限增长。
 */
class ImageShareManager(
    context: Context,
    private val client: OkHttpClient,
) {

    private val appContext = context.applicationContext
    private val shareDir = File(appContext.cacheDir, SHARE_DIR)

    suspend fun share(url: String, originalName: String): Result<SharedImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                clean()
                shareDir.mkdirs()
                val safeName = ImageSaveNaming.uniqueName(originalName) { candidate ->
                    File(shareDir, candidate).exists()
                }
                val file = File(shareDir, safeName)
                val request = Request.Builder()
                    .url(url.toHttpUrl())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("服务器返回 HTTP ${response.code}")
                    }
                    val body = response.body
                    val mimeType = body.contentType()
                        ?.toString()
                        ?.substringBefore(';')
                        ?: ImageSaveNaming.mimeFor(safeName)
                    file.outputStream().use { output ->
                        body.byteStream().copyTo(output)
                    }
                    SharedImage(
                        uri = FileProvider.getUriForFile(appContext, authority(), file),
                        mimeType = mimeType,
                        displayName = safeName,
                    )
                }
            }
        }

    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { clean() }
    }

    fun sizeBytes(): Long = shareDir.listFiles()
        .orEmpty()
        .filter { it.isFile }
        .sumOf { it.length() }

    private fun clean() {
        if (!shareDir.exists()) return
        shareDir.listFiles().orEmpty().forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun authority(): String = "${appContext.packageName}.fileprovider"

    private companion object {
        const val SHARE_DIR = "share"
    }
}
