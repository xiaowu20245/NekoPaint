package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import io.github.xiaowu20245.nekopaint.core.network.AiToolDefinition

/**
 * ChatViewModel 与 feature 内运行时之间的契约。
 *
 * 生产环境直接使用现有实现；JVM 测试用可挂起的 fake 替换，
 * 以覆盖保存、会话切换和后台动作的并发窗口。
 */
interface ChatContextSource {
    suspend fun build(intent: ChatIntent, userText: String): String?

    fun taskContext(promptId: String): ChatTaskContext?
}

interface ChatToolRuntime {
    fun definitions(mode: ChatMode): List<AiToolDefinition>

    suspend fun execute(
        call: AiToolCall,
        fallbackPromptId: String? = null,
        attachments: List<ChatImageAttachment> = emptyList(),
        direct: Boolean = false,
    ): ChatToolExecution

    fun describe(call: AiToolCall, fallbackPromptId: String? = null): ChatToolActivity
}

interface ChatSessionStore {
    suspend fun listSessions(): List<ChatSessionSummary>

    suspend fun loadSession(
        sessionId: String,
        activeProposalIds: Set<String> = emptySet(),
    ): List<ChatUiMessage>

    suspend fun maxRevision(): Long

    suspend fun saveSession(
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
        revision: Long,
    ): ChatSessionSaveResult

    /**
     * 由 store 在写锁内分配下一个 revision。
     *
     * 用于后台动作结果写回：不能使用旧 ViewModel 的本地计数器，
     * 否则 rename/pin 或新实例保存后会产生 revision 冲突。
     */
    suspend fun saveSessionWithNextRevision(
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
    ): ChatSessionSaveResult

    suspend fun deleteSession(sessionId: String): ChatSessionSaveResult

    suspend fun renameSession(sessionId: String, title: String)

    suspend fun setPinned(sessionId: String, pinned: Boolean)
}

interface ChatActionExecutor {
    suspend fun execute(proposal: ChatActionProposal): Result<String>

    suspend fun savePreset(proposal: ChatActionProposal, name: String): Result<String>
}
