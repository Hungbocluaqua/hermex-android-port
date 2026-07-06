package com.uzairansar.hermex.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.network.parseCustomHeaderLines
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val serverUrl: String = "",
    val password: String = "",
    val customHeadersText: String = "",
    val isBusy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val isPasswordRequired: Boolean = true,
    val isConnected: Boolean = false,
)

class OnboardingViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<OnboardingUiState> = _state

    fun updateServerUrl(value: String) = _state.update {
        it.copy(
            serverUrl = value,
            message = null,
            messageIsError = false,
            isPasswordRequired = true,
        )
    }

    fun updatePassword(value: String) = _state.update {
        it.copy(password = value, message = null, messageIsError = false)
    }

    fun updateCustomHeadersText(value: String) = _state.update {
        it.copy(customHeadersText = value, message = null, messageIsError = false)
    }

    fun testConnection() {
        val snapshot = _state.value
        val headers = parseHeadersOrShowMessage(snapshot.customHeadersText) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, messageIsError = false) }
            runCatching { authRepository.testConnection(snapshot.serverUrl, headers) }
                .onSuccess { status ->
                    val passkeyOnly = status.authEnabled == true && status.passwordAuthEnabled == false
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = if (passkeyOnly) {
                                "This server signs in with passkeys, which Hermex Android does not support yet."
                            } else if (status.authEnabled == true) {
                                "Connection ok. Password required."
                            } else {
                                "Connection ok. Password not required."
                            },
                            messageIsError = passkeyOnly,
                            isPasswordRequired = status.authEnabled != false && status.passwordAuthEnabled != false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = error.message ?: "Connection failed.",
                            messageIsError = true,
                        )
                    }
                }
        }
    }

    fun connect(onConnected: () -> Unit) {
        val snapshot = _state.value
        val headers = parseHeadersOrShowMessage(snapshot.customHeadersText) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, messageIsError = false) }
            runCatching { authRepository.configure(snapshot.serverUrl, snapshot.password, headers) }
                .onSuccess {
                    _state.update { it.copy(isBusy = false, isConnected = true) }
                    onConnected()
                }
                .onFailure { error ->
                    val message = error.message ?: "Sign in failed."
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = message,
                            messageIsError = true,
                            isPasswordRequired = it.isPasswordRequired ||
                                message.contains("password", ignoreCase = true),
                        )
                    }
                }
        }
    }

    private fun parseHeadersOrShowMessage(text: String) =
        runCatching { parseCustomHeaderLines(text) }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        message = error.message ?: "Could not parse custom headers.",
                        messageIsError = true,
                    )
                }
            }
            .getOrNull()

    private fun initialState(): OnboardingUiState {
        val authState = authRepository.state.value
        val savedServer = (authState as? AuthState.LoggedOut)?.server?.toString().orEmpty()
        val activeServerId = authRepository.servers.value.activeServerId
        val savedHeaders = activeServerId
            ?.let(authRepository::customHeaders)
            .orEmpty()
            .joinToString("\n") { header -> "${header.name}: ${header.value}" }
        return OnboardingUiState(
            serverUrl = savedServer,
            customHeadersText = savedHeaders,
        )
    }
}
