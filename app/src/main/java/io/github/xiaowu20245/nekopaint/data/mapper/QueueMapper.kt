package io.github.xiaowu20245.nekopaint.data.mapper

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.network.ComfyImageUrlBuilder
import io.github.xiaowu20245.nekopaint.core.network.HistoryItemDto
import io.github.xiaowu20245.nekopaint.core.network.QueueDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * `/queue` 和 `/history` 的 DTO → 领域模型映射。
 *
 * ComfyUI 的历史结构比较松散，状态需要同时看 `status_str` 和事件消息，
 * 所以集中放在这里并补单元测试，避免 UI 层猜测。
 */
object QueueMapper {

    fun toQueueSnapshot(
        dto: QueueDto,
        now: Long = System.currentTimeMillis(),
    ): QueueSnapshot = QueueSnapshot(
        running = dto.running.mapNotNull { it.toQueueItem() },
        pending = dto.pending.mapNotNull { it.toQueueItem() },
        updatedAt = now,
    )

    fun toHistoryEntries(
        dto: Map<String, HistoryItemDto>,
        baseUrl: String,
    ): List<HistoryEntry> = dto
        .mapNotNull { (promptId, item) -> item.toHistoryEntry(promptId, baseUrl) }
        .sortedByDescending { it.timestamp ?: 0L }

    private fun JsonArray.toQueueItem(): QueueItem? {
        val promptId = (getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: return null
        val prompt = getOrNull(2) as? JsonObject
        val extra = getOrNull(3) as? JsonObject
        val clientId = (extra?.get("client_id") as? JsonPrimitive)?.contentOrNull
        val titles = prompt?.mapValues { (_, nodeElement) ->
            val node = nodeElement as? JsonObject
            val meta = node?.get("_meta") as? JsonObject
            (meta?.get("title") as? JsonPrimitive)?.contentOrNull
                ?: (node?.get("class_type") as? JsonPrimitive)?.contentOrNull
                ?: "节点"
        }.orEmpty()
        return QueueItem(
            number = (getOrNull(0) as? JsonPrimitive)?.intOrNull,
            promptId = promptId,
            clientId = clientId,
            prompt = prompt,
            nodeTitles = titles,
        )
    }

    private fun HistoryItemDto.toHistoryEntry(
        promptId: String,
        baseUrl: String,
    ): HistoryEntry {
        val images = outputs.values.flatMap { output ->
            val imagesArray = (output as? JsonObject)?.get("images") as? JsonArray
                ?: return@flatMap emptyList()
            imagesArray.mapNotNull { imageElement ->
                val image = imageElement as? JsonObject ?: return@mapNotNull null
                val filename = (image["filename"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                val subfolder = (image["subfolder"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val type = (image["type"] as? JsonPrimitive)?.contentOrNull ?: "output"
                HistoryImage(
                    filename = filename,
                    subfolder = subfolder,
                    type = type,
                    url = ComfyImageUrlBuilder.build(baseUrl, filename, subfolder, type),
                )
            }
        }

        val messageData = status?.messages.orEmpty().mapNotNull { message ->
            val array = message as? JsonArray ?: return@mapNotNull null
            val type = (array.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val data = array.getOrNull(1) as? JsonObject ?: return@mapNotNull null
            type to data
        }

        val startedAt = messageData
            .firstOrNull { it.first == "execution_start" }
            ?.second
            ?.timestamp()
        val finishedAt = messageData
            .firstOrNull {
                it.first == "execution_success" ||
                    it.first == "execution_error" ||
                    it.first == "execution_interrupted"
            }
            ?.second
            ?.timestamp()
        val timestamp = finishedAt
            ?: startedAt
            ?: messageData.lastOrNull()?.second?.timestamp()

        val interrupted = messageData.any { it.first == "execution_interrupted" }
        val errorData = messageData.firstOrNull { it.first == "execution_error" }?.second
        val errorMessage = errorData?.let { data ->
            (data["exception_message"] as? JsonPrimitive)?.contentOrNull
                ?: (data["exception_type"] as? JsonPrimitive)?.contentOrNull
        }

        val state = when {
            status?.statusStr == "success" || status?.completed == true -> "success"
            interrupted -> "interrupted"
            status?.statusStr == "error" || status?.completed == false -> "failed"
            else -> "unknown"
        }

        return HistoryEntry(
            promptId = promptId,
            status = state,
            completed = state == "success",
            timestamp = timestamp,
            startedAt = startedAt,
            finishedAt = finishedAt,
            images = images,
            errorNodeId = (errorData?.get("node_id") as? JsonPrimitive)?.contentOrNull,
            errorMessage = errorMessage,
        )
    }

    private fun JsonObject.timestamp(): Long? =
        (this["timestamp"] as? JsonPrimitive)?.longOrNull
}
