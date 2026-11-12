package io.github.xiaowu20245.nekopaint.feature.history

import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ExtractedTaskMetadata(
    val prompt: String? = null,
    val negativePrompt: String? = null,
    val seed: String? = null,
    val steps: String? = null,
    val cfg: String? = null,
    val sampler: String? = null,
    val scheduler: String? = null,
    val model: String? = null,
    val width: String? = null,
    val height: String? = null,
) {
    val sizeText: String?
        get() = if (width != null && height != null) "${width}x${height}" else null

    val samplerText: String?
        get() = when {
            sampler != null && scheduler != null -> "$sampler / $scheduler"
            sampler != null -> sampler
            scheduler != null -> scheduler
            else -> null
        }
}

object HistoryMetadataExtractor {

    fun extract(task: TaskRecord?): ExtractedTaskMetadata {
        if (task == null || task.values.isEmpty()) {
            return ExtractedTaskMetadata()
        }
        val values = task.values
        var positivePrompt: String? = null
        var negativePrompt: String? = null
        var seed: String? = null
        var steps: String? = null
        var cfg: String? = null
        var sampler: String? = null
        var scheduler: String? = null
        var model: String? = null
        var width: String? = null
        var height: String? = null

        val textCandidates = mutableListOf<Pair<String, String>>()

        for ((key, rawValue) in values) {
            val lowerKey = key.lowercase()
            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) continue

            when {
                lowerKey.endsWith(":seed") || lowerKey.endsWith(":noise_seed") || lowerKey == "seed" -> {
                    seed = trimmed
                }
                lowerKey.endsWith(":steps") || lowerKey == "steps" -> {
                    steps = trimmed
                }
                lowerKey.endsWith(":cfg") || lowerKey == "cfg" -> {
                    cfg = trimmed
                }
                lowerKey.endsWith(":sampler_name") || lowerKey == "sampler_name" || lowerKey.endsWith(":sampler") -> {
                    sampler = trimmed
                }
                lowerKey.endsWith(":scheduler") || lowerKey == "scheduler" -> {
                    scheduler = trimmed
                }
                lowerKey.endsWith(":ckpt_name") || lowerKey.endsWith(":unet_name") || lowerKey.endsWith(":model_name") || lowerKey == "ckpt_name" -> {
                    model = trimmed.substringAfterLast('/').substringAfterLast('\\')
                }
                lowerKey.endsWith(":width") || lowerKey == "width" -> {
                    width = trimmed
                }
                lowerKey.endsWith(":height") || lowerKey == "height" -> {
                    height = trimmed
                }
                lowerKey.endsWith(":text") || lowerKey.endsWith(":prompt") || lowerKey == "text" || lowerKey == "prompt" -> {
                    textCandidates.add(key to trimmed)
                }
            }
        }

        for ((key, text) in textCandidates) {
            val lowerKey = key.lowercase()
            val lowerText = text.lowercase()
            if (lowerKey.contains("neg") || lowerText.contains("worst quality") || lowerText.contains("low quality") || lowerText.contains("bad anatomy")) {
                if (negativePrompt == null) negativePrompt = text
            } else {
                if (positivePrompt == null) positivePrompt = text
                else if (negativePrompt == null) negativePrompt = text
            }
        }

        return ExtractedTaskMetadata(
            prompt = positivePrompt,
            negativePrompt = negativePrompt,
            seed = seed,
            steps = steps,
            cfg = cfg,
            sampler = sampler,
            scheduler = scheduler,
            model = model,
            width = width,
            height = height,
        )
    }

    fun extractPrompt(task: TaskRecord?, item: QueueItem?): String? {
        val fromTask = extract(task).prompt
        if (!fromTask.isNullOrBlank()) return fromTask

        val promptObj = item?.prompt ?: return null
        for ((_, nodeElement) in promptObj) {
            val node = nodeElement as? JsonObject ?: continue
            val classType = (node["class_type"] as? JsonPrimitive)?.contentOrNull
            if (classType == "CLIPTextEncode") {
                val inputs = node["inputs"] as? JsonObject ?: continue
                val text = (inputs["text"] as? JsonPrimitive)?.contentOrNull
                if (!text.isNullOrBlank()) {
                    return text
                }
            }
        }
        return null
    }
}
