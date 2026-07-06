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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionListUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val selectedProjectId: String? = null,
    val searchQuery: String = "",
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
    val renameProject: ProjectSummary? = null,
    val renameProjectDraft: String = "",
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
            return selectedProjectId?.let { projectId ->
                sourceFiltered.filter { it.projectId == projectId }
            } ?: sourceFiltered
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
        viewModelScope.launch {
            val snapshot = _state.value
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    notice = if (clearNotice) null else it.notice,
                )
            }
            runCatching { repository.loadProjects() }
                .onSuccess { projects -> _state.update { it.copy(projects = projects) } }
            val query = snapshot.searchQuery.trim()
            if (query.isNotEmpty()) {
                runCatching { repository.searchSessions(query) }
                    .onSuccess { page ->
                        _state.update {
                            it.copy(
                                sessions = page.sessions,
                                archivedCount = page.archivedCount ?: it.archivedCount,
                                isViewingCachedData = false,
                                isLoading = false,
                            )
                        }
                    }
                    .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.message ?: "Could not search sessions.") } }
            } else {
                when (val result = repository.loadSessions(includeArchived = snapshot.showArchived)) {
                    is ResultState.Data -> _state.update {
                        it.copy(
                            sessions = result.value.sessions,
                            archivedCount = result.value.archivedCount,
                            isViewingCachedData = result.fromCache,
                            isLoading = false,
                        )
                    }
                    is ResultState.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                    ResultState.Loading -> Unit
                }
            }
        }
    }

    fun updateSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value, error = null) }
    }

    fun clearSearch() {
        _state.update { it.copy(searchQuery = "", selectedProjectId = null) }
        refresh()
    }

    fun toggleArchived() {
        _state.update { it.copy(showArchived = !it.showArchived, selectedProjectId = null, searchQuery = "") }
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

    fun updateNewProjectName(value: String) {
        _state.update { it.copy(newProjectName = value, error = null) }
    }

    fun createProject() {
        val name = _state.value.newProjectName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Enter a project name.") }
            return
        }
        mutate("Project created.") {
            repository.createProject(name)
            _state.update { it.copy(newProjectName = "") }
            null
        }
    }

    fun requestRenameProject(project: ProjectSummary) {
        _state.update { it.copy(renameProject = project, renameProjectDraft = project.name.orEmpty(), error = null) }
    }

    fun updateRenameProjectDraft(value: String) {
        _state.update { it.copy(renameProjectDraft = value, error = null) }
    }

    fun dismissRenameProject() {
        _state.update { it.copy(renameProject = null, renameProjectDraft = "") }
    }

    fun confirmRenameProject() {
        val project = _state.value.renameProject ?: return
        val id = project.projectId ?: return
        val name = _state.value.renameProjectDraft.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Enter a project name.") }
            return
        }
        _state.update { it.copy(renameProject = null, renameProjectDraft = "") }
        mutate("Project renamed.") {
            repository.renameProject(id, name)
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

    private fun duplicateTitle(session: SessionSummary): String {
        val baseTitle = session.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled Session"
        return "$baseTitle (copy)"
    }
}

private val SessionSummary.isCronSession: Boolean
    get() = listOfNotNull(sourceTag, sessionSource, sourceLabel)
        .any { source -> source.contains("cron", ignoreCase = true) }
