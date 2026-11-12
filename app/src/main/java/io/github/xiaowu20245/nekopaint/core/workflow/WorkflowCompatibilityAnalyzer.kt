package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCapability
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowCompatibility
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowGraph
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowInputSlot
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowIssue
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowIssueSeverity
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowSlotKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 只读的兼容性检查：判断工作流需要什么外部输入、缺什么必需连接、是否依赖 UI 状态。
 *
 * 不修改节点图；所有结论都基于原始 API JSON。
 */
object WorkflowCompatibilityAnalyzer {

    const val CURRENT_VERSION = 3

    private val detailerClassNames = setOf(
        "DetailerForEach",
        "DetailerForEachDebug",
        "FaceDetailer",
        "SAM3_Detect",
        "MaskToSEGS",
        "INPAINT_ExpandMask",
    )

    private val upscaleClassNames = setOf(
        "ImageUpscaleWithModel",
        "UltimateSDUpscale",
        "UpscaleModelLoader",
        "ESRGAN",
        "SwinIR",
    )

    private val uiOnlyNodes = setOf(
        "GroupIgnoreManager",
        "Image Comparer (rgthree)",
        "PreviewBridge",
    )

    private val textConsumingInputs = setOf(
        "text",
        "positive",
        "negative",
        "opt_text",
        "temp_str",
        "wildcard",
    )

    fun analyze(
        graph: WorkflowGraph,
        catalog: NodeCatalog?,
        missingNodeTypes: List<String> = emptyList(),
    ): WorkflowCompatibility {
        val reachable = graph.reachableNodeIds
        val consumers = buildConsumers(graph)

        val imageSlots = mutableListOf<WorkflowInputSlot>()
        val maskSlots = mutableListOf<WorkflowInputSlot>()
        val issues = mutableListOf<WorkflowIssue>()

        for (node in graph.nodes.values) {
            val isLoadImage = isImageLoader(node.classType)
            if (node.id !in reachable) {
                if (isLoadImage) {
                    issues += WorkflowIssue(
                        severity = WorkflowIssueSeverity.WARNING,
                        nodeId = node.id,
                        message = "节点 ${node.id}（${node.classType}）没有接入输出链路",
                    )
                }
                continue
            }

            if (isLoadImage) {
                val usedSlots = consumers[node.id].orEmpty().map { it.slot }.toSet()
                val current = (node.inputs["image"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val usesImage = 0 in usedSlots
                val usesMask = 1 in usedSlots || node.classType == "LoadImageMask"
                if (usesImage) {
                    imageSlots += WorkflowInputSlot(
                        nodeId = node.id,
                        inputName = "image",
                        title = node.title,
                        kind = WorkflowSlotKind.IMAGE,
                        currentValue = current,
                    )
                }
                if (usesMask) {
                    maskSlots += WorkflowInputSlot(
                        nodeId = node.id,
                        inputName = "image",
                        title = node.title,
                        kind = WorkflowSlotKind.MASK,
                        currentValue = current,
                    )
                }
                if (!usesImage && !usesMask) {
                    issues += WorkflowIssue(
                        severity = WorkflowIssueSeverity.WARNING,
                        nodeId = node.id,
                        message = "LoadImage 节点 ${node.id} 没有下游消费者",
                    )
                }
            }

            if (node.classType == "DanbooruGalleryNode") {
                val promptBridges = consumers[node.id].orEmpty().filter { it.slot == 1 }
                val textTargets = promptBridges.flatMap { bridge ->
                    consumers[bridge.nodeId].orEmpty()
                        .filter { it.inputName in textConsumingInputs }
                        .map { bridge to it }
                }
                if (textTargets.isNotEmpty()) {
                    val path = textTargets.joinToString {
                        "节点 ${it.first.nodeId} → 节点 ${it.second.nodeId}.${it.second.inputName}"
                    }
                    issues += WorkflowIssue(
                        severity = WorkflowIssueSeverity.ERROR,
                        nodeId = node.id,
                        message = "D站画廊包含外部提示词，并接入生成链路：$path",
                    )
                } else {
                    issues += WorkflowIssue(
                        severity = WorkflowIssueSeverity.WARNING,
                        nodeId = node.id,
                        message = "D站画廊包含外部提示词/图片数据，可能依赖外部网络",
                    )
                }
            }

            if (node.classType in uiOnlyNodes) {
                issues += WorkflowIssue(
                    severity = WorkflowIssueSeverity.INFO,
                    nodeId = node.id,
                    message = "节点 ${node.id}（${node.classType}）可能依赖 ComfyUI 页面状态",
                )
            }

            for ((inputName, inputValue) in node.inputs) {
                val text = (inputValue as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (text.startsWith("$") &&
                    inputName in setOf("image", "images", "mask", "model", "clip")
                ) {
                    issues += WorkflowIssue(
                        severity = WorkflowIssueSeverity.ERROR,
                        nodeId = node.id,
                        message = "节点 ${node.id} 的 $inputName 使用了 UI 占位值 `$text`",
                    )
                }
                if (inputName == "rgthree_comparer") {
                    issues += WorkflowIssue(
                        severity = WorkflowIssueSeverity.INFO,
                        nodeId = node.id,
                        message = "节点 ${node.id} 包含 UI 比较器配置，API 中可能只是占位",
                    )
                }
            }

            if (catalog != null) {
                val required = catalog.requiredInputNames(node.classType)
                for (inputName in required) {
                    if (inputName !in node.inputs) {
                        issues += WorkflowIssue(
                            severity = WorkflowIssueSeverity.ERROR,
                            nodeId = node.id,
                            message = "节点 ${node.id}（${node.classType}）缺少必需输入 `$inputName`",
                        )
                    }
                }
            }
        }

        missingNodeTypes.forEach { type ->
            issues += WorkflowIssue(
                severity = WorkflowIssueSeverity.ERROR,
                message = "服务器缺少节点类型 `$type`",
            )
        }

        if (catalog == null) {
            issues += WorkflowIssue(
                severity = WorkflowIssueSeverity.WARNING,
                message = "未连接服务器，未检查必需输入和节点类型",
            )
        }

        val reachableNodes = graph.nodes.values.filter { it.id in reachable }
        val hasAutoDetail = reachableNodes.any { isDetailer(it.classType) }
        val hasLatent = reachableNodes.any { isLatentGenerator(it.classType) }
        val hasVideo = reachableNodes.any { isVideoNode(it.classType) }
        val hasMaskInput = reachableNodes.any { node ->
            node.inputs.any { (name, value) ->
                name.contains("mask", ignoreCase = true) &&
                    value.asNodeLink(graph.nodes.keys) != null
            }
        }
        val hasControlNet = reachableNodes.any {
            it.classType.contains("controlnet", ignoreCase = true)
        }
        val hasUpscale = reachableNodes.any {
            it.classType in upscaleClassNames ||
                it.classType.contains("upscale", ignoreCase = true) ||
                it.classType.contains("esrgan", ignoreCase = true) ||
                it.classType.contains("swinir", ignoreCase = true)
        }
        val capabilities = buildSet {
            if (hasControlNet) add(WorkflowCapability.CONTROL_NET)
            if (hasUpscale) add(WorkflowCapability.UPSCALE)
            if (hasAutoDetail) add(WorkflowCapability.DETAILER)
            if (hasVideo) add(WorkflowCapability.VIDEO)
        }
        val mode = when {
            // 不含 latent 生成器的细节修复工作流，以既有图片为输入，主分类仍是自动重绘。
            hasAutoDetail && !hasLatent -> WorkflowMode.AUTO_DETAIL
            maskSlots.isNotEmpty() || (hasMaskInput && imageSlots.isNotEmpty()) ->
                WorkflowMode.MASK_INPAINT
            hasVideo -> WorkflowMode.VIDEO
            imageSlots.isNotEmpty() -> WorkflowMode.IMAGE_TO_IMAGE
            hasLatent -> WorkflowMode.TEXT_TO_IMAGE
            else -> WorkflowMode.UNKNOWN
        }

        return WorkflowCompatibility(
            mode = mode,
            capabilities = capabilities,
            imageSlots = imageSlots,
            maskSlots = maskSlots,
            issues = issues.distinct(),
            catalogChecked = catalog != null,
            analyzerVersion = CURRENT_VERSION,
        )
    }

    private fun isImageLoader(classType: String): Boolean =
        classType.contains("LoadImage", ignoreCase = true) ||
            classType.contains("ImageLoader", ignoreCase = true)

    private fun isDetailer(classType: String): Boolean =
        classType in detailerClassNames ||
            classType.contains("Detailer", ignoreCase = true)

    private fun isLatentGenerator(classType: String): Boolean {
        if (isVideoNode(classType)) return false
        val lower = classType.lowercase()
        return lower.contains("empty") && lower.contains("latent")
    }

    private fun isVideoNode(classType: String): Boolean {
        val lower = classType.lowercase()
        return lower.contains("loadvideo") ||
            lower.contains("videoloader") ||
            lower.contains("wanimagetovideo") ||
            lower.contains("wanvideo") ||
            lower.contains("svd") ||
            lower.contains("hunyuanvideo") ||
            lower.contains("latentvideo")
    }

    private fun buildConsumers(
        graph: WorkflowGraph,
    ): Map<String, List<Consumer>> {
        val result = mutableMapOf<String, MutableList<Consumer>>()
        val nodeIds = graph.nodes.keys
        for (node in graph.nodes.values) {
            for ((inputName, value) in node.inputs) {
                val link = value.asNodeLink(nodeIds) ?: continue
                result.getOrPut(link.first) { mutableListOf() }
                    .add(Consumer(nodeId = node.id, inputName = inputName, slot = link.second))
            }
        }
        return result
    }

    private data class Consumer(
        val nodeId: String,
        val inputName: String,
        val slot: Int,
    )
}
