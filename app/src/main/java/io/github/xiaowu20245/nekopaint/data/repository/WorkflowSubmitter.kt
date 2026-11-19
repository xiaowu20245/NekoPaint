package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowPresetMatcher

/** 单次提交在一批任务中的位置；单次提交使用 (0, 0, 1)。 */
data class SubmissionRunContext(
    val runIndex: Int = 0,
    val batchIndex: Int = 0,
    val batchCount: Int = 1,
)

data class PreparedSubmission(
    val template: WorkflowTemplate,
    val selected: List<ParameterCandidate>,
    val context: SubmissionRunContext,
)

/**
 * 单次提交的唯一入口。
 *
 * 只负责“按 runIndex 应用 seed → 合并 overrides → 调用 repository.submitSelected”；
 * 批量循环、取消、进度仍归 OperationCenter，避免出现第二套批量引擎。
 */
class WorkflowSubmitter(
    private val submitSelected: suspend (
        WorkflowTemplate,
        List<ParameterCandidate>,
        SubmissionRunContext,
    ) -> WorkflowSubmitResult,
) {

    fun prepare(
        template: WorkflowTemplate,
        overrides: List<ParameterCandidate> = emptyList(),
        seedStrategy: SeedStrategy = SeedStrategy.FIXED,
        context: SubmissionRunContext = SubmissionRunContext(),
    ): PreparedSubmission {
        val seeded = WorkflowPresetMatcher.applySeedForRun(
            template = template,
            strategy = seedStrategy,
            runIndex = context.runIndex,
        )
        val selected = mergeSelection(seeded.selectedCandidates, overrides)
        return PreparedSubmission(
            template = seeded,
            selected = selected,
            context = context,
        )
    }

    suspend fun submit(
        template: WorkflowTemplate,
        overrides: List<ParameterCandidate> = emptyList(),
        seedStrategy: SeedStrategy = SeedStrategy.FIXED,
        context: SubmissionRunContext = SubmissionRunContext(),
    ): WorkflowSubmitResult = submit(
        prepare(template, overrides, seedStrategy, context),
    )

    suspend fun submit(prepared: PreparedSubmission): WorkflowSubmitResult =
        submitSelected(prepared.template, prepared.selected, prepared.context)

    private fun mergeSelection(
        seeded: List<ParameterCandidate>,
        overrides: List<ParameterCandidate>,
    ): List<ParameterCandidate> {
        if (overrides.isEmpty()) return seeded
        val overrideById = overrides.filterNot { it.readOnly }.associateBy { it.id }
        val merged = seeded.map { candidate ->
            overrideById[candidate.id]?.copy(selected = true) ?: candidate
        }
        val seededIds = seeded.mapTo(mutableSetOf()) { it.id }
        return (merged + overrides.filter { !it.readOnly && it.id !in seededIds })
            .distinctBy { it.id }
    }
}
