package io.github.xiaowu20245.nekopaint.data.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * 历史页“回填至工作流”的一次性参数中转。
 *
 * 只把历史参数带入详情页供用户确认，不直接覆盖已保存模板；
 * 用户在工作流详情页主动保存后才会真正落盘。
 */
class PendingWorkflowRefillStore {

    private val pending = ConcurrentHashMap<String, Map<String, String>>()

    fun put(templateId: String, values: Map<String, String>) {
        if (templateId.isBlank() || values.isEmpty()) return
        pending[templateId] = values.toMap()
    }

    fun consume(templateId: String): Map<String, String>? = pending.remove(templateId)
}
