package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset

/**
 * 普通保存/提交前对候选参数视图做结构校验。
 *
 * repository 是持久化边界，不能假设调用方总是把 UI 或检测器生成的合法候选传进来。
 * 校验只接受标量参数视图：id/节点/输入必须完整且一致，只读标记不能被普通保存翻转，
 * 同一模板内不能出现重复 id，数值/布尔候选的当前值必须能按声明类型解析。
 */
internal object WorkflowCandidateSafety {

    fun validate(
        candidates: List<ParameterCandidate>,
        reference: List<ParameterCandidate> = emptyList(),
    ) {
        val seen = mutableSetOf<String>()
        val referenceById = reference.associateBy { it.id }
        val referenceByKey = reference.associateBy { it.nodeId to it.inputName }
        candidates.forEach { candidate ->
            require(candidate.id.isNotBlank()) { "候选参数缺少 id" }
            val label = candidate.id
            require(seen.add(candidate.id)) { "候选参数 id 重复：$label" }
            require(candidate.nodeId.isNotBlank()) { "候选参数 $label 缺少节点 id" }
            require(candidate.inputName.isNotBlank()) { "候选参数 $label 缺少输入名" }
            require(candidate.id == "${candidate.nodeId}:${candidate.inputName}") {
                "候选参数 $label 与节点/输入不一致"
            }
            require((candidate.upstreamNodeId == null) == (candidate.upstreamInput == null)) {
                "候选参数 $label 的上游绑定不完整"
            }
            if (candidate.readOnly) {
                require(!candidate.selected) { "只读候选参数 $label 不能选中" }
            }
            validateScalarValue(candidate, label)

            if (reference.isNotEmpty()) {
                val stored = referenceById[candidate.id]
                    ?: referenceByKey[candidate.nodeId to candidate.inputName]
                require(stored != null) {
                    "候选参数视图已变化，请先修复工作流"
                }
                require(!(stored.readOnly && !candidate.readOnly)) {
                    "只读候选参数 $label 不能改为可编辑"
                }
                require(stored.type == candidate.type) {
                    "候选参数 $label 的类型已变化，请先修复工作流"
                }
                require(stored.group == candidate.group) {
                    "候选参数 $label 的分组已变化，请先修复工作流"
                }
            }
        }
    }

    /**
     * 修复工作流时是否可以把旧候选的值/选中状态迁移到新检测结果。
     * 只有同类标量参数、旧值能被新类型解析且双方都可编辑时才迁移。
     */
    fun canReuseValue(old: ParameterCandidate, new: ParameterCandidate): Boolean {
        if (old.readOnly || new.readOnly) return false
        if (old.type != new.type || old.group != new.group) return false
        if (old.classType.isNotBlank() &&
            new.classType.isNotBlank() &&
            old.classType != new.classType
        ) {
            return false
        }
        return isScalarValue(new.type, old.value)
    }

    fun isPrimaryPromptCandidate(candidate: ParameterCandidate): Boolean =
        candidate.group == ParameterGroup.PROMPT &&
            !candidate.readOnly &&
            candidate.type in PROMPT_TYPES

    /**
     * 修复后清理预设中已经无法落到任何可编辑参数上的条目，避免静默保留失效快照。
     */
    fun sanitizePresets(
        presets: List<WorkflowPreset>,
        candidates: List<ParameterCandidate>,
    ): List<WorkflowPreset> {
        if (presets.isEmpty()) return presets
        val byId = candidates.associateBy { it.id }
        return presets.mapNotNull { preset ->
            val entries = preset.entries.filter { entry ->
                val target = resolvePresetTarget(entry.candidateId, entry.classType, entry.inputName, entry.label, byId, candidates)
                target != null && !target.readOnly && isScalarValue(target.type, entry.value)
            }
            if (entries.isEmpty()) null else preset.copy(entries = entries)
        }
    }

    private fun resolvePresetTarget(
        candidateId: String,
        classType: String,
        inputName: String,
        label: String,
        byId: Map<String, ParameterCandidate>,
        candidates: List<ParameterCandidate>,
    ): ParameterCandidate? {
        byId[candidateId]?.let { return it }
        if (classType.isBlank()) return null
        val fullSignature = candidates.filter {
            it.classType == classType && it.inputName == inputName && it.label == label
        }
        if (fullSignature.size == 1) return fullSignature.single()
        if (fullSignature.isNotEmpty()) return null
        val classAndInput = candidates.filter {
            it.classType == classType && it.inputName == inputName
        }
        return classAndInput.singleOrNull()
    }

    private fun validateScalarValue(candidate: ParameterCandidate, label: String) {
        require(isScalarValue(candidate.type, candidate.value)) {
            "候选参数 $label 的值与类型不匹配"
        }
        require(isScalarValue(candidate.type, candidate.defaultValue)) {
            "候选参数 $label 的默认值与类型不匹配"
        }
    }

    private fun isScalarValue(type: ParameterType, value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true
        return when (type) {
            ParameterType.INT -> trimmed.toLongOrNull() != null
            ParameterType.FLOAT -> trimmed.toDoubleOrNull()?.isFinite() == true
            ParameterType.BOOLEAN -> trimmed.equals("true", ignoreCase = true) ||
                trimmed.equals("false", ignoreCase = true)

            ParameterType.TEXT,
            ParameterType.MULTILINE_TEXT,
            ParameterType.ENUM,
            ParameterType.IMAGE,
            ParameterType.UNKNOWN,
            -> true
        }
    }

    private val PROMPT_TYPES = setOf(
        ParameterType.TEXT,
        ParameterType.MULTILINE_TEXT,
        ParameterType.UNKNOWN,
    )
}
