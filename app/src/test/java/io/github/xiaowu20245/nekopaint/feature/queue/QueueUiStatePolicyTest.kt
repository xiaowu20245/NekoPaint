package io.github.xiaowu20245.nekopaint.feature.queue

import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueUiStatePolicyTest {

    private val runningItem = QueueItem(
        number = 1,
        promptId = "running",
        clientId = "client",
        prompt = null,
        nodeTitles = emptyMap(),
    )

    private val pendingItem = QueueItem(
        number = 2,
        promptId = "pending",
        clientId = "client",
        prompt = null,
        nodeTitles = emptyMap(),
    )

    private fun snapshot(
        running: List<QueueItem> = emptyList(),
        pending: List<QueueItem> = emptyList(),
    ) = QueueSnapshot(running = running, pending = pending, updatedAt = 0L)

    @Test
    fun unconfiguredWinsEvenWithCachedQueue() {
        assertEquals(
            QueueLoadState.Unconfigured,
            QueueUiStatePolicy.loadState(
                queue = snapshot(running = listOf(runningItem)),
                refreshing = false,
                error = null,
                configured = false,
            ),
        )
    }

    @Test
    fun firstLoadWithoutQueueIsLoading() {
        assertEquals(
            QueueLoadState.Loading,
            QueueUiStatePolicy.loadState(
                queue = null,
                refreshing = false,
                error = null,
                configured = true,
            ),
        )
    }

    @Test
    fun firstFailureIsError() {
        assertEquals(
            QueueLoadState.Error,
            QueueUiStatePolicy.loadState(
                queue = null,
                refreshing = false,
                error = "offline",
                configured = true,
            ),
        )
    }

    @Test
    fun retryWithNoQueueIsLoading() {
        assertEquals(
            QueueLoadState.Loading,
            QueueUiStatePolicy.loadState(
                queue = null,
                refreshing = true,
                error = "offline",
                configured = true,
            ),
        )
    }

    @Test
    fun refreshWithOldQueueKeepsContent() {
        assertEquals(
            QueueLoadState.Refreshing,
            QueueUiStatePolicy.loadState(
                queue = snapshot(running = listOf(runningItem)),
                refreshing = true,
                error = null,
                configured = true,
            ),
        )
    }

    @Test
    fun refreshFailureWithOldQueueIsLoadedWithError() {
        assertEquals(
            QueueLoadState.LoadedWithError,
            QueueUiStatePolicy.loadState(
                queue = snapshot(running = listOf(runningItem)),
                refreshing = false,
                error = "offline",
                configured = true,
            ),
        )
    }

    @Test
    fun emptyQueueIsEmpty() {
        assertEquals(
            QueueLoadState.Empty,
            QueueUiStatePolicy.loadState(
                queue = snapshot(),
                refreshing = false,
                error = null,
                configured = true,
            ),
        )
    }

    @Test
    fun runningQueueIsLoaded() {
        assertEquals(
            QueueLoadState.Loaded,
            QueueUiStatePolicy.loadState(
                queue = snapshot(running = listOf(runningItem), pending = listOf(pendingItem)),
                refreshing = false,
                error = null,
                configured = true,
            ),
        )
    }
}
