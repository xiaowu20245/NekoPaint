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
) {

    private val dataStore = context.applicationContext.aiChatHistoryDataStore
    private val mutex = Mutex()

    suspend fun listSessions(): List<ChatSessionSummary> = mutex.withLock {
        val raw = dataStore.data.first()[SessionsKey]
        ChatHistoryCodec.listSessions(json, raw)
    }

    suspend fun loadSession(sessionId: String): List<ChatUiMessage> = mutex.withLock {
        val raw = dataStore.data.first()[SessionsKey]
        ChatHistoryCodec.loadSession(json, raw, sessionId)
    }

    suspend fun saveSession(
        sessionId: String,
        promptId: String?,
        title: String,
        mode: ChatMode,
        messages: List<ChatUiMessage>,
    ) {
        mutex.withLock {
            dataStore.edit { preferences ->
                val encoded = ChatHistoryCodec.saveSession(
                    json = json,
                    existingRaw = preferences[SessionsKey],
                    sessionId = sessionId,
                    promptId = promptId,
                    title = title,
                    mode = mode,
                    messages = messages,
                )
                if (encoded == null) {
                    preferences.remove(SessionsKey)
                } else {
                    preferences[SessionsKey] = encoded
                }
            }
        }
    }

    suspend fun deleteSession(sessionId: String) {
        mutex.withLock {
            dataStore.edit { preferences ->
                val encoded = ChatHistoryCodec.deleteSession(
                    json = json,
                    existingRaw = preferences[SessionsKey],
                    sessionId = sessionId,
                )
                if (encoded == null) {
                    preferences.remove(SessionsKey)
                } else {
                    preferences[SessionsKey] = encoded
                }
            }
        }
    }

    suspend fun renameSession(sessionId: String, title: String) {
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

    suspend fun setPinned(sessionId: String, pinned: Boolean) {
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
                )
            }

    fun loadSession(json: Json, raw: String?, sessionId: String): List<ChatUiMessage> {
        val session = decodeSessions(json, raw).firstOrNull { it.id == sessionId }
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
                    val restored = if (proposal.state == ChatActionState.EXECUTING) {
                        proposal.copy(
                            state = ChatActionState.FAILED,
                            resultMessage = "上次操作状态未知，请检查队列后再试",
                        )
                    } else {
                        proposal
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
    ): String? {
        val sessions = decodeSessions(json, existingRaw).toMutableList()
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
        if (storedMessages.isEmpty()) {
            if (existingIndex >= 0) sessions.removeAt(existingIndex)
        } else {
            val existing = sessions.getOrNull(existingIndex)
            val stored = StoredSession(
                id = sessionId,
                promptId = promptId,
                title = existing?.title?.takeIf { it.isNotBlank() }
                    ?: title.takeIf { it.isNotBlank() }
                    ?: deriveTitle(storedMessages, promptId),
                pinned = existing?.pinned ?: false,
                mode = mode,
                updatedAt = System.currentTimeMillis(),
                messages = storedMessages,
            )
            if (existingIndex >= 0) {
                sessions[existingIndex] = stored
            } else {
                sessions += stored
            }
        }
        return encodeSessions(json, sessions)
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
            if (session.id == sessionId) session.copy(title = trimmed) else session
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
            if (session.id == sessionId) session.copy(pinned = pinned) else session
        }
        return encodeSessions(json, sessions)
    }

    fun deleteSession(json: Json, existingRaw: String?, sessionId: String): String? {
        val sessions = decodeSessions(json, existingRaw)
            .filterNot { it.id == sessionId }
        return encodeSessions(json, sessions)
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
        val trimmed = sessions
            .sortedWith(
                compareByDescending<StoredSession> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
            .take(MAX_STORED_SESSIONS)
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
