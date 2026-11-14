package io.github.xiaowu20245.nekopaint.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantClient
import io.github.xiaowu20245.nekopaint.core.network.AiChatMessage
import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageEncoder
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageStore
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

enum class ChatRole(val apiRole: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

enum class ChatStatus {
    THINKING,
    QUEUE,
    HISTORY,
    WORKFLOW,
    DIAGNOSE,
    ACTION,
    VISION,
    FINALIZE,
}

data class ChatUiMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val imageUri: Uri? = null,
    val imageId: String? = null,
    val imageDataUrl: String? = null,
    val isError: Boolean = false,
    val toolActivities: List<ChatToolActivity> = emptyList(),
    val actionProposals: List<ChatActionProposal> = emptyList(),
    val historyImages: List<ChatHistoryImage> = emptyList(),
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val sending: Boolean = false,
    val configured: Boolean = false,
    val visionConfigured: Boolean = false,
    val modelLabel: String = "",
    val status: ChatStatus? = null,
    val contextPromptId: String? = null,
    val taskContext: ChatTaskContext? = null,
    val streamingMessageId: Long? = null,
    val currentSessionId: String? = null,
    val sessions: List<ChatSessionSummary> = emptyList(),
    val mode: ChatMode = ChatMode.CHAT,
)

private data class ChatAnswer(
    val text: String,
    val toolActivities: List<ChatToolActivity> = emptyList(),
    val streamedMessageId: Long? = null,
    val actionProposals: List<ChatActionProposal> = emptyList(),
    val historyImages: List<ChatHistoryImage> = emptyList(),
)

/**
 * Chat：多轮文字对话、图片视觉分析，以及模型自主调用只读工具。
 *
 * 从历史/任务详情进入时会携带任务上下文，工具调用失败时自动回退到本地意图路由。
 */
class ChatViewModel(
    private val store: AiAssistantStore,
    private val client: AiAssistantClient,
    private val imageEncoder: ChatImageEncoder,
    private val imageStore: ChatImageStore,
    private val contextBuilder: ChatContextBuilder,
    private val toolRegistry: ChatToolRegistry,
    private val historyStore: ChatHistoryStore,
    private val actionHandler: ChatTaskActionHandler,
    private val appScope: CoroutineScope,
    contextPromptId: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var config = AiAssistantConfig()
    private var nextMessageId = 1L
    private var taskPromptId: String? = contextPromptId
    private var currentSessionId: String? = null
    private var mode = ChatMode.CHAT

    init {
        if (contextPromptId != null) {
            _state.update {
                it.copy(
                    contextPromptId = contextPromptId,
                    taskContext = currentTaskContext(),
                )
            }
        }
        viewModelScope.launch {
            val sessions = historyStore.listSessions()
            val latest = ChatStartupPolicy.initialSession(contextPromptId, sessions)
            val restored = hydrateStoredImages(
                latest?.let { historyStore.loadSession(it.id) }.orEmpty(),
            )
            // 避免与用户极快发送消息的竞态：只有当前仍是空白新对话时才应用启动策略。
            if (currentSessionId == null && _state.value.messages.isEmpty()) {
                currentSessionId = latest?.id
                mode = ChatStartupPolicy.initialMode(latest)
                nextMessageId = (restored.maxOfOrNull { it.id } ?: 0L) + 1L
                _state.update {
                    it.copy(
                        messages = restored,
                        sessions = sessions,
                        currentSessionId = currentSessionId,
                        mode = mode,
                    )
                }
            } else {
                _state.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            store.config.collect { value ->
                config = value
                _state.update {
                    it.copy(
                        configured = value.isConfigured,
                        visionConfigured = value.visionModel.isNotBlank(),
                        modelLabel = value.textModel,
                    )
                }
            }
        }
    }

    fun send(text: String, imageUri: Uri?) {
        val trimmed = text.trim()
        if (_state.value.sending || (trimmed.isEmpty() && imageUri == null)) return
        if (!config.isConfigured) {
            appendAssistant(
                text = "尚未配置 AI 助手，请先在设置中填写 Base URL 和文本模型。",
                isError = true,
            )
            return
        }

        // 历史数据可能在进入聊天后才刷新，这里同步一次关联任务信息。
        refreshTaskContext()

        val userText = if (trimmed.isEmpty()) "请分析这张图片。" else trimmed
        val imageId = if (imageUri != null) UUID.randomUUID().toString() else null
        val userMessage = ChatUiMessage(
            id = nextMessageId++,
            role = ChatRole.USER,
            text = userText,
            imageUri = imageUri,
            imageId = imageId,
        )
        _state.update {
            it.copy(messages = it.messages + userMessage, sending = true)
        }
        persistMessages()

        appScope.launch {
            val imageData = if (imageUri != null) {
                val result = imageEncoder.toDataUrl(imageUri)
                val data = result.getOrElse { error ->
                    _state.update { current ->
                        current.copy(
                            sending = false,
                            status = null,
                            messages = current.messages.map { message ->
                                if (message.id == userMessage.id) {
                                    message.copy(isError = true)
                                } else {
                                    message
                                }
                            } + ChatUiMessage(
                                id = nextMessageId++,
                                role = ChatRole.ASSISTANT,
                                text = "图片处理失败：${error.message ?: "无法读取图片"}",
                                isError = true,
                            ),
                        )
                    }
                    persistMessages()
                    return@launch
                }
                _state.update { current ->
                    current.copy(
                        messages = current.messages.map { message ->
                            if (message.id == userMessage.id) {
                                message.copy(imageDataUrl = data)
                            } else {
                                message
                            }
                        },
                    )
                }
                if (imageId != null) {
                    runCatching { imageStore.save(imageId, data) }
                }
                data
            } else {
                null
            }

            val history = _state.value.messages
                .filterNot { it.isError }
                .filter { it.text.isNotBlank() }
                .takeLast(MAX_CONTEXT_MESSAGES)
                .map { message ->
                    AiChatMessage(
                        role = message.role.apiRole,
                        content = message.text,
                        imageDataUrl = message.imageDataUrl,
                    )
                }

            answer(
                userText = userText,
                history = history,
            ).fold(
                onSuccess = { reply ->
                    val streamedId = reply.streamedMessageId
                    if (streamedId != null) {
                        finalizeAssistant(
                            id = streamedId,
                            text = reply.text.trim(),
                            toolActivities = reply.toolActivities,
                            actionProposals = reply.actionProposals,
                            historyImages = reply.historyImages,
                        )
                    } else {
                        appendAssistant(
                            text = reply.text.trim(),
                            toolActivities = reply.toolActivities,
                            actionProposals = reply.actionProposals,
                            historyImages = reply.historyImages,
                        )
                    }
                },
                onFailure = { error ->
                    appendAssistant(
                        text = "调用失败：${error.message ?: "请检查网络和模型配置"}",
                        isError = true,
                    )
                },
            )
        }
    }

    /**
     * 用户从工具面板直接调用只读工具，不经过模型决策。
     */
    fun runTool(toolName: String, label: String) {
        if (_state.value.sending) return
        if (toolName !in ChatToolPolicy.readOnlyTools) return
        val userMessage = ChatUiMessage(
            id = nextMessageId++,
            role = ChatRole.USER,
            text = "使用工具：$label",
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                sending = true,
                status = toolStatus(toolName),
            )
        }
        persistMessages()
        appScope.launch {
            val args = buildJsonObject {
                taskPromptId?.let { put("promptId", it) }
            }
            val call = AiToolCall(
                id = "manual-${UUID.randomUUID()}",
                name = toolName,
                arguments = args.toString(),
            )
            val execution = toolRegistry.execute(
                call = call,
                fallbackPromptId = taskPromptId,
                attachments = emptyList(),
                direct = true,
            )
            val activity = toolRegistry.describe(call, taskPromptId)
                .copy(isError = execution.isError)
            appendAssistant(
                text = execution.content,
                isError = execution.isError,
                toolActivities = listOf(activity),
                historyImages = execution.historyImages,
            )
        }
    }

    /**
     * 用户从图片工具面板直接调用视觉模型，不经过文本模型。
     */
    fun runImageTool(imageUri: Uri?, mode: String, question: String?) {
        if (imageUri == null || _state.value.sending) return
        val trimmedQuestion = question?.trim().orEmpty()
        val label = when (mode) {
            "tags" -> "提取这张图片的标签"
            "describe" -> "描述这张图片"
            "question" -> trimmedQuestion.takeIf { it.isNotBlank() } ?: return
            else -> return
        }
        val imageId = UUID.randomUUID().toString()
        val userMessage = ChatUiMessage(
            id = nextMessageId++,
            role = ChatRole.USER,
            text = label,
            imageUri = imageUri,
            imageId = imageId,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                sending = true,
                status = ChatStatus.VISION,
            )
        }
        persistMessages()
        appScope.launch {
            val data = imageEncoder.toDataUrl(imageUri).getOrElse { error ->
                appendAssistant(
                    text = "图片处理失败：${error.message ?: "无法读取图片"}",
                    isError = true,
                )
                return@launch
            }
            _state.update { current ->
                current.copy(
                    messages = current.messages.map { message ->
                        if (message.id == userMessage.id) {
                            message.copy(imageDataUrl = data)
                        } else {
                            message
                        }
                    },
                )
            }
            runCatching { imageStore.save(imageId, data) }
            val args = buildJsonObject {
                put("mode", mode)
                if (mode == "question") put("question", trimmedQuestion)
            }
            val call = AiToolCall(
                id = "manual-${UUID.randomUUID()}",
                name = "analyze_image",
                arguments = args.toString(),
            )
            val execution = toolRegistry.execute(
                call = call,
                fallbackPromptId = taskPromptId,
                attachments = currentImageAttachments(),
                direct = true,
            )
            val activity = toolRegistry.describe(call, taskPromptId)
                .copy(isError = execution.isError)
            appendAssistant(
                text = execution.content,
                isError = execution.isError,
                toolActivities = listOf(activity),
                historyImages = execution.historyImages,
            )
        }
    }

    private suspend fun answer(
        userText: String,
        history: List<AiChatMessage>,
    ): Result<ChatAnswer> {
        val attachments = currentImageAttachments()
        // 文本模型不直接接收图片；图片通过 analyze_image 工具按需交给视觉模型。
        val toolHistory = history.map { it.copy(imageDataUrl = null) }
        val activities = mutableListOf<ChatToolActivity>()
        val actionProposals = mutableListOf<ChatActionProposal>()
        val historyImages = mutableListOf<ChatHistoryImage>()
        val tools = toolRegistry.definitions(mode)
        val messages = mutableListOf<AiChatMessage>(
            AiChatMessage(
                role = "system",
                content = buildNekoSystemPrompt(
                    basePrompt = if (mode == ChatMode.AGENT) {
                        TOOL_SYSTEM_PROMPT_AGENT
                    } else {
                        TOOL_SYSTEM_PROMPT_CHAT
                    },
                    persona = config.persona,
                    preInstruction = config.preInstruction,
                ),
            ),
        )
        currentTaskContext()?.let { context ->
            messages += AiChatMessage(role = "system", content = taskContextPrompt(context))
        }
        if (attachments.isNotEmpty()) {
            messages += AiChatMessage(
                role = "system",
                content = imageAvailabilityPrompt(attachments),
            )
        }
        messages += toolHistory

        for (round in 0 until MAX_TOOL_ROUNDS) {
            _state.update { it.copy(status = ChatStatus.THINKING) }
            var streamedId: Long? = null
            val completion = client.chatWithToolsStream(
                config = config,
                messages = messages,
                tools = tools,
                onDelta = { delta ->
                    val id = streamedId
                        ?: beginStreamingAssistant().also { streamedId = it }
                    appendStreamingDelta(id, delta)
                },
            ).getOrElse {
                val hadStreamed = streamedId != null
                streamedId?.let(::discardStreamingAssistant)
                if (!hadStreamed) {
                    // 服务商可能不支持流式工具调用，退回一次非流式请求。
                    client.chatWithTools(
                        config = config,
                        messages = messages,
                        tools = tools,
                    ).getOrElse {
                        return legacyAnswer(userText, history).map {
                            ChatAnswer(
                                text = it,
                                toolActivities = activities.toList(),
                                actionProposals = actionProposals.toList(),
                            historyImages = historyImages.toList(),
                            )
                        }
                    }
                } else {
                    // 已经输出过内容就不重试，避免用户看到重复段落。
                    return legacyAnswer(userText, history).map {
                        ChatAnswer(
                            text = it,
                            toolActivities = activities.toList(),
                            actionProposals = actionProposals.toList(),
                            historyImages = historyImages.toList(),
                        )
                    }
                }
            }

            if (completion.toolCalls.isEmpty()) {
                val content = completion.content?.trim().orEmpty()
                return if (content.isNotBlank()) {
                    Result.success(
                        ChatAnswer(
                            text = content,
                            toolActivities = activities.toList(),
                            streamedMessageId = streamedId,
                            actionProposals = actionProposals.toList(),
                            historyImages = historyImages.toList(),
                        ),
                    )
                } else {
                    streamedId?.let(::discardStreamingAssistant)
                    legacyAnswer(userText, history).map {
                        ChatAnswer(
                            text = it,
                            toolActivities = activities.toList(),
                            actionProposals = actionProposals.toList(),
                            historyImages = historyImages.toList(),
                        )
                    }
                }
            }

            // 工具调用轮次即使先输出了少量前言，也以最终回答为准。
            streamedId?.let(::discardStreamingAssistant)
            messages += AiChatMessage(
                role = "assistant",
                content = completion.content,
                toolCalls = completion.toolCalls,
            )
            completion.toolCalls.forEach { call ->
                _state.update { it.copy(status = toolStatus(call.name)) }
                val execution = toolRegistry.execute(
                    call = call,
                    fallbackPromptId = taskPromptId,
                    attachments = attachments,
                )
                if (
                    call.name != "propose_task_action" &&
                    call.name != "propose_submit_task" &&
                    call.name != "propose_batch_task_action" &&
                    call.name != "propose_install_missing_nodes"
                ) {
                    val activity = toolRegistry.describe(call, taskPromptId)
                    activities += if (
                        call.name == "get_history_images" &&
                        execution.historyImages.isNotEmpty()
                    ) {
                        activity.copy(
                            limit = execution.historyImages.size,
                            isError = execution.isError,
                        )
                    } else {
                        activity.copy(isError = execution.isError)
                    }
                }
                execution.actionProposal?.let { actionProposals += it }
                execution.historyImages.forEach { image ->
                    if (historyImages.none { it.url == image.url }) {
                        historyImages += image
                    }
                }
                messages += AiChatMessage(
                    role = "tool",
                    content = execution.content,
                    toolCallId = call.id,
                    name = call.name,
                )
            }
        }

        // 工具轮次用尽后强制模型基于已有结果作答。
        _state.update { it.copy(status = ChatStatus.FINALIZE) }
        var finalStreamId: Long? = null
        val streamed = client.chatWithToolsStream(
            config = config,
            messages = messages,
            tools = tools,
            toolChoice = "none",
            onDelta = { delta ->
                val id = finalStreamId
                    ?: beginStreamingAssistant().also { finalStreamId = it }
                appendStreamingDelta(id, delta)
            },
        )
        val result = streamed.fold(
            onSuccess = { completion ->
                val content = completion.content?.trim()?.takeIf { it.isNotBlank() }
                if (content == null) {
                    finalStreamId?.let(::discardStreamingAssistant)
                    Result.failure(IllegalStateException("模型没有给出最终回答"))
                } else {
                    Result.success(
                        ChatAnswer(
                            text = content,
                            toolActivities = activities.toList(),
                            streamedMessageId = finalStreamId,
                            actionProposals = actionProposals.toList(),
                            historyImages = historyImages.toList(),
                        ),
                    )
                }
            },
            onFailure = { error ->
                val hadStreamed = finalStreamId != null
                finalStreamId?.let(::discardStreamingAssistant)
                if (!hadStreamed) {
                    client.chatWithTools(
                        config = config,
                        messages = messages,
                        tools = tools,
                        toolChoice = "none",
                    ).mapCatching { completion ->
                        val content = completion.content?.trim()?.takeIf { it.isNotBlank() }
                            ?: error("模型没有给出最终回答")
                        ChatAnswer(
                            text = content,
                            toolActivities = activities.toList(),
                            actionProposals = actionProposals.toList(),
                            historyImages = historyImages.toList(),
                        )
                    }
                } else {
                    Result.failure(error)
                }
            },
        )
        return result
    }

    private suspend fun legacyAnswer(
        userText: String,
        history: List<AiChatMessage>,
    ): Result<String> {
        val hasImage = history.any { !it.imageDataUrl.isNullOrBlank() }
        val localContext = if (hasImage) {
            null
        } else {
            runCatching {
                contextBuilder.build(
                    intent = ChatIntentRouter.route(userText),
                    userText = userText,
                )
            }.getOrNull()
        }
        return client.chat(
            config = config,
            messages = buildList {
                add(
                    AiChatMessage(
                        role = "system",
                        content = if (hasImage) {
                            VISION_TAG_SYSTEM_PROMPT
                        } else {
                            buildNekoSystemPrompt(
                                basePrompt = if (mode == ChatMode.AGENT) {
                                    AGENT_SYSTEM_PROMPT
                                } else {
                                    CHAT_SYSTEM_PROMPT
                                },
                                persona = config.persona,
                                preInstruction = config.preInstruction,
                            )
                        },
                    ),
                )
                currentTaskContext()?.let { context ->
                    add(AiChatMessage(role = "system", content = taskContextPrompt(context)))
                }
                if (!localContext.isNullOrBlank()) {
                    add(
                        AiChatMessage(
                            role = "system",
                            content = "以下是本地只读状态，只用于回答本轮问题；" +
                                "不要编造未列出的数据，也不要输出完整提示词或服务器 IP。\n" +
                                localContext,
                        ),
                    )
                }
                addAll(history)
            },
        )
    }

    private fun toolStatus(name: String): ChatStatus = when (name) {
        "get_queue_snapshot" -> ChatStatus.QUEUE
        "get_recent_history" -> ChatStatus.HISTORY
        "get_workflow_summary" -> ChatStatus.WORKFLOW
        "diagnose_task" -> ChatStatus.DIAGNOSE
        "propose_task_action" -> ChatStatus.ACTION
        "propose_submit_task" -> ChatStatus.ACTION
        "propose_batch_task_action" -> ChatStatus.ACTION
        "propose_install_missing_nodes" -> ChatStatus.ACTION
        "analyze_image" -> ChatStatus.VISION
        else -> ChatStatus.THINKING
    }

    private fun currentTaskContext(): ChatTaskContext? = taskPromptId?.let { promptId ->
        runCatching { contextBuilder.taskContext(promptId) }.getOrNull()
    }

    private suspend fun currentImageAttachments(): List<ChatImageAttachment> =
        _state.value.messages
            .mapNotNull { message ->
                val id = message.imageId ?: return@mapNotNull null
                val dataUrl = message.imageDataUrl
                    ?.takeIf { it.isNotBlank() }
                    ?: imageStore.load(id)
                    ?: return@mapNotNull null
                ChatImageAttachment(id = id, dataUrl = dataUrl)
            }
            .takeLast(MAX_IMAGE_ATTACHMENTS)

    /**
     * 会话恢复时从私有目录加载压缩图片，供聊天界面预览和视觉工具使用。
     */
    private suspend fun hydrateStoredImages(
        messages: List<ChatUiMessage>,
    ): List<ChatUiMessage> {
        val imageIds = messages.asReversed()
            .asSequence()
            .filter { it.imageId != null && it.imageDataUrl.isNullOrBlank() }
            .mapNotNull { it.imageId }
            .distinct()
            .take(MAX_HYDRATED_IMAGES)
            .toList()
        if (imageIds.isEmpty()) return messages
        val dataById = imageIds.mapNotNull { id ->
            imageStore.load(id)?.let { data -> id to data }
        }.toMap()
        if (dataById.isEmpty()) return messages
        return messages.map { message ->
            val data = message.imageId?.let { dataById[it] }
            if (data != null) message.copy(imageDataUrl = data) else message
        }
    }

    private fun refreshTaskContext() {
        if (taskPromptId == null) return
        val context = currentTaskContext() ?: return
        _state.update { it.copy(taskContext = context) }
    }

    fun clearTaskContext() {
        taskPromptId = null
        _state.update { it.copy(contextPromptId = null, taskContext = null) }
    }

    private fun taskContextPrompt(context: ChatTaskContext): String = buildString {
        appendLine("当前对话从任务详情进入，已关联以下任务。用户说「这个任务」时都指它：")
        appendLine("任务：${context.title}（${context.shortId}），状态：${context.statusLabel}")
        appendLine(context.summary)
        append("需要诊断这个任务时，调用 diagnose_task 并传入 promptId=${context.promptId}，不要查询其他任务。")
    }

    private fun beginStreamingAssistant(): Long {
        val id = nextMessageId++
        _state.update {
            it.copy(
                messages = it.messages + ChatUiMessage(
                    id = id,
                    role = ChatRole.ASSISTANT,
                    text = "",
                ),
                status = null,
                streamingMessageId = id,
            )
        }
        return id
    }

    private fun appendStreamingDelta(id: Long, delta: String) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == id) {
                        message.copy(text = message.text + delta)
                    } else {
                        message
                    }
                },
            )
        }
    }

    private fun discardStreamingAssistant(id: Long) {
        _state.update { state ->
            state.copy(
                messages = state.messages.filterNot { it.id == id },
                streamingMessageId = if (state.streamingMessageId == id) {
                    null
                } else {
                    state.streamingMessageId
                },
            )
        }
    }

    private fun finalizeAssistant(
        id: Long,
        text: String,
        toolActivities: List<ChatToolActivity>,
        actionProposals: List<ChatActionProposal>,
        historyImages: List<ChatHistoryImage>,
    ) {
        _state.update { state ->
            state.copy(
                sending = false,
                status = null,
                streamingMessageId = null,
                messages = state.messages.map { message ->
                    if (message.id == id) {
                        message.copy(
                            text = text,
                            toolActivities = toolActivities,
                            actionProposals = actionProposals,
                            historyImages = historyImages,
                        )
                    } else {
                        message
                    }
                },
            )
        }
        persistMessages()
    }

    private fun persistMessages() {
        val snapshot = _state.value.messages
        val promptId = taskPromptId
        val sessionId = currentSessionId ?: UUID.randomUUID().toString().also { id ->
            currentSessionId = id
            _state.update { it.copy(currentSessionId = id) }
        }
        val title = sessionTitle(snapshot)
        appScope.launch {
            historyStore.saveSession(
                sessionId = sessionId,
                promptId = promptId,
                title = title,
                mode = mode,
                messages = snapshot,
            )
            val sessions = historyStore.listSessions()
            _state.update {
                it.copy(sessions = sessions, currentSessionId = sessionId)
            }
        }
    }

    private fun sessionTitle(messages: List<ChatUiMessage>): String {
        currentTaskContext()?.let { return it.title }
        return messages.firstOrNull {
            it.role == ChatRole.USER && it.text.isNotBlank()
        }?.text
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.take(28)
            ?.takeIf { it.isNotBlank() }
            ?: "新会话"
    }

    fun newSession() {
        if (_state.value.sending) return
        currentSessionId = null
        mode = ChatMode.CHAT
        _state.update {
            it.copy(
                messages = emptyList(),
                currentSessionId = null,
                sending = false,
                status = null,
                streamingMessageId = null,
                mode = ChatMode.CHAT,
            )
        }
    }

    fun switchMode(newMode: ChatMode) {
        if (_state.value.sending || mode == newMode) return
        mode = newMode
        _state.update {
            it.copy(
                mode = newMode,
                status = null,
            )
        }
        if (currentSessionId != null) {
            persistMessages()
        }
    }

    fun switchSession(sessionId: String) {
        val state = _state.value
        if (state.sending || state.currentSessionId == sessionId) return
        viewModelScope.launch {
            val summary = state.sessions.firstOrNull { it.id == sessionId }
                ?: return@launch
            val messages = hydrateStoredImages(historyStore.loadSession(sessionId))
            taskPromptId = summary.promptId
            mode = summary.mode
            nextMessageId = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
            currentSessionId = sessionId
            _state.update {
                it.copy(
                    messages = messages,
                    currentSessionId = sessionId,
                    contextPromptId = summary.promptId,
                    taskContext = currentTaskContext(),
                    mode = mode,
                    sending = false,
                    status = null,
                    streamingMessageId = null,
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        if (_state.value.sending) return
        viewModelScope.launch {
            val messages = runCatching { historyStore.loadSession(sessionId) }
                .getOrDefault(emptyList())
            val imageIds = messages.mapNotNull { it.imageId }
            historyStore.deleteSession(sessionId)
            imageStore.delete(imageIds)
            val sessions = historyStore.listSessions()
            if (_state.value.currentSessionId == sessionId) {
                currentSessionId = null
                nextMessageId = 1L
                mode = ChatMode.CHAT
                _state.update {
                    it.copy(
                        messages = emptyList(),
                        currentSessionId = null,
                        sessions = sessions,
                        sending = false,
                        status = null,
                        streamingMessageId = null,
                        mode = ChatMode.CHAT,
                    )
                }
            } else {
                _state.update { it.copy(sessions = sessions) }
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            historyStore.renameSession(sessionId, trimmed)
            val sessions = historyStore.listSessions()
            _state.update { it.copy(sessions = sessions) }
        }
    }

    fun togglePinned(sessionId: String, pinned: Boolean) {
        viewModelScope.launch {
            historyStore.setPinned(sessionId, pinned)
            val sessions = historyStore.listSessions()
            _state.update { it.copy(sessions = sessions) }
        }
    }

    fun confirmAction(messageId: Long, proposalId: String) {
        if (mode != ChatMode.AGENT) return
        val proposal = _state.value.messages
            .firstOrNull { it.id == messageId }
            ?.actionProposals
            ?.firstOrNull { it.id == proposalId }
            ?: return
        if (proposal.state != ChatActionState.PENDING) return
        updateActionProposal(messageId, proposalId) {
            it.copy(state = ChatActionState.EXECUTING, resultMessage = null)
        }
        persistMessages()
        viewModelScope.launch {
            val result = actionHandler.execute(proposal)
            val successText = result.getOrNull()
            val outcomeId = successText?.let { nextMessageId++ }
            updateActionProposal(messageId, proposalId) { current ->
                result.fold(
                    onSuccess = { message ->
                        current.copy(
                            state = ChatActionState.SUCCESS,
                            resultMessage = message,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            state = ChatActionState.FAILED,
                            resultMessage = error.message ?: "执行失败",
                        )
                    },
                )
            }
            if (successText != null && outcomeId != null) {
                _state.update { state ->
                    state.copy(
                        messages = state.messages + ChatUiMessage(
                            id = outcomeId,
                            role = ChatRole.ASSISTANT,
                            text = successText,
                        ),
                    )
                }
            }
            persistMessages()
        }
    }

    fun cancelAction(messageId: Long, proposalId: String) {
        val proposal = _state.value.messages
            .firstOrNull { it.id == messageId }
            ?.actionProposals
            ?.firstOrNull { it.id == proposalId }
            ?: return
        if (proposal.state != ChatActionState.PENDING) return
        updateActionProposal(messageId, proposalId) {
            it.copy(state = ChatActionState.CANCELLED)
        }
        persistMessages()
    }

    fun selectPromptCandidate(
        messageId: Long,
        proposalId: String,
        candidateId: String,
    ) {
        val proposal = _state.value.messages
            .firstOrNull { it.id == messageId }
            ?.actionProposals
            ?.firstOrNull { it.id == proposalId }
            ?: return
        if (proposal.action != ChatTaskAction.SUBMIT ||
            proposal.state != ChatActionState.PENDING
        ) {
            return
        }
        val option = proposal.promptOptions.firstOrNull { it.id == candidateId } ?: return
        updateActionProposal(messageId, proposalId) {
            it.copy(
                promptCandidateId = option.id,
                promptCandidateLabel = option.label,
            )
        }
        persistMessages()
    }

    fun toggleInstallRestart(messageId: Long, proposalId: String) {
        if (mode != ChatMode.AGENT) return
        val proposal = _state.value.messages
            .firstOrNull { it.id == messageId }
            ?.actionProposals
            ?.firstOrNull { it.id == proposalId }
            ?: return
        if (proposal.action != ChatTaskAction.INSTALL_NODES ||
            proposal.state != ChatActionState.PENDING
        ) {
            return
        }
        updateActionProposal(messageId, proposalId) {
            it.copy(restartAfterInstall = !it.restartAfterInstall)
        }
        persistMessages()
    }

    fun savePreset(messageId: Long, proposalId: String, name: String) {
        if (mode != ChatMode.AGENT) return
        val proposal = _state.value.messages
            .firstOrNull { it.id == messageId }
            ?.actionProposals
            ?.firstOrNull { it.id == proposalId }
            ?: return
        if (proposal.action != ChatTaskAction.SUBMIT ||
            proposal.state != ChatActionState.SUCCESS ||
            proposal.presetSaved ||
            proposal.presetSaving ||
            name.isBlank()
        ) {
            return
        }
        updateActionProposal(messageId, proposalId) {
            it.copy(presetSaving = true, presetError = null)
        }
        viewModelScope.launch {
            val result = actionHandler.savePreset(proposal, name)
            updateActionProposal(messageId, proposalId) { current ->
                result.fold(
                    onSuccess = { message ->
                        current.copy(
                            presetSaving = false,
                            presetSaved = true,
                            presetName = name.trim(),
                            presetError = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            presetSaving = false,
                            presetError = error.message ?: "保存预设失败",
                        )
                    },
                )
            }
            persistMessages()
        }
    }

    private fun updateActionProposal(
        messageId: Long,
        proposalId: String,
        transform: (ChatActionProposal) -> ChatActionProposal,
    ) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) {
                        message
                    } else {
                        message.copy(
                            actionProposals = message.actionProposals.map { proposal ->
                                if (proposal.id == proposalId) transform(proposal) else proposal
                            },
                        )
                    }
                },
            )
        }
    }

    private fun appendAssistant(
        text: String,
        isError: Boolean = false,
        toolActivities: List<ChatToolActivity> = emptyList(),
        actionProposals: List<ChatActionProposal> = emptyList(),
        historyImages: List<ChatHistoryImage> = emptyList(),
    ) {
        _state.update {
            it.copy(
                sending = false,
                status = null,
                streamingMessageId = null,
                messages = it.messages + ChatUiMessage(
                    id = nextMessageId++,
                    role = ChatRole.ASSISTANT,
                    text = text,
                    isError = isError,
                    toolActivities = toolActivities,
                    actionProposals = actionProposals,
                    historyImages = historyImages,
                ),
            )
        }
        persistMessages()
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 3
        private const val MAX_CONTEXT_MESSAGES = 40
        private const val MAX_IMAGE_ATTACHMENTS = 4
        private const val MAX_HYDRATED_IMAGES = 8

        private const val CHAT_SYSTEM_PROMPT =
            "你是 Neko I 的 Chat 模式，NekoPaint 的只读 AI 助手。NekoPaint 是连接局域网 ComfyUI 的 Android 客户端。" +
                "你只能查询和解释信息，不能重试、中断、删除或提交任务。" +
                "用户要求执行写操作时，说明需要切换到 Agent 模式。" +
                "回答使用中文，简洁、可执行，尽量使用短句和换行。" +
                "可以使用简单 Markdown：标题、列表、加粗、行内代码和代码块；不要使用表格或图片。" +
                "如果本轮提供了「本地只读状态」，必须严格依据它回答，不要编造服务器、队列或历史状态。"

        private const val AGENT_SYSTEM_PROMPT =
            "你是 Neko I 的 Agent 模式，NekoPaint 的 AI 助手。NekoPaint 是连接局域网 ComfyUI 的 Android 客户端。" +
                "回答使用中文，简洁、可执行，尽量使用短句和换行。" +
                "可以使用简单 Markdown：标题、列表、加粗、行内代码和代码块；不要使用表格或图片。" +
                "如果本轮提供了「本地只读状态」，必须严格依据它回答，不要编造服务器、队列或历史状态；" +
                "如果没有提供状态，就提示用户到对应页面查看，或说明该能力将在后续版本接入。"

        private const val VISION_TAG_SYSTEM_PROMPT =
            "你是 Neko I 的图片标签分析助手。用户会发一张图片，请输出可直接用于 AI 绘图的标签。" +
                "回答使用中文描述，但标签必须是英文。" +
                "格式要求：" +
                "1. 先用 1-2 句简短中文描述画面。" +
                "2. 然后另起一行写“标签：”。" +
                "3. 标签放在 ```tags 代码块中，代码块内只放标签，不要写解释、编号、标题或中文。" +
                "4. 标签用逗号加空格分隔，按重要性排序；多词标签用下划线，例如 long_hair、blue_eyes。" +
                "5. 标签应覆盖能看清的人数、角色/作品（确定才写）、发型、眼睛、表情、服装、动作、构图、背景、光照和画风。" +
                "6. 不要编造看不清或不确定的内容，不要重复标签。" +
                "7. 如果用户只要求标签，可以只输出 tags 代码块。" +
                "示例：\n画面是一个白发蓝眼的少女，站在夜晚的街道上。\n标签：\n" +
                "```tags\n1girl, solo, long_hair, white_hair, blue_eyes, night, street, city_lights\n```"

        private const val TOOL_SYSTEM_PROMPT_CHAT =
            "你是 Neko I 的 Chat 模式，NekoPaint 的只读 AI 助手。你可以调用只读工具：" +
                "get_queue_snapshot、get_recent_history、get_history_images、get_workflow_summary、" +
                "get_workflow_parameters、diagnose_task、analyze_image。" +
                "需要实时数据时必须先调用合适的工具，不要猜测。用户要求查看历史图片时调用 get_history_images，" +
                "图片会由 App 直接展示，不要输出图片 URL、服务器 IP 或文件路径。" +
                "用户附了图片时，必须先调用 analyze_image 获取内容；不要猜测图片内容，也不要声称直接看到了图片。" +
                "如果工具结果包含 ```tags 代码块，最终回答必须原样保留，方便用户复制。" +
                "你没有写操作能力，不能重试、中断、删除或提交任务；用户要求这些操作时，说明需要切换到 Agent 模式。" +
                "回答使用中文，简洁、可执行。可以使用简单 Markdown：标题、列表、加粗、" +
                "行内代码和代码块；不要使用表格或图片。"

        private const val TOOL_SYSTEM_PROMPT_AGENT =
            "你是 Neko I 的 Agent 模式，NekoPaint 的 AI 助手。你可以调用只读工具查询本地应用状态：" +
                "get_queue_snapshot、get_recent_history、get_history_images、get_workflow_summary、" +
                "get_workflow_parameters、diagnose_task、analyze_image。" +
                "需要实时数据时必须先调用合适的工具，不要猜测。用户要求查看历史图片时调用 get_history_images，" +
                "图片会由 App 直接展示，不要输出图片 URL、服务器 IP 或文件路径。" +
                "用户附了图片时，必须先调用 analyze_image 获取内容；不要猜测图片内容，也不要声称直接看到了图片。" +
                "如果工具结果包含 ```tags 代码块，最终回答必须原样保留，方便用户复制。" +
                "你可以调用 propose_task_action 为重试、中断或删除任务创建建议，但它只会生成确认卡片，" +
                "不会直接执行。只有用户明确要求重试、中断或删除时才调用；绝不能声称操作已经完成，" +
                "必须提示用户在卡片中确认。" +
                "需要批量重试失败任务或批量删除等待任务时，调用 propose_batch_task_action，" +
                "一次最多 10 个任务，同样必须等待用户确认。" +
                "工作流缺少自定义节点时，调用 propose_install_missing_nodes；它会通过主机上的 " +
                "ComfyUI-Manager 查询安装包，列出包名和来源，同样必须等待用户确认，" +
                "不要声称已经安装或重启。" +
                "你可以调用 propose_submit_task 创建文生图任务提交建议；只支持不需要图片或蒙版的文生图模板，" +
                "同样必须等待用户确认，不能声称已经提交。用户指定步数、CFG、seed、尺寸等参数时，" +
                "先调用 get_workflow_parameters 获取准确的 candidateId，再通过 parameters 临时覆盖；" +
                "不要编造参数 ID，也不要试图修改提示词字段的参数。" +
                "不要要求完整提示词、图片内容或服务器 IP。" +
                "回答使用中文，简洁、可执行。可以使用简单 Markdown：标题、列表、加粗、" +
                "行内代码和代码块；不要使用表格或图片。"

        fun factory(
            container: AppContainer,
            promptId: String? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val contextBuilder = ChatContextBuilder(
                    taskManager = container.taskManager,
                    workflowRepository = container.workflowRepository,
                )
                val actionHandler = ChatTaskActionHandler(
                    dataSource = DefaultChatTaskActionDataSource(
                        taskManager = container.taskManager,
                        workflowRepository = container.workflowRepository,
                        queueRepository = container.queueRepository,
                    ),
                    nodeInstaller = container.nodeInstallRepository,
                )
                val visionAnalyzer = ChatVisionAnalyzer(
                    client = container.aiAssistantClient,
                    store = container.aiAssistantStore,
                )
                ChatViewModel(
                    store = container.aiAssistantStore,
                    client = container.aiAssistantClient,
                    imageEncoder = container.chatImageEncoder,
                    imageStore = container.chatImageStore,
                    contextBuilder = contextBuilder,
                    toolRegistry = ChatToolRegistry(
                        json = container.json,
                        contextBuilder = contextBuilder,
                        actionHandler = actionHandler,
                        visionAnalyzer = visionAnalyzer,
                    ),
                    historyStore = container.chatHistoryStore,
                    actionHandler = actionHandler,
                    appScope = container.appScope,
                    contextPromptId = promptId,
                )
            }
        }
    }
}
