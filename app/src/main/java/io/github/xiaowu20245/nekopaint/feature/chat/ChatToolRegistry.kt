package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.network.AiToolCall
import io.github.xiaowu20245.nekopaint.core.network.AiToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Neko I 的工具白名单。
 *
 * 读工具只访问本地缓存/仓库；唯一的写工具只创建确认卡片，不直接执行动作。
 */
class ChatToolRegistry(
    private val json: Json,
    private val contextBuilder: ChatContextBuilder,
    private val actionHandler: ChatTaskActionHandler,
    private val visionAnalyzer: VisionAnalyzer? = null,
) {

    private val imageAnalysisTool = ImageAnalysisTool(visionAnalyzer)

    fun definitions(mode: ChatMode): List<AiToolDefinition> =
        allDefinitions().filter { tool -> ChatToolPolicy.allows(tool.name, mode) }

    private fun allDefinitions(): List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = "get_queue_snapshot",
            description = "读取当前 ComfyUI 队列：连接状态、运行中/等待中任务、当前节点和进度。" +
                "用户询问队列、排队、进度、还有几个任务时使用。",
            parameters = objectSchema(),
        ),
        AiToolDefinition(
            name = "get_recent_history",
            description = "读取最近的历史记录，可按状态过滤。用户询问最近生成了什么、成功/失败记录时使用。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("minimum", 1)
                        put("maximum", 10)
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("success")
                            add("failed")
                            add("interrupted")
                        }
                    }
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "get_history_images",
            description = "读取历史任务中的图片并在聊天中直接展示。用户要求看最近生成的图片、" +
                "某条历史记录的图片或预览历史成图时使用。图片会由 App 渲染，不要输出图片 URL。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("promptId") {
                        put("type", "string")
                        put("description", "任务 promptId，可选；不传则使用最近一条带图片的历史记录")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("minimum", 1)
                        put("maximum", 6)
                        put("description", "最多展示图片数，默认 4")
                    }
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "get_workflow_summary",
            description = "读取已导入工作流的模式、能力标签、节点数、图片/蒙版槽和兼容性提示。" +
                "用户询问工作流、模板、节点、参数或兼容性时使用。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("keyword") {
                        put("type", "string")
                        put("description", "工作流名称关键词，可选")
                    }
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "analyze_image",
            description = "分析用户在对话中发送的图片。用户附了图片并需要看图、提取标签或询问图片内容时调用。" +
                "没有可用图片时不要调用；不要猜测图片内容。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("imageId") {
                        put("type", "string")
                        put("description", "图片 ID，可选；不传则使用最新一张图片")
                    }
                    putJsonObject("mode") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("tags")
                            add("describe")
                            add("question")
                        }
                        put("description", "tags 提取标签，describe 描述画面，question 回答关于图片的问题")
                    }
                    putJsonObject("question") {
                        put("type", "string")
                        put("description", "mode=question 时要问图片的问题")
                    }
                }
                putJsonArray("required") {
                    add("mode")
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "diagnose_task",
            description = "读取最近一条失败/中断历史，或指定 promptId 的记录，返回状态、失败节点、错误和参数摘要。" +
                "用户询问失败原因、报错、中断时优先使用。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("promptId") {
                        put("type", "string")
                        put("description", "任务 promptId，可选；不传则使用最近一条失败记录")
                    }
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "get_workflow_parameters",
            description = "读取文生图模板可覆盖的采样、尺寸、模型等参数。返回 candidateId、标签、类型、" +
                "当前值和约束。用户要指定步数、CFG、seed、尺寸等参数时先调用此工具。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("workflow") {
                        put("type", "string")
                        put("description", "工作流名称或关键词，可选；只有一个文生图模板时可省略")
                    }
                    putJsonObject("includeAdvanced") {
                        put("type", "boolean")
                        put("description", "是否包含高级/其他参数，默认 false")
                    }
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "propose_submit_task",
            description = "创建文生图任务提交建议。只支持不需要图片或蒙版的文生图模板；" +
                "会生成确认卡片，不会直接提交。用户要求生成图片时使用。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("workflow") {
                        put("type", "string")
                        put("description", "工作流名称或关键词，可选；只有一个文生图模板时可省略")
                    }
                    putJsonObject("prompt") {
                        put("type", "string")
                        put("description", "要提交的英文提示词")
                    }
                    putJsonObject("parameters") {
                        put("type", "array")
                        put("description", "需要临时覆盖的参数，不影响原工作流保存值")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("candidateId") {
                                    put("type", "string")
                                    put("description", "get_workflow_parameters 返回的参数 ID")
                                }
                                putJsonObject("value") {
                                    put("type", "string")
                                    put("description", "新的参数值，统一用字符串")
                                }
                            }
                            putJsonArray("required") {
                                add("candidateId")
                                add("value")
                            }
                            put("additionalProperties", false)
                        }
                    }
                }
                putJsonArray("required") {
                    add("prompt")
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "propose_batch_task_action",
            description = "批量重试失败/中断任务，或批量删除等待中的任务。一次最多 10 个。" +
                "只创建确认卡片，不会直接执行；用户明确要求批量操作时使用。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("retry")
                            add("delete")
                        }
                    }
                    putJsonObject("promptIds") {
                        put("type", "array")
                        put("description", "要操作的任务 promptId 列表")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                }
                putJsonArray("required") {
                    add("action")
                    add("promptIds")
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "propose_install_missing_nodes",
            description = "为缺少自定义节点的工作流创建安装建议。会先通过主机上的 ComfyUI-Manager " +
                "查询节点包映射，列出包名和来源；需要主机已启用 ComfyUI-Manager。" +
                "只生成确认卡片，不会直接安装。用户明确要求安装缺失节点时使用。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("workflow") {
                        put("type", "string")
                        put("description", "工作流名称或关键词，可选；只有一个缺少节点的工作流时可省略")
                    }
                }
                put("additionalProperties", false)
            },
        ),
        AiToolDefinition(
            name = "propose_task_action",
            description = "为指定任务创建需要用户确认的动作建议。" +
                "action=retry 重试历史任务，action=interrupt 中断运行中任务，action=delete 删除等待中任务。" +
                "调用后只生成确认卡片，不会执行；不要声称已经重试、中断或删除。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(ChatTaskAction.RETRY.apiValue)
                            add(ChatTaskAction.INTERRUPT.apiValue)
                            add(ChatTaskAction.DELETE.apiValue)
                        }
                    }
                    putJsonObject("promptId") {
                        put("type", "string")
                        put("description", "要操作的任务 promptId")
                    }
                }
                putJsonArray("required") {
                    add("action")
                    add("promptId")
                }
                put("additionalProperties", false)
            },
        ),
    )

    suspend fun execute(
        call: AiToolCall,
        fallbackPromptId: String? = null,
        attachments: List<ChatImageAttachment> = emptyList(),
        direct: Boolean = false,
    ): ChatToolExecution {
        val args = parseArguments(call.arguments)
        var actionProposal: ChatActionProposal? = null
        var historyImages: List<ChatHistoryImage> = emptyList()

        val content = runCatching {
            when (call.name) {
                "analyze_image" -> imageAnalysisTool.analyze(args, attachments, direct)
                "get_queue_snapshot" -> contextBuilder.queueSnapshot()
                "get_recent_history" -> contextBuilder.historySnapshot(
                    limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 5,
                    status = args["status"]?.jsonPrimitive?.contentOrNull,
                )
                "get_history_images" -> {
                    val result = contextBuilder.historyImages(
                        promptId = args["promptId"]?.jsonPrimitive?.contentOrNull,
                        maxImages = args["limit"]?.jsonPrimitive?.intOrNull ?: 4,
                    )
                    if (result == null) {
                        "本地状态：没有找到可展示的历史图片。"
                    } else {
                        historyImages = result.images
                        buildString {
                            appendLine("本地状态：历史图片")
                            appendLine(
                                "任务：${result.title}（${result.promptId.take(8)}），" +
                                    chatStatusLabel(result.status),
                            )
                            appendLine(
                                "图片：${result.images.joinToString("、") { image ->
                                    "${image.filename}（${if (image.isOutput) "成图" else "草稿"}）"
                                }}",
                            )
                            if (direct) {
                                append("图片已展示在下方。")
                            } else {
                                append("图片已展示给用户；不要输出图片 URL、服务器 IP 或文件路径。")
                            }
                        }
                    }
                }
                "get_workflow_parameters" -> actionHandler.describeParameters(
                    workflowKeyword = args["workflow"]?.jsonPrimitive?.contentOrNull,
                    includeAdvanced = args["includeAdvanced"]?.jsonPrimitive?.booleanOrNull
                        ?: false,
                ).getOrElse { error ->
                    "工具执行失败：${error.message ?: "读取参数失败"}"
                }
                "get_workflow_summary" -> contextBuilder.workflowSummary(
                    keyword = args["keyword"]?.jsonPrimitive?.contentOrNull,
                )
                "diagnose_task" -> contextBuilder.diagnoseTask(
                    promptId = args["promptId"]?.jsonPrimitive?.contentOrNull
                        ?: fallbackPromptId,
                )
                "propose_task_action" -> proposeAction(
                    actionValue = args["action"]?.jsonPrimitive?.contentOrNull,
                    promptId = args["promptId"]?.jsonPrimitive?.contentOrNull,
                ) { proposal ->
                    actionProposal = proposal
                }
                "propose_batch_task_action" -> {
                    val action = when (args["action"]?.jsonPrimitive?.contentOrNull) {
                        "retry" -> ChatTaskAction.BATCH_RETRY
                        "delete" -> ChatTaskAction.BATCH_DELETE
                        else -> null
                    }
                    val promptIds = (args["promptIds"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { it.jsonPrimitive.contentOrNull }
                        .filter { it.isNotBlank() }
                    if (action == null || promptIds.isEmpty()) {
                        "无法创建批量操作建议：参数无效"
                    } else {
                        actionHandler.proposeBatch(action, promptIds).fold(
                            onSuccess = { proposal ->
                                actionProposal = proposal
                                val actionLabel = if (proposal.action == ChatTaskAction.BATCH_RETRY) {
                                    "批量重试"
                                } else {
                                    "批量删除"
                                }
                                "已创建${actionLabel}建议：${proposal.batchTargets.size} 个任务。" +
                                    "请让用户点击确认卡片后执行，不要声称操作已经完成。"
                            },
                            onFailure = { error ->
                                "无法创建批量操作建议：${error.message ?: "任务状态不匹配"}"
                            },
                        )
                    }
                }
                "propose_install_missing_nodes" -> {
                    actionHandler.proposeNodeInstall(
                        workflowKeyword = args["workflow"]?.jsonPrimitive?.contentOrNull,
                    ).fold(
                        onSuccess = { proposal ->
                            actionProposal = proposal
                            "已创建节点安装建议：工作流「${proposal.title}」缺少 " +
                                "${proposal.missingNodeTypeCount} 个节点，计划安装 " +
                                "${proposal.installPackages.size} 个节点包。" +
                                "请让用户检查包名和来源后点击确认卡片，不要声称已经安装。"
                        },
                        onFailure = { error ->
                            "无法创建节点安装建议：${error.message ?: "检查缺失节点失败"}"
                        },
                    )
                }
                "propose_submit_task" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val workflow = args["workflow"]?.jsonPrimitive?.contentOrNull
                    val rawParameters = args["parameters"] as? JsonArray
                    val parameterRequests = rawParameters.orEmpty().mapNotNull { element ->
                        val parameter = element as? JsonObject ?: return@mapNotNull null
                        val id = parameter["candidateId"]?.jsonPrimitive?.contentOrNull
                        val value = parameter["value"]?.jsonPrimitive?.contentOrNull
                        if (id.isNullOrBlank() || value == null) {
                            null
                        } else {
                            ChatSubmissionParameter(candidateId = id, value = value)
                        }
                    }
                    if (rawParameters != null && parameterRequests.size != rawParameters.size) {
                        "无法创建提交建议：参数格式无效"
                    } else {
                        actionHandler.proposeSubmit(
                            workflowKeyword = workflow,
                            prompt = prompt,
                            parameters = parameterRequests,
                        ).fold(
                            onSuccess = { proposal ->
                                actionProposal = proposal
                                val parameterText = if (proposal.parameterOverrides.isEmpty()) {
                                    ""
                                } else {
                                    "，参数覆盖 ${proposal.parameterOverrides.size} 项"
                                }
                                "已创建提交建议：文生图「${proposal.title}」$parameterText。" +
                                    "请让用户点击确认卡片后提交，不要声称任务已经提交。"
                            },
                            onFailure = { error ->
                                "无法创建提交建议：${error.message ?: "模板状态不匹配"}"
                            },
                        )
                    }
                }
                else -> "未知工具：${call.name}"
            }
        }.getOrElse { error ->
            "工具执行失败：${error.message ?: "未知错误"}"
        }.take(MAX_RESULT_CHARS)

        return ChatToolExecution(
            content = content,
            isError = content.startsWith(ERROR_PREFIX) ||
                content.startsWith(UNKNOWN_PREFIX) ||
                content.startsWith(ACTION_ERROR_PREFIX) ||
                content.startsWith(SUBMIT_ERROR_PREFIX) ||
                content.startsWith(BATCH_ERROR_PREFIX) ||
                content.startsWith(INSTALL_ERROR_PREFIX),
            actionProposal = actionProposal,
            historyImages = historyImages,
        )
    }

    private fun proposeAction(        actionValue: String?,
        promptId: String?,
        onProposal: (ChatActionProposal) -> Unit,
    ): String {
        val action = ChatTaskAction.fromApiValue(actionValue)
            ?: return "无法创建动作建议：不支持的操作"
        if (promptId.isNullOrBlank()) {
            return "无法创建动作建议：缺少任务 ID"
        }
        return actionHandler.propose(action, promptId).fold(
            onSuccess = { proposal ->
                onProposal(proposal)
                val actionLabel = when (proposal.action) {
                    ChatTaskAction.RETRY -> "重试"
                    ChatTaskAction.INTERRUPT -> "中断"
                    ChatTaskAction.DELETE -> "删除"
                    ChatTaskAction.SUBMIT -> "提交"
                    ChatTaskAction.BATCH_RETRY -> "批量重试"
                    ChatTaskAction.BATCH_DELETE -> "批量删除"
                    ChatTaskAction.INSTALL_NODES -> "安装缺失节点"
                }
                "已创建动作建议：$actionLabel「${proposal.title}」（${proposal.shortId}）。" +
                    "请让用户点击确认卡片后执行，不要声称操作已经完成。"
            },
            onFailure = { error ->
                "无法创建动作建议：${error.message ?: "任务状态不匹配"}"
            },
        )
    }

    fun describe(call: AiToolCall, fallbackPromptId: String? = null): ChatToolActivity =
        describeToolCall(json, call, fallbackPromptId)

    private fun parseArguments(arguments: String): JsonObject = runCatching {
        json.parseToJsonElement(arguments).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))

    private fun objectSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        put("additionalProperties", false)
    }

    private companion object {
        const val MAX_RESULT_CHARS = 4000
        const val ERROR_PREFIX = "工具执行失败："
        const val UNKNOWN_PREFIX = "未知工具："
        const val ACTION_ERROR_PREFIX = "无法创建动作建议："
        const val SUBMIT_ERROR_PREFIX = "无法创建提交建议："
        const val BATCH_ERROR_PREFIX = "无法创建批量操作建议："
        const val INSTALL_ERROR_PREFIX = "无法创建节点安装建议："
    }
}

data class ChatToolExecution(
    val content: String,
    val isError: Boolean,
    val actionProposal: ChatActionProposal? = null,
    val historyImages: List<ChatHistoryImage> = emptyList(),
)
