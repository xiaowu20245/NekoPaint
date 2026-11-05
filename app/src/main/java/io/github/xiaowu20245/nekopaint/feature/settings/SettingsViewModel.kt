package io.github.xiaowu20245.nekopaint.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xiaowu20245.nekopaint.data.repository.MediaCacheManager
import io.github.xiaowu20245.nekopaint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val cacheBytes: Long = 0L,
    val loading: Boolean = true,
    val clearing: Boolean = false,
    val cacheCleared: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val cacheManager: MediaCacheManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        if (_uiState.value.clearing) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val bytes = runCatching { cacheManager.sizeBytes() }.getOrDefault(0L)
            _uiState.update { it.copy(cacheBytes = bytes, loading = false) }
        }
    }

    fun clearCache() {
        if (_uiState.value.clearing) return
        viewModelScope.launch {
            _uiState.update { it.copy(clearing = true, error = null) }
            val result = cacheManager.clear()
            result.onSuccess {
                val bytes = runCatching { cacheManager.sizeBytes() }.getOrDefault(0L)
                _uiState.update {
                    it.copy(
                        cacheBytes = bytes,
                        clearing = false,
                        loading = false,
                        cacheCleared = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        clearing = false,
                        loading = false,
                        error = throwable.message ?: "清理失败",
                    )
                }
            }
        }
    }

    fun consumeCacheCleared() {
        _uiState.update { it.copy(cacheCleared = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.mediaCacheManager) }
        }
    }
}
