package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 服务器自定义图标存储。
 *
 * 图片复制到应用私有目录，避免外部 content URI 的临时权限在重启后失效。
 * 表单里新裁剪的图片先写成 draft，保存服务器时才提交为正式头像，
 * 这样在编辑弹窗中选图后取消不会覆盖已有图标。
 */
class ServerAvatarStore(context: Context) {

    private val rootDir = File(context.applicationContext.filesDir, "server_avatars")

    suspend fun saveBitmap(serverId: String, bitmap: Bitmap): Result<String> =
        writeBitmap(target = ensureTarget(serverId), bitmap = bitmap)

    suspend fun saveDraft(formTargetId: String, bitmap: Bitmap): Result<String> =
        writeBitmap(target = draftFile(formTargetId), bitmap = bitmap)

    fun isDraft(uri: String?): Boolean =
        uri != null && uri.toUri().lastPathSegment?.endsWith(DRAFT_SUFFIX) == true

    suspend fun commitDraft(serverId: String, draftUri: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val draftPath = draftUri.toUri().path ?: error("无效的草稿图标路径")
                val draft = File(draftPath)
                if (!draft.exists()) error("草稿图标不存在")
                val target = ensureTarget(serverId)
                draft.copyTo(target, overwrite = true)
                draft.delete()
                Uri.fromFile(target).toString()
            }
        }

    suspend fun deleteDraft(formTargetId: String) = withContext(Dispatchers.IO) {
        draftFile(formTargetId).delete()
        Unit
    }

    suspend fun delete(serverId: String) = withContext(Dispatchers.IO) {
        File(rootDir, "$serverId.png").delete()
        Unit
    }

    private suspend fun writeBitmap(target: File, bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!rootDir.exists()) rootDir.mkdirs()
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                Uri.fromFile(target).toString()
            }
        }

    private fun ensureTarget(serverId: String): File {
        if (!rootDir.exists()) rootDir.mkdirs()
        return File(rootDir, "$serverId.png")
    }

    private fun draftFile(formTargetId: String): File {
        if (!rootDir.exists()) rootDir.mkdirs()
        return File(rootDir, "$formTargetId$DRAFT_SUFFIX")
    }

    private companion object {
        const val DRAFT_SUFFIX = "-draft.png"
    }
}
