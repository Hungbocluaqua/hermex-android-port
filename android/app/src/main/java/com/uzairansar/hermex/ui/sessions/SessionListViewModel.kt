package com.uzairansar.hermex.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.data.repository.ResultState
import com.uzairansar.hermex.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionListUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val selectedProjectId: String? = null,
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val archivedCount: Int? = null,
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
        get() = selectedProjectId?.let { projectId ->
            sessions.filter { it.projectId == projectId }
        } ?: sessions
}

class SessionListViewModel(
    private val repository: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionListUiState(isLoading = true))
    val state: StateFlow<SessionListUiState> = _state

    init {
        refresh()
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
        _state.update { it.copy(showArchived = !it.showArchived, selectedProjectId = null) }
        refresh()
    }

    fun selectProject(projectId: String?) {
        _state.update { it.copy(selectedProjectId = if (it.selectedProjectId == projectId) null else projectId) }
    }

    fun createSession(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { repository.createSession()?.sessionId }
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

    fun move(session: SessionSummary, projectId: String?) {
        val id = session.sessionId ?: return
        if (session.projectId == projectId) return
        mutate(if (projectId == null) "Moved to no project." else "Moved to project.") {
            repository.move(id, projectId)?.let { null }
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
}
