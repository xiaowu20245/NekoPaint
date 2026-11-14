package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class WorkflowFormat {
    API,
    UI,
    UNKNOWN,
}

enum class ParameterType {
    TEXT,
    MULTILINE_TEXT,
    INT,
    FLOAT,
    BOOLEAN,
    ENUM,
    IMAGE,
    UNKNOWN,
}

enum class ParameterGroup {
    PROMPT,
    IMAGE,
    SAMPLING,
    MODEL,
    ADVANCED,
    OTHER,
}

enum class BindingSource {
    DIRECT,
    UPSTREAM,
}

enum class DetectionConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

@Serializable
data class ParameterConstraints(
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<String> = emptyList(),
)

@Serializable
data class ParameterCandidate(
    val id: String,
    val nodeId: String,
    val inputName: String,
    val label: String,
    val type: ParameterType,
    val group: ParameterGroup,
    val defaultValue: String = "",
    val value: String = "",
    val constraints: ParameterConstraints = ParameterConstraints(),
    val source: BindingSource = BindingSource.DIRECT,
    val upstreamNodeId: String? = null,
    val upstreamInput: String? = null,
    val confidence: DetectionConfidence = DetectionConfidence.MEDIUM,
    val selected: Boolean = false,
    val readOnly: Boolean = false,
    val classType: String = "",
)

enum class WorkflowMode {
    TEXT_TO_IMAGE,
    IMAGE_TO_IMAGE,
    MASK_INPAINT,
    AUTO_DETAIL,
    VIDEO,
    UNKNOWN,
}

@Serializable
enum class WorkflowCapability {
    CONTROL_NET,
    UPSCALE,
    DETAILER,
    VIDEO,
}

enum class WorkflowSlotKind {
    IMAGE,
    MASK,
}

enum class WorkflowIssueSeverity {
    INFO,
    WARNING,
    ERROR,
}

@Serializable
data class WorkflowInputSlot(
    val nodeId: String,
    val inputName: String,
    val title: String,
    val kind: WorkflowSlotKind,
    val currentValue: String = "",
)

@Serializable
data class WorkflowIssue(
    val severity: WorkflowIssueSeverity,
    val nodeId: String? = null,
    val message: String,
)

@Serializable
data class WorkflowCompatibility(
    val mode: WorkflowMode = WorkflowMode.UNKNOWN,
    val capabilities: Set<WorkflowCapability> = emptySet(),
    val imageSlots: List<WorkflowInputSlot> = emptyList(),
    val maskSlots: List<WorkflowInputSlot> = emptyList(),
    val issues: List<WorkflowIssue> = emptyList(),
    val catalogChecked: Boolean = false,
    val analyzerVersion: Int = 0,
)

@Serializable
enum class SeedStrategy {
    RANDOM,
    INCREMENT,
    FIXED,
}

/**
 * 预设中的单个参数快照条目。
 *
 * 同时记录精确 `candidateId`（如 "3:steps"）和语义签名（`classType + inputName + label`），
 * 以便未来工作流增删节点或节点 ID 变化时仍能按语义兜底回填。
 */
@Serializable
data class PresetValueEntry(
    val candidateId: String,
    val classType: String = "",
    val inputName: String = "",
    val label: String = "",
    val value: String,
)

@Serializable
data class WorkflowPreset(
    val id: String,
    val name: String,
    val entries: List<PresetValueEntry> = emptyList(),
    val updatedAt: Long = 0L,
)

/**
 * 提示词标签组，名称以 `#名称` 出现在提示词文本中，提交时展开为 [tags]。
 * 组数据随工作流模板持久化，按工作流独立管理。
 */
@Serializable
data class PromptTagGroup(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class WorkflowTemplate(
    val id: String,
    val name: String,
    val sourceFileName: String,
    val sourceHash: String,
    val importedAt: Long,
    val nodeCount: Int,
    val reachableNodeCount: Int,
    val outputNodeIds: List<String> = emptyList(),
    val missingNodeTypes: List<String> = emptyList(),
    val catalogChecked: Boolean = false,
    val candidates: List<ParameterCandidate> = emptyList(),
    val primaryPromptCandidateId: String? = null,
    val compatibility: WorkflowCompatibility? = null,
    val presets: List<WorkflowPreset> = emptyList(),
    val activePresetId: String? = null,
    val promptTagGroups: List<PromptTagGroup> = emptyList(),
) {
    val selectedCandidates: List<ParameterCandidate>
        get() = candidates.filter { it.selected && !it.readOnly }

    val selectedCount: Int
        get() = selectedCandidates.size

    val hasSeedCandidate: Boolean
        get() = candidates.any { !it.readOnly && it.inputName in SEED_INPUT_NAMES }

    val primaryPromptPreview: String?
        get() = candidates.firstOrNull {
            it.selected &&
                it.group == ParameterGroup.PROMPT &&
                it.value.isNotBlank() &&
                !it.label.contains("负") &&
                !it.label.contains("反")
        }?.value?.trim()?.replace(Regex("\\s+"), " ")

    companion object {
        val SEED_INPUT_NAMES = setOf("seed", "noise_seed")
    }
}

data class WorkflowNode(
    val id: String,
    val classType: String,
    val title: String,
    val inputs: Map<String, JsonElement>,
)

data class WorkflowGraph(
    val nodes: Map<String, WorkflowNode>,
    val outputNodeIds: Set<String>,
    val reachableNodeIds: Set<String>,
    val format: WorkflowFormat,
)

data class WorkflowDetection(
    val candidates: List<ParameterCandidate>,
    val missingNodeTypes: List<String>,
    val catalogChecked: Boolean,
)

data class UploadedImageRef(
    val reference: String,
    val fileName: String,
)
