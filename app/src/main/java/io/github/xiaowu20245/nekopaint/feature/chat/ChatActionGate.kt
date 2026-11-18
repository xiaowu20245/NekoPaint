package io.github.xiaowu20245.nekopaint.feature.chat

/**
 * v1 动作执行门禁：发送中或存在 EXECUTING proposal 时禁止
 * new/switch/delete 会话与切换 Chat/Agent 模式。
 */
internal object ChatActionGate {

    fun hasExecutingAction(messages: List<ChatUiMessage>): Boolean =
        messages.any { message ->
            message.actionProposals.any { it.state == ChatActionState.EXECUTING }
        }

    fun canMutateSession(sending: Boolean, messages: List<ChatUiMessage>): Boolean =
        !sending && !hasExecutingAction(messages)

    fun canSwitchMode(sending: Boolean, messages: List<ChatUiMessage>): Boolean =
        !sending && !hasExecutingAction(messages)
}
