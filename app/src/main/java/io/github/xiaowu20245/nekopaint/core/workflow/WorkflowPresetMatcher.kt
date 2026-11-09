package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.PresetValueEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import kotlin.random.Random

/**
 * 工作流参数预设构建、双重匹配回填与批量种子策略工具。
 */
object WorkflowPresetMatcher {

    /**
     * 从当前模板可编辑参数构建预设快照条目。
     * 同时保存精确 `candidateId` 与节点语义签名，兼容后续工作流节点增删。
     */
    fun buildEntries(candidates: List<ParameterCandidate>): List<PresetValueEntry> =
        candidates
            .filter { !it.readOnly && it.selected }
            .map { candidate ->
                PresetValueEntry(
                    candidateId = candidate.id,
                    classType = candidate.classType,
                    inputName = candidate.inputName,
                    label = candidate.label,
                    value = candidate.value,
                )
            }

    /**
     * 将预设应用到候选参数列表：
     * 1. 优先按精确 `candidateId`（如 "3:steps"）匹配；
     * 2. 若工作流节点编号变化导致精确 ID 未命中，回退按 `classType + inputName + label` 唯一匹配；
     * 3. 仍未命中但 `classType + inputName` 在未精确命中的条目中唯一、且当前候选中同签名目标唯一时，按 `classType + inputName` 兜底；
     * 4. 新增节点保留其自身当前值/默认值；已删除节点的旧预设条目自动忽略；
     * 5. 命中预设的参数会恢复 `selected = true`，确保简单模式下可见并可提交。
     */
    fun applyPreset(
        candidates: List<ParameterCandidate>,
        preset: WorkflowPreset,
    ): List<ParameterCandidate> {
        val exactMap = preset.entries.associateBy { it.candidateId }
        val currentIds = candidates.map { it.id }.toSet()
        // 仅那些原 candidateId 已不在当前工作流中的条目参与语义兜底，避免覆盖同类型另一个节点
        val orphanEntries = preset.entries.filter { it.candidateId !in currentIds }

        return candidates.map { candidate ->
            if (candidate.readOnly) return@map candidate

            val exact = exactMap[candidate.id]
            if (exact != null) {
                // 预设保存的是“当时已选参数”，回填时同步恢复选中状态，
                // 否则简单模式可能只改到值却看不到该参数。
                return@map candidate.copy(value = exact.value, selected = true)
            }

            val byFullSignature = orphanEntries.filter {
                it.classType.isNotBlank() &&
                    it.classType == candidate.classType &&
                    it.inputName == candidate.inputName &&
                    it.label == candidate.label
            }
            val fullSignatureTargets = candidates.count {
                it.classType == candidate.classType &&
                    it.inputName == candidate.inputName &&
                    it.label == candidate.label
            }
            if (byFullSignature.size == 1 && fullSignatureTargets == 1) {
                return@map candidate.copy(
                    value = byFullSignature.single().value,
                    selected = true,
                )
            }

            val byClassAndInput = orphanEntries.filter {
                it.classType.isNotBlank() &&
                    it.classType == candidate.classType &&
                    it.inputName == candidate.inputName
            }
            val classInputTargets = candidates.count {
                it.classType == candidate.classType &&
                    it.inputName == candidate.inputName
            }
            if (byClassAndInput.size == 1 && classInputTargets == 1) {
                return@map candidate.copy(
                    value = byClassAndInput.single().value,
                    selected = true,
                )
            }

            candidate
        }
    }

    /**
     * 恢复为工作流导入时的初始默认参数值。
     */
    fun resetToDefault(candidates: List<ParameterCandidate>): List<ParameterCandidate> =
        candidates.map { candidate ->
            if (candidate.readOnly) {
                candidate
            } else {
                candidate.copy(value = candidate.defaultValue)
            }
        }

    /**
     * 根据种子策略为单次或批量提交生成对应轮次的模板参数。
     *
     * - [SeedStrategy.RANDOM]：为每个可写 `seed` / `noise_seed` 节点生成新随机正整数，避免命中 ComfyUI 缓存；
     * - [SeedStrategy.INCREMENT]：在当前种子基础上按 `runIndex` 递增（`+0`, `+1`, `+2`...）；
     * - [SeedStrategy.FIXED]：保持面板当前种子不变。
     */
    fun applySeedForRun(
        template: WorkflowTemplate,
        strategy: SeedStrategy,
        runIndex: Int,
        randomSeedGenerator: () -> Long = { Random.nextLong(1L, 999_999_999_999_999L) },
    ): WorkflowTemplate {
        if (!template.hasSeedCandidate || strategy == SeedStrategy.FIXED) {
            return template
        }
        val updatedCandidates = template.candidates.map { candidate ->
            if (candidate.readOnly || candidate.inputName !in WorkflowTemplate.SEED_INPUT_NAMES) {
                return@map candidate
            }
            val nextSeed = when (strategy) {
                SeedStrategy.RANDOM -> randomSeedGenerator().coerceAtLeast(0L)
                SeedStrategy.INCREMENT -> {
                    val base = candidate.value.trim().toLongOrNull()
                        ?: candidate.defaultValue.trim().toLongOrNull()
                        ?: 1L
                    (base + runIndex).coerceAtLeast(0L)
                }
                SeedStrategy.FIXED -> return@map candidate
            }
            // 确保该种子参数被标记为 selected 以写入最终提交的 API JSON
            candidate.copy(
                value = nextSeed.toString(),
                selected = true,
            )
        }
        return template.copy(candidates = updatedCandidates)
    }
}
