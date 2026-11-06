package io.github.xiaowu20245.nekopaint.feature.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.data.repository.InvalidComfyResponseException
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

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
        if (!card.isTesting) refreshCard(card.profile.id)
    }

    fun refreshCard(id: String) {
        val card = _uiState.value.cards.firstOrNull { it.profile.id == id } ?: return
        if (card.isTesting) return
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map {
                    if (it.profile.id == id) it.copy(isTesting = true, error = null) else it
                },
            )
        }
        viewModelScope.launch {
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
        }
    }

    fun toggleExpanded(id: String) {
        val card = _uiState.value.cards.firstOrNull { it.profile.id == id } ?: return
        val willExpand = _uiState.value.expandedId != id
        _uiState.update { it.copy(expandedId = if (willExpand) id else null) }
        if (willExpand && !card.isTesting) {
            refreshCard(id)
        }
    }

    fun startAdd() {
        _uiState.update {
            it.copy(
                formMode = ServerFormMode.Add,
                editingId = null,
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
                form = ServerForm.fromProfile(profile),
                formTesting = false,
                formSaving = false,
                formSnapshot = null,
                formError = null,
            )
        }
    }

    fun cancelForm() {
        _uiState.update {
            it.copy(
                formMode = ServerFormMode.None,
                editingId = null,
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
        val profile = ServerProfile.fromConfig(
            config = config,
            id = editingId ?: UUID.randomUUID().toString(),
        )
        val activate = editingId == null || state.activeCard?.profile?.id == profile.id
        viewModelScope.launch {
            _uiState.update { it.copy(formSaving = true, formError = null) }
            runCatching {
                repository.saveServer(profile, activate = activate)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        formSaving = false,
                        formMode = ServerFormMode.None,
                        editingId = null,
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
            initializer { ServerViewModel(container.serverRepository) }
        }
    }
}
