package io.github.xiaowu20245.nekopaint.feature.server

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.data.repository.InvalidComfyResponseException
import io.github.xiaowu20245.nekopaint.data.repository.ServerAvatarStore
import io.github.xiaowu20245.nekopaint.data.repository.ServerRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class ServerViewModel(
    private val repository: ServerRepository,
    private val avatarStore: ServerAvatarStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()
    private val refreshingIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(repository.servers, repository.activeServerId) { servers, activeId ->
                servers to activeId
            }.collect { (servers, activeId) ->
                _uiState.update { state ->
                    val existing = state.cards.associateBy { it.profile.id }
                    val cards = servers.map { profile ->
                        existing[profile.id]?.copy(
                            profile = profile,
                            isActive = profile.id == activeId,
                        ) ?: ServerCardUi(
                            profile = profile,
                            isActive = profile.id == activeId,
                        )
                    }
                    state.copy(
                        isLoading = false,
                        cards = cards,
                        expandedId = state.expandedId
                            ?.takeIf { id -> cards.any { it.profile.id == id } },
                    )
                }
            }
        }
    }

    fun refreshActive() {
        val card = _uiState.value.activeCard ?: return
        if (!card.isTesting) refreshCard(card.profile.id, silent = true)
    }

    fun refreshCard(id: String, silent: Boolean = false) {
        val card = _uiState.value.cards.firstOrNull { it.profile.id == id } ?: return
        if (card.isTesting || !refreshingIds.add(id)) return
        // 静默刷新保留徽章与按钮状态；只有手动刷新或首次无数据时才显示“检测中”。
        val showTesting = !silent || card.snapshot == null
        if (showTesting) {
            _uiState.update { state ->
                state.copy(
                    cards = state.cards.map {
                        if (it.profile.id == id) it.copy(isTesting = true, error = null) else it
                    },
                )
            }
        }
        viewModelScope.launch {
            try {
                repository.check(card.profile.toConfig())
                .onSuccess { snapshot ->
                    _uiState.update { state ->
                        state.copy(
                            cards = state.cards.map {
                                if (it.profile.id == id) {
                                    it.copy(
                                        isTesting = false,
                                        snapshot = snapshot,
                                        error = null,
                                        lastUpdatedAt = snapshot.checkedAt,
                                    )
                                } else {
                                    it
                                }
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            cards = state.cards.map {
                                if (it.profile.id == id) {
                                    it.copy(isTesting = false, error = throwable.toServerError())
                                } else {
                                    it
                                }
                            },
                        )
                    }
                }
            } finally {
                refreshingIds.remove(id)
            }
        }
    }

    fun toggleExpanded(id: String) {
        val card = _uiState.value.cards.firstOrNull { it.profile.id == id } ?: return
        val willExpand = _uiState.value.expandedId != id
        _uiState.update { it.copy(expandedId = if (willExpand) id else null) }
        if (willExpand && !card.isTesting) {
            refreshCard(id, silent = card.snapshot != null)
        }
    }

    fun startAdd() {
        _uiState.update {
            it.copy(
                formMode = ServerFormMode.Add,
                editingId = null,
                formTargetId = UUID.randomUUID().toString(),
                form = ServerForm(),
                formTesting = false,
                formSaving = false,
                formSnapshot = null,
                formError = null,
            )
        }
    }

    fun startEdit(profile: ServerProfile) {
        _uiState.update {
            it.copy(
                formMode = ServerFormMode.Edit,
                editingId = profile.id,
                formTargetId = profile.id,
                form = ServerForm.fromProfile(profile),
                formTesting = false,
                formSaving = false,
                formSnapshot = null,
                formError = null,
            )
        }
    }

    fun cancelForm() {
        if (_uiState.value.formSaving) return
        val targetId = _uiState.value.formTargetId
        if (targetId != null) {
            viewModelScope.launch { avatarStore.deleteDraft(targetId) }
        }
        _uiState.update {
            it.copy(
                formMode = ServerFormMode.None,
                editingId = null,
                formTargetId = null,
                formTesting = false,
                formSaving = false,
                formSnapshot = null,
                formError = null,
            )
        }
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value) }

    fun onHostChange(value: String) = updateForm {
        it.copy(host = value, hostError = null)
    }

    fun onPortChange(value: String) = updateForm {
        it.copy(port = value.filter { char -> char.isDigit() }, portError = null)
    }

    fun onSchemeChange(value: String) = updateForm {
        it.copy(scheme = if (value == "https") "https" else "http")
    }

    fun setFormAvatar(bitmap: Bitmap) {
        val targetId = _uiState.value.formTargetId ?: return
        viewModelScope.launch {
            avatarStore.saveDraft(targetId, bitmap)
                .onSuccess { path ->
                    updateForm { it.copy(avatarUri = path) }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(formError = ServerError.AvatarFailed)
                    }
                }
        }
    }

    fun clearFormAvatar() {
        val state = _uiState.value
        val shouldDeleteDraft = avatarStore.isDraft(state.form.avatarUri)
        val targetId = state.formTargetId
        updateForm { it.copy(avatarUri = null) }
        if (shouldDeleteDraft && targetId != null) {
            viewModelScope.launch { avatarStore.deleteDraft(targetId) }
        }
    }

    fun setAvatar(serverId: String, bitmap: Bitmap) {
        val profile = _uiState.value.cards
            .firstOrNull { it.profile.id == serverId }
            ?.profile
            ?: return
        viewModelScope.launch {
            avatarStore.saveBitmap(serverId, bitmap)
                .onSuccess { path ->
                    repository.saveServer(
                        profile = profile.copy(avatarUri = path),
                        activate = false,
                    )
                }
        }
    }

    fun testFormConnection() {
        val config = validateForm() ?: return
        if (_uiState.value.formTesting) return
        _uiState.update { it.copy(formTesting = true, formError = null, formSnapshot = null) }
        viewModelScope.launch {
            repository.check(config)
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(formTesting = false, formSnapshot = snapshot, formError = null)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            formTesting = false,
                            formSnapshot = null,
                            formError = throwable.toServerError(),
                        )
                    }
                }
        }
    }

    fun saveAndConnect() {
        val config = validateForm() ?: return
        if (_uiState.value.formSaving) return
        val state = _uiState.value
        val editingId = state.editingId
        val baseProfile = ServerProfile.fromConfig(
            config = config,
            id = editingId ?: state.formTargetId ?: UUID.randomUUID().toString(),
        )
        val originalAvatar = state.cards
            .firstOrNull { it.profile.id == baseProfile.id }
            ?.profile
            ?.avatarUri
        val formTargetId = state.formTargetId
        val activate = editingId == null || state.activeCard?.profile?.id == baseProfile.id
        _uiState.update { it.copy(formSaving = true, formError = null) }
        viewModelScope.launch {
            runCatching {
                val formAvatar = config.avatarUri
                val resolvedAvatar = when {
                    formAvatar == null -> null
                    avatarStore.isDraft(formAvatar) -> {
                        avatarStore.commitDraft(baseProfile.id, formAvatar).getOrElse {
                            avatarStore.deleteDraft(formTargetId ?: baseProfile.id)
                            originalAvatar
                        }
                    }

                    else -> formAvatar
                }
                val profile = baseProfile.copy(avatarUri = resolvedAvatar)
                repository.saveServer(profile, activate = activate)
                if (resolvedAvatar == null) {
                    avatarStore.delete(baseProfile.id)
                }
                profile
            }.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        formSaving = false,
                        formMode = ServerFormMode.None,
                        editingId = null,
                        formTargetId = null,
                        formSnapshot = null,
                        formError = null,
                        expandedId = profile.id,
                    )
                }
                refreshCard(profile.id)
            }.onFailure {
                _uiState.update {
                    it.copy(formSaving = false, formError = ServerError.Unknown)
                }
            }
        }
    }

    fun setActive(profile: ServerProfile) {
        viewModelScope.launch {
            repository.setActive(profile.id)
            refreshCard(profile.id)
        }
    }

    fun delete(profile: ServerProfile) {
        viewModelScope.launch {
            repository.deleteServer(profile.id)
            avatarStore.delete(profile.id)
            _uiState.update {
                if (it.expandedId == profile.id) it.copy(expandedId = null) else it
            }
        }
    }

    private fun validateForm(): ServerConfig? {
        val form = _uiState.value.form
        val host = form.host.trim()
        val port = form.port.trim().toIntOrNull()

        val hostError = when {
            host.isEmpty() -> ServerError.EmptyHost
            host.any { it.isWhitespace() } || host.contains('/') || host.contains("://") -> {
                ServerError.InvalidHost
            }

            host.count { it == ':' } == 1 && !host.startsWith("[") -> ServerError.InvalidHost
            else -> null
        }
        val portError = if (port == null || port !in 1..65535) {
            ServerError.InvalidPort
        } else {
            null
        }

        _uiState.update {
            it.copy(
                form = form.copy(hostError = hostError, portError = portError),
                formError = null,
            )
        }

        if (hostError != null || portError != null || port == null) {
            return null
        }

        return ServerConfig(
            name = form.name.trim().ifEmpty { ServerConfig.DEFAULT_NAME },
            scheme = form.scheme,
            host = host,
            port = port,
            avatarUri = form.avatarUri,
        )
    }

    private fun updateForm(transform: (ServerForm) -> ServerForm) {
        _uiState.update {
            it.copy(form = transform(it.form), formError = null, formSnapshot = null)
        }
    }

    private fun Throwable.toServerError(): ServerError = when (this) {
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException,
        -> ServerError.Unreachable

        is SocketTimeoutException -> ServerError.Timeout
        is HttpException -> ServerError.Http
        is SerializationException -> ServerError.Parse
        is InvalidComfyResponseException -> ServerError.InvalidResponse
        else -> ServerError.Unknown
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ServerViewModel(
                    repository = container.serverRepository,
                    avatarStore = container.serverAvatarStore,
                )
            }
        }
    }
}
