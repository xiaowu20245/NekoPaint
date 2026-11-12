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
import io.github.xiaowu20245.nekopaint.data.repository.ChatImageEncoder
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatRole(val apiRole: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

data class ChatUiMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val imageUri: Uri? = null,
    val imageDataUrl: String? = null,
    val isError: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val sending: Boolean = false,
    val configured: Boolean = false,
    val visionConfigured: Boolean = false,
    val modelLabel: String = "",
)

/**
 * Chat MVP：多轮文字对话 + 图片视觉分析。
 *
 * 会话目前保存在内存中；应用任务工具调用将在后续阶段接入。
 */
class ChatViewModel(
    private val store: AiAssistantStore,
    private val client: AiAssistantClient,
    private val imageEncoder: ChatImageEncoder,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var config = AiAssistantConfig()
    private var nextMessageId = 1L

    init {
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

        val userText = if (trimmed.isEmpty()) "请分析这张图片。" else trimmed
        val userMessage = ChatUiMessage(
            id = nextMessageId++,
            role = ChatRole.USER,
            text = userText,
            imageUri = imageUri,
        )
        _state.update {
            it.copy(messages = it.messages + userMessage, sending = true)
        }

        viewModelScope.launch {
            val imageData = if (imageUri != null) {
                val result = imageEncoder.toDataUrl(imageUri)
                val data = result.getOrElse { error ->
                    _state.update { current ->
                        current.copy(
                            sending = false,
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
                data
            } else {
                null
            }

            val history = _state.value.messages
                .filterNot { it.isError }
                .map { message ->
                    AiChatMessage(
                        role = message.role.apiRole,
                        content = message.text,
                        imageDataUrl = message.imageDataUrl,
                    )
                }

            client.chat(
                config = config,
                messages = listOf(AiChatMessage(role = "system", content = SYSTEM_PROMPT)) + history,
            ).fold(
                onSuccess = { reply ->
                    appendAssistant(reply.trim())
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

    fun clearConversation() {
        _state.update { it.copy(messages = emptyList(), sending = false) }
    }

    private fun appendAssistant(text: String, isError: Boolean = false) {
        _state.update {
            it.copy(
                sending = false,
                messages = it.messages + ChatUiMessage(
                    id = nextMessageId++,
                    role = ChatRole.ASSISTANT,
                    text = text,
                    isError = isError,
                ),
            )
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是 NekoPaint 的 AI 助手。NekoPaint 是连接局域网 ComfyUI 的 Android 客户端。" +
                "回答使用中文，简洁、可执行，尽量使用短句和换行，不要使用 Markdown 标题或符号。" +
                "不要编造服务器、队列或历史状态；" +
                "当前版本还没有接入应用工具，如果用户询问实时状态或要求执行操作，" +
                "请提示用户到对应页面查看，或说明该能力将在后续版本接入。"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    store = container.aiAssistantStore,
                    client = container.aiAssistantClient,
                    imageEncoder = container.chatImageEncoder,
                )
            }
        }
    }
}
