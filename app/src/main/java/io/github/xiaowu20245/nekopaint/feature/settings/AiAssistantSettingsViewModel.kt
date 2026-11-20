package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.common.toNekoMessage
import io.github.xiaowu20245.nekopaint.core.datastore.AiAssistantSettingsStore
import io.github.xiaowu20245.nekopaint.core.model.ai.AiAssistantConfig
import io.github.xiaowu20245.nekopaint.core.model.ai.AiDisclosurePolicy
import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent
import io.github.xiaowu20245.nekopaint.core.network.AiAssistantSettingsClient
import io.github.xiaowu20245.nekopaint.core.network.AiCredentialPolicy
import io.github.xiaowu20245.nekopaint.core.network.AiCredentialState
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointPolicy
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointPolicyResult
import io.github.xiaowu20245.nekopaint.core.network.AiEndpointValidator
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAssistantFormState(
    val providerId: String = AiProviderPreset.DeepSeek.id,
    val baseUrl: String = "",
    val apiKey: String = "",
    val apiKeyOrigin: String? = null,
    val textModel: String = "",
    val visionModel: String = "",
    val keyVisible: Boolean = false,
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testLatencyMs: Long? = null,
    val testError: NekoMessage? = null,
    val error: NekoMessage? = null,
    val baseUrlError: NekoMessage? = null,
    val credentialMessage: String? = null,
    val policyMessage: String? = null,
    val policyBlocked: Boolean = false,
    val disclosureAcknowledged: Boolean = false,
    val confirmationMessage: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
    val dirty: Boolean = false,
) {
    val canSubmit: Boolean
        get() = baseUrl.isNotBlank() &&
            textModel.isNotBlank() &&
            baseUrlError == null &&
            !policyBlocked

    fun toConfig(): AiAssistantConfig = AiAssistantConfig(
        providerId = providerId,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        apiKeyOrigin = apiKeyOrigin,
        textModel = textModel.trim(),
        visionModel = visionModel.trim(),
    )
}

class AiAssistantSettingsViewModel(
    private val store: AiAssistantSettingsStore,
    private val client: AiAssistantSettingsClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantFormState())
    val uiState: StateFlow<AiAssistantFormState> = _uiState.asStateFlow()

    private var consent = AiHttpConsent()
    private var pendingAction: (() -> Unit)? = null
    private var originalBaseUrlValid = true
    private var originalApiKey = ""
    private var keyEdited = false
    private var disclosureAcknowledgements: Set<String> = emptySet()
    private var serviceBaseline: ServiceBaseline? = null

    /**
     * 服务字段 + 凭据归属的完整基线。apiKeyOrigin 不参与 dirty 比较，
     * 但“放弃修改”必须恢复它，否则旧 Key 会和新地址混合。
     */
    private data class ServiceBaseline(
        val draft: AiServiceDraft,
        val apiKeyOrigin: String?,
    )

    init {
        viewModelScope.launch {
            val config = store.current()
            originalBaseUrlValid = AiEndpointValidator.parse(config.baseUrl).isSuccess
            originalApiKey = config.apiKey
            keyEdited = false
            consent = runCatching { store.currentHttpConsent() }
                .getOrDefault(AiHttpConsent())
            disclosureAcknowledgements = runCatching {
                store.currentDisclosureAcknowledgements()
            }.getOrDefault(emptySet())
            _uiState.update { current ->
                val loaded = current.copy(
                    providerId = config.providerId.ifBlank {
                        AiProviderPreset.DeepSeek.id
                    },
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    apiKeyOrigin = config.apiKeyOrigin,
                    textModel = config.textModel,
                    visionModel = config.visionModel,
                    loaded = true,
                )
                serviceBaseline = ServiceBaseline(
                    draft = AiAssistantSettingsPolicy.serviceDraft(loaded),
                    apiKeyOrigin = loaded.apiKeyOrigin,
                )
                withDerived(
                    if (config.apiKeyInvalid) {
                        loaded.copy(error = NekoMessage(NekoMessageKey.KEY_INVALID))
                    } else {
                        loaded
                    },
                )
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        updateForm {
            it.copy(
                baseUrl = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun onApiKeyChange(value: String) {
        val endpoint = AiEndpointValidator.parse(_uiState.value.baseUrl).getOrNull()
        keyEdited = value != originalApiKey
        updateForm {
            it.copy(
                apiKey = value,
                apiKeyOrigin = AiAssistantSettingsPolicy.apiKeyOriginForNewKey(value, endpoint),
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun onTextModelChange(value: String) {
        updateForm {
            it.copy(
                textModel = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun onVisionModelChange(value: String) {
        updateForm {
            it.copy(
                visionModel = value,
                providerId = AiAssistantConfig.PROVIDER_CUSTOM,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    fun toggleKeyVisible() {
        _uiState.update { it.copy(keyVisible = !it.keyVisible) }
    }

    fun acknowledgeDisclosure() {
        val endpoint = AiEndpointValidator.parse(_uiState.value.baseUrl).getOrNull()
        if (endpoint == null) {
            _uiState.update {
                it.copy(error = NekoMessage(NekoMessageKey.SETTINGS_AI_BASE_URL_REQUIRED))
            }
            return
        }
        viewModelScope.launch {
            runCatching { store.acknowledgeDisclosure(endpoint.origin) }
                .onSuccess {
                    disclosureAcknowledgements = disclosureAcknowledgements +
                        AiDisclosurePolicy.key(endpoint.origin)
                    _uiState.update { withDerived(it) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            error = error.toSettingsMessage(
                                NekoMessageKey.SETTINGS_DISCLOSURE_SAVE_FAILED,
                            ),
                        )
                    }
                }
        }
    }

    fun requestClearConfig() {
        pendingAction = {
            viewModelScope.launch {
                runCatching { store.clear() }
                    .onSuccess {
                        disclosureAcknowledgements = emptySet()
                        consent = AiHttpConsent()
                        originalBaseUrlValid = true
                        originalApiKey = ""
                        keyEdited = false
                        _uiState.update {
                            val cleared = AiAssistantFormState(loaded = true)
                            serviceBaseline = ServiceBaseline(
                                draft = AiAssistantSettingsPolicy.serviceDraft(cleared),
                                apiKeyOrigin = cleared.apiKeyOrigin,
                            )
                            withDerived(cleared)
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                error = error.toSettingsMessage(
                                    NekoMessageKey.SETTINGS_AI_CLEAR_FAILED,
                                ),
                            )
                        }
                    }
            }
        }
        _uiState.update {
            it.copy(confirmationMessage = "将清除 AI Base URL、API Key、人设和所有安全确认，是否继续？")
        }
    }

    fun applyPreset(preset: AiProviderPreset) {
        val state = _uiState.value
        val presetEndpoint = AiEndpointValidator.parse(preset.baseUrl).getOrNull()
        val currentEndpoint = AiEndpointValidator.parse(state.baseUrl).getOrNull()
        val shouldClearKey = AiAssistantSettingsPolicy.presetRequiresKeyClear(
            currentEndpoint = currentEndpoint,
            presetEndpoint = presetEndpoint,
            apiKey = state.apiKey,
            apiKeyOrigin = state.apiKeyOrigin,
        )
        if (shouldClearKey) {
            pendingAction = { applyPresetNow(preset, clearKey = true) }
            _uiState.update {
                it.copy(
                    confirmationMessage = if (state.apiKeyOrigin.isNullOrBlank()) {
                        "当前 API Key 尚未绑定地址，切换服务商将清空 Key，是否继续？"
                    } else {
                        "服务商地址已改变，当前 API Key 将被清空，是否继续？"
                    },
                )
            }
            return
        }
        applyPresetNow(preset, clearKey = false)
    }

    fun testConnection() {
        prepareRequest(test = true)
    }

    fun save() {
        prepareRequest(test = false)
    }

    fun confirmPendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        _uiState.update { it.copy(confirmationMessage = null) }
        action()
    }

    fun cancelPendingAction() {
        pendingAction = null
        _uiState.update { it.copy(confirmationMessage = null) }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    /** 返回门禁选择“放弃修改”时恢复已保存的服务字段与 Key 归属。 */
    fun discardChanges() {
        val baseline = serviceBaseline ?: return
        keyEdited = false
        updateForm {
            it.copy(
                providerId = baseline.draft.providerId,
                baseUrl = baseline.draft.baseUrl,
                apiKey = baseline.draft.apiKey,
                apiKeyOrigin = baseline.apiKeyOrigin,
                textModel = baseline.draft.textModel,
                visionModel = baseline.draft.visionModel,
                testLatencyMs = null,
                testError = null,
                error = null,
            )
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun applyPresetNow(preset: AiProviderPreset, clearKey: Boolean) {
        updateForm {
            it.copy(
                providerId = preset.id,
                baseUrl = preset.baseUrl,
                textModel = preset.textModel,
                visionModel = preset.visionModel,
                apiKey = if (clearKey) "" else it.apiKey,
                apiKeyOrigin = if (clearKey) null else it.apiKeyOrigin,
                testLatencyMs = null,
                testError = null,
                saved = false,
            )
        }
    }

    private fun prepareRequest(test: Boolean) {
        val state = _uiState.value
        if (state.testing || state.saving) return
        val endpoint = AiEndpointValidator.parse(state.baseUrl).getOrNull()
        if (!state.canSubmit || endpoint == null) {
            _uiState.update {
                withDerived(
                    it.copy(
                        error = when {
                            it.policyBlocked -> NekoMessage(
                                NekoMessageKey.SETTINGS_AI_BLOCKED,
                                listOf(it.policyMessage ?: "blocked"),
                            )
                            it.baseUrl.isBlank() -> NekoMessage(
                                NekoMessageKey.SETTINGS_AI_BASE_URL_REQUIRED,
                            )
                            else -> NekoMessage(
                                NekoMessageKey.SETTINGS_AI_BASE_URL_INVALID,
                            )
                        },
                    ),
                )
            }
            return
        }

        when (val credential = AiCredentialPolicy.evaluate(
            apiKey = state.apiKey,
            apiKeyOrigin = state.apiKeyOrigin,
            endpoint = endpoint,
        )) {
            AiCredentialState.NoKey,
            AiCredentialState.Bound,
            -> Unit

            AiCredentialState.MissingOrigin -> {
                if (!AiAssistantSettingsPolicy.canBindLegacyKey(
                        originalBaseUrlValid = originalBaseUrlValid,
                        keyEdited = keyEdited,
                    )
                ) {
                    pendingAction = {
                        val cleared = _uiState.value.copy(apiKey = "", apiKeyOrigin = null)
                        _uiState.update { withDerived(cleared) }
                        if (test) {
                            _uiState.update {
                                it.copy(
                                    error = NekoMessage(NekoMessageKey.SETTINGS_AI_OLD_KEY_CLEARED),
                                )
                            }
                        } else {
                            prepareRequest(false)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            confirmationMessage =
                                "旧配置的 Base URL 无效，无法确认旧 Key 归属。继续将清空旧 Key。",
                        )
                    }
                    return
                }
                pendingAction = {
                    val bound = _uiState.value.copy(apiKeyOrigin = endpoint.origin)
                    _uiState.update { withDerived(bound) }
                    prepareRequest(test)
                }
                _uiState.update {
                    it.copy(
                        confirmationMessage =
                            "此 API Key 尚未绑定当前地址。确认将其绑定到 ${endpoint.origin} 吗？",
                    )
                }
                return
            }

            is AiCredentialState.OriginChanged -> {
                pendingAction = {
                    val cleared = _uiState.value.copy(apiKey = "", apiKeyOrigin = null)
                    _uiState.update { withDerived(cleared) }
                    if (test) {
                        _uiState.update {
                            it.copy(
                                error = NekoMessage(NekoMessageKey.SETTINGS_AI_KEY_CLEARED),
                            )
                        }
                    } else {
                        prepareRequest(false)
                    }
                }
                _uiState.update {
                    it.copy(
                        confirmationMessage =
                            "当前 API Key 属于 ${credential.boundOrigin}，与当前地址不一致。继续将清空 Key。",
                    )
                }
                return
            }

            AiCredentialState.InvalidEndpoint -> {
                _uiState.update {
                    it.copy(error = NekoMessage(NekoMessageKey.SETTINGS_AI_BASE_URL_INVALID))
                }
                return
            }
        }

        when (val policy = AiEndpointPolicy.evaluate(
            endpoint = endpoint,
            hasApiKey = state.apiKey.isNotBlank(),
            consent = consent,
        )) {
            is AiEndpointPolicyResult.Blocked -> {
                _uiState.update {
                    it.copy(
                        error = NekoMessage(
                            NekoMessageKey.SETTINGS_AI_BLOCKED,
                            listOf(policy.message),
                        ),
                    )
                }
            }

            is AiEndpointPolicyResult.NeedsLocalTrust,
            is AiEndpointPolicyResult.NeedsKeyApproval,
            -> {
                val needsTrust = policy.requiresLocalTrust
                val needsKeyApproval = policy.requiresKeyApproval
                pendingAction = {
                    viewModelScope.launch {
                        val updatedConsent = runCatching {
                            if (needsTrust) store.trustHttpOrigin(endpoint.origin)
                            if (needsKeyApproval) store.approveHttpKeyOrigin(endpoint.origin)
                            store.currentHttpConsent()
                        }
                        updatedConsent.onSuccess { value ->
                            consent = value
                            _uiState.update { withDerived(it) }
                            prepareRequest(test)
                        }.onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    error = error.toSettingsMessage(
                                        NekoMessageKey.SETTINGS_HTTP_CONFIRM_SAVE_FAILED,
                                    ),
                                )
                            }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        confirmationMessage = policy.message
                            ?: "此 HTTP 地址需要确认后才能继续。",
                    )
                }
            }

            is AiEndpointPolicyResult.Allowed -> {
                if (test && !AiDisclosurePolicy.isAcknowledged(
                        endpoint.origin,
                        disclosureAcknowledgements,
                    )
                ) {
                    pendingAction = {
                        viewModelScope.launch {
                            runCatching { store.acknowledgeDisclosure(endpoint.origin) }
                                .onSuccess {
                                    disclosureAcknowledgements = disclosureAcknowledgements +
                                        AiDisclosurePolicy.key(endpoint.origin)
                                    _uiState.update { withDerived(it) }
                                    prepareRequest(true)
                                }
                                .onFailure { error ->
                                    _uiState.update {
                                        it.copy(
                                            error = error.toSettingsMessage(
                                                NekoMessageKey.SETTINGS_DISCLOSURE_SAVE_FAILED,
                                            ),
                                        )
                                    }
                                }
                        }
                    }
                    _uiState.update {
                        it.copy(
                            confirmationMessage =
                                "测试连接会把提示词、所选择的图片、任务摘要和错误信息发送到第三方 AI 服务。是否继续并记录此地址的确认？",
                        )
                    }
                } else {
                    runRequest(test, state)
                }
            }
        }
    }

    private fun runRequest(test: Boolean, state: AiAssistantFormState) {
        if (test) {
            runTest(state)
        } else {
            runSave(state)
        }
    }

    private fun runTest(state: AiAssistantFormState) {
        if (_uiState.value.testing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    testing = true,
                    testLatencyMs = null,
                    testError = null,
                    error = null,
                )
            }
            val result = client.testConnection(state.toConfig())
            _uiState.update {
                withDerived(
                    it.copy(
                        testing = false,
                        testLatencyMs = result.getOrNull(),
                        testError = result.exceptionOrNull().toSettingsMessage(
                            NekoMessageKey.SETTINGS_AI_TEST_FAILED,
                        ),
                    ),
                )
            }
        }
    }

    private fun runSave(state: AiAssistantFormState) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, saved = false) }
            store.updateServiceConfig(state.toConfig())
                .onSuccess {
                    val savedConfig = state.toConfig()
                    originalBaseUrlValid = AiEndpointValidator.parse(savedConfig.baseUrl).isSuccess
                    originalApiKey = savedConfig.apiKey
                    keyEdited = false
                    _uiState.update {
                        val normalized = it.copy(
                            baseUrl = savedConfig.baseUrl,
                            apiKey = savedConfig.apiKey,
                            textModel = savedConfig.textModel,
                            visionModel = savedConfig.visionModel,
                            saving = false,
                            saved = true,
                        )
                        serviceBaseline = ServiceBaseline(
                            draft = AiAssistantSettingsPolicy.serviceDraft(normalized),
                            apiKeyOrigin = normalized.apiKeyOrigin,
                        )
                        withDerived(normalized)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = throwable.toSettingsMessage(
                                NekoMessageKey.SETTINGS_AI_SAVE_FAILED,
                            ),
                        )
                    }
                }
        }
    }

    private fun updateForm(transform: (AiAssistantFormState) -> AiAssistantFormState) {
        _uiState.update { current -> withDerived(transform(current)) }
    }

    private fun withDerived(state: AiAssistantFormState): AiAssistantFormState {
        val derived = AiAssistantSettingsPolicy.derive(state, consent)
        val endpoint = AiEndpointValidator.parse(state.baseUrl).getOrNull()
        return state.copy(
            baseUrlError = derived.baseUrlError,
            credentialMessage = derived.credentialMessage,
            policyMessage = derived.policyMessage,
            policyBlocked = derived.policyBlocked,
            disclosureAcknowledged = endpoint != null &&
                AiDisclosurePolicy.isAcknowledged(
                    endpoint.origin,
                    disclosureAcknowledgements,
                ),
            dirty = serviceBaseline?.let { baseline ->
                AiAssistantSettingsPolicy.isDirty(
                    AiAssistantSettingsPolicy.serviceDraft(state),
                    baseline.draft,
                )
            } ?: false,
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiAssistantSettingsViewModel(
                    store = container.aiAssistantStore,
                    client = container.aiAssistantClient,
                )
            }
        }
    }
}

private fun Throwable?.toSettingsMessage(fallback: NekoMessageKey): NekoMessage {
    val throwable = this ?: return NekoMessage(fallback)
    val typed = throwable.toNekoMessage(NekoMessageKey.GENERIC_ERROR)
    return if (typed.key != NekoMessageKey.GENERIC_ERROR) {
        typed
    } else {
        NekoMessage(fallback, listOf(throwable.message ?: throwable.javaClass.simpleName))
    }
}
