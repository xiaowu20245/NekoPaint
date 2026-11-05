package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统计并清理图片缓存。
 *
 * 只清理 Coil 的图片缓存和分享临时文件，不触碰系统相册里用户已保存的图片。
 */
class MediaCacheManager(
    context: Context,
    private val shareManager: ImageShareManager,
) {

    private val appContext = context.applicationContext

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        val imageLoader = SingletonImageLoader.get(appContext)
        val imageBytes = imageLoader.diskCache?.size ?: 0L
        imageBytes + shareManager.sizeBytes()
    }

    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val imageLoader = SingletonImageLoader.get(appContext)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            shareManager.clear().getOrThrow()
        }
    }
}
