package io.github.xiaowu20245.nekopaint.core.common

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Writes a file through a unique temporary sibling and atomically replaces the
 * target. The original target is left intact when writing or moving fails.
 */
object AtomicFileWriter {

    fun writeText(target: File, text: String) {
        writeText(target, text, ::moveIntoPlace)
    }

    internal fun writeText(
        target: File,
        text: String,
        move: (File, File) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val temp = File(
            target.parentFile,
            "${target.name}.${UUID.randomUUID()}.tmp",
        )
        try {
            FileOutputStream(temp).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            move(temp, target)
        } finally {
            temp.delete()
        }
    }

    private fun moveIntoPlace(temp: File, target: File) {
        var lastFailure: Throwable? = null
        repeat(MOVE_ATTEMPTS) { attempt ->
            try {
                tryMove(temp, target)
                return
            } catch (cancelled: InterruptedException) {
                throw cancelled
            } catch (throwable: Throwable) {
                lastFailure = throwable
                if (attempt < MOVE_ATTEMPTS - 1) {
                    Thread.sleep(10L * (attempt + 1))
                }
            }
        }
        throw lastFailure ?: IllegalStateException("无法替换文件")
    }

    private fun tryMove(temp: File, target: File) {
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private const val MOVE_ATTEMPTS = 8
}
