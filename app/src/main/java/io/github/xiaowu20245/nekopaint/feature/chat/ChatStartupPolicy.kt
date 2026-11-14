package io.github.xiaowu20245.nekopaint.feature.chat

/**
 * Neko I 打开时的会话选择策略。
 *
 * 每次打开都从空白新对话开始，不自动恢复任何历史会话；
 * 历史会话由用户从会话列表手动选择。新对话默认 Chat 模式。
 */
internal object ChatStartupPolicy {

    @Suppress("UNUSED_PARAMETER")
    fun initialSession(
        contextPromptId: String?,
        sessions: List<ChatSessionSummary>,
    ): ChatSessionSummary? {
        // contextPromptId 只用于注入任务上下文，不用于自动续接旧会话。
        return null
    }

    fun initialMode(session: ChatSessionSummary?): ChatMode =
        session?.mode ?: ChatMode.CHAT
}
