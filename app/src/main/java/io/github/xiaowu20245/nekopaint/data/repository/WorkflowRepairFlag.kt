package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate

internal object WorkflowRepairFlag {
    fun effective(
        stored: Boolean,
        incoming: Boolean,
        currentView: Boolean?,
    ): Boolean = stored || incoming || currentView == true

    fun blocksSubmit(view: WorkflowTemplate?, stored: WorkflowTemplate?): Boolean =
        view?.repairRequired == true || stored?.repairRequired == true
}
