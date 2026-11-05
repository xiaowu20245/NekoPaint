package io.github.xiaowu20245.nekopaint.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class SavedImage(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
)

/**
 * 把 ComfyUI `/view` 图片保存到系统相册的 `Pictures/NekoPaint`。
 *
 * Android 10+ 使用分区存储，不需要存储权限；Android 8/9 仍写入公共目录，
 * 需要运行时权限（由 UI 层在调用前申请）。
 */
class ImageSaver(
    context: Context,
    private val client: OkHttpClient,
) {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    suspend fun save(url: String, originalName: String): Result<SavedImage> =
        withContext(Dispatchers.IO) {
            runCatching {
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
                        ?: ImageSaveNaming.mimeFor(originalName)
                    val relativePath = "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME/"
                    val displayName = ImageSaveNaming.uniqueName(originalName) { candidate ->
                        exists(candidate, relativePath)
                    }
                    val values = buildValues(displayName, mimeType, relativePath)
                    val collection = imagesCollection()
                    val uri = resolver.insert(collection, values)
                        ?: error("无法在相册中创建文件")

                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            body.byteStream().copyTo(output)
                        } ?: error("无法写入相册文件")

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val done = ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            }
                            resolver.update(uri, done, null, null)
                        }
                        SavedImage(uri = uri, displayName = displayName, mimeType = mimeType)
                    } catch (throwable: Throwable) {
                        resolver.delete(uri, null, null)
                        throw throwable
                    }
                }
            }
        }

    private fun buildValues(
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): ContentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            @Suppress("DEPRECATION")
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM_NAME,
            ).apply { mkdirs() }
            put(MediaStore.Images.Media.DATA, File(directory, displayName).absolutePath)
        }
    }

    private fun exists(displayName: String, relativePath: String): Boolean {
        val collection = imagesCollection()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(displayName, relativePath),
                null,
            )?.use { cursor -> return cursor.moveToFirst() }
            return false
        }

        @Suppress("DEPRECATION")
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "$ALBUM_NAME/$displayName",
        )
        @Suppress("DEPRECATION")
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DATA} = ?",
            arrayOf(file.absolutePath),
            null,
        )?.use { cursor -> return cursor.moveToFirst() }
        return file.exists()
    }

    private fun imagesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private companion object {
        const val ALBUM_NAME = "NekoPaint"
    }
}

object ImageSaveNaming {

    fun mimeFor(filename: String): String = when (filename.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }

    /**
     * 同名文件自动加序号，避免覆盖用户在相册里的旧图。
     */
    fun uniqueName(originalName: String, exists: (String) -> Boolean): String {
        val safeName = originalName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "nekopaint.png" }
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val extension = if (dot > 0) safeName.substring(dot) else ""
        var candidate = safeName
        var index = 1
        while (exists(candidate)) {
            candidate = "$base ($index)$extension"
            index += 1
            if (index > MAX_ATTEMPTS) error("同名文件过多，无法保存")
        }
        return candidate
    }

    private const val MAX_ATTEMPTS = 999
}
