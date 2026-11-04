package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.NodeErrorInfo
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val fieldErrors: Map<String, FieldError> = emptyMap(),
    val submitError: SubmitErrorUi? = null,
    val submission: WorkflowSubmitResult.Success? = null,
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

    fun save() {
        val template = _uiState.value.template ?: return
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorRes = null) }
            val result = runCatching { repository.updateTemplate(template) }
            _uiState.update {
                it.copy(
                    saving = false,
                    dirty = result.isFailure,
                    saved = result.isSuccess,
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    submitting = true,
                    fieldErrors = emptyMap(),
                    submitError = null,
                    submission = null,
                )
            }
            runCatching { repository.updateTemplate(template) }

            when (val result = repository.submit(template)) {
                is WorkflowSubmitResult.Success -> {
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submission = result,
                            dirty = false,
                            saved = true,
                        )
                    }
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
                            submitting = false,
                            fieldErrors = fieldErrors,
                            submitError = SubmitErrorUi(
                                message = result.summary,
                                nodeErrors = result.nodeErrors,
                            ),
                        )
                    }
                }

                WorkflowSubmitResult.NoServer -> {
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submitError = SubmitErrorUi(
                                messageRes = R.string.workflow_submit_no_server,
                            ),
                        )
                    }
                }

                is WorkflowSubmitResult.Error -> {
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submitError = SubmitErrorUi(message = result.message),
                        )
                    }
                }
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
                )
            }
        }
    }
}
