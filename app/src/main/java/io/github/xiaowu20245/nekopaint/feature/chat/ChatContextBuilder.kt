package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCapability
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 把本地只读状态压缩成短摘要，注入 Neko I 的系统上下文。
 *
 * 只读取任务/历史/工作流的元数据，不把提示词正文、图片内容或服务器 IP 放进上下文。
 */
class ChatContextBuilder(
    private val taskManager: TaskManager,
    private val workflowRepository: WorkflowRepository,
) {

    suspend fun build(intent: ChatIntent, userText: String): String? = when (intent) {
        ChatIntent.QUEUE -> queueSnapshot()
        ChatIntent.HISTORY -> historySnapshot()
        ChatIntent.WORKFLOW -> workflowSummary(userText)
        ChatIntent.FAILURE -> diagnoseTask()
        ChatIntent.GENERAL -> null
    }

    fun queueSnapshot(): String {
        val queue = taskManager.queue.value
            ?: return "本地状态：队列尚未读取到，可能未连接或正在加载。"
        val progress = taskManager.progress.value
        val tasks = taskManager.tasks.value.associateBy { it.promptId }
        val lines = mutableListOf(
            "本地状态：队列",
            "连接：${connectionLabel(taskManager.connection.value)}",
            "运行中 ${queue.running.size} 个，等待中 ${queue.pending.size} 个，服务器剩余 ${taskManager.queueRemaining.value} 个",
        )
        queue.running.take(3).forEach { item ->
            val task = tasks[item.promptId]
            val itemProgress = progress[item.promptId]
            val node = itemProgress?.currentNodeLabel ?: itemProgress?.currentNode ?: "计算中"
            val stepText = if (itemProgress?.value != null && itemProgress.max != null) {
                "${itemProgress.value.roundToInt()}/${itemProgress.max.roundToInt()} 步"
            } else {
                "进度未知"
            }
            lines += "- 运行中：${task?.templateName ?: "未命名任务"}（${shortId(item.promptId)}），$node，$stepText"
        }
        queue.pending.take(5).forEach { item ->
            val task = tasks[item.promptId]
            lines += "- 等待中：${task?.templateName ?: "未命名任务"}（${shortId(item.promptId)}）"
        }
        return lines.joinToString("\n")
    }

    fun historySnapshot(limit: Int = 5, status: String? = null): String {
        val max = limit.coerceIn(1, 10)
        val history = taskManager.history.value
            .filter { status.isNullOrBlank() || it.status == status }
        if (history.isEmpty()) return "本地状态：没有符合条件的历史记录。"
        val tasks = taskManager.tasks.value.associateBy { it.promptId }
        val lines = mutableListOf("本地状态：最近 ${minOf(max, history.size)} 条历史")
        history.take(max).forEach { entry ->
            val task = tasks[entry.promptId]
            val duration = durationText(entry.startedAt ?: task?.startedAt, entry.finishedAt ?: entry.timestamp)
            lines += buildString {
                append("- ${formatTime(entry.timestamp)} ")
                append("${statusLabel(entry.status)} ")
                append("${task?.templateName ?: "未命名任务"}（${shortId(entry.promptId)}）")
                duration?.let { append("，耗时 $it") }
                append("，${entry.images.size} 张图")
                entry.errorMessage?.let { append("，错误：${it.take(120)}") }
            }
        }
        return lines.joinToString("\n")
    }

    suspend fun workflowSummary(keyword: String? = null): String {
        val templates = runCatching { workflowRepository.listTemplates() }
            .getOrDefault(emptyList())
        if (templates.isEmpty()) return "本地状态：尚未导入工作流模板。"
        val matched = templates
            .filter { keyword.isNullOrBlank() || it.name.contains(keyword, ignoreCase = true) }
            .ifEmpty { templates.take(5) }
        val lines = mutableListOf("本地状态：工作流模板")
        matched.forEach { template ->
            val compatibility = template.compatibility
            val capabilities = compatibility?.capabilities.orEmpty()
                .map { capabilityLabel(it) }
                .filter { it.isNotBlank() }
            val issueCount = compatibility?.issues.orEmpty().count {
                it.severity.name == "ERROR" || it.severity.name == "WARNING"
            }
            lines += buildString {
                append("- ${template.name}：")
                append("${modeLabel(compatibility?.mode)}，")
                append("${template.nodeCount} 个节点")
                if (capabilities.isNotEmpty()) append("，能力：${capabilities.joinToString("/")}")
                if (compatibility != null) {
                    append("，图槽 ${compatibility.imageSlots.size}，蒙版槽 ${compatibility.maskSlots.size}")
                }
                if (template.missingNodeTypes.isNotEmpty()) {
                    append("，缺少 ${template.missingNodeTypes.size} 个节点")
                }
                if (issueCount > 0) append("，$issueCount 项提示")
            }
        }
        return lines.joinToString("\n")
    }

    fun historyImages(promptId: String?, maxImages: Int): ChatHistoryImageResult? =
        selectHistoryImages(
            history = taskManager.history.value,
            tasks = taskManager.tasks.value,
            promptId = promptId,
            maxImages = maxImages,
        )

    /**
     * 读取指定任务的只读上下文；任务不在本地历史中时返回 null。
     */
    fun taskContext(promptId: String): ChatTaskContext? {        val entry = taskManager.history.value.firstOrNull { it.promptId == promptId }
            ?: return null
        val task = taskManager.tasks.value.firstOrNull { it.promptId == promptId }
        return ChatTaskContext.from(
            entry = entry,
            task = task,
            summary = diagnoseTask(promptId),
        )
    }

    fun diagnoseTask(promptId: String? = null): String {
        val history = taskManager.history.value
        val entry = promptId?.let { id -> history.firstOrNull { it.promptId == id } }
            ?: history.firstOrNull { it.status == "failed" || it.status == "interrupted" }
        if (entry == null) {
            return if (promptId.isNullOrBlank()) {
                "本地状态：最近没有失败或中断的历史记录。"
            } else {
                "本地状态：没有找到 promptId=$promptId 的历史记录。"
            }
        }
        val task = taskManager.tasks.value.firstOrNull { it.promptId == entry.promptId }
        val values = task?.values.orEmpty()
        val params = listOf(
            "seed" to values.byInput("seed"),
            "steps" to values.byInput("steps"),
            "cfg" to values.byInput("cfg"),
            "sampler" to values.byInput("sampler_name"),
            "model" to values.byInput("ckpt_name"),
        ).mapNotNull { (label, value) -> value?.let { "$label=$it" } }
        return buildString {
            if (promptId.isNullOrBlank()) {
                appendLine("本地状态：最近一条失败/中断记录")
            } else {
                appendLine("本地状态：任务诊断")
            }
            appendLine("状态：${statusLabel(entry.status)}")
            appendLine("任务：${task?.templateName ?: "未命名任务"}（${shortId(entry.promptId)}）")
            durationText(entry.startedAt ?: task?.startedAt, entry.finishedAt ?: entry.timestamp)
                ?.let { appendLine("耗时：$it") }
            appendLine("输出图片：${entry.images.size} 张")
            entry.errorNodeId?.let { appendLine("失败节点：$it") }
            entry.errorMessage?.let { appendLine("错误信息：${it.take(240)}") }
            if (params.isNotEmpty()) appendLine("参数摘要：${params.joinToString("，")}")
            if (entry.status == "failed" || entry.status == "interrupted") {
                append("请基于以上信息分析可能原因；不要编造未列出的错误。")
            } else {
                append("请基于以上信息回答用户关于该任务的问题；不要编造未列出的数据。")
            }
        }
    }

    private fun Map<String, String>.byInput(input: String): String? =
        entries.firstOrNull { (key, _) ->
            val name = key.substringAfter(':', key)
            name == input
        }?.value

    private fun connectionLabel(state: ComfyConnectionState): String = when (state) {
        ComfyConnectionState.Connected -> "已连接"
        ComfyConnectionState.Connecting -> "连接中"
        ComfyConnectionState.Reconnecting -> "重连中"
        ComfyConnectionState.Disconnected -> "离线"
    }

    private fun modeLabel(mode: WorkflowMode?): String = when (mode) {
        WorkflowMode.TEXT_TO_IMAGE -> "文生图"
        WorkflowMode.IMAGE_TO_IMAGE -> "图生图"
        WorkflowMode.MASK_INPAINT -> "蒙版重绘"
        WorkflowMode.AUTO_DETAIL -> "自动部位重绘"
        WorkflowMode.VIDEO -> "视频生成"
        WorkflowMode.UNKNOWN, null -> "未知类型"
    }

    private fun capabilityLabel(capability: WorkflowCapability): String = when (capability) {
        WorkflowCapability.CONTROL_NET -> "ControlNet"
        WorkflowCapability.UPSCALE -> "放大"
        WorkflowCapability.DETAILER -> "细节修复"
        WorkflowCapability.VIDEO -> "视频"
    }

    private fun statusLabel(status: String): String = chatStatusLabel(status)

    private fun durationText(start: Long?, end: Long?): String? {
        if (start == null || end == null || end <= start) return null
        val seconds = ((end - start) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds} 秒"
            seconds < 3600 -> "${seconds / 60} 分 ${seconds % 60} 秒"
            else -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
        }
    }

    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null) return "时间未知"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun shortId(promptId: String): String =
        if (promptId.length <= 8) promptId else promptId.take(8)
}
