package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryCodecRevisionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun newerRevisionWinsAndOldRevisionIsRejected() {
        val first = save(raw = null, revision = 1, text = "hello")
        assertTrue(first.result is ChatSessionSaveResult.Saved)
        val second = save(raw = first.encoded, revision = 2, text = "updated")
        assertTrue(second.result is ChatSessionSaveResult.Saved)

        val stale = save(raw = second.encoded, revision = 1, text = "stale")
        assertTrue(stale.result is ChatSessionSaveResult.Rejected)
        assertEquals(
            "updated",
            ChatHistoryCodec.loadSession(json, second.encoded, SESSION_ID).single().text,
        )
    }

    @Test
    fun sameRevisionIsIdempotentOnlyForSameContent() {
        val first = save(raw = null, revision = 3, text = "same")
        val same = save(raw = first.encoded, revision = 3, text = "same")
        val conflict = save(raw = first.encoded, revision = 3, text = "different")

        assertTrue(same.result is ChatSessionSaveResult.Unchanged)
        assertTrue(conflict.result is ChatSessionSaveResult.Rejected)
    }

    @Test
    fun deleteCreatesTombstoneAndRejectsInFlightSave() {
        val first = save(raw = null, revision = 5, text = "hello")
        val deleted = ChatHistoryCodec.deleteSessionWithResult(
            json,
            first.encoded,
            SESSION_ID,
        )

        assertTrue(deleted.result is ChatSessionSaveResult.Saved)
        assertTrue(ChatHistoryCodec.listSessions(json, deleted.encoded).isEmpty())
        assertTrue(
            ChatHistoryCodec.loadSession(json, deleted.encoded, SESSION_ID).isEmpty(),
        )
        assertTrue(ChatHistoryCodec.maxRevision(json, deleted.encoded) >= 6L)

        val stale = save(raw = deleted.encoded, revision = 5, text = "resurrect")
        assertTrue(stale.result is ChatSessionSaveResult.Rejected)
    }

    @Test
    fun revisionSurvivesRoundTripForListAndLoad() {
        val saved = save(raw = null, revision = 7, text = "hello")
        val summary = ChatHistoryCodec.listSessions(json, saved.encoded).single()
        val loaded = ChatHistoryCodec.loadSession(json, saved.encoded, SESSION_ID)

        assertEquals(7L, summary.revision)
        assertEquals("hello", loaded.single().text)
        assertEquals(7L, ChatHistoryCodec.maxRevision(json, saved.encoded))
    }

    @Test
    fun activeProposalIdsKeepExecutingStateAndOthersBecomeUnknown() {
        val proposal = ChatActionProposal(
            id = "proposal-1",
            sessionId = SESSION_ID,
            action = ChatTaskAction.RETRY,
            promptId = "task-1",
            title = "task",
            status = "failed",
            state = ChatActionState.EXECUTING,
        )
        val saved = ChatHistoryCodec.saveSessionWithResult(
            json = json,
            existingRaw = null,
            sessionId = SESSION_ID,
            promptId = null,
            title = "Session",
            mode = ChatMode.AGENT,
            messages = listOf(
                ChatUiMessage(
                    id = 1L,
                    role = ChatRole.ASSISTANT,
                    text = "confirm",
                    actionProposals = listOf(proposal),
                ),
            ),
            revision = 1L,
        )

        val kept = ChatHistoryCodec.loadSession(
            json = json,
            raw = saved.encoded,
            sessionId = SESSION_ID,
            activeProposalIds = setOf("proposal-1"),
        ).single().actionProposals.single()
        val orphaned = ChatHistoryCodec.loadSession(
            json = json,
            raw = saved.encoded,
            sessionId = SESSION_ID,
        ).single().actionProposals.single()

        assertEquals(ChatActionState.EXECUTING, kept.state)
        assertEquals(ChatActionState.FAILED, orphaned.state)
        assertTrue(orphaned.resultMessage.orEmpty().isNotBlank())
    }

    @Test
    fun activePendingProposalIsPromotedToExecuting() {
        val proposal = ChatActionProposal(
            id = "proposal-pending",
            sessionId = SESSION_ID,
            action = ChatTaskAction.RETRY,
            promptId = "task-1",
            title = "task",
            status = "failed",
            state = ChatActionState.PENDING,
        )
        val saved = ChatHistoryCodec.saveSessionWithResult(
            json = json,
            existingRaw = null,
            sessionId = SESSION_ID,
            promptId = null,
            title = "Session",
            mode = ChatMode.AGENT,
            messages = listOf(
                ChatUiMessage(
                    id = 1L,
                    role = ChatRole.ASSISTANT,
                    text = "confirm",
                    actionProposals = listOf(proposal),
                ),
            ),
            revision = 1L,
        )

        val promoted = ChatHistoryCodec.loadSession(
            json = json,
            raw = saved.encoded,
            sessionId = SESSION_ID,
            activeProposalIds = setOf("proposal-pending"),
        ).single().actionProposals.single()
        val untouched = ChatHistoryCodec.loadSession(
            json = json,
            raw = saved.encoded,
            sessionId = SESSION_ID,
        ).single().actionProposals.single()

        assertEquals(ChatActionState.EXECUTING, promoted.state)
        assertNull(promoted.resultMessage)
        assertEquals(ChatActionState.PENDING, untouched.state)
    }

    @Test
    fun tombstonesDoNotEvictLiveSessionsFromStorage() {
        var raw = ChatHistoryCodec.saveSessionWithResult(
            json = json,
            existingRaw = null,
            sessionId = "live",
            promptId = null,
            title = "Live",
            mode = ChatMode.CHAT,
            messages = listOf(message("live")),
            revision = 1L,
        ).encoded

        repeat(MAX_STORED_SESSIONS) { index ->
            val sessionId = "tombstone-$index"
            raw = ChatHistoryCodec.saveSessionWithResult(
                json = json,
                existingRaw = raw,
                sessionId = sessionId,
                promptId = null,
                title = sessionId,
                mode = ChatMode.CHAT,
                messages = listOf(message("deleted")),
                revision = 1L,
            ).encoded
            raw = ChatHistoryCodec.deleteSessionWithResult(
                json = json,
                existingRaw = raw,
                sessionId = sessionId,
            ).encoded
        }

        val live = ChatHistoryCodec.listSessions(json, raw)
        assertEquals(1, live.size)
        assertEquals("live", live.single().id)
    }

    private fun save(
        raw: String?,
        revision: Long,
        text: String,
    ) = ChatHistoryCodec.saveSessionWithResult(
        json = json,
        existingRaw = raw,
        sessionId = SESSION_ID,
        promptId = null,
        title = "Session",
        mode = ChatMode.CHAT,
        messages = listOf(message(text)),
        revision = revision,
    )

    private fun message(text: String) = ChatUiMessage(
        id = 1L,
        role = ChatRole.USER,
        text = text,
    )

    private companion object {
        const val SESSION_ID = "session-1"
        const val MAX_STORED_SESSIONS = 30
    }
}
