package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueSnapshot
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.HistoryOperationDto
import io.github.xiaowu20245.nekopaint.core.network.InterruptRequestDto
import io.github.xiaowu20245.nekopaint.core.network.QueueOperationDto
import io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator
import io.github.xiaowu20245.nekopaint.data.mapper.QueueMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class QueueRepository(
    private val apiFactory: ComfyApiFactory,
    private val serverConfigStore: ServerConfigStore,
) {

    suspend fun queue(config: ServerConfig? = null): Result<QueueSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            QueueMapper.toQueueSnapshot(apiFactory.create(resolved).queue())
        }
    }

    suspend fun history(
        maxItems: Int = 50,
        config: ServerConfig? = null,
    ): Result<List<HistoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            QueueMapper.toHistoryEntries(
                dto = apiFactory.create(resolved).history(maxItems),
                baseUrl = ServerEndpointValidator.require(resolved).baseUrl,
            )
        }
    }

    /**
     * 按 promptId 定向查询历史，避免 unknown 恢复只检查最近一页而漏掉旧任务。
     */
    suspend fun historyItem(
        promptId: String,
        config: ServerConfig? = null,
    ): Result<HistoryEntry?> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(resolved).historyItem(promptId)
            val entries = if (response.isSuccessful) {
                QueueMapper.toHistoryEntries(
                    dto = response.body().orEmpty(),
                    baseUrl = ServerEndpointValidator.require(resolved).baseUrl,
                )
            } else {
                emptyList()
            }
            when (val outcome = HistoryLookupPolicy.classify(
                code = response.code(),
                entries = entries,
                promptId = promptId,
            )) {
                HistoryLookupOutcome.NotFound -> null
                is HistoryLookupOutcome.Found -> outcome.entry
                is HistoryLookupOutcome.Failed -> error(outcome.message)
            }
        }
    }

    suspend fun interrupt(
        promptId: String,
        config: ServerConfig? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(resolved).interrupt(
                InterruptRequestDto(promptId = promptId),
            )
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
    }

    suspend fun deletePending(
        promptId: String,
        config: ServerConfig? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(resolved).updateQueue(
                QueueOperationDto(delete = listOf(promptId)),
            )
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
    }

    suspend fun deleteHistory(
        promptId: String,
        config: ServerConfig? = null,
    ): Result<Unit> = deleteHistory(listOf(promptId), config)

    suspend fun deleteHistory(
        promptIds: List<String>,
        config: ServerConfig? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(resolved).updateHistory(
                HistoryOperationDto(delete = promptIds),
            )
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
    }

    suspend fun clearHistory(config: ServerConfig? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = config ?: serverConfigStore.config.first() ?: error("未配置服务器")
            val response = apiFactory.create(resolved).updateHistory(
                HistoryOperationDto(clear = true),
            )
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
    }
}
