package io.github.xiaowu20245.nekopaint.feature.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryStoreInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val store = ChatHistoryStore(context, json)

    @Test
    fun revisionAndTombstoneRoundTripThroughDataStore() = runBlocking {
        val sessionId = "instrumented-${System.nanoTime()}"
        val first = store.saveSession(
            sessionId = sessionId,
            promptId = null,
            title = "Session",
            mode = ChatMode.CHAT,
            messages = listOf(message("first")),
            revision = 1L,
        )
        assertTrue(first is ChatSessionSaveResult.Saved)
        val second = store.saveSession(
            sessionId = sessionId,
            promptId = null,
            title = "Session",
            mode = ChatMode.CHAT,
            messages = listOf(message("second")),
            revision = 2L,
        )
        assertTrue(second is ChatSessionSaveResult.Saved)
        assertEquals(
            "second",
            store.loadSession(sessionId).single().text,
        )
        assertEquals(2L, store.listSessions().first { it.id == sessionId }.revision)
        assertTrue(store.maxRevision() >= 2L)

        val deleted = store.deleteSession(sessionId)
        assertTrue(deleted is ChatSessionSaveResult.Saved)
        assertTrue(store.listSessions().none { it.id == sessionId })
        assertTrue(store.loadSession(sessionId).isEmpty())

        val resurrect = store.saveSession(
            sessionId = sessionId,
            promptId = null,
            title = "Session",
            mode = ChatMode.CHAT,
            messages = listOf(message("resurrect")),
            revision = 3L,
        )
        assertTrue(resurrect is ChatSessionSaveResult.Rejected)
    }

    @Test
    fun storeAllocatedRevisionIgnoresStaleCallerCounter() = runBlocking {
        val sessionId = "instrumented-next-${System.nanoTime()}"
        store.saveSession(
            sessionId = sessionId,
            promptId = null,
            title = "Session",
            mode = ChatMode.CHAT,
            messages = listOf(message("first")),
            revision = 1L,
        )
        store.saveSession(
            sessionId = sessionId,
            promptId = null,
            title = "Session",
            mode = ChatMode.CHAT,
            messages = listOf(message("second")),
            revision = 2L,
        )

        val saved = store.saveSessionWithNextRevision(
            sessionId = sessionId,
            promptId = null,
            title = "Session",
            mode = ChatMode.CHAT,
            messages = listOf(message("action-result")),
        )

        assertTrue(saved is ChatSessionSaveResult.Saved)
        assertEquals("action-result", store.loadSession(sessionId).single().text)
        assertTrue(store.listSessions().first { it.id == sessionId }.revision > 2L)
    }

    private fun message(text: String) = ChatUiMessage(
        id = 1L,
        role = ChatRole.USER,
        text = text,
    )
}
