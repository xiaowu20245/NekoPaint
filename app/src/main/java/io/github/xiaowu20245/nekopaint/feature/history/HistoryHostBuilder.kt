package io.github.xiaowu20245.nekopaint.feature.history

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ArchivedServer
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord

/**
 * Builds the history host filter, including archived hosts.
 *
 * Current profiles are grouped by endpoint (D-058). Archived ids are kept in
 * their own read-only groups; they are never merged into a current host even
 * if the endpoint string happens to match.
 */
object HistoryHostBuilder {

    private const val DEFAULT_HOST_NAME = "本机 ComfyUI"

    fun build(
        servers: List<ServerProfile>,
        archivedServers: List<ArchivedServer>,
        activeId: String,
        entries: List<HistoryEntry>,
        tasks: List<TaskRecord>,
    ): List<HistoryHost> {
        val activeProfile = servers.firstOrNull { it.id == activeId }
        val activeEndpoint = activeProfile?.endpointKey

        val groups = LinkedHashMap<String, MutableList<ServerProfile>>()
        servers.forEach { profile ->
            groups.getOrPut(profile.endpointKey) { mutableListOf() } += profile
        }

        val hosts = groups.map { (endpoint, members) ->
            val active = endpoint == activeEndpoint
            val primary = if (active) {
                members.firstOrNull { it.id == activeId } ?: members.first()
            } else {
                members.first()
            }
            HistoryHost(
                id = endpoint,
                name = primary.name,
                active = active,
                serverIds = members.map { it.id }.toSet(),
                aliasNames = members.map { it.name },
                aliasCount = members.size,
            )
        }.toMutableList()

        val knownIds = servers.map { it.id }.toSet()
        val archivedById = archivedServers.associateBy { it.id }
        val unknownIds = LinkedHashSet<String>()
        entries.mapTo(unknownIds) { it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } }
        tasks.mapTo(unknownIds) { it.serverId.ifBlank { ServerConfigStore.DEFAULT_SERVER_ID } }
        unknownIds.removeAll(knownIds)
        unknownIds.forEach { id ->
            val archived = archivedById[id]
            val fallbackName = when {
                archived != null -> archived.name
                id == ServerConfigStore.DEFAULT_SERVER_ID -> DEFAULT_HOST_NAME
                else -> id.take(8)
            }
            hosts += HistoryHost(
                id = "server:$id",
                name = fallbackName,
                active = false,
                serverIds = setOf(id),
                aliasNames = listOf(fallbackName),
            )
        }

        return hosts.sortedWith(
            compareByDescending<HistoryHost> { it.active }
                .thenBy { it.name.lowercase() },
        )
    }
}
