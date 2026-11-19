package io.github.xiaowu20245.nekopaint.feature.history

/**
 * 历史多选策略：选择集只允许包含当前筛选可见且可操作的条目。
 * 纯集合函数，避免把“筛选后仍删除不可见记录”的回归写死在 UI 里。
 */
object HistorySelectionPolicy {

    fun prune(selected: Set<String>, selectable: Set<String>): Set<String> =
        selected.intersect(selectable)

    fun toggle(
        selected: Set<String>,
        promptId: String,
        selectable: Set<String>,
    ): Set<String> {
        if (promptId !in selectable) return selected
        return if (promptId in selected) selected - promptId else selected + promptId
    }

    fun toggleAll(selected: Set<String>, selectable: Set<String>): Set<String> =
        if (allSelected(selected, selectable)) emptySet() else selectable

    fun allSelected(selected: Set<String>, selectable: Set<String>): Boolean =
        selectable.isNotEmpty() && selected.containsAll(selectable)
}
