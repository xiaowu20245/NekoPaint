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
    UNKNOWN,
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
    val imageSlots: List<WorkflowInputSlot> = emptyList(),
    val maskSlots: List<WorkflowInputSlot> = emptyList(),
    val issues: List<WorkflowIssue> = emptyList(),
    val catalogChecked: Boolean = false,
    val analyzerVersion: Int = 0,
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
    val compatibility: WorkflowCompatibility? = null,
) {
    val selectedCandidates: List<ParameterCandidate>
        get() = candidates.filter { it.selected && !it.readOnly }

    val selectedCount: Int
        get() = selectedCandidates.size
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
