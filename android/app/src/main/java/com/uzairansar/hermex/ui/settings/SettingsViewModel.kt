package com.uzairansar.hermex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ProviderSummary
import com.uzairansar.hermex.core.model.SettingsResponse
import com.uzairansar.hermex.core.network.CustomHeader
import com.uzairansar.hermex.core.network.parseCustomHeaderLines
import com.uzairansar.hermex.data.repository.AddServerResult
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.data.preferences.modelIdentifier
import com.uzairansar.hermex.data.preferences.AppThemeMode
import com.uzairansar.hermex.data.preferences.ChatDisplaySettings
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.preferences.SessionRowDisplaySettings
import com.uzairansar.hermex.data.preferences.SessionIdentitySettings
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.CacheMaintenanceRepository
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.data.secure.ServerRegistrySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val tintPrimaryActionsWithThemeColor: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val sessionIdentitySettings: SessionIdentitySettings = SessionIdentitySettings(),
    val headerLogoColorHex: String = "#FFD700",
    val streamingSendBehavior: StreamingSendBehavior = StreamingSendBehavior.Steer,
    val chatDisplaySettings: ChatDisplaySettings = ChatDisplaySettings(),
    val sessionRowDisplaySettings: SessionRowDisplaySettings = SessionRowDisplaySettings(),
    val responseCompletionNotificationsEnabled: Boolean = false,
    val hasRequestedResponseCompletionNotificationPermission: Boolean = false,
    val responseCompletionNotificationStatusMessage: String? = null,
    val serverSnapshot: ServerRegistrySnapshot = ServerRegistrySnapshot(),
    val showCliSessions: Boolean = true,
    val cliSessionsServerSynced: Boolean = false,
    val isSavingCliSessions: Boolean = false,
    val cliSessionsError: String? = null,
    val isSigningOut: Boolean = false,
    val isLoadingServerSettings: Boolean = false,
    val isSavingDefaultModel: Boolean = false,
    val isSavingDefaultProfile: Boolean = false,
    val isLoadingLiveModels: Boolean = false,
    val showDefaultModelPicker: Boolean = false,
    val defaultModelPickerError: String? = null,
    val showDefaultProfilePicker: Boolean = false,
    val defaultProfilePickerError: String? = null,
    val models: List<ModelSummary> = emptyList(),
    val modelProviders: List<ProviderSummary> = emptyList(),
    val defaultModel: String? = null,
    val profiles: List<ProfileSummary> = emptyList(),
    val activeProfile: String? = null,
    val isSingleProfileMode: Boolean = false,
    val showCreateProfileDialog: Boolean = false,
    val isCreatingProfile: Boolean = false,
    val profileCreateError: String? = null,
    val serverSettings: SettingsResponse? = null,
    val serverUpdateState: WebUiUpdateState? = null,
    val isCheckingForUpdates: Boolean = false,
    val forcedUpdateCheckOutcome: ForcedUpdateCheckOutcome? = null,
    val showForcedUpdateCheckResult: Boolean = false,
    val confirmServerUpdate: Boolean = false,
    val updateApplyPhase: ServerUpdateApplyPhase = ServerUpdateApplyPhase.Idle,
    val updateApplyMessage: String? = null,
    val customHeadersByServer: Map<String, List<CustomHeader>> = emptyMap(),
    val identityEditorServer: ServerAccount? = null,
    val identityDraft: ServerIdentityDraft = ServerIdentityDraft(),
    val headerEditorServer: ServerAccount? = null,
    val headerEditorText: String = "",
    val headerEditorError: String? = null,
    val isAddingServer: Boolean = false,
    val addServerDialogVisible: Boolean = false,
    val addServerUrl: String = "",
    val addServerPassword: String = "",
    val addServerHeadersText: String = "",
    val addServerDisplayName: String = "",
    val addServerInitials: String = "",
    val addServerHeaderColorHex: String = DefaultServerHeaderColorHex,
    val addServerNeedsPassword: Boolean = false,
    val addServerError: String? = null,
    val clearCacheServer: ServerAccount? = null,
    val forgetServer: ServerAccount? = null,
    val isForgettingServer: Boolean = false,
    val isClearingCache: Boolean = false,
    val isMaintainingCache: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

data class ServerIdentityDraft(
    val displayName: String = "",
    val initials: String = "",
    val headerLogoColorHex: String = DefaultServerHeaderColorHex,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val localSettingsRepository: LocalSettingsRepository,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository?,
    private val panelsRepository: PanelsRepository?,
) : ViewModel() {
    private val initialServerSnapshot = authRepository.servers.value
    private val initialActiveAccount = initialServerSnapshot.servers.firstOrNull {
        it.id == initialServerSnapshot.activeServerId
    }
    private val _state = MutableStateFlow(
        SettingsUiState(
            serverSnapshot = initialServerSnapshot,
            sessionIdentitySettings = initialActiveAccount?.let {
                SessionIdentitySettings(displayName = it.displayName, initials = it.initials)
            } ?: SessionIdentitySettings(),
            headerLogoColorHex = initialActiveAccount?.headerLogoColorHex ?: DefaultServerHeaderColorHex,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            localSettingsRepository.themeMode.collectLatest { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.streamingSendBehavior.collectLatest { behavior ->
                _state.update { it.copy(streamingSendBehavior = behavior) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.tintPrimaryActionsWithThemeColor.collectLatest { enabled ->
                _state.update { it.copy(tintPrimaryActionsWithThemeColor = enabled) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.hapticsEnabled.collectLatest { enabled ->
                _state.update { it.copy(hapticsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.chatDisplaySettings.collectLatest { displaySettings ->
                _state.update { it.copy(chatDisplaySettings = displaySettings) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.sessionRowDisplaySettings.collectLatest { displaySettings ->
                _state.update { it.copy(sessionRowDisplaySettings = displaySettings) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.responseCompletionNotificationsEnabled.collectLatest { enabled ->
                _state.update { it.copy(responseCompletionNotificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.hasRequestedResponseCompletionNotificationPermission.collectLatest { requested ->
                _state.update { it.copy(hasRequestedResponseCompletionNotificationPermission = requested) }
            }
        }
        viewModelScope.launch {
            authRepository.servers.collectLatest { snapshot ->
                val activeAccount = snapshot.servers.firstOrNull { it.id == snapshot.activeServerId }
                _state.update {
                    it.copy(
                        serverSnapshot = snapshot,
                        sessionIdentitySettings = activeAccount?.let { account ->
                            SessionIdentitySettings(
                                displayName = account.displayName,
                                initials = account.initials,
                            )
                        } ?: it.sessionIdentitySettings,
                        headerLogoColorHex = activeAccount?.headerLogoColorHex ?: it.headerLogoColorHex,
                        customHeadersByServer = snapshot.servers.associate { account ->
                            account.id to authRepository.customHeaders(account.id)
                        },
                    )
                }
                activeAccount?.let { account ->
                    localSettingsRepository.setSessionIdentityDisplayName(account.displayName)
                    localSettingsRepository.setSessionIdentityInitials(account.initials)
                    localSettingsRepository.setHeaderLogoColorHex(account.headerLogoColorHex)
                }
            }
        }
        loadServerSettings()
    }

    private val activeServerId: String?
        get() = _state.value.serverSnapshot.activeServerId

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setThemeMode(mode) }
                .onSuccess { _state.update { it.copy(themeMode = mode, notice = "Appearance updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update appearance.") } }
        }
    }

    fun setStreamingSendBehavior(behavior: StreamingSendBehavior) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setStreamingSendBehavior(behavior) }
                .onSuccess { _state.update { it.copy(streamingSendBehavior = behavior, notice = "Interaction updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update interaction behavior.") } }
        }
    }

    fun setTintPrimaryActionsWithThemeColor(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setTintPrimaryActionsWithThemeColor(enabled) }
                .onSuccess { _state.update { it.copy(tintPrimaryActionsWithThemeColor = enabled, notice = "Appearance updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update appearance.") } }
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setHapticsEnabled(enabled) }
                .onSuccess { _state.update { it.copy(hapticsEnabled = enabled, notice = "Interaction updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update haptic feedback.") } }
        }
    }

    fun setSessionIdentityDisplayName(displayName: String) {
        val limited = displayName.take(80)
        _state.update {
            it.copy(sessionIdentitySettings = it.sessionIdentitySettings.copy(displayName = limited))
        }
        val targetAccount = currentActiveServerAccount()
        val targetInitials = _state.value.sessionIdentitySettings.initials
        val targetColor = _state.value.headerLogoColorHex
        viewModelScope.launch {
            runCatching {
                localSettingsRepository.setSessionIdentityDisplayName(limited)
                updateServerIdentity(targetAccount, limited, targetInitials, targetColor)
            }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update session identity.") } }
        }
    }

    fun setSessionIdentityInitials(initials: String) {
        val normalized = normalizedInitials(initials)
        _state.update {
            it.copy(sessionIdentitySettings = it.sessionIdentitySettings.copy(initials = normalized))
        }
        val targetAccount = currentActiveServerAccount()
        val targetDisplayName = _state.value.sessionIdentitySettings.displayName
        val targetColor = _state.value.headerLogoColorHex
        viewModelScope.launch {
            runCatching {
                localSettingsRepository.setSessionIdentityInitials(normalized)
                updateServerIdentity(targetAccount, targetDisplayName, normalized, targetColor)
            }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update session identity.") } }
        }
    }

    fun setHeaderLogoColorHex(colorHex: String) {
        val normalized = normalizedHeaderColorHex(colorHex)
        _state.update { it.copy(headerLogoColorHex = normalized) }
        val targetAccount = currentActiveServerAccount()
        val targetIdentity = _state.value.sessionIdentitySettings
        viewModelScope.launch {
            runCatching {
                localSettingsRepository.setHeaderLogoColorHex(normalized)
                updateServerIdentity(
                    targetAccount,
                    targetIdentity.displayName,
                    targetIdentity.initials,
                    normalized,
                )
            }
                .onSuccess { _state.update { it.copy(notice = "Appearance updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update appearance.") } }
        }
    }

    private fun currentActiveServerAccount(): ServerAccount? {
        val snapshot = _state.value.serverSnapshot
        return snapshot.servers.firstOrNull { it.id == snapshot.activeServerId }
    }

    private fun updateServerIdentity(
        account: ServerAccount?,
        displayName: String,
        initials: String,
        headerLogoColorHex: String,
    ) {
        account ?: return
        authRepository.updateServerIdentity(
            account = account,
            displayName = displayName,
            initials = initials,
            headerLogoColorHex = headerLogoColorHex,
        )
    }

    fun loadModels() {
        loadServerSettings()
    }

    fun loadServerSettings() {
        val repository = panelsRepository ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingServerSettings = true, error = null, notice = null) }
            val serverId = activeServerId
            val cachedCliSessions = serverId?.let { localSettingsRepository.currentShowCliSessions(it) } ?: true
            val modelsResult = runCatching { repository.models() }
            val profilesResult = runCatching { repository.profiles() }
            val settingsResult = runCatching { repository.settings() }
            val updatesResult = runCatching { repository.updatesCheck() }
            val settings = settingsResult.getOrNull()
            val serverCliSessions = settings?.showCliSessions
            if (serverId != null && serverCliSessions != null) {
                runCatching { localSettingsRepository.setShowCliSessions(serverId, serverCliSessions) }
            }
            _state.update { current ->
                current.copy(
                    isLoadingServerSettings = false,
                    models = modelsResult.getOrNull()?.models.orEmpty(),
                    modelProviders = modelsResult.getOrNull()?.providers.orEmpty(),
                    defaultModel = modelsResult.getOrNull()?.defaultModel,
                    profiles = profilesResult.getOrNull()?.profiles.orEmpty(),
                    activeProfile = profilesResult.getOrNull()?.effectiveDefaultProfileName(),
                    isSingleProfileMode = profilesResult.getOrNull()?.singleProfileMode == true,
                    serverSettings = settings,
                    serverUpdateState = updatesResult.getOrNull()?.webUiUpdateState() ?: current.serverUpdateState,
                    showCliSessions = serverCliSessions ?: cachedCliSessions,
                    cliSessionsServerSynced = serverCliSessions != null,
                    cliSessionsError = null,
                    error = listOfNotNull(
                        modelsResult.exceptionOrNull()?.message,
                        profilesResult.exceptionOrNull()?.message,
                        settingsResult.exceptionOrNull()?.message,
                    ).firstOrNull(),
                )
            }
        }
    }

    fun setShowCliSessions(enabled: Boolean) {
        val repository = panelsRepository ?: return
        val serverId = activeServerId ?: return
        val previous = _state.value.showCliSessions
        val shouldSyncWithServer = _state.value.cliSessionsServerSynced
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showCliSessions = enabled,
                    isSavingCliSessions = true,
                    cliSessionsError = null,
                    notice = null,
                    error = null,
                )
            }
            runCatching { localSettingsRepository.setShowCliSessions(serverId, enabled) }
            if (!shouldSyncWithServer) {
                _state.update { it.copy(isSavingCliSessions = false) }
                return@launch
            }
            runCatching { repository.updateSettings(showCliSessions = enabled) }
                .onSuccess { response ->
                    response.showCliSessions?.let { serverValue ->
                        localSettingsRepository.setShowCliSessions(serverId, serverValue)
                    }
                    _state.update {
                        it.copy(
                            showCliSessions = response.showCliSessions ?: enabled,
                            isSavingCliSessions = false,
                            cliSessionsServerSynced = response.showCliSessions != null,
                            cliSessionsError = null,
                            notice = "CLI session visibility updated.",
                        )
                    }
                }
                .onFailure { error ->
                    localSettingsRepository.setShowCliSessions(serverId, previous)
                    _state.update {
                        it.copy(
                            showCliSessions = previous,
                            isSavingCliSessions = false,
                            cliSessionsError = error.message ?: "Could not sync CLI session visibility.",
                        )
                    }
                }
        }
    }

    fun setShowThinkingAndToolCards(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setShowThinkingAndToolCards(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setThinkingCardsStartExpanded(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setThinkingCardsStartExpanded(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setToolCardsStartExpanded(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setToolCardsStartExpanded(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setHidesAttachmentPaths(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setHidesAttachmentPaths(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setShowsAssistantTurnTimestamps(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setShowsAssistantTurnTimestamps(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setRtlChatLayoutEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setRtlChatLayoutEnabled(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setWrapsCodeBlockLines(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setWrapsCodeBlockLines(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setStreamedTextAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setStreamedTextAnimationEnabled(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setShowsStatusNotificationResponseExcerpts(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setShowsStatusNotificationResponseExcerpts(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Chat display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update chat display.") } }
        }
    }

    fun setSessionRowShowMessageCount(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setSessionRowShowMessageCount(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Session display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update session display.") } }
        }
    }

    fun setSessionRowShowWorkspace(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setSessionRowShowWorkspace(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Session display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update session display.") } }
        }
    }

    fun setShowCronSessions(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setShowCronSessions(enabled) }
                .onSuccess { _state.update { it.copy(notice = "Session display updated.", error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update session display.") } }
        }
    }

    fun setResponseCompletionNotificationsEnabled(enabled: Boolean, statusMessage: String? = null) {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setResponseCompletionNotificationsEnabled(enabled) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            responseCompletionNotificationsEnabled = enabled,
                            responseCompletionNotificationStatusMessage = statusMessage,
                            notice = if (enabled) "Response complete alerts enabled." else "Response complete alerts disabled.",
                            error = null,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not update notification alerts.") } }
        }
    }

    fun markResponseCompletionNotificationPermissionRequested() {
        viewModelScope.launch {
            runCatching { localSettingsRepository.setHasRequestedResponseCompletionNotificationPermission(true) }
                .onSuccess { _state.update { it.copy(hasRequestedResponseCompletionNotificationPermission = true) } }
        }
    }

    fun refreshResponseCompletionNotificationPermission(canPostNotifications: Boolean) {
        viewModelScope.launch {
            val preferenceEnabled = localSettingsRepository.responseCompletionNotificationsEnabled.first()
            if (!canPostNotifications && preferenceEnabled) {
                runCatching { localSettingsRepository.setResponseCompletionNotificationsEnabled(false) }
                    .onSuccess {
                        _state.update {
                            it.copy(
                                responseCompletionNotificationsEnabled = false,
                                responseCompletionNotificationStatusMessage = "Android notifications disabled.",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(error = error.message ?: "Could not update notification alerts.") }
                    }
            } else {
                _state.update {
                    it.copy(
                        responseCompletionNotificationsEnabled = preferenceEnabled,
                        responseCompletionNotificationStatusMessage = null,
                    )
                }
            }
        }
    }

    fun handleResponseCompletionNotificationPermissionResult(granted: Boolean) {
        setResponseCompletionNotificationsEnabled(
            enabled = granted,
            statusMessage = if (granted) "Android notifications allowed." else "Android notifications disabled.",
        )
    }

    fun activateServer(id: String) {
        runCatching { authRepository.activate(id) }
            .onSuccess { _state.update { it.copy(notice = "Active server updated.", error = null) } }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch server.") } }
    }

    fun openIdentityEditor(account: ServerAccount) {
        _state.update {
            it.copy(
                identityEditorServer = account,
                identityDraft = ServerIdentityDraft(
                    displayName = account.displayName,
                    initials = account.initials,
                    headerLogoColorHex = normalizedHeaderColorHex(account.headerLogoColorHex),
                ),
                notice = null,
                error = null,
            )
        }
    }

    fun updateIdentityDraft(draft: ServerIdentityDraft) {
        _state.update { it.copy(identityDraft = draft.copy(initials = normalizedInitials(draft.initials)), error = null) }
    }

    fun dismissIdentityEditor() {
        _state.update { it.copy(identityEditorServer = null, identityDraft = ServerIdentityDraft()) }
    }

    fun saveIdentityDraft() {
        val account = _state.value.identityEditorServer ?: return
        val draft = _state.value.identityDraft
        val finalName = draft.displayName.trim().ifBlank { account.displayName }
        val finalInitials = displayInitials(
            displayName = finalName,
            storedInitials = draft.initials,
            fallbackFullName = account.displayName.ifBlank { account.urlString },
        )
        val color = normalizedHeaderColorHex(draft.headerLogoColorHex)
        runCatching {
            authRepository.updateServerIdentity(
                account = account,
                displayName = finalName,
                initials = finalInitials,
                headerLogoColorHex = color,
            ) ?: error("Server is no longer configured.")
        }
            .onSuccess { updated ->
                _state.update {
                    it.copy(
                        identityEditorServer = null,
                        identityDraft = ServerIdentityDraft(),
                        notice = "Identity saved for ${updated.displayName}.",
                        error = null,
                    )
                }
            }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not save identity.") } }
    }

    fun openHeaderEditor(account: ServerAccount) {
        val text = authRepository.customHeaders(account.id)
            .joinToString(separator = "\n") { "${it.name}: ${it.value}" }
        _state.update {
            it.copy(
                headerEditorServer = account,
                headerEditorText = text,
                headerEditorError = null,
                notice = null,
                error = null,
            )
        }
    }

    fun updateHeaderEditorText(text: String) {
        _state.update { it.copy(headerEditorText = text, headerEditorError = null) }
    }

    fun dismissHeaderEditor() {
        _state.update { it.copy(headerEditorServer = null, headerEditorText = "", headerEditorError = null) }
    }

    fun saveHeaderEditor() {
        val server = _state.value.headerEditorServer ?: return
        val parsed = runCatching { parseCustomHeaderLines(_state.value.headerEditorText) }
        parsed
            .onSuccess { headers ->
                authRepository.saveCustomHeaders(server.id, headers)
                _state.update {
                    it.copy(
                        customHeadersByServer = it.customHeadersByServer + (server.id to headers),
                        headerEditorServer = null,
                        headerEditorText = "",
                        headerEditorError = null,
                        notice = if (headers.isEmpty()) "Custom headers cleared." else "Custom headers saved.",
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(headerEditorError = error.message ?: "Could not parse custom headers.") }
            }
    }

    fun openAddServer() {
        _state.update {
            it.copy(
                addServerDialogVisible = true,
                addServerUrl = "",
                addServerPassword = "",
                addServerHeadersText = "",
                addServerDisplayName = "",
                addServerInitials = "",
                addServerHeaderColorHex = DefaultServerHeaderColorHex,
                addServerNeedsPassword = false,
                addServerError = null,
                notice = null,
                error = null,
            )
        }
    }

    fun dismissAddServer() {
        if (_state.value.isAddingServer) return
        _state.update {
            it.copy(
                addServerDialogVisible = false,
                addServerUrl = "",
                addServerPassword = "",
                addServerHeadersText = "",
                addServerDisplayName = "",
                addServerInitials = "",
                addServerHeaderColorHex = DefaultServerHeaderColorHex,
                addServerNeedsPassword = false,
                addServerError = null,
            )
        }
    }

    fun updateAddServerUrl(value: String) {
        _state.update { it.copy(addServerUrl = value, addServerError = null) }
    }

    fun updateAddServerPassword(value: String) {
        _state.update { it.copy(addServerPassword = value, addServerError = null) }
    }

    fun updateAddServerHeadersText(value: String) {
        _state.update { it.copy(addServerHeadersText = value, addServerError = null) }
    }

    fun updateAddServerDisplayName(value: String) {
        _state.update { it.copy(addServerDisplayName = value, addServerError = null) }
    }

    fun updateAddServerInitials(value: String) {
        _state.update { it.copy(addServerInitials = normalizedInitials(value), addServerError = null) }
    }

    fun updateAddServerHeaderColor(hex: String) {
        _state.update { it.copy(addServerHeaderColorHex = normalizedHeaderColorHex(hex), addServerError = null) }
    }

    fun submitAddServer() {
        val snapshot = _state.value
        if (snapshot.addServerUrl.isBlank()) {
            _state.update { it.copy(addServerError = "Server URL is required.") }
            return
        }
        val headers = runCatching { parseCustomHeaderLines(snapshot.addServerHeadersText) }
            .onFailure { error -> _state.update { it.copy(addServerError = error.message ?: "Could not parse custom headers.") } }
            .getOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAddingServer = true, addServerError = null, notice = null, error = null) }
            runCatching { authRepository.addServer(snapshot.addServerUrl, snapshot.addServerPassword, headers) }
                .onSuccess { result ->
                    when (result) {
                        is AddServerResult.Added -> {
                            val finalName = snapshot.addServerDisplayName.trim().ifBlank { result.account.displayName }
                            val finalInitials = displayInitials(
                                displayName = finalName,
                                storedInitials = snapshot.addServerInitials,
                                fallbackFullName = result.account.displayName,
                            )
                            val savedAccount = authRepository.updateServerIdentity(
                                account = result.account,
                                displayName = finalName,
                                initials = finalInitials,
                                headerLogoColorHex = normalizedHeaderColorHex(snapshot.addServerHeaderColorHex),
                            ) ?: result.account
                            _state.update {
                                it.copy(
                                    isAddingServer = false,
                                    addServerDialogVisible = false,
                                    addServerUrl = "",
                                    addServerPassword = "",
                                    addServerHeadersText = "",
                                    addServerDisplayName = "",
                                    addServerInitials = "",
                                    addServerHeaderColorHex = DefaultServerHeaderColorHex,
                                    addServerNeedsPassword = false,
                                    addServerError = null,
                                    notice = "Server added: ${savedAccount.displayName}.",
                                    error = null,
                                )
                            }
                            loadServerSettings()
                        }
                        AddServerResult.NeedsPassword -> {
                            _state.update {
                                it.copy(
                                    isAddingServer = false,
                                    addServerNeedsPassword = true,
                                    addServerError = null,
                                    notice = null,
                                )
                            }
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAddingServer = false,
                            addServerError = error.message ?: "Could not add server.",
                        )
                    }
                }
        }
    }

    fun requestClearCache(account: ServerAccount) {
        _state.update { it.copy(clearCacheServer = account, notice = null, error = null) }
    }

    fun cancelClearCache() {
        _state.update { it.copy(clearCacheServer = null) }
    }

    fun confirmClearCache() {
        val account = _state.value.clearCacheServer ?: return
        val repository = cacheMaintenanceRepository ?: return
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true, error = null, notice = null) }
            runCatching { repository.clearServer(account.urlString) }
                .onSuccess {
                    _state.update { it.copy(isClearingCache = false, clearCacheServer = null, notice = "Offline cache cleared for ${account.displayName}.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(isClearingCache = false, error = error.message ?: "Could not clear cache.") }
                }
        }
    }

    fun runCacheMaintenance() {
        val repository = cacheMaintenanceRepository ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMaintainingCache = true, error = null, notice = null) }
            runCatching { repository.maintenance() }
                .onSuccess { _state.update { it.copy(isMaintainingCache = false, notice = "Expired cache cleaned up.") } }
                .onFailure { error ->
                    _state.update { it.copy(isMaintainingCache = false, error = error.message ?: "Could not clean up cache.") }
                }
        }
    }

    fun checkForUpdatesManually() {
        val repository = panelsRepository ?: return
        val phase = _state.value.updateApplyPhase
        if (_state.value.isCheckingForUpdates || phase == ServerUpdateApplyPhase.Applying || phase == ServerUpdateApplyPhase.Recovering) {
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCheckingForUpdates = true,
                    forcedUpdateCheckOutcome = null,
                    showForcedUpdateCheckResult = false,
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.updatesCheckForced() }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            serverUpdateState = response.webUiUpdateState(),
                            forcedUpdateCheckOutcome = response.forcedCheckOutcome(),
                            showForcedUpdateCheckResult = true,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            forcedUpdateCheckOutcome = ForcedUpdateCheckOutcome.Error,
                            showForcedUpdateCheckResult = true,
                        )
                    }
                }
        }
    }

    fun dismissForcedUpdateCheckResult() {
        _state.update { it.copy(showForcedUpdateCheckResult = false) }
    }

    fun requestServerUpdate() {
        _state.update { it.copy(confirmServerUpdate = true) }
    }

    fun cancelServerUpdate() {
        _state.update { it.copy(confirmServerUpdate = false) }
    }

    fun applyServerUpdate() {
        val repository = panelsRepository ?: return
        val snapshot = _state.value
        if (snapshot.isCheckingForUpdates) return
        when (snapshot.updateApplyPhase) {
            ServerUpdateApplyPhase.Idle,
            ServerUpdateApplyPhase.Blocked,
            ServerUpdateApplyPhase.Failed,
            -> Unit
            ServerUpdateApplyPhase.Applying,
            ServerUpdateApplyPhase.Recovering,
            -> return
        }

        viewModelScope.launch {
            val previousVersion = _state.value.serverSettings?.webuiVersion
            _state.update {
                it.copy(
                    confirmServerUpdate = false,
                    updateApplyPhase = ServerUpdateApplyPhase.Applying,
                    updateApplyMessage = null,
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.applyUpdate("webui") }
                .onSuccess { response ->
                    when (response.applyOutcome()) {
                        ServerUpdateApplyOutcome.Applying -> {
                            _state.update { it.copy(updateApplyPhase = ServerUpdateApplyPhase.Recovering) }
                            waitForServerToReturn(previousVersion)
                        }
                        ServerUpdateApplyOutcome.RestartBlocked -> {
                            _state.update {
                                it.copy(
                                    updateApplyPhase = ServerUpdateApplyPhase.Blocked,
                                    updateApplyMessage = response.displayMessage("The server is busy with active work. Wait for it to finish, then retry."),
                                )
                            }
                        }
                        ServerUpdateApplyOutcome.Failed -> {
                            _state.update {
                                it.copy(
                                    updateApplyPhase = ServerUpdateApplyPhase.Failed,
                                    updateApplyMessage = response.displayMessage("The update could not be applied."),
                                )
                            }
                        }
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            updateApplyPhase = ServerUpdateApplyPhase.Failed,
                            updateApplyMessage = "Could not reach the server to start the update.",
                        )
                    }
                }
        }
    }

    private suspend fun waitForServerToReturn(previousVersion: String?) {
        val repository = panelsRepository ?: return
        repeat(30) {
            delay(2_000)
            val settings = runCatching { repository.settings() }.getOrNull() ?: return@repeat
            val updateState = runCatching { repository.updatesCheck().webUiUpdateState() }.getOrNull()
                ?: WebUiUpdateState.Unavailable
            val restartConfirmed = (settings.webuiVersion != null && settings.webuiVersion != previousVersion) ||
                updateState == WebUiUpdateState.UpToDate
            if (restartConfirmed) {
                _state.update {
                    it.copy(
                        serverSettings = settings,
                        serverUpdateState = updateState,
                        updateApplyPhase = ServerUpdateApplyPhase.Idle,
                        updateApplyMessage = null,
                        notice = "Server update finished.",
                    )
                }
                return
            }
        }

        loadServerSettings()
        _state.update { current ->
            if (current.serverUpdateState is WebUiUpdateState.UpdateAvailable) {
                current.copy(
                    updateApplyPhase = ServerUpdateApplyPhase.Failed,
                    updateApplyMessage = "The update is taking longer than expected to finish. Try again in a moment.",
                )
            } else {
                current.copy(updateApplyPhase = ServerUpdateApplyPhase.Idle, updateApplyMessage = null)
            }
        }
    }

    fun requestForgetServer(account: ServerAccount) {
        _state.update { it.copy(forgetServer = account, notice = null, error = null) }
    }

    fun cancelForgetServer() {
        _state.update { it.copy(forgetServer = null) }
    }

    fun confirmForgetServer(onSignedOut: () -> Unit) {
        val account = _state.value.forgetServer ?: return
        val wasActive = account.id == _state.value.serverSnapshot.activeServerId
        _state.update { it.copy(isForgettingServer = true, error = null) }
        viewModelScope.launch {
            runCatching {
                cacheMaintenanceRepository?.clearServer(account.urlString)
                authRepository.forget(account.id)
            }
                .onSuccess {
                    _state.update {
                        it.copy(
                            forgetServer = null,
                            isForgettingServer = false,
                            customHeadersByServer = it.customHeadersByServer - account.id,
                            notice = "Server removed.",
                            error = null,
                        )
                    }
                    if (wasActive && authRepository.state.value !is AuthState.LoggedIn) onSignedOut()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isForgettingServer = false,
                            error = error.message ?: "Could not remove server.",
                        )
                    }
                }
        }
    }

    fun saveDefaultProfile(profile: ProfileSummary) {
        val repository = panelsRepository ?: return
        val name = profile.normalizedProfileName() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSavingDefaultProfile = true, error = null, notice = null) }
            runCatching { repository.switchProfile(name) }
                .onSuccess { response ->
                    val responseError = response.error?.trim()?.takeIf { it.isNotBlank() }
                    if (responseError != null) {
                        _state.update {
                            it.copy(
                                isSavingDefaultProfile = false,
                                defaultProfilePickerError = responseError,
                                error = responseError,
                            )
                        }
                        return@onSuccess
                    }
                    val profiles = response.profiles ?: _state.value.profiles
                    val resolved = response.active?.trim()?.takeIf { it.isNotBlank() }
                        ?: profiles.firstOrNull { it.isActive == true }?.normalizedProfileName()
                        ?: name
                    _state.update {
                        it.copy(
                            isSavingDefaultProfile = false,
                            showDefaultProfilePicker = false,
                            profiles = profiles,
                            activeProfile = resolved,
                            defaultModel = response.defaultModel ?: it.defaultModel,
                            notice = "Default profile saved.",
                            defaultProfilePickerError = null,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Could not save default profile."
                    _state.update { it.copy(isSavingDefaultProfile = false, defaultProfilePickerError = message, error = message) }
                }
        }
    }

    fun saveDefaultModel(model: ModelSummary) {
        saveDefaultModelId(model.modelIdentifier.orEmpty())
    }

    fun saveDefaultModelId(rawModelId: String) {
        val repository = panelsRepository ?: return
        val id = rawModelId.trim()
        if (id.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSavingDefaultModel = true, error = null, notice = null) }
            runCatching { repository.saveDefaultModel(id) }
                .onSuccess { response ->
                    val responseError = response.error?.trim()?.takeIf { it.isNotBlank() }
                    if (responseError != null || response.ok == false) {
                        val message = responseError ?: "The server did not confirm the change."
                        _state.update {
                            it.copy(
                                isSavingDefaultModel = false,
                                defaultModelPickerError = message,
                                error = message,
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            isSavingDefaultModel = false,
                            showDefaultModelPicker = false,
                            defaultModel = response.model ?: id,
                            notice = "Default model saved.",
                            defaultModelPickerError = null,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Could not save default model."
                    _state.update { it.copy(isSavingDefaultModel = false, defaultModelPickerError = message, error = message) }
                }
        }
    }

    fun openDefaultModelPicker() {
        _state.update {
            it.copy(
                showDefaultModelPicker = true,
                defaultModelPickerError = null,
                notice = null,
                error = null,
            )
        }
        refreshLiveModels()
    }

    fun dismissDefaultModelPicker() {
        _state.update { it.copy(showDefaultModelPicker = false, defaultModelPickerError = null) }
    }

    fun openDefaultProfilePicker() {
        _state.update {
            it.copy(
                showDefaultProfilePicker = true,
                defaultProfilePickerError = null,
                notice = null,
                error = null,
            )
        }
    }

    fun dismissDefaultProfilePicker() {
        _state.update {
            it.copy(
                showDefaultProfilePicker = false,
                defaultProfilePickerError = null,
                showCreateProfileDialog = false,
                profileCreateError = null,
            )
        }
    }

    fun openCreateProfileDialog() {
        _state.update { it.copy(showCreateProfileDialog = true, profileCreateError = null) }
    }

    fun dismissCreateProfileDialog() {
        if (_state.value.isCreatingProfile) return
        _state.update { it.copy(showCreateProfileDialog = false, profileCreateError = null) }
    }

    fun createProfile(
        rawName: String,
        cloneConfig: Boolean,
        rawDefaultModel: String,
        rawModelProvider: String,
        rawBaseUrl: String,
        rawApiKey: String,
    ) {
        val repository = panelsRepository ?: return
        val name = rawName.trim().lowercase(Locale.US)
        val defaultModel = rawDefaultModel.trim().takeIf { it.isNotBlank() }
        val modelProvider = rawModelProvider.trim().takeIf { it.isNotBlank() }
        val baseUrl = rawBaseUrl.trim().takeIf { it.isNotBlank() }
        val apiKey = rawApiKey.trim().takeIf { it.isNotBlank() }
        when {
            !ProfileNameRules.isValid(name) -> {
                _state.update { it.copy(profileCreateError = "Use lowercase letters, numbers, hyphens, or underscores; start with a letter or number.") }
                return
            }
            baseUrl != null && !ProfileNameRules.isValidBaseUrl(baseUrl) -> {
                _state.update { it.copy(profileCreateError = "Base URL must start with http:// or https://.") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(isCreatingProfile = true, profileCreateError = null, error = null, notice = null) }
            runCatching {
                repository.createProfile(
                    name = name,
                    cloneConfig = cloneConfig,
                    defaultModel = defaultModel,
                    modelProvider = modelProvider?.takeUnless { it == "default" },
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                )
            }
                .onSuccess { response ->
                    val responseError = response.error?.trim()?.takeIf { it.isNotBlank() }
                    if (responseError != null) {
                        _state.update {
                            it.copy(
                                isCreatingProfile = false,
                                profileCreateError = responseError,
                                error = responseError,
                            )
                        }
                        return@onSuccess
                    }
                    val created = response.profile
                    _state.update { current ->
                        val profiles = if (created != null && current.profiles.none { it.normalizedProfileName() == created.normalizedProfileName() }) {
                            current.profiles + created
                        } else {
                            current.profiles
                        }
                        current.copy(
                            isCreatingProfile = false,
                            showCreateProfileDialog = false,
                            profiles = profiles,
                            profileCreateError = null,
                            notice = "Profile created.",
                            error = null,
                        )
                    }
                    loadServerSettings()
                }
                .onFailure { error ->
                    val message = error.message ?: "Could not create profile."
                    _state.update { it.copy(isCreatingProfile = false, profileCreateError = message, error = message) }
                }
        }
    }

    private fun refreshLiveModels() {
        val repository = panelsRepository ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingLiveModels = true) }
            runCatching { repository.modelsLive() }
                .onSuccess { live ->
                    _state.update { it.copy(isLoadingLiveModels = false, models = overlayLiveModels(it.models, live)) }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingLiveModels = false) }
                }
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

const val DefaultServerHeaderColorHex = "#FFD700"

val ServerHeaderColorPresets = listOf(
    DefaultServerHeaderColorHex,
    "#5B7CFF",
    "#AF52DE",
    "#FF3B30",
    "#34C759",
    "#FFFFFF",
)

fun normalizedInitials(value: String): String =
    value.filter { it.isLetterOrDigit() }.take(3).uppercase()

fun displayInitials(
    displayName: String,
    storedInitials: String,
    fallbackFullName: String,
): String {
    normalizedInitials(storedInitials).takeIf { it.isNotBlank() }?.let { return it }
    val words = Regex("[A-Za-z0-9]+")
        .findAll(displayName.ifBlank { fallbackFullName })
        .map { it.value }
        .toList()
    val initials = when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().take(2)
        else -> "HX"
    }
    return normalizedInitials(initials).ifBlank { "HX" }
}

fun normalizedHeaderColorHex(hex: String): String {
    val raw = hex.trim().removePrefix("#").uppercase()
    return if (raw.matches(Regex("[0-9A-F]{6}"))) "#$raw" else DefaultServerHeaderColorHex
}
