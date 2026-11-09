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
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.UploadedImageRef
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.ModelInputResolver
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowCompatibilityAnalyzer
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowPresetMatcher
import io.github.xiaowu20245.nekopaint.data.repository.ModelsRepository
import io.github.xiaowu20245.nekopaint.data.repository.NodeErrorInfo
import io.github.xiaowu20245.nekopaint.data.repository.TaskManager
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.di.AppContainer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val maskSourceImages: List<HistoryImage> = emptyList(),
    val rawJsonVisible: Boolean = false,
    val rawJson: String? = null,
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

class WorkflowDetailViewModel(
    private val templateId: String,
    private val repository: WorkflowRepository,
    private val modelsRepository: ModelsRepository,
    private val taskManager: TaskManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowDetailUiState())
    val uiState: StateFlow<WorkflowDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val template = runCatching { repository.getTemplate(templateId) }.getOrNull()
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
            _uiState.update {
                it.copy(
                    loading = false,
                    template = template,
                    errorRes = if (template == null) R.string.workflow_error_generic else null,
                )
            }
            template?.let { loadModelOptions(it) }
            template?.takeIf {
                it.compatibility == null ||
                    !it.catalogChecked ||
                    it.compatibility.analyzerVersion != WorkflowCompatibilityAnalyzer.CURRENT_VERSION
            }?.let { refreshCompatibility(it) }
        }
        viewModelScope.launch {
            taskManager.history.collect { entries ->
                val images = entries
                    .flatMap { entry -> entry.images.filter { it.type == "output" } }
                    .take(60)
                _uiState.update { it.copy(maskSourceImages = images) }
            }
        }
        taskManager.refresh()
    }

    private fun refreshCompatibility(template: WorkflowTemplate) {
        viewModelScope.launch {
            val analyzed = repository.analyzeCompatibility(template) ?: return@launch
            _uiState.update { state ->
                val current = state.template ?: return@update state
                if (current.id != analyzed.id) return@update state
                state.copy(
                    template = current.copy(
                        missingNodeTypes = analyzed.missingNodeTypes,
                        catalogChecked = analyzed.catalogChecked,
                        compatibility = analyzed.compatibility,
                    ),
                )
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
     * 上传画布导出的蒙版，并写回当前蒙版槽。
     */
    fun uploadMask(candidateId: String, bitmap: Bitmap) {
        uploadToCandidate(candidateId) { repository.uploadMask(bitmap) }
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
        val updated = current.copy(
            candidates = nextCandidates,
            activePresetId = presetId,
        )
        _uiState.update {
            it.copy(
                template = updated,
                dirty = false,
                saved = false,
                fieldErrors = emptyMap(),
                submission = null,
                submitError = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.updateTemplate(updated) }
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
        val updated = current.copy(
            presets = current.presets + newPreset,
            activePresetId = newPreset.id,
        )
        viewModelScope.launch {
            val result = runCatching { repository.updateTemplate(updated) }
            _uiState.update {
                it.copy(
                    template = if (result.isSuccess) updated else it.template,
                    dirty = if (result.isSuccess) false else it.dirty,
                    presetNotice = if (result.isSuccess) PresetNotice.Saved(trimmed) else null,
                    errorRes = if (result.isFailure) R.string.workflow_error_generic else null,
                )
            }
        }
    }

    fun renameActivePreset(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value.template ?: return
        val activeId = current.activePresetId ?: return
        val updated = current.copy(
            presets = current.presets.map { preset ->
                if (preset.id == activeId) {
                    preset.copy(name = trimmed, updatedAt = System.currentTimeMillis())
                } else {
                    preset
                }
            },
        )
        viewModelScope.launch {
            if (runCatching { repository.updateTemplate(updated) }.isSuccess) {
                _uiState.update {
                    it.copy(
                        template = updated,
                        presetNotice = PresetNotice.Updated(trimmed),
                    )
                }
            }
        }
    }

    fun deleteActivePreset() {
        val current = _uiState.value.template ?: return
        val activeId = current.activePresetId ?: return
        val updated = current.copy(
            presets = current.presets.filterNot { it.id == activeId },
            activePresetId = null,
        )
        viewModelScope.launch {
            if (runCatching { repository.updateTemplate(updated) }.isSuccess) {
                _uiState.update {
                    it.copy(
                        template = updated,
                        presetNotice = PresetNotice.Deleted,
                    )
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
        val updated = current.copy(promptTagGroups = groups)
        _uiState.update { it.copy(template = updated, errorRes = null) }
        viewModelScope.launch {
            // 标签组管理立即落盘，但不能顺带提交尚未保存的提示词/参数编辑。
            val persisted = runCatching { repository.getTemplate(current.id) }.getOrNull()
                ?: current
            val toWrite = persisted.copy(promptTagGroups = groups)
            runCatching { repository.updateTemplate(toWrite) }
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
        val updated = current.copy(promptTagGroups = groups)
        _uiState.update { it.copy(template = updated, errorRes = null) }
        viewModelScope.launch {
            val persisted = runCatching { repository.getTemplate(current.id) }.getOrNull()
                ?: current
            val toWrite = persisted.copy(promptTagGroups = groups)
            runCatching { repository.updateTemplate(toWrite) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorRes = R.string.workflow_error_generic)
                    }
                }
        }
    }

    fun save() {
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
        }
    }

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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    submitting = true,
                    fieldErrors = emptyMap(),
                    submitError = null,
                    submission = null,
                )
            }

            var latestTemplate = template
            var lastSuccess: WorkflowSubmitResult.Success? = null
            var successCount = 0

            for (runIndex in 0 until totalRuns) {
                val runTemplate = WorkflowPresetMatcher.applySeedForRun(
                    template = template,
                    strategy = strategy,
                    runIndex = runIndex,
                )
                when (val result = repository.submit(runTemplate)) {
                    is WorkflowSubmitResult.Success -> {
                        latestTemplate = runTemplate
                        lastSuccess = result
                        successCount++
                    }

                    is WorkflowSubmitResult.Validation -> {
                        val fieldErrors = mutableMapOf<String, FieldError>()
                        template.selectedCandidates.forEach { candidate ->
                            val nodeError = result.nodeErrors.firstOrNull {
                                it.nodeId == candidate.nodeId
                            }
                            if (nodeError != null) {
                                fieldErrors[candidate.id] = FieldError.Server(nodeError.message)
                            }
                        }
                        _uiState.update {
                            it.copy(
                                template = latestTemplate,
                                submitting = false,
                                fieldErrors = fieldErrors,
                                submitError = SubmitErrorUi(
                                    message = result.summary,
                                    nodeErrors = result.nodeErrors,
                                ),
                            )
                        }
                        return@launch
                    }

                    WorkflowSubmitResult.NoServer -> {
                        _uiState.update {
                            it.copy(
                                template = latestTemplate,
                                submitting = false,
                                submitError = SubmitErrorUi(
                                    messageRes = R.string.workflow_submit_no_server,
                                ),
                            )
                        }
                        return@launch
                    }

                    is WorkflowSubmitResult.Error -> {
                        _uiState.update {
                            it.copy(
                                template = latestTemplate,
                                submitting = false,
                                submitError = SubmitErrorUi(message = result.message),
                            )
                        }
                        return@launch
                    }
                }
            }

            runCatching { repository.updateTemplate(latestTemplate) }

            _uiState.update {
                it.copy(
                    template = latestTemplate,
                    submitting = false,
                    submission = lastSuccess,
                    submittedBatchCount = successCount.coerceAtLeast(1),
                    dirty = false,
                    saved = true,
                )
            }
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
        fun factory(
            container: AppContainer,
            templateId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkflowDetailViewModel(
                    templateId = templateId,
                    repository = container.workflowRepository,
                    modelsRepository = container.modelsRepository,
                    taskManager = container.taskManager,
                )
            }
        }
    }
}
