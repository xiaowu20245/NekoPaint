package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ComfyManagerApi
import io.github.xiaowu20245.nekopaint.core.network.ManagerQueueStatusDto
import io.github.xiaowu20245.nekopaint.core.workflow.NodeInstallPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class NodeInstallOutcome(
    val queuedCount: Int = 0,
    val failedPackages: List<String> = emptyList(),
    val queueStartFailed: Boolean = false,
    val queueStatusUnavailable: Boolean = false,
    val queueTimedOut: Boolean = false,
    val restartRequested: Boolean = false,
    val restartRejected: Boolean = false,
    val serverReconnected: Boolean = false,
    val updatedTemplate: WorkflowTemplate? = null,
    val remainingMissingNodeTypes: List<String> = emptyList(),
) {
    fun summary(): String = buildString {
        if (queuedCount > 0) {
            append("已向主机提交 $queuedCount 个节点包的安装请求")
        } else {
            append("没有成功提交安装请求")
        }
        if (failedPackages.isNotEmpty()) {
            append("；${failedPackages.size} 个包提交失败：")
            append(failedPackages.take(3).joinToString("；"))
        }
        when {
            queueStartFailed -> append("。安装队列启动失败，请到主机的 ComfyUI-Manager 页面手动执行安装。")
            queueStatusUnavailable -> {
                append("。无法读取安装队列状态，请等主机完成安装后手动重启 ComfyUI。")
            }

            queueTimedOut -> {
                append("。安装仍在主机后台进行，完成后重启 ComfyUI 才会生效。")
            }

            restartRequested && restartRejected -> {
                append("。主机拒绝了重启请求，请手动重启 ComfyUI 后再检查节点。")
            }

            restartRequested && !serverReconnected -> {
                append("。已请求重启 ComfyUI，但主机尚未恢复连接，请稍后重新检查。")
            }

            restartRequested && remainingMissingNodeTypes.isEmpty() -> {
                append("，重启后缺失节点已补齐。")
            }

            restartRequested -> {
                append("，重启后仍有 ${remainingMissingNodeTypes.size} 个节点缺失：")
                append(remainingMissingNodeTypes.take(5).joinToString("、"))
                if (remainingMissingNodeTypes.size > 5) append(" 等")
                append("。可能是安装失败或缺少依赖，请查看主机日志。")
            }

            else -> append("。重启 ComfyUI 后生效。")
        }
    }
}

/**
 * 节点安装能力接口。Chat 与工作流详情共用，便于在测试中替换为假实现。
 */
interface NodeInstaller {
    suspend fun preparePlan(
        templateId: String,
        reanalyze: Boolean = true,
    ): Result<NodeInstallPlan>

    suspend fun install(
        plan: NodeInstallPlan,
        restartAfterInstall: Boolean,
        onProgress: (String) -> Unit = {},
    ): Result<NodeInstallOutcome>

    suspend fun recheck(templateId: String): Result<WorkflowTemplate>
}

/**
 * 通过 ComfyUI-Manager 安装工作流缺失的自定义节点。
 *
 * 只负责“检查 + 排队 + 等待 + 重启 + 复检”，不提供静默安装：
 * 调用方必须先展示 [NodeInstallPlan] 并由用户确认。
 */
class NodeInstallRepository(
    private val apiFactory: ComfyApiFactory,
    private val serverConfigStore: ServerConfigStore,
    private val workflowRepository: WorkflowRepository,
) : NodeInstaller {

    override suspend fun preparePlan(
        templateId: String,
        reanalyze: Boolean,
    ): Result<NodeInstallPlan> =
        withContext(Dispatchers.IO) {
            val config = currentConfig()
                ?: return@withContext Result.failure(IllegalStateException("请先配置服务器"))
            val template = workflowRepository.getTemplate(templateId)
                ?: return@withContext Result.failure(IllegalStateException("找不到工作流模板"))

            // 刚在详情页完成过 compat 复检时可跳过 object_info 的第二次请求。
            val analyzed = if (reanalyze) {
                workflowRepository.analyzeCompatibility(template)
            } else {
                null
            }
            val missing = if (analyzed?.catalogChecked == true) {
                analyzed.missingNodeTypes
            } else {
                template.missingNodeTypes
            }
            if (reanalyze && analyzed?.catalogChecked == true && missing.isEmpty()) {
                workflowRepository.saveCompatibility(templateId, analyzed)
                return@withContext Result.failure(
                    IllegalStateException("节点已经齐全，无需安装"),
                )
            }
            if (missing.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("该工作流当前没有记录到缺失节点"),
                )
            }

            val manager = apiFactory.createManager(config)
            val versionResponse = runCatching { manager.version() }.getOrElse { error ->
                return@withContext Result.failure(
                    IllegalStateException("无法连接 ComfyUI-Manager：${error.message ?: "网络错误"}"),
                )
            }
            if (!versionResponse.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException(managerUnavailableMessage(versionResponse.code())),
                )
            }
            val managerVersion = versionResponse.body()?.string()?.trim().orEmpty()

            val mappings = loadMappings(manager).getOrElse { error ->
                return@withContext Result.failure(error)
            }

            var plan = NodeInstallPlanner.buildPlan(
                template = template.copy(missingNodeTypes = missing),
                mappings = mappings,
                managerVersion = managerVersion,
            )
            // 主机自带的 local 映射可能较旧；有节点找不到包时再合并一次 cache 映射。
            if (plan.unresolvedNodeTypes.isNotEmpty()) {
                val cacheMappings = fetchMappings(manager, "cache").getOrNull()
                if (cacheMappings != null) {
                    val mergedPlan = NodeInstallPlanner.buildPlan(
                        template = template.copy(missingNodeTypes = missing),
                        mappings = JsonObject(mappings + cacheMappings),
                        managerVersion = managerVersion,
                    )
                    if (mergedPlan.unresolvedNodeTypes.size < plan.unresolvedNodeTypes.size ||
                        (
                            mergedPlan.unresolvedNodeTypes.size == plan.unresolvedNodeTypes.size &&
                                mergedPlan.packages.size > plan.packages.size
                            )
                    ) {
                        plan = mergedPlan
                    }
                }
            }
            if (!plan.canInstall) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "ComfyUI-Manager 的映射里找不到这些节点的安装包：" +
                            plan.unresolvedNodeTypes.take(8).joinToString("、") +
                            "。请在主机手动安装对应插件，或更新 Manager 节点列表后重试",
                    ),
                )
            }
            Result.success(plan)
        }

    /**
     * 排队安装并等待完成。至少成功提交一个包时返回成功结果。
     *
     * [restartAfterInstall] 为 true 时，在队列完成后请求重启并等待服务器恢复，
     * 然后重新分析工作流，返回仍然缺失的节点。
     */
    override suspend fun install(
        plan: NodeInstallPlan,
        restartAfterInstall: Boolean,
        onProgress: (String) -> Unit,
    ): Result<NodeInstallOutcome> = withContext(Dispatchers.IO) {
        if (plan.packages.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("安装计划为空"))
        }
        val config = currentConfig()
            ?: return@withContext Result.failure(IllegalStateException("请先配置服务器"))
        val manager = apiFactory.createManager(config)
        val comfyApi = apiFactory.create(config)

        val queued = mutableListOf<String>()
        val failures = mutableListOf<String>()
        plan.packages.forEachIndexed { index, pkg ->
            onProgress("正在排队 ${index + 1}/${plan.packages.size}：${pkg.title}")
            val request = NodeInstallPlanner.buildInstallRequest(pkg)
            val result = runCatching { manager.install(request) }
            val response = result.getOrNull()
            if (response == null) {
                failures += "${pkg.title}：${result.exceptionOrNull()?.message ?: "网络错误"}"
            } else if (response.isSuccessful) {
                queued += pkg.title
            } else {
                failures += "${pkg.title}：${installErrorMessage(response.code())}"
            }
        }
        if (queued.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "安装请求全部失败：" +
                        failures.take(3).joinToString("；").ifBlank { "服务器没有响应" },
                ),
            )
        }

        val startResponse = runCatching { manager.startQueue() }.getOrNull()
        val queueStartFailed = startResponse == null ||
            (!startResponse.isSuccessful && startResponse.code() != 201)
        if (queueStartFailed) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    queueStartFailed = true,
                ),
            )
        }

        if (fetchQueueStatus(manager) == null) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    queueStatusUnavailable = true,
                ),
            )
        }

        val waitResult = waitForQueue(
            manager = manager,
            queuedCount = queued.size,
            onProgress = onProgress,
        )
        if (waitResult.timedOut) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    queueTimedOut = true,
                ),
            )
        }

        if (!restartAfterInstall) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    restartRequested = false,
                ),
            )
        }

        onProgress("正在重启 ComfyUI…")
        val rebootResult = runCatching { manager.reboot() }
        val rebootResponse = rebootResult.getOrNull()
        val rebootRejected = rebootResponse != null &&
            !rebootResponse.isSuccessful &&
            rebootResponse.code() != 200
        val reconnected = if (rebootRejected) {
            false
        } else {
            waitForServer(comfyApi)
        }
        val updated = if (reconnected) {
            onProgress("正在重新检查节点…")
            workflowRepository.reanalyzeAndSave(plan.templateId)
        } else {
            null
        }
        Result.success(
            NodeInstallOutcome(
                queuedCount = queued.size,
                failedPackages = failures,
                restartRequested = true,
                restartRejected = rebootRejected,
                serverReconnected = reconnected,
                updatedTemplate = updated,
                remainingMissingNodeTypes = updated?.missingNodeTypes.orEmpty(),
            ),
        )
    }

    override suspend fun recheck(templateId: String): Result<WorkflowTemplate> =
        withContext(Dispatchers.IO) {
            val updated = workflowRepository.reanalyzeAndSave(templateId)
                ?: return@withContext Result.failure(
                    IllegalStateException("服务器尚未恢复连接，无法重新检查节点"),
                )
            Result.success(updated)
        }

    private suspend fun loadMappings(manager: ComfyManagerApi): Result<JsonObject> {
        val local = fetchMappings(manager, "local")
        if (local.isSuccess) return local
        val cache = fetchMappings(manager, "cache")
        return if (cache.isSuccess) cache else local
    }

    private suspend fun fetchMappings(
        manager: ComfyManagerApi,
        mode: String,
    ): Result<JsonObject> {
        val response = runCatching { manager.mappings(mode) }.getOrNull()
        return when {
            response == null -> Result.failure(
                IllegalStateException("无法读取 ComfyUI-Manager 的节点映射（$mode）"),
            )

            response.isSuccessful -> {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("ComfyUI-Manager 返回了空映射"))
            }

            response.code() == 404 -> Result.failure(
                IllegalStateException("当前 ComfyUI-Manager 不支持节点映射接口，请先更新 Manager"),
            )

            else -> Result.failure(
                IllegalStateException(
                    "读取节点映射失败（HTTP ${response.code()}，$mode），" +
                        "请检查 Manager 状态和主机网络",
                ),
            )
        }
    }

    private suspend fun waitForQueue(
        manager: ComfyManagerApi,
        queuedCount: Int,
        onProgress: (String) -> Unit,
    ): QueueWaitResult {
        var lastReported = -1
        // 给 Manager 的 task_worker 一点启动时间，避免把“尚未开始”误判成“已经完成”。
        delay(QUEUE_START_GRACE_MS)
        repeat(QUEUE_POLL_ATTEMPTS) {
            val status = fetchQueueStatus(manager)
            if (status != null) {
                val active = status.isProcessing ||
                    status.inProgressCount > 0 ||
                    status.totalCount > 0
                if (!active) {
                    // done_count / total_count 在队列清空后会归零，因此以“空闲”作为完成信号。
                    return QueueWaitResult()
                }
                val done = status.doneCount.coerceIn(0, queuedCount)
                if (done != lastReported) {
                    lastReported = done
                    onProgress("主机正在安装… 已完成 $done/$queuedCount")
                }
            }
            delay(QUEUE_POLL_INTERVAL_MS)
        }
        return QueueWaitResult(timedOut = true)
    }

    private suspend fun fetchQueueStatus(
        manager: ComfyManagerApi,
    ): ManagerQueueStatusDto? = runCatching {
        manager.queueStatus().takeIf { it.isSuccessful }?.body()
    }.getOrNull()

    private suspend fun waitForServer(api: io.github.xiaowu20245.nekopaint.core.network.ComfyApi): Boolean {
        repeat(RECONNECT_ATTEMPTS) {
            delay(RECONNECT_INTERVAL_MS)
            if (runCatching { api.systemStats() }.isSuccess) return true
        }
        return false
    }

    private suspend fun currentConfig(): ServerConfig? =
        runCatching { serverConfigStore.config.first() }.getOrNull()

    private fun managerUnavailableMessage(code: Int): String = when (code) {
        401, 403 -> "ComfyUI-Manager 拒绝了访问，请检查主机安全策略或反向代理权限"
        404 -> "主机没有启用 ComfyUI-Manager。请先在主机安装并启用 Manager，再重启 ComfyUI"
        else -> "无法访问 ComfyUI-Manager（HTTP $code）"
    }

    private fun installErrorMessage(code: Int): String = when (code) {
        403 -> "安全策略禁止安装，请在主机把 security_level 调整为 normal 或更宽松并重启 ComfyUI"
        404 -> "Manager 找不到该包，或不允许 Git URL 安装"
        else -> "Manager 返回 HTTP $code"
    }

    private data class QueueWaitResult(
        val timedOut: Boolean = false,
    )

    private companion object {
        const val QUEUE_START_GRACE_MS = 1_000L
        const val QUEUE_POLL_INTERVAL_MS = 2_000L
        const val QUEUE_POLL_ATTEMPTS = 150
        const val RECONNECT_INTERVAL_MS = 2_000L
        const val RECONNECT_ATTEMPTS = 45
    }
}
