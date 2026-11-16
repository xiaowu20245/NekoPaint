package io.github.xiaowu20245.nekopaint.core.datastore

import io.github.xiaowu20245.nekopaint.core.model.ArchivedServer
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigReplacementTest {

    private val old = ServerProfile(id = "old", name = "旧主机", host = "10.0.0.1")
    private val other = ServerProfile(id = "other", name = "另一台", host = "10.0.0.2")
    private val new = ServerProfile(id = "new", name = "新主机", host = "10.0.0.3")

    @Test
    fun replacingActiveEndpointArchivesOldAndActivatesNew() {
        val result = ServerConfigReplacementPlanner.replace(
            servers = listOf(old, other),
            archivedServers = emptyList(),
            oldProfile = old,
            newProfile = new,
            activeId = old.id,
            activate = true,
        ).getOrThrow()

        assertEquals(listOf(other, new), result.servers)
        assertEquals(new.id, result.activeId)
        assertEquals(1, result.archivedServers.size)
        assertEquals(old.id, result.archivedServers.single().id)
        assertEquals(old.host, result.archivedServers.single().host)
    }

    @Test
    fun replacingInactiveEndpointKeepsCurrentActiveId() {
        val result = ServerConfigReplacementPlanner.replace(
            servers = listOf(old, other),
            archivedServers = emptyList(),
            oldProfile = other,
            newProfile = new,
            activeId = old.id,
            activate = false,
        ).getOrThrow()

        assertEquals(listOf(old, new), result.servers)
        assertEquals(old.id, result.activeId)
    }

    @Test
    fun deletionArchivesDeletedProfileAndPicksNextActive() {
        val result = ServerConfigReplacementPlanner.delete(
            servers = listOf(old, other),
            archivedServers = emptyList(),
            id = old.id,
            activeId = old.id,
        ).getOrThrow()

        assertEquals(listOf(other), result.servers)
        assertEquals(other.id, result.activeId)
        assertEquals(old.id, result.archivedServers.single().id)
    }

    @Test
    fun deletionWithNullActiveIdPicksFirstRemainingServer() {
        val result = ServerConfigReplacementPlanner.delete(
            servers = listOf(old, other),
            archivedServers = emptyList(),
            id = old.id,
            activeId = null,
        ).getOrThrow()

        assertEquals(other.id, result.activeId)
        assertEquals(listOf(other), result.servers)
    }

    @Test
    fun replaceRejectsDifferentPersistedEndpoint() {
        val stale = old.copy(host = "10.0.0.99")
        assertTrue(
            ServerConfigReplacementPlanner.replace(
                servers = listOf(old),
                archivedServers = emptyList(),
                oldProfile = stale,
                newProfile = new,
                activeId = old.id,
                activate = true,
            ).isFailure,
        )
    }

    @Test
    fun replaceRejectsExistingNewId() {
        assertTrue(
            ServerConfigReplacementPlanner.replace(
                servers = listOf(old, other),
                archivedServers = emptyList(),
                oldProfile = old,
                newProfile = new.copy(id = other.id),
                activeId = old.id,
                activate = true,
            ).isFailure,
        )
    }

    @Test
    fun deletionWithStaleActiveIdPicksFirstRemainingServer() {
        val result = ServerConfigReplacementPlanner.delete(
            servers = listOf(old, other),
            archivedServers = emptyList(),
            id = old.id,
            activeId = "missing",
        ).getOrThrow()

        assertEquals(other.id, result.activeId)
    }

    @Test
    fun replaceWithStaleActiveIdPicksFirstUpdatedServer() {
        val result = ServerConfigReplacementPlanner.replace(
            servers = listOf(old, other),
            archivedServers = emptyList(),
            oldProfile = other,
            newProfile = new,
            activeId = "missing",
            activate = false,
        ).getOrThrow()

        assertEquals(old.id, result.activeId)
    }

    @Test
    fun repeatedArchiveReplacesSameIdEntry() {
        val oldArchive = ArchivedServer(id = old.id, name = "旧名字", host = old.host)
        val result = ServerConfigReplacementPlanner.replace(
            servers = listOf(old),
            archivedServers = listOf(oldArchive),
            oldProfile = old,
            newProfile = new,
            activeId = old.id,
            activate = true,
        ).getOrThrow()

        assertEquals(1, result.archivedServers.size)
        assertEquals(old.id, result.archivedServers.single().id)
        assertEquals(old.name, result.archivedServers.single().name)
    }

    @Test
    fun missingOldProfileFailsWithoutChangingLists() {
        assertTrue(
            ServerConfigReplacementPlanner.replace(
                servers = listOf(other),
                archivedServers = emptyList(),
                oldProfile = old,
                newProfile = new,
                activeId = old.id,
                activate = true,
            ).isFailure,
        )
    }
}
