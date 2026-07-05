package com.uzairansar.hermex.ui.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.GitBranchRef
import com.uzairansar.hermex.core.model.GitBranchesResponse
import com.uzairansar.hermex.core.model.GitFileChange
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.GitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitBranchOption(
    val name: String,
    val mode: String,
    val subject: String? = null,
    val upstream: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
    val isCurrent: Boolean = false,
) {
    val displayName: String = name.removePrefix("refs/heads/")
}

data class GitCheckoutSelection(
    val ref: String,
    val mode: String,
    val newBranch: String? = null,
    val track: Boolean = false,
) {
    val displayName: String = newBranch ?: ref.removePrefix("refs/heads/")
}

data class GitUiState(
    val branch: String? = null,
    val branches: List<GitBranchOption> = emptyList(),
    val newBranchName: String = "",
    val files: List<GitFileChange> = emptyList(),
    val selectedPath: String? = null,
    val selectedKind: String = "unstaged",
    val diff: String? = null,
    val commitMessage: String = "",
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val pendingCheckout: GitCheckoutSelection? = null,
    val pendingDirtyCheckout: GitCheckoutSelection? = null,
    val showPushConfirm: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class GitViewModel(
    private val sessionId: String,
    private val repository: GitRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state

    init {
        refresh()
    }

    fun refresh(clearNotice: Boolean = true, clearError: Boolean = true) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = if (clearError) null else it.error,
                    notice = if (clearNotice) null else it.notice,
                )
            }
            runCatching {
                val status = repository.status(sessionId)
                val branches = runCatching { repository.branches(sessionId) }.getOrNull()
                status to branches
            }
                .onSuccess { (status, branches) ->
                    val current = branches?.branches?.current ?: branches?.current ?: status.branch
                    _state.update {
                        it.copy(
                            branch = current,
                            branches = branches.toOptions(current),
                            files = status.files.orEmpty(),
                            isLoading = false,
                            error = status.error ?: if (clearError) null else it.error,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.message ?: "Could not load git status.") } }
        }
    }

    fun updateNewBranchName(value: String) {
        _state.update { it.copy(newBranchName = value, error = null) }
    }

    fun selectFile(file: GitFileChange, kind: String = if (file.staged == true) "staged" else "unstaged") {
        val path = file.path ?: return
        viewModelScope.launch {
            _state.update { it.copy(selectedPath = path, selectedKind = kind, diff = null, error = null) }
            runCatching { repository.diff(sessionId, path, kind) }
                .onSuccess { diff -> _state.update { it.copy(diff = diff.diff ?: diff.error ?: "No diff.") } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Could not load diff.") } }
        }
    }

    fun updateCommitMessage(value: String) {
        _state.update { it.copy(commitMessage = value) }
    }

    fun stageSelected() {
        mutateSelected("Staged") { path -> repository.stage(sessionId, listOf(path)).error }
    }

    fun unstageSelected() {
        mutateSelected("Unstaged") { path -> repository.unstage(sessionId, listOf(path)).error }
    }

    fun requestDiscardSelected() {
        if (_state.value.selectedPath != null) {
            _state.update { it.copy(showDiscardConfirm = true, error = null) }
        }
    }

    fun dismissDiscardConfirm() {
        _state.update { it.copy(showDiscardConfirm = false) }
    }

    fun confirmDiscardSelected() {
        val state = _state.value
        val path = state.selectedPath ?: return
        val deleteUntracked = state.files.firstOrNull { it.path == path }?.status?.contains("untracked", ignoreCase = true) == true
        _state.update { it.copy(showDiscardConfirm = false) }
        mutate("Discarded") { repository.discard(sessionId, listOf(path), deleteUntracked).error }
    }

    fun fetch() {
        mutate("Fetched") { repository.fetch(sessionId).error }
    }

    fun pull() {
        mutate("Pulled") { repository.pull(sessionId).error }
    }

    fun requestPush() {
        _state.update { it.copy(showPushConfirm = true, error = null) }
    }

    fun dismissPushConfirm() {
        _state.update { it.copy(showPushConfirm = false) }
    }

    fun confirmPush() {
        _state.update { it.copy(showPushConfirm = false) }
        mutate("Pushed") { repository.push(sessionId).error }
    }

    fun requestCheckout(option: GitBranchOption) {
        if (option.isCurrent) return
        _state.update {
            it.copy(
                pendingCheckout = GitCheckoutSelection(
                    ref = option.name,
                    mode = option.mode,
                    track = option.mode == "remote",
                ),
                error = null,
            )
        }
    }

    fun requestCreateBranch() {
        val name = _state.value.newBranchName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Enter a branch name.") }
            return
        }
        val baseRef = _state.value.branch ?: "HEAD"
        _state.update {
            it.copy(
                pendingCheckout = GitCheckoutSelection(ref = baseRef, mode = "local", newBranch = name),
                error = null,
            )
        }
    }

    fun dismissCheckoutConfirm() {
        _state.update { it.copy(pendingCheckout = null, pendingDirtyCheckout = null) }
    }

    fun confirmCheckout() {
        val target = _state.value.pendingCheckout ?: return
        checkout(target, stashingChanges = false)
    }

    fun confirmDirtyCheckout() {
        val target = _state.value.pendingDirtyCheckout ?: return
        checkout(target, stashingChanges = true)
    }

    fun generateCommitMessage() {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { repository.commitMessage(sessionId) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            commitMessage = response.message ?: it.commitMessage,
                            isMutating = false,
                            error = response.error,
                            notice = if (response.error == null) "Generated commit message." else null,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not generate commit message.") } }
        }
    }

    fun commit() {
        val message = _state.value.commitMessage.trim()
        if (message.isBlank()) {
            _state.update { it.copy(error = "Enter a commit message.") }
            return
        }
        mutate("Committed") { repository.commit(sessionId, message).error }
    }

    private fun mutateSelected(success: String, action: suspend (String) -> String?) {
        val path = _state.value.selectedPath ?: return
        mutate(success) { action(path) }
    }

    private fun mutate(success: String, action: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { action() }
                .onSuccess { error ->
                    if (error == null) {
                        _state.update { it.copy(isMutating = false, notice = success, diff = null) }
                        refresh()
                    } else {
                        _state.update { it.copy(isMutating = false, error = error) }
                    }
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Git action failed.") } }
        }
    }

    private fun checkout(target: GitCheckoutSelection, stashingChanges: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isMutating = true,
                    pendingCheckout = null,
                    pendingDirtyCheckout = null,
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                if (stashingChanges) {
                    repository.stashCheckout(sessionId, target.ref, target.mode, target.newBranch, target.track)
                } else {
                    repository.checkout(sessionId, target.ref, target.mode, target.newBranch, target.track)
                }
            }.onSuccess { response ->
                val responseError = response.error ?: response.restoreError
                val message = response.message ?: "Switched to ${response.currentBranch ?: target.displayName}."
                _state.update {
                    it.copy(
                        isMutating = false,
                        newBranchName = if (target.newBranch != null && responseError == null) "" else it.newBranchName,
                        error = responseError,
                        notice = if (responseError == null) message else null,
                    )
                }
                refresh(clearNotice = responseError != null, clearError = responseError == null)
            }.onFailure { error ->
                if (!stashingChanges && error.isDirtyWorktree()) {
                    _state.update {
                        it.copy(
                            isMutating = false,
                            pendingDirtyCheckout = target,
                            error = null,
                        )
                    }
                } else {
                    _state.update { it.copy(isMutating = false, error = error.message ?: "Could not switch branches.") }
                }
            }
        }
    }
}

private fun GitBranchesResponse?.toOptions(currentFallback: String?): List<GitBranchOption> {
    val branchSet = this?.branches ?: return emptyList()
    val current = branchSet.current ?: this.current ?: currentFallback
    val local = branchSet.local.orEmpty()
        .mapNotNull { it.toOption("local", current) }
    val remote = branchSet.remote.orEmpty()
        .filterNot { it.name?.endsWith("/HEAD") == true }
        .mapNotNull { it.toOption("remote", current) }
    return local + remote
}

private fun GitBranchRef.toOption(mode: String, current: String?): GitBranchOption? {
    val branchName = name?.takeIf { it.isNotBlank() } ?: return null
    return GitBranchOption(
        name = branchName,
        mode = mode,
        subject = subject,
        upstream = upstream,
        ahead = ahead,
        behind = behind,
        isCurrent = branchName == current || branchName.substringAfterLast('/') == current,
    )
}

private fun Throwable.isDirtyWorktree(): Boolean =
    (this as? ApiError.Http)?.body?.contains("dirty_worktree", ignoreCase = true) == true ||
        message?.contains("dirty_worktree", ignoreCase = true) == true
