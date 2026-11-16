package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class StagedAvatar(
    val target: File,
    val backup: File?,
    val draft: File?,
) {
    val uri: String
        get() = Uri.fromFile(target).toString()
}

/**
 * 服务器自定义图标存储。
 *
 * 图片复制到应用私有目录，避免外部 content URI 的临时权限在重启后失效。
 * 表单里新裁剪的图片先写成 draft，保存服务器时才提交为正式头像，
 * 这样在编辑弹窗中选图后取消不会覆盖已有图标。
 */
class ServerAvatarStore(context: Context) {

    private val rootDir = File(context.applicationContext.filesDir, "server_avatars")

    suspend fun stageBitmap(serverId: String, bitmap: Bitmap): Result<StagedAvatar> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = ensureTarget(serverId)
                val backup = if (target.exists()) {
                    File(rootDir, "$serverId-backup-${UUID.randomUUID()}.png").also {
                        target.copyTo(it, overwrite = true)
                    }
                } else {
                    null
                }
                try {
                    val written = target.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    if (!written) error("头像压缩失败")
                } catch (error: Throwable) {
                    val restored = restoreTarget(target, backup)
                    if (restored.isFailure) {
                        throw IllegalStateException("头像写入失败且无法恢复旧文件", error)
                    }
                    throw error
                }
                StagedAvatar(target = target, backup = backup, draft = null)
            }
        }

    suspend fun saveDraft(formTargetId: String, bitmap: Bitmap): Result<String> =
        writeBitmap(target = draftFile(formTargetId), bitmap = bitmap)

    fun isDraft(uri: String?): Boolean =
        uri != null && uri.toUri().lastPathSegment?.endsWith(DRAFT_SUFFIX) == true

    /**
     * Copies an avatar to its target and keeps a backup of the previous target.
     * The draft source is not deleted until [commitStaged].
     */
    suspend fun stageForSave(
        serverId: String,
        sourceUri: String,
        isDraft: Boolean,
    ): Result<StagedAvatar> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = sourceUri.toUri().path ?: error("无效的头像路径")
            val source = File(sourcePath)
            if (!source.exists()) error("头像文件不存在")
            val target = ensureTarget(serverId)
            val backup = if (target.exists() && source.absolutePath != target.absolutePath) {
                File(rootDir, "$serverId-backup-${UUID.randomUUID()}.png").also {
                    target.copyTo(it, overwrite = true)
                }
            } else {
                null
            }
            if (source.absolutePath != target.absolutePath) {
                try {
                    source.copyTo(target, overwrite = true)
                } catch (error: Throwable) {
                    val restored = restoreTarget(target, backup)
                    if (restored.isFailure) {
                        throw IllegalStateException("头像写入失败且无法恢复旧文件", error)
                    }
                    throw error
                }
            }
            StagedAvatar(
                target = target,
                backup = backup,
                draft = if (isDraft) source else null,
            )
        }
    }

    suspend fun commitStaged(staged: StagedAvatar): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                staged.draft?.let { draft ->
                    if (!draft.delete() && draft.exists()) error("无法清理头像草稿")
                }
                staged.backup?.let { backup ->
                    if (!backup.delete() && backup.exists()) error("无法清理头像备份")
                }
                Unit
            }
        }

    suspend fun rollbackStaged(staged: StagedAvatar): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                restoreTarget(staged.target, staged.backup).getOrThrow()
                Unit
            }
        }

    private fun restoreTarget(target: File, backup: File?): Result<Unit> =
        runCatching {
            if (backup != null && backup.exists()) {
                try {
                    Files.move(
                        backup.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    val temp = File(
                        target.parentFile,
                        "${target.name}.restore-${UUID.randomUUID()}.tmp",
                    )
                    try {
                        backup.copyTo(temp, overwrite = true)
                        Files.move(
                            temp.toPath(),
                            target.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                        backup.delete()
                    } finally {
                        temp.delete()
                    }
                }
            } else {
                target.delete()
            }
            return@runCatching Unit
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
                try {
                    val written = target.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    if (!written) {
                        error("头像压缩失败")
                    }
                } catch (error: Throwable) {
                    target.delete()
                    throw error
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
