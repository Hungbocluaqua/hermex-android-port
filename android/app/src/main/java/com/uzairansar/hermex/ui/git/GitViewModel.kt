package com.uzairansar.hermex.ui.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.GitBranchRef
import com.uzairansar.hermex.core.model.GitBranchesResponse
import com.uzairansar.hermex.core.model.GitCommitResponse
import com.uzairansar.hermex.core.model.GitDiffResponse
import com.uzairansar.hermex.core.model.GitFileChange
import com.uzairansar.hermex.core.model.GitMutationResponse
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.GitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
    val changedCount: Int = 0,
    val totalAdditions: Int = 0,
    val totalDeletions: Int = 0,
    val truncated: Boolean = false,
    val selectedPath: String? = null,
    val selectedKind: String = "unstaged",
    val selectedPaths: Set<String> = emptySet(),
    val diff: GitDiffResponse? = null,
    val commitMessage: String = "",
    val messageWasTruncated: Boolean = false,
    val pushAfterCommit: Boolean = false,
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val pendingCheckout: GitCheckoutSelection? = null,
    val pendingDirtyCheckout: GitCheckoutSelection? = null,
    val showPushConfirm: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val pendingDiscardPaths: List<String> = emptyList(),
    val pendingDiscardDeletesFiles: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class GitViewModel(
    private val sessionId: String,
    private val repository: GitRepository,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        GitUiState(
            newBranchName = savedStateHandle?.get<String>(SAVED_NEW_BRANCH_NAME).orEmpty(),
            commitMessage = savedStateHandle?.get<String>(SAVED_COMMIT_MESSAGE).orEmpty(),
            pushAfterCommit = savedStateHandle?.get<Boolean>(SAVED_PUSH_AFTER_COMMIT) ?: false,
        ),
    )
    val state: StateFlow<GitUiState> = _state
    private var diffJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init {
        refresh()
    }

    fun refresh(clearNotice: Boolean = true, clearError: Boolean = true) {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
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
                    if (generation != refreshGeneration) return@onSuccess
                    val current = branches?.branches?.current ?: branches?.current ?: status.branch
                    val files = status.files.orEmpty()
                    val validPaths = files.mapNotNull { it.serverPath() }.toSet()
                    _state.update { previous ->
                        val selectedPath = previous.selectedPath?.takeIf { it in validPaths }
                        previous.copy(
                            branch = current,
                            branches = branches.toOptions(current),
                            files = files,
                            changedCount = status.changedCount ?: files.size,
                            totalAdditions = status.totalAdditions ?: files.sumOf { file -> file.additions ?: 0 },
                            totalDeletions = status.totalDeletions ?: files.sumOf { file -> file.deletions ?: 0 },
                            truncated = status.truncated == true,
                            selectedPaths = previous.selectedPaths.intersect(validPaths),
                            selectedPath = selectedPath,
                            diff = if (selectedPath == null) null else previous.diff,
                            isLoading = false,
                            error = status.error ?: if (clearError) null else previous.error,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException || generation != refreshGeneration) return@onFailure
                    _state.update { it.copy(isLoading = false, error = error.friendlyGitMessage("Could not load git status.")) }
                }
        }
    }

    fun updateNewBranchName(value: String) {
        savedStateHandle?.set(SAVED_NEW_BRANCH_NAME, value)
        _state.update { it.copy(newBranchName = value, error = null) }
    }

    fun selectFile(file: GitFileChange, kind: String = if (file.staged == true) "staged" else "unstaged") {
        val path = file.serverPath() ?: return
        diffJob?.cancel()
        diffJob = viewModelScope.launch {
            _state.update { it.copy(selectedPath = path, selectedKind = kind, diff = null, error = null) }
            runCatching { repository.diff(sessionId, path, kind) }
                .onSuccess { diff ->
                    _state.update {
                        if (it.selectedPath == path && it.selectedKind == kind) {
                            it.copy(diff = diff, error = diff.error)
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update {
                        if (it.selectedPath == path && it.selectedKind == kind) {
                            it.copy(error = error.friendlyGitMessage("Could not load diff."))
                        } else {
                            it
                        }
                    }
                }
        }
    }

    fun togglePathSelection(file: GitFileChange) {
        val path = file.serverPath() ?: return
        _state.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            state.copy(selectedPaths = selected, error = null, notice = null)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPaths = emptySet(), error = null) }
    }

    fun updateCommitMessage(value: String) {
        savedStateHandle?.set(SAVED_COMMIT_MESSAGE, value)
        _state.update { it.copy(commitMessage = value, messageWasTruncated = false) }
    }

    fun updatePushAfterCommit(value: Boolean) {
        savedStateHandle?.set(SAVED_PUSH_AFTER_COMMIT, value)
        _state.update { it.copy(pushAfterCommit = value) }
    }

    fun stageSelected() {
        mutateSelected("Staged") { path -> repository.stage(sessionId, listOf(path)) }
    }

    fun unstageSelected() {
        mutateSelected("Unstaged") { path -> repository.unstage(sessionId, listOf(path)) }
    }

    fun stageSelectedOrAll() {
        mutateTargetPaths("Staged") { paths -> repository.stage(sessionId, paths) }
    }

    fun unstageSelectedOrAll() {
        mutateTargetPaths("Unstaged") { paths -> repository.unstage(sessionId, paths) }
    }

    fun requestDiscardSelected() {
        val state = _state.value
        val path = state.selectedPath ?: return
        requestDiscard(listOf(path))
    }

    fun requestDiscardSelectedOrAll() {
        requestDiscard(targetPaths())
    }

    private fun requestDiscard(paths: List<String>) {
        if (paths.isEmpty()) return
        val targets = _state.value.files.filter { it.serverPath() in paths }
        _state.update {
            it.copy(
                showDiscardConfirm = true,
                pendingDiscardPaths = paths,
                pendingDiscardDeletesFiles = targets.any { file -> file.discardMayDeleteFile() },
                error = null,
            )
        }
    }

    fun dismissDiscardConfirm() {
        _state.update { it.copy(showDiscardConfirm = false, pendingDiscardPaths = emptyList(), pendingDiscardDeletesFiles = false) }
    }

    fun confirmDiscardSelected() {
        val state = _state.value
        val paths = state.pendingDiscardPaths
        if (paths.isEmpty()) return
        val stagedPaths = state.files
            .filter { it.serverPath() in paths && it.staged == true }
            .mapNotNull { it.serverPath() }
        val deleteUntracked = state.pendingDiscardDeletesFiles
        _state.update { it.copy(showDiscardConfirm = false, pendingDiscardPaths = emptyList(), pendingDiscardDeletesFiles = false) }
        mutate("Discarded") {
            if (stagedPaths.isNotEmpty()) {
                val unstage = repository.unstage(sessionId, stagedPaths)
                if (unstage.error != null) return@mutate unstage
            }
            repository.discard(sessionId, paths, deleteUntracked)
        }
    }

    fun fetch() {
        mutate("Fetched") { repository.fetch(sessionId) }
    }

    fun pull() {
        mutate("Pulled") { repository.pull(sessionId) }
    }

    fun requestPush() {
        _state.update { it.copy(showPushConfirm = true, error = null) }
    }

    fun dismissPushConfirm() {
        _state.update { it.copy(showPushConfirm = false) }
    }

    fun confirmPush() {
        _state.update { it.copy(showPushConfirm = false) }
        mutate("Pushed") { repository.push(sessionId) }
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
        if (!beginMutation()) return
        viewModelScope.launch {
            val paths = _state.value.selectedPaths.toList()
            runCatching {
                if (paths.isEmpty()) {
                    repository.commitMessage(sessionId)
                } else {
                    repository.commitMessageSelected(sessionId, paths)
                }
            }
                .onSuccess { response ->
                    val suggested = response.message?.trim().orEmpty()
                    _state.update {
                        if (suggested.isEmpty() && response.error == null) {
                            it.copy(
                                isMutating = false,
                                error = "No commit message could be generated.",
                                notice = null,
                            )
                        } else {
                            it.copy(
                                commitMessage = suggested.ifBlank { it.commitMessage },
                                messageWasTruncated = response.truncated == true,
                                isMutating = false,
                                error = response.error,
                                notice = if (response.error == null) "Generated commit message." else null,
                            )
                        }
                    }
                    if (suggested.isNotBlank()) savedStateHandle?.set(SAVED_COMMIT_MESSAGE, suggested)
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.friendlyGitMessage("Could not generate commit message.")) } }
        }
    }

    fun commit() {
        val state = _state.value
        if (state.files.none { it.staged == true }) {
            _state.update { it.copy(error = "Stage at least one file before committing all changes.") }
            return
        }
        runCommit(paths = null)
    }

    fun commitSelected() {
        val paths = _state.value.selectedPaths.toList()
        if (paths.isEmpty()) {
            _state.update { it.copy(error = "Select at least one file to commit selected changes.") }
            return
        }
        runCommit(paths = paths)
    }

    private fun runCommit(paths: List<String>?) {
        val message = _state.value.commitMessage.trim()
        if (message.isBlank()) {
            _state.update { it.copy(error = "Enter a commit message.") }
            return
        }
        if (!beginMutation()) return
        viewModelScope.launch {
            val shouldPush = _state.value.pushAfterCommit
            runCatching {
                if (paths == null) {
                    repository.commit(sessionId, message)
                } else {
                    repository.commitSelected(sessionId, message, paths)
                }
            }.onSuccess { response ->
                if (response.error != null) {
                    _state.update { it.copy(isMutating = false, error = response.error) }
                    return@onSuccess
                }

                val pushError = if (shouldPush) pushAfterCommitOrError() else null
                val notice = buildCommitNotice(response, shouldPush && pushError == null)
                _state.update {
                    it.copy(
                        isMutating = false,
                        commitMessage = "",
                        messageWasTruncated = false,
                        selectedPaths = emptySet(),
                        diff = null,
                        error = pushError,
                        notice = notice,
                    )
                }
                savedStateHandle?.set(SAVED_COMMIT_MESSAGE, "")
                refresh(clearNotice = false, clearError = false)
            }.onFailure { error ->
                _state.update { it.copy(isMutating = false, error = error.friendlyGitMessage("Could not commit changes.")) }
            }
        }
    }

    private suspend fun pushAfterCommitOrError(): String? =
        runCatching { repository.push(sessionId) }
            .fold(
                onSuccess = { response -> response.error?.let { "Committed, but the push failed. $it" } },
                onFailure = { error -> "Committed, but the push failed. ${error.friendlyGitMessage("Push failed.")}" },
            )

    private fun buildCommitNotice(response: GitCommitResponse, pushed: Boolean): String {
        val sha = response.shortSha ?: response.sha?.take(7)
        val suffix = when {
            pushed && sha != null -> " and pushed $sha"
            pushed -> " and pushed"
            sha != null -> " $sha"
            else -> ""
        }
        return "Committed$suffix."
    }

    private fun mutateSelected(success: String, action: suspend (String) -> GitMutationResponse) {
        val path = _state.value.selectedPath ?: return
        mutate(success) { action(path) }
    }

    private fun mutateTargetPaths(success: String, action: suspend (List<String>) -> GitMutationResponse) {
        val paths = targetPaths()
        if (paths.isEmpty()) {
            _state.update { it.copy(error = "No changed files to update.") }
            return
        }
        mutate(success) { action(paths) }
    }

    private fun targetPaths(): List<String> {
        val state = _state.value
        return state.files
            .filter { file ->
                val path = file.serverPath()
                path != null && (state.selectedPaths.isEmpty() || path in state.selectedPaths)
            }
            .mapNotNull { it.serverPath() }
    }

    private fun mutate(success: String, action: suspend () -> GitMutationResponse) {
        if (!beginMutation()) return
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { response ->
                    if (response.error == null) {
                        _state.update { it.copy(isMutating = false, notice = response.message ?: success, diff = null) }
                        refresh(clearNotice = false)
                    } else {
                        _state.update { it.copy(isMutating = false, error = response.error) }
                    }
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.friendlyGitMessage("Git action failed.")) } }
        }
    }

    private fun checkout(target: GitCheckoutSelection, stashingChanges: Boolean) {
        if (!beginMutation()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
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
                if (target.newBranch != null && responseError == null) {
                    savedStateHandle?.set(SAVED_NEW_BRANCH_NAME, "")
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
                    _state.update { it.copy(isMutating = false, error = error.friendlyGitMessage("Could not switch branches.")) }
                }
            }
        }
    }

    private fun beginMutation(): Boolean {
        if (_state.value.isMutating) return false
        _state.update { it.copy(isMutating = true, error = null, notice = null) }
        return true
    }

    private companion object {
        const val SAVED_COMMIT_MESSAGE = "git_commit_message"
        const val SAVED_NEW_BRANCH_NAME = "git_new_branch_name"
        const val SAVED_PUSH_AFTER_COMMIT = "git_push_after_commit"
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

private fun Throwable.friendlyGitMessage(fallback: String): String {
    val body = (this as? ApiError.Http)?.body.orEmpty()
    if (body.contains("destructive_git_disabled", ignoreCase = true)) {
        return "Writes disabled on server. Enable HERMES_WEBUI_WORKSPACE_GIT_DESTRUCTIVE=1 on the server to use this."
    }
    if (body.contains("active_stream", ignoreCase = true)) {
        return "Wait for the active response to finish before changing this repository."
    }
    return message ?: fallback
}

private fun GitFileChange.serverPath(): String? =
    (path ?: workspacePath)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun GitFileChange.discardMayDeleteFile(): Boolean {
    val statusText = status.orEmpty()
    return untracked == true ||
        statusText.contains("untracked", ignoreCase = true) ||
        statusText.equals("A", ignoreCase = true) ||
        statusText.contains("added", ignoreCase = true) ||
        statusText.contains("renamed", ignoreCase = true)
}
