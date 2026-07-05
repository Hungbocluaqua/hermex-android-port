package com.uzairansar.hermex.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.PendingApproval
import com.uzairansar.hermex.core.model.PendingClarification
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.network.SseEvent
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.ResultState
import com.uzairansar.hermex.data.share.SharedAttachment
import com.uzairansar.hermex.data.share.SharedDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

private data class SessionActionResult(
    val error: String? = null,
    val notice: String? = null,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val modelOptions: List<ModelSummary> = emptyList(),
    val profileOptions: List<ProfileSummary> = emptyList(),
    val reasoningOptions: List<String> = emptyList(),
    val selectedModel: ModelSummary? = null,
    val selectedProfile: ProfileSummary? = null,
    val selectedReasoning: String? = null,
    val pendingAttachments: List<UploadResponse> = emptyList(),
    val pendingApproval: PendingApproval? = null,
    val pendingApprovalCount: Int = 0,
    val pendingClarification: PendingClarification? = null,
    val pendingClarificationCount: Int = 0,
    val clarificationDraft: String = "",
    val isRespondingToPendingPrompt: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingComposerConfig: Boolean = false,
    val isUploadingAttachment: Boolean = false,
    val isRecordingVoiceNote: Boolean = false,
    val isTranscribingVoiceNote: Boolean = false,
    val isRunningSessionAction: Boolean = false,
    val isStreaming: Boolean = false,
    val activeStreamId: String? = null,
    val isViewingCachedData: Boolean = false,
    val liveReasoning: String = "",
    val liveToolActivity: String? = null,
    val notice: String? = null,
    val error: String? = null,
)

class ChatViewModel(
    private val sessionId: String,
    private val repository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    private var streamJob: Job? = null
    private var pendingPromptJob: Job? = null

    init {
        load()
        loadComposerConfig()
    }

    fun updateDraft(value: String) = _state.update { it.copy(draft = value, error = null, notice = null) }
    fun updateClarificationDraft(value: String) = _state.update { it.copy(clarificationDraft = value, error = null) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.loadMessages(sessionId)) {
                is ResultState.Data -> _state.update {
                    it.copy(messages = result.value, isViewingCachedData = result.fromCache, isLoading = false)
                }
                is ResultState.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                ResultState.Loading -> Unit
            }
        }
    }

    fun loadComposerConfig() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingComposerConfig = true) }
            runCatching {
                val models = repository.models()
                val profiles = repository.profiles()
                val selectedModel = models.firstOrNull()
                val reasoning = repository.reasoning(selectedModel)
                Triple(models, profiles, reasoning)
            }.onSuccess { (models, profiles, reasoning) ->
                _state.update {
                    it.copy(
                        modelOptions = models,
                        profileOptions = profiles,
                        reasoningOptions = reasoning.supportedEfforts ?: listOf("low", "medium", "high"),
                        selectedModel = it.selectedModel ?: models.firstOrNull(),
                        selectedProfile = it.selectedProfile ?: profiles.firstOrNull(),
                        selectedReasoning = it.selectedReasoning ?: reasoning.effort,
                        isLoadingComposerConfig = false,
                    )
                }
            }.onFailure {
                _state.update { current -> current.copy(isLoadingComposerConfig = false) }
            }
        }
    }

    fun cycleModel() {
        val state = _state.value
        val next = state.modelOptions.nextAfter(state.selectedModel)
        _state.update { it.copy(selectedModel = next) }
        viewModelScope.launch {
            runCatching { repository.reasoning(next) }.onSuccess { reasoning ->
                _state.update {
                    it.copy(
                        reasoningOptions = reasoning.supportedEfforts ?: it.reasoningOptions,
                        selectedReasoning = reasoning.effort ?: it.selectedReasoning,
                    )
                }
            }
        }
    }

    fun cycleProfile() {
        val next = _state.value.profileOptions.nextAfter(_state.value.selectedProfile)
        _state.update { it.copy(selectedProfile = next) }
        if (next != null) {
            viewModelScope.launch {
                runCatching { repository.switchProfile(next) }
                    .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch profile.") } }
            }
        }
    }

    fun cycleReasoning() {
        val next = _state.value.reasoningOptions.nextAfter(_state.value.selectedReasoning) ?: return
        val model = _state.value.selectedModel
        _state.update { it.copy(selectedReasoning = next) }
        viewModelScope.launch {
            runCatching { repository.setReasoning(next, model) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch reasoning.") } }
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
            .onSuccess { _state.update { it.copy(isRecordingVoiceNote = true, error = null) } }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not start recording.") } }
    }

    fun stopAndTranscribeVoiceNote(recorder: VoiceNoteRecorder) {
        val file = recorder.stop()
        _state.update { it.copy(isRecordingVoiceNote = false) }
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
        _state.update { it.copy(isRecordingVoiceNote = false, isTranscribingVoiceNote = false) }
    }

    suspend fun synthesizeSpeech(text: String): ByteArray? =
        runCatching { repository.synthesizeSpeech(text) }.getOrNull()

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

    fun compressContext(focusTopic: String? = null) {
        sessionAction("Wait for the current response to finish before compressing context.") {
            val response = repository.compressSession(sessionId, focusTopic?.trim()?.ifBlank { null })
            if (response.error != null) {
                SessionActionResult(error = response.error)
            } else {
                response.session?.messages?.let { messages ->
                    _state.update { it.copy(messages = messages, isViewingCachedData = false) }
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

    private suspend fun submitMessage(text: String, snapshot: ChatUiState) {
            val optimistic = ChatMessage(role = "user", content = text)
            _state.update { it.copy(messages = it.messages + optimistic, draft = "", pendingAttachments = emptyList(), isStreaming = true, error = null) }
            runCatching {
                snapshot.selectedReasoning?.let { repository.setReasoning(it, snapshot.selectedModel) }
                snapshot.selectedProfile?.let { repository.switchProfile(it) }
                repository.send(sessionId, text, model = snapshot.selectedModel, attachments = snapshot.pendingAttachments)
            }
                .onSuccess { streamId ->
                    if (streamId.isNullOrBlank()) {
                        _state.update { it.copy(isStreaming = false, error = "Server did not return a stream id.") }
                    } else {
                        _state.update { it.copy(activeStreamId = streamId) }
                        attachStream(streamId)
                        startPendingPromptPolling()
                    }
                }
                .onFailure { error -> _state.update { it.copy(isStreaming = false, error = error.message ?: "Send failed.") } }
    }

    fun cancel() {
        viewModelScope.launch {
            cancelActiveStream()
        }
    }

    private suspend fun cancelActiveStream() {
        val streamId = _state.value.activeStreamId
        if (streamId != null) runCatching { repository.cancel(streamId) }
        streamJob?.cancel()
        stopPendingPromptPolling(clearPrompts = true)
        _state.update {
            it.copy(
                isStreaming = false,
                activeStreamId = null,
                liveToolActivity = null,
                pendingApproval = null,
                pendingClarification = null,
            )
        }
    }

    private fun handleSlashCommand(text: String, snapshot: ChatUiState): Boolean {
        if (!text.startsWith("/")) return false
        val withoutSlash = text.drop(1).trimStart()
        val command = withoutSlash.substringBefore(' ').lowercase()
        val args = withoutSlash.substringAfter(' ', "").trim()
        when (command) {
            "help", "commands" -> {
                _state.update { it.copy(draft = "", notice = null, error = null) }
                appendLocalAssistant(SLASH_HELP)
            }
            "clear" -> _state.update { it.copy(messages = emptyList(), draft = "", notice = "Transcript cleared locally.", error = null) }
            "stop" -> {
                _state.update { it.copy(draft = "") }
                cancel()
            }
            "steer" -> {
                if (args.isBlank()) _state.update { it.copy(error = "Usage: /steer <message>") } else steer(args)
            }
            "interrupt" -> {
                if (args.isBlank()) {
                    _state.update { it.copy(error = "Usage: /interrupt <message>") }
                } else {
                    viewModelScope.launch {
                        cancelActiveStream()
                        submitMessage(args, snapshot.copy(draft = args, pendingAttachments = emptyList(), isStreaming = false))
                    }
                }
            }
            "goal" -> submitGoal(args)
            "compress", "compact" -> compressContext(args)
            "undo" -> undoLastExchange()
            "retry" -> retryLastTurn()
            "model" -> switchModel(args)
            "profile" -> switchProfile(args)
            "reasoning" -> switchReasoning(args)
            "status" -> appendLocalAssistant(statusText())
            else -> {
                _state.update { it.copy(draft = "", error = "Unknown command: /$command") }
                appendLocalAssistant(SLASH_HELP)
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
        _state.update { it.copy(draft = "", selectedModel = model, notice = "Model set to ${model.label ?: model.name ?: model.id}.", error = null) }
    }

    private fun switchProfile(args: String) {
        val query = args.trim()
        val profile = _state.value.profileOptions.firstOrNull {
            val values = listOfNotNull(it.name, it.displayName).map { value -> value.lowercase() }
            values.any { value -> value == query.lowercase() || value.contains(query.lowercase()) }
        }
        if (query.isBlank() || profile == null) {
            _state.update { it.copy(error = "Profile not found.") }
            return
        }
        _state.update { it.copy(draft = "", selectedProfile = profile, notice = "Profile set to ${profile.displayName ?: profile.name}.", error = null) }
        viewModelScope.launch {
            runCatching { repository.switchProfile(profile) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch profile.") } }
        }
    }

    private fun switchReasoning(args: String) {
        val query = args.trim()
        val effort = _state.value.reasoningOptions.firstOrNull { it.equals(query, ignoreCase = true) }
        if (query.isBlank() || effort == null) {
            _state.update { it.copy(error = "Reasoning level not found.") }
            return
        }
        val model = _state.value.selectedModel
        _state.update { it.copy(draft = "", selectedReasoning = effort, notice = "Reasoning set to $effort.", error = null) }
        viewModelScope.launch {
            runCatching { repository.setReasoning(effort, model) }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not switch reasoning.") } }
        }
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

    private fun statusText(): String {
        val state = _state.value
        return listOf(
            "Streaming: ${if (state.isStreaming) "yes" else "no"}",
            "Messages: ${state.messages.size}",
            "Model: ${state.selectedModel?.label ?: state.selectedModel?.name ?: state.selectedModel?.id ?: "default"}",
            "Profile: ${state.selectedProfile?.displayName ?: state.selectedProfile?.name ?: "default"}",
            "Reasoning: ${state.selectedReasoning ?: "default"}",
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

    private fun attachStream(streamId: String) {
        streamJob?.cancel()
        var assistantText = ""
        streamJob = viewModelScope.launch {
            repository.stream(streamId).collect { event ->
                when (event) {
                    is SseEvent.Token -> {
                        assistantText += event.text
                        upsertStreamingAssistant(assistantText)
                    }
                    is SseEvent.InterimAssistant -> {
                        if (assistantText.isBlank()) {
                            assistantText = event.text
                            upsertStreamingAssistant(assistantText)
                        }
                    }
                    is SseEvent.Reasoning -> _state.update { it.copy(liveReasoning = it.liveReasoning + event.text) }
                    is SseEvent.ToolStarted -> _state.update { it.copy(liveToolActivity = event.event.name ?: "Tool running") }
                    is SseEvent.ToolCompleted -> _state.update { it.copy(liveToolActivity = null) }
                    is SseEvent.Title -> Unit
                    is SseEvent.Done, SseEvent.StreamEnd -> finishStream()
                    SseEvent.Cancelled -> _state.update { it.copy(isStreaming = false, activeStreamId = null) }
                    is SseEvent.Error -> _state.update { it.copy(isStreaming = false, activeStreamId = null, error = event.message) }
                    is SseEvent.TransportError -> _state.update { it.copy(isStreaming = false, activeStreamId = null, error = event.message) }
                    is SseEvent.PendingSteerLeftover, SseEvent.Ignored -> Unit
                }
            }
        }
    }

    private fun upsertStreamingAssistant(text: String) {
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

    private fun finishStream() {
        streamJob?.cancel()
        stopPendingPromptPolling(clearPrompts = true)
        _state.update {
            it.copy(
                isStreaming = false,
                activeStreamId = null,
                liveReasoning = "",
                liveToolActivity = null,
                pendingApproval = null,
                pendingClarification = null,
            )
        }
        load()
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
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open attachment." }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
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
            repository.upload(sessionId, file, mimeType).also {
                if (file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                    file.delete()
                }
            }
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

    private companion object {
        val SLASH_HELP = """
            Available mobile commands:

            `/help` - Show this command list.
            `/clear` - Clear the local transcript.
            `/stop` - Stop the current response.
            `/model <id>` - Switch this session's model.
            `/profile <name>` - Switch profile.
            `/reasoning <level>` - Set reasoning effort.
            `/steer <message>` - Steer the active response.
            `/interrupt <message>` - Stop the active response and send a new message.
            `/goal <text|status|pause|resume|clear>` - Manage the persistent goal.
            `/compress [focus]` - Compress this session's context.
            `/compact [focus]` - Alias for `/compress`.
            `/undo` - Undo the last exchange.
            `/retry` - Retry the last turn.
            `/status` - Show local session status.
        """.trimIndent()
    }
}
