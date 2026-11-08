package io.github.xiaowu20245.nekopaint.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import io.github.xiaowu20245.nekopaint.core.datastore.ClientIdStore
import io.github.xiaowu20245.nekopaint.core.datastore.ServerConfigStore
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.UploadedImageRef
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.network.ContentUriRequestBody
import io.github.xiaowu20245.nekopaint.core.network.PromptErrorResponseDto
import io.github.xiaowu20245.nekopaint.core.network.PromptRequestDto
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog
import io.github.xiaowu20245.nekopaint.core.workflow.ParameterDetector
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCompatibilityAnalyzer
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowJsonApplier
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParseResult
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    data class Error(val message: String) : WorkflowImportResult
}

data class NodeErrorInfo(
    val nodeId: String,
    val message: String,
)

sealed interface WorkflowSubmitResult {
    data class Success(
        val promptId: String,
        val queueNumber: Int?,
    ) : WorkflowSubmitResult

    data class Validation(
        val summary: String,
        val nodeErrors: List<NodeErrorInfo>,
    ) : WorkflowSubmitResult

    data object NoServer : WorkflowSubmitResult

    data class Error(val message: String) : WorkflowSubmitResult
}

class WorkflowRepository(
    context: Context,
    private val json: Json,
    private val apiFactory: ComfyApiFactory,
    private val serverConfigStore: ServerConfigStore,
    private val clientIdStore: ClientIdStore,
    private val taskStore: TaskStore,
) {

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "workflows")

    suspend fun listTemplates(): List<WorkflowTemplate> = withContext(Dispatchers.IO) {
        ensureRoot()
        rootDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir -> readProfile(dir) }
            .sortedByDescending { it.importedAt }
    }

    suspend fun getTemplate(id: String): WorkflowTemplate? = withContext(Dispatchers.IO) {
        readProfile(File(rootDir, id))
    }

    suspend fun updateTemplate(template: WorkflowTemplate) = withContext(Dispatchers.IO) {
        val dir = File(rootDir, template.id)
        if (!dir.exists()) error("Template not found")
        writeProfile(dir, template)
    }

    suspend fun renameTemplate(id: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("名称不能为空"))
            }
            runCatching {
                val dir = File(rootDir, id)
                val template = readProfile(dir) ?: error("找不到工作流模板")
                writeProfile(dir, template.copy(name = trimmed))
            }
        }

    /**
     * 复制模板目录和参数配置，生成新的模板 ID。
     *
     * 复制名称自动追加“副本”，重名时继续加序号，避免列表出现两个同名模板。
     */
    suspend fun copyTemplate(id: String): Result<WorkflowTemplate> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceDir = File(rootDir, id)
                val source = readProfile(sourceDir) ?: error("找不到工作流模板")
                val raw = readApiJson(id) ?: error("找不到工作流 JSON")
                val existingNames = listTemplates().map { it.name }.toSet()
                val copyName = buildCopyName(source.name, existingNames)
                val copied = source.copy(
                    id = UUID.randomUUID().toString(),
                    name = copyName,
                    importedAt = System.currentTimeMillis(),
                )
                saveTemplate(copied, raw)
                copied
            }
        }

    private fun buildCopyName(baseName: String, existingNames: Set<String>): String =
        buildCopyTemplateName(baseName, existingNames)

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        File(rootDir, id).deleteRecursively()
        Unit
    }

    suspend fun readApiJson(id: String): String? = withContext(Dispatchers.IO) {
        val file = File(File(rootDir, id), "api.json")
        if (file.exists()) file.readText() else null
    }

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
        val resolver = appContext.contentResolver
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
        val config = runCatching { serverConfigStore.config.first() }.getOrNull()
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
                return@withContext WorkflowImportResult.Error("文件超过 10 MB 限制")
            }
            val raw = appContext.contentResolver.openInputStream(uri)?.use {
                readLimited(it, MAX_FILE_BYTES)
            } ?: return@withContext WorkflowImportResult.Error("无法读取文件")

            when (val parsed = WorkflowParser.parse(raw)) {
                WorkflowParseResult.UiFormat -> WorkflowImportResult.UiFormat
                is WorkflowParseResult.Invalid -> WorkflowImportResult.Error(parsed.reason)
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
                    saveTemplate(template, raw)
                    WorkflowImportResult.Success(template)
                }
            }
        } catch (throwable: Throwable) {
            WorkflowImportResult.Error(throwable.message ?: "导入失败")
        }
    }

    /**
     * 对旧模板或未连接服务器时导入的模板补做兼容性分析。
     */
    suspend fun analyzeCompatibility(template: WorkflowTemplate): WorkflowTemplate? =
        withContext(Dispatchers.IO) {
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
            // 不在这里回写整个模板，避免覆盖用户刚修改的参数值；
            // 新导入的模板已在 import() 时保存兼容性。
            template.copy(
                missingNodeTypes = detection.missingNodeTypes,
                catalogChecked = detection.catalogChecked,
                compatibility = compatibility,
            )
        }

    suspend fun submit(template: WorkflowTemplate): WorkflowSubmitResult =
        submitSelected(template, template.selectedCandidates)

    /**
     * 使用历史记录里的参数快照重新提交。
     *
     * 只复用当前模板仍存在的参数；模板被修改或参数被删除时，缺失项跳过，
     * 但至少需要一个匹配项，避免静默提交一个完全不同的任务。
     */
    suspend fun resubmit(task: TaskRecord): WorkflowSubmitResult = withContext(Dispatchers.IO) {
        if (task.values.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error("这条记录没有保存参数快照")
        }
        val template = getTemplate(task.templateId)
            ?: return@withContext WorkflowSubmitResult.Error("找不到原工作流模板")
        val selected = template.candidates.mapNotNull { candidate ->
            val value = task.values[candidate.id] ?: return@mapNotNull null
            if (candidate.readOnly) null else candidate.copy(value = value, selected = true)
        }
        if (selected.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error("原模板参数已变化，无法再次生成")
        }
        submitSelected(template, selected)
    }

    private suspend fun submitSelected(
        template: WorkflowTemplate,
        selected: List<ParameterCandidate>,
    ): WorkflowSubmitResult = withContext(Dispatchers.IO) {
        val config = runCatching { serverConfigStore.config.first() }.getOrNull()
            ?: return@withContext WorkflowSubmitResult.NoServer
        val serverId = runCatching { serverConfigStore.activeServerId.first() }.getOrNull()
            ?: return@withContext WorkflowSubmitResult.Error("找不到当前服务器配置")
        val raw = readApiJson(template.id)
            ?: return@withContext WorkflowSubmitResult.Error("找不到工作流 JSON")

        if (selected.isEmpty()) {
            return@withContext WorkflowSubmitResult.Error("没有选中任何参数")
        }

        val prompt = runCatching {
            WorkflowJsonApplier.apply(raw, selected)
        }.getOrElse { throwable ->
            return@withContext WorkflowSubmitResult.Error(
                throwable.message ?: "应用参数失败",
            )
        }

        val clientId = clientIdStore.getOrCreate()
        val requestedPromptId = UUID.randomUUID().toString()
        val api = runCatching { apiFactory.create(config) }.getOrElse { throwable ->
            return@withContext WorkflowSubmitResult.Error(
                throwable.message ?: "无法创建网络请求",
            )
        }

        val response = runCatching {
            api.submitPrompt(
                PromptRequestDto(
                    prompt = prompt,
                    clientId = clientId,
                    promptId = requestedPromptId,
                ),
            )
        }.getOrElse { throwable ->
            return@withContext WorkflowSubmitResult.Error(
                throwable.message ?: "提交失败",
            )
        }

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string().orEmpty()
            val parsed = runCatching {
                json.decodeFromString<PromptErrorResponseDto>(body)
            }.getOrNull()
            val summary = parsed?.error?.message
                ?: parsed?.error?.details
                ?: "服务器拒绝了任务（HTTP ${response.code()}）"

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
                    )
                }
            }
            return@withContext WorkflowSubmitResult.Validation(
                summary = summary,
                nodeErrors = nodeErrors,
            )
        }

        val body = response.body()
            ?: return@withContext WorkflowSubmitResult.Error("服务器返回为空")
        val promptId = body.promptId ?: requestedPromptId

        runCatching {
            taskStore.save(
                serverId = serverId,
                record = io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord(
                    promptId = promptId,
                    serverId = serverId,
                    templateId = template.id,
                    templateName = template.name,
                    clientId = clientId,
                    queueNumber = body.number,
                    submittedAt = System.currentTimeMillis(),
                    values = selected.associate { it.id to it.value },
                ),
            )
        }

        WorkflowSubmitResult.Success(
            promptId = promptId,
            queueNumber = body.number,
        )
    }

    private suspend fun loadCatalog(): NodeCatalog? {
        val config = serverConfigStore.config.first() ?: return null
        return runCatching {
            NodeCatalog(apiFactory.create(config).objectInfo())
        }.getOrNull()
    }

    private fun saveTemplate(template: WorkflowTemplate, rawJson: String) {
        val dir = File(rootDir, template.id)
        dir.mkdirs()
        File(dir, "api.json").writeText(rawJson)
        writeProfile(dir, template)
    }

    private fun writeProfile(dir: File, template: WorkflowTemplate) {
        File(dir, "profile.json").writeText(
            json.encodeToString(WorkflowTemplate.serializer(), template),
        )
    }

    private fun readProfile(dir: File): WorkflowTemplate? {
        val file = File(dir, "profile.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(WorkflowTemplate.serializer(), file.readText())
        }.getOrNull()
    }

    private fun ensureRoot() {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    private fun queryFileMeta(uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        appContext.contentResolver.query(
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

    private companion object {
        const val MAX_FILE_BYTES = 10 * 1024 * 1024
        const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
        val TEXT_PLAIN = "text/plain".toMediaTypeOrNull()
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
