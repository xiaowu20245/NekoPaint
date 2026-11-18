package io.github.xiaowu20245.nekopaint.feature.chat

/** 动作身份：sessionId + proposalId；旧数据 sessionId 为空时按当前会话兼容。 */
internal object ChatActionLocator {

    data class Located(
        val message: ChatUiMessage,
        val proposal: ChatActionProposal,
    )

    fun find(
        messages: List<ChatUiMessage>,
        sessionId: String?,
        proposalId: String,
    ): Located? {
        messages.forEach { message ->
            val proposal = message.actionProposals.firstOrNull { candidate ->
                candidate.id == proposalId &&
                    (candidate.sessionId == null || candidate.sessionId == sessionId)
            }
            if (proposal != null) return Located(message, proposal)
        }
        return null
    }
}
