package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSensitiveHostProviderTest {

    @Test
    fun emitsCurrentAndUpdatedServerHosts() = runTest {
        val servers = MutableStateFlow(
            listOf(
                ServerProfile(id = "one", host = "192.168.1.10"),
                ServerProfile(id = "two", host = "comfy.local"),
            ),
        )
        val provider = ServerSensitiveHostProvider(
            servers,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        assertTrue(provider.awaitReady())

        assertEquals(setOf("192.168.1.10", "comfy.local"), provider.hosts())

        servers.value = listOf(ServerProfile(id = "three", host = "10.0.0.2"))

        assertEquals(setOf("10.0.0.2"), provider.hosts())
    }

    @Test
    fun emptyServerListIsReadyWithEmptyHosts() = runTest {
        val servers = MutableStateFlow<List<ServerProfile>>(emptyList())
        val provider = ServerSensitiveHostProvider(
            servers,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        assertTrue(provider.awaitReady())

        assertTrue(provider.hosts().isEmpty())
    }

    @Test
    fun firstEmptySnapshotReleasesWaitAndLaterHostsStillArrive() = runTest {
        val servers = MutableStateFlow<List<ServerProfile>>(emptyList())
        val provider = ServerSensitiveHostProvider(
            servers,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        // ServerConfigStore maps a complete DataStore value, so the first
        // empty snapshot is authoritative: no servers are configured yet.
        assertTrue(provider.awaitReady())
        assertTrue(provider.hosts().isEmpty())

        servers.value = listOf(ServerProfile(id = "one", host = "comfy.local"))

        assertEquals(setOf("comfy.local"), provider.hosts())
    }

    @Test
    fun awaitReadySuspendsUntilFirstEmission() = runTest {
        val servers = MutableSharedFlow<List<ServerProfile>>()
        val provider = ServerSensitiveHostProvider(
            servers,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        val ready = async(start = CoroutineStart.UNDISPATCHED) {
            provider.awaitReady()
        }
        assertFalse(ready.isCompleted)
        assertTrue(provider.hosts().isEmpty())

        servers.emit(listOf(ServerProfile(id = "one", host = "comfy.local")))

        assertTrue(ready.await())
        assertEquals(setOf("comfy.local"), provider.hosts())
    }

    @Test
    fun failingFlowMakesAwaitReadyFailClosed() = runTest {
        val provider = ServerSensitiveHostProvider(
            flow<List<ServerProfile>> { throw IOException("server config read failed") },
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        assertFalse(provider.awaitReady())
    }

    @Test
    fun completedFlowWithoutEmissionMakesAwaitReadyFailClosed() = runTest {
        val provider = ServerSensitiveHostProvider(
            flowOf<List<ServerProfile>>(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        assertFalse(provider.awaitReady())
    }

    @Test
    fun neverEmittingFlowTimesOutFailClosed() = runTest {
        val provider = ServerSensitiveHostProvider(
            MutableSharedFlow<List<ServerProfile>>(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        assertFalse(provider.awaitReady())
    }
}
