package com.uzairansar.hermex.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionExportFile
import com.uzairansar.hermex.core.model.SessionExportFormat
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.preferences.SessionRowDisplaySettings
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.data.repository.ResultState
import com.uzairansar.hermex.data.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val sessionRowDisplaySettings: SessionRowDisplaySettings = SessionRowDisplaySettings(),
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
}

class SessionListViewModel(
    private val repository: SessionRepository,
    private val panelsRepository: PanelsRepository,
    private val localSettingsRepository: LocalSettingsRepository,
    private val serverId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionListUiState(isLoading = true))
    val state: StateFlow<SessionListUiState> = _state
    private var refreshJob: Job? = null
    private var remoteSearchJob: Job? = null

    init {
        viewModelScope.launch {
            localSettingsRepository.showCliSessions(serverId).collectLatest { enabled ->
                _state.update { it.copy(showCliSessions = enabled) }
            }
        }
        viewModelScope.launch {
            localSettingsRepository.sessionRowDisplaySettings.collectLatest { displaySettings ->
                _state.update { it.copy(sessionRowDisplaySettings = displaySettings) }
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
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    notice = if (clearNotice) null else it.notice,
                )
            }
            try {
                val projects = repository.loadProjects()
                _state.update { it.copy(projects = projects) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Sessions remain usable if project metadata cannot refresh.
            }
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
                is ResultState.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                ResultState.Loading -> Unit
            }
        }
    }

    fun refreshAll() {
        refresh()
        refreshProfiles()
    }

    fun updateSearchQuery(value: String) {
        val query = value.normalizedSearchQuery()
        remoteSearchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = value,
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
        viewModelScope.launch {
            _state.update { it.copy(isLoadingProfiles = true, profileError = null) }
            runCatching { panelsRepository.profiles() }
                .onSuccess { response ->
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
        viewModelScope.launch {
            _state.update { it.copy(isSwitchingProfile = true, profileError = null, notice = null, error = null) }
            runCatching { panelsRepository.switchProfile(profileName) }
                .onSuccess { response ->
                    val error = response.error
                    if (error == null) {
                        _state.update {
                            it.copy(
                                activeProfileName = profileName,
                                isSwitchingProfile = false,
                                notice = "Profile set to ${profile.displayName?.takeIf { name -> name.isNotBlank() } ?: profileName}.",
                            )
                        }
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

    fun createSession(profile: String? = null, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { repository.createSession(profile)?.sessionId }
                .onSuccess { id ->
                    _state.update { it.copy(isMutating = false, notice = "Session created.") }
                    refresh(clearNotice = false)
                    if (!id.isNullOrBlank()) onCreated(id)
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not create session.") } }
        }
    }

    fun togglePin(session: SessionSummary) {
        val id = session.sessionId ?: return
        mutate("Pin updated.") { repository.pin(id, session.pinned != true)?.let { null } }
    }

    fun toggleArchive(session: SessionSummary) {
        val id = session.sessionId ?: return
        val archived = session.archived != true
        mutate(if (archived) "Session archived." else "Session restored.") {
            repository.archive(id, archived)?.let { null }
        }
    }

    fun requestRename(session: SessionSummary) {
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
        val id = session.sessionId ?: return
        val title = _state.value.renameDraft.trim()
        if (title.isBlank()) {
            _state.update { it.copy(error = "Enter a session title.") }
            return
        }
        _state.update { it.copy(renameSession = null, renameDraft = "") }
        mutate("Session renamed.") { repository.rename(id, title)?.let { null } }
    }

    fun requestDelete(session: SessionSummary) {
        _state.update { it.copy(deleteSession = session, error = null) }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteSession = null) }
    }

    fun confirmDelete() {
        val id = _state.value.deleteSession?.sessionId ?: return
        _state.update { it.copy(deleteSession = null) }
        mutate("Session deleted.") {
            repository.delete(id).error
        }
    }

    fun requestBranch(session: SessionSummary) {
        _state.update { it.copy(branchSession = session, branchTitleDraft = "", error = null) }
    }

    fun updateBranchTitleDraft(value: String) {
        _state.update { it.copy(branchTitleDraft = value, error = null) }
    }

    fun dismissBranch() {
        _state.update { it.copy(branchSession = null, branchTitleDraft = "") }
    }

    fun confirmBranch(onCreated: (String) -> Unit) {
        val session = _state.value.branchSession ?: return
        val id = session.sessionId ?: return
        val title = _state.value.branchTitleDraft.trim().ifBlank { null }
        _state.update { it.copy(branchSession = null, branchTitleDraft = "") }
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { repository.branch(id, title) }
                .onSuccess { branchedId ->
                    _state.update { it.copy(isMutating = false, notice = "Session branched.") }
                    refresh(clearNotice = false)
                    if (!branchedId.isNullOrBlank()) onCreated(branchedId)
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not branch session.") } }
        }
    }

    fun duplicate(session: SessionSummary, onCreated: (String) -> Unit) {
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
            runCatching { repository.duplicate(id, duplicateTitle(session)) }
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
                    duplicatedSession.sessionId?.takeIf { it.isNotBlank() }?.let(onCreated)
                }
                .onFailure { error ->
                    _state.update { it.copy(isMutating = false, error = error.message ?: "Could not duplicate session.") }
                }
        }
    }

    fun move(session: SessionSummary, projectId: String?) {
        val id = session.sessionId ?: return
        if (session.projectId == projectId) return
        mutate(if (projectId == null) "Moved to no project." else "Moved to project.") {
            repository.move(id, projectId)?.let { null }
        }
    }

    fun exportSession(
        session: SessionSummary,
        format: SessionExportFormat,
        onExported: (SessionExportFile) -> Unit,
    ) {
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
            runCatching { repository.exportSession(id, format, session.title) }
                .onSuccess { file ->
                    _state.update { it.copy(isMutating = false, notice = "Export ready.") }
                    onExported(file)
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
            repository.createProject(name, snapshot.newProjectColor)
            _state.update { it.copy(newProjectName = "", newProjectColor = null) }
            null
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
            null
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
            repository.deleteProject(id).error
        }
    }

    private fun mutate(success: String, action: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { action() }
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
}

private const val REMOTE_SEARCH_DEBOUNCE_MILLIS = 350L

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
    get() = listOfNotNull(sourceTag, sessionSource, sourceLabel)
        .any { source -> source.contains("cron", ignoreCase = true) }
