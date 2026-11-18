package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionGateTest {

    @Test
    fun executingActionBlocksSessionMutationAndModeSwitch() {
        val messages = listOf(message(ChatActionState.EXECUTING))

        assertFalse(ChatActionGate.canMutateSession(false, messages))
        assertFalse(ChatActionGate.canSwitchMode(false, messages))
    }

    @Test
    fun pendingSuccessFailedDoNotBlock() {
        listOf(
            ChatActionState.PENDING,
            ChatActionState.SUCCESS,
            ChatActionState.FAILED,
            ChatActionState.CANCELLED,
        ).forEach { state ->
            val messages = listOf(message(state))
            assertTrue(ChatActionGate.canMutateSession(false, messages))
            assertTrue(ChatActionGate.canSwitchMode(false, messages))
        }
    }

    @Test
    fun sendingBlocksEvenWithoutAction() {
        val messages = listOf(message(ChatActionState.SUCCESS))

        assertFalse(ChatActionGate.canMutateSession(true, messages))
        assertFalse(ChatActionGate.canSwitchMode(true, messages))
    }

    private fun message(state: ChatActionState) = ChatUiMessage(
        id = 1L,
        role = ChatRole.ASSISTANT,
        text = "message",
        actionProposals = listOf(
            ChatActionProposal(
                id = "proposal-1",
                action = ChatTaskAction.RETRY,
                promptId = "p1",
                title = "task",
                status = "failed",
                state = state,
            ),
        ),
    )
}
