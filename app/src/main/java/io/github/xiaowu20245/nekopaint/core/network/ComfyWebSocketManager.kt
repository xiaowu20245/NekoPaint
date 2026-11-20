package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.random.Random

enum class ComfyConnectionState {
    Offline,
    Connecting,
    Online,
    Reconnecting,
    Unconfigured,
}

data class NodeProgress(
    val nodeId: String,
    val value: Double,
    val max: Double,
    val state: String?,
)

sealed interface ComfyEvent {    data class Status(val queueRemaining: Int) : ComfyEvent
    data class ExecutionStart(val promptId: String) : ComfyEvent
    data class ExecutionCached(val promptId: String, val nodes: List<String>) : ComfyEvent
    data class Executing(val promptId: String, val nodeId: String?) : ComfyEvent
    data class Progress(
        val promptId: String,
        val nodeId: String,
        val value: Double,
        val max: Double,
    ) : ComfyEvent

    data class ProgressState(
        val promptId: String,
        val nodes: List<NodeProgress>,
    ) : ComfyEvent

    data class Executed(
        val promptId: String,
        val nodeId: String,
        val output: JsonObject?,
    ) : ComfyEvent

    data class Success(val promptId: String) : ComfyEvent
    data class Error(
        val promptId: String,
        val nodeId: String?,
        val message: String,
    ) : ComfyEvent

    data class Interrupted(val promptId: String) : ComfyEvent
}

data class ComfyEventEnvelope(
    val endpointKey: String,
    val connectionToken: Long,
    val socketToken: Long,
    val event: ComfyEvent,
)

class ComfyWebSocketManager(
    private val client: OkHttpClient,
    private val json: Json,
    private val serverConfigStore: ServerConfigStore,
    private val clientIdStore: ClientIdStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connection = MutableStateFlow(ComfyConnectionState.Offline)
    val connection: StateFlow<ComfyConnectionState> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<ComfyEventEnvelope>(extraBufferCapacity = 128)
    val events: SharedFlow<ComfyEventEnvelope> = _events.asSharedFlow()

    private val eventParser = ComfyEventParser(json)

    @Volatile
    private var connectionToken: Long = 0L

    @Volatile
    private var socketToken: Long = 0L

    private var job: Job? = null
    private var socket: WebSocket? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            serverConfigStore.config.collectLatest { config ->
                val token = ++connectionToken
                socket?.close(1000, null)
                socket = null
                if (config == null) {
                    _connection.value = ComfyConnectionState.Offline
                    return@collectLatest
                }
                val endpoint = ServerEndpointValidator.parse(config).getOrNull()
                if (endpoint == null) {
                    _connection.value = ComfyConnectionState.Unconfigured
                    return@collectLatest
                }
                connectLoop(endpoint, token)
            }
        }
    }

    fun isCurrentConnection(token: Long, socket: Long): Boolean =
        token == connectionToken && socket == socketToken

    fun stop() {
        connectionToken++
        socketToken++
        job?.cancel()
        job = null
        socket?.close(1000, null)
        socket = null
        _connection.value = ComfyConnectionState.Offline
    }

    private suspend fun connectLoop(endpoint: ServerEndpoint, token: Long) {
        var backoffMs = 1_000L
        while (currentCoroutineContext().isActive) {
            val attemptToken = ++socketToken
            _connection.value = ComfyConnectionState.Connecting
            val clientId = try {
                clientIdStore.getOrCreate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _connection.value = ComfyConnectionState.Reconnecting
                delay(backoffMs + Random.nextLong(0, 500))
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                continue
            }
            val connected = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (token != connectionToken || attemptToken != socketToken) return
                    backoffMs = 1_000L
                    _connection.value = ComfyConnectionState.Online
                    connected.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (token != connectionToken || attemptToken != socketToken) return
                    eventParser.parse(text)?.let {
                        _events.tryEmit(
                            ComfyEventEnvelope(endpoint.endpointKey, token, attemptToken, it),
                        )
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    // V0.3 ignores binary preview frames.
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (token != connectionToken || attemptToken != socketToken) return
                    closed.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (token != connectionToken || attemptToken != socketToken) return
                    closed.complete(Unit)
                }
            }

            val ws = try {
                client.newWebSocket(
                    Request.Builder().url(endpoint.webSocketUrl(clientId)).build(),
                    listener,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: InvalidServerConfigException) {
                _connection.value = ComfyConnectionState.Unconfigured
                return
            } catch (invalid: IllegalArgumentException) {
                _connection.value = ComfyConnectionState.Unconfigured
                return
            } catch (_: Throwable) {
                _connection.value = ComfyConnectionState.Reconnecting
                delay(backoffMs + Random.nextLong(0, 500))
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                continue
            }
            socket = ws

            try {
                withTimeout(5_000) { connected.await() }
                closed.await()
            } catch (cancelled: CancellationException) {
                ws.cancel()
                // withTimeout 超时也会抛 CancellationException，但协程本身没被取消；
                // 只有父 Job 真的取消时才退出重连循环。
                if (!currentCoroutineContext().isActive) throw cancelled
            } catch (_: Throwable) {
                ws.cancel()
            } finally {
                if (socket === ws) socket = null
                if (currentCoroutineContext().isActive) {
                    _connection.value = ComfyConnectionState.Reconnecting
                    // 加少量抖动，避免多个客户端同时重连。
                    delay(backoffMs + Random.nextLong(0, 500))
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }
}
