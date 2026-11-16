package io.github.xiaowu20245.nekopaint.feature.history

import io.github.xiaowu20245.nekopaint.core.model.ArchivedServer
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryHostBuilderTest {

    private val active = ServerProfile(id = "active", name = "当前主机", host = "10.0.0.1")
    private val alias = ServerProfile(id = "alias", name = "同地址别名", host = "10.0.0.1")
    private val other = ServerProfile(id = "other", name = "另一台", host = "10.0.0.2")

    @Test
    fun groupsSameEndpointAliases() {
        val hosts = HistoryHostBuilder.build(
            servers = listOf(active, alias, other),
            archivedServers = emptyList(),
            activeId = active.id,
            entries = emptyList(),
            tasks = emptyList(),
        )

        val current = hosts.first { it.active }
        assertEquals(setOf(active.id, alias.id), current.serverIds)
        assertEquals(2, current.aliasCount)
    }

    @Test
    fun archivedProfileProvidesReadableName() {
        val hosts = HistoryHostBuilder.build(
            servers = listOf(active),
            archivedServers = listOf(
                ArchivedServer(id = "old", name = "旧客厅电脑", host = "10.0.0.9"),
            ),
            activeId = active.id,
            entries = listOf(entry("old")),
            tasks = emptyList(),
        )

        val archived = hosts.first { it.id == "server:old" }
        assertEquals("旧客厅电脑", archived.name)
        assertFalse(archived.active)
        assertEquals(setOf("old"), archived.serverIds)
    }

    @Test
    fun missingArchiveFallsBackToShortId() {
        val hosts = HistoryHostBuilder.build(
            servers = listOf(active),
            archivedServers = emptyList(),
            activeId = active.id,
            entries = listOf(entry("1234567890")),
            tasks = emptyList(),
        )

        assertTrue(hosts.any { it.id == "server:1234567890" && it.name == "12345678" })
    }

    @Test
    fun archivedHostIsNeverMarkedActive() {
        val hosts = HistoryHostBuilder.build(
            servers = listOf(active),
            archivedServers = listOf(
                ArchivedServer(id = "old", name = "旧主机", host = "10.0.0.9"),
            ),
            activeId = "old",
            entries = listOf(entry("old")),
            tasks = emptyList(),
        )

        val archived = hosts.first { it.id == "server:old" }
        assertFalse(archived.active)
        assertTrue(hosts.none { it.active })
    }

    @Test
    fun defaultMissingHostKeepsLegacyName() {
        val hosts = HistoryHostBuilder.build(
            servers = emptyList(),
            archivedServers = emptyList(),
            activeId = "default",
            entries = listOf(entry("default")),
            tasks = emptyList(),
        )

        assertEquals("本机 ComfyUI", hosts.first { it.id == "server:default" }.name)
    }

    @Test
    fun archivedEntryIsNotMergedIntoCurrentSameEndpointGroup() {
        val hosts = HistoryHostBuilder.build(
            servers = listOf(active),
            archivedServers = listOf(
                ArchivedServer(
                    id = "deleted-alias",
                    name = "已删除别名",
                    host = active.host,
                    port = active.port,
                ),
            ),
            activeId = active.id,
            entries = listOf(entry("deleted-alias")),
            tasks = emptyList(),
        )

        val current = hosts.first { it.active }
        val archived = hosts.first { it.id == "server:deleted-alias" }
        assertFalse(archived.serverIds.contains(active.id))
        assertFalse(current.serverIds.contains("deleted-alias"))
    }

    private fun entry(serverId: String): HistoryEntry = HistoryEntry(
        promptId = "prompt-$serverId",
        serverId = serverId,
        status = "success",
        completed = true,
        timestamp = 1L,
        startedAt = 1L,
        finishedAt = 2L,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )
}
