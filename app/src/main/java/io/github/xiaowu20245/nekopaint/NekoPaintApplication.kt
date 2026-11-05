package io.github.xiaowu20245.nekopaint

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.xiaowu20245.nekopaint.di.AppContainer
import okio.Path.Companion.toOkioPath

class NekoPaintApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // 共享应用连接池，使用放宽了读取超时的图片专用客户端。
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { container.imageOkHttpClient },
                    ),
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }
            .build()
}
