package com.uzairansar.hermex.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.ui.SavedStatePolicy
import com.uzairansar.hermex.ui.setBoundedEncodedState
import com.uzairansar.hermex.core.runSuspendCatching
import com.uzairansar.hermex.core.network.HermesJson
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ProjectMutationResponse
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionBranchResponse
import com.uzairansar.hermex.core.model.SessionExportFile
import com.uzairansar.hermex.core.model.SessionExportFormat
import com.uzairansar.hermex.core.model.SessionMutationResponse
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.model.isConfirmedMutation
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.preferences.MainPageDisplaySettings
import com.uzairansar.hermex.data.preferences.SessionRowDisplaySettings
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.data.repository.ResultState
import com.uzairansar.hermex.data.repository.SessionPage
import com.uzairansar.hermex.data.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

internal data class ProjectColorOption(
    val name: String,
    val hex: String,
)

internal val ProjectColorPalette = listOf(
    ProjectColorOption(name = "Sky", hex = "#7cb9ff"),
    ProjectColorOption(name = "Gold", hex = "#f5c542"),
    ProjectColorOption(name = "Red", hex = "#e94560"),
    ProjectColorOption(name = "Green", hex = "#50c878"),
    ProjectColorOption(name = "Violet", hex = "#c084fc"),
    ProjectColorOption(name = "Orange", hex = "#fb923c"),
    ProjectColorOption(name = "Cyan", hex = "#67e8f9"),
    ProjectColorOption(name = "Pink", hex = "#f472b6"),
)

internal fun defaultProjectColorHex(existingProjectCount: Int): String =
    ProjectColorPalette[existingProjectCount.coerceAtLeast(0) % ProjectColorPalette.size].hex

data class ScheduledSessionGroups(
    val ordinary: List<SessionSummary>,
    val scheduled: List<SessionSummary>,
    val totalScheduledCount: Int,
) {
    val scheduledPreview: List<SessionSummary>
        get() = scheduled.take(ScheduledPreviewLimit)

    val hasAdditionalScheduledSessions: Boolean
        get() = scheduled.size > scheduledPreview.size

    fun showsDisclosure(isSearchActive: Boolean): Boolean =
        totalScheduledCount > 0 && (!isSearchActive || scheduled.isNotEmpty())

    private companion object {
        const val ScheduledPreviewLimit = 5
    }
}

enum class SessionOpenDestination {
    Chat,
    VoiceChat,
    SharedDraft,
}

internal sealed interface SessionListEvent {
    data class OpenSession(val sessionId: String, val destination: SessionOpenDestination) : SessionListEvent
    data class ExportReady(val file: SessionExportFile) : SessionListEvent
}

data class SessionListUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val selectedProjectId: String? = null,
    val searchQuery: String = "",
    val remoteSearchQuery: String? = null,
    val remoteContentSearchSessionIds: List<String> = emptyList(),
    val isSearchingRemoteSessions: Boolean = false,
    val searchError: String? = null,
    val showArchived: Boolean = false,
    val showCliSessions: Boolean = true,
    val showClaudeCodeSessions: Boolean = true,
    val sessionRowDisplaySettings: SessionRowDisplaySettings = SessionRowDisplaySettings(),
    val mainPageDisplaySettings: MainPageDisplaySettings = MainPageDisplaySettings(),
    val tintPrimaryActionsWithThemeColor: Boolean = false,
    val archivedCount: Int? = null,
    val profileOptions: List<ProfileSummary> = emptyList(),
    val activeProfileName: String? = null,
    val isSingleProfileMode: Boolean = false,
    val isLoadingProfiles: Boolean = false,
    val isSwitchingProfile: Boolean = false,
    val profileError: String? = null,
    val renameSession: SessionSummary? = null,
    val renameDraft: String = "",
    val deleteSession: SessionSummary? = null,
    val branchSession: SessionSummary? = null,
    val branchTitleDraft: String = "",
    val newProjectName: String = "",
    val newProjectColor: String? = null,
    val renameProject: ProjectSummary? = null,
    val renameProjectDraft: String = "",
    val renameProjectColor: String? = null,
    val deleteProject: ProjectSummary? = null,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isViewingCachedData: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
) {
    val visibleSessions: List<SessionSummary>
        get() {
            val archiveFiltered = if (showArchived) {
                sessions.filter { it.archived == true }
            } else {
                sessions.filter { it.archived != true }
            }
            val sourceFiltered = archiveFiltered.filter { showCliSessions || it.isCliSession != true }
                .filter { sessionRowDisplaySettings.showCronSessions || !it.isCronSession }
                .filter { showClaudeCodeSessions || !it.isClaudeCodeSession }
                .filter { sessionRowDisplaySettings.showSubagentSessions || !it.isDelegatedSubagentSession }
            val projectFiltered = selectedProjectId?.let { projectId ->
                sourceFiltered.filter { it.projectId == projectId }
            } ?: sourceFiltered
            val query = searchQuery.normalizedSearchQuery()
            val localMatches = projectFiltered
                .filter { query.isEmpty() || it.searchableText.contains(query) }
                .sortedForSessionList()

            if (query.isEmpty() || remoteSearchQuery != query) return localMatches

            val localMatchIds = localMatches.mapNotNullTo(mutableSetOf()) { it.sessionId }
            val sessionsById = projectFiltered.mapNotNull { session ->
                session.sessionId?.takeIf { it.isNotBlank() }?.let { it to session }
            }.toMap()
            val remoteMatches = remoteContentSearchSessionIds.mapNotNull { sessionId ->
                if (sessionId in localMatchIds) null else sessionsById[sessionId]
            }
            return localMatches + remoteMatches.sortedForSessionList()
        }

    val scheduledSessionGroups: ScheduledSessionGroups
        get() {
            val visible = visibleSessions
            return ScheduledSessionGroups(
                ordinary = visible.filterNot { it.isCronSession },
                scheduled = visible.filter { it.isCronSession && it.archived != true },
                totalScheduledCount = if (sessionRowDisplaySettings.showCronSessions) {
                    sessions.count { it.isCronSession && it.archived != true }
                } else {
                    0
                },
            )
        }

    fun scheduledSessions(searchQuery: String): List<SessionSummary> {
        if (!sessionRowDisplaySettings.showCronSessions) return emptyList()
        val query = searchQuery.normalizedSearchQuery()
        return sessions
            .asSequence()
            .filter { it.archived != true && it.isCronSession }
            .filter { showCliSessions || it.isCliSession != true }
            .filter { showClaudeCodeSessions || !it.isClaudeCodeSession }
            .filter { sessionRowDisplaySettings.showSubagentSessions || !it.isDelegatedSubagentSession }
            .filter { query.isEmpty() || it.searchableText.contains(query) }
            .toList()
            .sortedForSessionList()
    }
}

@Serializable
private data class SessionListSavedState(
    val selectedProjectId: String? = null,
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val renameSession: SessionSummary? = null,
    val renameDraft: String = "",
    val deleteSession: SessionSummary? = null,
    val branchSession: SessionSummary? = null,
    val branchTitleDraft: String = "",
    val newProjectName: String = "",
    val newProjectColor: String? = null,
    val renameProject: ProjectSummary? = null,
    val renameProjectDraft: String = "",
    val renameProjectColor: String? = null,
    val deleteProject: ProjectSummary? = null,
) {
    fun toUiState(): SessionListUiState = SessionListUiState(
        selectedProjectId = selectedProjectId,
        searchQuery = searchQuery,
        showArchived = showArchived,
        renameSession = renameSession,
        renameDraft = renameDraft,
        deleteSession = deleteSession,
        branchSession = branchSession,
        branchTitleDraft = branchTitleDraft,
        newProjectName = newProjectName,
        newProjectColor = newProjectColor,
        renameProject = renameProject,
        renameProjectDraft = renameProjectDraft,
        renameProjectColor = renameProjectColor,
        deleteProject = deleteProject,
        isLoading = true,
    )

    companion object {
        fun from(state: SessionListUiState): SessionListSavedState = SessionListSavedState(
            selectedProjectId = state.selectedProjectId,
            searchQuery = state.searchQuery,
            showArchived = state.showArchived,
            renameSession = state.renameSession,
            renameDraft = state.renameDraft,
            deleteSession = state.deleteSession,
            branchSession = state.branchSession,
            branchTitleDraft = state.branchTitleDraft,
            newProjectName = state.newProjectName,
            newProjectColor = state.newProjectColor,
            renameProject = state.renameProject,
            renameProjectDraft = state.renameProjectDraft,
            renameProjectColor = state.renameProjectColor,
            deleteProject = state.deleteProject,
        )
    }
}

class SessionListViewModel(
    private val repository: SessionRepository,
    private val panelsRepository: PanelsRepository,
    private val localSettingsRepository: LocalSettingsRepository,
    private val serverId: String,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {
    private val eventChannel = Channel<SessionListEvent>(Channel.BUFFERED)
    internal val events = eventChannel.receiveAsFlow()
    private val restoredState = savedStateHandle?.get<String>(SAVED_TRANSIENT_STATE)
        ?.let { encoded -> runCatching { HermesJson.decodeFromString<SessionListSavedState>(encoded) }.getOrNull() }
    private val _state = MutableStateFlow(restoredState?.toUiState() ?: SessionListUiState(isLoading = true))
    val state: StateFlow<SessionListUiState> = _state
    private var refreshJob: Job? = null
    private var remoteSearchJob: Job? = null
    private var profilesJob: Job? = null
    private var profilesGeneration = 0L
    private var hasEnteredComposition = false

    init {
        viewModelScope.launch {
            _state.collectLatest { current ->
                val encoded = withContext(Dispatchers.Default) {
                    HermesJson.encodeToString(SessionListSavedState.from(current))
                }
                savedStateHandle?.setBoundedEncodedState(SAVED_TRANSIENT_STATE, encoded)
            }
        }
        viewModelScope.launch {
            localSettingsRepository.showCliSessions(serverId).collectLatest { enabled ->
                _state.update { it.copy(showCliSessions = enabled) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.showClaudeCodeSessions(serverId).collectLatest { enabled ->
                _state.update { it.copy(showClaudeCodeSessions = enabled) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.sessionRowDisplaySettings.collectLatest { displaySettings ->
                _state.update { it.copy(sessionRowDisplaySettings = displaySettings) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.mainPageDisplaySettings.collectLatest { displaySettings ->
                _state.update { it.copy(mainPageDisplaySettings = displaySettings) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.tintPrimaryActionsWithThemeColor.collectLatest { enabled ->
                _state.update { it.copy(tintPrimaryActionsWithThemeColor = enabled) }
            }
        }
        refresh()
        refreshProfiles()
    }

    fun refresh(clearNotice: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val snapshot = _state.value
            val requestedArchivedMode = snapshot.showArchived
            var cachedPreview: SessionPage? = null
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    notice = if (clearNotice) null else it.notice,
                )
            }
            if (snapshot.sessions.isEmpty()) {
                runSuspendCatching { repository.loadCachedSessions(requestedArchivedMode) }
                    .getOrNull()
                    ?.let { cached ->
                        if (_state.value.showArchived != requestedArchivedMode) return@launch
                        cachedPreview = cached
                        _state.update {
                            it.copy(
                                sessions = cached.sessions,
                                archivedCount = cached.archivedCount,
                                isViewingCachedData = false,
                            )
                        }
                    }
            }
            val projects = async { runSuspendCatching { repository.loadProjects() } }
            val result = repository.loadSessions(includeArchived = requestedArchivedMode)
            if (_state.value.showArchived != requestedArchivedMode) return@launch
            when (result) {
                is ResultState.Data -> {
                    if (result.fromCache) remoteSearchJob?.cancel()
                    _state.update {
                        it.copy(
                            sessions = result.value.sessions,
                            archivedCount = result.value.archivedCount,
                            isViewingCachedData = result.fromCache,
                            remoteContentSearchSessionIds = if (result.fromCache) emptyList() else it.remoteContentSearchSessionIds,
                            isSearchingRemoteSessions = if (result.fromCache) false else it.isSearchingRemoteSessions,
                            isLoading = false,
                        )
                    }
                    if (!result.fromCache) {
                        scheduleRemoteSearch(
                            query = _state.value.searchQuery.normalizedSearchQuery(),
                            delayMillis = 0L,
                        )
                    }
                }
                is ResultState.Error -> _state.update {
                    val shouldRevertCachedPreview = cachedPreview?.let { preview -> it.sessions == preview.sessions } == true
                    it.copy(
                        sessions = if (shouldRevertCachedPreview) snapshot.sessions else it.sessions,
                        archivedCount = if (shouldRevertCachedPreview) snapshot.archivedCount else it.archivedCount,
                        isLoading = false,
                        isViewingCachedData = false,
                        error = result.message,
                    )
                }
                ResultState.Loading -> Unit
            }
            projects.await().onSuccess { loadedProjects ->
                if (_state.value.showArchived == requestedArchivedMode) {
                    _state.update { it.copy(projects = loadedProjects) }
                }
            }
        }
    }

    fun refreshAll() {
        refresh()
        refreshProfiles()
    }

    fun refreshAllOnVisible() {
        if (hasEnteredComposition) {
            refreshAll()
        } else {
            hasEnteredComposition = true
        }
    }

    fun updateSearchQuery(value: String) {
        val boundedValue = SavedStatePolicy.boundedInput(value, SavedStatePolicy.MaximumSearchCharacters)
        val query = boundedValue.normalizedSearchQuery()
        remoteSearchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = boundedValue,
                remoteSearchQuery = query.takeIf(String::isNotEmpty),
                remoteContentSearchSessionIds = emptyList(),
                isSearchingRemoteSessions = false,
                searchError = null,
            )
        }
        scheduleRemoteSearch(query, REMOTE_SEARCH_DEBOUNCE_MILLIS)
    }

    fun searchNow() {
        val query = _state.value.searchQuery.normalizedSearchQuery()
        remoteSearchJob?.cancel()
        _state.update {
            it.copy(
                remoteSearchQuery = query.takeIf(String::isNotEmpty),
                remoteContentSearchSessionIds = emptyList(),
                isSearchingRemoteSessions = false,
                searchError = null,
            )
        }
        scheduleRemoteSearch(query, delayMillis = 0L)
    }

    fun clearSearch() {
        remoteSearchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = "",
                remoteSearchQuery = null,
                remoteContentSearchSessionIds = emptyList(),
                isSearchingRemoteSessions = false,
                searchError = null,
            )
        }
    }

    fun toggleArchived() {
        remoteSearchJob?.cancel()
        _state.update {
            it.copy(
                showArchived = !it.showArchived,
                selectedProjectId = null,
                searchQuery = "",
                remoteSearchQuery = null,
                remoteContentSearchSessionIds = emptyList(),
                isSearchingRemoteSessions = false,
                searchError = null,
            )
        }
        refresh()
    }

    fun refreshProfiles() {
        profilesJob?.cancel()
        val generation = ++profilesGeneration
        profilesJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingProfiles = true, profileError = null) }
            runSuspendCatching { panelsRepository.profiles() }
                .onSuccess { response ->
                    if (generation != profilesGeneration) return@onSuccess
                    _state.update {
                        it.copy(
                            profileOptions = response.profiles.orEmpty(),
                            activeProfileName = response.active ?: it.activeProfileName,
                            isSingleProfileMode = response.singleProfileMode == true,
                            isLoadingProfiles = false,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException || generation != profilesGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            isLoadingProfiles = false,
                            profileError = error.message ?: "Could not load profiles.",
                        )
                    }
                }
        }
    }

    fun switchProfile(profile: ProfileSummary) {
        val profileName = profile.name?.takeIf { it.isNotBlank() } ?: return
        if (profileName == _state.value.activeProfileName) return
        if (_state.value.isSwitchingProfile || _state.value.isMutating) return
        profilesJob?.cancel()
        profilesGeneration += 1
        _state.update {
            it.copy(
                isSwitchingProfile = true,
                isLoadingProfiles = false,
                profileError = null,
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runSuspendCatching { panelsRepository.switchProfile(profileName) }
                .onSuccess { response ->
                    val error = response.error
                    if (error == null) {
                        remoteSearchJob?.cancel()
                        _state.update {
                            it.copy(
                                sessions = emptyList(),
                                projects = emptyList(),
                                archivedCount = null,
                                activeProfileName = profileName,
                                isSwitchingProfile = false,
                                isViewingCachedData = false,
                                remoteSearchQuery = null,
                                remoteContentSearchSessionIds = emptyList(),
                                isSearchingRemoteSessions = false,
                                searchError = null,
                                notice = "Profile set to ${profile.displayName?.takeIf { name -> name.isNotBlank() } ?: profileName}.",
                            )
                        }
                        refresh(clearNotice = false)
                        refreshProfiles()
                    } else {
                        _state.update { it.copy(isSwitchingProfile = false, profileError = error) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSwitchingProfile = false,
                            profileError = error.message ?: "Could not switch profile.",
                        )
                    }
                }
        }
    }

    fun selectProject(projectId: String?) {
        _state.update { it.copy(selectedProjectId = if (it.selectedProjectId == projectId) null else projectId) }
    }

    fun createSession(
        profile: String? = null,
        destination: SessionOpenDestination = SessionOpenDestination.Chat,
    ) {
        if (_state.value.isMutating || _state.value.isSwitchingProfile) return
        _state.update { it.copy(isMutating = true, error = null, notice = null) }
        viewModelScope.launch {
            runSuspendCatching { repository.createSession(profile) }
                .onSuccess { response ->
                    val responseError = response.mutationError("The server could not create a session.")
                    val id = response.session?.sessionId
                    if (responseError != null) {
                        _state.update { it.copy(isMutating = false, error = responseError) }
                        return@onSuccess
                    }
                    if (id.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isMutating = false,
                                error = "The server did not return the new session ID.",
                            )
                        }
                    } else {
                        _state.update { it.copy(isMutating = false, notice = "Session created.") }
                        refresh(clearNotice = false)
                        eventChannel.send(SessionListEvent.OpenSession(id, destination))
                    }
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not create session.") } }
        }
    }

    fun reportActionError(message: String) {
        _state.update { it.copy(isMutating = false, notice = null, error = message) }
    }

    fun togglePin(session: SessionSummary) {
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId ?: return
        mutate("Pin updated.") { repository.pin(id, session.pinned != true).mutationError("The server could not update the pin.") }
    }

    fun toggleArchive(session: SessionSummary) {
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId ?: return
        val archived = session.archived != true
        mutate(if (archived) "Session archived." else "Session restored.") {
            repository.archive(id, archived).mutationError("The server could not update the archive state.")
        }
    }

    fun requestRename(session: SessionSummary) {
        if (rejectReadOnlyMutation(session)) return
        _state.update { it.copy(renameSession = session, renameDraft = session.title.orEmpty(), error = null) }
    }

    fun updateRenameDraft(value: String) {
        _state.update { it.copy(renameDraft = value, error = null) }
    }

    fun dismissRename() {
        _state.update { it.copy(renameSession = null, renameDraft = "") }
    }

    fun confirmRename() {
        val session = _state.value.renameSession ?: return
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId ?: return
        val title = _state.value.renameDraft.trim()
        if (title.isBlank()) {
            _state.update { it.copy(error = "Enter a session title.") }
            return
        }
        _state.update { it.copy(renameSession = null, renameDraft = "") }
        mutate("Session renamed.") { repository.rename(id, title).mutationError("The server could not rename the session.") }
    }

    fun requestDelete(session: SessionSummary) {
        if (rejectReadOnlyMutation(session)) return
        _state.update { it.copy(deleteSession = session, error = null) }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteSession = null) }
    }

    fun confirmDelete() {
        val session = _state.value.deleteSession ?: return
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            try {
                val error = repository.delete(id).mutationError("The server could not delete the session.")
                if (error == null) {
                    _state.update { current ->
                        current.copy(
                            deleteSession = current.deleteSession?.takeUnless { it.sessionId == id },
                            isMutating = false,
                            notice = "Session deleted.",
                        )
                    }
                    refresh(clearNotice = false)
                } else {
                    _state.update { it.copy(isMutating = false, error = error) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { it.copy(isMutating = false, error = error.message ?: "Could not delete session.") }
            }
        }
    }

    fun requestBranch(session: SessionSummary) {
        if (rejectReadOnlyMutation(session)) return
        _state.update { it.copy(branchSession = session, branchTitleDraft = "", error = null) }
    }

    fun updateBranchTitleDraft(value: String) {
        _state.update { it.copy(branchTitleDraft = value, error = null) }
    }

    fun dismissBranch() {
        _state.update { it.copy(branchSession = null, branchTitleDraft = "") }
    }

    fun confirmBranch() {
        val session = _state.value.branchSession ?: return
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId ?: return
        val title = _state.value.branchTitleDraft.trim().ifBlank { null }
        _state.update { it.copy(branchSession = null, branchTitleDraft = "") }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runSuspendCatching { repository.branch(id, title) }
                .onSuccess { response ->
                    val responseError = response.mutationError()
                    val branchedId = response.sessionId
                    if (responseError != null || branchedId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isMutating = false,
                                error = responseError ?: "The server did not return the branched session ID.",
                            )
                        }
                        return@onSuccess
                    }
                    _state.update { it.copy(isMutating = false, notice = "Session branched.") }
                    refresh(clearNotice = false)
                    eventChannel.send(SessionListEvent.OpenSession(branchedId, SessionOpenDestination.Chat))
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not branch session.") } }
        }
    }

    fun duplicate(session: SessionSummary) {
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(error = "The server did not provide a session ID.") }
            return
        }
        if (_state.value.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to duplicate a session.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runSuspendCatching { repository.duplicate(id, duplicateTitle(session)) }
                .onSuccess { result ->
                    val duplicatedSession = result.session
                    if (duplicatedSession == null) {
                        _state.update {
                            it.copy(
                                isMutating = false,
                                error = result.errorMessage ?: "Could not duplicate session.",
                            )
                        }
                        return@onSuccess
                    }
                    _state.update { current ->
                        val sessions = if (current.sessions.any { it.sessionId == duplicatedSession.sessionId }) {
                            current.sessions
                        } else {
                            listOf(duplicatedSession) + current.sessions
                        }
                        current.copy(
                            sessions = sessions,
                            isMutating = false,
                            notice = "Session duplicated.",
                        )
                    }
                    refresh(clearNotice = false)
                    duplicatedSession.sessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
                        eventChannel.send(SessionListEvent.OpenSession(sessionId, SessionOpenDestination.Chat))
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isMutating = false, error = error.message ?: "Could not duplicate session.") }
                }
        }
    }

    fun move(session: SessionSummary, projectId: String?) {
        if (rejectReadOnlyMutation(session)) return
        val id = session.sessionId ?: return
        if (session.projectId == projectId) return
        mutate(if (projectId == null) "Moved to no project." else "Moved to project.") {
            repository.move(id, projectId).mutationError("The server could not move the session.")
        }
    }

    fun exportSession(session: SessionSummary, format: SessionExportFormat) {
        val id = session.sessionId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(error = "The server did not provide a session ID.") }
            return
        }
        if (_state.value.isViewingCachedData) {
            _state.update { it.copy(error = "Reconnect to the server to export a session.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runSuspendCatching { repository.exportSession(id, format, session.title) }
                .onSuccess { file ->
                    _state.update { it.copy(isMutating = false, notice = "Export ready.") }
                    eventChannel.send(SessionListEvent.ExportReady(file))
                }
                .onFailure { error ->
                    _state.update { it.copy(isMutating = false, error = error.message ?: "Export failed.") }
                }
        }
    }

    fun beginCreateProject() {
        _state.update {
            it.copy(
                newProjectName = "",
                newProjectColor = defaultProjectColorHex(it.projects.size),
                error = null,
            )
        }
    }

    fun updateNewProjectName(value: String) {
        _state.update { it.copy(newProjectName = value, error = null) }
    }

    fun updateNewProjectColor(value: String?) {
        _state.update { it.copy(newProjectColor = value, error = null) }
    }

    fun dismissCreateProject() {
        _state.update { it.copy(newProjectName = "", newProjectColor = null) }
    }

    fun createProject() {
        val snapshot = _state.value
        val name = snapshot.newProjectName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Enter a project name.") }
            return
        }
        mutate("Project created.") {
            val error = repository.createProject(name, snapshot.newProjectColor)
                .mutationError("The server could not create the project.")
            if (error == null) _state.update { it.copy(newProjectName = "", newProjectColor = null) }
            error
        }
    }

    fun requestRenameProject(project: ProjectSummary) {
        _state.update {
            it.copy(
                renameProject = project,
                renameProjectDraft = project.name.orEmpty(),
                renameProjectColor = project.color,
                error = null,
            )
        }
    }

    fun updateRenameProjectDraft(value: String) {
        _state.update { it.copy(renameProjectDraft = value, error = null) }
    }

    fun updateRenameProjectColor(value: String?) {
        _state.update { it.copy(renameProjectColor = value, error = null) }
    }

    fun dismissRenameProject() {
        _state.update { it.copy(renameProject = null, renameProjectDraft = "", renameProjectColor = null) }
    }

    fun confirmRenameProject() {
        val snapshot = _state.value
        val project = snapshot.renameProject ?: return
        val id = project.projectId ?: return
        val name = snapshot.renameProjectDraft.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Enter a project name.") }
            return
        }
        _state.update { it.copy(renameProject = null, renameProjectDraft = "", renameProjectColor = null) }
        mutate("Project renamed.") {
            repository.renameProject(id, name, snapshot.renameProjectColor)
                .mutationError("The server could not rename the project.")
        }
    }

    fun requestDeleteProject(project: ProjectSummary) {
        _state.update { it.copy(deleteProject = project, error = null) }
    }

    fun dismissDeleteProject() {
        _state.update { it.copy(deleteProject = null) }
    }

    fun confirmDeleteProject() {
        val project = _state.value.deleteProject ?: return
        val id = project.projectId ?: return
        _state.update {
            it.copy(
                deleteProject = null,
                selectedProjectId = if (it.selectedProjectId == id) null else it.selectedProjectId,
            )
        }
        mutate("Project deleted.") {
            repository.deleteProject(id).mutationError("The server could not delete the project.")
        }
    }

    private fun mutate(success: String, action: suspend () -> String?) {
        if (_state.value.isMutating || _state.value.isSwitchingProfile) return
        _state.update { it.copy(isMutating = true, error = null, notice = null) }
        viewModelScope.launch {
            runSuspendCatching { action() }
                .onSuccess { error ->
                    if (error == null) {
                        _state.update { it.copy(isMutating = false, notice = success) }
                        refresh(clearNotice = false)
                    } else {
                        _state.update { it.copy(isMutating = false, error = error) }
                    }
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Session action failed.") } }
        }
    }

    private fun scheduleRemoteSearch(query: String, delayMillis: Long) {
        remoteSearchJob?.cancel()
        if (query.isEmpty()) return
        val snapshot = _state.value
        if (snapshot.showArchived || snapshot.isViewingCachedData) return

        remoteSearchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            if (_state.value.searchQuery.normalizedSearchQuery() != query) return@launch

            _state.update { it.copy(isSearchingRemoteSessions = true) }
            try {
                val response = repository.searchSessions(query, content = true, depth = 5)
                if (_state.value.searchQuery.normalizedSearchQuery() != query) return@launch
                _state.update {
                    it.copy(
                        remoteSearchQuery = query,
                        remoteContentSearchSessionIds = contentMatchSessionIds(it.sessions, response.sessions),
                        searchError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_state.value.searchQuery.normalizedSearchQuery() == query) {
                    _state.update {
                        it.copy(
                            remoteContentSearchSessionIds = emptyList(),
                            searchError = error.message ?: "Could not search sessions.",
                        )
                    }
                }
            } finally {
                if (_state.value.searchQuery.normalizedSearchQuery() == query) {
                    _state.update { it.copy(isSearchingRemoteSessions = false) }
                }
            }
        }
    }

    private fun duplicateTitle(session: SessionSummary): String {
        val baseTitle = session.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled Session"
        return "$baseTitle (copy)"
    }

    private fun rejectReadOnlyMutation(session: SessionSummary): Boolean {
        if (!session.isSessionReadOnly) return false
        _state.update {
            it.copy(
                renameSession = null,
                deleteSession = null,
                branchSession = null,
                isMutating = false,
                notice = null,
                error = "This session is read-only.",
            )
        }
        return true
    }

    private companion object {
        const val SAVED_TRANSIENT_STATE = "session_list_transient_state"
    }
}

private const val REMOTE_SEARCH_DEBOUNCE_MILLIS = 350L

private fun SessionMutationResponse.mutationError(fallback: String): String? =
    error?.trim()?.takeIf { it.isNotBlank() } ?: fallback.takeUnless { isConfirmedMutation() }

private fun ProjectMutationResponse.mutationError(fallback: String): String? =
    error?.trim()?.takeIf { it.isNotBlank() } ?: fallback.takeUnless { isConfirmedMutation() }

private fun SessionBranchResponse.mutationError(): String? = error?.trim()?.takeIf { it.isNotBlank() }

private fun String.normalizedSearchQuery(): String = trim().lowercase()

private val SessionSummary.searchableText: String
    get() = listOfNotNull(title, workspace, model, modelProvider, profile, sourceLabel)
        .joinToString(" ")
        .lowercase()

private val SessionSummary.sessionListTimestamp: Double
    get() = lastMessageAt ?: updatedAt ?: createdAt ?: 0.0

private fun List<SessionSummary>.sortedForSessionList(): List<SessionSummary> = sortedWith(
    compareByDescending<SessionSummary> { it.pinned == true }
        .thenByDescending { it.sessionListTimestamp },
)

internal fun contentMatchSessionIds(
    loadedSessions: List<SessionSummary>,
    searchResults: List<SessionSummary>,
): List<String> {
    val loadedIds = loadedSessions.mapNotNullTo(mutableSetOf()) { session ->
        if (session.archived == true) null else session.sessionId?.takeIf { it.isNotBlank() }
    }
    val seen = mutableSetOf<String>()
    return searchResults.mapNotNull { result ->
        result.sessionId?.takeIf { sessionId ->
            result.matchType.equals("content", ignoreCase = true) &&
                sessionId in loadedIds &&
                seen.add(sessionId)
        }
    }
}

private val SessionSummary.isCronSession: Boolean
    get() {
        if (sessionId?.trim()?.startsWith("cron_", ignoreCase = true) == true) return true
        return listOfNotNull(sessionSource, sourceTag, rawSource, sourceLabel)
            .map(String::trim)
            .any { source -> source.contains("cron", ignoreCase = true) }
    }
