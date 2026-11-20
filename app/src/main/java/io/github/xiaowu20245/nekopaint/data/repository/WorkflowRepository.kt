package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.UploadedImageRef
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ContentUriRequestBody
import io.github.xiaowu20245.nekopaint.core.network.PromptErrorResponseDto
import io.github.xiaowu20245.nekopaint.core.network.PromptRequestDto
import io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalogCache
import io.github.xiaowu20245.nekopaint.core.workflow.ParameterDetector
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagExpander
import io.github.xiaowu20245.nekopaint.core.workflow.ServerBindingPolicy
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCandidateSafety
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCompatibilityAnalyzer
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowJsonApplier
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParseResult
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

sealed interface WorkflowImportResult {
    data class Success(val template: WorkflowTemplate) : WorkflowImportResult
    data object UiFormat : WorkflowImportResult
    data class Error(val message: NekoMessage) : WorkflowImportResult
}

data class NodeErrorInfo(
    val nodeId: String,
    val message: String,
    val inputName: String? = null,
    val details: String? = null,
)

sealed interface WorkflowSubmitResult {
    data class Success(
        val promptId: String,
        val queueNumber: Int?,
        val localRecordWarning: NekoMessage? = null,
    ) : WorkflowSubmitResult

    data class Validation(
        val summary: NekoMessage,
        val nodeErrors: List<NodeErrorInfo>,
    ) : WorkflowSubmitResult

    data object NoServer : WorkflowSubmitResult

    data class Unknown(
        val promptId: String,
        val message: NekoMessage,
    ) : WorkflowSubmitResult

    data class Error(val message: NekoMessage) : WorkflowSubmitResult
}

internal data class WorkflowRepositoryDependencies(
    val appContext: Context?,
    val json: Json,
    val templateStore: WorkflowTemplateStore,
    val configProvider: suspend () -> ServerConfig?,
    val activeServerIdProvider: suspend () -> String?,
    val activeBindingProvider: suspend () -> ServerBinding? = { null },
    val clientIdProvider: suspend () -> String,
    val apiFactory: ComfyApiFactory,
    val taskRecordRepository: TaskRecordRepository,
    val nodeCatalogCache: NodeCatalogCache? = null,
    val retryDelay: suspend (Long) -> Unit = { delay(it) },
)

class WorkflowRepository private constructor(
    private val deps: WorkflowRepositoryDependencies,
) {

    constructor(
        context: Context,
        json: Json,
        apiFactory: ComfyApiFactory,
        serverConfigStore: ServerConfigStore,
        clientIdStore: ClientIdStore,
        taskRecordRepository: TaskRecordRepository,
    ) : this(
        WorkflowRepositoryDependencies(
            appContext = context.applicationContext,
            json = json,
            templateStore = WorkflowTemplateStore(
                rootDir = File(context.applicationContext.filesDir, "workflows"),
                json = json,
            ),
            configProvider = { serverConfigStore.config.first() },
            activeServerIdProvider = { serverConfigStore.activeServerId.first() },
            activeBindingProvider = {
                serverConfigStore.activeServer.first()?.let(ServerBindingPolicy::fromProfile)
            },
            clientIdProvider = { clientIdStore.getOrCreate() },
            apiFactory = apiFactory,
            taskRecordRepository = taskRecordRepository,
            nodeCatalogCache = NodeCatalogCache(
                rootDir = File(context.applicationContext.filesDir, "node_catalog"),
                json = json,
            ),
        ),
    )

    private val appContext: Context?
        get() = deps.appContext
    private val json: Json
        get() = deps.json
    private val templateStore: WorkflowTemplateStore
        get() = deps.templateStore
    private val apiFactory: ComfyApiFactory
        get() = deps.apiFactory
    private val taskRecordRepository: TaskRecordRepository
        get() = deps.taskRecordRepository
    private val refreshMutex = Mutex()

    private var lastCompatibilityServerId: String? = null
    private var lastCompatibilityRefreshAt = 0L

    suspend fun listTemplates(): List<WorkflowTemplate> = templateStore.listTemplates()

    suspend fun getTemplate(id: String): WorkflowTemplate? = templateStore.getTemplate(id)

    suspend fun updateTemplate(template: WorkflowTemplate) = templateStore.updateTemplate(template)

    suspend fun patchPrimaryPrompt(id: String, candidateId: String?) =
        templateStore.patchPrimaryPrompt(id, candidateId)

    suspend fun patchPreset(id: String, preset: WorkflowPreset) =
        templateStore.patchPreset(id, preset)

    suspend fun deletePreset(id: String, presetId: String) =
        templateStore.deletePreset(id, presetId)

    suspend fun patchActivePreset(id: String, presetId: String?) =
        templateStore.patchActivePreset(id, presetId)

    suspend fun patchPromptTagGroups(id: String, groups: List<PromptTagGroup>) =
        templateStore.patchPromptTagGroups(id, groups)

    suspend fun patchExecutionValues(id: String, snapshot: List<ParameterCandidate>) =
        templateStore.patchExecutionValues(id, snapshot)

    suspend fun patchApplyPreset(
        id: String,
        snapshot: List<ParameterCandidate>,
        presetId: String?,
    ) = templateStore.patchApplyPreset(id, snapshot, presetId)

    suspend fun patchSavePreset(
        id: String,
        preset: WorkflowPreset,
        snapshot: List<ParameterCandidate>,
    ) = templateStore.patchSavePreset(id, preset, snapshot)

    /**
     * Rebuilds detection-derived fields from api.json while preserving user
     * metadata. This is the only write path allowed to clear repairRequired.
     */
    suspend fun repairTemplate(id: String): Result<WorkflowTemplate> =
        templateStore.repairTemplate(id, loadCatalog())

    suspend fun renameTemplate(id: String, newName: String): Result<Unit> =
        templateStore.renameTemplate(id, newName)

    /**
     * 复制模板目录和参数配置，生成新的模板 ID。
     *
     * 复制名称自动追加“副本”，重名时继续加序号，避免列表出现两个同名模板。
     */
    suspend fun copyTemplate(id: String): Result<WorkflowTemplate> =
        templateStore.copyTemplate(id)

    suspend fun deleteTemplate(id: String) = templateStore.deleteTemplate(id)

    suspend fun readApiJson(id: String): String? = templateStore.readApiJson(id)

    /**
     * 上传图片到 ComfyUI 的 input 目录，返回可直接写入 LoadImage.image 的引用。
     *
     * 使用时间戳前缀避免同名覆盖；上传成功只代表文件已落地，
     * 图片能否解码仍由 ComfyUI 执行阶段决定。
     */
    suspend fun uploadImage(uri: Uri): Result<UploadedImageRef> = withContext(Dispatchers.IO) {
        val (displayName, size) = queryFileMeta(uri)
        if (size != null && size > MAX_UPLOAD_BYTES) {
            return@withContext Result.failure(
                IllegalStateException("图片超过 ${MAX_UPLOAD_BYTES / 1024 / 1024} MB"),
            )
        }
        val resolver = appContext?.contentResolver
            ?: return@withContext Result.failure(IllegalStateException("当前环境不支持图片上传"))
        val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
        val part = MultipartBody.Part.createFormData(
            "image",
            buildUploadName(displayName),
            ContentUriRequestBody(resolver, uri, mediaType, size),
        )
        uploadPart(part)
    }

    /**
     * 把画布导出的蒙版 PNG 上传到服务器，供 LoadImageMask.image 使用。
     */
    suspend fun uploadMask(bitmap: Bitmap): Result<UploadedImageRef> = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        if (bytes.size > MAX_UPLOAD_BYTES) {
            return@withContext Result.failure(
                IllegalStateException("蒙版超过 ${MAX_UPLOAD_BYTES / 1024 / 1024} MB"),
            )
        }
        val part = MultipartBody.Part.createFormData(
            "image",
            buildUploadName("mask.png"),
            bytes.toRequestBody("image/png".toMediaTypeOrNull()),
        )
        uploadPart(part)
    }

    private suspend fun uploadPart(part: MultipartBody.Part): Result<UploadedImageRef> {
        val config = runCatching { deps.configProvider() }.getOrNull()
            ?: return Result.failure(IllegalStateException("请先配置服务器"))
        return runCatching {
            val response = apiFactory.create(config).uploadImage(
                image = part,
                type = "input".toRequestBody(TEXT_PLAIN),
                overwrite = "false".toRequestBody(TEXT_PLAIN),
            )
            if (response.name.isBlank()) {
                error("服务器没有返回文件名")
            }
            UploadedImageRef(
                reference = if (response.subfolder.isBlank()) {
                    response.name
                } else {
                    "${response.subfolder}/${response.name}"
                },
                fileName = response.name,
            )
        }
    }

    private fun buildUploadName(displayName: String?): String =
        buildUploadFileName(displayName)

    suspend fun import(uri: Uri): WorkflowImportResult = withContext(Dispatchers.IO) {
        try {
            val (displayName, size) = queryFileMeta(uri)
            if (size != null && size > MAX_FILE_BYTES) {
                return@withContext WorkflowImportResult.Error(
                    NekoMessage(NekoMessageKey.WORKFLOW_IMPORT_FILE_TOO_LARGE),
                )
            }
            val resolver = appContext?.contentResolver
                ?: return@withContext WorkflowImportResult.Error(
                    NekoMessage(NekoMessageKey.WORKFLOW_IMPORT_ENV_UNSUPPORTED),
                )
            val raw = resolver.openInputStream(uri)?.use {
                readLimited(it, MAX_FILE_BYTES)
            } ?: return@withContext WorkflowImportResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_IMPORT_READ_FAILED),
            )

            when (val parsed = WorkflowParser.parse(raw)) {
                WorkflowParseResult.UiFormat -> WorkflowImportResult.UiFormat
                is WorkflowParseResult.Invalid -> WorkflowImportResult.Error(
                    NekoMessage(
                        NekoMessageKey.WORKFLOW_IMPORT_FAILED,
                        listOf(parsed.reason),
                    ),
                )
                is WorkflowParseResult.Success -> {
                    val catalog = loadCatalog()
                    val detection = ParameterDetector.detect(parsed.graph, catalog)
                    val compatibility = WorkflowCompatibilityAnalyzer.analyze(
                        graph = parsed.graph,
                        catalog = catalog,
                        missingNodeTypes = detection.missingNodeTypes,
                    )
                    val template = WorkflowTemplate(
                        id = UUID.randomUUID().toString(),
                        name = (displayName ?: "workflow.json").toTemplateName(),
                        sourceFileName = displayName ?: "workflow.json",
                        sourceHash = raw.sha256(),
                        importedAt = System.currentTimeMillis(),
                        nodeCount = parsed.graph.nodes.size,
                        reachableNodeCount = parsed.graph.reachableNodeIds.size,
                        outputNodeIds = parsed.graph.outputNodeIds.sorted(),
                        missingNodeTypes = detection.missingNodeTypes,
                        catalogChecked = detection.catalogChecked,
                        candidates = detection.candidates,
                        compatibility = compatibility,
                    )
                    templateStore.saveTemplate(template, raw)
                    WorkflowImportResult.Success(template)
                }
            }
        } catch (throwable: Throwable) {
            WorkflowImportResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_IMPORT_FAILED,
                    listOf(throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }
    }

    /**
     * 对旧模板或未连接服务器时导入的模板补做兼容性分析。
     */
    suspend fun analyzeCompatibility(
        template: WorkflowTemplate,
        binding: ServerBinding? = null,
    ): WorkflowTemplate? =
        withContext(Dispatchers.IO) {
            if (binding != null && !bindingMatches(binding)) return@withContext null
            val raw = readApiJson(template.id) ?: return@withContext null
            val parsed = WorkflowParser.parse(raw) as? WorkflowParseResult.Success
                ?: return@withContext null
            val catalog = loadCatalog()
            val detection = ParameterDetector.detect(parsed.graph, catalog)
            val compatibility = WorkflowCompatibilityAnalyzer.analyze(
                graph = parsed.graph,
                catalog = catalog,
                missingNodeTypes = detection.missingNodeTypes,
            )
            if (binding != null && !bindingMatches(binding)) return@withContext null
            // 不在这里回写整个模板，避免覆盖用户刚修改的参数值；
            // 新导入的模板已在 import() 时保存兼容性。
            template.copy(
                missingNodeTypes = detection.missingNodeTypes,
                catalogChecked = detection.catalogChecked,
                compatibility = compatibility,
            )
        }

    /**
     * 只回写兼容性字段，不改动参数值或预设。
     */
    suspend fun saveCompatibility(
        templateId: String,
        analyzed: WorkflowTemplate,
        binding: ServerBinding? = null,
    ): WorkflowTemplate? {
        val guard: (suspend () -> Boolean)? = binding?.let { expected ->
            { bindingMatches(expected) }
        }
        return templateStore.saveCompatibility(templateId, analyzed, guard)
    }

    /**
     * 节点安装/重启后重新拉取 object_info 并保存缺失节点结果。
     *
     * 服务器尚未恢复或工作流文件不可读时返回 null，避免把“无法检查”误存成“没有缺失”。
     */
    suspend fun reanalyzeAndSave(
        templateId: String,
        binding: ServerBinding? = null,
    ): WorkflowTemplate? {
        if (binding != null && !bindingMatches(binding)) return null
        val template = templateStore.getTemplate(templateId) ?: return null
        if (template.repairRequired) return template
        // 节点安装/重启后的“重新检查”必须绕过 5 分钟缓存。
        val catalog = loadCatalog(force = true) ?: return null
        if (binding != null && !bindingMatches(binding)) return null
        val guard: (suspend () -> Boolean)? = binding?.let { expected ->
            { bindingMatches(expected) }
        }
        return templateStore.reanalyzeAndSave(templateId, catalog, guard)
    }

    private suspend fun bindingMatches(binding: ServerBinding): Boolean {
        val active = runCatching { deps.activeBindingProvider() }.getOrNull() ?: return false
        return active.serverId == binding.serverId &&
            active.endpointKey == binding.endpointKey
    }

    /**
     * 切换服务器后批量刷新所有模板的兼容性。
     *
     * object_info 只拉取一次；只回写缺失节点和兼容性字段，不改参数值与预设。
     * 服务器离线时返回 null，保留现有状态。
     */
    suspend fun refreshAllCompatibility(): List<WorkflowTemplate>? =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val server = currentServer()
                val serverId = server?.serverId
                val now = System.currentTimeMillis()
                if (serverId != null &&
                    serverId == lastCompatibilityServerId &&
                    now - lastCompatibilityRefreshAt < COMPATIBILITY_REFRESH_GUARD_MS
                ) {
                    return@withLock templateStore.listTemplates()
                }
                val catalog = loadCatalog(force = true) ?: return@withLock null
                if (server == null || !isCurrent(server)) return@withLock null
                val refreshed = templateStore.refreshAllCompatibility(catalog) {
                    isCurrent(server)
                } ?: return@withLock null
                if (!isCurrent(server)) return@withLock null
                lastCompatibilityServerId = serverId
                lastCompatibilityRefreshAt = now
                refreshed
            }
        }

    /**
     * 使用历史记录里的参数快照重新提交。
     *
     * 只复用当前模板仍存在的参数；模板被修改或参数被删除时，缺失项跳过，
     * 但至少需要一个匹配项，避免静默提交一个完全不同的任务。
     */
    suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult = withContext(Dispatchers.IO) {
        if (task.values.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_RECORD_NO_VALUES),
            )
        }
        val template = getTemplate(task.templateId)
            ?: return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_TEMPLATE_NOT_FOUND),
            )
        val selected = template.candidates.mapNotNull { candidate ->
            val value = task.values[candidate.id] ?: return@mapNotNull null
            if (candidate.readOnly) null else candidate.copy(value = value, selected = true)
        }
        if (selected.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_PARAMS_CHANGED),
            )
        }
        submitSelected(template, selected)
    }

    /**
     * 结果未知时复用原 promptId 重试，避免服务器实际已受理却生成第二个任务。
     *
     * 历史记录里的“再次生成”仍然走 [resubmit] 并使用新 attempt。
     */
    suspend fun retryUnknown(task: TaskRecord): WorkflowSubmitResult = withContext(Dispatchers.IO) {
        val serverId = task.serverId.ifBlank {
            runCatching { deps.activeServerIdProvider() }.getOrNull().orEmpty()
        }
        if (serverId.isBlank()) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.SERVER_NOT_CONFIGURED),
            )
        }
        // 队列/历史可能已把该记录升级；必须用磁盘当前状态而不是调用方持有的旧对象。
        val current = taskRecordRepository.get(serverId, task.promptId)
            ?: return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_TASK_RECORD_MISSING),
            )
        if (current.supersededBy != null) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_TASK_SUPERSEDED,
                    listOf(current.supersededBy.take(8)),
                ),
            )
        }
        if (current.status != TaskStatus.UNKNOWN && current.status != TaskStatus.SUBMITTING) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_TASK_NOT_RETRYABLE,
                    listOf(current.status),
                ),
            )
        }
        val requestedPromptId = current.requestedPromptId?.takeIf { it.isNotBlank() }
            ?: current.promptId
        // 只有原始请求记录（promptId == requestedPromptId）才需要向后寻找迁移新记录；
        // 迁移后的新记录本身使用自己的 promptId 重试，不能被旧记录反向阻断。
        if (current.promptId == requestedPromptId) {
            val migrated = taskRecordRepository.list(serverId).firstOrNull { record ->
                record.promptId != current.promptId &&
                    record.requestedPromptId == requestedPromptId
            }
            if (migrated != null) {
                return@withContext WorkflowSubmitResult.Error(
                    NekoMessage(
                        NekoMessageKey.WORKFLOW_TASK_SUPERSEDED,
                        listOf(migrated.promptId.take(8)),
                    ),
                )
            }
        }
        if (current.values.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_RECORD_NO_VALUES),
            )
        }
        val template = getTemplate(current.templateId)
            ?: return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_TEMPLATE_NOT_FOUND),
            )
        val selected = template.candidates.mapNotNull { candidate ->
            val value = current.values[candidate.id] ?: return@mapNotNull null
            if (candidate.readOnly) null else candidate.copy(value = value, selected = true)
        }
        if (selected.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_PARAMS_CHANGED),
            )
        }
        submitSelected(
            template = template,
            selected = selected,
            context = SubmissionRunContext(
                runIndex = 0,
                batchIndex = current.batchIndex ?: 0,
                batchCount = current.batchCount ?: 1,
            ),
            // 迁移后的新记录必须复用自身 new ID；原始请求两者相同。
            existingPromptId = current.promptId,
            // 但记录里的 requestedPromptId 要保留 old→new 映射，供旧记录阻断使用。
            preservedRequestedPromptId = current.requestedPromptId
                ?.takeIf { it.isNotBlank() }
                ?: current.promptId,
        )
    }

    internal suspend fun submitSelected(
        template: WorkflowTemplate,
        selected: List<ParameterCandidate>,
        context: SubmissionRunContext = SubmissionRunContext(),
        existingPromptId: String? = null,
        preservedRequestedPromptId: String? = null,
    ): WorkflowSubmitResult = withContext(Dispatchers.IO) {
        val stored = templateStore.getTemplate(template.id)
        if (WorkflowRepairFlag.blocksSubmit(template, stored)) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_REPAIR_REQUIRED),
            )
        }
        val config = runCatching { deps.configProvider() }.getOrNull()
            ?: return@withContext WorkflowSubmitResult.NoServer
        val serverId = runCatching { deps.activeServerIdProvider() }.getOrNull()
            ?: return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.SERVER_NOT_CONFIGURED),
            )
        val raw = readApiJson(template.id)
            ?: return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_JSON_NOT_FOUND),
            )

        // 旧 profile、快捷运行和历史重试都可能携带结构化候选；最终提交前统一过滤。
        val safeCandidates = WorkflowJsonApplier.safeCandidates(raw, template.candidates)
        val safeSelected = WorkflowJsonApplier.safeCandidates(raw, selected)
        val safetyError = runCatching {
            WorkflowCandidateSafety.validate(safeCandidates, stored?.candidates.orEmpty())
            WorkflowCandidateSafety.validate(safeSelected)
        }.exceptionOrNull()
        if (safetyError != null) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_CANDIDATES_INVALID,
                    listOf(safetyError.message ?: safetyError.javaClass.simpleName),
                ),
            )
        }
        if (safeSelected.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(NekoMessageKey.WORKFLOW_NO_SCALAR_PARAMS),
            )
        }

        // 提示词里的 #标签组 在提交前展开，历史记录也保存展开后的值。
        val expandedSelected = safeSelected.map { candidate ->
            if (candidate.group == ParameterGroup.PROMPT) {
                candidate.copy(
                    value = PromptTagExpander.expand(
                        candidate.value,
                        template.promptTagGroups,
                    ),
                )
            } else {
                candidate
            }
        }

        val prompt = runCatching {
            WorkflowJsonApplier.apply(raw, expandedSelected)
        }.getOrElse { throwable ->
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_APPLY_PARAMS_FAILED,
                    listOf(throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }

        val clientId = runCatching { deps.clientIdProvider() }.getOrElse { throwable ->
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_CLIENT_ID_FAILED,
                    listOf(throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }
        val requestedPromptId = existingPromptId ?: UUID.randomUUID().toString()
        val attemptedAt = System.currentTimeMillis()
        val values = expandedSelected.associate { it.id to it.value }
        val prepared = TaskRecord(
            promptId = requestedPromptId,
            serverId = serverId,
            templateId = template.id,
            templateName = template.name,
            clientId = clientId,
            batchIndex = context.batchIndex.takeIf { context.batchCount > 1 },
            batchCount = context.batchCount.takeIf { context.batchCount > 1 },
            submittedAt = attemptedAt,
            requestedAt = attemptedAt,
            requestedPromptId = preservedRequestedPromptId ?: requestedPromptId,
            status = TaskStatus.SUBMITTING,
            values = values,
            statusUpdatedAt = attemptedAt,
            statusSource = TaskStatusSource.LOCAL.name,
        )
        val preparedResult = if (existingPromptId != null) {
            // 重试必须在写锁内复核状态/迁移映射，不能覆盖已被队列/历史升级的记录。
            taskRecordRepository.prepareRetry(serverId, prepared)
        } else {
            taskRecordRepository.upsert(serverId, prepared)
        }
        if (preparedResult.isFailure) {
            val cause = preparedResult.exceptionOrNull()
            return@withContext WorkflowSubmitResult.Error(
                NekoMessage(
                    NekoMessageKey.WORKFLOW_LOCAL_RECORD_FAILED,
                    listOf(cause?.message ?: cause?.javaClass?.simpleName ?: "unknown"),
                ),
            )
        }
        val api = runCatching { apiFactory.create(config) }.getOrElse { throwable ->
            val message = NekoMessage(
                NekoMessageKey.WORKFLOW_NETWORK_REQUEST_FAILED,
                listOf(throwable.message ?: throwable.javaClass.simpleName),
            )
            taskRecordRepository.upsert(
                serverId,
                prepared.copy(
                    status = TaskStatus.FAILED,
                    errorMessage = throwable.message ?: throwable.javaClass.simpleName,
                    statusUpdatedAt = System.currentTimeMillis(),
                    statusSource = TaskStatusSource.LOCAL.name,
                ),
            )
            return@withContext WorkflowSubmitResult.Error(message)
        }

        suspend fun submitOnce() = api.submitPrompt(
            PromptRequestDto(
                prompt = prompt,
                clientId = clientId,
                promptId = requestedPromptId,
            ),
        )

        var response = runCatching { submitOnce() }.getOrElse { throwable ->
            markSubmissionUnknown(serverId, prepared)
            return@withContext WorkflowSubmitResult.Unknown(
                promptId = requestedPromptId,
                message = NekoMessage(
                    NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN,
                    listOf(throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }
        var httpAttempt = 1
        while (!response.isSuccessful &&
            PromptSubmissionPolicy.shouldRetry(response.code(), httpAttempt)
        ) {
            runCatching { response.errorBody()?.close() }
            markSubmissionUnknown(serverId, prepared)
            deps.retryDelay(PromptSubmissionPolicy.RETRY_DELAY_MS)
            httpAttempt += 1
            response = runCatching { submitOnce() }.getOrElse { throwable ->
                markSubmissionUnknown(serverId, prepared)
                return@withContext WorkflowSubmitResult.Unknown(
                    promptId = requestedPromptId,
                    message = NekoMessage(
                        NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN,
                        listOf(throwable.message ?: throwable.javaClass.simpleName),
                    ),
                )
            }
        }

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string().orEmpty()
            val parsed = runCatching {
                json.decodeFromString<PromptErrorResponseDto>(body)
            }.getOrNull()
            val serverSummary = parsed?.error?.message ?: parsed?.error?.details
            val summary = if (serverSummary != null) {
                NekoMessage(
                    NekoMessageKey.WORKFLOW_VALIDATION_FAILED,
                    listOf(serverSummary),
                )
            } else {
                NekoMessage(
                    NekoMessageKey.WORKFLOW_SERVER_REJECTED,
                    listOf(response.code().toString()),
                )
            }

            val nodeErrors = parsed?.nodeErrors.orEmpty().flatMap { (nodeId, nodeError) ->
                nodeError.errors.ifEmpty {
                    listOf(io.github.xiaowu20245.nekopaint.core.network.NodeErrorDetailDto(
                        message = "节点 $nodeId 校验失败",
                    ))
                }.map { detail ->
                    NodeErrorInfo(
                        nodeId = nodeId,
                        message = detail.message
                            ?: detail.details
                            ?: detail.type
                            ?: "节点 $nodeId 校验失败",
                        inputName = detail.inputName,
                        details = detail.details,
                    )
                }
            }
            val unknownResult = PromptSubmissionPolicy.classify(response.code()) ==
                PromptSubmissionPolicy.HttpClassification.UNKNOWN_RETRYABLE
            taskRecordRepository.upsert(
                serverId,
                prepared.copy(
                    status = if (unknownResult) TaskStatus.UNKNOWN else TaskStatus.FAILED,
                    errorMessage = if (unknownResult) {
                        null
                    } else {
                        serverSummary?.takeIf { it.isNotBlank() }
                            ?: "HTTP ${response.code()}"
                    },
                    statusUpdatedAt = System.currentTimeMillis(),
                    statusSource = TaskStatusSource.LOCAL.name,
                ),
            )
            if (unknownResult) {
                return@withContext WorkflowSubmitResult.Unknown(
                    promptId = requestedPromptId,
                    message = summary,
                )
            }
            return@withContext WorkflowSubmitResult.Validation(
                summary = summary,
                nodeErrors = nodeErrors,
            )
        }

        val body = response.body()
            ?: run {
                markSubmissionUnknown(serverId, prepared)
                return@withContext WorkflowSubmitResult.Unknown(
                    promptId = requestedPromptId,
                    message = NekoMessage(NekoMessageKey.WORKFLOW_SUBMIT_EMPTY_RESPONSE),
                )
            }
        val promptId = body.promptId ?: requestedPromptId
        val completed = prepared.copy(
            promptId = promptId,
            queueNumber = body.number,
            status = TaskStatus.QUEUED,
            statusUpdatedAt = System.currentTimeMillis(),
            statusSource = TaskStatusSource.LOCAL.name,
        )
        val saved = taskRecordRepository.upsert(serverId, completed)
        val localRecordWarning = when {
            saved.isFailure -> {
                // 服务器已用新 ID 接收任务，但新记录没落盘；旧请求记录必须标记为已迁移，
                // 否则 30 秒后恢复为 UNKNOWN 仍可复用旧 ID 重试。
                if (promptId != requestedPromptId) {
                    taskRecordRepository.upsert(
                        serverId,
                        prepared.copy(
                            status = TaskStatus.UNKNOWN,
                            supersededBy = promptId,
                            errorMessage = "服务器任务 ${promptId.take(8)} 已接收，" +
                                "但本地新记录保存失败，旧记录待清理",
                            statusUpdatedAt = System.currentTimeMillis(),
                            statusSource = TaskStatusSource.LOCAL.name,
                        ),
                    )
                }
                NekoMessage(
                    NekoMessageKey.WORKFLOW_LOCAL_RECORD_FAILED,
                    listOf(
                        saved.exceptionOrNull()?.message
                            ?: saved.exceptionOrNull()?.javaClass?.simpleName
                            ?: "unknown",
                    ),
                )
            }

            promptId != requestedPromptId -> {
                val deleteFailure = taskRecordRepository
                    .delete(serverId, requestedPromptId)
                    .exceptionOrNull()
                if (deleteFailure != null) {
                    // 删除失败时把旧记录标记为已迁移，Queue 不依赖新记录是否已进入内存读模型。
                    taskRecordRepository.upsert(
                        serverId,
                        prepared.copy(
                            status = TaskStatus.UNKNOWN,
                            supersededBy = promptId,
                            errorMessage = "已由新任务 ${promptId.take(8)} 接管，旧记录待清理",
                            statusUpdatedAt = System.currentTimeMillis(),
                            statusSource = TaskStatusSource.LOCAL.name,
                        ),
                    )
                }
                deleteFailure?.let { throwable ->
                    NekoMessage(
                        NekoMessageKey.WORKFLOW_OLD_RECORD_CLEANUP_FAILED,
                        listOf(throwable.message ?: requestedPromptId),
                    )
                }
            }

            else -> null
        }

        WorkflowSubmitResult.Success(
            promptId = promptId,
            queueNumber = body.number,
            localRecordWarning = localRecordWarning,
        )
    }

    private suspend fun markSubmissionUnknown(serverId: String, record: TaskRecord) {
        taskRecordRepository.upsert(
            serverId,
            record.copy(
                status = TaskStatus.UNKNOWN,
                statusUpdatedAt = System.currentTimeMillis(),
                statusSource = TaskStatusSource.LOCAL.name,
            ),
        )
    }

    private data class CurrentServer(
        val config: ServerConfig,
        val serverId: String,
        val endpointKey: String,
    )

    private suspend fun currentServer(): CurrentServer? {
        val config = runCatching { deps.configProvider() }.getOrNull() ?: return null
        val endpoint = runCatching { ServerEndpointValidator.require(config) }.getOrNull()
            ?: return null
        val serverId = runCatching { deps.activeServerIdProvider() }.getOrNull()
            ?: endpoint.endpointKey
        return CurrentServer(config, serverId, endpoint.endpointKey)
    }

    private suspend fun isCurrent(server: CurrentServer): Boolean {
        val current = currentServer() ?: return false
        return current.serverId == server.serverId &&
            current.endpointKey == server.endpointKey
    }

    private suspend fun loadCatalog(force: Boolean = false): NodeCatalog? {
        val server = currentServer() ?: return null
        val cache = deps.nodeCatalogCache
        if (!force) {
            val cached = cache?.load(server.serverId, server.endpointKey)
            if (cached != null) {
                // 读缓存期间也可能切换服务器；返回前复核身份。
                return if (isCurrent(server)) cached else null
            }
        }
        val catalog = runCatching {
            NodeCatalog(apiFactory.create(server.config).objectInfo())
        }.getOrNull() ?: return null

        // 请求期间若切换了服务器，旧 catalog 既不能写缓存，也不能继续用于
        // 回写模板兼容性；调用方必须把 null 当作“本次结果已失效”。
        if (!isCurrent(server)) return null
        cache?.save(server.serverId, server.endpointKey, catalog)
        return catalog
    }

    private fun queryFileMeta(uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        appContext?.contentResolver?.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun readLimited(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("文件超过 10 MB 限制")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private fun String.toTemplateName(): String {
        val base = substringAfterLast('/').substringAfterLast('\\')
        return base
            .removeSuffix(".json")
            .removeSuffix(".api")
            .ifBlank { "未命名工作流" }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal companion object {
        const val MAX_FILE_BYTES = 10 * 1024 * 1024
        const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
        const val COMPATIBILITY_REFRESH_GUARD_MS = 30_000L
        val TEXT_PLAIN = "text/plain".toMediaTypeOrNull()

        /** JVM 测试构造：不读取 Android Context，templateStore 使用临时目录。 */
        fun forTest(
            rootDir: File,
            json: Json,
            config: ServerConfig?,
            serverId: String?,
            clientId: String,
            apiFactory: ComfyApiFactory,
            taskRecordRepository: TaskRecordRepository,
            nodeCatalogCache: NodeCatalogCache? = null,
            retryDelay: suspend (Long) -> Unit = {},
            configProvider: (suspend () -> ServerConfig?)? = null,
            activeServerIdProvider: (suspend () -> String?)? = null,
        ): WorkflowRepository = WorkflowRepository(
            WorkflowRepositoryDependencies(
                appContext = null,
                json = json,
                templateStore = WorkflowTemplateStore(rootDir, json),
                configProvider = configProvider ?: { config },
                activeServerIdProvider = activeServerIdProvider ?: { serverId },
                clientIdProvider = { clientId },
                apiFactory = apiFactory,
                taskRecordRepository = taskRecordRepository,
                nodeCatalogCache = nodeCatalogCache,
                retryDelay = retryDelay,
            ),
        )
    }
}

/**
 * 生成不会覆盖服务器已有文件的名称，同时保留扩展名便于 ComfyUI 识别。
 */
internal fun buildUploadFileName(
    displayName: String?,
    timestamp: Long = System.currentTimeMillis(),
): String {
    val raw = displayName.orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    if (raw.isBlank()) {
        return "nekopaint_${timestamp}_image.png"
    }
    val sanitized = raw
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "image.png" }
    val extension = sanitized
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it.length <= 8 }
        ?.let { ".$it" }
        .orEmpty()
    val base = sanitized
        .removeSuffix(extension)
        .take(48)
        .ifBlank { "image" }
    return "nekopaint_${timestamp}_$base$extension"
}

internal fun buildCopyTemplateName(
    baseName: String,
    existingNames: Set<String>,
): String {
    var candidate = "$baseName 副本"
    var index = 2
    while (candidate in existingNames) {
        candidate = "$baseName 副本 $index"
        index++
    }
    return candidate
}
