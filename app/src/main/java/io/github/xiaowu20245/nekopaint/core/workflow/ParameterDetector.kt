package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.BindingSource
import io.github.xiaowu20245.nekopaint.core.model.workflow.DetectionConfidence
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterConstraints
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowDetection
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowGraph
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

object ParameterDetector {

    private val samplerTypes = setOf("KSampler", "KSamplerAdvanced")

    private val promptSourceTypes = setOf(
        "WeiLinPromptUI",
        "WeiLinPromptUIWithoutLora",
        "CR Text",
        "PrimitiveString",
        "String",
        "Text",
    )

    private val noiseTypes = setOf(
        "Note",
        "Reroute",
        "SetNode",
        "GetNode",
        "PreviewImage",
        "PreviewAny",
        "Image Comparer (rgthree)",
        "GroupIgnoreManager",
        "DanbooruGalleryNode",
        "MaskPreview",
        "DrawBBoxes",
        "easy showAnything",
        "easy cleanGpuUsed",
    )

    private val blockedInputs = setOf(
        "temp_str",
        "temp_lora_str",
        "gmm_ui",
        "danbooru_gallery_widget",
        "selection_data",
        "filter_data",
        "rgthree_comparer",
        "Update inputs",
    )

    fun detect(
        graph: WorkflowGraph,
        catalog: NodeCatalog?,
    ): WorkflowDetection {
        val candidates = mutableListOf<ParameterCandidate>()
        val seen = mutableSetOf<String>()
        val reachable = graph.reachableNodeIds.mapNotNull { graph.nodes[it] }
        val promptRoles = promptRoleMap(graph)
        val stageLabels = samplerStageLabels(graph)

        fun add(candidate: ParameterCandidate) {
            if (seen.add(candidate.id)) {
                candidates.add(candidate)
            }
        }

        // First pass: prompt chains and linked size inputs.
        for (node in reachable) {
            if (node.classType in noiseTypes) continue
            for ((inputName, value) in node.inputs) {
                val link = value.asNodeLink(graph.nodes.keys)
                when {
                    node.classType == "CLIPTextEncode" && inputName == "text" -> {
                        val role = promptRoles[node.id] ?: roleFromTitle(node.title)
                        if (link == null) {
                            add(
                                buildCandidate(
                                    classType = node.classType,
                                    nodeId = node.id,
                                    inputName = inputName,
                                    label = role.label(),
                                    type = ParameterType.MULTILINE_TEXT,
                                    group = ParameterGroup.PROMPT,
                                    value = value,
                                    constraints = ParameterConstraints(),
                                    source = BindingSource.DIRECT,
                                    confidence = DetectionConfidence.HIGH,
                                    selected = true,
                                    catalog = catalog,
                                ),
                            )
                        } else {
                            val source = resolvePromptSource(graph, link.first)
                            if (source != null) {
                                val sourceNode = graph.nodes[source.nodeId]
                                add(
                                    buildCandidate(
                                        classType = sourceNode?.classType.orEmpty(),
                                        nodeId = source.nodeId,
                                        inputName = source.inputName,
                                        label = role.label(),
                                        type = ParameterType.MULTILINE_TEXT,
                                        group = ParameterGroup.PROMPT,
                                        value = source.value,
                                        constraints = ParameterConstraints(),
                                        source = BindingSource.UPSTREAM,
                                        upstreamNodeId = node.id,
                                        upstreamInput = inputName,
                                        confidence = DetectionConfidence.HIGH,
                                        selected = true,
                                        catalog = catalog,
                                    ),
                                )
                            } else {
                                add(
                                    ParameterCandidate(
                                        id = "${node.id}:text",
                                        nodeId = node.id,
                                        inputName = "text",
                                        label = "${role.label()}（由工作流内部生成）",
                                        type = ParameterType.MULTILINE_TEXT,
                                        group = ParameterGroup.PROMPT,
                                        source = BindingSource.UPSTREAM,
                                        upstreamNodeId = link.first,
                                        upstreamInput = "text",
                                        confidence = DetectionConfidence.LOW,
                                        selected = false,
                                        readOnly = true,
                                        classType = node.classType,
                                    ),
                                )
                            }
                        }
                    }

                    link != null &&
                        node.classType == "EmptyLatentImage" &&
                        (inputName == "width" || inputName == "height") -> {
                        val upstream = resolveUpstreamScalar(graph, link.first)
                        if (upstream != null) {
                            add(
                                buildCandidate(
                                    classType = graph.nodes[upstream.nodeId]?.classType.orEmpty(),
                                    nodeId = upstream.nodeId,
                                    inputName = upstream.inputName,
                                    label = if (inputName == "width") "宽度" else "高度",
                                    type = ParameterType.INT,
                                    group = ParameterGroup.IMAGE,
                                    value = upstream.value,
                                    constraints = ParameterConstraints(min = 64.0, max = 8192.0, step = 8.0),
                                    source = BindingSource.UPSTREAM,
                                    upstreamNodeId = node.id,
                                    upstreamInput = inputName,
                                    confidence = DetectionConfidence.HIGH,
                                    selected = true,
                                    catalog = catalog,
                                ),
                            )
                        }
                    }

                    link != null &&
                        node.classType == "ImageResizeKJv2" &&
                        (inputName == "width" || inputName == "height") -> {
                        val upstream = resolveUpstreamScalar(graph, link.first)
                        if (upstream != null) {
                            add(
                                buildCandidate(
                                    classType = graph.nodes[upstream.nodeId]?.classType.orEmpty(),
                                    nodeId = upstream.nodeId,
                                    inputName = upstream.inputName,
                                    label = if (inputName == "width") "最终宽度" else "最终高度",
                                    type = ParameterType.INT,
                                    group = ParameterGroup.IMAGE,
                                    value = upstream.value,
                                    constraints = ParameterConstraints(min = 0.0, max = 8192.0, step = 2.0),
                                    source = BindingSource.UPSTREAM,
                                    upstreamNodeId = node.id,
                                    upstreamInput = inputName,
                                    confidence = DetectionConfidence.HIGH,
                                    selected = true,
                                    catalog = catalog,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Second pass: direct scalar inputs with known semantics.
        for (node in reachable) {
            if (node.classType in noiseTypes) continue
            for ((inputName, value) in node.inputs) {
                if (inputName in blockedInputs) continue
                if (value.asNodeLink(graph.nodes.keys) != null) continue
                if (node.classType == "CLIPTextEncode" && inputName == "text") continue

                val known = knownInput(node.classType, inputName, stageLabels[node.id], node.title)
                if (known != null) {
                    add(
                        buildCandidate(
                            classType = node.classType,
                            nodeId = node.id,
                            inputName = inputName,
                            label = known.label,
                            type = known.type,
                            group = known.group,
                            value = value,
                            constraints = known.constraints ?: ParameterConstraints(),
                            source = BindingSource.DIRECT,
                            confidence = known.confidence,
                            selected = known.selected,
                            catalog = catalog,
                        ),
                    )
                } else {
                    add(
                        buildCandidate(
                            classType = node.classType,
                            nodeId = node.id,
                            inputName = inputName,
                            label = readableName(inputName),
                            type = ParameterType.UNKNOWN,
                            group = ParameterGroup.ADVANCED,
                            value = value,
                            constraints = ParameterConstraints(),
                            source = BindingSource.DIRECT,
                            confidence = DetectionConfidence.LOW,
                            selected = false,
                            catalog = catalog,
                        ),
                    )
                }
            }
        }

        val missing = catalog?.missingTypes(reachable.map { it.classType }).orEmpty()

        return WorkflowDetection(
            candidates = candidates.sortedWith(
                compareBy<ParameterCandidate> { it.group.ordinal }
                    .thenBy { it.confidence.ordinal }
                    .thenBy { it.nodeId.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.inputName },
            ),
            missingNodeTypes = missing,
            catalogChecked = catalog != null,
        )
    }

    private fun buildCandidate(
        classType: String,
        nodeId: String,
        inputName: String,
        label: String,
        type: ParameterType,
        group: ParameterGroup,
        value: JsonElement,
        constraints: ParameterConstraints,
        source: BindingSource,
        upstreamNodeId: String? = null,
        upstreamInput: String? = null,
        confidence: DetectionConfidence,
        selected: Boolean,
        catalog: NodeCatalog?,
    ): ParameterCandidate {
        val schema = catalog?.inputSchema(classType, inputName)
        val resolvedType = when {
            type != ParameterType.UNKNOWN -> type
            schema != null && schema.type != ParameterType.UNKNOWN -> schema.type
            else -> inferType(value)
        }
        val resolvedConstraints = when {
            schema != null && (
                schema.constraints.options.isNotEmpty() ||
                    schema.constraints.min != null ||
                    schema.constraints.max != null
                ) -> schema.constraints

            else -> constraints
        }

        return ParameterCandidate(
            id = "$nodeId:$inputName",
            nodeId = nodeId,
            inputName = inputName,
            label = label,
            type = resolvedType,
            group = group,
            defaultValue = value.scalarText().orEmpty(),
            value = value.scalarText().orEmpty(),
            constraints = resolvedConstraints,
            source = source,
            upstreamNodeId = upstreamNodeId,
            upstreamInput = upstreamInput,
            confidence = confidence,
            selected = selected,
            readOnly = false,
            classType = classType,
        )
    }

    private data class KnownInput(
        val label: String,
        val type: ParameterType,
        val group: ParameterGroup,
        val confidence: DetectionConfidence = DetectionConfidence.HIGH,
        val selected: Boolean = true,
        val constraints: ParameterConstraints? = null,
    )

    private fun samplingLabel(label: String, stageLabel: String?): String =
        if (!stageLabel.isNullOrBlank()) "$stageLabel $label" else label

    private fun knownInput(
        classType: String,
        inputName: String,
        stageLabel: String?,
        nodeTitle: String,
    ): KnownInput? = when (classType) {
        "KSampler" -> when (inputName) {
            "seed" -> KnownInput(samplingLabel("种子", stageLabel), ParameterType.INT, ParameterGroup.SAMPLING)
            "steps" -> KnownInput(samplingLabel("步数", stageLabel), ParameterType.INT, ParameterGroup.SAMPLING)
            "cfg" -> KnownInput(samplingLabel("CFG", stageLabel), ParameterType.FLOAT, ParameterGroup.SAMPLING)
            "sampler_name" -> KnownInput(
                samplingLabel("采样器", stageLabel),
                ParameterType.ENUM,
                ParameterGroup.SAMPLING,
                selected = false,
            )
            "scheduler" -> KnownInput(
                samplingLabel("调度器", stageLabel),
                ParameterType.ENUM,
                ParameterGroup.SAMPLING,
                selected = false,
            )
            "denoise" -> KnownInput(samplingLabel("重绘幅度", stageLabel), ParameterType.FLOAT, ParameterGroup.SAMPLING)
            else -> null
        }

        "KSamplerAdvanced" -> when (inputName) {
            "noise_seed" -> KnownInput(samplingLabel("种子", stageLabel), ParameterType.INT, ParameterGroup.SAMPLING)
            "steps" -> KnownInput(samplingLabel("步数", stageLabel), ParameterType.INT, ParameterGroup.SAMPLING)
            "cfg" -> KnownInput(samplingLabel("CFG", stageLabel), ParameterType.FLOAT, ParameterGroup.SAMPLING)
            "sampler_name" -> KnownInput(
                samplingLabel("采样器", stageLabel),
                ParameterType.ENUM,
                ParameterGroup.SAMPLING,
                selected = false,
            )
            "scheduler" -> KnownInput(
                samplingLabel("调度器", stageLabel),
                ParameterType.ENUM,
                ParameterGroup.SAMPLING,
                selected = false,
            )
            "start_at_step" -> KnownInput(
                samplingLabel("起始步", stageLabel),
                ParameterType.INT,
                ParameterGroup.SAMPLING,
                selected = false,
            )
            "end_at_step" -> KnownInput(
                samplingLabel("结束步", stageLabel),
                ParameterType.INT,
                ParameterGroup.SAMPLING,
                selected = false,
            )
            else -> null
        }

        "EmptyLatentImage" -> when (inputName) {
            "width" -> KnownInput("宽度", ParameterType.INT, ParameterGroup.IMAGE)
            "height" -> KnownInput("高度", ParameterType.INT, ParameterGroup.IMAGE)
            "batch_size" -> KnownInput(
                "批次",
                ParameterType.INT,
                ParameterGroup.IMAGE,
                selected = false,
            )
            else -> null
        }

        "CheckpointLoaderSimple" -> if (inputName == "ckpt_name") {
            KnownInput("模型", ParameterType.ENUM, ParameterGroup.MODEL)
        } else null

        "UNETLoader" -> if (inputName == "unet_name") {
            KnownInput("UNet 模型", ParameterType.ENUM, ParameterGroup.MODEL)
        } else null

        "CLIPLoader" -> if (inputName == "clip_name") {
            KnownInput("CLIP 模型", ParameterType.ENUM, ParameterGroup.MODEL, selected = false)
        } else null

        "VAELoader" -> if (inputName == "vae_name") {
            KnownInput("VAE", ParameterType.ENUM, ParameterGroup.MODEL, selected = false)
        } else null

        "LoraLoader", "LoraLoaderModelOnly" -> when (inputName) {
            "lora_name" -> KnownInput("LoRA", ParameterType.ENUM, ParameterGroup.MODEL, selected = false)
            "strength_model" -> KnownInput("LoRA 模型强度", ParameterType.FLOAT, ParameterGroup.MODEL, selected = false)
            "strength_clip" -> KnownInput("LoRA CLIP 强度", ParameterType.FLOAT, ParameterGroup.MODEL, selected = false)
            else -> null
        }

        "LoadImage" -> if (inputName == "image") {
            KnownInput("输入图片", ParameterType.IMAGE, ParameterGroup.IMAGE)
        } else null

        "SaveImage" -> if (inputName == "filename_prefix") {
            KnownInput(
                "输出前缀",
                ParameterType.TEXT,
                ParameterGroup.OTHER,
                confidence = DetectionConfidence.MEDIUM,
                selected = false,
            )
        } else null

        "ImageResizeKJv2" -> when (inputName) {
            "width" -> KnownInput("最终宽度", ParameterType.INT, ParameterGroup.IMAGE, selected = false)
            "height" -> KnownInput("最终高度", ParameterType.INT, ParameterGroup.IMAGE, selected = false)
            "upscale_method" -> KnownInput(
                "缩放算法",
                ParameterType.ENUM,
                ParameterGroup.IMAGE,
                confidence = DetectionConfidence.MEDIUM,
                selected = false,
            )
            else -> null
        }

        "ImageScaleBy" -> when (inputName) {
            "scale_by" -> KnownInput("放大倍数", ParameterType.FLOAT, ParameterGroup.IMAGE, selected = false)
            "upscale_method" -> KnownInput(
                "放大算法",
                ParameterType.ENUM,
                ParameterGroup.IMAGE,
                confidence = DetectionConfidence.MEDIUM,
                selected = false,
            )
            else -> null
        }

        "ControlNetApplyAdvanced" -> if (inputName == "strength") {
            KnownInput("ControlNet 强度", ParameterType.FLOAT, ParameterGroup.SAMPLING, selected = false)
        } else null

        "WeiLinPromptUI", "WeiLinPromptUIWithoutLora" -> if (inputName == "positive") {
            KnownInput("提示词", ParameterType.MULTILINE_TEXT, ParameterGroup.PROMPT)
        } else null

        "CR Text" -> if (inputName == "text") {
            KnownInput(
                nodeTitle.ifBlank { "文本" },
                ParameterType.MULTILINE_TEXT,
                ParameterGroup.PROMPT,
                selected = false,
            )
        } else null

        "INTConstant" -> if (inputName == "value") {
            KnownInput(
                "整数常量",
                ParameterType.INT,
                ParameterGroup.ADVANCED,
                confidence = DetectionConfidence.LOW,
                selected = false,
            )
        } else null

        "PrimitiveInt" -> if (inputName == "value") {
            KnownInput("整数", ParameterType.INT, ParameterGroup.ADVANCED, confidence = DetectionConfidence.MEDIUM, selected = false)
        } else null

        "PrimitiveFloat" -> if (inputName == "value") {
            KnownInput("小数", ParameterType.FLOAT, ParameterGroup.ADVANCED, confidence = DetectionConfidence.MEDIUM, selected = false)
        } else null

        "PrimitiveString" -> if (inputName == "value") {
            KnownInput("文本", ParameterType.MULTILINE_TEXT, ParameterGroup.ADVANCED, confidence = DetectionConfidence.MEDIUM, selected = false)
        } else null

        "PrimitiveBoolean" -> if (inputName == "value") {
            KnownInput("开关", ParameterType.BOOLEAN, ParameterGroup.ADVANCED, confidence = DetectionConfidence.MEDIUM, selected = false)
        } else null

        else -> null
    }

    private data class PromptSource(
        val nodeId: String,
        val inputName: String,
        val value: JsonElement,
    )

    private fun resolvePromptSource(graph: WorkflowGraph, startNodeId: String): PromptSource? {
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(startNodeId to 0)
        val visited = mutableSetOf<String>()
        val found = mutableListOf<Pair<Int, PromptSource>>()

        while (queue.isNotEmpty()) {
            val (nodeId, depth) = queue.removeFirst()
            if (depth > 8 || !visited.add(nodeId)) continue
            val node = graph.nodes[nodeId] ?: continue
            if (node.classType in promptSourceTypes) {
                val inputName = when {
                    node.classType.startsWith("WeiLin") -> "positive"
                    node.classType == "CR Text" -> "text"
                    else -> if (node.inputs.containsKey("value")) "value" else "text"
                }
                val value = node.inputs[inputName]
                if (value != null && value.asNodeLink(graph.nodes.keys) == null) {
                    val score = when {
                        node.classType.startsWith("WeiLin") -> 3000
                        node.classType == "CR Text" -> 2000
                        else -> 1000
                    } + (value.scalarText()?.length ?: 0)
                    found.add(score to PromptSource(nodeId, inputName, value))
                }
            }
            for (input in node.inputs.values) {
                val link = input.asNodeLink(graph.nodes.keys) ?: continue
                queue.add(link.first to depth + 1)
            }
        }

        return found.maxByOrNull { it.first }?.second
    }

    private data class UpstreamScalar(
        val nodeId: String,
        val inputName: String,
        val value: JsonElement,
    )

    private fun resolveUpstreamScalar(graph: WorkflowGraph, startNodeId: String): UpstreamScalar? {
        var currentId = startNodeId
        repeat(4) {
            val node = graph.nodes[currentId] ?: return null
            val preferred = listOf("value", "width", "height", "int", "float", "text")
            for (name in preferred + node.inputs.keys) {
                val value = node.inputs[name] ?: continue
                if (value.asNodeLink(graph.nodes.keys) == null) {
                    return UpstreamScalar(node.id, name, value)
                }
            }
            val next = node.inputs.values
                .firstNotNullOfOrNull { it.asNodeLink(graph.nodes.keys)?.first }
                ?: return null
            currentId = next
        }
        return null
    }

    private enum class PromptRole {
        POSITIVE,
        NEGATIVE,
        UNKNOWN,
    }

    private fun PromptRole.label(): String = when (this) {
        PromptRole.POSITIVE -> "正面提示词"
        PromptRole.NEGATIVE -> "负面提示词"
        PromptRole.UNKNOWN -> "提示词"
    }

    private fun promptRoleMap(graph: WorkflowGraph): Map<String, PromptRole> {
        val roles = mutableMapOf<String, PromptRole>()
        for (nodeId in graph.reachableNodeIds) {
            val node = graph.nodes[nodeId] ?: continue
            if (node.classType !in samplerTypes) continue
            val positive = node.inputs["positive"]?.asNodeLink(graph.nodes.keys)?.first
            val negative = node.inputs["negative"]?.asNodeLink(graph.nodes.keys)?.first
            if (positive != null) roles.putIfAbsent(positive, PromptRole.POSITIVE)
            if (negative != null) roles.putIfAbsent(negative, PromptRole.NEGATIVE)
        }
        return roles
    }

    private fun roleFromTitle(title: String): PromptRole {
        val lower = title.lowercase()
        return when {
            "负" in title || "negative" in lower -> PromptRole.NEGATIVE
            "正" in title || "positive" in lower -> PromptRole.POSITIVE
            else -> PromptRole.UNKNOWN
        }
    }

    private fun samplerStageLabels(graph: WorkflowGraph): Map<String, String> {
        val samplers = topologicalOrder(graph).filter {
            graph.nodes[it]?.classType in samplerTypes
        }
        return samplers.mapIndexed { index, id ->
            id to if (samplers.size > 1) "采样 ${index + 1}" else ""
        }.toMap()
    }

    private fun topologicalOrder(graph: WorkflowGraph): List<String> {
        val reachable = graph.reachableNodeIds
        val indegree = reachable.associateWith { 0 }.toMutableMap()
        val outgoing = reachable.associateWith { mutableListOf<String>() }.toMutableMap()
        for (nodeId in reachable) {
            val node = graph.nodes[nodeId] ?: continue
            for (value in node.inputs.values) {
                val link = value.asNodeLink(graph.nodes.keys) ?: continue
                if (link.first in indegree) {
                    indegree[nodeId] = (indegree[nodeId] ?: 0) + 1
                    outgoing.getOrPut(link.first) { mutableListOf() }.add(nodeId)
                }
            }
        }
        val queue = ArrayDeque(reachable.filter { indegree[it] == 0 }.sorted())
        val result = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()
            result.add(nodeId)
            for (child in outgoing[nodeId].orEmpty()) {
                if (child !in indegree) continue
                val next = (indegree[child] ?: 0) - 1
                indegree[child] = next
                if (next == 0) queue.addLast(child)
            }
        }
        if (result.size < reachable.size) {
            result.addAll(reachable.filterNot { it in result }.sorted())
        }
        return result
    }

    private fun readableName(name: String): String =
        name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun inferType(value: JsonElement): ParameterType {
        val primitive = value as? JsonPrimitive ?: return ParameterType.UNKNOWN
        return when {
            primitive.booleanOrNull != null -> ParameterType.BOOLEAN
            primitive.intOrNull != null -> ParameterType.INT
            primitive.doubleOrNull != null -> ParameterType.FLOAT
            primitive.contentOrNull != null -> ParameterType.TEXT
            else -> ParameterType.UNKNOWN
        }
    }
}
