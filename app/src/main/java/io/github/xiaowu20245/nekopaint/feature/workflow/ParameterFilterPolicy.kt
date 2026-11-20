package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate

/** 高级参数列表的快捷筛选。 */
enum class ParameterQuickFilter {
    ALL,
    SELECTED,
    READ_ONLY,
    ERROR,
}

/**
 * 参数筛选的纯策略，避免把“已选/只读/错误”的判定散落在 Compose 代码中。
 */
object ParameterFilterPolicy {

    fun matches(
        candidate: ParameterCandidate,
        filter: ParameterQuickFilter,
        error: FieldError?,
    ): Boolean = when (filter) {
        ParameterQuickFilter.ALL -> true
        ParameterQuickFilter.SELECTED -> candidate.selected
        ParameterQuickFilter.READ_ONLY -> candidate.readOnly
        ParameterQuickFilter.ERROR -> error != null
    }

    fun apply(
        candidates: List<ParameterCandidate>,
        fieldErrors: Map<String, FieldError>,
        filter: ParameterQuickFilter,
    ): List<ParameterCandidate> {
        if (filter == ParameterQuickFilter.ALL) return candidates
        return candidates.filter { candidate ->
            matches(candidate, filter, fieldErrors[candidate.id])
        }
    }
}
