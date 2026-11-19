package io.github.xiaowu20245.nekopaint.feature.workflow

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding
import io.github.xiaowu20245.nekopaint.core.model.workflow.UploadedImageRef
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.ModelInputResolver
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCompatibilityAnalyzer
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowPresetMatcher
import io.github.xiaowu20245.nekopaint.data.operation.OperationCenter
import io.github.xiaowu20245.nekopaint.data.operation.OperationStatus
import io.github.xiaowu20245.nekopaint.data.operation.SubmissionOperationState
import io.github.xiaowu20245.nekopaint.data.repository.ModelsRepository
import io.github.xiaowu20245.nekopaint.data.repository.NodeErrorInfo
import io.github.xiaowu20245.nekopaint.data.repository.NodeInstallRepository
import io.github.xiaowu20245.nekopaint.data.repository.PendingWorkflowRefillStore
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.di.AppContainer
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FieldError {
    data class Local(@param:StringRes val messageRes: Int) : FieldError
    data class Server(val message: String) : FieldError
}

data class SubmitErrorUi(
    @param:StringRes val messageRes: Int? = null,
    val message: String? = null,
    val nodeErrors: List<NodeErrorInfo> = emptyList(),
)

sealed interface PresetNotice {
    data class Saved(val name: String) : PresetNotice
    data class Updated(val name: String) : PresetNotice
    data object Deleted : PresetNotice
}

data class WorkflowDetailUiState(
    val loading: Boolean = true,
    val template: WorkflowTemplate? = null,
    val advanced: Boolean = false,
    val editing: Boolean = false,
    val editSnapshotIds: Set<String> = emptySet(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val submitting: Boolean = false,
    val batchCount: Int = 1,
    val seedStrategy: SeedStrategy = SeedStrategy.RANDOM,
    val submittedBatchCount: Int = 1,
    val presetNotice: PresetNotice? = null,
    val fieldErrors: Map<String, FieldError> = emptyMap(),
    val submitError: SubmitErrorUi? = null,
    val submission: WorkflowSubmitResult.Success? = null,
    val uploadingCandidateId: String? = null,
    val uploadedImageName: String? = null,
    val imageUploadError: String? = null,
    val rawJsonVisible: Boolean = false,
    val rawJson: String? = null,
    val checkingNodes: Boolean = false,
    val repairing: Boolean = false,
    val repairError: String? = null,
    @param:StringRes val errorRes: Int? = null,
) {
    val visibleCandidates: List<ParameterCandidate>
        get() = template?.let { current ->
            when {
                advanced -> current.candidates
                editing -> current.candidates.filter { it.id in editSnapshotIds }
                else -> current.selectedCandidates
            }
        }.orEmpty()
}

data class MaskExportState(
    val rendering: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
) {
    val busy: Boolean
        get() = rendering || uploading
}

data class NodeInstallUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val plan: NodeInstallPlan? = null,
    val binding: ServerBinding? = null,
    val error: String? = null,
    val installing: Boolean = false,
    val progress: String? = null,
    val restartAfterInstall: Boolean = true,
    val result: String? = null,
    val remainingMissing: List<String> = emptyList(),
)

/** install 后 plan 会被清空，recheck 必须使用安装时保存的 binding。 */
internal fun NodeInstallUiState.recheckBinding(): ServerBinding? = binding ?: plan?.binding

class WorkflowDetailViewModel(
    private val templateId: String,
    private val repository: WorkflowRepository,
    private val operationCenter: OperationCenter,
    private val modelsRepository: ModelsRepository,
    private val taskManager: TaskManager,
    private val pendingRefillStore: PendingWorkflowRefillStore,
    private val nodeInstallRepository: NodeInstallRepository,
    private val maskExportScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowDetailUiState())
    val uiState: StateFlow<WorkflowDetailUiState> = _uiState.asStateFlow()

    private val _nodeInstallState = MutableStateFlow(NodeInstallUiState())
    val nodeInstallState: StateFlow<NodeInstallUiState> = _nodeInstallState.asStateFlow()

    private val _maskExportState = MutableStateFlow(MaskExportState())
    val maskExportState: StateFlow<MaskExportState> = _maskExportState.asStateFlow()

    /**
     * 蒙版来源图只在打开蒙版编辑器时订阅，避免历史刷新触发表单整页重组。
     */
    val maskSourceImages: StateFlow<List<HistoryImage>> = taskManager.history
        .map { entries ->
            entries
                .flatMap { entry -> entry.images.filter { it.type == "output" } }
                .take(60)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private var compatibilityFresh = false
    private var activeOperationId: String? = null

    init {
        viewModelScope.launch {
            val loaded = runCatching { repository.getTemplate(templateId) }.getOrNull()
                ?.let { current ->
                    current.copy(
                        candidates = current.candidates.map { candidate ->
                            if (candidate.value.isBlank() && candidate.defaultValue.isNotBlank()) {
                                candidate.copy(value = candidate.defaultValue)
                            } else {
                                candidate
                            }
                        },
                    )
                }
            val refillValues = loaded?.let { pendingRefillStore.consume(templateId) }
            val template = if (loaded != null && refillValues != null) {
                loaded.copy(
                    candidates = loaded.candidates.map { candidate ->
                        val value = refillValues[candidate.id]
                        if (value != null && !candidate.readOnly) {
                            candidate.copy(value = value, selected = true)
                        } else {
                            candidate
                        }
                    },
                    // 历史参数是临时回填，不应继续显示为某个已保存预设。
                    activePresetId = null,
                )
            } else {
                loaded
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    template = template,
                    dirty = refillValues != null && template != null,
                    errorRes = if (template == null) R.string.workflow_error_generic else null,
                )
            }
            template?.let { loadModelOptions(it) }
            // 服务器可能切换过，旧模板的缺失节点缓存不一定适用于当前主机；每次打开都复核一次。
            template?.let { refreshCompatibility(it) }
        }
        taskManager.refresh()
        viewModelScope.launch {
            operationCenter.states
                .map { states ->
                    states.values
                        .filter { it.templateId == templateId }
                        .maxByOrNull { it.updatedAt }
                }
                .distinctUntilChanged()
                .collect { operation ->
                    if (operation == null) return@collect
                    val active = activeOperationId
                    if (operation.operationId != active &&
                        !(active == null && operation.status == OperationStatus.RUNNING)
                    ) {
                        return@collect
                    }
                    if (operation.operationId != active) {
                        activeOperationId = operation.operationId
                    }
                    applyOperationState(operation)
                    if (operation.isTerminal) {
                        activeOperationId = null
                    }
                }
        }
    }

    private fun refreshCompatibility(template: WorkflowTemplate) {
        compatibilityFresh = false
        viewModelScope.launch {
            // 避开页面进入动画；模板内容先稳定绘制，再开始兼容性复核。
            delay(INITIAL_SETTLE_DELAY_MS)
            _uiState.update { it.copy(checkingNodes = true) }
            try {
                val analyzed = repository.analyzeCompatibility(template) ?: return@launch
                // 未连接服务器时 catalogChecked 为 false，不能把“无法检查”写成“没有缺失”。
                if (!analyzed.catalogChecked) return@launch
                val saved = repository.saveCompatibility(template.id, analyzed) ?: analyzed
                compatibilityFresh = true
                _uiState.update { state ->
                    val current = state.template ?: return@update state
                    if (current.id != saved.id) return@update state
                    state.copy(
                        template = current.copy(
                            missingNodeTypes = saved.missingNodeTypes,
                            catalogChecked = saved.catalogChecked,
                            compatibility = saved.compatibility,
                        ),
                    )
                }
            } finally {
                _uiState.update { it.copy(checkingNodes = false) }
            }
        }
    }

    /**
     * 增量补全模型下拉选项。
     *
     * 导入时如果服务器离线，object_info 里的模型列表可能为空；
     * 这里在打开模板时用 `/models/{folder}` 再补一次，不改变参数值。
     */
    private fun loadModelOptions(template: WorkflowTemplate) {
        val folderByCandidate = template.candidates.mapNotNull { candidate ->
            ModelInputResolver.folderFor(candidate.classType, candidate.inputName)
                ?.let { candidate.id to it }
        }.toMap()
        if (folderByCandidate.isEmpty()) return

        viewModelScope.launch {
            delay(INITIAL_SETTLE_DELAY_MS)
            modelsRepository.load(folderByCandidate.values.toSet())
                .onSuccess { catalog ->
                    _uiState.update { state ->
                        val current = state.template ?: return@update state
                        val updated = current.copy(
                            candidates = current.candidates.map { candidate ->
                                val folder = folderByCandidate[candidate.id]
                                    ?: return@map candidate
                                val remote = catalog[folder].orEmpty()
                                if (remote.isEmpty()) return@map candidate
                                val options = (
                                    listOf(candidate.value).filter { it.isNotBlank() } +
                                        remote +
                                        candidate.constraints.options
                                    ).distinct()
                                candidate.copy(
                                    constraints = candidate.constraints.copy(options = options),
                                )
                            },
                        )
                        state.copy(template = updated)
                    }
                }
        }
    }

    fun setAdvanced(value: Boolean) {
        _uiState.update {
            it.copy(
                advanced = value,
                editing = false,
                editSnapshotIds = emptySet(),
            )
        }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                advanced = !it.advanced,
                editing = false,
                editSnapshotIds = emptySet(),
            )
        }
    }

    fun startEditing() {
        val state = _uiState.value
        val template = state.template ?: return
        _uiState.update {
            it.copy(
                editing = true,
                editSnapshotIds = template.selectedCandidates.map { candidate -> candidate.id }.toSet(),
            )
        }
    }

    fun finishEditing() {
        _uiState.update {
            it.copy(
                editing = false,
                editSnapshotIds = emptySet(),
                dirty = true,
                saved = false,
            )
        }
    }

    fun toggleCandidate(candidateId: String) {
        val template = _uiState.value.template ?: return
        val updated = template.copy(
            candidates = template.candidates.map { candidate ->
                if (candidate.id == candidateId && !candidate.readOnly) {
                    candidate.copy(selected = !candidate.selected)
                } else {
                    candidate
                }
            },
        )
        _uiState.update {
            it.copy(
                template = updated,
                dirty = true,
                saved = false,
                submission = null,
                submitError = null,
            )
        }
    }

    fun setPrimaryPrompt(candidateId: String?) {
        val template = _uiState.value.template ?: return
        if (candidateId != null) {
            val valid = template.candidates.any { candidate ->
                candidate.id == candidateId &&
                    candidate.group == ParameterGroup.PROMPT &&
                    !candidate.readOnly &&
                    !candidate.label.contains("负")
            }
            if (!valid) return
        }
        _uiState.update { it.copy(errorRes = null) }
        viewModelScope.launch {
            repository.patchPrimaryPrompt(template.id, candidateId)
                .onSuccess { saved ->
                    _uiState.update { state ->
                        state.copy(
                            template = state.template?.copy(
                                primaryPromptCandidateId = saved.primaryPromptCandidateId,
                            ) ?: saved,
                            errorRes = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun onValueChange(candidateId: String, value: String) {
        val template = _uiState.value.template ?: return
        val updated = template.copy(
            candidates = template.candidates.map { candidate ->
                if (candidate.id == candidateId) candidate.copy(value = value) else candidate
            },
        )
        _uiState.update {
            it.copy(
                template = updated,
                dirty = true,
                saved = false,
                fieldErrors = it.fieldErrors - candidateId,
                submission = null,
                submitError = null,
            )
        }
    }

    /**
     * 选图后上传到服务器，并直接把返回的文件名写入对应参数。
     *
     * 上传和参数编辑共用同一个 dirty/save 流程：离开前保存或提交即可持久化。
     */
    fun uploadImage(candidateId: String, uri: Uri) {
        uploadToCandidate(candidateId) { repository.uploadImage(uri) }
    }

    /**
     * 在后台按预算渲染蒙版并上传，Bitmap 只在本次导出内持有，所有路径 finally recycle。
     */
    fun exportMask(
        candidateId: String,
        strokes: List<MaskStroke>,
        sourceWidth: Int,
        sourceHeight: Int,
    ) {
        if (_maskExportState.value.busy) return
        if (strokes.isEmpty() || sourceWidth <= 0 || sourceHeight <= 0) {
            _maskExportState.value = MaskExportState(error = "蒙版尺寸或笔迹无效")
            return
        }
        _maskExportState.value = MaskExportState(rendering = true)
        maskExportScope.launch {
            var bitmap: Bitmap? = null
            try {
                val target = MaskExportPolicy.targetSize(sourceWidth, sourceHeight)
                // 让渲染完成并把 Bitmap 赋给可清理变量后再响应取消，避免返回窗口泄漏。
                bitmap = withContext(Dispatchers.Default + NonCancellable) {
                    MaskBitmapRenderer.render(target, strokes)
                }
                _maskExportState.update { it.copy(rendering = false, uploading = true) }
                repository.uploadMask(bitmap)
                    .onSuccess { uploaded ->
                        _uiState.update { state ->
                            val template = state.template ?: return@update state
                            state.copy(
                                template = template.copy(
                                    candidates = template.candidates.map { candidate ->
                                        if (candidate.id == candidateId) {
                                            candidate.copy(value = uploaded.reference)
                                        } else {
                                            candidate
                                        }
                                    },
                                ),
                                uploadedImageName = uploaded.fileName,
                                imageUploadError = null,
                                dirty = true,
                                saved = false,
                                fieldErrors = state.fieldErrors - candidateId,
                                submission = null,
                                submitError = null,
                            )
                        }
                        _maskExportState.value = MaskExportState(success = true)
                    }
                    .onFailure { throwable ->
                        _maskExportState.value = MaskExportState(
                            error = throwable.message ?: "蒙版上传失败",
                        )
                    }
            } catch (oom: OutOfMemoryError) {
                _maskExportState.value = MaskExportState(error = "图片过大，内存不足")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _maskExportState.value = MaskExportState(
                    error = throwable.message ?: "蒙版导出失败",
                )
            } finally {
                bitmap?.recycle()
                _maskExportState.update { state ->
                    if (state.busy) state.copy(rendering = false, uploading = false) else state
                }
            }
        }
    }

    fun resetMaskExportState() {
        _maskExportState.value = MaskExportState()
    }

    private fun uploadToCandidate(
        candidateId: String,
        upload: suspend () -> Result<UploadedImageRef>,
    ) {
        if (_uiState.value.uploadingCandidateId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    uploadingCandidateId = candidateId,
                    uploadedImageName = null,
                    imageUploadError = null,
                )
            }
            upload()
                .onSuccess { uploaded ->
                    _uiState.update { state ->
                        val template = state.template ?: return@update state
                        state.copy(
                            template = template.copy(
                                candidates = template.candidates.map { candidate ->
                                    if (candidate.id == candidateId) {
                                        candidate.copy(value = uploaded.reference)
                                    } else {
                                        candidate
                                    }
                                },
                            ),
                            uploadingCandidateId = null,
                            uploadedImageName = uploaded.fileName,
                            dirty = true,
                            saved = false,
                            fieldErrors = state.fieldErrors - candidateId,
                            submission = null,
                            submitError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            uploadingCandidateId = null,
                            imageUploadError = throwable.message ?: "上传失败",
                        )
                    }
                }
        }
    }

    fun clearImageUploadNotice() {
        _uiState.update {
            it.copy(uploadedImageName = null, imageUploadError = null)
        }
    }

    fun showRawJson() {
        viewModelScope.launch {
            val raw = runCatching { repository.readApiJson(templateId) }.getOrNull()
            _uiState.update { it.copy(rawJsonVisible = true, rawJson = raw) }
        }
    }

    fun dismissRawJson() {
        _uiState.update { it.copy(rawJsonVisible = false) }
    }

    fun stepBatchCount(delta: Int) {
        _uiState.update { state ->
            state.copy(batchCount = (state.batchCount + delta).coerceIn(1, 16))
        }
    }

    fun cycleSeedStrategy() {
        _uiState.update { state ->
            val next = when (state.seedStrategy) {
                SeedStrategy.RANDOM -> SeedStrategy.INCREMENT
                SeedStrategy.INCREMENT -> SeedStrategy.FIXED
                SeedStrategy.FIXED -> SeedStrategy.RANDOM
            }
            state.copy(seedStrategy = next)
        }
    }

    /**
     * 切换参数预设。
     * `presetId == null` 表示切回工作流初始默认参数；
     * 非空时使用 `WorkflowPresetMatcher` 按精确节点 ID + 语义签名双重匹配回填。
     */
    fun selectPreset(presetId: String?) {
        val current = _uiState.value.template ?: return
        val nextCandidates = if (presetId == null) {
            WorkflowPresetMatcher.resetToDefault(current.candidates)
        } else {
            val preset = current.presets.firstOrNull { it.id == presetId } ?: return
            WorkflowPresetMatcher.applyPreset(current.candidates, preset)
        }
        _uiState.update {
            it.copy(
                fieldErrors = emptyMap(),
                submission = null,
                submitError = null,
                errorRes = null,
            )
        }
        viewModelScope.launch {
            repository.patchApplyPreset(current.id, nextCandidates, presetId)
                .onSuccess { saved ->
                    val flags = WorkflowDirtyPolicy.selectPreset()
                    _uiState.update {
                        it.copy(
                            template = saved,
                            dirty = flags.dirty,
                            saved = flags.saved,
                            fieldErrors = emptyMap(),
                            submission = null,
                            submitError = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun saveNewPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value.template ?: return
        val newPreset = WorkflowPreset(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            entries = WorkflowPresetMatcher.buildEntries(current.candidates),
            updatedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            repository.patchSavePreset(current.id, newPreset, current.candidates)
                .onSuccess { saved ->
                    val flags = WorkflowDirtyPolicy.saveNewPreset()
                    _uiState.update {
                        it.copy(
                            template = saved,
                            dirty = flags.dirty,
                            saved = flags.saved,
                            presetNotice = PresetNotice.Saved(trimmed),
                            errorRes = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun renameActivePreset(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value.template ?: return
        val activeId = current.activePresetId ?: return
        val updatedPreset = current.presets.firstOrNull { it.id == activeId }
            ?.copy(name = trimmed, updatedAt = System.currentTimeMillis())
            ?: return
        viewModelScope.launch {
            repository.patchPreset(current.id, updatedPreset)
                .onSuccess { saved ->
                    _uiState.update { state ->
                        state.copy(
                            template = state.template?.copy(
                                presets = saved.presets,
                                activePresetId = saved.activePresetId,
                            ) ?: saved,
                            presetNotice = PresetNotice.Updated(trimmed),
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun deleteActivePreset() {
        val current = _uiState.value.template ?: return
        val activeId = current.activePresetId ?: return
        viewModelScope.launch {
            repository.deletePreset(current.id, activeId)
                .onSuccess { saved ->
                    _uiState.update { state ->
                        state.copy(
                            template = state.template?.copy(
                                presets = saved.presets,
                                activePresetId = saved.activePresetId,
                            ) ?: saved,
                            presetNotice = PresetNotice.Deleted,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun clearPresetNotice() {
        _uiState.update { it.copy(presetNotice = null) }
    }

    /**
     * 新增或更新提示词标签组。名称不带 `#`，在提示词文本中以 `#名称` 引用。
     * 标签组随工作流模板持久化，因此这里立即写盘，避免只存在于弹窗内存中。
     */
    fun addPromptTagGroup(name: String, tags: List<String>) {
        val groupName = name.trim().removePrefix("#").trim()
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }
        if (groupName.isBlank() || cleanTags.isEmpty()) return
        val current = _uiState.value.template ?: return
        val existing = current.promptTagGroups.firstOrNull {
            it.name.equals(groupName, ignoreCase = true)
        }
        val groups = if (existing == null) {
            current.promptTagGroups + PromptTagGroup(
                id = UUID.randomUUID().toString(),
                name = groupName,
                tags = cleanTags,
            )
        } else {
            current.promptTagGroups.map { group ->
                if (group.id == existing.id) group.copy(tags = cleanTags) else group
            }
        }
        _uiState.update {
            it.copy(
                template = it.template?.copy(promptTagGroups = groups),
                errorRes = null,
            )
        }
        viewModelScope.launch {
            // 只 patch 标签组，不能顺带提交尚未保存的提示词/参数编辑。
            repository.patchPromptTagGroups(current.id, groups)
                .onSuccess { saved ->
                    _uiState.update { state ->
                        state.copy(
                            template = state.template?.copy(
                                promptTagGroups = saved.promptTagGroups,
                            ) ?: saved,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun deletePromptTagGroup(id: String) {
        val current = _uiState.value.template ?: return
        val groups = current.promptTagGroups.filterNot { it.id == id }
        _uiState.update {
            it.copy(
                template = it.template?.copy(promptTagGroups = groups),
                errorRes = null,
            )
        }
        viewModelScope.launch {
            repository.patchPromptTagGroups(current.id, groups)
                .onSuccess { saved ->
                    _uiState.update { state ->
                        state.copy(
                            template = state.template?.copy(
                                promptTagGroups = saved.promptTagGroups,
                            ) ?: saved,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun openNodeInstall() {
        if (_uiState.value.template == null) return
        _nodeInstallState.value = NodeInstallUiState(visible = true, loading = true)
        viewModelScope.launch {
            nodeInstallRepository.preparePlan(
                templateId = templateId,
                reanalyze = !compatibilityFresh,
            ).fold(
                onSuccess = { plan ->
                    _nodeInstallState.update {
                        it.copy(
                            loading = false,
                            plan = plan,
                            binding = plan.binding,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    if (error.message == "节点已经齐全，无需安装") {
                        repository.getTemplate(templateId)?.let(::applyCompatibilityUpdate)
                    }
                    _nodeInstallState.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "检查缺失节点失败",
                        )
                    }
                },
            )
        }
    }

    fun dismissNodeInstall() {
        if (_nodeInstallState.value.installing) return
        _nodeInstallState.value = NodeInstallUiState()
    }

    fun setRestartAfterInstall(value: Boolean) {
        _nodeInstallState.update { it.copy(restartAfterInstall = value) }
    }

    fun confirmNodeInstall() {
        val current = _nodeInstallState.value
        val plan = current.plan ?: return
        if (current.installing) return
        _nodeInstallState.update {
            it.copy(
                installing = true,
                error = null,
                progress = "正在提交安装请求…",
            )
        }
        viewModelScope.launch {
            nodeInstallRepository.install(
                plan = plan,
                restartAfterInstall = current.restartAfterInstall,
                onProgress = { message ->
                    _nodeInstallState.update { it.copy(progress = message) }
                },
            ).fold(
                onSuccess = { outcome ->
                    outcome.updatedTemplate?.let(::applyCompatibilityUpdate)
                    _nodeInstallState.update {
                        it.copy(
                            installing = false,
                            plan = null,
                            binding = current.plan?.binding ?: current.binding,
                            progress = null,
                            result = outcome.summary(),
                            remainingMissing = outcome.remainingMissingNodeTypes,
                        )
                    }
                },
                onFailure = { error ->
                    _nodeInstallState.update {
                        it.copy(
                            installing = false,
                            progress = null,
                            error = error.message ?: "安装失败",
                        )
                    }
                },
            )
        }
    }

    fun recheckNodeInstall() {
        val current = _nodeInstallState.value
        if (current.installing) return
        _nodeInstallState.update {
            it.copy(loading = true, error = null, result = null)
        }
        viewModelScope.launch {
            nodeInstallRepository.recheck(templateId, current.recheckBinding()).fold(
                onSuccess = { updated ->
                    applyCompatibilityUpdate(updated)
                    val remaining = updated.missingNodeTypes
                    _nodeInstallState.update {
                        it.copy(
                            loading = false,
                            plan = null,
                            result = if (remaining.isEmpty()) {
                                "节点已齐全。"
                            } else {
                                "仍有 ${remaining.size} 个节点缺失：" +
                                    remaining.take(8).joinToString("、")
                            },
                            remainingMissing = remaining,
                        )
                    }
                },
                onFailure = { error ->
                    _nodeInstallState.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "重新检查失败",
                        )
                    }
                },
            )
        }
    }

    private fun applyCompatibilityUpdate(updated: WorkflowTemplate) {
        _uiState.update { state ->
            val current = state.template ?: return@update state
            if (current.id != updated.id) return@update state
            state.copy(
                template = current.copy(
                    missingNodeTypes = updated.missingNodeTypes,
                    catalogChecked = updated.catalogChecked,
                    compatibility = updated.compatibility,
                ),
            )
        }
    }

    fun repairTemplate() {
        val state = _uiState.value
        if (state.repairing) return
        val template = state.template ?: return
        _uiState.update { it.copy(repairing = true, repairError = null) }
        viewModelScope.launch {
            repository.repairTemplate(template.id)
                .onSuccess { repaired ->
                    _uiState.update {
                        it.copy(
                            template = repaired,
                            dirty = false,
                            repairing = false,
                            repairError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            repairing = false,
                            repairError = error.message ?: "修复工作流失败",
                        )
                    }
                }
        }
    }

    fun save(onSaved: (() -> Unit)? = null) {
        val current = _uiState.value.template ?: return
        if (_uiState.value.saving) return
        val activeId = current.activePresetId
        val activePresetName = current.presets.firstOrNull { it.id == activeId }?.name
        val templateToSave = if (activeId != null) {
            val freshEntries = WorkflowPresetMatcher.buildEntries(current.candidates)
            current.copy(
                presets = current.presets.map { preset ->
                    if (preset.id == activeId) {
                        preset.copy(
                            entries = freshEntries,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        preset
                    }
                },
            )
        } else {
            current
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorRes = null) }
            val result = runCatching { repository.updateTemplate(templateToSave) }
            _uiState.update {
                it.copy(
                    template = if (result.isSuccess) templateToSave else it.template,
                    saving = false,
                    dirty = result.isFailure,
                    saved = result.isSuccess && activePresetName == null,
                    presetNotice = if (result.isSuccess && activePresetName != null) {
                        PresetNotice.Updated(activePresetName)
                    } else {
                        it.presetNotice
                    },
                    errorRes = if (result.isFailure) R.string.workflow_error_generic else null,
                )
            }
            if (result.isSuccess) {
                onSaved?.invoke()
            }
        }
    }

    /** 返回键/顶部返回箭头询问：当前模板是否仍有后台提交在运行。 */
    fun hasActiveSubmission(): Boolean =
        operationCenter.stateFor(templateId)?.status == OperationStatus.RUNNING

    fun submit() {
        val state = _uiState.value
        val template = state.template ?: return
        if (state.submitting) return

        val errors = validateSelected(template)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    fieldErrors = errors,
                    submitError = SubmitErrorUi(
                        messageRes = R.string.workflow_validation_failed,
                    ),
                )
            }
            return
        }

        val totalRuns = state.batchCount.coerceIn(1, 16)
        val strategy = state.seedStrategy
        val started = operationCenter.start(template, totalRuns, strategy)
        started.onFailure { error ->
            _uiState.update {
                it.copy(
                    submitError = SubmitErrorUi(
                        message = error.message ?: "无法启动提交任务",
                    ),
                )
            }
        }.onSuccess { operationId ->
            activeOperationId = operationId
            _uiState.update {
                it.copy(
                    submitting = true,
                    fieldErrors = emptyMap(),
                    submitError = null,
                    submission = null,
                )
            }
            operationCenter.stateFor(templateId)
                ?.takeIf { it.operationId == operationId }
                ?.let(::applyOperationState)
        }
    }

    private fun applyOperationState(operation: SubmissionOperationState) {
        var needsCompatibilityRefresh = false
        _uiState.update { current ->
            val mapped = WorkflowDetailOperationPolicy.apply(current, operation)
            needsCompatibilityRefresh = mapped.refreshCompatibility
            mapped.state
        }
        if (needsCompatibilityRefresh) {
            _uiState.value.template?.let(::refreshCompatibility)
        }
    }

    fun clearSubmission() {
        _uiState.update { it.copy(submission = null) }
    }

    fun clearSubmitError() {
        _uiState.update { it.copy(submitError = null, fieldErrors = emptyMap()) }
    }

    fun clearSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    private fun validateSelected(template: WorkflowTemplate): Map<String, FieldError> {
        val errors = mutableMapOf<String, FieldError>()
        for (candidate in template.selectedCandidates) {
            if (candidate.readOnly) continue
            val value = candidate.value.trim()
            when (candidate.type) {
                ParameterType.INT -> {
                    val number = value.toLongOrNull()
                    when {
                        number == null -> errors[candidate.id] =
                            FieldError.Local(R.string.workflow_invalid_int)

                        candidate.constraints.min != null &&
                            number < candidate.constraints.min -> errors[candidate.id] =
                            FieldError.Local(R.string.workflow_out_of_range)

                        candidate.constraints.max != null &&
                            number > candidate.constraints.max -> errors[candidate.id] =
                            FieldError.Local(R.string.workflow_out_of_range)
                    }
                }

                ParameterType.FLOAT -> {
                    val number = value.toDoubleOrNull()
                    when {
                        number == null -> errors[candidate.id] =
                            FieldError.Local(R.string.workflow_invalid_float)

                        candidate.constraints.min != null &&
                            number < candidate.constraints.min -> errors[candidate.id] =
                            FieldError.Local(R.string.workflow_out_of_range)

                        candidate.constraints.max != null &&
                            number > candidate.constraints.max -> errors[candidate.id] =
                            FieldError.Local(R.string.workflow_out_of_range)
                    }
                }

                ParameterType.ENUM -> {
                    if (value.isEmpty()) {
                        errors[candidate.id] = FieldError.Local(R.string.workflow_field_required)
                    } else if (
                        candidate.constraints.options.isNotEmpty() &&
                        value !in candidate.constraints.options
                    ) {
                        errors[candidate.id] = FieldError.Local(R.string.workflow_invalid_enum)
                    }
                }

                ParameterType.IMAGE -> {
                    if (value.isEmpty()) {
                        errors[candidate.id] = FieldError.Local(R.string.workflow_field_required)
                    }
                }

                ParameterType.BOOLEAN,
                ParameterType.TEXT,
                ParameterType.MULTILINE_TEXT,
                ParameterType.UNKNOWN,
                -> Unit
            }
        }
        return errors
    }

    companion object {
        private const val INITIAL_SETTLE_DELAY_MS = 250L

        fun factory(
            container: AppContainer,
            templateId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkflowDetailViewModel(
                    templateId = templateId,
                    repository = container.workflowRepository,
                    operationCenter = container.operationCenter,
                    modelsRepository = container.modelsRepository,
                    taskManager = container.taskManager,
                    pendingRefillStore = container.pendingWorkflowRefillStore,
                    nodeInstallRepository = container.nodeInstallRepository,
                    maskExportScope = container.operationScope,
                )
            }
        }
    }
}
