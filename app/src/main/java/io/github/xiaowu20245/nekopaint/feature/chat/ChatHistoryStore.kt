package io.github.xiaowu20245.nekopaint.feature.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val Context.aiChatHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_chat_history",
)

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val promptId: String?,
    val updatedAt: Long,
    val messageCount: Int,
    val pinned: Boolean = false,
    val mode: ChatMode = ChatMode.CHAT,
    val revision: Long = 0L,
)

sealed interface ChatSessionSaveResult {
    data class Saved(val revision: Long) : ChatSessionSaveResult
    data class Unchanged(val revision: Long) : ChatSessionSaveResult
    data class Rejected(val reason: String) : ChatSessionSaveResult
    data class Failed(val message: String) : ChatSessionSaveResult
}

data class ChatSessionSaveOutcome(
    val encoded: String?,
    val result: ChatSessionSaveResult,
)

/**
 * Neko I 多会话持久化。
 *
 * 每个会话独立保存文字、错误标记和工具调用摘要：
 * - 不保存图片 URI 或 Base64，避免权限失效和体积膨胀；
 * - 不保存系统提示、工具返回正文或完整错误堆栈；
 * - 每个会话最多 100 条，总计最多 30 个会话。
 *
 * 兼容早期的「单会话」和「全局 + 按任务」两种存储格式。
 */
class ChatHistoryStore(
    context: Context,
    private val json: Json,
) : ChatSessionStore {

    private val dataStore = context.applicationContext.aiChatHistoryDataStore
    private val mutex = Mutex()

    override suspend fun listSessions(): List<ChatSessionSummary> = mutex.withLock {
        val raw = dataStore.data.first()[SessionsKey]
        ChatHistoryCodec.listSessions(json, raw)
    }

    override suspend fun loadSession(
        sessionId: String,
        activeProposalIds: Set<String>,
    ): List<ChatUiMessage> = mutex.withLock {
        val raw = dataStore.data.first()[SessionsKey]
        ChatHistoryCodec.loadSession(json, raw, sessionId, activeProposalIds)
    }

    override suspend fun maxRevision(): Long = mutex.withLock {
        ChatHistoryCodec.maxRevision(json, dataStore.data.first()[SessionsKey])
    }

    override suspend fun saveSession(
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
        revision: Long,
    ): ChatSessionSaveResult = saveSessionInternal(
        sessionId = sessionId,
        promptId = promptId,
        title = title,
        mode = mode,
        messages = messages,
        revision = revision,
    )

    override suspend fun saveSessionWithNextRevision(
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
    ): ChatSessionSaveResult = saveSessionInternal(
        sessionId = sessionId,
        promptId = promptId,
        title = title,
        mode = mode,
        messages = messages,
        revision = null,
    )

    private suspend fun saveSessionInternal(
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
        revision: Long?,
    ): ChatSessionSaveResult {
        var result: ChatSessionSaveResult =
            ChatSessionSaveResult.Failed("会话保存失败")
        runCatching {
            mutex.withLock {
                dataStore.edit { preferences ->
                    // 由写锁内分配 revision，避免旧 ViewModel 的本地计数器过期。
                    val effectiveRevision = revision
                        ?: (ChatHistoryCodec.maxRevision(json, preferences[SessionsKey]) + 1L)
                    val outcome = ChatHistoryCodec.saveSessionWithResult(
                        json = json,
                        existingRaw = preferences[SessionsKey],
                        sessionId = sessionId,
                        promptId = promptId,
                        title = title,
                        mode = mode,
                        messages = messages,
                        revision = effectiveRevision,
                    )
                    if (outcome.encoded == null) {
                        preferences.remove(SessionsKey)
                    } else {
                        preferences[SessionsKey] = outcome.encoded
                    }
                    result = outcome.result
                }
            }
        }.onFailure { throwable ->
            result = ChatSessionSaveResult.Failed(
                throwable.message ?: "会话保存失败",
            )
        }
        return result
    }

    override suspend fun deleteSession(sessionId: String): ChatSessionSaveResult {
        var result: ChatSessionSaveResult =
            ChatSessionSaveResult.Failed("会话删除失败")
        runCatching {
            mutex.withLock {
                dataStore.edit { preferences ->
                    val outcome = ChatHistoryCodec.deleteSessionWithResult(
                        json = json,
                        existingRaw = preferences[SessionsKey],
                        sessionId = sessionId,
                    )
                    if (outcome.encoded == null) {
                        preferences.remove(SessionsKey)
                    } else {
                        preferences[SessionsKey] = outcome.encoded
                    }
                    result = outcome.result
                }
            }
        }.onFailure { throwable ->
            result = ChatSessionSaveResult.Failed(
                throwable.message ?: "会话删除失败",
            )
        }
        return result
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        mutex.withLock {
            dataStore.edit { preferences ->
                val encoded = ChatHistoryCodec.renameSession(
                    json = json,
                    existingRaw = preferences[SessionsKey],
                    sessionId = sessionId,
                    title = title,
                )
                if (encoded != null) {
                    preferences[SessionsKey] = encoded
                }
            }
        }
    }

    override suspend fun setPinned(sessionId: String, pinned: Boolean) {
        mutex.withLock {
            dataStore.edit { preferences ->
                val encoded = ChatHistoryCodec.setPinned(
                    json = json,
                    existingRaw = preferences[SessionsKey],
                    sessionId = sessionId,
                    pinned = pinned,
                )
                if (encoded != null) {
                    preferences[SessionsKey] = encoded
                }
            }
        }
    }

    private companion object {
        val SessionsKey = stringPreferencesKey("conversation_json")
    }
}

internal object ChatHistoryCodec {

    fun listSessions(json: Json, raw: String?): List<ChatSessionSummary> =
        decodeSessions(json, raw)
            .filterNot { it.deleted }
            .sortedWith(
                compareByDescending<StoredSession> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
            .map { session ->
                ChatSessionSummary(
                    id = session.id,
                    title = session.title.ifBlank {
                        deriveTitle(session.messages, session.promptId)
                    },
                    promptId = session.promptId,
                    updatedAt = session.updatedAt,
                    messageCount = session.messages.size,
                    pinned = session.pinned,
                    mode = session.mode,
                    revision = session.revision,
                )
            }

    fun maxRevision(json: Json, raw: String?): Long =
        decodeSessions(json, raw).maxOfOrNull { session ->
            maxOf(session.revision, session.deleteGeneration)
        } ?: 0L

    fun loadSession(
        json: Json,
        raw: String?,
        sessionId: String,
        activeProposalIds: Set<String> = emptySet(),
    ): List<ChatUiMessage> {
        val session = decodeSessions(json, raw)
            .firstOrNull { it.id == sessionId && !it.deleted }
            ?: return emptyList()
        return session.messages.mapIndexedNotNull { index, message ->
            val role = ChatRole.entries.firstOrNull { it.name == message.role }
                ?: return@mapIndexedNotNull null
            if (message.text.isBlank()) return@mapIndexedNotNull null
            ChatUiMessage(
                id = index + 1L,
                role = role,
                text = message.text,
                imageId = message.imageId,
                isError = message.isError,
                toolActivities = message.toolActivities,
                actionProposals = message.actionProposals.map { proposal ->
                    // 活跃动作即使尚未落盘为 EXECUTING，也要在新实例里保持一致；
                    // 进程死亡且没有登记时才降级为未知。
                    val restored = when {
                        proposal.id in activeProposalIds -> proposal.copy(
                            state = ChatActionState.EXECUTING,
                            resultMessage = null,
                        )

                        proposal.state == ChatActionState.EXECUTING -> proposal.copy(
                            state = ChatActionState.FAILED,
                            resultMessage = "上次操作状态未知，请检查队列后再试",
                        )

                        else -> proposal
                    }
                    restored.copy(presetSaving = false)
                },
                historyImages = message.historyImages,
            )
        }
    }

    fun saveSession(
        json: Json,
        existingRaw: String?,
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
        revision: Long = -1L,
    ): String? {
        val existing = decodeSessions(json, existingRaw).firstOrNull { it.id == sessionId }
        val effectiveRevision = if (revision < 0) {
            maxOf(existing?.revision ?: 0L, existing?.deleteGeneration ?: 0L) + 1L
        } else {
            revision
        }
        return saveSessionWithResult(
            json = json,
            existingRaw = existingRaw,
            sessionId = sessionId,
            promptId = promptId,
            title = title,
            mode = mode,
            messages = messages,
            revision = effectiveRevision,
        ).encoded
    }

    fun saveSessionWithResult(
        json: Json,
        existingRaw: String?,
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
        revision: Long,
    ): ChatSessionSaveOutcome {        val sessions = decodeSessions(json, existingRaw).toMutableList()
        val storedMessages = messages
            .filter { it.text.isNotBlank() }
            .takeLast(MAX_STORED_MESSAGES)
            .map { message ->
                StoredChatMessage(
                    role = message.role.name,
                    text = message.text,
                    imageId = message.imageId,
                    isError = message.isError,
                    toolActivities = message.toolActivities,
                    actionProposals = message.actionProposals,
                    historyImages = message.historyImages,
                )
            }
        val existingIndex = sessions.indexOfFirst { it.id == sessionId }
        val existing = sessions.getOrNull(existingIndex)
        if (revision <= 0) {
            return ChatSessionSaveOutcome(
                encoded = existingRaw,
                result = ChatSessionSaveResult.Rejected("revision 必须大于 0"),
            )
        }
        if (existing?.deleted == true) {
            // 会话 ID 不复用：tombstone 之后任何旧会话保存都不能复活。
            return ChatSessionSaveOutcome(
                encoded = existingRaw,
                result = ChatSessionSaveResult.Rejected("会话已删除"),
            )
        }
        if (existing != null && revision < existing.revision) {
            return ChatSessionSaveOutcome(
                encoded = existingRaw,
                result = ChatSessionSaveResult.Rejected("会话已有更新的版本"),
            )
        }
        if (existing != null && revision == existing.revision) {
            val sameContent = existing.promptId == promptId &&
                existing.mode == mode &&
                existing.messages == storedMessages
            return ChatSessionSaveOutcome(
                encoded = existingRaw,
                result = if (sameContent) {
                    ChatSessionSaveResult.Unchanged(revision)
                } else {
                    ChatSessionSaveResult.Rejected("同一 revision 内容不一致")
                },
            )
        }
        if (storedMessages.isEmpty()) {
            if (existingIndex < 0) {
                return ChatSessionSaveOutcome(
                    encoded = existingRaw,
                    result = ChatSessionSaveResult.Unchanged(0L),
                )
            }
            val generation = maxOf(revision, existing?.revision ?: 0L) + 1L
            sessions[existingIndex] = existing!!.copy(
                deleted = true,
                messages = emptyList(),
                deleteGeneration = generation,
                revision = generation,
                updatedAt = System.currentTimeMillis(),
            )
            return ChatSessionSaveOutcome(
                encoded = encodeSessions(json, sessions),
                result = ChatSessionSaveResult.Saved(generation),
            )
        }
        val resolvedTitle = existing?.title?.takeIf { it.isNotBlank() }
            ?: title.takeIf { it.isNotBlank() }
            ?: deriveTitle(storedMessages, promptId)
        val stored = StoredSession(
            id = sessionId,
            promptId = promptId,
            title = resolvedTitle,
            pinned = existing?.pinned ?: false,
            mode = mode,
            updatedAt = System.currentTimeMillis(),
            messages = storedMessages,
            revision = revision,
            deleted = false,
            deleteGeneration = existing?.deleteGeneration ?: 0L,
        )
        if (existingIndex >= 0) {
            sessions[existingIndex] = stored
        } else {
            sessions += stored
        }
        return ChatSessionSaveOutcome(
            encoded = encodeSessions(json, sessions),
            result = ChatSessionSaveResult.Saved(revision),
        )
    }

    fun renameSession(
        json: Json,
        existingRaw: String?,
        sessionId: String,
        title: String,
    ): String? {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return existingRaw
        val sessions = decodeSessions(json, existingRaw).map { session ->
            if (session.id == sessionId && !session.deleted) {
                session.copy(
                    title = trimmed,
                    revision = session.revision + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                session
            }
        }
        return encodeSessions(json, sessions)
    }

    fun setPinned(
        json: Json,
        existingRaw: String?,
        sessionId: String,
        pinned: Boolean,
    ): String? {
        val sessions = decodeSessions(json, existingRaw).map { session ->
            if (session.id == sessionId && !session.deleted) {
                session.copy(
                    pinned = pinned,
                    revision = session.revision + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            } else {
                session
            }
        }
        return encodeSessions(json, sessions)
    }

    fun deleteSession(json: Json, existingRaw: String?, sessionId: String): String? =
        deleteSessionWithResult(json, existingRaw, sessionId).encoded

    fun deleteSessionWithResult(
        json: Json,
        existingRaw: String?,
        sessionId: String,
    ): ChatSessionSaveOutcome {
        val sessions = decodeSessions(json, existingRaw).toMutableList()
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) {
            return ChatSessionSaveOutcome(
                encoded = existingRaw,
                result = ChatSessionSaveResult.Unchanged(0L),
            )
        }
        val existing = sessions[index]
        val generation = maxOf(
            existing.revision,
            existing.deleteGeneration,
        ) + 1L
        val tombstone = existing.copy(
            deleted = true,
            messages = emptyList(),
            deleteGeneration = generation,
            revision = generation,
            updatedAt = System.currentTimeMillis(),
        )
        sessions[index] = tombstone
        return ChatSessionSaveOutcome(
            encoded = encodeSessions(json, sessions),
            result = ChatSessionSaveResult.Saved(generation),
        )
    }

    private fun decodeSessions(json: Json, raw: String?): List<StoredSession> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyList()
        return when {
            root.containsKey("sessions") -> runCatching {
                json.decodeFromString<StoredSessions>(raw)
            }.getOrNull()?.sessions.orEmpty()

            root.containsKey("items") -> runCatching {
                json.decodeFromString<LegacyConversations>(raw)
            }.getOrNull()?.items.orEmpty().map { (key, conversation) ->
                val promptId = if (key == LEGACY_GLOBAL_KEY) {
                    null
                } else {
                    conversation.promptId ?: key
                }
                conversation.toSession(
                    id = if (key == LEGACY_GLOBAL_KEY) "legacy-global" else key,
                    promptId = promptId,
                )
            }

            else -> runCatching {
                json.decodeFromString<LegacyConversation>(raw)
            }.getOrNull()?.let { conversation ->
                listOf(
                    conversation.toSession(
                        id = conversation.promptId?.let { "legacy-$it" } ?: "legacy-global",
                        promptId = conversation.promptId,
                    ),
                )
            }.orEmpty()
        }
    }

    private fun encodeSessions(json: Json, sessions: List<StoredSession>): String? {
        if (sessions.isEmpty()) return null
        // tombstone 不挤占正常会话名额；保留最近一批用于拒绝在途旧保存。
        val active = sessions.filterNot { it.deleted }
            .sortedWith(
                compareByDescending<StoredSession> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
            .take(MAX_STORED_SESSIONS)
        val tombstones = sessions.filter { it.deleted }
            .sortedByDescending { maxOf(it.deleteGeneration, it.revision) }
            .take(MAX_STORED_TOMBSTONES)
        val trimmed = (active + tombstones).sortedWith(
            compareByDescending<StoredSession> { it.pinned }
                .thenByDescending { it.updatedAt },
        )
        return json.encodeToString(StoredSessions(sessions = trimmed))
    }

    private fun LegacyConversation.toSession(id: String, promptId: String?): StoredSession =
        StoredSession(
            id = id,
            promptId = promptId,
            title = deriveTitle(messages, promptId),
            updatedAt = updatedAt,
            messages = messages,
        )

    private fun deriveTitle(messages: List<StoredChatMessage>, promptId: String?): String {
        val firstUserMessage = messages.firstOrNull {
            it.role == ChatRole.USER.name && it.text.isNotBlank()
        }?.text
        return firstUserMessage
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.take(28)
            ?.takeIf { it.isNotBlank() }
            ?: promptId?.let { "任务 ${it.take(8)}" }
            ?: "新会话"
    }

    private const val LEGACY_GLOBAL_KEY = "__global__"
    private const val MAX_STORED_MESSAGES = 100
    private const val MAX_STORED_SESSIONS = 30
    private const val MAX_STORED_TOMBSTONES = 30
}

@Serializable
private data class StoredSessions(
    val version: Int = 3,
    val sessions: List<StoredSession> = emptyList(),
)

@Serializable
private data class StoredSession(
    val id: String,
    val promptId: String? = null,
    val title: String = "",
    val pinned: Boolean = false,
    val mode: ChatMode = ChatMode.CHAT,
    val updatedAt: Long = 0L,
    val messages: List<StoredChatMessage> = emptyList(),
    val revision: Long = 0L,
    val deleted: Boolean = false,
    val deleteGeneration: Long = 0L,
)

@Serializable
private data class StoredChatMessage(
    val role: String,
    val text: String,
    val imageId: String? = null,
    val isError: Boolean = false,
    val toolActivities: List<ChatToolActivity> = emptyList(),
    val actionProposals: List<ChatActionProposal> = emptyList(),
    val historyImages: List<ChatHistoryImage> = emptyList(),
)

@Serializable
private data class LegacyConversations(
    val items: Map<String, LegacyConversation> = emptyMap(),
)

@Serializable
private data class LegacyConversation(
    val promptId: String? = null,
    val updatedAt: Long = 0L,
    val messages: List<StoredChatMessage> = emptyList(),
)
