package io.github.xiaowu20245.nekopaint.di

import android.content.Context
import io.github.xiaowu20245.nekopaint.BuildConfig
import io.github.xiaowu20245.nekopaint.core.common.AppLog
import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.datastore.SettingsStore
import io.github.xiaowu20245.nekopaint.data.operation.OperationCenter
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantClient
import io.github.xiaowu20245.nekopaint.core.network.AiPrivacySanitizer
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ComfyWebSocketManager
import io.github.xiaowu20245.nekopaint.core.network.NetworkConfig
import io.github.xiaowu20245.nekopaint.core.network.RedactingHttpLogger
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageEncoder
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageStore
import io.github.xiaowu20245.nekopaint.data.repository.HistoryStore
import io.github.xiaowu20245.nekopaint.data.repository.HistoryAutoSaver
import io.github.xiaowu20245.nekopaint.data.repository.ImageDimensionReader
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
import io.github.xiaowu20245.nekopaint.data.repository.ServerSensitiveHostProvider
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.TaskRecordRepository
import io.github.xiaowu20245.nekopaint.data.repository.TaskStore
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitter
import io.github.xiaowu20245.nekopaint.feature.chat.ChatActionExecutionRegistry
import io.github.xiaowu20245.nekopaint.feature.chat.ChatHistoryStore
import io.github.xiaowu20245.nekopaint.feature.chat.ChatSendExecutionRegistry
import io.github.xiaowu20245.nekopaint.feature.chat.ChatSessionOperationGate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val MANAGER_READ_TIMEOUT_SECONDS = 60L

/**
 * 手动依赖注入容器。
 *
 * V0.0 只搭建骨架；当依赖数量明显增加时，再评估是否迁移到 Hilt。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    // 应用级协程作用域：聊天请求在离开页面后仍继续完成并写盘。
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // 批量提交/安装等长操作使用独立作用域，异常不改变聊天等共享 appScope 语义。
    val operationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            AppLog.w("operation failed", throwable)
        },
    )

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    val serverConfigStore = ServerConfigStore(context.applicationContext, json)
    val sensitiveHostProvider = ServerSensitiveHostProvider(serverConfigStore, appScope)
    val aiPrivacySanitizer = AiPrivacySanitizer(sensitiveHostProvider)
    val clientIdStore = ClientIdStore(context.applicationContext)
    val settingsStore = SettingsStore(context.applicationContext)
    val aiAssistantStore = AiAssistantStore(context.applicationContext)
    val chatHistoryStore = ChatHistoryStore(context.applicationContext, json)
    // 页面销毁后后台动作仍可据此判断 session+proposal 是否在执行。
    val chatActionExecutionRegistry = ChatActionExecutionRegistry()
    // 页面销毁后 appScope 发送仍会写回原会话，新实例据此拒绝切换/删除。
    val chatSendExecutionRegistry = ChatSendExecutionRegistry()
    // 跨 ChatViewModel 共享的 SEND/ACTION/SWITCH/DELETE 许可，避免检查后另一实例 begin。
    val chatSessionOperationGate = ChatSessionOperationGate()
    val promptLexiconRepository = PromptLexiconRepository(context.applicationContext, json)
    val pendingWorkflowRefillStore = PendingWorkflowRefillStore()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NetworkConfig.ConnectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(NetworkConfig.ReadTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(NetworkConfig.WriteTimeoutSeconds, TimeUnit.SECONDS)
        .pingInterval(NetworkConfig.WebSocketPingSeconds, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // 只记录 method/path/status/耗时，host、query、body 全部不落日志。
                addInterceptor(RedactingHttpLogger())
            }
        }
        .build()

    // 图片下载单独放宽读取超时，避免 4K 原图在慢速局域网上被 15 秒截断。
    val imageOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(NetworkConfig.ImageReadTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    // 大模型连接建立和响应都可能更慢：连接 20s、读取 60s；ComfyUI 保持 5s 连接超时。
    val aiOkHttpClient: OkHttpClient = NetworkConfig.aiClient(okHttpClient)

    // Manager 首次拉取节点映射时可能需要访问远程源，单独放宽读取超时。
    val managerOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(MANAGER_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val aiAssistantClient = AiAssistantClient(
        client = aiOkHttpClient,
        json = json,
        sessionIdProvider = clientIdStore::getOrCreate,
        consentProvider = aiAssistantStore::currentHttpConsent,
        disclosureProvider = aiAssistantStore::isDisclosureAcknowledged,
        privacySanitizer = aiPrivacySanitizer,
    )

    val comfyApiFactory = ComfyApiFactory(okHttpClient, json)
    val managerApiFactory = ComfyApiFactory(managerOkHttpClient, json)

    val serverRepository = ServerRepository(
        store = serverConfigStore,
        apiFactory = comfyApiFactory,
        managerApiFactory = managerApiFactory,
    )
    val serverAvatarStore = ServerAvatarStore(context.applicationContext)

    val imageSaver = ImageSaver(context.applicationContext, imageOkHttpClient)

    val imageDimensionReader = ImageDimensionReader(
        contentResolver = context.applicationContext.contentResolver,
        httpClient = imageOkHttpClient,
    )

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
    val taskRecordRepository = TaskRecordRepository(taskStore)
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
        taskRecordRepository = taskRecordRepository,
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
        taskRecordRepository = taskRecordRepository,
    )

    val workflowSubmitter = WorkflowSubmitter { template, selected, context ->
        workflowRepository.submitSelected(template, selected, context)
    }

    val operationCenter = OperationCenter(
        scope = operationScope,
        submitter = workflowSubmitter,
        persistExecutionValues = { template ->
            workflowRepository.patchExecutionValues(template.id, template.candidates).getOrThrow()
        },
    )

    val nodeInstallRepository = NodeInstallRepository(
        apiFactory = managerApiFactory,
        serverConfigStore = serverConfigStore,
        workflowRepository = workflowRepository,
    )
}
