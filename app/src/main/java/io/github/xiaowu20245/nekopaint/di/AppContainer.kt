package io.github.xiaowu20245.nekopaint.di

import android.content.Context
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ComfyWebSocketManager
import io.github.xiaowu20245.nekopaint.core.network.NetworkConfig
import io.github.xiaowu20245.nekopaint.data.repository.ImageSaver
import io.github.xiaowu20245.nekopaint.data.repository.ImageShareManager
import io.github.xiaowu20245.nekopaint.data.repository.MediaCacheManager
import io.github.xiaowu20245.nekopaint.data.repository.QueueRepository
import io.github.xiaowu20245.nekopaint.data.repository.ServerRepository
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.TaskStore
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * 手动依赖注入容器。
 *
 * V0.0 只搭建骨架；当依赖数量明显增加时，再评估是否迁移到 Hilt。
 */
class AppContainer(context: Context) {

    val serverConfigStore = ServerConfigStore(context.applicationContext)
    val clientIdStore = ClientIdStore(context.applicationContext)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NetworkConfig.ConnectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(NetworkConfig.ReadTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(NetworkConfig.WriteTimeoutSeconds, TimeUnit.SECONDS)
        .pingInterval(NetworkConfig.WebSocketPingSeconds, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        // BASIC 不记录请求体，避免把提示词写入日志。
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    // 图片下载单独放宽读取超时，避免 4K 原图在慢速局域网上被 15 秒截断。
    val imageOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(NetworkConfig.ImageReadTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    val comfyApiFactory = ComfyApiFactory(okHttpClient, json)

    val serverRepository = ServerRepository(serverConfigStore, comfyApiFactory)

    val imageSaver = ImageSaver(context.applicationContext, imageOkHttpClient)

    val imageShareManager = ImageShareManager(context.applicationContext, imageOkHttpClient)

    val mediaCacheManager = MediaCacheManager(context.applicationContext, imageShareManager)

    val taskStore = TaskStore(context.applicationContext, json)

    val webSocketManager = ComfyWebSocketManager(
        client = okHttpClient,
        json = json,
        serverConfigStore = serverConfigStore,
        clientIdStore = clientIdStore,
    )

    val queueRepository = QueueRepository(comfyApiFactory, serverConfigStore)

    val taskManager = TaskManager(
        taskStore = taskStore,
        queueRepository = queueRepository,
        webSocketManager = webSocketManager,
        clientIdStore = clientIdStore,
    )

    val workflowRepository = WorkflowRepository(
        context = context.applicationContext,
        json = json,
        apiFactory = comfyApiFactory,
        serverConfigStore = serverConfigStore,
        clientIdStore = clientIdStore,
        taskStore = taskStore,
    )
}
