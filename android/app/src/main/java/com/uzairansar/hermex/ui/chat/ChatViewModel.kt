package com.uzairansar.hermex.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.AgentCommand
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.BackgroundResult
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.CompressionAnchorResolver
import com.uzairansar.hermex.core.model.CompressionReferenceCard
import com.uzairansar.hermex.core.model.ContextWindowSnapshot
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.MessageActionContext
import com.uzairansar.hermex.core.model.MessageActionContextResolver
import com.uzairansar.hermex.core.model.MessageActionRole
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.PendingApproval
import com.uzairansar.hermex.core.model.PendingClarification
import com.uzairansar.hermex.core.model.PersonalitySummary
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ProfilesResponse
import com.uzairansar.hermex.core.model.SessionStatusResponse
import com.uzairansar.hermex.core.model.SkillSummary
import com.uzairansar.hermex.core.model.ToolCallGroup
import com.uzairansar.hermex.core.model.ToolCallGroupResolver
import com.uzairansar.hermex.core.model.TranscriptMediaReference
import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.core.model.WorkspacesResponse
import com.uzairansar.hermex.core.model.compressionAnchorMetadata
import com.uzairansar.hermex.core.model.contextWindowSnapshot
import com.uzairansar.hermex.core.network.SseEvent
import com.uzairansar.hermex.data.repository.ChatSessionSnapshot
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.ResultState
import com.uzairansar.hermex.data.share.SharedAttachment
import com.uzairansar.hermex.data.share.SharedDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

private data class SessionActionResult(
    val error: String? = null,
    val notice: String? = null,
)

private data class QueuedDraft(
    val text: String,
    val attachments: List<UploadResponse>,
)

private data class ComposerConfig(
    val models: List<ModelSummary>,
    val profiles: ProfilesResponse,
    val workspaces: WorkspacesResponse,
    val skillSuggestions: List<SlashSkillSuggestion>,
    val agentCommands: List<AgentCommand>,
)

enum class ActiveStreamRecoveryState(val label: String) {
    Idle("Stream active"),
    Checking("Checking stream"),
    Reconnecting("Reconnecting stream"),
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val messagesOffset: Int = 0,
    val hasOlderMessages: Boolean = false,
    val compressionReferenceCard: CompressionReferenceCard? = null,
    val completedToolCallGroups: List<ToolCallGroup> = emptyList(),
    val draft: String = "",
    val modelOptions: List<ModelSummary> = emptyList(),
    val agentCommands: List<AgentCommand> = emptyList(),
    val profileOptions: List<ProfileSummary> = emptyList(),
    val reasoningOptions: List<String> = ReasoningEffortOption.optionsForSupportedEfforts(null).map { it.id },
    val supportedReasoningEfforts: List<String>? = null,
    val supportsReasoningEffort: Boolean? = null,
    val workspaceRoots: List<WorkspaceRoot> = emptyList(),
    val workspaceSuggestions: List<String> = emptyList(),
    val skillSuggestions: List<SlashSkillSuggestion> = emptyList(),
    val selectedModel: ModelSummary? = null,
    val selectedProfile: ProfileSummary? = null,
    val activeProfileName: String? = null,
    val isSingleProfileMode: Boolean = false,
    val selectedReasoning: String? = null,
    val selectedWorkspacePath: String? = null,
    val sessionModel: String? = null,
    val sessionModelProvider: String? = null,
    val pendingExplicitModelPick: Boolean = false,
    val sessionTitle: String? = null,
    val sessionWorkspacePath: String? = null,
    val sessionProfile: String? = null,
    val contextWindowSnapshot: ContextWindowSnapshot? = null,
    val pendingAttachments: List<UploadResponse> = emptyList(),
    val pendingApproval: PendingApproval? = null,
    val pendingApprovalCount: Int = 0,
    val isSessionApprovalBypassEnabled: Boolean = false,
    val pendingClarification: PendingClarification? = null,
    val pendingClarificationCount: Int = 0,
    val clarificationDraft: String = "",
    val isRespondingToPendingPrompt: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingComposerConfig: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isUploadingAttachment: Boolean = false,
    val isRecordingVoiceNote: Boolean = false,
    val voiceNoteStartedAtMillis: Long? = null,
    val isTranscribingVoiceNote: Boolean = false,
    val isRunningSessionAction: Boolean = false,
    val isRegeneratingMessage: Boolean = false,
    val isEditingMessage: Boolean = false,
    val isForkingMessage: Boolean = false,
    val isStreaming: Boolean = false,
    val activeStreamRecoveryState: ActiveStreamRecoveryState = ActiveStreamRecoveryState.Idle,
    val activeStreamId: String? = null,
    val responseCompletionTrigger: Int = 0,
    val responseCompletionNeedsTranscriptRefresh: Boolean = false,
    val isViewingCachedData: Boolean = false,
    val liveReasoning: String = "",
    val liveToolActivity: String? = null,
    val openSessionId: String? = null,
    val notice: String? = null,
    val error: String? = null,
) {
    val isRecoveringStream: Boolean
        get() = activeStreamRecoveryState != ActiveStreamRecoveryState.Idle

    val activeStreamRecoveryLabel: String?
        get() = activeStreamRecoveryState.takeUnless { it == ActiveStreamRecoveryState.Idle }?.label

    val showsReasoningControl: Boolean
        get() = ReasoningEffortOption.showsEffortControl(
            supportsReasoningEffort = supportsReasoningEffort,
            supportedEfforts = supportedReasoningEfforts,
        )

    val showsProfileControl: Boolean
        get() = profileOptions.isNotEmpty() && !isSingleProfileMode
}

class ChatViewModel(
    private val sessionId: String,
    private val repository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    private var streamJob: Job? = null
    private var streamRecoveryJob: Job? = null
    private var btwJob: Job? = null
    private var backgroundPollJob: Job? = null
    private var pendingPromptJob: Job? = null
    private var workspaceSuggestionsJob: Job? = null
    private var workspaceSuggestionsGeneration = 0L
    private val backgroundPromptsByTaskId = mutableMapOf<String, String>()
    private val queuedSlashMessages = ArrayDeque<QueuedDraft>()
    private var isDrainingQueuedSlashMessage = false

    init {
        load()
        drainQueuedSlashMessageIfIdle()
        loadComposerConfig()
        refreshApprovalBypassState()
    }

    fun updateDraft(value: String) = _state.update { it.copy(draft = value, error = null, notice = null) }
    fun updateClarificationDraft(value: String) = _state.update { it.copy(clarificationDraft = value, error = null) }
    fun consumeOpenSession() = _state.update { it.copy(openSessionId = null) }

    override fun onCleared() {
        streamJob?.cancel()
        streamRecoveryJob?.cancel()
        btwJob?.cancel()
        backgroundPollJob?.cancel()
        pendingPromptJob?.cancel()
        workspaceSuggestionsJob?.cancel()
        super.onCleared()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.loadSessionSnapshot(sessionId)) {
                is ResultState.Data -> {
                    applySessionSnapshot(result.value, fromCache = result.fromCache) {
                        it.copy(isLoading = false)
                    }
                    refreshReasoningForModel(_state.value.selectedModel, reportError = false)
                    reconnectLoadedActiveStream(result.value, fromCache = result.fromCache)
                }
                is ResultState.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                ResultState.Loading -> Unit
            }
        }
    }

    private fun applySessionSnapshot(
        snapshot: ChatSessionSnapshot,
        fromCache: Boolean? = null,
        transform: (ChatUiState) -> ChatUiState = { it },
    ) {
        _state.update { current ->
            val nextSessionModel = snapshot.model.nonBlank() ?: current.sessionModel
            val nextSessionModelProvider = snapshot.modelProvider.nonBlank() ?: current.sessionModelProvider
            val sessionModelSelection = current.modelOptions.firstMatchingModel(nextSessionModel, nextSessionModelProvider)
            transform(
                current.copy(
                    messages = snapshot.messages,
                    messagesOffset = snapshot.messagesOffset,
                    hasOlderMessages = snapshot.hasOlderMessages,
                    compressionReferenceCard = snapshot.compressionReferenceCard,
                    completedToolCallGroups = snapshot.completedToolCallGroups,
                    contextWindowSnapshot = snapshot.contextWindowSnapshot ?: current.contextWindowSnapshot,
                    sessionTitle = snapshot.title.nonBlank() ?: current.sessionTitle,
                    sessionWorkspacePath = snapshot.workspace.nonBlank() ?: current.sessionWorkspacePath,
                    sessionProfile = snapshot.profile.nonBlank() ?: current.sessionProfile,
                    sessionModel = nextSessionModel,
                    sessionModelProvider = nextSessionModelProvider,
                    selectedModel = when {
                        current.pendingExplicitModelPick -> current.selectedModel
                        sessionModelSelection != null -> sessionModelSelection
                        current.selectedModel == null -> nextSessionModel?.let { ModelSummary(id = it, name = it, label = it, provider = nextSessionModelProvider) }
                        else -> current.selectedModel
                    },
                    selectedWorkspacePath = snapshot.workspace.nonBlank() ?: current.selectedWorkspacePath,
                    isViewingCachedData = fromCache ?: current.isViewingCachedData,
                    activeStreamId = snapshot.activeStreamId,
                    isStreaming = snapshot.isStreaming,
                    activeStreamRecoveryState = if (
                        current.isRecoveringStream &&
                        snapshot.isStreaming &&
                        current.activeStreamId == snapshot.activeStreamId
                    ) {
                        current.activeStreamRecoveryState
                    } else {
                        ActiveStreamRecoveryState.Idle
                    },
                ),
            )
        }
    }

    private fun reconnectLoadedActiveStream(snapshot: ChatSessionSnapshot, fromCache: Boolean) {
        val streamId = snapshot.activeStreamId?.takeIf { it.isNotBlank() }
        if (fromCache || streamId == null || !snapshot.isStreaming) {
            return
        }
        if (_state.value.activeStreamId == streamId && streamJob?.isActive == true) {
            return
        }
        attachStream(streamId)
        startPendingPromptPolling()
    }

    fun loadOlderMessages() {
        val state = _state.value
        if (state.isLoadingOlderMessages || !state.hasOlderMessages) return
        if (state.messagesOffset <= 0) {
            _state.update { it.copy(hasOlderMessages = false) }
            return
        }

        viewModelScope.launch {
            val before = _state.value.messagesOffset
            val currentMessages = _state.value.messages
            _state.update { it.copy(isLoadingOlderMessages = true, error = null) }
            runCatching {
                repository.loadOlderSessionSnapshot(
                    sessionId = sessionId,
                    before = before,
                    currentMessages = currentMessages,
                )
            }
                .onSuccess { snapshot ->
                    val previousStreamId = _state.value.activeStreamId
                    val previousIsStreaming = _state.value.isStreaming
                    applySessionSnapshot(snapshot, fromCache = false) {
                        it.copy(
                            isLoadingOlderMessages = false,
                            activeStreamId = snapshot.activeStreamId ?: previousStreamId,
                            isStreaming = snapshot.isStreaming || previousIsStreaming,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoadingOlderMessages = false,
                            error = error.message ?: "Could not load older messages.",
                        )
                    }
                }
        }
    }

    fun loadComposerConfig() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingComposerConfig = true) }
            runCatching {
                val models = repository.models()
                val profiles = repository.profilesResponse()
                val workspaces = repository.workspaces()
                val skills = try {
                    repository.skills()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    emptyList()
                }
                val commands = runCatching { repository.commands() }.getOrDefault(emptyList())
                val skillSuggestions = SlashSkillFormatter.suggestions(
                    skills.map { skill ->
                        SlashSkillDefinition(
                            name = skill.name,
                            category = skill.category,
                            description = skill.description,
                            enabled = skill.enabled,
                            disabled = skill.disabled,
                        )
                    },
                )
                ComposerConfig(models, profiles, workspaces, skillSuggestions, commands)
            }.onSuccess { config ->
                val workspaceRoots = config.workspaces.normalizedRoots
                val profileOptions = config.profiles.profiles.orEmpty()
                val activeProfileName = config.profiles.active.nonBlank()
                _state.update {
                    val sessionModelSelection = config.models.firstMatchingModel(it.sessionModel, it.sessionModelProvider)
                    it.copy(
                        modelOptions = config.models,
                        agentCommands = config.agentCommands,
                        profileOptions = profileOptions,
                        activeProfileName = activeProfileName,
                        isSingleProfileMode = config.profiles.singleProfileMode == true,
                        workspaceRoots = workspaceRoots,
                        workspaceSuggestions = workspaceRoots.mapNotNull { root -> root.path },
                        skillSuggestions = config.skillSuggestions,
                        selectedModel = when {
                            it.pendingExplicitModelPick -> it.selectedModel ?: sessionModelSelection ?: config.models.firstOrNull()
                            sessionModelSelection != null -> sessionModelSelection
                            it.selectedModel != null -> it.selectedModel
                            it.sessionModel != null -> ModelSummary(
                                id = it.sessionModel,
                                name = it.sessionModel,
                                label = it.sessionModel,
                                provider = it.sessionModelProvider,
                            )
                            else -> config.models.firstOrNull()
                        },
                        selectedProfile = it.selectedProfile
                            ?: profileOptions.firstMatchingProfile(it.sessionProfile)
                            ?: profileOptions.firstMatchingProfile(activeProfileName)
                            ?: profileOptions.firstOrNull(),
                        sessionProfile = it.sessionProfile ?: activeProfileName,
                        selectedWorkspacePath = it.selectedWorkspacePath
                            ?: config.workspaces.last.nonBlank()
                            ?: workspaceRoots.firstNotNullOfOrNull { root -> root.path.nonBlank() },
                        isLoadingComposerConfig = false,
                    )
                }
                refreshReasoningForModel(_state.value.selectedModel, reportError = false)
            }.onFailure {
                _state.update { current -> current.copy(isLoadingComposerConfig = false) }
            }
        }
    }

    fun cycleModel() {
        val state = _state.value
        val next = state.modelOptions.nextAfter(state.selectedModel)
        if (next != null) selectModel(next)
    }

    fun cycleProfile() {
        val state = _state.value
        if (!state.showsProfileControl) return
        val next = state.profileOptions.nextAfter(state.selectedProfile)
        if (next != null) selectProfile(next)
    }

    fun cycleReasoning() {
        val state = _state.value
        if (!state.showsReasoningControl) return
        val next = state.reasoningOptions.nextAfter(state.selectedReasoning) ?: return
        selectReasoning(next)
    }

    fun selectModel(model: ModelSummary) {
        val snapshot = _state.value
        if (snapshot.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to change models.") }
            return
        }
        if (snapshot.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before changing models.") }
            return
        }
        val isExplicitPick = !model.matchesModelIdentity(snapshot.sessionModel, snapshot.sessionModelProvider)
        val previousModel = snapshot.selectedModel
        val previousPendingExplicit = snapshot.pendingExplicitModelPick
        val previousSessionModel = snapshot.sessionModel
        val previousSessionModelProvider = snapshot.sessionModelProvider
        _state.update {
            it.copy(
                selectedModel = model,
                pendingExplicitModelPick = isExplicitPick,
                isRunningSessionAction = isExplicitPick,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                if (isExplicitPick) {
                    repository.updateSessionConfiguration(sessionId, snapshot.selectedWorkspacePath, model)
                } else {
                    null
                }
            }.onSuccess { config ->
                val resolvedSessionModel = config?.model ?: snapshot.sessionModel ?: model.modelIdentity
                val resolvedSessionProvider = config?.modelProvider ?: snapshot.sessionModelProvider ?: model.provider
                val resolvedModel = _state.value.modelOptions.firstMatchingModel(resolvedSessionModel, resolvedSessionProvider) ?: model
                _state.update {
                    it.copy(
                        selectedModel = resolvedModel,
                        sessionModel = resolvedSessionModel,
                        sessionModelProvider = resolvedSessionProvider,
                        sessionWorkspacePath = config?.workspace ?: it.sessionWorkspacePath,
                        selectedWorkspacePath = config?.workspace ?: it.selectedWorkspacePath,
                        pendingExplicitModelPick = isExplicitPick,
                        isRunningSessionAction = false,
                        notice = "Model set to ${resolvedModel.label ?: resolvedModel.name ?: resolvedModel.id}.",
                    )
                }
                refreshReasoningForModel(resolvedModel)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        selectedModel = previousModel,
                        pendingExplicitModelPick = previousPendingExplicit,
                        sessionModel = previousSessionModel,
                        sessionModelProvider = previousSessionModelProvider,
                        isRunningSessionAction = false,
                        error = error.message ?: "Could not switch models.",
                    )
                }
            }
        }
    }

    fun selectProfile(profile: ProfileSummary) {
        if (!_state.value.showsProfileControl) return
        val profileName = profile.name ?: profile.displayName
        _state.update {
            it.copy(
                selectedProfile = profile,
                activeProfileName = profileName,
                sessionProfile = profileName,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.switchProfile(profile) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch profile.") } }
        }
    }

    fun selectReasoning(effort: String) {
        if (effort.isBlank()) return
        val state = _state.value
        if (!state.showsReasoningControl) return
        val model = state.selectedModel
        _state.update { it.copy(selectedReasoning = effort, error = null) }
        viewModelScope.launch {
            runCatching { repository.setReasoning(effort, model) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch reasoning.") } }
        }
    }

    private suspend fun refreshReasoningForModel(model: ModelSummary?, reportError: Boolean = true) {
        runCatching { repository.reasoning(model) }.onSuccess { reasoning ->
            val currentModel = _state.value.selectedModel
            if (
                model?.modelIdentity != currentModel?.modelIdentity ||
                model?.provider.nonBlank() != currentModel?.provider.nonBlank()
            ) {
                return@onSuccess
            }
            val supportedReasoningEfforts = reasoning.normalizedSupportedEfforts
            _state.update {
                it.copy(
                    reasoningOptions = ReasoningEffortOption.optionsForSupportedEfforts(supportedReasoningEfforts).map { option -> option.id },
                    supportedReasoningEfforts = supportedReasoningEfforts,
                    supportsReasoningEffort = reasoning.supportsReasoningEffort,
                    selectedReasoning = reasoning.effectiveEffort ?: it.selectedReasoning,
                )
            }
        }.onFailure { error ->
            if (reportError) {
                _state.update { it.copy(error = error.message ?: "Could not load reasoning options.") }
            }
        }
    }

    fun selectWorkspace(path: String) {
        val workspace = path.nonBlank() ?: return
        val snapshot = _state.value
        if (snapshot.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to change workspace.") }
            return
        }
        if (snapshot.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before changing workspace.") }
            return
        }
        val previousWorkspace = snapshot.selectedWorkspacePath
        val previousSessionWorkspace = snapshot.sessionWorkspacePath
        val previousSessionModel = snapshot.sessionModel
        val previousSessionModelProvider = snapshot.sessionModelProvider
        _state.update {
            it.copy(
                selectedWorkspacePath = workspace,
                sessionWorkspacePath = workspace,
                isRunningSessionAction = true,
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.updateSessionConfiguration(sessionId, workspace, snapshot.selectedModel)
            }.onSuccess { config ->
                val resolvedWorkspace = config.workspace ?: workspace
                val resolvedSessionModel = config.model ?: previousSessionModel
                val resolvedSessionProvider = config.modelProvider ?: previousSessionModelProvider
                val resolvedModel = _state.value.modelOptions.firstMatchingModel(resolvedSessionModel, resolvedSessionProvider)
                    ?: _state.value.selectedModel
                _state.update {
                    it.copy(
                        selectedWorkspacePath = resolvedWorkspace,
                        sessionWorkspacePath = resolvedWorkspace,
                        sessionModel = resolvedSessionModel,
                        sessionModelProvider = resolvedSessionProvider,
                        selectedModel = resolvedModel,
                        isRunningSessionAction = false,
                        notice = "Workspace set to ${resolvedWorkspace.lastPathComponentFallback()}.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        selectedWorkspacePath = previousWorkspace,
                        sessionWorkspacePath = previousSessionWorkspace,
                        sessionModel = previousSessionModel,
                        sessionModelProvider = previousSessionModelProvider,
                        isRunningSessionAction = false,
                        error = error.message ?: "Could not change workspace.",
                    )
                }
            }
        }
    }

    fun loadWorkspaceSuggestions(prefix: String) {
        val query = prefix.trim()
        val generation = ++workspaceSuggestionsGeneration
        workspaceSuggestionsJob?.cancel()
        workspaceSuggestionsJob = viewModelScope.launch {
            if (query.isBlank()) {
                if (generation == workspaceSuggestionsGeneration) {
                    _state.update { state -> state.copy(workspaceSuggestions = state.workspaceRoots.mapNotNull { it.path }) }
                }
                return@launch
            }
            try {
                val suggestions = repository.workspaceSuggestions(query)
                if (generation == workspaceSuggestionsGeneration) {
                    _state.update { it.copy(workspaceSuggestions = suggestions) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == workspaceSuggestionsGeneration) {
                    _state.update { it.copy(error = error.message ?: "Could not load workspace suggestions.") }
                }
            }
        }
    }

    fun attach(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAttachment = true, error = null) }
            runCatching {
                val file = copyUriToCache(context, uri)
                repository.upload(sessionId, file, context.contentResolver.getType(uri))
            }.onSuccess { upload ->
                _state.update { it.copy(pendingAttachments = it.pendingAttachments + upload, isUploadingAttachment = false) }
            }.onFailure { error ->
                _state.update { it.copy(isUploadingAttachment = false, error = error.message ?: "Upload failed.") }
            }
        }
    }

    fun consumeSharedDraft(context: Context, draft: SharedDraft) {
        viewModelScope.launch {
            val sharedText = draft.text.trim()
            val attachments = draft.attachments.ifEmpty {
                draft.uris.map { SharedAttachment(uri = it) }
            }
            if (sharedText.isNotBlank()) {
                _state.update { state ->
                    val separator = if (state.draft.isBlank() || state.draft.endsWith("\n")) "" else "\n\n"
                    state.copy(
                        draft = "${state.draft}$separator$sharedText",
                        notice = "Shared text added to the draft.",
                        error = null,
                    )
                }
            }
            if (attachments.isEmpty()) return@launch

            _state.update { it.copy(isUploadingAttachment = true, error = null) }
            val uploaded = mutableListOf<UploadResponse>()
            val failures = mutableListOf<String>()
            attachments.forEach { attachment ->
                runCatching { uploadSharedAttachment(context, attachment) }
                    .onSuccess { upload -> uploaded += upload }
                    .onFailure { error -> failures += (error.message ?: "Shared attachment upload failed.") }
            }
            _state.update {
                it.copy(
                    pendingAttachments = it.pendingAttachments + uploaded,
                    isUploadingAttachment = false,
                    notice = when {
                        uploaded.isNotEmpty() && sharedText.isNotBlank() -> "Shared text and ${uploaded.size} attachment(s) added."
                        uploaded.isNotEmpty() -> "${uploaded.size} shared attachment(s) added."
                        else -> it.notice
                    },
                    error = failures.takeIf { failureList -> failureList.isNotEmpty() }?.joinToString("\n"),
                )
            }
        }
    }

    fun removeAttachment(upload: UploadResponse) {
        _state.update { it.copy(pendingAttachments = it.pendingAttachments - upload) }
    }

    fun startVoiceNote(recorder: VoiceNoteRecorder) {
        runCatching { recorder.start() }
            .onSuccess {
                _state.update {
                    it.copy(
                        isRecordingVoiceNote = true,
                        voiceNoteStartedAtMillis = System.currentTimeMillis(),
                        error = null,
                    )
                }
            }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not start recording.") } }
    }

    fun stopAndTranscribeVoiceNote(recorder: VoiceNoteRecorder) {
        val file = recorder.stop()
        _state.update { it.copy(isRecordingVoiceNote = false, voiceNoteStartedAtMillis = null) }
        if (file == null) {
            _state.update { it.copy(error = "Voice note was empty.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTranscribingVoiceNote = true, error = null) }
            runCatching { repository.transcribe(file) }
                .onSuccess { response ->
                    val transcript = response.transcript?.trim().orEmpty()
                    if (response.error != null || transcript.isEmpty()) {
                        _state.update {
                            it.copy(
                                isTranscribingVoiceNote = false,
                                error = response.error ?: "The server did not return a transcript.",
                            )
                        }
                    } else {
                        _state.update {
                            val separator = if (it.draft.isBlank() || it.draft.endsWith("\n")) "" else "\n"
                            it.copy(
                                draft = "${it.draft}$separator$transcript",
                                isTranscribingVoiceNote = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isTranscribingVoiceNote = false, error = error.message ?: "Could not transcribe voice note.") }
                }
        }
    }

    fun cancelVoiceNote(recorder: VoiceNoteRecorder) {
        recorder.stop(delete = true)
        _state.update { it.copy(isRecordingVoiceNote = false, voiceNoteStartedAtMillis = null, isTranscribingVoiceNote = false) }
    }

    suspend fun synthesizeSpeech(text: String): ByteArray? =
        runCatching { repository.synthesizeSpeech(text) }.getOrNull()

    suspend fun transcriptMediaThumbnailData(reference: TranscriptMediaReference): ByteArray? =
        runCatching { repository.transcriptMediaData(reference) }.getOrNull()

    suspend fun attachmentImageData(path: String): ByteArray? =
        transcriptMediaThumbnailData(TranscriptMediaReference(path))

    suspend fun attachmentTextFile(path: String): FileResponse? =
        runCatching { repository.attachmentFile(sessionId, path) }.getOrNull()

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        val snapshot = _state.value
        if (handleSlashCommand(text, snapshot)) return
        if (_state.value.isStreaming) return
        viewModelScope.launch {
            submitMessage(text, snapshot)
        }
    }

    fun steerDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(error = "Enter steering text.") }
            return
        }
        steer(text)
    }

    fun submitStreamingDraft(behavior: StreamingSendBehavior) {
        val snapshot = _state.value
        val text = snapshot.draft.trim()
        if (text.isEmpty()) return
        if (!snapshot.isStreaming) {
            send()
            return
        }
        when (behavior) {
            StreamingSendBehavior.Steer -> steer(text)
            StreamingSendBehavior.Queue -> queueDraft(text, snapshot)
            StreamingSendBehavior.Interrupt -> {
                viewModelScope.launch {
                    cancelActiveStream()
                    submitMessage(
                        text = text,
                        snapshot = snapshot.copy(
                            draft = text,
                            isStreaming = false,
                            activeStreamId = null,
                        ),
                    )
                }
            }
        }
    }

    fun undoLastExchange() {
        sessionAction("Undo is available after the current response finishes.") {
            val response = repository.undoSession(sessionId)
            if (response.error != null) {
                SessionActionResult(error = response.error)
            } else {
                load()
                SessionActionResult(notice = "Undid ${response.removedCount ?: 1} message(s).")
            }
        }
    }

    fun retryLastTurn() {
        if (_state.value.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before retrying.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, error = null, notice = null) }
            runCatching { repository.retrySession(sessionId) }
                .onSuccess { response ->
                    if (response.error != null) {
                        _state.update { it.copy(isRunningSessionAction = false, error = response.error) }
                        return@onSuccess
                    }
                    val lastUserText = response.lastUserText?.trim().orEmpty()
                    if (lastUserText.isBlank()) {
                        _state.update { it.copy(isRunningSessionAction = false, error = "The server did not return a message to retry.") }
                        return@onSuccess
                    }
                    _state.update { it.copy(isRunningSessionAction = false) }
                    load()
                    submitMessage(lastUserText, _state.value.copy(pendingAttachments = emptyList()))
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not retry the last turn.") }
                }
        }
    }

    fun forkFromMessage(context: MessageActionContext) {
        val state = _state.value
        if (state.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to fork a conversation.") }
            return
        }
        if (state.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before forking.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRunningSessionAction = true,
                    isForkingMessage = true,
                    draft = "",
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                repository.branchSession(
                    sessionId = sessionId,
                    keepCount = context.keepCountThroughMessage,
                )
            }
                .onSuccess { result ->
                    val branchId = result.session?.sessionId
                    if (branchId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                isForkingMessage = false,
                                error = result.errorMessage ?: "Could not fork the session.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                isForkingMessage = false,
                                notice = "Forked session created.",
                                openSessionId = branchId,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            isForkingMessage = false,
                            error = error.message ?: "Could not fork the session.",
                        )
                    }
                }
        }
    }

    fun editMessage(context: MessageActionContext, newText: String) {
        if (context.role != MessageActionRole.User) {
            _state.update { it.copy(error = "Only user messages can be edited.") }
            return
        }
        val editedText = newText.trim()
        if (editedText.isBlank()) {
            _state.update { it.copy(error = "The edited message cannot be empty.") }
            return
        }
        val state = _state.value
        if (state.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to edit a message.") }
            return
        }
        if (state.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before editing.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRunningSessionAction = true,
                    isEditingMessage = true,
                    draft = "",
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.truncateSessionSnapshot(sessionId, context.fullHistoryIndex) }
                .onSuccess { snapshot ->
                    applySessionSnapshot(snapshot) {
                        it.copy(
                            isRunningSessionAction = false,
                            isEditingMessage = false,
                        )
                    }
                    submitMessage(
                        text = editedText,
                        snapshot = _state.value.copy(
                            draft = editedText,
                            pendingAttachments = emptyList(),
                            isStreaming = false,
                        ),
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            isEditingMessage = false,
                            error = error.message ?: "Could not edit the message.",
                        )
                    }
                }
        }
    }

    fun regenerateAssistantResponse(context: MessageActionContext) {
        if (context.role != MessageActionRole.Assistant) {
            _state.update { it.copy(error = "Only assistant messages can be regenerated.") }
            return
        }
        val state = _state.value
        if (state.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to regenerate a response.") }
            return
        }
        if (state.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before regenerating.") }
            return
        }
        val userText = MessageActionContextResolver.precedingUserMessageText(
            messages = state.messages,
            beforeVisibleIndex = context.visibleIndex,
        )
        if (userText.isNullOrBlank()) {
            _state.update { it.copy(error = "Load older messages before regenerating this response.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRunningSessionAction = true,
                    isRegeneratingMessage = true,
                    draft = "",
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.truncateSessionSnapshot(sessionId, context.fullHistoryIndex) }
                .onSuccess { snapshot ->
                    applySessionSnapshot(snapshot) {
                        it.copy(
                            isRunningSessionAction = false,
                            isRegeneratingMessage = false,
                        )
                    }
                    submitMessage(
                        text = userText,
                        snapshot = _state.value.copy(
                            draft = userText,
                            pendingAttachments = emptyList(),
                            isStreaming = false,
                        ),
                        appendOptimisticUser = false,
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            isRegeneratingMessage = false,
                            error = error.message ?: "Could not regenerate the response.",
                        )
                    }
                }
        }
    }

    fun compressContext(focusTopic: String? = null) {
        sessionAction("Wait for the current response to finish before compressing context.") {
            val response = repository.compressSession(sessionId, focusTopic?.trim()?.ifBlank { null })
            if (response.error != null) {
                SessionActionResult(error = response.error)
            } else {
                response.session?.let { session ->
                    val messages = session.messages.orEmpty()
                    val messagesOffset = session.messagesOffset ?: 0
                    _state.update {
                        it.copy(
                            messages = messages,
                            messagesOffset = messagesOffset,
                            compressionReferenceCard = CompressionAnchorResolver.resolve(
                                messages = messages,
                                messagesOffset = messagesOffset,
                                metadata = session.compressionAnchorMetadata(),
                            ),
                            completedToolCallGroups = ToolCallGroupResolver.groups(
                                messages = messages,
                                messagesOffset = messagesOffset,
                                persistedToolCalls = session.toolCalls,
                            ),
                            contextWindowSnapshot = session.contextWindowSnapshot() ?: it.contextWindowSnapshot,
                            sessionTitle = session.title.nonBlank() ?: it.sessionTitle,
                            sessionWorkspacePath = session.workspace.nonBlank() ?: it.sessionWorkspacePath,
                            sessionProfile = session.profile.nonBlank() ?: it.sessionProfile,
                            selectedWorkspacePath = session.workspace.nonBlank() ?: it.selectedWorkspacePath,
                            isViewingCachedData = false,
                        )
                    }
                } ?: load()
                SessionActionResult(
                    notice = listOfNotNull(
                        "Context compressed.",
                        response.summary?.headline,
                        response.summary?.tokenLine,
                        response.focusTopic?.let { "Focus: $it" },
                    ).joinToString("\n"),
                )
            }
        }
    }

    private suspend fun submitMessage(
        text: String,
        snapshot: ChatUiState,
        appendOptimisticUser: Boolean = true,
    ): Boolean {
            var sent = false
            _state.update {
                val optimisticMessages = if (appendOptimisticUser) {
                    it.messages + ChatMessage(role = "user", content = text)
                } else {
                    it.messages
                }
                it.copy(
                    messages = optimisticMessages,
                    draft = "",
                    pendingAttachments = emptyList(),
                    isStreaming = true,
                    error = null,
                )
            }
            runCatching {
                val explicitModelPick = snapshot.pendingExplicitModelPick && snapshot.selectedModel?.modelIdentity != null
                snapshot.selectedReasoning
                    ?.takeIf { snapshot.showsReasoningControl }
                    ?.let { repository.setReasoning(it, snapshot.selectedModel) }
                snapshot.selectedProfile?.let { repository.switchProfile(it) }
                repository.send(
                    sessionId,
                    text,
                    model = snapshot.selectedModel,
                    profile = snapshot.selectedProfile,
                    profileName = snapshot.selectedProfile?.name
                        ?: snapshot.selectedProfile?.displayName
                        ?: snapshot.sessionProfile
                        ?: snapshot.activeProfileName,
                    explicitModelPick = explicitModelPick,
                    attachments = snapshot.pendingAttachments,
                    workspace = snapshot.selectedWorkspacePath,
                )
            }
                .onSuccess { streamId ->
                    if (streamId.isNullOrBlank()) {
                        _state.update { it.copy(isStreaming = false, error = "Server did not return a stream id.") }
                        drainQueuedSlashMessageIfIdle()
                    } else {
                        _state.update {
                            it.copy(
                                activeStreamId = streamId,
                                pendingExplicitModelPick = if (snapshot.pendingExplicitModelPick) false else it.pendingExplicitModelPick,
                            )
                        }
                        attachStream(streamId)
                        startPendingPromptPolling()
                        sent = true
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isStreaming = false, error = error.message ?: "Send failed.") }
                    drainQueuedSlashMessageIfIdle()
                }
            return sent
    }

    fun cancel() {
        viewModelScope.launch {
            cancelActiveStream()
        }
    }

    fun clearConversation() {
        val snapshot = _state.value
        if (snapshot.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to clear this conversation.") }
            return
        }
        if (snapshot.isRunningSessionAction) return

        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, error = null, notice = null) }
            runCatching { repository.clearSessionSnapshot(sessionId) }
                .onSuccess { result ->
                    val clearedSnapshot = result.snapshot
                    if (result.error != null || clearedSnapshot == null) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = result.error ?: "The server did not return the cleared session.",
                            )
                        }
                        return@onSuccess
                    }

                    discardActiveStreamAfterSessionClear()
                    backgroundPollJob?.cancel()
                    backgroundPollJob = null
                    backgroundPromptsByTaskId.clear()
                    queuedSlashMessages.clear()
                    isDrainingQueuedSlashMessage = false
                    applySessionSnapshot(clearedSnapshot, fromCache = false) {
                        it.copy(
                            isRunningSessionAction = false,
                            draft = "",
                            pendingAttachments = emptyList(),
                            pendingApproval = null,
                            pendingApprovalCount = 0,
                            pendingClarification = null,
                            pendingClarificationCount = 0,
                            clarificationDraft = "",
                            isRespondingToPendingPrompt = false,
                            isStreaming = false,
                            activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                            activeStreamId = null,
                            responseCompletionNeedsTranscriptRefresh = false,
                            liveReasoning = "",
                            liveToolActivity = null,
                            sessionTitle = clearedSnapshot.title.nonBlank() ?: "Untitled",
                            error = null,
                            notice = "Conversation cleared.",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            error = error.message ?: "Could not clear conversation.",
                        )
                    }
                }
        }
    }

    private suspend fun cancelActiveStream() {
        val streamId = _state.value.activeStreamId
        if (streamId != null) runCatching { repository.cancel(streamId) }
        if (streamId != null) repository.clearStreamCursor(streamId)
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        streamJob?.cancel()
        stopPendingPromptPolling(clearPrompts = true)
        _state.update {
            it.copy(
                isStreaming = false,
                activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                activeStreamId = null,
                liveToolActivity = null,
                pendingApproval = null,
                pendingClarification = null,
            )
        }
        drainQueuedSlashMessageIfIdle()
    }

    private fun discardActiveStreamAfterSessionClear() {
        _state.value.activeStreamId?.let(repository::clearStreamCursor)
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        streamJob?.cancel()
        streamJob = null
        btwJob?.cancel()
        btwJob = null
        stopPendingPromptPolling(clearPrompts = true)
    }

    private fun handleSlashCommand(text: String, snapshot: ChatUiState): Boolean {
        if (!text.startsWith("/")) return false
        val withoutSlash = text.drop(1).trimStart()
        val command = withoutSlash.substringBefore(' ').lowercase()
        val args = withoutSlash.substringAfter(' ', "").trim()
        if (
            command !in BUILTIN_SLASH_COMMAND_NAMES &&
            SlashSkillFormatter.skill(command, snapshot.skillSuggestions) != null
        ) {
            return false
        }
        when (command) {
            "help" -> {
                _state.update { it.copy(draft = "", notice = null, error = null) }
                appendLocalAssistant(slashHelpText(snapshot.agentCommands))
            }
            "clear" -> _state.update { it.copy(messages = emptyList(), draft = "", notice = "Transcript cleared locally.", error = null) }
            "stop" -> {
                _state.update { it.copy(draft = "") }
                cancel()
            }
            "new" -> createSessionFromSlashCommand()
            "title" -> renameSessionFromSlashCommand(args)
            "branch", "fork" -> branchSessionFromSlashCommand(args)
            "btw" -> askBtwFromSlashCommand(args)
            "background", "bg" -> startBackgroundFromSlashCommand(args)
            "skills" -> searchSkillsFromSlashCommand(args)
            "queue" -> queueMessageFromSlashCommand(args, snapshot)
            "steer" -> {
                if (args.isBlank()) _state.update { it.copy(error = "Usage: /steer <message>") } else steer(args)
            }
            "interrupt" -> {
                if (args.isBlank()) {
                    _state.update { it.copy(error = "Usage: /interrupt <message>") }
                } else {
                    viewModelScope.launch {
                        cancelActiveStream()
                        submitMessage(args, snapshot.copy(draft = args, isStreaming = false))
                    }
                }
            }
            "goal" -> submitGoal(args)
            "compress", "compact" -> compressContext(args)
            "undo" -> undoLastExchange()
            "retry" -> retryLastTurn()
            "model" -> switchModel(args)
            "profile" -> switchProfile(args)
            "personality" -> setPersonalityFromSlashCommand(args)
            "reasoning" -> switchReasoning(args)
            "workspace" -> switchWorkspace(args)
            "status" -> appendLocalAssistant(statusText())
            else -> {
                when {
                    isKnownUnsupportedSlashCommand(command) -> {
                        val message = unsupportedSlashCommandMessage(command)
                        _state.update { it.copy(draft = "", error = message) }
                        appendLocalAssistant(message)
                    }
                    command == "skill" -> {
                        val message = "Use `/skills [query]` to search skills."
                        _state.update { it.copy(draft = "", error = message) }
                        appendLocalAssistant(message)
                    }
                    else -> return false
                }
            }
        }
        return true
    }

    private fun steer(text: String) {
        if (!_state.value.isStreaming) {
            _state.update { it.copy(error = "No active response to steer.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, error = null, notice = null) }
            runCatching { repository.steer(sessionId, text) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            draft = "",
                            isRunningSessionAction = false,
                            notice = if (response.error == null) "Steering sent." else null,
                            error = response.error,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not steer the response.") }
                }
        }
    }

    private fun submitGoal(args: String) {
        if (args.isBlank()) {
            _state.update { it.copy(error = "Usage: /goal <goal text | status | pause | resume | clear>") }
            return
        }
        viewModelScope.launch {
            val snapshot = _state.value
            _state.update { it.copy(isRunningSessionAction = true, error = null, notice = null, draft = "") }
            runCatching { repository.submitGoal(sessionId, args, snapshot.selectedModel, snapshot.selectedProfile) }
                .onSuccess { response ->
                    val kickoff = response.kickoffPrompt?.trim().orEmpty()
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            notice = response.message ?: response.decision?.message ?: response.goal?.goal ?: "Goal updated.",
                            error = response.error,
                        )
                    }
                    if (response.error == null && kickoff.isNotBlank() && !_state.value.isStreaming) {
                        submitMessage(kickoff, _state.value.copy(pendingAttachments = emptyList()))
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not submit goal.") }
                }
        }
    }

    private fun switchModel(args: String) {
        val query = args.trim()
        val model = _state.value.modelOptions.firstOrNull {
            val values = listOfNotNull(it.id, it.name, it.label).map { value -> value.lowercase() }
            values.any { value -> value == query.lowercase() || value.contains(query.lowercase()) }
        }
        if (query.isBlank() || model == null) {
            _state.update { it.copy(error = "Model not found.") }
            return
        }
        _state.update { it.copy(draft = "") }
        selectModel(model)
    }

    private fun switchProfile(args: String) {
        val query = args.trim()
        val state = _state.value
        if (!state.showsProfileControl) {
            _state.update { it.copy(error = "Profile switching is not available on this server.") }
            return
        }
        val profile = state.profileOptions.firstOrNull {
            val values = listOfNotNull(it.name, it.displayName).map { value -> value.lowercase() }
            values.any { value -> value == query.lowercase() || value.contains(query.lowercase()) }
        }
        if (query.isBlank() || profile == null) {
            _state.update { it.copy(error = "Profile not found.") }
            return
        }
        _state.update {
            it.copy(
                draft = "",
                selectedProfile = profile,
                activeProfileName = profile.name ?: profile.displayName,
                sessionProfile = profile.name ?: profile.displayName,
                notice = "Profile set to ${profile.displayName ?: profile.name}.",
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.switchProfile(profile) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch profile.") } }
        }
    }

    private fun switchReasoning(args: String) {
        val query = args.trim()
        val state = _state.value
        if (!state.showsReasoningControl) {
            _state.update { it.copy(error = "Reasoning is not available for the selected model.") }
            return
        }
        val effort = state.reasoningOptions.firstOrNull { it.equals(query, ignoreCase = true) }
        if (query.isBlank() || effort == null) {
            _state.update { it.copy(error = "Reasoning level not found.") }
            return
        }
        val model = state.selectedModel
        _state.update { it.copy(draft = "", selectedReasoning = effort, notice = "Reasoning set to $effort.", error = null) }
        viewModelScope.launch {
            runCatching { repository.setReasoning(effort, model) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch reasoning.") } }
        }
    }

    private fun switchWorkspace(args: String) {
        val query = args.trim()
        val workspace = _state.value.workspaceRoots.firstNotNullOfOrNull { root ->
            val path = root.path.nonBlank() ?: return@firstNotNullOfOrNull null
            val name = root.name.nonBlank()
            val leaf = path.lastPathComponentFallback()
            if (
                path.equals(query, ignoreCase = true) ||
                leaf.equals(query, ignoreCase = true) ||
                name?.equals(query, ignoreCase = true) == true
            ) {
                path
            } else {
                null
            }
        } ?: query.nonBlank()
        if (workspace == null) {
            _state.update { it.copy(error = "Usage: /workspace <path>") }
            return
        }
        _state.update { it.copy(draft = "") }
        selectWorkspace(workspace)
    }

    private fun sessionAction(streamingMessage: String, action: suspend () -> SessionActionResult) {
        if (_state.value.isStreaming) {
            _state.update { it.copy(error = streamingMessage) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { action() }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            error = result.error,
                            notice = if (result.error == null) result.notice else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Session action failed.") }
                }
        }
    }

    private fun appendLocalAssistant(text: String) {
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(
                    id = "local-${System.currentTimeMillis()}",
                    role = "assistant",
                    content = text,
                ),
            )
        }
    }

    private fun queueMessageFromSlashCommand(args: String, snapshot: ChatUiState) {
        val message = args.trim()
        if (message.isBlank()) {
            _state.update { it.copy(error = "Usage: /queue <message>") }
            return
        }
        if (!snapshot.isStreaming) {
            viewModelScope.launch {
                _state.update { it.copy(draft = "", error = null, notice = null) }
                submitMessage(message, snapshot.copy(draft = message, isStreaming = false))
            }
            return
        }

        queueDraft(message, snapshot)
    }

    private fun queueDraft(message: String, snapshot: ChatUiState, atFront: Boolean = false) {
        val queued = QueuedDraft(message, snapshot.pendingAttachments)
        if (atFront) {
            queuedSlashMessages.addFirst(queued)
        } else {
            queuedSlashMessages.addLast(queued)
        }
        _state.update {
            it.copy(
                draft = "",
                pendingAttachments = emptyList(),
                error = null,
                notice = "Queued for next turn (#${queuedSlashMessages.size}).",
            )
        }
    }

    private fun drainQueuedSlashMessageIfIdle() {
        if (_state.value.isStreaming || isDrainingQueuedSlashMessage || queuedSlashMessages.isEmpty()) return
        val next = queuedSlashMessages.removeFirst()
        isDrainingQueuedSlashMessage = true
        viewModelScope.launch {
            val sent = submitMessage(
                next.text,
                _state.value.copy(
                    draft = next.text,
                    pendingAttachments = next.attachments,
                    isStreaming = false,
                ),
            )
            if (!sent) queuedSlashMessages.addFirst(next)
            isDrainingQueuedSlashMessage = false
            if (!_state.value.isStreaming) drainQueuedSlashMessageIfIdle()
        }
    }

    private fun updateLocalAssistant(id: String, content: String) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id == id) message.copy(content = content) else message
                },
            )
        }
    }

    private fun createSessionFromSlashCommand() {
        val state = _state.value
        if (state.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to start a new session.") }
            return
        }
        if (state.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before starting a new session.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.createSession(state.selectedWorkspacePath, state.selectedModel, state.selectedProfile) }
                .onSuccess { session ->
                    val newSessionId = session?.sessionId
                    if (newSessionId.isNullOrBlank()) {
                        _state.update { it.copy(isRunningSessionAction = false, error = "The server did not return the new session.") }
                    } else {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                notice = "Session created.",
                                openSessionId = newSessionId,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not create a new session.") }
                }
        }
    }

    private fun renameSessionFromSlashCommand(args: String) {
        val title = args.trim()
        if (title.isBlank()) {
            appendLocalAssistant("Current title: **${_state.value.sessionTitle ?: "Untitled Session"}**\n\nUse `/title <text>` to rename this session.")
            _state.update { it.copy(draft = "", error = null) }
            return
        }
        if (_state.value.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before renaming the session.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.renameSession(sessionId, title) }
                .onSuccess { response ->
                    val newTitle = response.session?.title?.trim()?.takeIf { it.isNotBlank() } ?: title
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            sessionTitle = newTitle,
                            notice = if (response.error == null) "Title set to $newTitle." else null,
                            error = response.error,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not rename the session.") }
                }
        }
    }

    private fun branchSessionFromSlashCommand(args: String) {
        val state = _state.value
        if (state.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to fork a conversation.") }
            return
        }
        if (state.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before forking.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.branchSession(sessionId, args.trim().takeIf { it.isNotBlank() }) }
                .onSuccess { result ->
                    val branchId = result.session?.sessionId
                    if (branchId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = result.errorMessage ?: "Could not fork the session.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                notice = "Forked session created.",
                                openSessionId = branchId,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not fork the session.") }
                }
        }
    }

    private fun askBtwFromSlashCommand(args: String) {
        val question = args.trim()
        if (question.isBlank()) {
            _state.update { it.copy(error = "Usage: /btw <question>") }
            return
        }
        if (_state.value.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to ask a side question.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.startBtw(sessionId, question) }
                .onSuccess { response ->
                    val streamId = response.streamId
                    if (!response.error.isNullOrBlank() || streamId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = response.error ?: "The server did not return a side-question stream.",
                            )
                        }
                        return@onSuccess
                    }
                    val messageId = "btw-${System.currentTimeMillis()}"
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            messages = it.messages + ChatMessage(
                                id = messageId,
                                role = "assistant",
                                content = btwMessageText(question, answer = null, isLoading = true),
                            ),
                        )
                    }
                    attachBtwStream(streamId, messageId, question)
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not ask the side question.") }
                }
        }
    }

    private fun attachBtwStream(streamId: String, messageId: String, question: String) {
        btwJob?.cancel()
        var answer = ""
        btwJob = viewModelScope.launch {
            repository.stream(streamId).collect { event ->
                when (event) {
                    is SseEvent.Token -> {
                        answer += event.text
                        updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = true))
                    }
                    is SseEvent.InterimAssistant -> {
                        if (answer.isBlank()) {
                            answer = event.text
                            updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = true))
                        }
                    }
                    is SseEvent.Done, SseEvent.StreamEnd -> {
                        updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = false))
                        btwJob = null
                    }
                    SseEvent.Cancelled -> {
                        updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = false))
                        btwJob = null
                    }
                    is SseEvent.Error -> {
                        updateLocalAssistant(messageId, btwMessageText(question, event.message, isLoading = false))
                        _state.update { it.copy(error = event.message) }
                        btwJob = null
                    }
                    is SseEvent.TransportError -> {
                        updateLocalAssistant(messageId, btwMessageText(question, event.message, isLoading = false))
                        _state.update { it.copy(error = event.message) }
                        btwJob = null
                    }
                    is SseEvent.Reasoning,
                    is SseEvent.ToolStarted,
                    is SseEvent.ToolCompleted,
                    is SseEvent.Title,
                    is SseEvent.PendingSteerLeftover,
                    SseEvent.Ignored -> Unit
                }
            }
        }
    }

    private fun btwMessageText(question: String, answer: String?, isLoading: Boolean): String {
        val body = answer?.trim()?.takeIf { it.isNotBlank() } ?: if (isLoading) "Thinking..." else "No answer produced."
        return "**BTW** $question\n\n$body"
    }

    private fun startBackgroundFromSlashCommand(args: String) {
        val prompt = args.trim()
        if (prompt.isBlank()) {
            _state.update { it.copy(error = "Usage: /background <prompt>") }
            return
        }
        if (_state.value.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to start a background task.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.startBackground(sessionId, prompt) }
                .onSuccess { response ->
                    val taskId = response.taskId
                    if (!response.error.isNullOrBlank() || taskId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = response.error ?: "The server did not return a background task.",
                            )
                        }
                        return@onSuccess
                    }
                    backgroundPromptsByTaskId[taskId] = prompt
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            notice = "Background task started. I'll add the result here when it completes.",
                        )
                    }
                    startBackgroundPollingIfNeeded()
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not start a background task.") }
                }
        }
    }

    private fun startBackgroundPollingIfNeeded() {
        if (backgroundPollJob != null) return
        backgroundPollJob = viewModelScope.launch {
            while (backgroundPromptsByTaskId.isNotEmpty()) {
                runCatching { repository.backgroundStatus(sessionId) }
                    .onSuccess { response -> handleBackgroundResults(response.results.orEmpty()) }
                    .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not check background task status.") } }
                if (backgroundPromptsByTaskId.isNotEmpty()) delay(3_000)
            }
            backgroundPollJob = null
        }
    }

    private fun handleBackgroundResults(results: List<BackgroundResult>) {
        results.forEach { result ->
            val taskId = result.taskId
            val prompt = taskId?.let { backgroundPromptsByTaskId.remove(it) }
                ?: result.prompt?.trim()?.takeIf { it.isNotBlank() }
                ?: "Background task"
            appendLocalAssistant(backgroundResultText(prompt, result.answer))
        }
    }

    private fun backgroundResultText(prompt: String, answer: String?): String {
        val body = answer?.trim()?.takeIf { it.isNotBlank() } ?: "No answer produced."
        val summary = prompt.take(80).let { if (prompt.length > 80) "$it..." else it }
        return "**Background** $summary\n\n$body"
    }

    private fun searchSkillsFromSlashCommand(args: String) {
        val query = args.trim()
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.skills() }
                .onSuccess { skills ->
                    _state.update { it.copy(isRunningSessionAction = false) }
                    appendLocalAssistant(skillsMessage(skills, query))
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not load skills.") }
                }
        }
    }

    private fun skillsMessage(skills: List<SkillSummary>, query: String): String {
        val normalizedQuery = query.lowercase()
        val matches = skills
            .filter { skill ->
                normalizedQuery.isBlank() || listOfNotNull(
                    skill.name,
                    skill.description,
                    skill.category,
                    skill.tags?.joinToString(" "),
                ).any { it.lowercase().contains(normalizedQuery) }
            }
            .sortedWith(compareBy<SkillSummary> { it.disabled == true || it.enabled == false }.thenBy { it.name.orEmpty() })
            .take(12)

        if (matches.isEmpty()) {
            return if (query.isBlank()) "No skills are available." else "No skills matched `$query`."
        }

        val header = if (query.isBlank()) "Available skills:" else "Skills matching `$query`:"
        val rows = matches.joinToString("\n") { skill ->
            val name = skill.name?.takeIf { it.isNotBlank() } ?: "unnamed-skill"
            val disabled = if (skill.disabled == true || skill.enabled == false) " (disabled)" else ""
            val description = skill.description?.trim()?.takeIf { it.isNotBlank() } ?: skill.category?.trim()
            if (description.isNullOrBlank()) "- `$name`$disabled" else "- `$name`$disabled - $description"
        }
        return "$header\n\n$rows"
    }

    private fun setPersonalityFromSlashCommand(args: String) {
        val requestedPersonality = args.trim()
        if (requestedPersonality.isBlank()) {
            listPersonalitiesFromSlashCommand()
            return
        }
        if (_state.value.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before changing personality.") }
            return
        }

        val normalized = requestedPersonality.lowercase()
        val name = if (PERSONALITY_CLEAR_ARGS.contains(normalized)) "" else requestedPersonality
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.setPersonality(sessionId, name) }
                .onSuccess { response ->
                    if (response.error != null) {
                        _state.update { it.copy(isRunningSessionAction = false, error = response.error) }
                        return@onSuccess
                    }
                    _state.update { it.copy(isRunningSessionAction = false) }
                    if (name.isBlank() || response.personality.isNullOrBlank()) {
                        appendLocalAssistant("Personality cleared.")
                    } else {
                        appendLocalAssistant("Personality set to **${response.personality}**.")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not set personality.") }
                }
        }
    }

    private fun listPersonalitiesFromSlashCommand() {
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runCatching { repository.personalities() }
                .onSuccess { personalities ->
                    _state.update { it.copy(isRunningSessionAction = false) }
                    appendLocalAssistant(personalitiesMessage(personalities))
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not load personalities.") }
                }
        }
    }

    private fun personalitiesMessage(personalities: List<PersonalitySummary>): String {
        val rows = personalities.mapNotNull { personality ->
            val name = personality.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val description = personality.description?.trim()?.takeIf { it.isNotBlank() }
            if (description == null) "- **$name**" else "- **$name** - $description"
        }
        if (rows.isEmpty()) return "No personalities are configured on the server."
        return "Available personalities:\n\n${rows.joinToString("\n")}\n\nUse `/personality <name>` or `/personality none`."
    }

    private fun statusText(): String {
        val state = _state.value
        return listOf(
            "Streaming: ${if (state.isStreaming) "yes" else "no"}",
            "Queued messages: ${queuedSlashMessages.size}",
            "Background tasks: ${backgroundPromptsByTaskId.size}",
            "Messages: ${state.messages.size}",
            "Model: ${state.selectedModel?.label ?: state.selectedModel?.name ?: state.selectedModel?.id ?: "default"}",
            "Profile: ${state.selectedProfile?.displayName ?: state.selectedProfile?.name ?: "default"}",
            "Reasoning: ${state.selectedReasoning ?: "default"}",
            "Workspace: ${state.selectedWorkspacePath ?: "default"}",
        ).joinToString("\n")
    }

    fun respondApproval(choice: ApprovalChoice) {
        val approval = _state.value.pendingApproval ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRespondingToPendingPrompt = true, error = null) }
            runCatching {
                repository.respondApproval(sessionId, choice, approval.normalizedApprovalId)
            }.onSuccess { response ->
                _state.update {
                    it.copy(
                        pendingApproval = null,
                        pendingApprovalCount = 0,
                        isRespondingToPendingPrompt = false,
                        error = response.stale?.takeIf { stale -> stale }?.let { "That approval request already expired." } ?: response.errorMessage(),
                    )
                }
                refreshPendingPrompts()
            }.onFailure { error ->
                _state.update { it.copy(isRespondingToPendingPrompt = false, error = error.message ?: "Could not answer approval.") }
            }
        }
    }

    fun skipApprovalsForCurrentSession() {
        val approval = _state.value.pendingApproval ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRespondingToPendingPrompt = true, error = null, notice = null) }
            runCatching { repository.setSessionYolo(sessionId, enabled = true) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isSessionApprovalBypassEnabled = response.yoloEnabled ?: response.yoloEnabledSnake ?: true,
                            pendingApproval = null,
                            pendingApprovalCount = 0,
                            isRespondingToPendingPrompt = false,
                            notice = null,
                            error = response.error,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isRespondingToPendingPrompt = false, error = error.message ?: "Could not enable approval bypass.") }
                }
        }
    }

    private fun refreshApprovalBypassState() {
        viewModelScope.launch {
            runCatching { repository.sessionYolo(sessionId) }.onSuccess { response ->
                val enabled = response.isEnabled
                _state.update {
                    it.copy(
                        isSessionApprovalBypassEnabled = enabled,
                        pendingApproval = if (enabled) null else it.pendingApproval,
                        pendingApprovalCount = if (enabled) 0 else it.pendingApprovalCount,
                    )
                }
            }
        }
    }

    fun respondClarification(response: String = _state.value.clarificationDraft) {
        val clarification = _state.value.pendingClarification ?: return
        val trimmed = response.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = "Enter a response before submitting.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRespondingToPendingPrompt = true, error = null) }
            runCatching {
                repository.respondClarification(sessionId, trimmed, clarification.normalizedClarifyId)
            }.onSuccess { serverResponse ->
                _state.update {
                    it.copy(
                        pendingClarification = null,
                        pendingClarificationCount = 0,
                        clarificationDraft = "",
                        isRespondingToPendingPrompt = false,
                        error = serverResponse.stale?.takeIf { stale -> stale }?.let { "That clarification prompt already expired." },
                    )
                }
                refreshPendingPrompts()
            }.onFailure { error ->
                _state.update { it.copy(isRespondingToPendingPrompt = false, error = error.message ?: "Could not answer clarification.") }
            }
        }
    }

    private fun attachStream(
        streamId: String,
        replayAfterSeq: Int? = null,
        cancelRecovery: Boolean = true,
    ) {
        if (cancelRecovery) {
            streamRecoveryJob?.cancel()
            streamRecoveryJob = null
        }
        streamJob?.cancel()
        var assistantText = _state.value.streamingAssistantText()
        val replayBaseText = assistantText
        var replayMatchedPrefixLength = if (replayAfterSeq == 0) 0 else replayBaseText.length
        streamJob = viewModelScope.launch {
            repository.stream(streamId, replayAfterSeq)
                .onCompletion { cause ->
                    if (
                        ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                            cause = cause,
                            activeStreamId = _state.value.activeStreamId,
                            streamId = streamId,
                        )
                    ) {
                        handleStreamTransportError(streamId, "SSE connection closed unexpectedly.")
                    }
                }
                .collect { event ->
                when (event) {
                    is SseEvent.Token -> {
                        val tokenText = if (replayAfterSeq == 0) {
                            val delta = replayTokenDelta(event.text, replayBaseText, replayMatchedPrefixLength)
                            replayMatchedPrefixLength = delta.matchedPrefixLength
                            delta.text
                        } else {
                            event.text
                        }
                        clearStreamRecoveryState()
                        if (tokenText.isNotEmpty()) {
                            assistantText += tokenText
                            upsertStreamingAssistant(assistantText)
                        }
                    }
                    is SseEvent.InterimAssistant -> {
                        if (assistantText.isBlank()) {
                            assistantText = event.text
                            upsertStreamingAssistant(assistantText)
                        }
                    }
                    is SseEvent.Reasoning -> {
                        clearStreamRecoveryState()
                        _state.update { it.copy(liveReasoning = it.liveReasoning + event.text) }
                    }
                    is SseEvent.ToolStarted -> {
                        clearStreamRecoveryState()
                        _state.update { it.copy(liveToolActivity = event.event.name ?: "Tool running") }
                    }
                    is SseEvent.ToolCompleted -> {
                        clearStreamRecoveryState()
                        _state.update { it.copy(liveToolActivity = null) }
                    }
                    is SseEvent.Title -> clearStreamRecoveryState()
                    is SseEvent.Done -> completeStream(event)
                    SseEvent.StreamEnd -> finishStream(needsTranscriptRefresh = assistantText.isBlank())
                    SseEvent.Cancelled -> _state.update {
                        repository.clearStreamCursor(streamId)
                        it.copy(isStreaming = false, activeStreamRecoveryState = ActiveStreamRecoveryState.Idle, activeStreamId = null)
                    }
                    is SseEvent.Error -> _state.update {
                        repository.clearStreamCursor(streamId)
                        it.copy(
                            isStreaming = false,
                            activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                            activeStreamId = null,
                            error = event.message,
                        )
                    }
                    is SseEvent.TransportError -> handleStreamTransportError(streamId, event.message)
                    is SseEvent.PendingSteerLeftover, SseEvent.Ignored -> Unit
                }
            }
        }
    }

    private fun handleStreamTransportError(streamId: String, message: String) {
        streamRecoveryJob?.cancel()
        streamRecoveryJob = viewModelScope.launch {
            if (_state.value.activeStreamId != streamId) return@launch

            stopPendingPromptPolling(clearPrompts = true)
            _state.update {
                it.copy(
                    isStreaming = true,
                    activeStreamRecoveryState = ActiveStreamRecoveryState.Checking,
                    activeStreamId = streamId,
                    liveToolActivity = null,
                    notice = ActiveStreamRecoveryState.Checking.label,
                    error = null,
                )
            }
            delay(STREAM_RECOVERY_RETRY_DELAY_MS)

            val statusResult = runCatching { repository.chatStreamStatus(streamId) }
            if (_state.value.activeStreamId != streamId) return@launch

            statusResult
                .onSuccess { status ->
                    if (status.isActiveFor(streamId)) {
                        val replayAfterSeq = replayAfterSeq(status, streamId)
                        _state.update {
                            it.copy(
                                isStreaming = true,
                                activeStreamRecoveryState = ActiveStreamRecoveryState.Reconnecting,
                                activeStreamId = streamId,
                                notice = null,
                                error = null,
                            )
                        }
                        attachStream(streamId, replayAfterSeq = replayAfterSeq, cancelRecovery = false)
                        startPendingPromptPolling()
                    } else {
                        streamJob?.cancel()
                        streamJob = null
                        repository.clearStreamCursor(streamId)
                        stopPendingPromptPolling(clearPrompts = true)
                        _state.update {
                            it.copy(
                                isStreaming = false,
                                activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                                activeStreamId = null,
                                liveToolActivity = null,
                                notice = null,
                                error = status.error.nonBlank(),
                            )
                        }
                        load()
                    }
                }
                .onFailure {
                    if (_state.value.activeStreamId != streamId) return@launch
                    val replayAfterSeq = repository.replayAfterSeq(streamId)
                    _state.update {
                        it.copy(
                            isStreaming = true,
                            activeStreamRecoveryState = ActiveStreamRecoveryState.Reconnecting,
                            activeStreamId = streamId,
                            notice = null,
                            error = null,
                        )
                    }
                    attachStream(streamId, replayAfterSeq = replayAfterSeq, cancelRecovery = false)
                    startPendingPromptPolling()
                }
        }
    }

    private fun replayAfterSeq(status: SessionStatusResponse, streamId: String): Int? {
        if (status.replayAvailable != true) return null
        return repository.replayAfterSeq(streamId) ?: 0
    }

    private fun SessionStatusResponse.isActiveFor(streamId: String): Boolean {
        if (active == false) return false
        if (active == true || isStreaming == true) return true
        val reportedStreamId = this.streamId.nonBlank() ?: activeStreamId.nonBlank()
        return reportedStreamId == streamId
    }

    private fun upsertStreamingAssistant(text: String) {
        clearStreamRecoveryState()
        _state.update { current ->
            val messages = current.messages.toMutableList()
            val last = messages.lastOrNull()
            if (last?.role == "assistant" && last.id == "streaming") {
                messages[messages.lastIndex] = last.copy(content = text)
            } else {
                messages += ChatMessage(id = "streaming", role = "assistant", content = text)
            }
            current.copy(messages = messages)
        }
    }

    private fun ChatUiState.streamingAssistantText(): String =
        messages.lastOrNull { it.role == "assistant" && it.id == "streaming" }?.displayText.orEmpty()

    private data class ReplayTokenDelta(
        val matchedPrefixLength: Int,
        val text: String,
    )

    private fun replayTokenDelta(token: String, replayBaseText: String, matchedPrefixLength: Int): ReplayTokenDelta {
        if (token.isEmpty() || replayBaseText.isEmpty()) {
            return ReplayTokenDelta(matchedPrefixLength, token)
        }

        var consumed = 0
        var cursor = matchedPrefixLength.coerceIn(0, replayBaseText.length)
        while (
            consumed < token.length &&
            cursor < replayBaseText.length &&
            token[consumed] == replayBaseText[cursor]
        ) {
            consumed += 1
            cursor += 1
        }
        return ReplayTokenDelta(cursor, token.drop(consumed))
    }

    private fun clearStreamRecoveryState() {
        if (!_state.value.isRecoveringStream) return
        _state.update { it.copy(activeStreamRecoveryState = ActiveStreamRecoveryState.Idle, notice = null) }
    }

    private suspend fun completeStream(event: SseEvent.Done) {
        val completedSession = event.session?.takeIf { completed ->
            completed.sessionId.isNullOrBlank() || completed.sessionId == sessionId
        }
        val completedTranscript = completedSession?.takeIf { it.messages?.isNotEmpty() == true }
        if (completedTranscript != null) {
            val snapshot = repository.snapshotFromCompletedSession(sessionId, completedTranscript)
            applySessionSnapshot(snapshot) {
                it.copy(
                    isStreaming = false,
                    activeStreamId = null,
                    responseCompletionNeedsTranscriptRefresh = false,
                )
            }
        }
        event.usage?.let { usage ->
            _state.update { it.copy(contextWindowSnapshot = usage) }
        }
        finishStream(needsTranscriptRefresh = completedTranscript == null)
    }

    fun refreshCompletedTranscriptIfNeeded() {
        if (!_state.value.responseCompletionNeedsTranscriptRefresh) return
        viewModelScope.launch {
            when (val result = repository.loadSessionSnapshot(sessionId)) {
                is ResultState.Data -> {
                    if (result.value.messages.hasAssistantResponseAfterLatestUser()) {
                        applySessionSnapshot(result.value, fromCache = result.fromCache)
                    }
                    _state.update { it.copy(responseCompletionNeedsTranscriptRefresh = false) }
                }
                is ResultState.Error -> {
                    _state.update { it.copy(responseCompletionNeedsTranscriptRefresh = false) }
                }
                ResultState.Loading -> Unit
            }
        }
    }

    private fun finishStream(needsTranscriptRefresh: Boolean = false) {
        _state.value.activeStreamId?.let(repository::clearStreamCursor)
        streamJob?.cancel()
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        stopPendingPromptPolling(clearPrompts = true)
        _state.update {
            it.copy(
                isStreaming = false,
                activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                activeStreamId = null,
                responseCompletionTrigger = it.responseCompletionTrigger + 1,
                responseCompletionNeedsTranscriptRefresh = needsTranscriptRefresh,
                liveReasoning = "",
                liveToolActivity = null,
                pendingApproval = null,
                pendingClarification = null,
            )
        }
        drainQueuedSlashMessageIfIdle()
    }

    private fun List<ChatMessage>.hasAssistantResponseAfterLatestUser(): Boolean {
        val latestUserIndex = indexOfLast { it.role == "user" }
        if (latestUserIndex < 0) return lastOrNull()?.role == "assistant"
        return drop(latestUserIndex + 1).any { message ->
            message.role == "assistant" && message.displayText.isNotBlank()
        }
    }

    private fun startPendingPromptPolling() {
        pendingPromptJob?.cancel()
        pendingPromptJob = viewModelScope.launch {
            while (_state.value.isStreaming) {
                refreshPendingPrompts()
                delay(1_500)
            }
        }
    }

    private fun stopPendingPromptPolling(clearPrompts: Boolean) {
        pendingPromptJob?.cancel()
        pendingPromptJob = null
        if (clearPrompts) {
            _state.update {
                it.copy(
                    pendingApproval = null,
                    pendingApprovalCount = 0,
                    pendingClarification = null,
                    pendingClarificationCount = 0,
                    clarificationDraft = "",
                    isRespondingToPendingPrompt = false,
                )
            }
        }
    }

    private suspend fun refreshPendingPrompts() {
        runCatching { repository.approvalPending(sessionId) }.onSuccess { response ->
            val pending = response.pending?.takeUnless { it.isEmpty }
            _state.update {
                if (it.isSessionApprovalBypassEnabled) {
                    return@update it.copy(pendingApproval = null, pendingApprovalCount = 0)
                }
                it.copy(
                    pendingApproval = pending,
                    pendingApprovalCount = if (pending == null) 0 else response.displayPendingCount,
                )
            }
        }
        runCatching { repository.clarificationPending(sessionId) }.onSuccess { response ->
            val pending = response.pending?.takeUnless { it.isEmpty }
            _state.update {
                it.copy(
                    pendingClarification = pending,
                    pendingClarificationCount = if (pending == null) 0 else response.displayPendingCount,
                )
            }
        }
    }

    private fun com.uzairansar.hermex.core.model.ApprovalRespondResponse.errorMessage(): String? =
        when {
            stale == true -> "That approval request already expired."
            staleCleared == true || staleClearedSnake == true -> "That approval was already resolved."
            else -> null
        }

    private suspend fun copyUriToCache(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val name = context.displayName(uri) ?: "attachment-${System.currentTimeMillis()}"
        val safeName = name.replace(Regex("""[^\w.\- ]"""), "_")
        val file = File(context.cacheDir, safeName)
        var copied = false
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open attachment." }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            copied = true
            file
        } finally {
            if (!copied) runCatching { file.delete() }
        }
    }

    private suspend fun uploadSharedAttachment(context: Context, attachment: SharedAttachment): UploadResponse =
        withContext(Dispatchers.IO) {
            val cachedFile = attachment.cachedPath
                ?.let(::File)
                ?.takeIf { it.exists() && it.isFile }
            val file = cachedFile ?: copyUriToCache(context, Uri.parse(attachment.uri))
            val mimeType = attachment.mimeType ?: runCatching {
                context.contentResolver.getType(Uri.parse(attachment.uri))
            }.getOrNull()
            try {
                repository.upload(sessionId, file, mimeType)
            } finally {
                if (file.isInside(context.cacheDir)) runCatching { file.delete() }
            }
        }

    private fun File.isInside(directory: File): Boolean {
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        val canonicalFile = runCatching { this.canonicalFile }.getOrNull() ?: return false
        return canonicalFile.path.startsWith(canonicalDirectory.path + File.separator)
    }

    private fun Context.displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun <T> List<T>.nextAfter(current: T?): T? {
        if (isEmpty()) return null
        val index = indexOf(current).takeIf { it >= 0 } ?: -1
        return this[(index + 1) % size]
    }

    private fun List<ProfileSummary>.firstMatchingProfile(value: String?): ProfileSummary? {
        val query = value.nonBlank()?.lowercase() ?: return null
        return firstOrNull { profile ->
            listOfNotNull(profile.name, profile.displayName)
                .any { it.lowercase() == query }
        }
    }

    private val WorkspacesResponse.normalizedRoots: List<WorkspaceRoot>
        get() = (workspaces ?: roots.orEmpty())
            .filter { !it.path.isNullOrBlank() }
            .distinctBy { it.path }

    private val ModelSummary.modelIdentity: String?
        get() = id.nonBlank() ?: name.nonBlank()

    private fun ModelSummary.matchesModelIdentity(model: String?, provider: String?): Boolean {
        val targetModel = model.nonBlank() ?: return false
        val modelMatches = listOfNotNull(id, name).any { value -> value.equals(targetModel, ignoreCase = true) }
        if (!modelMatches) return false
        val targetProvider = provider.nonBlank() ?: return true
        return this.provider.nonBlank()?.equals(targetProvider, ignoreCase = true) == true
    }

    private fun List<ModelSummary>.firstMatchingModel(model: String?, provider: String?): ModelSummary? =
        firstOrNull { option -> option.matchesModelIdentity(model, provider) }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun String.lastPathComponentFallback(): String {
        val trimmed = trim().trimEnd('/', '\\')
        return trimmed.substringAfterLast('/').substringAfterLast('\\').ifBlank { this }
    }

    private fun slashHelpText(commands: List<AgentCommand>): String {
        val serverCommandLines = commands
            .asSequence()
            .filter { it.isMobileVisible }
            .mapNotNull { command ->
                val name = command.displayName ?: return@mapNotNull null
                val normalized = name.lowercase()
                if (normalized in BUILTIN_SLASH_COMMAND_NAMES) return@mapNotNull null
                val args = command.displayArgsHint?.let { " $it" }.orEmpty()
                val description = command.displayDescription ?: "Agent command"
                normalized to "- `/$name$args` - $description"
            }
            .distinctBy { it.first }
            .map { it.second }
            .toList()
        if (serverCommandLines.isEmpty()) return SLASH_HELP
        return "$SLASH_HELP\n\nServer commands:\n\n${serverCommandLines.joinToString("\n")}"
    }

    private companion object {
        const val STREAM_RECOVERY_RETRY_DELAY_MS = 750L

        val PERSONALITY_CLEAR_ARGS = setOf("none", "default", "clear")

        val BUILTIN_SLASH_COMMAND_NAMES = setOf(
            "help",
            "clear",
            "stop",
            "new",
            "title",
            "branch",
            "fork",
            "model",
            "profile",
            "personality",
            "reasoning",
            "workspace",
            "steer",
            "interrupt",
            "goal",
            "btw",
            "background",
            "bg",
            "skills",
            "skill",
            "queue",
            "compress",
            "compact",
            "undo",
            "retry",
            "status",
        )

        fun isKnownUnsupportedSlashCommand(command: String): Boolean =
            command in setOf("terminal", "theme", "voice", "yolo")

        fun unsupportedSlashCommandMessage(command: String): String =
            when (command) {
                "terminal" -> "Terminal is not available in the mobile app."
                "theme" -> "Theme switching is not available from mobile slash commands."
                "voice" -> "Voice commands are not available in the mobile app."
                "yolo" -> "YOLO mode is not available in the mobile app."
                else -> "This command is not available in the mobile app."
            }

        val SLASH_HELP = """
            Available mobile commands:

            `/help` - Show this command list.
            `/clear` - Clear the local transcript.
            `/stop` - Stop the current response.
            `/new` - Start a new session with the current composer settings.
            `/title [text]` - Show or rename this session.
            `/branch [title]` - Fork this session and open the copy.
            `/fork [title]` - Alias for `/branch`.
            `/model <id>` - Switch this session's model.
            `/profile <name>` - Switch profile.
            `/personality <name>` - Set or clear this session's personality.
            `/reasoning <level>` - Set reasoning effort.
            `/workspace <path>` - Switch this session's workspace.
            `/steer <message>` - Steer the active response.
            `/interrupt <message>` - Stop the active response and send a new message.
            `/goal <text|status|pause|resume|clear>` - Manage the persistent goal.
            `/btw <question>` - Ask a side question without changing this chat.
            `/background <prompt>` - Run a parallel task and post the result here.
            `/bg <prompt>` - Alias for `/background`.
            `/skills [query]` - Search available skills.
            `/queue <message>` - Queue a message for the next turn.
            `/compress [focus]` - Compress this session's context.
            `/compact [focus]` - Alias for `/compress`.
            `/undo` - Undo the last exchange.
            `/retry` - Retry the last turn.
            `/status` - Show local session status.
        """.trimIndent()
    }
}
