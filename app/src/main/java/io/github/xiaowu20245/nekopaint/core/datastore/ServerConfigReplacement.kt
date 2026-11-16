package io.github.xiaowu20245.nekopaint.core.datastore

import io.github.xiaowu20245.nekopaint.core.model.ArchivedServer
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile

data class ServerConfigReplacement(
    val servers: List<ServerProfile>,
    val archivedServers: List<ArchivedServer>,
    val activeId: String?,
)

/**
 * Pure list editing used by [ServerConfigStore].
 *
 * Keeping this logic outside DataStore makes replacement/archiving/active-id
 * behavior testable on the JVM and guarantees that the caller can write all
 * three keys in one transaction.
 */
object ServerConfigReplacementPlanner {

    fun replace(
        servers: List<ServerProfile>,
        archivedServers: List<ArchivedServer>,
        oldProfile: ServerProfile,
        newProfile: ServerProfile,
        activeId: String?,
        activate: Boolean,
    ): Result<ServerConfigReplacement> {
        if (oldProfile.id == newProfile.id) {
            return Result.failure(IllegalArgumentException("新地址必须使用新的 serverId"))
        }
        val index = servers.indexOfFirst { it.id == oldProfile.id }
        if (index < 0) {
            return Result.failure(IllegalStateException("找不到要替换的服务器"))
        }
        val persisted = servers[index]
        if (persisted.endpointKey != oldProfile.endpointKey) {
            return Result.failure(
                IllegalStateException("要替换的服务器地址已变化，请刷新后重试"),
            )
        }
        if (servers.any { it.id == newProfile.id }) {
            return Result.failure(IllegalStateException("新 serverId 已存在"))
        }
        val updated = servers.toMutableList().apply {
            removeAt(index)
            add(newProfile)
        }
        val archived = upsertArchive(archivedServers, ArchivedServer.from(persisted))
        val activeExists = activeId != null && updated.any { it.id == activeId }
        val nextActiveId = when {
            activeId == oldProfile.id -> newProfile.id
            activate -> newProfile.id
            activeExists -> activeId
            else -> updated.firstOrNull()?.id
        }
        return Result.success(
            ServerConfigReplacement(
                servers = updated,
                archivedServers = archived,
                activeId = nextActiveId,
            ),
        )
    }

    fun delete(
        servers: List<ServerProfile>,
        archivedServers: List<ArchivedServer>,
        id: String,
        activeId: String?,
    ): Result<ServerConfigReplacement> {
        val profile = servers.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalStateException("找不到要删除的服务器"))
        val remaining = servers.filterNot { it.id == id }
        val archived = upsertArchive(archivedServers, ArchivedServer.from(profile))
        val nextActiveId = when {
            activeId != null && remaining.any { it.id == activeId } -> activeId
            else -> remaining.firstOrNull()?.id
        }
        return Result.success(
            ServerConfigReplacement(
                servers = remaining,
                archivedServers = archived,
                activeId = nextActiveId,
            ),
        )
    }

    private fun upsertArchive(
        archived: List<ArchivedServer>,
        entry: ArchivedServer,
    ): List<ArchivedServer> =
        archived.filterNot { it.id == entry.id } + entry
}
