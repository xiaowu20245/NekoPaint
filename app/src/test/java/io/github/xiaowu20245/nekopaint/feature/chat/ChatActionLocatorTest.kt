package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatActionLocatorTest {

    @Test
    fun findsProposalBySessionAndProposalId() {
        val message = message(
            proposal(sessionId = "session-a", id = "proposal-1"),
            proposal(sessionId = "session-b", id = "proposal-2"),
        )

        val located = ChatActionLocator.find(
            messages = listOf(message),
            sessionId = "session-b",
            proposalId = "proposal-2",
        )

        assertEquals("proposal-2", located?.proposal?.id)
        assertNull(
            ChatActionLocator.find(listOf(message), "session-a", "proposal-2"),
        )
    }

    @Test
    fun legacyProposalWithoutSessionMatchesCurrentSession() {
        val message = message(proposal(sessionId = null, id = "legacy"))

        assertEquals(
            "legacy",
            ChatActionLocator.find(listOf(message), "current", "legacy")?.proposal?.id,
        )
    }

    private fun message(vararg proposals: ChatActionProposal) = ChatUiMessage(
        id = 1L,
        role = ChatRole.ASSISTANT,
        text = "message",
        actionProposals = proposals.toList(),
    )

    private fun proposal(sessionId: String?, id: String) = ChatActionProposal(
        id = id,
        sessionId = sessionId,
        action = ChatTaskAction.RETRY,
        promptId = "p1",
        title = "task",
        status = "failed",
    )
}
