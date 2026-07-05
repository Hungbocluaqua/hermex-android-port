package com.uzairansar.hermex.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val serverUrl: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val message: String? = null,
    val isConnected: Boolean = false,
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    fun updateServerUrl(value: String) = _state.update { it.copy(serverUrl = value, message = null) }
    fun updatePassword(value: String) = _state.update { it.copy(password = value, message = null) }

    fun testConnection() {
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { authRepository.testConnection(snapshot.serverUrl) }
                .onSuccess { status ->
                    val suffix = if (status.authEnabled == true) " Password required." else " No password required."
                    _state.update { it.copy(isBusy = false, message = "Connection OK.$suffix") }
                }
                .onFailure { error -> _state.update { it.copy(isBusy = false, message = error.message ?: "Connection failed.") } }
        }
    }

    fun connect(onConnected: () -> Unit) {
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { authRepository.configure(snapshot.serverUrl, snapshot.password) }
                .onSuccess {
                    _state.update { it.copy(isBusy = false, isConnected = true) }
                    onConnected()
                }
                .onFailure { error -> _state.update { it.copy(isBusy = false, message = error.message ?: "Sign in failed.") } }
        }
    }
}
