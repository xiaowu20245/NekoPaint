package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ArchivedServer
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.privacy.SensitiveHostProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Host provider backed by the persisted server list.
 *
 * The first emission is treated as a complete snapshot: [ServerConfigStore]
 * maps the whole DataStore value, so an empty first emission means "no
 * configured servers" rather than a partial loading state.
 */
class ServerSensitiveHostProvider(
    servers: Flow<List<ServerProfile>>,
    scope: CoroutineScope,
    archivedServers: Flow<List<ArchivedServer>> = flowOf(emptyList()),
) : SensitiveHostProvider {

    constructor(store: ServerConfigStore, scope: CoroutineScope) :
        this(store.servers, scope, store.archivedServers)

    private val hosts = MutableStateFlow<Set<String>>(emptySet())
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                combine(servers, archivedServers) { current, archived ->
                    current.map { it.host.trim() } + archived.map { it.host.trim() }
                }.collect { values ->
                    hosts.value = values
                        .filter { it.isNotBlank() }
                        .toSet()
                    if (!ready.isCompleted) ready.complete(Unit)
                }
                if (!ready.isCompleted) {
                    ready.completeExceptionally(
                        IllegalStateException("服务器配置流在首次发射前结束"),
                    )
                }
            } catch (cause: CancellationException) {
                if (!ready.isCompleted) ready.completeExceptionally(cause)
                throw cause
            } catch (cause: Throwable) {
                // Keep the failure inside this scope; awaitReady() reports it
                // as an unavailable snapshot instead of leaking the exception.
                if (!ready.isCompleted) ready.completeExceptionally(cause)
            }
        }
    }

    override fun hosts(): Set<String> = hosts.value

    override suspend fun awaitReady(): Boolean = try {
        withTimeoutOrNull(HOST_READY_TIMEOUT_MS) { ready.await() } != null
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        false
    }

    private companion object {
        const val HOST_READY_TIMEOUT_MS = 5_000L
    }
}
