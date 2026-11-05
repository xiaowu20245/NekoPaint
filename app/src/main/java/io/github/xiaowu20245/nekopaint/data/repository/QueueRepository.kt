package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.InterruptRequestDto
import io.github.xiaowu20245.nekopaint.core.network.QueueOperationDto
import io.github.xiaowu20245.nekopaint.data.mapper.QueueMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class QueueRepository(
    private val apiFactory: ComfyApiFactory,
    private val serverConfigStore: ServerConfigStore,
) {

    suspend fun queue(): Result<QueueSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val config = serverConfigStore.config.first() ?: error("未配置服务器")
            QueueMapper.toQueueSnapshot(apiFactory.create(config).queue())
        }
    }

    suspend fun history(maxItems: Int = 50): Result<List<HistoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val config = serverConfigStore.config.first() ?: error("未配置服务器")
            QueueMapper.toHistoryEntries(
                dto = apiFactory.create(config).history(maxItems),
                baseUrl = config.baseUrl,
            )
        }
    }

    suspend fun interrupt(promptId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val config = serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(config).interrupt(
                InterruptRequestDto(promptId = promptId),
            )
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
    }

    suspend fun deletePending(promptId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val config = serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(config).updateQueue(
                QueueOperationDto(delete = listOf(promptId)),
            )
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
    }
}
