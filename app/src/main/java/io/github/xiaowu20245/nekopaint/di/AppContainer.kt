package io.github.xiaowu20245.nekopaint.di

import android.content.Context
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantClient
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ComfyWebSocketManager
import io.github.xiaowu20245.nekopaint.core.network.NetworkConfig
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageEncoder
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageStore
import io.github.xiaowu20245.nekopaint.data.repository.HistoryStore
import io.github.xiaowu20245.nekopaint.data.repository.HistoryAutoSaver
import io.github.xiaowu20245.nekopaint.data.repository.ImageSaver
import io.github.xiaowu20245.nekopaint.data.repository.ImageShareManager
import io.github.xiaowu20245.nekopaint.data.repository.MediaCacheManager
import io.github.xiaowu20245.nekopaint.data.repository.ModelCatalogStore
import io.github.xiaowu20245.nekopaint.data.repository.ModelsRepository
import io.github.xiaowu20245.nekopaint.data.repository.NodeInstallRepository
import io.github.xiaowu20245.nekopaint.data.repository.PendingWorkflowRefillStore
import io.github.xiaowu20245.nekopaint.data.repository.PromptLexiconRepository
import io.github.xiaowu20245.nekopaint.data.repository.QueueRepository
import io.github.xiaowu20245.nekopaint.data.repository.ServerRepository
import io.github.xiaowu20245.nekopaint.data.repository.ServerAvatarStore
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.TaskStore
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.feature.chat.ChatHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

private const val MANAGER_READ_TIMEOUT_SECONDS = 60L

/**
 * 手动依赖注入容器。
 *
 * V0.0 只搭建骨架；当依赖数量明显增加时，再评估是否迁移到 Hilt。
 */
class AppContainer(context: Context) {

    // 应用级协程作用域：聊天请求在离开页面后仍继续完成并写盘。
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    val serverConfigStore = ServerConfigStore(context.applicationContext, json)
    val clientIdStore = ClientIdStore(context.applicationContext)
    val settingsStore = SettingsStore(context.applicationContext)
    val aiAssistantStore = AiAssistantStore(context.applicationContext)
    val chatHistoryStore = ChatHistoryStore(context.applicationContext, json)
    val promptLexiconRepository = PromptLexiconRepository(context.applicationContext, json)
    val pendingWorkflowRefillStore = PendingWorkflowRefillStore()

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

    // 大模型响应可能超过普通请求的 15 秒读取超时，单独放宽到 60 秒。
    val aiOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(NetworkConfig.AiReadTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    // Manager 首次拉取节点映射时可能需要访问远程源，单独放宽读取超时。
    val managerOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(MANAGER_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val aiAssistantClient = AiAssistantClient(
        client = aiOkHttpClient,
        json = json,
        sessionIdProvider = clientIdStore::getOrCreate,
    )

    val comfyApiFactory = ComfyApiFactory(okHttpClient, json)
    val managerApiFactory = ComfyApiFactory(managerOkHttpClient, json)

    val serverRepository = ServerRepository(serverConfigStore, comfyApiFactory)
    val serverAvatarStore = ServerAvatarStore(context.applicationContext)

    val imageSaver = ImageSaver(context.applicationContext, imageOkHttpClient)

    val historyAutoSaver = HistoryAutoSaver(
        context = context.applicationContext,
        json = json,
        settingsStore = settingsStore,
        imageSaver = imageSaver,
    )

    val imageShareManager = ImageShareManager(context.applicationContext, imageOkHttpClient)

    val chatImageEncoder = ChatImageEncoder(context.applicationContext)

    val chatImageStore = ChatImageStore(context.applicationContext)

    val mediaCacheManager = MediaCacheManager(context.applicationContext, imageShareManager)

    val taskStore = TaskStore(context.applicationContext, json)
    val historyStore = HistoryStore(context.applicationContext, json)
    val modelCatalogStore = ModelCatalogStore(context.applicationContext, json)
    val modelsRepository = ModelsRepository(
        apiFactory = comfyApiFactory,
        serverConfigStore = serverConfigStore,
        store = modelCatalogStore,
    )

    val webSocketManager = ComfyWebSocketManager(
        client = okHttpClient,
        json = json,
        serverConfigStore = serverConfigStore,
        clientIdStore = clientIdStore,
    )

    val queueRepository = QueueRepository(comfyApiFactory, serverConfigStore)

    val taskManager = TaskManager(
        taskStore = taskStore,
        historyStore = historyStore,
        queueRepository = queueRepository,
        webSocketManager = webSocketManager,
        clientIdStore = clientIdStore,
        serverConfigStore = serverConfigStore,
        historyAutoSaver = historyAutoSaver,
    )

    val workflowRepository = WorkflowRepository(
        context = context.applicationContext,
        json = json,
        apiFactory = comfyApiFactory,
        serverConfigStore = serverConfigStore,
        clientIdStore = clientIdStore,
        taskStore = taskStore,
    )

    val nodeInstallRepository = NodeInstallRepository(
        apiFactory = managerApiFactory,
        serverConfigStore = serverConfigStore,
        workflowRepository = workflowRepository,
    )
}
