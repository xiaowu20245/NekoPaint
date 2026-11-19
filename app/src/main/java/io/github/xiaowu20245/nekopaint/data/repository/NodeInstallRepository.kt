package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.NekoException
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ComfyManagerApi
import io.github.xiaowu20245.nekopaint.core.network.ManagerQueueStatusDto
import io.github.xiaowu20245.nekopaint.core.workflow.NodeInstallPlanner
import io.github.xiaowu20245.nekopaint.core.workflow.NodeInstallQueuePolicy
import io.github.xiaowu20245.nekopaint.core.workflow.ServerBindingPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class NodeInstallOutcome(
    val queuedCount: Int = 0,
    val failedPackages: List<String> = emptyList(),
    val queueBusy: Boolean = false,
    val queueStartFailed: Boolean = false,
    val queueStatusUnavailable: Boolean = false,
    val queueUnconfirmed: Boolean = false,
    val queueTimedOut: Boolean = false,
    val restartRequested: Boolean = false,
    val restartRejected: Boolean = false,
    val serverReconnected: Boolean = false,
    val recheckFailed: Boolean = false,
    val serverChanged: Boolean = false,
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
            serverChanged -> {
                append("。安装期间服务器已切换，已停止后续步骤；请切回原服务器重新检查后再操作。")
            }

            queueBusy -> {
                append("。主机安装队列正在忙，未提交任何安装请求；请等队列空闲后重试。")
            }

            queueStartFailed -> append("。安装队列启动失败，请到主机的 ComfyUI-Manager 页面手动执行安装。")
            queueStatusUnavailable -> {
                append("。无法读取安装队列状态，请等主机完成安装后手动重启 ComfyUI。")
            }

            queueUnconfirmed -> {
                append("。无法确认本次安装队列已启动，未自动重启 ComfyUI；请到主机 Manager 页面检查。")
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

            restartRequested && recheckFailed -> {
                append("。重启后无法确认节点是否补齐，请重新检查。")
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

    suspend fun recheck(
        templateId: String,
        binding: ServerBinding? = null,
    ): Result<WorkflowTemplate>
}

/**
 * 通过 ComfyUI-Manager 安装工作流缺失的自定义节点。
 *
 * 只负责“检查 + 排队 + 等待 + 重启 + 复检”，不提供静默安装：
 * 调用方必须先展示 [NodeInstallPlan] 并由用户确认。
 */
internal data class NodeInstallRepositoryDependencies(
    val apiFactory: ComfyApiFactory,
    val activeProfile: suspend () -> ServerProfile?,
    val templateProvider: suspend (String) -> WorkflowTemplate?,
    val analyzeCompatibility: suspend (WorkflowTemplate, ServerBinding?) -> WorkflowTemplate?,
    val saveCompatibility: suspend (String, WorkflowTemplate, ServerBinding?) -> WorkflowTemplate?,
    val reanalyzeAndSave: suspend (String, ServerBinding?) -> WorkflowTemplate?,
    val pollDelay: suspend (Long) -> Unit = { delay(it) },
)

class NodeInstallRepository internal constructor(
    private val deps: NodeInstallRepositoryDependencies,
) : NodeInstaller {

    constructor(
        apiFactory: ComfyApiFactory,
        serverConfigStore: ServerConfigStore,
        workflowRepository: WorkflowRepository,
    ) : this(
        NodeInstallRepositoryDependencies(
            apiFactory = apiFactory,
            activeProfile = {
                runCatching { serverConfigStore.activeServer.first() }.getOrNull()
            },
            templateProvider = { workflowRepository.getTemplate(it) },
            analyzeCompatibility = { template, binding ->
                workflowRepository.analyzeCompatibility(template, binding)
            },
            saveCompatibility = { id, template, binding ->
                workflowRepository.saveCompatibility(id, template, binding)
            },
            reanalyzeAndSave = { id, binding ->
                workflowRepository.reanalyzeAndSave(id, binding)
            },
        ),
    )

    override suspend fun preparePlan(
        templateId: String,
        reanalyze: Boolean,
    ): Result<NodeInstallPlan> =
        withContext(Dispatchers.IO) {
            val profile = activeProfile()
                ?: return@withContext Result.failure(
                    nodeError(NekoMessageKey.NODE_INSTALL_SERVER_REQUIRED),
                )
            val binding = ServerBindingPolicy.fromProfile(profile)
            val config = ServerBindingPolicy.toConfig(binding)
            val template = deps.templateProvider(templateId)
                ?: return@withContext Result.failure(
                    nodeError(NekoMessageKey.NODE_INSTALL_TEMPLATE_NOT_FOUND),
                )

            // 刚在详情页完成过 compat 复检时可跳过 object_info 的第二次请求。
            val analyzed = if (reanalyze) {
                deps.analyzeCompatibility(template, binding)
            } else {
                null
            }
            val missing = if (analyzed?.catalogChecked == true) {
                analyzed.missingNodeTypes
            } else {
                template.missingNodeTypes
            }
            if (reanalyze && analyzed?.catalogChecked == true && missing.isEmpty()) {
                if (!bindingStillActive(binding)) {
                    return@withContext Result.failure(serverChangedError())
                }
                deps.saveCompatibility(templateId, analyzed, binding)
                return@withContext Result.failure(
                    nodeError(NekoMessageKey.NODE_INSTALL_ALREADY_COMPLETE),
                )
            }
            if (missing.isEmpty()) {
                return@withContext Result.failure(
                    nodeError(NekoMessageKey.NODE_INSTALL_NO_MISSING),
                )
            }

            val manager = runCatching { deps.apiFactory.createManager(config) }.getOrElse { error ->
                return@withContext Result.failure(
                    nodeError(
                        NekoMessageKey.NODE_INSTALL_CONFIG_INVALID,
                        error.message ?: error.javaClass.simpleName,
                    ),
                )
            }
            val versionResponse = runCatching { manager.version() }.getOrElse { error ->
                return@withContext Result.failure(
                    nodeError(
                        NekoMessageKey.NODE_INSTALL_MANAGER_UNREACHABLE,
                        error.message ?: error.javaClass.simpleName,
                    ),
                )
            }
            if (!versionResponse.isSuccessful) {
                return@withContext Result.failure(
                    managerUnavailableError(versionResponse.code()),
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
                    nodeError(
                        NekoMessageKey.NODE_INSTALL_MAPPING_MISSING_PACKAGES,
                        plan.unresolvedNodeTypes.take(8).joinToString("、"),
                    ),
                )
            }
            if (!bindingStillActive(binding)) {
                return@withContext Result.failure(serverChangedError())
            }
            Result.success(plan.copy(binding = binding))
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
            return@withContext Result.failure(
                nodeError(NekoMessageKey.NODE_INSTALL_PLAN_EMPTY),
            )
        }
        val binding = plan.binding
            ?: return@withContext Result.failure(
                nodeError(NekoMessageKey.NODE_INSTALL_PLAN_NO_BINDING_NEW),
            )
        if (!bindingStillActive(binding)) {
            return@withContext Result.failure(serverChangedError())
        }
        val config = ServerBindingPolicy.toConfig(binding)
        val manager = runCatching { deps.apiFactory.createManager(config) }.getOrElse { error ->
            return@withContext Result.failure(
                nodeError(
                    NekoMessageKey.NODE_INSTALL_CONFIG_INVALID,
                    error.message ?: error.javaClass.simpleName,
                ),
            )
        }
        val comfyApi = runCatching { deps.apiFactory.create(config) }.getOrElse { error ->
            return@withContext Result.failure(
                nodeError(
                    NekoMessageKey.NODE_INSTALL_CONFIG_INVALID,
                    error.message ?: error.javaClass.simpleName,
                ),
            )
        }

        // 第一个 install 之前必须确认队列空闲；忙碌时零 install、不重启。
        val baseline = NodeInstallQueuePolicy.baseline(fetchQueueStatus(manager))
        when (baseline) {
            NodeInstallQueuePolicy.Baseline.UNAVAILABLE -> {
                return@withContext Result.success(
                    NodeInstallOutcome(queueStatusUnavailable = true),
                )
            }

            NodeInstallQueuePolicy.Baseline.BUSY -> {
                return@withContext Result.success(
                    NodeInstallOutcome(queueBusy = true),
                )
            }

            NodeInstallQueuePolicy.Baseline.READY -> Unit
        }

        val queued = mutableListOf<String>()
        val failures = mutableListOf<String>()
        plan.packages.forEachIndexed { index, pkg ->
            if (!bindingStillActive(binding)) {
                return@withContext Result.success(
                    NodeInstallOutcome(
                        queuedCount = queued.size,
                        failedPackages = failures,
                        serverChanged = true,
                    ),
                )
            }
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
                nodeError(
                    NekoMessageKey.NODE_INSTALL_ALL_FAILED,
                    failures.take(3).joinToString("；")
                        .ifBlank { "no response" },
                ),
            )
        }

        if (!bindingStillActive(binding)) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    serverChanged = true,
                ),
            )
        }
        val startResponse = runCatching { manager.startQueue() }.getOrNull()
        val queueStartFailed = startResponse == null || !startResponse.isSuccessful
        if (queueStartFailed) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    queueStartFailed = true,
                ),
            )
        }

        when (waitForQueueActive(manager, queued.size, onProgress)) {
            ActiveWaitResult.ACTIVE -> Unit
            ActiveWaitResult.UNAVAILABLE -> {
                return@withContext Result.success(
                    NodeInstallOutcome(
                        queuedCount = queued.size,
                        failedPackages = failures,
                        queueStatusUnavailable = true,
                    ),
                )
            }

            ActiveWaitResult.UNCONFIRMED -> {
                return@withContext Result.success(
                    NodeInstallOutcome(
                        queuedCount = queued.size,
                        failedPackages = failures,
                        queueUnconfirmed = true,
                    ),
                )
            }
        }

        val waitResult = waitForQueueIdle(
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
        if (!bindingStillActive(binding)) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    serverChanged = true,
                ),
            )
        }

        onProgress("正在重启 ComfyUI…")
        val rebootResult = runCatching { manager.reboot() }
        val rebootResponse = rebootResult.getOrNull()
        val rebootRejected = rebootResult.isFailure ||
            rebootResponse == null ||
            !rebootResponse.isSuccessful
        val reconnected = if (rebootRejected) {
            false
        } else {
            waitForServer(comfyApi)
        }
        if (!reconnected) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    restartRequested = true,
                    restartRejected = rebootRejected,
                    serverReconnected = false,
                ),
            )
        }
        if (!bindingStillActive(binding)) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    restartRequested = true,
                    serverReconnected = true,
                    serverChanged = true,
                ),
            )
        }
        onProgress("正在重新检查节点…")
        val updated = deps.reanalyzeAndSave(plan.templateId, binding)
        if (!bindingStillActive(binding)) {
            return@withContext Result.success(
                NodeInstallOutcome(
                    queuedCount = queued.size,
                    failedPackages = failures,
                    restartRequested = true,
                    serverReconnected = true,
                    serverChanged = true,
                ),
            )
        }
        Result.success(
            NodeInstallOutcome(
                queuedCount = queued.size,
                failedPackages = failures,
                restartRequested = true,
                restartRejected = rebootRejected,
                serverReconnected = true,
                recheckFailed = updated == null,
                updatedTemplate = updated,
                remainingMissingNodeTypes = updated?.missingNodeTypes.orEmpty(),
            ),
        )
    }

    override suspend fun recheck(
        templateId: String,
        binding: ServerBinding?,
    ): Result<WorkflowTemplate> =
        withContext(Dispatchers.IO) {
            val target = binding
                ?: return@withContext Result.failure(
                    nodeError(NekoMessageKey.NODE_INSTALL_PLAN_NO_BINDING_RETRY),
                )
            if (!bindingStillActive(target)) {
                return@withContext Result.failure(serverChangedError())
            }
            val updated = deps.reanalyzeAndSave(templateId, target)
            if (updated == null) {
                // 真实 reanalyze 在绑定变化时返回 null；先区分 ServerChanged，再报离线。
                if (!bindingStillActive(target)) {
                    return@withContext Result.failure(serverChangedError())
                }
                return@withContext Result.failure(
                    nodeError(NekoMessageKey.NODE_INSTALL_SERVER_NOT_RECONNECTED),
                )
            }
            if (!bindingStillActive(target)) {
                return@withContext Result.failure(serverChangedError())
            }
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
                nodeError(NekoMessageKey.NODE_INSTALL_MAPPING_READ_FAILED, mode),
            )

            response.isSuccessful -> {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(nodeError(NekoMessageKey.NODE_INSTALL_MAPPING_EMPTY))
            }

            response.code() == 404 -> Result.failure(
                nodeError(NekoMessageKey.NODE_INSTALL_MAPPING_UNSUPPORTED),
            )

            else -> Result.failure(
                nodeError(
                    NekoMessageKey.NODE_INSTALL_MAPPING_HTTP_FAILED,
                    response.code().toString(),
                    mode,
                ),
            )
        }
    }

    private suspend fun waitForQueueActive(
        manager: ComfyManagerApi,
        queuedCount: Int,
        onProgress: (String) -> Unit,
    ): ActiveWaitResult {
        var sawStatus = false
        onProgress("等待主机安装队列启动…")
        repeat(QUEUE_ACTIVE_ATTEMPTS) {
            val status = fetchQueueStatus(manager)
            if (status != null) {
                sawStatus = true
                if (NodeInstallQueuePolicy.activeCoversQueued(status, queuedCount)) {
                    return ActiveWaitResult.ACTIVE
                }
                if (NodeInstallQueuePolicy.isActive(status)) {
                    // 队列有活动但没有覆盖本次提交数量，无法确认归属。
                    return ActiveWaitResult.UNCONFIRMED
                }
            }
            deps.pollDelay(QUEUE_POLL_INTERVAL_MS)
        }
        return if (sawStatus) {
            ActiveWaitResult.UNCONFIRMED
        } else {
            ActiveWaitResult.UNAVAILABLE
        }
    }

    private suspend fun waitForQueueIdle(
        manager: ComfyManagerApi,
        queuedCount: Int,
        onProgress: (String) -> Unit,
    ): QueueWaitResult {
        var lastReported = -1
        repeat(QUEUE_POLL_ATTEMPTS) {
            val status = fetchQueueStatus(manager)
            if (status != null) {
                if (NodeInstallQueuePolicy.isIdle(status)) {
                    return QueueWaitResult()
                }
                val done = status.doneCount.coerceIn(0, queuedCount)
                if (done != lastReported) {
                    lastReported = done
                    onProgress("主机正在安装… 已完成 $done/$queuedCount")
                }
            }
            deps.pollDelay(QUEUE_POLL_INTERVAL_MS)
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
            deps.pollDelay(RECONNECT_INTERVAL_MS)
            val stats = runCatching { api.systemStats() }.getOrNull()
            // Retrofit 对非 2xx 抛异常，成功解析才会返回非空。
            if (stats != null) return true
        }
        return false
    }

    private suspend fun activeProfile(): ServerProfile? = deps.activeProfile()

    private suspend fun bindingStillActive(binding: ServerBinding): Boolean =
        ServerBindingPolicy.matches(activeProfile(), binding)

    private fun serverChangedError(): NekoException =
        nodeError(NekoMessageKey.NODE_INSTALL_SERVER_CHANGED)

    private fun managerUnavailableError(code: Int): NekoException = when (code) {
        401, 403 -> nodeError(NekoMessageKey.NODE_INSTALL_MANAGER_FORBIDDEN)
        404 -> nodeError(NekoMessageKey.NODE_INSTALL_MANAGER_DISABLED)
        else -> nodeError(NekoMessageKey.NODE_INSTALL_MANAGER_HTTP, code.toString())
    }

    private fun nodeError(key: NekoMessageKey, vararg args: String): NekoException =
        NekoException(NekoMessage(key, args.toList()))

    private fun installErrorMessage(code: Int): String = when (code) {
        403 -> "安全策略禁止安装，请在主机把 security_level 调整为 normal 或更宽松并重启 ComfyUI"
        404 -> "Manager 找不到该包，或不允许 Git URL 安装"
        else -> "Manager 返回 HTTP $code"
    }

    private data class QueueWaitResult(
        val timedOut: Boolean = false,
    )

    private enum class ActiveWaitResult {
        ACTIVE,
        UNCONFIRMED,
        UNAVAILABLE,
    }

    private companion object {
        const val QUEUE_POLL_INTERVAL_MS = 2_000L
        const val QUEUE_POLL_ATTEMPTS = 150
        const val QUEUE_ACTIVE_ATTEMPTS = 15
        const val RECONNECT_INTERVAL_MS = 2_000L
        const val RECONNECT_ATTEMPTS = 45
    }
}
