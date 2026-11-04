package io.github.xiaowu20245.nekopaint.feature.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.data.repository.InvalidComfyResponseException
import io.github.xiaowu20245.nekopaint.data.repository.ServerRepository
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ServerViewModel(
    private val repository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.loadConfig() }
                .onSuccess { config ->
                    if (config == null) {
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                config = config,
                                form = ServerForm.fromConfig(config),
                                isEditing = false,
                            )
                        }
                        check(config)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, error = ServerError.Unknown)
                    }
                }
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

    fun edit() {
        val config = _uiState.value.config ?: return
        _uiState.update {
            it.copy(
                form = ServerForm.fromConfig(config),
                isEditing = true,
                error = null,
            )
        }
    }

    fun cancelEdit() {
        val config = _uiState.value.config
        _uiState.update {
            if (config == null) {
                it.copy(isEditing = true, error = null)
            } else {
                it.copy(
                    form = ServerForm.fromConfig(config),
                    isEditing = false,
                    error = null,
                )
            }
        }
    }

    fun testConnection() {
        val config = validateForm() ?: return
        check(config)
    }

    fun saveAndConnect() {
        val config = validateForm() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val saved = runCatching { repository.saveConfig(config) }
            if (saved.isFailure) {
                _uiState.update {
                    it.copy(isSaving = false, error = ServerError.Unknown)
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    config = config,
                    form = ServerForm.fromConfig(config),
                    isEditing = false,
                    error = null,
                )
            }
            check(config)
        }
    }

    fun refresh() {
        val config = _uiState.value.config ?: return
        check(config)
    }

    fun refreshIfConfigured() {
        val state = _uiState.value
        val config = state.config ?: return
        if (!state.isEditing && !state.isBusy) {
            check(config)
        }
    }

    private fun check(config: ServerConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, error = null) }
            repository.check(config)
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            snapshot = snapshot,
                            error = null,
                            lastUpdatedAt = snapshot.checkedAt,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            error = throwable.toServerError(),
                        )
                    }
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
                error = null,
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
        _uiState.update { it.copy(form = transform(it.form), error = null) }
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
