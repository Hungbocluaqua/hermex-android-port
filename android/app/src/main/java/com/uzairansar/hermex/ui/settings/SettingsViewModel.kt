package com.uzairansar.hermex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.data.preferences.AppThemeMode
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.PanelsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val isSigningOut: Boolean = false,
    val isLoadingModels: Boolean = false,
    val isSavingDefaultModel: Boolean = false,
    val models: List<ModelSummary> = emptyList(),
    val defaultModel: String? = null,
    val notice: String? = null,
    val error: String? = null,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val localSettingsRepository: LocalSettingsRepository,
    private val panelsRepository: PanelsRepository?,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            localSettingsRepository.themeMode.collectLatest { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        loadModels()
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setThemeMode(mode) }
                .onSuccess { _state.update { it.copy(themeMode = mode, notice = "Appearance updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update appearance.") } }
        }
    }

    fun loadModels() {
        val repository = panelsRepository ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true, error = null, notice = null) }
            runCatching { repository.models() }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isLoadingModels = false,
                            models = response.models.orEmpty(),
                            defaultModel = response.defaultModel,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(isLoadingModels = false, error = error.message ?: "Could not load models.") } }
        }
    }

    fun saveDefaultModel(model: ModelSummary) {
        val repository = panelsRepository ?: return
        val id = model.id ?: model.name ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSavingDefaultModel = true, error = null, notice = null) }
            runCatching { repository.saveDefaultModel(id) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isSavingDefaultModel = false,
                            defaultModel = response.model ?: id,
                            notice = "Default model saved.",
                            error = response.error,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(isSavingDefaultModel = false, error = error.message ?: "Could not save default model.") } }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSigningOut = true, error = null) }
            runCatching { authRepository.logout() }
                .onSuccess {
                    _state.update { it.copy(isSigningOut = false) }
                    onSignedOut()
                }
                .onFailure { error -> _state.update { it.copy(isSigningOut = false, error = error.message) } }
        }
    }
}
