package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryArchiveTest {

    @Test
    fun `merges entries from different hosts and sorts by time`() {
        val archived = listOf(
            entry("a", serverId = "local", timestamp = 100L),
            entry("b", serverId = "autodl", timestamp = 300L),
        )

        val merged = mergeHistoryArchives(archived, emptyList())

        assertEquals(listOf("b", "a"), merged.map { it.promptId })
        assertEquals(listOf("autodl", "local"), merged.map { it.serverId })
    }

    @Test
    fun `live entry overrides same host and prompt id`() {
        val archived = listOf(
            entry("shared", serverId = "local", timestamp = 100L, status = "running"),
            entry("shared", serverId = "autodl", timestamp = 200L, status = "success"),
        )
        val live = listOf(
            entry("shared", serverId = "local", timestamp = 150L, status = "success"),
        )

        val merged = mergeHistoryArchives(archived, live)

        assertEquals(2, merged.size)
        assertEquals("success", merged.first { it.serverId == "local" }.status)
        assertEquals(150L, merged.first { it.serverId == "local" }.timestamp)
        assertEquals(200L, merged.first { it.serverId == "autodl" }.timestamp)
    }

    @Test
    fun `blank server id is treated as default host`() {
        val archived = listOf(entry("p1", serverId = "", timestamp = 100L))
        val live = listOf(entry("p1", serverId = "default", timestamp = 200L))

        val merged = mergeHistoryArchives(archived, live)

        assertEquals(1, merged.size)
        assertEquals(200L, merged.single().timestamp)
    }

    @Test
    fun `task archives keep both hosts and prefer live record`() {
        val archived = listOf(
            task("p1", serverId = "local", submittedAt = 100L, status = "queued"),
            task("p1", serverId = "autodl", submittedAt = 200L, status = "success"),
        )
        val live = listOf(
            task("p1", serverId = "local", submittedAt = 150L, status = "success"),
        )

        val merged = mergeTaskArchives(archived, live)

        assertEquals(2, merged.size)
        assertEquals("success", merged.first { it.serverId == "local" }.status)
        assertEquals(150L, merged.first { it.serverId == "local" }.submittedAt)
    }

    @Test
    fun `duplicate prompt id across aliases keeps active host entry on tie`() {
        val entries = listOf(
            entry("shared", serverId = "old-alias", timestamp = 100L),
            entry("shared", serverId = "active", timestamp = 100L),
        )

        val deduped = dedupeHistoryByPrompt(entries, preferredServerId = "active")

        assertEquals(1, deduped.size)
        assertEquals("active", deduped.single().serverId)
    }

    @Test
    fun `newer inactive alias entry wins over older active archive`() {
        val entries = listOf(
            entry("shared", serverId = "active", timestamp = 100L),
            entry("shared", serverId = "newer", timestamp = 300L),
        )

        val deduped = dedupeHistoryByPrompt(entries, preferredServerId = "active")

        assertEquals(1, deduped.size)
        assertEquals("newer", deduped.single().serverId)
    }

    @Test
    fun `duplicate prompt id in inactive archives keeps newest entry`() {
        val entries = listOf(
            entry("shared", serverId = "old", timestamp = 100L),
            entry("shared", serverId = "newer", timestamp = 300L),
        )

        val deduped = dedupeHistoryByPrompt(entries, preferredServerId = "active")

        assertEquals(1, deduped.size)
        assertEquals("newer", deduped.single().serverId)
    }

    @Test
    fun `duplicate task records are deduped by prompt id`() {
        val tasks = listOf(
            task("p1", serverId = "old-alias", submittedAt = 100L, status = "success"),
            task("p1", serverId = "active", submittedAt = 100L, status = "success"),
            task("p2", serverId = "active", submittedAt = 200L, status = "success"),
        )

        val deduped = dedupeTasksByPrompt(tasks, preferredServerId = "active")

        assertEquals(listOf("p2", "p1"), deduped.map { it.promptId })
        assertEquals("active", deduped.first { it.promptId == "p1" }.serverId)
    }

    @Test
    fun `same endpoint server ids include aliases of active host`() {
        val profiles = listOf(
            profile("local", "ComfyUI", "127.0.0.1", 8188),
            profile("local-alias", "我的 ComfyUI", "127.0.0.1", 8188),
            profile("remote", "AutoDL", "example.com", 8443),
        )

        val ids = sameEndpointServerIds(profiles, activeId = "local")

        assertEquals(setOf("local", "local-alias"), ids)
    }

    @Test
    fun `same endpoint fallback keeps active id when profile is missing`() {
        val ids = sameEndpointServerIds(emptyList(), activeId = "removed")

        assertEquals(setOf("removed"), ids)
    }

    private fun profile(
        id: String,
        name: String,
        host: String,
        port: Int,
    ): ServerProfile = ServerProfile(
        id = id,
        name = name,
        scheme = "http",
        host = host,
        port = port,
    )

    private fun entry(
        promptId: String,
        serverId: String,
        timestamp: Long,
        status: String = "success",
    ): HistoryEntry = HistoryEntry(
        promptId = promptId,
        serverId = serverId,
        status = status,
        completed = status != "running",
        timestamp = timestamp,
        startedAt = timestamp,
        finishedAt = timestamp,
        images = emptyList(),
        errorNodeId = null,
        errorMessage = null,
    )

    private fun task(
        promptId: String,
        serverId: String,
        submittedAt: Long,
        status: String,
    ): TaskRecord = TaskRecord(
        promptId = promptId,
        serverId = serverId,
        templateId = "tpl",
        templateName = "模板",
        clientId = "client",
        submittedAt = submittedAt,
        status = status,
    )
}
