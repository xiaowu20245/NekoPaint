package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.AtomicFileWriter
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog
import io.github.xiaowu20245.nekopaint.core.workflow.ParameterDetector
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCandidateSafety
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCompatibilityAnalyzer
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParseResult
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * 工作流模板的磁盘存储与修复合并逻辑。
 *
 * 不依赖 Android Context，测试可注入临时目录。所有读写入口在同一个 [Mutex] 内串行化，
 * 避免同名 profile/api.json 的更新、修复、复制和批量刷新互相覆盖。
 */
internal class WorkflowTemplateStore(
    private val rootDir: File,
    private val json: Json,
) {

    private val mutex = Mutex()

    suspend fun listTemplates(): List<WorkflowTemplate> = withContext(Dispatchers.IO) {
        mutex.withLock { listTemplatesUnlocked() }
    }

    suspend fun getTemplate(id: String): WorkflowTemplate? = withContext(Dispatchers.IO) {
        mutex.withLock { readProfile(File(rootDir, id)) }
    }

    suspend fun readApiJson(id: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(File(rootDir, id), "api.json")
            if (file.exists()) file.readText() else null
        }
    }

    suspend fun saveTemplate(template: WorkflowTemplate, rawJson: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock { saveTemplateUnlocked(template, rawJson) }
        }

    suspend fun updateTemplate(template: WorkflowTemplate) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = File(rootDir, template.id)
            if (!dir.exists()) error("Template not found")
            val stored = readStoredProfile(dir) ?: error("Template profile invalid")
            val currentView = readProfile(dir)
            val reference = (stored.candidates + currentView?.candidates.orEmpty())
                .distinctBy { it.id }
            WorkflowCandidateSafety.validate(template.candidates, reference)
            val repairRequired = WorkflowRepairFlag.effective(
                stored = stored.repairRequired,
                incoming = template.repairRequired,
                currentView = currentView?.repairRequired,
            )
            val primary = template.primaryPromptCandidateId?.takeIf { id ->
                template.candidates.any { candidate ->
                    candidate.id == id &&
                        WorkflowCandidateSafety.isPrimaryPromptCandidate(candidate)
                }
            }
            val presets = WorkflowCandidateSafety.sanitizePresets(
                template.presets,
                template.candidates,
            )
            val activePreset = template.activePresetId?.takeIf { id ->
                presets.any { it.id == id }
            }
            writeProfile(
                dir,
                template.copy(
                    repairRequired = repairRequired,
                    primaryPromptCandidateId = primary,
                    presets = presets,
                    activePresetId = activePreset,
                ),
            )
        }
    }

    /**
     * 从 api.json 重建检测派生字段。只有这条路径允许清除 repairRequired。
     */
    suspend fun repairTemplate(
        id: String,
        catalog: NodeCatalog?,
    ): Result<WorkflowTemplate> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val dir = File(rootDir, id)
                val stored = readStoredProfile(dir) ?: error("模板不存在或 profile 损坏")
                val raw = File(dir, "api.json").takeIf { it.exists() }?.readText()
                    ?: error("api.json 不存在")
                val parsed = WorkflowParser.parse(raw) as? WorkflowParseResult.Success
                    ?: error("api.json 无法解析")
                val detection = ParameterDetector.detect(parsed.graph, catalog)
                val compatibility = WorkflowCompatibilityAnalyzer.analyze(
                    graph = parsed.graph,
                    catalog = catalog,
                    missingNodeTypes = detection.missingNodeTypes,
                )
                val mergedCandidates = detection.candidates.map { candidate ->
                    val old = stored.candidates.firstOrNull {
                        it.id == candidate.id ||
                            (it.nodeId == candidate.nodeId && it.inputName == candidate.inputName)
                    }
                    if (old != null && WorkflowCandidateSafety.canReuseValue(old, candidate)) {
                        candidate.copy(value = old.value, selected = old.selected)
                    } else {
                        candidate
                    }
                }
                val primary = stored.primaryPromptCandidateId?.takeIf { storedId ->
                    mergedCandidates.any {
                        it.id == storedId &&
                            WorkflowCandidateSafety.isPrimaryPromptCandidate(it)
                    }
                }
                val presets = WorkflowCandidateSafety.sanitizePresets(
                    stored.presets,
                    mergedCandidates,
                )
                val activePreset = stored.activePresetId?.takeIf { presetId ->
                    presets.any { it.id == presetId }
                }
                val repaired = stored.copy(
                    sourceHash = raw.sha256(),
                    nodeCount = parsed.graph.nodes.size,
                    reachableNodeCount = parsed.graph.reachableNodeIds.size,
                    outputNodeIds = parsed.graph.outputNodeIds.sorted(),
                    missingNodeTypes = detection.missingNodeTypes,
                    catalogChecked = detection.catalogChecked,
                    candidates = mergedCandidates,
                    primaryPromptCandidateId = primary,
                    presets = presets,
                    activePresetId = activePreset,
                    compatibility = compatibility,
                    repairRequired = false,
                )
                writeProfile(dir, repaired)
                repaired
            }
        }
    }

    suspend fun renameTemplate(id: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val trimmed = newName.trim()
                if (trimmed.isEmpty()) {
                    return@withLock Result.failure(IllegalArgumentException("名称不能为空"))
                }
                runCatching {
                    val dir = File(rootDir, id)
                    val template = readProfile(dir) ?: error("找不到工作流模板")
                    writeProfile(dir, template.copy(name = trimmed))
                }
            }
        }

    suspend fun copyTemplate(id: String): Result<WorkflowTemplate> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val sourceDir = File(rootDir, id)
                    val source = readProfile(sourceDir) ?: error("找不到工作流模板")
                    val raw = readApiJsonUnlocked(id) ?: error("找不到工作流 JSON")
                    val existingNames = listTemplatesUnlocked().map { it.name }.toSet()
                    val copyName = buildCopyTemplateName(source.name, existingNames)
                    val copied = source.copy(
                        id = UUID.randomUUID().toString(),
                        name = copyName,
                        importedAt = System.currentTimeMillis(),
                    )
                    saveTemplateUnlocked(copied, raw)
                    copied
                }
            }
        }

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(rootDir, id).deleteRecursively()
            Unit
        }
    }

    suspend fun saveCompatibility(
        templateId: String,
        analyzed: WorkflowTemplate,
    ): WorkflowTemplate? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = File(rootDir, templateId)
            val stored = readProfile(dir) ?: return@withLock null
            if (stored.repairRequired) return@withLock stored
            val updated = stored.copy(
                missingNodeTypes = analyzed.missingNodeTypes,
                catalogChecked = analyzed.catalogChecked,
                compatibility = analyzed.compatibility,
            )
            if (updated != stored) writeProfile(dir, updated)
            updated
        }
    }

    suspend fun reanalyzeAndSave(
        templateId: String,
        catalog: NodeCatalog,
    ): WorkflowTemplate? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = File(rootDir, templateId)
            val template = readProfile(dir) ?: return@withLock null
            if (template.repairRequired) return@withLock template
            val raw = readApiJsonUnlocked(templateId) ?: return@withLock null
            val parsed = WorkflowParser.parse(raw) as? WorkflowParseResult.Success
                ?: return@withLock null
            val detection = ParameterDetector.detect(parsed.graph, catalog)
            val compatibility = WorkflowCompatibilityAnalyzer.analyze(
                graph = parsed.graph,
                catalog = catalog,
                missingNodeTypes = detection.missingNodeTypes,
            )
            val updated = template.copy(
                missingNodeTypes = detection.missingNodeTypes,
                catalogChecked = detection.catalogChecked,
                compatibility = compatibility,
            )
            writeProfile(dir, updated)
            updated
        }
    }

    suspend fun refreshAllCompatibility(
        catalog: NodeCatalog,
    ): List<WorkflowTemplate> = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureRoot()
            rootDir.listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .mapNotNull { dir ->
                    val template = readProfile(dir) ?: return@mapNotNull null
                    if (template.repairRequired) return@mapNotNull template
                    val raw = readApiJsonUnlocked(template.id) ?: return@mapNotNull template
                    val parsed = WorkflowParser.parse(raw) as? WorkflowParseResult.Success
                        ?: return@mapNotNull template
                    val detection = ParameterDetector.detect(parsed.graph, catalog)
                    val compatibility = WorkflowCompatibilityAnalyzer.analyze(
                        graph = parsed.graph,
                        catalog = catalog,
                        missingNodeTypes = detection.missingNodeTypes,
                    )
                    val updated = template.copy(
                        missingNodeTypes = detection.missingNodeTypes,
                        catalogChecked = detection.catalogChecked,
                        compatibility = compatibility,
                    )
                    if (updated != template) writeProfile(dir, updated)
                    updated
                }
                .sortedByDescending { it.importedAt }
        }
    }

    private suspend fun listTemplatesUnlocked(): List<WorkflowTemplate> {
        ensureRoot()
        return rootDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir -> readProfile(dir) }
            .sortedByDescending { it.importedAt }
    }

    private fun saveTemplateUnlocked(template: WorkflowTemplate, rawJson: String) {
        val dir = File(rootDir, template.id)
        dir.mkdirs()
        AtomicFileWriter.writeText(File(dir, "api.json"), rawJson)
        writeProfile(dir, template)
    }

    private fun writeProfile(dir: File, template: WorkflowTemplate) {
        AtomicFileWriter.writeText(
            File(dir, "profile.json"),
            json.encodeToString(WorkflowTemplate.serializer(), template),
        )
    }

    private fun readProfile(dir: File): WorkflowTemplate? {
        val template = readStoredProfile(dir) ?: return null
        val raw = File(dir, "api.json").takeIf { it.exists() }?.readText()
        if (raw != null && raw.sha256() != template.sourceHash) {
            val parsed = WorkflowParser.parse(raw) as? WorkflowParseResult.Success
            if (parsed != null) {
                val detection = ParameterDetector.detect(parsed.graph, null)
                val candidates = detection.candidates.map { candidate ->
                    val old = template.candidates.firstOrNull {
                        it.id == candidate.id ||
                            (it.nodeId == candidate.nodeId && it.inputName == candidate.inputName)
                    }
                    if (old != null && WorkflowCandidateSafety.canReuseValue(old, candidate)) {
                        candidate.copy(value = old.value, selected = old.selected)
                    } else {
                        candidate
                    }
                }
                val primary = template.primaryPromptCandidateId?.takeIf { id ->
                    candidates.any {
                        it.id == id &&
                            WorkflowCandidateSafety.isPrimaryPromptCandidate(it)
                    }
                }
                val presets = WorkflowCandidateSafety.sanitizePresets(
                    template.presets,
                    candidates,
                )
                val activePreset = template.activePresetId?.takeIf { id ->
                    presets.any { it.id == id }
                }
                return template.copy(
                    sourceHash = raw.sha256(),
                    nodeCount = parsed.graph.nodes.size,
                    reachableNodeCount = parsed.graph.reachableNodeIds.size,
                    outputNodeIds = parsed.graph.outputNodeIds.sorted(),
                    missingNodeTypes = detection.missingNodeTypes,
                    catalogChecked = detection.catalogChecked,
                    candidates = candidates,
                    primaryPromptCandidateId = primary,
                    presets = presets,
                    activePresetId = activePreset,
                    compatibility = WorkflowCompatibilityAnalyzer.analyze(
                        graph = parsed.graph,
                        catalog = null,
                        missingNodeTypes = detection.missingNodeTypes,
                    ),
                    repairRequired = true,
                )
            }
        }
        if (template.repairRequired ||
            raw == null ||
            raw.sha256() != template.sourceHash
        ) {
            return template.copy(repairRequired = true)
        }

        // 分析器升级后，读取旧模板时按当前版本重算分类与能力标签，避免必须重新导入。
        val compatibility = template.compatibility
        if (compatibility != null &&
            compatibility.analyzerVersion < WorkflowCompatibilityAnalyzer.CURRENT_VERSION
        ) {
            val parsed = WorkflowParser.parse(raw) as? WorkflowParseResult.Success
            if (parsed != null) {
                val refreshed = WorkflowCompatibilityAnalyzer.analyze(
                    graph = parsed.graph,
                    catalog = null,
                    missingNodeTypes = template.missingNodeTypes,
                ).copy(
                    issues = compatibility.issues,
                    catalogChecked = compatibility.catalogChecked,
                )
                // 只返回刷新后的视图，不隐藏写回；显式保存/修复路径负责持久化。
                return template.copy(compatibility = refreshed)
            }
        }
        return template
    }

    private fun readStoredProfile(dir: File): WorkflowTemplate? {
        val file = File(dir, "profile.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(WorkflowTemplate.serializer(), file.readText())
        }.getOrNull()
    }

    private fun readApiJsonUnlocked(id: String): String? {
        val file = File(File(rootDir, id), "api.json")
        return if (file.exists()) file.readText() else null
    }

    private fun ensureRoot() {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
