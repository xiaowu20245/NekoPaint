package io.github.xiaowu20245.nekopaint.feature.chat

enum class ChatIntent {
    QUEUE,
    HISTORY,
    WORKFLOW,
    FAILURE,
    GENERAL,
}

/**
 * 本地只读意图路由。
 *
 * 先不依赖模型 function calling：从用户问题里识别要读取哪类本地状态，
 * 再把结构化摘要注入系统上下文。这样所有 OpenAI 兼容服务都能工作。
 */
object ChatIntentRouter {

    private val failureKeywords = listOf(
        "失败", "报错", "错误", "异常", "原因", "为什么", "中断",
        "超时", "oom", "内存不足", "挂了", "没成功",
        "failed", "error", "why", "cause", "timeout",
    )
    private val workflowKeywords = listOf(
        "工作流", "模板", "节点", "参数", "兼容", "蒙版", "图生图",
        "文生图", "重绘", "lora", "controlnet", "放大", "视频",
        "workflow", "template", "node", "inpaint", "upscale",
    )
    private val queueKeywords = listOf(
        "队列", "排队", "运行中", "等待中", "进度", "剩余", "还有几个",
        "当前任务", "在跑", "执行中",
        "queue", "running", "pending", "progress",
    )
    private val historyKeywords = listOf(
        "历史", "最近", "记录", "完成了", "成功", "耗时", "生成过",
        "history", "recent", "record", "success",
    )

    fun route(text: String): ChatIntent {
        val lower = text.lowercase()
        val failure = failureKeywords.count { lower.contains(it) }
        val workflow = workflowKeywords.count { lower.contains(it) }
        val queue = queueKeywords.count { lower.contains(it) }
        val history = historyKeywords.count { lower.contains(it) }

        return when {
            failure > 0 -> ChatIntent.FAILURE
            workflow > 0 -> ChatIntent.WORKFLOW
            queue > 0 -> ChatIntent.QUEUE
            history > 0 -> ChatIntent.HISTORY
            else -> ChatIntent.GENERAL
        }
    }
}
