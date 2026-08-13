package com.uzairansar.hermex.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.WorkspaceMutationResponse
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceManagerState(
    val workspaces: List<WorkspaceRoot> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
    val mutationVersion: Int = 0,
)

class WorkspaceManagerViewModel(
    private val repository: WorkspaceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceManagerState())
    val state: StateFlow<WorkspaceManagerState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val workspaces = repository.workspaces().validWorkspaceRows()
                _state.update { it.copy(workspaces = workspaces, isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { it.copy(isLoading = false, error = error.message ?: "Could not load workspaces.") }
            }
        }
    }

    fun add(path: String, name: String?, create: Boolean) {
        val trimmedPath = path.trim()
        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedPath.isEmpty()) {
            _state.update { it.copy(error = "Enter a workspace path.") }
            return
        }
        mutate { repository.add(trimmedPath, trimmedName, create.takeIf { it }) }
    }

    fun rename(workspace: WorkspaceRoot, name: String) {
        val path = workspace.path?.trim().orEmpty()
        val trimmedName = name.trim()
        if (path.isEmpty() || trimmedName.isEmpty()) {
            _state.update { it.copy(error = "Enter a workspace name.") }
            return
        }
        mutate { repository.rename(path, trimmedName) }
    }

    fun remove(workspace: WorkspaceRoot) {
        val path = workspace.path?.trim().orEmpty()
        if (path.isEmpty()) return
        mutate { repository.remove(path) }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val snapshot = _state.value
        if (snapshot.isMutating || fromIndex !in snapshot.workspaces.indices || toIndex !in snapshot.workspaces.indices) return
        val reordered = snapshot.workspaces.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        val paths = reordered.mapNotNull { it.path?.trim()?.takeIf(String::isNotEmpty) }
        if (paths.size != reordered.size) return

        _state.update { it.copy(workspaces = reordered, isMutating = true, error = null) }
        viewModelScope.launch {
            try {
                val response = repository.reorder(paths)
                val echoed = response.confirmedWorkspacesOrThrow()
                val canonical = echoed ?: runCatching { repository.workspaces() }.getOrNull()
                _state.update {
                    it.copy(
                        workspaces = canonical?.validWorkspaceRows() ?: reordered,
                        isMutating = false,
                        error = if (canonical == null) "Workspaces were reordered, but the updated list could not be refreshed." else null,
                        mutationVersion = it.mutationVersion + 1,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val canonical = runCatching { repository.workspaces().validWorkspaceRows() }.getOrDefault(snapshot.workspaces)
                _state.update {
                    it.copy(
                        workspaces = canonical,
                        isMutating = false,
                        error = error.message ?: "Could not reorder workspaces.",
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun mutate(request: suspend () -> WorkspaceMutationResponse) {
        if (_state.value.isMutating) return
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null) }
            try {
                val response = request()
                val echoed = response.confirmedWorkspacesOrThrow()
                val canonical = echoed ?: runCatching { repository.workspaces() }.getOrNull()
                _state.update {
                    it.copy(
                        workspaces = canonical?.validWorkspaceRows() ?: it.workspaces,
                        isMutating = false,
                        error = if (canonical == null) "The workspace change succeeded, but the updated list could not be refreshed." else null,
                        mutationVersion = it.mutationVersion + 1,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { it.copy(isMutating = false, error = error.message ?: "Could not update workspaces.") }
            }
        }
    }
}

private fun WorkspaceMutationResponse.confirmedWorkspacesOrThrow(): List<WorkspaceRoot>? {
    if (!isConfirmed) throw IllegalStateException(error?.takeIf { it.isNotBlank() } ?: "The server did not confirm the workspace change.")
    return workspaces
}

private fun List<WorkspaceRoot>.validWorkspaceRows(): List<WorkspaceRoot> =
    filter { !it.path.isNullOrBlank() }
