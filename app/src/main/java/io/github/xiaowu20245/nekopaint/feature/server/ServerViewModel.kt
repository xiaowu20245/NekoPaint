package io.github.xiaowu20245.nekopaint.feature.server

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerEditPlan
import io.github.xiaowu20245.nekopaint.core.model.ServerEditPlanner
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.network.InvalidServerConfigException
import io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator
import io.github.xiaowu20245.nekopaint.data.repository.InvalidComfyResponseException
import io.github.xiaowu20245.nekopaint.data.repository.ServerAvatarStore
import io.github.xiaowu20245.nekopaint.data.repository.ServerRepository
import io.github.xiaowu20245.nekopaint.data.repository.StagedAvatar
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val probes = ServerProbeTracker()
    private val avatarMutex = Mutex()

    init {
        viewModelScope.launch {
            combine(repository.servers, repository.activeServerId) { servers, activeId ->
                servers to activeId
            }.collect { (servers, activeId) ->
                _uiState.update { state ->
                    val existing = state.cards.associateBy { it.profile.id }
                    val cards = servers.map { profile ->
                        mergeServerCard(
                            existing = existing[profile.id],
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
        refreshProfile(card.profile, silent)
    }

    /**
     * Probes the given profile directly instead of reading [_uiState.cards].
     *
     * This matters after saving: DataStore's `combine` may not have merged the
     * new profile into the UI state yet, and probing the old invalid profile
     * would leave `InvalidConfig` stuck on the card.
     */
    private fun refreshProfile(profile: ServerProfile, silent: Boolean) {
        val config = profile.toConfig()
        if (ServerEndpointValidator.parse(config).isFailure) {
            probes.invalidate(profile.id)
            _uiState.update { state ->
                state.copy(
                    cards = state.cards.map {
                        if (it.profile.id == profile.id) {
                            it.copy(
                                isTesting = false,
                                snapshot = null,
                                error = ServerError.InvalidConfig,
                                lastUpdatedAt = null,
                            )
                        } else {
                            it
                        }
                    },
                )
            }
            return
        }
        val generation = probes.start(profile.id, profile.endpointKey) ?: return
        val card = _uiState.value.cards.firstOrNull { it.profile.id == profile.id }
        // 静默刷新保留徽章与按钮状态；只有手动刷新或首次无数据时才显示“检测中”。
        val showTesting = !silent || card?.snapshot == null
        if (showTesting) {
            _uiState.update { state ->
                state.copy(
                    cards = state.cards.map {
                        if (it.profile.id == profile.id) {
                            it.copy(isTesting = true, error = null)
                        } else {
                            it
                        }
                    },
                )
            }
        }
        viewModelScope.launch {
            try {
                repository.check(config)
                .onSuccess { snapshot ->
                    if (!probes.isCurrent(profile.id, generation)) return@onSuccess
                    _uiState.update { state ->
                        state.copy(
                            cards = state.cards.map {
                                if (it.profile.id == profile.id) {
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
                    if (!probes.isCurrent(profile.id, generation)) return@onFailure
                    _uiState.update { state ->
                        state.copy(
                            cards = state.cards.map {
                                if (it.profile.id == profile.id) {
                                    it.copy(isTesting = false, error = throwable.toServerError())
                                } else {
                                    it
                                }
                            },
                        )
                    }
                }
            } finally {
                probes.finish(profile.id, profile.endpointKey)
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
                pendingEndpointChange = null,
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
                pendingEndpointChange = null,
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
                pendingEndpointChange = null,
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
            avatarMutex.withLock {
            val staged = avatarStore.stageBitmap(serverId, bitmap).getOrElse {
                markAvatarFailure(serverId)
                return@withLock
            }
            repository.saveServer(
                profile = profile.copy(avatarUri = staged.uri),
                activate = false,
            ).onSuccess {
                avatarStore.commitStaged(staged).onFailure {
                    markAvatarFailure(serverId)
                }
            }.onFailure { error ->
                val failure = if (error.message?.contains("缓存身份") == true) {
                    ServerError.CacheIdentityUnknown
                } else {
                    ServerError.AvatarFailed
                }
                avatarStore.rollbackStaged(staged).onFailure {
                    markAvatarFailure(serverId, failure)
                }
                markAvatarFailure(serverId, failure)
            }
            }
        }
    }

    private fun markAvatarFailure(
        serverId: String,
        error: ServerError = ServerError.AvatarFailed,
    ) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.profile.id == serverId) {
                        card.copy(error = error)
                    } else {
                        card
                    }
                },
            )
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
        val oldProfile = editingId?.let { id ->
            state.cards.firstOrNull { it.profile.id == id }?.profile
        }
        val baseProfile = ServerProfile.fromConfig(
            config = config,
            id = editingId ?: state.formTargetId ?: UUID.randomUUID().toString(),
        )
        val formTargetId = state.formTargetId
        val activate = editingId == null || state.activeCard?.profile?.id == editingId
        if (oldProfile != null && ServerEditPlanner.plan(oldProfile, config) == ServerEditPlan.NEW_ID) {
            _uiState.update {
                it.copy(
                    formError = null,
                    pendingEndpointChange = PendingEndpointChange(
                        oldProfile = oldProfile,
                        newProfile = baseProfile.copy(id = UUID.randomUUID().toString()),
                        activate = activate,
                        formTargetId = formTargetId,
                    ),
                )
            }
            return
        }
        commitProfile(
            oldProfile = oldProfile,
            baseProfile = baseProfile,
            activate = activate,
            formTargetId = formTargetId,
        )
    }

    fun confirmEndpointChange() {
        val pending = _uiState.value.pendingEndpointChange ?: return
        _uiState.update { it.copy(pendingEndpointChange = null) }
        commitProfile(
            oldProfile = pending.oldProfile,
            baseProfile = pending.newProfile,
            activate = pending.activate,
            formTargetId = pending.formTargetId,
        )
    }

    fun cancelEndpointChange() {
        if (_uiState.value.pendingEndpointChange == null) return
        _uiState.update { it.copy(pendingEndpointChange = null) }
        cancelForm()
    }

    private fun commitProfile(
        oldProfile: ServerProfile?,
        baseProfile: ServerProfile,
        activate: Boolean,
        formTargetId: String?,
    ) {
        val endpointChanged = oldProfile != null && oldProfile.id != baseProfile.id
        _uiState.update { it.copy(formSaving = true, formError = null) }
        viewModelScope.launch {
            avatarMutex.withLock {
            var stagedAvatar: StagedAvatar? = null
            runCatching {
                val formAvatar = baseProfile.avatarUri
                val isDraft = formAvatar != null && avatarStore.isDraft(formAvatar)
                val staged = if (formAvatar != null && (endpointChanged || isDraft)) {
                    avatarStore.stageForSave(baseProfile.id, formAvatar, isDraft)
                        .getOrElse { error ->
                            throw AvatarPreparationException(
                                "无法准备服务器头像",
                                error,
                            )
                        }
                } else {
                    null
                }
                stagedAvatar = staged
                val resolvedAvatar = staged?.uri ?: formAvatar
                val profile = baseProfile.copy(avatarUri = resolvedAvatar)
                if (endpointChanged) {
                    repository.replaceServerEndpoint(
                        oldProfile = requireNotNull(oldProfile),
                        newProfile = profile,
                        activate = activate,
                    ).getOrThrow()
                } else {
                    repository.saveServer(profile, activate = activate).getOrThrow()
                }
                profile
            }.onSuccess { profile ->
                // Config is durable now; avatar cleanup stays inside the same
                // mutex so a following setAvatar cannot race with old deletes.
                val cleanupFailed = stagedAvatar?.let {
                    avatarStore.commitStaged(it).isFailure
                } ?: false
                if (profile.avatarUri == null) {
                    avatarStore.delete(profile.id)
                }
                if (endpointChanged) {
                    avatarStore.delete(oldProfile.id)
                }
                _uiState.update { state ->
                    val cardsWithoutOld = if (endpointChanged) {
                        state.cards.filterNot { it.profile.id == oldProfile.id }
                    } else {
                        state.cards
                    }
                    val existingCard = cardsWithoutOld.firstOrNull { it.profile.id == profile.id }
                    val updatedCard = (existingCard ?: ServerCardUi(
                        profile = profile,
                        isActive = activate,
                    )).copy(
                        profile = profile,
                        isActive = existingCard?.isActive == true || activate,
                        isTesting = false,
                        snapshot = null,
                        error = null,
                        lastUpdatedAt = null,
                    )
                    val cards = if (existingCard == null) {
                        cardsWithoutOld + updatedCard
                    } else {
                        cardsWithoutOld.map { card ->
                            if (card.profile.id == profile.id) updatedCard else card
                        }
                    }
                    state.copy(
                        cards = cards,
                        formSaving = false,
                        formMode = ServerFormMode.None,
                        editingId = null,
                        formTargetId = null,
                        formSnapshot = null,
                        formError = if (cleanupFailed) {
                            ServerError.AvatarFailed
                        } else {
                            null
                        },
                        pendingEndpointChange = null,
                        expandedId = profile.id,
                    )
                }
                // DataStore/combine may not have merged this profile yet. Probe
                // the newly saved endpoint directly so a corrected config can
                // recover without waiting for another UI event.
                refreshProfile(profile, silent = true)
            }.onFailure { error ->
                stagedAvatar?.let { staged ->
                    avatarStore.rollbackStaged(staged).onFailure {
                        _uiState.update { state ->
                            state.copy(formError = ServerError.AvatarFailed)
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        formSaving = false,
                        formError = when {
                            error is AvatarPreparationException -> ServerError.AvatarFailed
                            error.message?.contains("缓存身份") == true ->
                                ServerError.CacheIdentityUnknown
                            else -> ServerError.Unknown
                        },
                        pendingEndpointChange = null,
                    )
                }
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
            avatarMutex.withLock {
                repository.deleteServer(profile.id)
                avatarStore.delete(profile.id)
            }
            _uiState.update {
                if (it.expandedId == profile.id) it.copy(expandedId = null) else it
            }
        }
    }

    private fun validateForm(): ServerConfig? {
        val form = _uiState.value.form
        val host = form.host.trim()
        val port = form.port.trim().toIntOrNull()
        val validPort = port != null && port in 1..65535
        val hostCheck = if (host.isNotEmpty()) {
            ServerEndpointValidator.parse(
                scheme = form.scheme,
                rawHost = host,
                port = port?.takeIf { it in 1..65535 } ?: ServerConfig.DEFAULT_PORT,
            )
        } else {
            null
        }

        val hostError = when {
            host.isEmpty() -> ServerError.EmptyHost
            hostCheck?.isFailure == true -> ServerError.InvalidHost
            else -> null
        }
        val portError = if (!validPort) {
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

        if (hostError != null || portError != null) {
            return null
        }

        val endpoint = ServerEndpointValidator.parse(form.scheme, host, port!!)
            .getOrNull()
            ?: run {
                _uiState.update {
                    it.copy(form = it.form.copy(hostError = ServerError.InvalidHost))
                }
                return null
            }
        return ServerConfig(
            name = form.name.trim().ifEmpty { ServerConfig.DEFAULT_NAME },
            scheme = endpoint.scheme,
            host = endpoint.displayHost,
            port = endpoint.port,
            avatarUri = form.avatarUri,
        )
    }

    private fun updateForm(transform: (ServerForm) -> ServerForm) {
        _uiState.update {
            it.copy(form = transform(it.form), formError = null, formSnapshot = null)
        }
    }

    private fun Throwable.toServerError(): ServerError = when (this) {
        is InvalidServerConfigException -> ServerError.InvalidConfig

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

private class AvatarPreparationException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
