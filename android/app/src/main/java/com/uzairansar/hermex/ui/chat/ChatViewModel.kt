package com.uzairansar.hermex.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.runSuspendCatching
import com.uzairansar.hermex.core.model.AgentCommand
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.BackgroundResult
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.CompressionAnchorResolver
import com.uzairansar.hermex.core.model.CompressionReferenceCard
import com.uzairansar.hermex.core.model.ContextWindowSnapshot
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.MessageAttachment
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
import com.uzairansar.hermex.core.model.isConfirmedClarification
import com.uzairansar.hermex.core.model.isConfirmedMutation
import com.uzairansar.hermex.core.model.isConfirmedPersonalityMutation
import com.uzairansar.hermex.core.model.isConfirmedYoloMutation
import com.uzairansar.hermex.core.network.SseEvent
import com.uzairansar.hermex.core.network.HermesJson
import com.uzairansar.hermex.data.repository.ChatSessionSnapshot
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.ResultState
import com.uzairansar.hermex.data.repository.withLatestAssistantResponseSpeed
import com.uzairansar.hermex.data.share.SharedAttachment
import com.uzairansar.hermex.data.share.SharedDraft
import com.uzairansar.hermex.data.share.SharedDraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

private data class SessionActionResult(
    val error: String? = null,
    val notice: String? = null,
)

@Serializable
internal data class QueuedDraft(
    val text: String,
    val attachments: List<UploadResponse>,
)

internal object ChatProfileSwitchPolicy {
    fun requiresNewSessionConfirmation(hasPersistedConversation: Boolean): Boolean = hasPersistedConversation
}

data class PendingProfileSwitch(
    val profile: ProfileSummary,
    val consumedDraft: String? = null,
)

internal object ChatStreamOwnershipPolicy {
    fun stillOwnsStream(requestedStreamId: String, activeStreamId: String?): Boolean =
        requestedStreamId == activeStreamId
}

internal object QueuedDraftDrainPolicy {
    fun shouldContinue(sent: Boolean, isStreaming: Boolean): Boolean = sent && !isStreaming
}

internal object StreamRecoveryBackoffPolicy {
    fun shouldRetry(attempt: Int, maximumAttempts: Int): Boolean = attempt <= maximumAttempts

    fun delayMillis(attempt: Int, baseDelayMillis: Long, maximumDelayMillis: Long): Long {
        val shift = (attempt - 1).coerceIn(0, 20)
        return (baseDelayMillis * (1L shl shift)).coerceAtMost(maximumDelayMillis)
    }
}

internal object AttachmentLimitPolicy {
    fun remaining(maximum: Int, attached: Int, uploadsInFlight: Int): Int =
        (maximum - attached - uploadsInFlight).coerceAtLeast(0)
}

internal object ChatDraftPersistencePolicy {
    const val DebounceMillis = 300L
    const val MaximumPersistedCharacters = 64 * 1_024

    fun persistedDraft(value: String): String =
        value.takeIf { it.length <= MaximumPersistedCharacters }.orEmpty()
}

internal object QueuedDraftRegistry {
    private val drafts = java.util.concurrent.ConcurrentHashMap<String, List<QueuedDraft>>()

    fun load(sessionId: String): List<QueuedDraft> = drafts[sessionId].orEmpty()

    fun save(sessionId: String, queued: Collection<QueuedDraft>) {
        if (queued.isEmpty()) drafts.remove(sessionId) else drafts[sessionId] = queued.toList()
    }

    fun clear(sessionId: String) { drafts.remove(sessionId) }
}

@Serializable
internal data class BackgroundTaskState(
    val prompt: String,
    val startedAtMillis: Long = System.currentTimeMillis(),
)

private object BackgroundTaskRegistry {
    private val tasks = java.util.concurrent.ConcurrentHashMap<String, Map<String, BackgroundTaskState>>()

    fun load(sessionId: String): Map<String, BackgroundTaskState> = tasks[sessionId].orEmpty()

    fun save(sessionId: String, values: Map<String, BackgroundTaskState>) {
        if (values.isEmpty()) tasks.remove(sessionId) else tasks[sessionId] = values.toMap()
    }
}

@Serializable
internal data class BtwTaskState(
    val streamId: String,
    val messageId: String,
    val question: String,
    val answer: String,
)

private object BtwTaskRegistry {
    private val tasks = java.util.concurrent.ConcurrentHashMap<String, BtwTaskState>()

    fun load(sessionId: String): BtwTaskState? = tasks[sessionId]
    fun save(sessionId: String, task: BtwTaskState) { tasks[sessionId] = task }
    fun clear(sessionId: String) { tasks.remove(sessionId) }
}

@Serializable
internal data class PersistedChatPendingState(
    val draft: String = "",
    val pendingAttachments: List<UploadResponse> = emptyList(),
    val pendingLocalUploads: List<PendingLocalAttachmentUpload> = emptyList(),
    val queuedDrafts: List<QueuedDraft> = emptyList(),
    val backgroundTasks: Map<String, BackgroundTaskState> = emptyMap(),
    val btwTask: BtwTaskState? = null,
    val importedSharedDraftCreatedAtEpochMillis: Long? = null,
    val importedSharedDraftRemainder: List<SharedAttachment> = emptyList(),
)

@Serializable
internal data class PendingLocalAttachmentUpload(
    val id: String = UUID.randomUUID().toString(),
    val cachedPath: String,
    val mimeType: String? = null,
)

internal class ChatPendingStateStore(context: Context, key: String) {
    private val preferences = context.getSharedPreferences("hermex_chat_pending_state", Context.MODE_PRIVATE)
    private val preferenceKey = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    @Synchronized
    fun load(): PersistedChatPendingState = preferences.getString(preferenceKey, null)
        ?.let { runCatching { HermesJson.decodeFromString<PersistedChatPendingState>(it) }.getOrNull() }
        ?: PersistedChatPendingState()

    @Synchronized
    fun save(state: PersistedChatPendingState, durable: Boolean = false) {
        val editor = if (state == PersistedChatPendingState()) {
            preferences.edit().remove(preferenceKey)
        } else {
            preferences.edit().putString(preferenceKey, HermesJson.encodeToString(state))
        }
        if (durable) {
            check(editor.commit()) { "Could not persist pending chat state." }
        } else {
            editor.apply()
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(preferenceKey).apply()
    }
}

internal fun draftAfterConsuming(current: String, consumed: String): String =
    if (current == consumed || current.trim() == consumed.trim()) "" else current

internal fun draftAfterFailedConsumption(current: String, consumed: String?): String =
    if (current.isBlank() && !consumed.isNullOrBlank()) consumed else current

internal fun matchingBackgroundTaskId(
    tasks: Map<String, BackgroundTaskState>,
    result: BackgroundResult,
): String? {
    result.taskId?.takeIf(tasks::containsKey)?.let { return it }
    val prompt = result.prompt?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return tasks.entries.firstOrNull { it.value.prompt.trim() == prompt }?.key
}

internal fun copyAttachmentWithLimit(
    input: InputStream,
    destination: File,
    maximumBytes: Long,
): Long {
    var copied = false
    return try {
        var totalBytes = 0L
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                totalBytes += count
                require(totalBytes <= maximumBytes) { "Attachments must be 20 MB or smaller." }
                output.write(buffer, 0, count)
            }
        }
        require(totalBytes > 0L) { "Attachment was empty." }
        copied = true
        totalBytes
    } finally {
        if (!copied) runCatching { destination.delete() }
    }
}

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
    val hasPersistedConversation: Boolean = false,
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
    val pendingProfileSwitch: PendingProfileSwitch? = null,
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
    val attachmentUploadsInFlight: Int = 0,
    val pendingLocalUploadCount: Int = 0,
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

    val isUploadingAttachment: Boolean
        get() = attachmentUploadsInFlight > 0
}

class ChatViewModel internal constructor(
    private val sessionId: String,
    private val repository: ChatRepository,
    private val pendingStateStore: ChatPendingStateStore? = null,
) : ViewModel() {
    private val registryKey = "${repository.serverUrl}\u0000$sessionId"
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    private var streamJob: Job? = null
    private var streamPacingJob: Job? = null
    private var streamPacingOwnerId: String? = null
    private var pendingStreamingAssistantText: String = ""
    private var streamRecoveryJob: Job? = null
    private var streamRecoveryAttempt = 0
    private var streamLivenessJob: Job? = null
    private var streamLivenessGeneration = 0L
    private var streamActivityGeneration = 0L
    private var streamConnectionStartedAtMillis = 0L
    private var lastStreamProgressAtMillis: Long? = null
    private var lastStreamTransportActivityAtMillis: Long? = null
    private var lastStreamStatusCheckAtMillis: Long? = null
    private var completedResponseStreamId: String? = null
    private var completedResponseTokensPerSecond: Double? = null
    private var completedResponseTitleOverride: String? = null
    private var sendStartJob: Job? = null
    private var sendStartGeneration = 0L
    private var cancelledSendStartGeneration: Long? = null
    private var reconcileFinalTranscriptForStreamId: String? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var olderMessagesJob: Job? = null
    private var olderMessagesGeneration = 0L
    private var completedTranscriptRefreshJob: Job? = null
    private var composerConfigJob: Job? = null
    private var composerConfigGeneration = 0L
    private var modelSwitchJob: Job? = null
    private var modelSwitchGeneration = 0L
    private var profileSwitchJob: Job? = null
    private var profileSwitchGeneration = 0L
    private var reasoningSwitchJob: Job? = null
    private var reasoningSwitchGeneration = 0L
    private var workspaceSwitchJob: Job? = null
    private var workspaceSwitchGeneration = 0L
    private var btwJob: Job? = null
    private var btwStreamOwnerId: String? = null
    private var backgroundPollJob: Job? = null
    private var pendingPromptJob: Job? = null
    private var workspaceSuggestionsJob: Job? = null
    private var workspaceSuggestionsGeneration = 0L
    private var draftPersistenceJob: Job? = null
    private val backgroundPromptsByTaskId = mutableMapOf<String, BackgroundTaskState>()
    private val pendingLocalUploads = linkedMapOf<String, PendingLocalAttachmentUpload>()
    private val queuedSlashMessages = ArrayDeque<QueuedDraft>()
    private var currentBtwTask: BtwTaskState? = null
    private var importedSharedDraftCreatedAtEpochMillis: Long? = null
    private var importedSharedDraftRemainder: List<SharedAttachment> = emptyList()
    private var isDrainingQueuedSlashMessage = false
    @Volatile private var isClearing = false

    init {
        val persisted = pendingStateStore?.load()
        queuedSlashMessages.addAll(persisted?.queuedDrafts ?: QueuedDraftRegistry.load(registryKey))
        backgroundPromptsByTaskId.putAll(persisted?.backgroundTasks ?: BackgroundTaskRegistry.load(registryKey))
        pendingLocalUploads.putAll(persisted?.pendingLocalUploads.orEmpty().associateBy { it.id })
        _state.value = _state.value.copy(
            draft = persisted?.draft.orEmpty(),
            pendingAttachments = persisted?.pendingAttachments.orEmpty(),
            attachmentUploadsInFlight = pendingLocalUploads.size,
            pendingLocalUploadCount = pendingLocalUploads.size,
        )
        currentBtwTask = persisted?.btwTask ?: BtwTaskRegistry.load(registryKey)
        importedSharedDraftCreatedAtEpochMillis = persisted?.importedSharedDraftCreatedAtEpochMillis
        importedSharedDraftRemainder = persisted?.importedSharedDraftRemainder.orEmpty()
        currentBtwTask?.let { BtwTaskRegistry.save(registryKey, it) }
        load()
        loadComposerConfig()
        refreshApprovalBypassState()
        resumePendingLocalUploads()
    }

    fun updateDraft(value: String) {
        _state.update { it.copy(draft = value, error = null, notice = null) }
        draftPersistenceJob?.cancel()
        draftPersistenceJob = viewModelScope.launch {
            delay(ChatDraftPersistencePolicy.DebounceMillis)
            persistPendingState()
            draftPersistenceJob = null
        }
    }
    fun updateClarificationDraft(value: String) = _state.update { it.copy(clarificationDraft = value, error = null) }
    fun consumeOpenSession() = _state.update { it.copy(openSessionId = null) }

    override fun onCleared() {
        isClearing = true
        draftPersistenceJob?.cancel()
        runCatching { persistPendingState(durable = true) }
        streamJob?.cancel()
        streamPacingJob?.cancel()
        streamRecoveryJob?.cancel()
        streamLivenessJob?.cancel()
        completedTranscriptRefreshJob?.cancel()
        composerConfigJob?.cancel()
        modelSwitchJob?.cancel()
        profileSwitchJob?.cancel()
        reasoningSwitchJob?.cancel()
        workspaceSwitchJob?.cancel()
        loadJob?.cancel()
        olderMessagesJob?.cancel()
        sendStartGeneration += 1
        btwJob?.cancel()
        btwStreamOwnerId = null
        backgroundPollJob?.cancel()
        pendingPromptJob?.cancel()
        workspaceSuggestionsJob?.cancel()
        persistQueuedDrafts()
        persistBackgroundTasks()
        persistPendingState()
        if (_state.value.activeStreamId == null) {
            _state.update {
                it.copy(
                    isStreaming = false,
                    activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                    activeStreamId = null,
                    liveReasoning = "",
                    liveToolActivity = null,
                )
            }
        }
        super.onCleared()
    }

    fun load() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.loadSessionSnapshot(sessionId)) {
                is ResultState.Data -> {
                    if (generation != loadGeneration) return@launch
                    applySessionSnapshot(result.value, fromCache = result.fromCache) {
                        it.copy(isLoading = false)
                    }
                    refreshReasoningForModel(_state.value.selectedModel, reportError = false)
                    reconnectLoadedActiveStream(result.value, fromCache = result.fromCache)
                    resumeAuxiliaryTasks()
                    drainQueuedSlashMessageIfIdle()
                }
                is ResultState.Error -> if (generation == loadGeneration) {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
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
            val sessionModelSelection = current.modelOptions.firstMatchingCatalogModel(nextSessionModel, nextSessionModelProvider)
            transform(
                current.copy(
                    messages = snapshot.messages,
                    hasPersistedConversation = snapshot.messages.isNotEmpty() ||
                        snapshot.messagesOffset > 0 || snapshot.hasOlderMessages,
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
        attachStream(streamId, replayAfterSeq = 0)
        startPendingPromptPolling()
    }

    fun loadOlderMessages() {
        val state = _state.value
        if (state.isLoadingOlderMessages || !state.hasOlderMessages) return
        if (state.messagesOffset <= 0) {
            _state.update { it.copy(hasOlderMessages = false) }
            return
        }

        val generation = ++olderMessagesGeneration
        olderMessagesJob?.cancel()
        olderMessagesJob = viewModelScope.launch {
            val before = _state.value.messagesOffset
            val currentMessages = _state.value.messages
            _state.update { it.copy(isLoadingOlderMessages = true, error = null) }
            runSuspendCatching {
                repository.loadOlderSessionSnapshot(
                    sessionId = sessionId,
                    before = before,
                    currentMessages = currentMessages,
                )
            }
                .onSuccess { snapshot ->
                    if (generation != olderMessagesGeneration) return@onSuccess
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
                    if (error is CancellationException || generation != olderMessagesGeneration) return@onFailure
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
        val generation = ++composerConfigGeneration
        composerConfigJob?.cancel()
        composerConfigJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingComposerConfig = true) }
            runSuspendCatching {
                coroutineScope {
                    val models = async { repository.models() }
                    val profiles = async { repository.profilesResponse() }
                    val workspaces = async { repository.workspaces() }
                    val skills = async {
                        try {
                            repository.skills()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                    val commands = async { runSuspendCatching { repository.commands() }.getOrDefault(emptyList()) }
                    val skillSuggestions = SlashSkillFormatter.suggestions(
                        skills.await().map { skill ->
                            SlashSkillDefinition(
                                name = skill.name,
                                category = skill.category,
                                description = skill.description,
                                enabled = skill.enabled,
                                disabled = skill.disabled,
                            )
                        },
                    )
                    ComposerConfig(
                        models = models.await(),
                        profiles = profiles.await(),
                        workspaces = workspaces.await(),
                        skillSuggestions = skillSuggestions,
                        agentCommands = commands.await(),
                    )
                }
            }.onSuccess { config ->
                if (generation != composerConfigGeneration) return@onSuccess
                val workspaceRoots = config.workspaces.normalizedRoots
                val profileOptions = config.profiles.profiles.orEmpty()
                val activeProfileName = config.profiles.active.nonBlank()
                _state.update {
                    val sessionModelSelection = config.models.firstMatchingCatalogModel(it.sessionModel, it.sessionModelProvider)
                    val selectedCatalogModel = config.models.firstMatchingCatalogModel(
                        it.selectedModel?.id ?: it.selectedModel?.name,
                        it.selectedModel?.provider,
                    )
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
                            it.pendingExplicitModelPick -> selectedCatalogModel ?: it.selectedModel ?: sessionModelSelection ?: config.models.firstOrNull()
                            sessionModelSelection != null -> sessionModelSelection
                            selectedCatalogModel != null -> selectedCatalogModel
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
            }.onFailure { error ->
                if (error is CancellationException || generation != composerConfigGeneration) return@onFailure
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
        if (snapshot.isRunningSessionAction) return
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
        val generation = ++modelSwitchGeneration
        modelSwitchJob?.cancel()
        modelSwitchJob = viewModelScope.launch {
            runSuspendCatching {
                if (isExplicitPick) {
                    repository.updateSessionConfiguration(sessionId, snapshot.selectedWorkspacePath, model)
                } else {
                    null
                }
            }.onSuccess { config ->
                if (generation != modelSwitchGeneration) return@onSuccess
                val resolvedSessionModel = config?.model ?: snapshot.sessionModel ?: model.modelIdentity
                val resolvedSessionProvider = config?.modelProvider ?: snapshot.sessionModelProvider ?: model.provider
                val resolvedModel = _state.value.modelOptions.firstMatchingCatalogModel(resolvedSessionModel, resolvedSessionProvider) ?: model
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
                if (error is CancellationException || generation != modelSwitchGeneration) return@onFailure
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
        requestProfileSwitch(profile)
    }

    fun dismissPendingProfileSwitch() {
        _state.update { it.copy(pendingProfileSwitch = null) }
    }

    fun confirmProfileSwitchStartingNewSession() {
        val pending = _state.value.pendingProfileSwitch ?: return
        _state.update { it.copy(pendingProfileSwitch = null) }
        performProfileSwitch(pending.profile, pending.consumedDraft, startNewSession = true)
    }

    private fun invalidatePendingLoad() {
        loadGeneration += 1
        loadJob?.cancel()
        loadJob = null
        _state.update { it.copy(isLoading = false) }
    }

    private fun requestProfileSwitch(profile: ProfileSummary, consumedDraft: String? = null) {
        val snapshot = _state.value
        if (snapshot.isRunningSessionAction) return
        if (!snapshot.showsProfileControl) return
        if (snapshot.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to change profiles.") }
            return
        }
        if (snapshot.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before changing profiles.") }
            return
        }
        val profileName = profile.name ?: profile.displayName ?: return
        val currentProfileName = snapshot.selectedProfile?.name ?: snapshot.selectedProfile?.displayName
        if (profileName.equals(currentProfileName, ignoreCase = true)) {
            if (consumedDraft != null) {
                _state.update { current -> current.copy(draft = draftAfterConsuming(current.draft, consumedDraft), error = null) }
            }
            return
        }
        if (ChatProfileSwitchPolicy.requiresNewSessionConfirmation(snapshot.hasPersistedConversation)) {
            _state.update {
                it.copy(
                    pendingProfileSwitch = PendingProfileSwitch(profile, consumedDraft),
                    error = null,
                    notice = null,
                )
            }
            return
        }
        performProfileSwitch(profile, consumedDraft, startNewSession = false)
    }

    private fun performProfileSwitch(
        profile: ProfileSummary,
        consumedDraft: String?,
        startNewSession: Boolean,
    ) {
        val snapshot = _state.value
        val profileName = profile.name ?: profile.displayName ?: return
        _state.update {
            it.copy(
                draft = consumedDraft?.let { consumed -> draftAfterConsuming(it.draft, consumed) } ?: it.draft,
                selectedProfile = profile,
                activeProfileName = profileName,
                sessionProfile = profileName,
                pendingProfileSwitch = null,
                isRunningSessionAction = true,
                notice = null,
                error = null,
            )
        }
        val generation = ++profileSwitchGeneration
        profileSwitchJob?.cancel()
        profileSwitchJob = viewModelScope.launch {
            var profileWasSwitched = false
            runSuspendCatching { repository.switchProfile(profile) }
                .onSuccess { response ->
                    if (generation != profileSwitchGeneration) return@onSuccess
                    val switchedProfileName = response.active?.takeIf { it.isNotBlank() } ?: profileName
                    val profileOptions = response.profiles ?: _state.value.profileOptions
                    val selectedProfile = profileOptions.firstMatchingProfile(switchedProfileName) ?: profile
                    val selectedWorkspace = response.defaultWorkspace?.takeIf { it.isNotBlank() }
                        ?: _state.value.selectedWorkspacePath
                    val selectedModel = response.defaultModel?.takeIf { it.isNotBlank() }?.let { modelName ->
                        _state.value.modelOptions.firstMatchingCatalogModel(modelName, selectedProfile.provider)
                            ?: ModelSummary(
                                id = modelName,
                                name = modelName,
                                label = modelName,
                                provider = selectedProfile.provider,
                            )
                    } ?: _state.value.selectedModel
                    _state.update { current ->
                        current.copy(
                            profileOptions = profileOptions,
                            selectedProfile = selectedProfile,
                            activeProfileName = switchedProfileName,
                            sessionProfile = switchedProfileName,
                            selectedWorkspacePath = selectedWorkspace,
                            selectedModel = selectedModel,
                            sessionModel = selectedModel?.id ?: selectedModel?.name,
                            sessionModelProvider = selectedModel?.provider,
                            pendingExplicitModelPick = false,
                        )
                    }
                    profileWasSwitched = true
                    loadComposerConfig()
                    if (startNewSession) {
                        val session = repository.createSession(selectedWorkspace, selectedModel, selectedProfile)
                        val newSessionId = session?.sessionId?.takeIf { it.isNotBlank() }
                            ?: error("The server did not return the new profile session.")
                        if (generation != profileSwitchGeneration) return@onSuccess
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                openSessionId = newSessionId,
                                notice = "Started a new ${profile.displayName ?: profile.name} session.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                notice = "Profile set to ${profile.displayName ?: profile.name}.",
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException || generation != profileSwitchGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            selectedProfile = if (profileWasSwitched) it.selectedProfile else snapshot.selectedProfile,
                            activeProfileName = if (profileWasSwitched) it.activeProfileName else snapshot.activeProfileName,
                            sessionProfile = if (profileWasSwitched) it.sessionProfile else snapshot.sessionProfile,
                            selectedWorkspacePath = if (profileWasSwitched) it.selectedWorkspacePath else snapshot.selectedWorkspacePath,
                            selectedModel = if (profileWasSwitched) it.selectedModel else snapshot.selectedModel,
                            draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                            isRunningSessionAction = false,
                            error = error.message ?: "Could not switch profile.",
                        )
                    }
                }
        }
    }

    fun selectReasoning(effort: String) {
        requestReasoningSwitch(effort)
    }

    private fun requestReasoningSwitch(effort: String, consumedDraft: String? = null) {
        if (effort.isBlank()) return
        val snapshot = _state.value
        if (snapshot.isRunningSessionAction) return
        if (!snapshot.showsReasoningControl) return
        if (snapshot.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before changing reasoning.") }
            return
        }
        val previousEffort = snapshot.selectedReasoning
        if (effort.equals(previousEffort, ignoreCase = true)) {
            if (consumedDraft != null) {
                _state.update { it.copy(draft = draftAfterConsuming(it.draft, consumedDraft), error = null) }
            }
            return
        }
        val generation = ++reasoningSwitchGeneration
        reasoningSwitchJob?.cancel()
        _state.update {
            it.copy(
                draft = consumedDraft?.let { consumed -> draftAfterConsuming(it.draft, consumed) } ?: it.draft,
                selectedReasoning = effort,
                isRunningSessionAction = true,
                error = null,
                notice = null,
            )
        }
        reasoningSwitchJob = viewModelScope.launch {
            try {
                repository.setReasoning(effort, snapshot.selectedModel)
                if (generation != reasoningSwitchGeneration) return@launch
                _state.update { it.copy(isRunningSessionAction = false, notice = "Reasoning set to $effort.") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation != reasoningSwitchGeneration) return@launch
                _state.update {
                    it.copy(
                        selectedReasoning = previousEffort,
                        draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                        isRunningSessionAction = false,
                        error = error.message ?: "Could not switch reasoning.",
                    )
                }
            }
        }
    }

    private suspend fun refreshReasoningForModel(model: ModelSummary?, reportError: Boolean = true) {
        runSuspendCatching { repository.reasoning(model) }.onSuccess { reasoning ->
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
        if (snapshot.isRunningSessionAction) return
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
        val generation = ++workspaceSwitchGeneration
        workspaceSwitchJob?.cancel()
        workspaceSwitchJob = viewModelScope.launch {
            runSuspendCatching {
                repository.updateSessionConfiguration(sessionId, workspace, snapshot.selectedModel)
            }.onSuccess { config ->
                if (generation != workspaceSwitchGeneration) return@onSuccess
                val resolvedWorkspace = config.workspace ?: workspace
                val resolvedSessionModel = config.model ?: previousSessionModel
                val resolvedSessionProvider = config.modelProvider ?: previousSessionModelProvider
                val resolvedModel = _state.value.modelOptions.firstMatchingCatalogModel(resolvedSessionModel, resolvedSessionProvider)
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
                if (error is CancellationException || generation != workspaceSwitchGeneration) return@onFailure
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
            if (!reserveAttachmentSlot()) return@launch
            val file = try {
                copyUriToCache(context, uri)
            } catch (error: CancellationException) {
                _state.update { it.copy(attachmentUploadsInFlight = (it.attachmentUploadsInFlight - 1).coerceAtLeast(0)) }
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        attachmentUploadsInFlight = (it.attachmentUploadsInFlight - 1).coerceAtLeast(0),
                        error = error.message ?: "Upload failed.",
                    )
                }
                return@launch
            }
            enqueuePendingLocalAttachment(file, context.contentResolver.getType(uri))
        }
    }

    fun attachCapturedPhoto(file: File) {
        viewModelScope.launch {
            if (!reserveAttachmentSlot()) {
                file.delete()
                return@launch
            }
            if (!file.isFile || file.length() !in 1..MAXIMUM_ATTACHMENT_BYTES) {
                file.delete()
                _state.update {
                    it.copy(
                        attachmentUploadsInFlight = (it.attachmentUploadsInFlight - 1).coerceAtLeast(0),
                        error = "Captured photos must be 20 MB or smaller.",
                    )
                }
                return@launch
            }
            enqueuePendingLocalAttachment(file, "image/jpeg")
        }
    }

    private fun reserveAttachmentSlot(): Boolean {
        var accepted = false
        _state.update {
            if (it.pendingAttachments.size + pendingLocalUploads.size >= MAXIMUM_MESSAGE_ATTACHMENTS) {
                it.copy(error = "Attach up to $MAXIMUM_MESSAGE_ATTACHMENTS files per message.")
            } else {
                accepted = true
                it.copy(attachmentUploadsInFlight = it.attachmentUploadsInFlight + 1, error = null)
            }
        }
        return accepted
    }

    private suspend fun enqueuePendingLocalAttachment(file: File, mimeType: String?) {
        val pending = PendingLocalAttachmentUpload(
            cachedPath = file.absolutePath,
            mimeType = mimeType,
        )
        pendingLocalUploads[pending.id] = pending
        _state.update { it.copy(pendingLocalUploadCount = pendingLocalUploads.size) }
        persistPendingState(durable = true)
        uploadPendingLocalAttachment(pending)
    }

    private fun resumePendingLocalUploads() {
        pendingLocalUploads.values.toList().forEach { pending ->
            viewModelScope.launch { uploadPendingLocalAttachment(pending) }
        }
    }

    private suspend fun uploadPendingLocalAttachment(pending: PendingLocalAttachmentUpload) {
        val file = File(pending.cachedPath)
        if (!file.isFile) {
            pendingLocalUploads.remove(pending.id)
            _state.update {
                it.copy(
                    attachmentUploadsInFlight = (it.attachmentUploadsInFlight - 1).coerceAtLeast(0),
                    pendingLocalUploadCount = pendingLocalUploads.size,
                    error = "An attachment could not be restored after the app restarted.",
                )
            }
            persistPendingState(durable = true)
            return
        }
        try {
            val upload = repository.upload(sessionId, file, pending.mimeType)
            require(upload.error.isNullOrBlank()) { upload.error ?: "Upload failed." }
            pendingLocalUploads.remove(pending.id)
            _state.update {
                it.copy(
                    pendingAttachments = it.pendingAttachments + upload,
                    attachmentUploadsInFlight = (it.attachmentUploadsInFlight - 1).coerceAtLeast(0),
                    pendingLocalUploadCount = pendingLocalUploads.size,
                )
            }
            persistPendingState(durable = true)
            file.delete()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    attachmentUploadsInFlight = (it.attachmentUploadsInFlight - 1).coerceAtLeast(0),
                    pendingLocalUploadCount = pendingLocalUploads.size,
                    error = "${error.message ?: "Upload failed."} Tap Retry to try again.",
                )
            }
            persistPendingState(durable = true)
        }
    }

    fun retryPendingLocalUploads() {
        val pending = pendingLocalUploads.values.toList()
        if (pending.isEmpty() || _state.value.attachmentUploadsInFlight > 0) return
        _state.update { it.copy(attachmentUploadsInFlight = pending.size, error = null) }
        pending.forEach { upload ->
            viewModelScope.launch { uploadPendingLocalAttachment(upload) }
        }
    }

    fun consumeSharedDraft(
        context: Context,
        draft: SharedDraft,
        onDurablyImported: (List<SharedAttachment>) -> Boolean,
    ) {
        viewModelScope.launch {
            if (importedSharedDraftCreatedAtEpochMillis == draft.createdAtEpochMillis) {
                if (onDurablyImported(importedSharedDraftRemainder)) {
                    importedSharedDraftCreatedAtEpochMillis = null
                    importedSharedDraftRemainder = emptyList()
                    persistPendingState(durable = true)
                }
                return@launch
            }
            val sharedText = draft.text.trim()
            val attachments = draft.attachments.ifEmpty {
                draft.uris.map { SharedAttachment(uri = it) }
            }
            val acceptedAttachments = attachments.take(remainingAttachmentSlots())
            val deferredAttachments = attachments.drop(acceptedAttachments.size)
            val preparedUploads = mutableListOf<PendingLocalAttachmentUpload>()
            try {
                acceptedAttachments.forEach { attachment ->
                    preparedUploads += prepareSharedAttachmentUpload(context, attachment)
                }
            } catch (error: CancellationException) {
                preparedUploads.forEach { runCatching { File(it.cachedPath).delete() } }
                throw error
            } catch (error: Throwable) {
                preparedUploads.forEach { runCatching { File(it.cachedPath).delete() } }
                _state.update {
                    it.copy(
                        error = error.message ?: "Could not import the shared attachment.",
                    )
                }
                return@launch
            }

            val previousState = _state.value
            val previousImportedId = importedSharedDraftCreatedAtEpochMillis
            val previousRemainder = importedSharedDraftRemainder
            val separator = if (previousState.draft.isBlank() || previousState.draft.endsWith("\n")) "" else "\n\n"
            preparedUploads.forEach { pendingLocalUploads[it.id] = it }
            importedSharedDraftCreatedAtEpochMillis = draft.createdAtEpochMillis
            importedSharedDraftRemainder = deferredAttachments
            _state.value = previousState.copy(
                draft = if (sharedText.isBlank()) previousState.draft else "${previousState.draft}$separator$sharedText",
                attachmentUploadsInFlight = previousState.attachmentUploadsInFlight + preparedUploads.size,
                pendingLocalUploadCount = pendingLocalUploads.size,
                notice = when {
                    sharedText.isNotBlank() && preparedUploads.isNotEmpty() -> "Shared text and ${preparedUploads.size} attachment(s) added."
                    sharedText.isNotBlank() -> "Shared text added to the draft."
                    preparedUploads.isNotEmpty() -> "${preparedUploads.size} shared attachment(s) added."
                    else -> previousState.notice
                },
                error = if (deferredAttachments.isNotEmpty()) {
                    "Attach up to $MAXIMUM_MESSAGE_ATTACHMENTS files per message. The remaining files are still in Share."
                } else {
                    null
                },
            )
            try {
                persistPendingState(durable = true)
            } catch (error: Throwable) {
                preparedUploads.forEach { pendingLocalUploads.remove(it.id) }
                importedSharedDraftCreatedAtEpochMillis = previousImportedId
                importedSharedDraftRemainder = previousRemainder
                _state.value = previousState.copy(
                    pendingLocalUploadCount = pendingLocalUploads.size,
                    error = error.message ?: "Could not save the shared draft.",
                )
                preparedUploads.forEach { runCatching { File(it.cachedPath).delete() } }
                return@launch
            }

            if (onDurablyImported(deferredAttachments)) {
                importedSharedDraftCreatedAtEpochMillis = null
                importedSharedDraftRemainder = emptyList()
                persistPendingState(durable = true)
            } else {
                _state.update {
                    it.copy(error = "Shared content is saved in this chat, but Share cleanup failed. Reopen this chat to retry cleanup.")
                }
            }
            preparedUploads.forEach { pending ->
                viewModelScope.launch { uploadPendingLocalAttachment(pending) }
            }
        }
    }

    fun removeAttachment(upload: UploadResponse) {
        _state.update { it.copy(pendingAttachments = it.pendingAttachments - upload) }
        persistPendingState()
    }

    fun remainingAttachmentSlots(): Int =
        AttachmentLimitPolicy.remaining(
            maximum = MAXIMUM_MESSAGE_ATTACHMENTS,
            attached = _state.value.pendingAttachments.size,
            uploadsInFlight = pendingLocalUploads.size,
        )

    fun startVoiceNote(recorder: VoiceNoteRecorder) {
        if (_state.value.isStreaming || _state.value.isRecordingVoiceNote || _state.value.isTranscribingVoiceNote) {
            _state.update { it.copy(error = "Wait for the current response or voice note to finish.") }
            return
        }
        runCatching {
            recorder.start {
                viewModelScope.launch {
                    if (_state.value.isRecordingVoiceNote) stopAndSendVoiceNote(recorder)
                }
            }
        }
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

    fun stopAndSendVoiceNote(recorder: VoiceNoteRecorder) {
        val file = recorder.stop()
        _state.update { it.copy(isRecordingVoiceNote = false, voiceNoteStartedAtMillis = null) }
        if (file == null) {
            _state.update { it.copy(error = recorder.lastErrorMessage ?: "Voice note was empty.") }
            return
        }
        if (_state.value.isStreaming) {
            runCatching { file.delete() }
            _state.update { it.copy(error = "Wait for the current response to finish before sending a voice note.") }
            return
        }
        _state.update { it.copy(isTranscribingVoiceNote = true, error = null) }
        viewModelScope.launch {
            runSuspendCatching {
                try {
                    val response = repository.transcribe(file)
                    val transcript = response.transcript?.trim().orEmpty()
                    require(response.error.isNullOrBlank() && transcript.isNotEmpty()) {
                        response.error ?: "The server did not return a transcript."
                    }
                    val upload = repository.upload(sessionId, file, "audio/mp4")
                    require(upload.error.isNullOrBlank() && !upload.path.isNullOrBlank()) {
                        upload.error ?: "The server did not return the uploaded voice note path."
                    }
                    transcript to upload
                } finally {
                    runCatching { file.delete() }
                }
            }
                .onSuccess { (transcript, upload) ->
                    if (_state.value.isStreaming) {
                        _state.update {
                            it.copy(
                                draft = listOf(it.draft.trim(), transcript)
                                    .filter { part -> part.isNotEmpty() }
                                    .joinToString("\n\n"),
                                pendingAttachments = it.pendingAttachments + upload,
                                isTranscribingVoiceNote = false,
                                error = "The voice note is ready. Send it after the current response finishes.",
                            )
                        }
                        persistPendingState(durable = true)
                        return@onSuccess
                    }
                    val snapshot = _state.value.copy(
                        draft = transcript,
                        pendingAttachments = listOf(upload),
                        isTranscribingVoiceNote = false,
                    )
                    _state.update { it.copy(isTranscribingVoiceNote = false) }
                    submitMessage(transcript, snapshot)
                }
                .onFailure { error ->
                    _state.update { it.copy(isTranscribingVoiceNote = false, error = error.message ?: "Could not send voice note.") }
                }
        }
    }

    fun cancelVoiceNote(recorder: VoiceNoteRecorder) {
        recorder.stop(delete = true)
        _state.update { it.copy(isRecordingVoiceNote = false, voiceNoteStartedAtMillis = null, isTranscribingVoiceNote = false) }
    }

    suspend fun synthesizeSpeech(text: String): ByteArray? =
        resultOrNullPreservingCancellation { repository.synthesizeSpeech(text) }

    suspend fun transcriptMediaThumbnailData(reference: TranscriptMediaReference): ByteArray? =
        resultOrNullPreservingCancellation { repository.transcriptMediaData(sessionId, reference) }

    suspend fun attachmentImageData(path: String): ByteArray? =
        transcriptMediaThumbnailData(TranscriptMediaReference(path))

    suspend fun attachmentTextFile(path: String): FileResponse? =
        resultOrNullPreservingCancellation { repository.attachmentFile(sessionId, path) }

    fun send() {
        if (_state.value.isRecordingVoiceNote || _state.value.isTranscribingVoiceNote) {
            _state.update { it.copy(error = "Wait for the voice note to finish before sending.") }
            return
        }
        if (_state.value.isUploadingAttachment) {
            _state.update { it.copy(error = "Wait for attachments to finish uploading.") }
            return
        }
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
                    if (!cancelActiveStream(drainQueue = false)) return@launch
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
            if (!response.isConfirmedMutation()) {
                SessionActionResult(error = response.error ?: "The server did not confirm the undo.")
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
            runSuspendCatching { repository.retrySession(sessionId) }
                .onSuccess { response ->
                    if (!response.isConfirmedMutation()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = response.error ?: "The server did not confirm the retry.",
                            )
                        }
                        return@onSuccess
                    }
                    val lastUserText = response.lastUserText?.trim().orEmpty()
                    if (lastUserText.isBlank()) {
                        _state.update { it.copy(isRunningSessionAction = false, error = "The server did not return a message to retry.") }
                        return@onSuccess
                    }
                    when (val snapshotResult = repository.loadSessionSnapshot(sessionId)) {
                        is ResultState.Data -> applySessionSnapshot(snapshotResult.value, fromCache = snapshotResult.fromCache)
                        is ResultState.Error -> {
                            _state.update { it.copy(isRunningSessionAction = false, error = snapshotResult.message) }
                            return@onSuccess
                        }
                        ResultState.Loading -> Unit
                    }
                    _state.update { it.copy(isRunningSessionAction = false) }
                    submitMessage(
                        lastUserText,
                        _state.value.copy(
                            draft = lastUserText,
                            pendingAttachments = emptyList(),
                            isStreaming = false,
                        ),
                    )
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
            runSuspendCatching {
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
            runSuspendCatching { repository.truncateSessionSnapshot(sessionId, context.fullHistoryIndex) }
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
            runSuspendCatching { repository.truncateSessionSnapshot(sessionId, context.fullHistoryIndex) }
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
            if (!response.isConfirmedMutation()) {
                SessionActionResult(error = response.error ?: "The server did not confirm context compression.")
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
        invalidatePendingLoad()
        val operationJob = currentCoroutineContext()[Job]
        sendStartJob?.takeIf { it !== operationJob }?.cancel(CancellationException("Superseded by a newer send."))
        val generation = ++sendStartGeneration
        sendStartJob = operationJob
        val optimisticMessageId = if (appendOptimisticUser) "optimistic-${System.nanoTime()}" else null
        _state.update {
            val optimisticMessages = if (appendOptimisticUser) {
                it.messages + ChatMessage(
                    id = optimisticMessageId,
                    role = "user",
                    content = text,
                    attachments = snapshot.pendingAttachments.map { attachment ->
                        MessageAttachment(
                            name = attachment.filename,
                            path = attachment.path,
                            mime = attachment.mime,
                            size = attachment.size?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            isImage = attachment.isImage,
                        )
                    }.takeIf { attachments -> attachments.isNotEmpty() },
                )
            } else {
                it.messages
            }
            it.copy(
                messages = optimisticMessages,
                hasPersistedConversation = true,
                draft = draftAfterConsuming(it.draft, snapshot.draft),
                pendingAttachments = it.pendingAttachments - snapshot.pendingAttachments.toSet(),
                isStreaming = true,
                error = null,
            )
        }
        persistPendingState()
        var acceptedStreamId: String? = null
        return try {
            val explicitModelPick = snapshot.pendingExplicitModelPick && snapshot.selectedModel?.modelIdentity != null
            val streamId = withContext(NonCancellable + Dispatchers.IO) {
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
                ).also { acceptedStreamId = it }
            }
            if (generation != sendStartGeneration || cancelledSendStartGeneration == generation || !currentCoroutineContext()[Job]!!.isActive) {
                val completedBeforeCancellation = !streamId.isNullOrBlank() && cancelAcceptedStream(streamId)
                if (cancelledSendStartGeneration == generation) {
                    if (completedBeforeCancellation && !isClearing) {
                        refreshAfterInactiveStream()
                    } else {
                        restoreFailedSend(
                            optimisticMessageId,
                            text,
                            snapshot.pendingAttachments,
                            snapshot.hasPersistedConversation,
                            "Send cancelled.",
                        )
                    }
                    cancelledSendStartGeneration = null
                }
                return false
            }
            sendStartJob = null
            if (streamId.isNullOrBlank()) {
                restoreFailedSend(
                    optimisticMessageId,
                    text,
                    snapshot.pendingAttachments,
                    snapshot.hasPersistedConversation,
                    "Server did not return a stream id.",
                )
                drainQueuedSlashMessageIfIdle()
                false
            } else {
                _state.update {
                    it.copy(
                        isStreaming = true,
                        activeStreamId = streamId,
                        pendingExplicitModelPick = if (snapshot.pendingExplicitModelPick) false else it.pendingExplicitModelPick,
                    )
                }
                attachStream(streamId)
                startPendingPromptPolling()
                true
            }
        } catch (error: CancellationException) {
            val completedBeforeCancellation = acceptedStreamId
                ?.takeIf { it.isNotBlank() }
                ?.let { cancelAcceptedStream(it) }
                ?: false
            if (generation == sendStartGeneration || isClearing) {
                if (completedBeforeCancellation) {
                    if (!isClearing) withContext(NonCancellable) { refreshAfterInactiveStream() }
                } else {
                    restoreFailedSend(
                        optimisticMessageId,
                        text,
                        snapshot.pendingAttachments,
                        snapshot.hasPersistedConversation,
                        "Send cancelled.",
                    )
                }
            }
            throw error
        } catch (error: Throwable) {
            if (generation == sendStartGeneration) {
                restoreFailedSend(
                    optimisticMessageId,
                    text,
                    snapshot.pendingAttachments,
                    snapshot.hasPersistedConversation,
                    error.message ?: "Send failed.",
                )
                drainQueuedSlashMessageIfIdle()
            }
            false
        } finally {
            if (generation == sendStartGeneration && sendStartJob === operationJob) sendStartJob = null
        }
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

        loadGeneration += 1
        loadJob?.cancel()
        olderMessagesGeneration += 1
        olderMessagesJob?.cancel()
        olderMessagesJob = null

        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, error = null, notice = null) }
            runSuspendCatching { repository.clearSessionSnapshot(sessionId) }
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
                    persistBackgroundTasks()
                    BtwTaskRegistry.clear(registryKey)
                    currentBtwTask = null
                    queuedSlashMessages.clear()
                    persistQueuedDrafts()
                    pendingStateStore?.clear()
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

    private suspend fun cancelActiveStream(drainQueue: Boolean = true): Boolean {
        val streamId = _state.value.activeStreamId
        if (streamId == null && _state.value.isStreaming) {
            val pendingStart = sendStartJob
            cancelledSendStartGeneration = sendStartGeneration
            streamRecoveryJob?.cancel()
            streamRecoveryJob = null
            streamRecoveryAttempt = 0
            stopPendingPromptPolling(clearPrompts = true)
            _state.update {
                it.copy(
                    isStreaming = false,
                    activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                    activeStreamId = null,
                    liveReasoning = "",
                    liveToolActivity = null,
                )
            }
            pendingStart?.join()
            if (drainQueue) drainQueuedSlashMessageIfIdle()
            return true
        }
        if (streamId != null) {
            var completedBeforeCancellation = false
            val cancelError = runSuspendCatching { repository.cancel(streamId) }
                .fold(
                    onSuccess = { response ->
                        completedBeforeCancellation = response.isConfirmedMutation() && response.cancelled == false
                        response.error?.takeIf { it.isNotBlank() }
                            ?: if (!response.isConfirmedMutation()) "The server could not cancel this response." else null
                    },
                    onFailure = { it.message ?: "Could not cancel the response." },
                )
            if (cancelError != null) {
                _state.update { it.copy(error = cancelError) }
                return false
            }
            if (completedBeforeCancellation) {
                finishStream(needsTranscriptRefresh = true)
                refreshAfterInactiveStream()
                return true
            }
            if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) {
                return false
            }
        }
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
                liveReasoning = "",
                liveToolActivity = null,
                pendingApproval = null,
                pendingClarification = null,
            )
        }
        if (drainQueue) drainQueuedSlashMessageIfIdle()
        return true
    }

    private suspend fun cancelAcceptedStream(streamId: String): Boolean =
        withContext(NonCancellable + Dispatchers.IO) {
            val response = runSuspendCatching { repository.cancel(streamId) }.getOrNull()
            repository.clearStreamCursor(streamId)
            response?.isConfirmedMutation() == true && response.cancelled == false
        }

    private fun discardActiveStreamAfterSessionClear() {
        _state.value.activeStreamId?.let(repository::clearStreamCursor)
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        streamJob?.cancel()
        streamJob = null
        btwJob?.cancel()
        btwJob = null
        btwStreamOwnerId = null
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
                        if (!cancelActiveStream(drainQueue = false)) return@launch
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
            runSuspendCatching { repository.steer(sessionId, text) }
                .onSuccess { response ->
                    val responseError = response.error?.takeIf { it.isNotBlank() }
                    val accepted = response.accepted != false && responseError == null
                    val rejection = response.fallback
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.replace('_', ' ')
                    _state.update {
                        it.copy(
                            draft = if (accepted) draftAfterConsuming(it.draft, text) else it.draft,
                            isRunningSessionAction = false,
                            notice = if (accepted) "Steering sent." else null,
                            error = if (accepted) null else responseError ?: rejection?.let { reason ->
                                "Steering was not accepted: $reason."
                            } ?: "The active response could not be steered.",
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
            val consumedDraft = snapshot.draft
            _state.update { it.copy(isRunningSessionAction = true, error = null, notice = null, draft = "") }
            runSuspendCatching {
                repository.submitGoal(
                    sessionId = sessionId,
                    args = args,
                    workspace = snapshot.selectedWorkspacePath,
                    model = snapshot.selectedModel,
                    profile = snapshot.selectedProfile,
                )
            }
                .onSuccess { response ->
                    val kickoff = response.kickoffPrompt?.trim().orEmpty()
                    val responseError = response.error?.takeIf { it.isNotBlank() }
                    val responseSessionId = response.sessionId?.trim()?.takeIf { it.isNotBlank() }
                    if (!response.isConfirmedMutation()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                                error = responseError ?: response.message ?: "Goal request failed.",
                            )
                        }
                        return@onSuccess
                    }
                    if (responseSessionId != null && responseSessionId != sessionId) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                                error = "The server started the goal in a different session.",
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            notice = response.message ?: response.decision?.message ?: response.goal?.goal ?: "Goal updated.",
                            error = null,
                        )
                    }
                    if (kickoff.isNotBlank()) {
                        attachGoalKickoffStream(response.streamId)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                            error = error.message ?: "Could not submit goal.",
                        )
                    }
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
        requestProfileSwitch(profile, consumedDraft = state.draft)
    }

    private fun switchReasoning(args: String) {
        val query = args.trim().lowercase()
        val state = _state.value
        if (query in REASONING_DISPLAY_ARGS) {
            requestReasoningDisplaySwitch(query, consumedDraft = state.draft)
            return
        }
        if (!state.showsReasoningControl) {
            _state.update { it.copy(error = "Reasoning is not available for the selected model.") }
            return
        }
        val effort = state.reasoningOptions.firstOrNull { it.equals(query, ignoreCase = true) }
        if (query.isBlank() || effort == null) {
            _state.update { it.copy(error = "Reasoning level not found.") }
            return
        }
        requestReasoningSwitch(effort, consumedDraft = state.draft)
    }

    private fun requestReasoningDisplaySwitch(display: String, consumedDraft: String) {
        val snapshot = _state.value
        if (snapshot.isRunningSessionAction) return
        if (snapshot.isStreaming) {
            _state.update { it.copy(error = "Wait for the current response to finish before changing reasoning display.") }
            return
        }
        val generation = ++reasoningSwitchGeneration
        reasoningSwitchJob?.cancel()
        _state.update {
            it.copy(
                draft = draftAfterConsuming(it.draft, consumedDraft),
                isRunningSessionAction = true,
                error = null,
                notice = null,
            )
        }
        reasoningSwitchJob = viewModelScope.launch {
            try {
                repository.setReasoningDisplay(display)
                if (generation != reasoningSwitchGeneration) return@launch
                _state.update { it.copy(isRunningSessionAction = false, notice = "Reasoning display updated.") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation != reasoningSwitchGeneration) return@launch
                _state.update {
                    it.copy(
                        draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                        isRunningSessionAction = false,
                        error = error.message ?: "Could not update reasoning display.",
                    )
                }
            }
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
        val consumedDraft = _state.value.draft
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runSuspendCatching { action() }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            draft = if (result.error == null) it.draft else draftAfterFailedConsumption(it.draft, consumedDraft),
                            error = result.error,
                            notice = if (result.error == null) result.notice else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                            error = error.message ?: "Session action failed.",
                        )
                    }
                }
        }
    }

    private fun appendLocalAssistant(text: String, id: String = "local-${System.currentTimeMillis()}") {
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(
                    id = id,
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
        persistQueuedDrafts()
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
        persistQueuedDrafts()
        isDrainingQueuedSlashMessage = true
        viewModelScope.launch {
            var continueDraining = false
            try {
                val sent = submitMessage(
                    next.text,
                    _state.value.copy(
                        draft = next.text,
                        pendingAttachments = next.attachments,
                        isStreaming = false,
                    ),
                )
                continueDraining = QueuedDraftDrainPolicy.shouldContinue(sent, _state.value.isStreaming)
            } catch (error: CancellationException) {
                if (isClearing) {
                    queuedSlashMessages.addFirst(next)
                    persistQueuedDrafts()
                }
                throw error
            } finally {
                isDrainingQueuedSlashMessage = false
            }
            if (continueDraining) drainQueuedSlashMessageIfIdle()
        }
    }

    private fun persistQueuedDrafts() {
        QueuedDraftRegistry.save(registryKey, queuedSlashMessages)
        persistPendingState()
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
            runSuspendCatching { repository.createSession(state.selectedWorkspacePath, state.selectedModel, state.selectedProfile) }
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
            runSuspendCatching { repository.renameSession(sessionId, title) }
                .onSuccess { response ->
                    if (!response.isConfirmedMutation()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = response.error ?: "The server did not confirm the title change.",
                            )
                        }
                        return@onSuccess
                    }
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
            runSuspendCatching { repository.branchSession(sessionId, args.trim().takeIf { it.isNotBlank() }) }
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
            currentBtwTask?.let { previous ->
                val cancelResponse = try {
                    repository.cancel(previous.streamId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            error = error.message ?: "Could not stop the previous side question.",
                        )
                    }
                    return@launch
                }
                val cancelError = cancelResponse.error?.takeIf { it.isNotBlank() }
                    ?: if (!cancelResponse.isConfirmedMutation()) "Could not stop the previous side question." else null
                if (cancelError != null) {
                    _state.update { it.copy(isRunningSessionAction = false, error = cancelError) }
                    return@launch
                }
                btwJob?.cancel()
                btwJob = null
                btwStreamOwnerId = null
                updateLocalAssistant(previous.messageId, btwMessageText(previous.question, previous.answer, isLoading = false))
                persistBtwTask(null)
            }
            runSuspendCatching { repository.startBtw(sessionId, question) }
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
                    persistBtwTask(BtwTaskState(streamId = streamId, messageId = messageId, question = question, answer = ""))
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
                    attachBtwStream(streamId, messageId, question, initialAnswer = "")
                }
                .onFailure { error ->
                    _state.update { it.copy(isRunningSessionAction = false, error = error.message ?: "Could not ask the side question.") }
                }
        }
    }

    private fun attachBtwStream(streamId: String, messageId: String, question: String, initialAnswer: String) {
        btwJob?.cancel()
        btwStreamOwnerId = streamId
        var answer = initialAnswer
        btwJob = viewModelScope.launch {
            repository.stream(streamId).collect { event ->
                if (btwStreamOwnerId != streamId) return@collect
                when (event) {
                    is SseEvent.Token -> {
                        answer += event.text
                        persistBtwTask(BtwTaskState(streamId, messageId, question, answer))
                        updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = true))
                    }
                    is SseEvent.InterimAssistant -> {
                        val interim = event.text.trim()
                        if (event.alreadyStreamed != true && interim.isNotBlank() && !answer.endsWith(interim)) {
                            answer = if (answer.isBlank()) interim else "$answer\n\n$interim"
                            persistBtwTask(BtwTaskState(streamId, messageId, question, answer))
                            updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = true))
                        }
                    }
                    is SseEvent.Done, SseEvent.StreamEnd -> {
                        updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = false))
                        persistBtwTask(null)
                        btwStreamOwnerId = null
                        btwJob = null
                    }
                    is SseEvent.Cancelled -> {
                        updateLocalAssistant(messageId, btwMessageText(question, answer, isLoading = false))
                        persistBtwTask(null)
                        btwStreamOwnerId = null
                        btwJob = null
                    }
                    is SseEvent.Error -> {
                        updateLocalAssistant(messageId, btwMessageText(question, event.message, isLoading = false))
                        persistBtwTask(null)
                        _state.update { it.copy(error = event.message) }
                        btwStreamOwnerId = null
                        btwJob = null
                    }
                    is SseEvent.TransportError -> {
                        updateLocalAssistant(messageId, btwMessageText(question, event.message, isLoading = false))
                        _state.update { it.copy(error = event.message) }
                        btwStreamOwnerId = null
                        btwJob = null
                    }
                    is SseEvent.Reasoning,
                    is SseEvent.ToolStarted,
                    is SseEvent.ToolCompleted,
                    is SseEvent.Title,
                    is SseEvent.PendingSteerLeftover,
                    is SseEvent.ApprovalPending,
                    is SseEvent.ClarificationPending,
                    SseEvent.Heartbeat,
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
        val consumedDraft = _state.value.draft
        viewModelScope.launch {
            _state.update { it.copy(isRunningSessionAction = true, draft = "", error = null, notice = null) }
            runSuspendCatching { repository.startBackground(sessionId, prompt) }
                .onSuccess { response ->
                    val taskId = response.taskId
                    if (!response.error.isNullOrBlank() || taskId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                                error = response.error ?: "The server did not return a background task.",
                            )
                        }
                        return@onSuccess
                    }
                    backgroundPromptsByTaskId[taskId] = BackgroundTaskState(prompt)
                    persistBackgroundTasks()
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            notice = "Background task started. I'll add the result here when it completes.",
                        )
                    }
                    startBackgroundPollingIfNeeded()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunningSessionAction = false,
                            draft = draftAfterFailedConsumption(it.draft, consumedDraft),
                            error = error.message ?: "Could not start a background task.",
                        )
                    }
                }
        }
    }

    private fun startBackgroundPollingIfNeeded() {
        if (backgroundPollJob != null) return
        backgroundPollJob = viewModelScope.launch {
            var failureCount = 0
            try {
                while (backgroundPromptsByTaskId.isNotEmpty()) {
                    try {
                        handleBackgroundResults(repository.backgroundStatus(sessionId).results.orEmpty())
                        failureCount = 0
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        failureCount += 1
                        _state.update {
                            it.copy(error = error.message ?: "Could not check background tasks. Retrying automatically.")
                        }
                    }
                    if (backgroundPromptsByTaskId.isNotEmpty()) {
                        delay(StreamRecoveryBackoffPolicy.delayMillis(failureCount.coerceAtLeast(1), 3_000, 60_000))
                    }
                }
            } finally {
                backgroundPollJob = null
            }
        }
    }

    private fun handleBackgroundResults(results: List<BackgroundResult>) {
        results.forEach { result ->
            val taskId = matchingBackgroundTaskId(backgroundPromptsByTaskId, result) ?: return@forEach
            val prompt = backgroundPromptsByTaskId.remove(taskId)?.prompt ?: return@forEach
            appendLocalAssistant(backgroundResultText(prompt, result.answer))
        }
        persistBackgroundTasks()
    }

    private fun persistBackgroundTasks() {
        BackgroundTaskRegistry.save(registryKey, backgroundPromptsByTaskId)
        persistPendingState(durable = true)
    }

    private fun persistBtwTask(task: BtwTaskState?) {
        currentBtwTask = task
        if (task == null) BtwTaskRegistry.clear(registryKey) else BtwTaskRegistry.save(registryKey, task)
        persistPendingState()
    }

    private fun persistPendingState(durable: Boolean = false) {
        pendingStateStore?.save(
            PersistedChatPendingState(
                draft = ChatDraftPersistencePolicy.persistedDraft(_state.value.draft),
                pendingAttachments = _state.value.pendingAttachments,
                pendingLocalUploads = pendingLocalUploads.values.toList(),
                queuedDrafts = queuedSlashMessages.toList(),
                backgroundTasks = backgroundPromptsByTaskId.toMap(),
                btwTask = currentBtwTask,
                importedSharedDraftCreatedAtEpochMillis = importedSharedDraftCreatedAtEpochMillis,
                importedSharedDraftRemainder = importedSharedDraftRemainder,
            ),
            durable = durable,
        )
    }

    private fun resumeAuxiliaryTasks() {
        BtwTaskRegistry.load(registryKey)?.let { task ->
            if (_state.value.messages.none { it.id == task.messageId }) {
                appendLocalAssistant(
                    btwMessageText(task.question, task.answer, isLoading = true),
                    id = task.messageId,
                )
            }
            if (btwJob?.isActive != true) {
                attachBtwStream(task.streamId, task.messageId, task.question, task.answer)
            }
        }
        if (backgroundPromptsByTaskId.isNotEmpty()) startBackgroundPollingIfNeeded()
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
            runSuspendCatching { repository.skills() }
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
            runSuspendCatching { repository.setPersonality(sessionId, name) }
                .onSuccess { response ->
                    if (!response.isConfirmedPersonalityMutation(name)) {
                        _state.update {
                            it.copy(
                                isRunningSessionAction = false,
                                error = response.error?.takeIf { message -> message.isNotBlank() }
                                    ?: "The server did not confirm the personality change.",
                            )
                        }
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
            runSuspendCatching { repository.personalities() }
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
            runSuspendCatching {
                repository.respondApproval(sessionId, choice, approval.normalizedApprovalId)
            }.onSuccess { response ->
                if (!response.isConfirmedMutation()) {
                    _state.update {
                        it.copy(
                            isRespondingToPendingPrompt = false,
                            error = "The server did not accept that approval response.",
                        )
                    }
                    refreshPendingPrompts()
                    return@onSuccess
                }
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
            runSuspendCatching { repository.setSessionYolo(sessionId, enabled = true) }
                .onSuccess { response ->
                    val responseError = response.error?.takeIf { it.isNotBlank() }
                    if (!response.isConfirmedYoloMutation(enabled = true)) {
                        _state.update {
                            it.copy(
                                isRespondingToPendingPrompt = false,
                                error = responseError ?: "Could not enable approval bypass.",
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            isSessionApprovalBypassEnabled = true,
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
            runSuspendCatching { repository.sessionYolo(sessionId) }.onSuccess { response ->
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
            runSuspendCatching {
                repository.respondClarification(sessionId, trimmed, clarification.normalizedClarifyId)
            }.onSuccess { serverResponse ->
                if (!serverResponse.isConfirmedClarification(trimmed)) {
                    _state.update {
                        it.copy(
                            isRespondingToPendingPrompt = false,
                            error = "The server did not accept that clarification response.",
                        )
                    }
                    refreshPendingPrompts()
                    return@onSuccess
                }
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
        if (completedResponseStreamId != streamId) {
            completedResponseTitleOverride = null
        }
        if (cancelRecovery) {
            streamRecoveryJob?.cancel()
            streamRecoveryJob = null
        }
        streamJob?.cancel()
        flushPendingStreamingAssistant()
        streamPacingJob?.cancel()
        streamPacingJob = null
        streamPacingOwnerId = streamId
        pendingStreamingAssistantText = ""
        var assistantText = _state.value.streamingAssistantText()
        val replayBaseText = assistantText
        var replayMatchedPrefixLength = if (replayAfterSeq == 0) 0 else replayBaseText.length
        startStreamLivenessMonitoring(streamId, isReplayConnection = replayAfterSeq != null)
        streamJob = viewModelScope.launch {
            repository.stream(streamId, replayAfterSeq)
                .onCompletion { cause ->
                    if (completedResponseStreamId == streamId) {
                        completedResponseStreamId = null
                    }
                    if (
                        !_state.value.isRecoveringStream &&
                        ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                            cause = cause,
                            activeStreamId = _state.value.activeStreamId,
                            streamId = streamId,
                        )
                    ) {
                        flushPendingStreamingAssistant()
                        handleStreamTransportError(streamId, "SSE connection closed unexpectedly.")
                    }
                }
                .collect { event ->
                if (!ownsStreamTransport(streamId)) return@collect
                if (event !is SseEvent.TransportError) {
                    recordStreamTransportActivity(demoteChecking = event == SseEvent.Heartbeat)
                }
                when (event) {
                    is SseEvent.Token -> {
                        val tokenText = if (replayAfterSeq == 0) {
                            val delta = replayTokenDelta(event.text, replayBaseText, replayMatchedPrefixLength)
                            replayMatchedPrefixLength = delta.matchedPrefixLength
                            delta.text
                        } else {
                            event.text
                        }
                        if (tokenText.isNotEmpty()) {
                            markStreamProgress()
                            assistantText += tokenText
                            enqueueStreamingAssistantText(streamId, tokenText)
                        }
                    }
                    is SseEvent.InterimAssistant -> {
                        flushPendingStreamingAssistant()
                        val interim = event.text.trim()
                        if (event.alreadyStreamed != true && interim.isNotBlank() && !assistantText.endsWith(interim)) {
                            markStreamProgress()
                            assistantText = if (assistantText.isBlank()) interim else "$assistantText\n\n$interim"
                            upsertStreamingAssistant(assistantText)
                        }
                    }
                    is SseEvent.Reasoning -> {
                        markStreamProgress()
                        _state.update { it.copy(liveReasoning = it.liveReasoning + event.text) }
                    }
                    is SseEvent.ToolStarted -> {
                        markStreamProgress()
                        _state.update { it.copy(liveToolActivity = event.event.name ?: "Tool running") }
                    }
                    is SseEvent.ToolCompleted -> {
                        markStreamProgress()
                        _state.update { it.copy(liveToolActivity = null) }
                    }
                    is SseEvent.Title -> {
                        if (event.sessionId.isNullOrBlank() || event.sessionId == sessionId) {
                            event.title?.trim()?.takeIf { it.isNotBlank() }?.let { title ->
                                markStreamProgress()
                                completedResponseTitleOverride = title
                                _state.update { it.copy(sessionTitle = title) }
                            }
                        }
                    }
                    is SseEvent.Done -> {
                        flushPendingStreamingAssistant()
                        completeStream(streamId, event)
                    }
                    SseEvent.StreamEnd -> {
                        flushPendingStreamingAssistant()
                        if (completedResponseStreamId == streamId) {
                            finishCompletedStreamTransport(streamId)
                        } else {
                            finishStream(
                                needsTranscriptRefresh = assistantText.isBlank() || reconcileFinalTranscriptForStreamId == streamId,
                            )
                        }
                    }
                    is SseEvent.Cancelled -> {
                        flushPendingStreamingAssistant()
                        reconcileTerminalStream(
                            streamId = streamId,
                            session = event.session,
                            replacementSessionId = event.replacementSessionId,
                            error = null,
                        )
                    }
                    is SseEvent.Error -> {
                        flushPendingStreamingAssistant()
                        reconcileTerminalStream(
                            streamId = streamId,
                            session = event.session,
                            replacementSessionId = event.replacementSessionId,
                            error = event.displayMessage,
                        )
                    }
                    is SseEvent.TransportError -> {
                        flushPendingStreamingAssistant()
                        handleStreamTransportError(streamId, event.message)
                    }
                    is SseEvent.PendingSteerLeftover -> {
                        markStreamProgress()
                        enqueuePendingSteerLeftover(event.text)
                    }
                    is SseEvent.ApprovalPending -> {
                        markStreamProgress()
                        applyApprovalPending(event.response)
                    }
                    is SseEvent.ClarificationPending -> {
                        markStreamProgress()
                        applyClarificationPending(event.response)
                    }
                    SseEvent.Heartbeat -> Unit
                    SseEvent.Ignored -> Unit
                }
            }
        }
    }

    private fun startStreamLivenessMonitoring(streamId: String, isReplayConnection: Boolean) {
        streamLivenessJob?.cancel()
        streamLivenessGeneration += 1
        streamActivityGeneration += 1
        val generation = streamLivenessGeneration
        val startedAt = monotonicMillis()
        streamConnectionStartedAtMillis = startedAt
        lastStreamProgressAtMillis = startedAt.takeIf { isReplayConnection }
        lastStreamTransportActivityAtMillis = startedAt
        lastStreamStatusCheckAtMillis = null
        streamLivenessJob = viewModelScope.launch {
            while (
                currentCoroutineContext().isActive &&
                streamLivenessGeneration == generation &&
                ownsStreamTransport(streamId)
            ) {
                delay(STREAM_LIVENESS_TICK_MS)
                if (streamLivenessGeneration != generation || !ownsStreamTransport(streamId)) break
                val current = _state.value
                val hasPendingPrompt = current.pendingApproval != null ||
                    current.pendingClarification != null ||
                    current.isRespondingToPendingPrompt
                if (hasPendingPrompt) {
                    if (current.activeStreamRecoveryState == ActiveStreamRecoveryState.Checking) {
                        _state.update {
                            it.copy(activeStreamRecoveryState = ActiveStreamRecoveryState.Idle, notice = null)
                        }
                    }
                    continue
                }
                when (
                    ChatStreamLivenessPolicy.action(
                        nowMillis = monotonicMillis(),
                        connectionStartedAtMillis = streamConnectionStartedAtMillis,
                        lastProgressAtMillis = lastStreamProgressAtMillis,
                        lastTransportActivityAtMillis = lastStreamTransportActivityAtMillis,
                        lastStatusCheckAtMillis = lastStreamStatusCheckAtMillis,
                        hasPendingPrompt = false,
                        hasRunningTool = current.liveToolActivity != null,
                    )
                ) {
                    ChatStreamLivenessAction.CheckStatus -> recoverTransportQuietStream(
                        streamId = streamId,
                        generation = generation,
                        forceReconnect = false,
                    )
                    ChatStreamLivenessAction.ForceReconnect -> recoverTransportQuietStream(
                        streamId = streamId,
                        generation = generation,
                        forceReconnect = true,
                    )
                    ChatStreamLivenessAction.None -> {
                        if (_state.value.activeStreamRecoveryState == ActiveStreamRecoveryState.Checking &&
                            lastStreamTransportActivityAtMillis != null &&
                            monotonicMillis() - requireNotNull(lastStreamTransportActivityAtMillis) <
                            ChatStreamLivenessTiming().transportFreshIntervalMillis
                        ) {
                            _state.update {
                                it.copy(
                                    activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                                    notice = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun recoverTransportQuietStream(
        streamId: String,
        generation: Long,
        forceReconnect: Boolean,
    ) {
        if (streamLivenessGeneration != generation || !ownsStreamTransport(streamId)) return
        val activityGeneration = streamActivityGeneration
        lastStreamStatusCheckAtMillis = monotonicMillis()
        _state.update {
            it.copy(
                activeStreamRecoveryState = ActiveStreamRecoveryState.Checking,
                notice = ActiveStreamRecoveryState.Checking.label,
                error = null,
            )
        }

        val statusResult = runSuspendCatching { repository.chatStreamStatus(streamId) }
        if (
            streamLivenessGeneration != generation ||
            streamActivityGeneration != activityGeneration ||
            !ownsStreamTransport(streamId) ||
            _state.value.activeStreamRecoveryState != ActiveStreamRecoveryState.Checking
        ) {
            return
        }

        statusResult
            .onSuccess { status ->
                if (!status.isActiveFor(streamId)) {
                    streamLivenessGeneration += 1
                    streamJob?.cancel()
                    streamJob = null
                    repository.clearStreamCursor(streamId)
                    stopPendingPromptPolling(clearPrompts = true)
                    _state.update {
                        it.copy(
                            isStreaming = false,
                            activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                            activeStreamId = null,
                            liveReasoning = "",
                            liveToolActivity = null,
                            notice = null,
                            error = status.error.nonBlank(),
                        )
                    }
                    refreshAfterInactiveStream()
                } else if (forceReconnect) {
                    reconnectTransportQuietStream(streamId, replayAfterSeq(status, streamId))
                }
            }
            .onFailure {
                if (forceReconnect) {
                    reconnectTransportQuietStream(streamId, repository.replayAfterSeq(streamId))
                }
            }
    }

    private fun reconnectTransportQuietStream(streamId: String, replayAfterSeq: Int?) {
        if (!ownsStreamTransport(streamId) || _state.value.activeStreamRecoveryState != ActiveStreamRecoveryState.Checking) return
        if (replayAfterSeq == null) reconcileFinalTranscriptForStreamId = streamId
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

    private fun recordStreamTransportActivity(demoteChecking: Boolean) {
        lastStreamTransportActivityAtMillis = monotonicMillis()
        if (demoteChecking) {
            val nextState = ChatStreamLivenessPolicy.stateAfterHeartbeat(_state.value.activeStreamRecoveryState)
            if (nextState != _state.value.activeStreamRecoveryState) {
                streamActivityGeneration += 1
                _state.update { it.copy(activeStreamRecoveryState = nextState, notice = null) }
            }
        }
    }

    private fun markStreamProgress() {
        val now = monotonicMillis()
        lastStreamProgressAtMillis = now
        lastStreamTransportActivityAtMillis = now
        lastStreamStatusCheckAtMillis = null
        streamActivityGeneration += 1
        clearStreamRecoveryState()
    }

    private fun stopStreamLivenessMonitoring() {
        streamLivenessGeneration += 1
        streamActivityGeneration += 1
        streamLivenessJob?.cancel()
        streamLivenessJob = null
        lastStreamProgressAtMillis = null
        lastStreamTransportActivityAtMillis = null
        lastStreamStatusCheckAtMillis = null
    }

    private fun handleStreamTransportError(streamId: String, message: String) {
        if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) return
        stopStreamLivenessMonitoring()
        streamRecoveryJob?.cancel()
        streamRecoveryAttempt += 1
        val attempt = streamRecoveryAttempt
        if (!StreamRecoveryBackoffPolicy.shouldRetry(attempt, MAXIMUM_STREAM_RECOVERY_ATTEMPTS)) {
            finishTerminalStream(streamId, "$message Reconnection attempts were exhausted.")
            return
        }
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
            delay(
                StreamRecoveryBackoffPolicy.delayMillis(
                    attempt = attempt,
                    baseDelayMillis = STREAM_RECOVERY_RETRY_DELAY_MS,
                    maximumDelayMillis = STREAM_RECOVERY_MAX_DELAY_MS,
                ),
            )

            val statusResult = runSuspendCatching { repository.chatStreamStatus(streamId) }
            if (_state.value.activeStreamId != streamId) return@launch

            statusResult
                .onSuccess { status ->
                    if (status.isActiveFor(streamId)) {
                        val replayAfterSeq = replayAfterSeq(status, streamId)
                        if (replayAfterSeq == null) reconcileFinalTranscriptForStreamId = streamId
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
                                liveReasoning = "",
                                liveToolActivity = null,
                                notice = null,
                                error = status.error.nonBlank(),
                            )
                        }
                        refreshAfterInactiveStream()
                    }
                }
                .onFailure {
                    if (_state.value.activeStreamId != streamId) return@launch
                    val replayAfterSeq = repository.replayAfterSeq(streamId)
                    if (replayAfterSeq == null) reconcileFinalTranscriptForStreamId = streamId
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

    private suspend fun refreshAfterInactiveStream() {
        when (val result = repository.loadSessionSnapshot(sessionId)) {
            is ResultState.Data -> applySessionSnapshot(result.value, fromCache = result.fromCache) {
                it.copy(
                    isStreaming = false,
                    activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                    activeStreamId = null,
                )
            }
            is ResultState.Error -> _state.update { it.copy(error = result.message) }
            ResultState.Loading -> Unit
        }
        drainQueuedSlashMessageIfIdle()
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
            if (last?.role == "assistant") {
                messages[messages.lastIndex] = last.copy(id = "streaming", content = text)
            } else {
                messages += ChatMessage(id = "streaming", role = "assistant", content = text)
            }
            current.copy(messages = messages)
        }
    }

    private fun enqueueStreamingAssistantText(streamId: String, text: String) {
        if (text.isEmpty()) return
        if (streamPacingOwnerId != streamId) {
            flushPendingStreamingAssistant()
            streamPacingOwnerId = streamId
        }
        pendingStreamingAssistantText += text
        if (streamPacingJob?.isActive == true) return

        streamPacingJob = viewModelScope.launch {
            delay(STREAMING_INITIAL_REVEAL_DELAY_MS)
            while (streamPacingOwnerId == streamId && pendingStreamingAssistantText.isNotEmpty()) {
                drainPendingStreamingAssistant()
                if (pendingStreamingAssistantText.isNotEmpty()) delay(STREAMING_WORD_REVEAL_CADENCE_MS)
            }
            if (streamPacingOwnerId == streamId) streamPacingJob = null
        }
    }

    private fun attachGoalKickoffStream(responseStreamId: String?) {
        viewModelScope.launch {
            val result = repository.loadSessionSnapshot(sessionId)
            val data = result as? ResultState.Data
            val snapshot = data?.value
            if (snapshot != null) {
                applySessionSnapshot(snapshot, fromCache = data.fromCache)
            }
            val streamId = snapshot?.activeStreamId.nonBlank() ?: responseStreamId.nonBlank()
            if (streamId == null) {
                if (result is ResultState.Error) {
                    _state.update { it.copy(error = result.message) }
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isStreaming = true,
                    activeStreamId = streamId,
                    activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                    error = null,
                )
            }
            attachStream(streamId, replayAfterSeq = 0)
            startPendingPromptPolling()
        }
    }

    private fun drainPendingStreamingAssistant(maximumWordUnits: Int? = null): Boolean {
        val pending = pendingStreamingAssistantText
        if (pending.isEmpty()) return false
        val quota = maximumWordUnits ?: StreamingWordDrainPolicy.drainQuota(
            backlogUnitCount = StreamingWordDrainPolicy.unitCount(pending),
            cadenceMillis = STREAMING_WORD_REVEAL_CADENCE_MS,
            maximumLagMillis = STREAMING_MAX_REVEAL_LAG_MS,
        )
        val (head, tail) = StreamingWordDrainPolicy.splitAtUnitBoundary(pending, quota)
        if (head.isEmpty()) return false
        pendingStreamingAssistantText = tail
        upsertStreamingAssistant(_state.value.streamingAssistantText() + head)
        return true
    }

    private fun flushPendingStreamingAssistant() {
        streamPacingJob?.cancel()
        streamPacingJob = null
        if (pendingStreamingAssistantText.isNotEmpty()) {
            drainPendingStreamingAssistant(maximumWordUnits = Int.MAX_VALUE)
        }
        pendingStreamingAssistantText = ""
        streamPacingOwnerId = null
    }

    private fun ChatUiState.streamingAssistantText(): String {
        messages.lastOrNull { it.role == "assistant" && it.id == "streaming" }?.let { return it.displayText }
        val latestUserIndex = messages.indexOfLast { it.role == "user" }
        return messages.drop(latestUserIndex + 1)
            .lastOrNull { it.role == "assistant" }
            ?.displayText
            .orEmpty()
    }

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
        streamRecoveryAttempt = 0
        streamRecoveryJob?.takeIf { it.isActive }?.cancel()
        streamRecoveryJob = null
        if (_state.value.isRecoveringStream) {
            _state.update { it.copy(activeStreamRecoveryState = ActiveStreamRecoveryState.Idle, notice = null) }
        }
    }

    private fun restoreFailedSend(
        optimisticMessageId: String?,
        text: String,
        attachments: List<UploadResponse>,
        hadPersistedConversation: Boolean,
        message: String,
    ) {
        _state.update { current ->
            val restoredDraft = when {
                current.draft.isBlank() -> text
                current.draft == text -> current.draft
                else -> "$text\n\n${current.draft}"
            }
            current.copy(
                messages = optimisticMessageId?.let { id -> current.messages.filterNot { it.id == id } } ?: current.messages,
                hasPersistedConversation = hadPersistedConversation,
                draft = restoredDraft,
                pendingAttachments = (attachments + current.pendingAttachments).distinctBy { it.path ?: it.filename },
                isStreaming = false,
                activeStreamId = null,
                error = message,
            )
        }
        persistPendingState()
    }

    private fun enqueuePendingSteerLeftover(text: String) {
        val message = text.trim()
        if (message.isBlank()) return
        queuedSlashMessages.addFirst(QueuedDraft(message, emptyList()))
        persistQueuedDrafts()
        _state.update { it.copy(notice = "Steering arrived too late and was queued for the next turn.") }
    }

    private fun applyApprovalPending(response: com.uzairansar.hermex.core.model.ApprovalPendingResponse) {
        val pending = response.pending?.takeUnless { it.isEmpty }
        _state.update {
            if (it.isSessionApprovalBypassEnabled) {
                it.copy(pendingApproval = null, pendingApprovalCount = 0)
            } else {
                it.copy(
                    pendingApproval = pending,
                    pendingApprovalCount = if (pending == null) 0 else response.displayPendingCount,
                )
            }
        }
    }

    private fun applyClarificationPending(response: com.uzairansar.hermex.core.model.ClarificationPendingResponse) {
        val pending = response.pending?.takeUnless { it.isEmpty }
        _state.update {
            it.copy(
                pendingClarification = pending,
                pendingClarificationCount = if (pending == null) 0 else response.displayPendingCount,
            )
        }
    }

    private fun finishTerminalStream(streamId: String, error: String? = null) {
        if (!ownsStreamTransport(streamId)) return
        flushPendingStreamingAssistant()
        if (reconcileFinalTranscriptForStreamId == streamId) reconcileFinalTranscriptForStreamId = null
        if (completedResponseStreamId == streamId) completedResponseStreamId = null
        repository.clearStreamCursor(streamId)
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        streamRecoveryAttempt = 0
        stopPendingPromptPolling(clearPrompts = true)
        _state.update {
            it.copy(
                isStreaming = false,
                activeStreamRecoveryState = ActiveStreamRecoveryState.Idle,
                activeStreamId = null,
                liveReasoning = "",
                liveToolActivity = null,
                error = error,
            )
        }
        drainQueuedSlashMessageIfIdle()
    }

    private suspend fun completeStream(streamId: String, event: SseEvent.Done) {
        if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) return
        flushPendingStreamingAssistant()
        val completedSession = event.session
        val completedTranscript = completedSession?.takeIf { it.messages?.isNotEmpty() == true }
        val finalTokensPerSecond = event.usage?.tokensPerSecond?.takeIf { it.isFinite() && it > 0.0 }
        completedResponseTokensPerSecond = finalTokensPerSecond.takeIf { completedTranscript == null }
        if (completedTranscript != null) {
            val snapshot = repository.snapshotFromCompletedSession(
                sessionId = sessionId,
                session = completedTranscript,
                streamId = streamId,
                turnTokensPerSecond = finalTokensPerSecond,
            )
            if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) return
            val completedSessionId = completedTranscript.sessionId?.trim()?.takeIf { it.isNotBlank() }
            if (completedSessionId == null || completedSessionId == sessionId) {
                applySessionSnapshot(snapshot) {
                    it.copy(
                        isStreaming = true,
                        activeStreamId = streamId,
                        responseCompletionNeedsTranscriptRefresh = false,
                    )
                }
            } else {
                _state.update { it.copy(openSessionId = completedSessionId) }
            }
        } else if (finalTokensPerSecond != null) {
            _state.update { it.copy(messages = it.messages.withLatestAssistantResponseSpeed(finalTokensPerSecond)) }
        }
        event.usage?.let { usage ->
            if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) return
            _state.update { it.copy(contextWindowSnapshot = usage) }
        }
        if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) return
        completeCurrentResponse(streamId, needsTranscriptRefresh = completedTranscript == null)
    }

    private suspend fun reconcileTerminalStream(
        streamId: String,
        session: com.uzairansar.hermex.core.model.SessionDetail?,
        replacementSessionId: String?,
        error: String?,
    ) {
        if (!ownsStreamTransport(streamId)) return
        val resolvedSessionId = replacementSessionId?.trim()?.takeIf { it.isNotBlank() }
            ?: session?.sessionId?.trim()?.takeIf { it.isNotBlank() }
        if (session != null) {
            val snapshot = repository.snapshotFromCompletedSession(sessionId, session, streamId)
            if (!ownsStreamTransport(streamId)) return
            if (resolvedSessionId == null || resolvedSessionId == sessionId) {
                applySessionSnapshot(snapshot) {
                    it.copy(isStreaming = true, activeStreamId = streamId)
                }
            }
        }
        if (resolvedSessionId != null && resolvedSessionId != sessionId) {
            _state.update { it.copy(openSessionId = resolvedSessionId) }
        }
        finishTerminalStream(streamId, error)
    }

    private fun completeCurrentResponse(streamId: String, needsTranscriptRefresh: Boolean) {
        if (!ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId)) return
        completedResponseStreamId = streamId
        reconcileFinalTranscriptForStreamId = null
        repository.clearStreamCursor(streamId)
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        streamRecoveryAttempt = 0
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

    private fun finishCompletedStreamTransport(streamId: String) {
        if (completedResponseStreamId != streamId) return
        completedResponseStreamId = null
        streamJob = null
    }

    fun refreshCompletedTranscriptIfNeeded() {
        if (!_state.value.responseCompletionNeedsTranscriptRefresh) return
        if (completedTranscriptRefreshJob?.isActive == true) return
        val completionTrigger = _state.value.responseCompletionTrigger
        completedTranscriptRefreshJob = viewModelScope.launch {
            repeat(COMPLETED_TRANSCRIPT_REFRESH_ATTEMPTS) { attempt ->
                if (
                    !_state.value.responseCompletionNeedsTranscriptRefresh ||
                    _state.value.responseCompletionTrigger != completionTrigger ||
                    _state.value.isStreaming
                ) {
                    return@launch
                }
                when (val result = repository.loadSessionSnapshot(sessionId)) {
                    is ResultState.Data -> {
                        if (
                            _state.value.responseCompletionTrigger != completionTrigger ||
                            _state.value.isStreaming
                        ) {
                            return@launch
                        }
                        if (!result.fromCache && result.value.messages.hasAssistantResponseAfterLatestUser()) {
                            val reconciled = result.value.copy(
                                messages = result.value.messages.withLatestAssistantResponseSpeed(completedResponseTokensPerSecond),
                            )
                            repository.cacheMessages(sessionId, reconciled.messages)
                            applySessionSnapshot(reconciled, fromCache = false) {
                                it.copy(
                                    sessionTitle = completedResponseTitleOverride ?: it.sessionTitle,
                                    responseCompletionNeedsTranscriptRefresh = false,
                                )
                            }
                            completedResponseTitleOverride = null
                            completedResponseTokensPerSecond = null
                            return@launch
                        }
                    }
                    is ResultState.Error, ResultState.Loading -> Unit
                }
                if (attempt < COMPLETED_TRANSCRIPT_REFRESH_ATTEMPTS - 1) {
                    delay(COMPLETED_TRANSCRIPT_REFRESH_DELAY_MS)
                }
            }
        }
    }

    private fun finishStream(needsTranscriptRefresh: Boolean = false) {
        flushPendingStreamingAssistant()
        reconcileFinalTranscriptForStreamId = null
        completedResponseStreamId = null
        _state.value.activeStreamId?.let(repository::clearStreamCursor)
        streamJob?.cancel()
        streamRecoveryJob?.cancel()
        streamRecoveryJob = null
        streamRecoveryAttempt = 0
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

    private fun ownsStreamTransport(streamId: String): Boolean =
        ChatStreamOwnershipPolicy.stillOwnsStream(streamId, _state.value.activeStreamId) ||
            completedResponseStreamId == streamId

    private val SseEvent.Error.displayMessage: String
        get() = listOfNotNull(
            message.trim().takeIf { it.isNotBlank() },
            hint?.trim()?.takeIf { it.isNotBlank() },
            details?.trim()?.takeIf { it.isNotBlank() && it != message.trim() },
        )
            .distinct()
            .joinToString("\n\n")
            .take(MAXIMUM_STREAM_ERROR_CHARACTERS)

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

    private suspend fun refreshPendingPrompts() = coroutineScope {
        launch {
            runSuspendCatching { repository.approvalPending(sessionId) }.onSuccess { response ->
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
        }
        launch {
            runSuspendCatching { repository.clarificationPending(sessionId) }.onSuccess { response ->
                val pending = response.pending?.takeUnless { it.isEmpty }
                _state.update {
                    it.copy(
                        pendingClarification = pending,
                        pendingClarificationCount = if (pending == null) 0 else response.displayPendingCount,
                    )
                }
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
        val file = File(context.cacheDir, "attachment-${System.nanoTime()}-$safeName")
        var copied = false
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open attachment." }
                copyAttachmentWithLimit(input, file, MAXIMUM_ATTACHMENT_BYTES)
            }
            copied = true
            file
        } finally {
            if (!copied) runCatching { file.delete() }
        }
    }

    private suspend fun prepareSharedAttachmentUpload(
        context: Context,
        attachment: SharedAttachment,
    ): PendingLocalAttachmentUpload =
        withContext(Dispatchers.IO) {
            val sourceFile = attachment.cachedPath
                ?.let(::File)
                ?.takeIf { it.exists() && it.isFile }
            val file = if (sourceFile != null) {
                val destination = File(context.cacheDir, "attachment-${System.nanoTime()}-${sourceFile.name}")
                sourceFile.inputStream().buffered().use { input ->
                    copyAttachmentWithLimit(input, destination, MAXIMUM_ATTACHMENT_BYTES)
                }
                destination
            } else {
                copyUriToCache(context, Uri.parse(attachment.uri))
            }
            val mimeType = attachment.mimeType ?: runCatching {
                context.contentResolver.getType(Uri.parse(attachment.uri))
            }.getOrNull()
            PendingLocalAttachmentUpload(cachedPath = file.absolutePath, mimeType = mimeType)
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
        const val STREAM_RECOVERY_MAX_DELAY_MS = 12_000L
        const val MAXIMUM_STREAM_RECOVERY_ATTEMPTS = 6
        const val STREAM_LIVENESS_TICK_MS = 1_000L
        const val MAXIMUM_STREAM_ERROR_CHARACTERS = 4_000
        const val COMPLETED_TRANSCRIPT_REFRESH_DELAY_MS = 500L
        const val COMPLETED_TRANSCRIPT_REFRESH_ATTEMPTS = 6
        const val STREAMING_INITIAL_REVEAL_DELAY_MS = 16L
        const val STREAMING_WORD_REVEAL_CADENCE_MS = 48L
        const val STREAMING_MAX_REVEAL_LAG_MS = 1_000L
        const val MAXIMUM_MESSAGE_ATTACHMENTS = 10
        const val MAXIMUM_ATTACHMENT_BYTES = 20L * 1_024L * 1_024L

        val PERSONALITY_CLEAR_ARGS = setOf("none", "default", "clear")
        val REASONING_DISPLAY_ARGS = setOf("show", "hide", "on", "off")

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
            `/reasoning show|hide|none|minimal|low|medium|high|xhigh` - Set reasoning display or effort.
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

private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

private suspend fun <T> resultOrNullPreservingCancellation(block: suspend () -> T): T? = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    null
}
