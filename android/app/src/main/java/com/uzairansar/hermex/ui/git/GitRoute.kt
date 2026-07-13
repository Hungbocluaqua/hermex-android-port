package com.uzairansar.hermex.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.GitDiffResponse
import com.uzairansar.hermex.core.model.GitFileChange
import com.uzairansar.hermex.data.repository.GitRepository
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexHairline

@Composable
fun GitRoute(
    sessionId: String,
    viewModelKey: String = "git:$sessionId",
    repository: GitRepository,
    onBack: () -> Unit,
) {
    val viewModel: GitViewModel = viewModel(key = viewModelKey, factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GitViewModel(sessionId, repository) as T
        }

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return GitViewModel(sessionId, repository, extras.createSavedStateHandle()) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            GitHeader(
                branch = state.branch,
                onBack = onBack,
                onRefresh = viewModel::refresh,
            )
            StatusLines(notice = state.notice, error = state.error)
            RemoteActionsBar(
                enabled = !state.isMutating,
                onFetch = viewModel::fetch,
                onPull = viewModel::pull,
                onPush = viewModel::requestPush,
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                return@Column
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                GitSummaryCard(
                    branch = state.branch,
                    changedCount = state.changedCount,
                    additions = state.totalAdditions,
                    deletions = state.totalDeletions,
                    truncated = state.truncated,
                    isClean = state.files.isEmpty(),
                )
                if (state.files.isNotEmpty()) {
                    BatchActionsBar(
                        selectedCount = state.selectedPaths.size,
                        totalCount = state.files.size,
                        enabled = !state.isMutating,
                        onStage = viewModel::stageSelectedOrAll,
                        onUnstage = viewModel::unstageSelectedOrAll,
                        onDiscard = viewModel::requestDiscardSelectedOrAll,
                        onClear = viewModel::clearSelection,
                    )
                    state.files.take(MAX_RENDERED_GIT_FILES).forEach { file ->
                        val path = file.gitPath()
                        GitFileRow(
                            file = file,
                            selected = path == state.selectedPath,
                            checked = path != null && path in state.selectedPaths,
                            onClick = { viewModel.selectFile(file) },
                            onCheckedChange = { viewModel.togglePathSelection(file) },
                        )
                    }
                    if (state.files.size > MAX_RENDERED_GIT_FILES) {
                        Text(
                            "Showing the first $MAX_RENDERED_GIT_FILES changed files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                BranchesSection(
                    branches = state.branches,
                    newBranchName = state.newBranchName,
                    onNewBranchNameChange = viewModel::updateNewBranchName,
                    onSelectBranch = viewModel::requestCheckout,
                    onCreateBranch = viewModel::requestCreateBranch,
                    enabled = !state.isMutating,
                )
                state.diff?.let { diff ->
                    DiffSection(
                        path = state.selectedPath,
                        diff = diff,
                        canAct = !state.isMutating && state.selectedPath != null,
                        onStage = viewModel::stageSelected,
                        onUnstage = viewModel::unstageSelected,
                        onDiscard = viewModel::requestDiscardSelected,
                    )
                }
                CommitSection(
                    message = state.commitMessage,
                    selectedCount = state.selectedPaths.size,
                    hasStagedChanges = state.files.any { it.staged == true },
                    messageWasTruncated = state.messageWasTruncated,
                    pushAfterCommit = state.pushAfterCommit,
                    onMessageChange = viewModel::updateCommitMessage,
                    onGenerate = viewModel::generateCommitMessage,
                    onCommit = viewModel::commit,
                    onCommitSelected = viewModel::commitSelected,
                    onPushAfterCommitChange = viewModel::updatePushAfterCommit,
                    enabled = !state.isMutating,
                )
            }
        }
    }

    GitDialogs(state, viewModel)
}

@Composable
private fun GitHeader(
    branch: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermexIconButton("Back", "‹", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Git", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(branch ?: "Repository workspace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
        }
        HermexIconButton("Refresh", "↻", onRefresh)
    }
}

@Composable
private fun RemoteActionsBar(
    enabled: Boolean,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HermexPillButton("Fetch", onFetch, enabled = enabled, modifier = Modifier.weight(1f))
        HermexPillButton("Pull", onPull, enabled = enabled, modifier = Modifier.weight(1f))
        HermexPillButton("Push", onPush, enabled = enabled, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusLines(notice: String?, error: String?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CleanTreeCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF34C759).copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text("✓", color = Color(0xFF34C759), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column {
            Text("Working tree clean", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("No changed files in this session workspace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun GitSummaryCard(
    branch: String?,
    changedCount: Int,
    additions: Int,
    deletions: Int,
    truncated: Boolean,
    isClean: Boolean,
) {
    SectionSurface("Working tree") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (changedCount == 1) "1 change" else "$changedCount changes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(branch ?: "HEAD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text("+$additions", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text("-$deletions", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        if (isClean) {
            Spacer(Modifier.height(12.dp))
            CleanTreeCard()
        }
        if (truncated) {
            Spacer(Modifier.height(8.dp))
            Text("Showing first 500 changed files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun BatchActionsBar(
    selectedCount: Int,
    totalCount: Int,
    enabled: Boolean,
    onStage: () -> Unit,
    onUnstage: () -> Unit,
    onDiscard: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .hermexHairline(HermexCardShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedCount > 0) "$selectedCount selected" else "All $totalCount changes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        HermexPillButton("Stage", onStage, enabled = enabled && totalCount > 0)
        HermexPillButton("Unstage", onUnstage, enabled = enabled && totalCount > 0)
        HermexPillButton("Discard", onDiscard, enabled = enabled && totalCount > 0)
        if (selectedCount > 0) {
            HermexPillButton("Clear", onClear, enabled = enabled)
        }
    }
}

@Composable
private fun BranchesSection(
    branches: List<GitBranchOption>,
    newBranchName: String,
    onNewBranchNameChange: (String) -> Unit,
    onSelectBranch: (GitBranchOption) -> Unit,
    onCreateBranch: () -> Unit,
    enabled: Boolean,
) {
    SectionSurface("Branches") {
        if (branches.isEmpty()) {
            Text("Branch list unavailable.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                branches.take(MAX_RENDERED_BRANCHES).forEach { branch ->
                    HermexPillButton(
                        label = buildString {
                            if (branch.isCurrent) append("Current: ")
                            append(branch.displayName)
                            if (branch.mode == "remote") append(" remote")
                        },
                        onClick = { onSelectBranch(branch) },
                        enabled = enabled && !branch.isCurrent,
                        filled = branch.isCurrent,
                    )
                }
            }
            if (branches.size > MAX_RENDERED_BRANCHES) {
                Text(
                    "Showing the first $MAX_RENDERED_BRANCHES branches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GitTextField(
            value = newBranchName,
            onValueChange = onNewBranchNameChange,
            placeholder = "New branch name",
            singleLine = true,
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        HermexPillButton("Create and switch", onCreateBranch, enabled = enabled && newBranchName.isNotBlank(), filled = true)
    }
}

@Composable
private fun DiffSection(
    path: String?,
    diff: GitDiffResponse,
    canAct: Boolean,
    onStage: () -> Unit,
    onUnstage: () -> Unit,
    onDiscard: () -> Unit,
) {
    SectionSurface(path ?: "Diff") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexPillButton("Stage", onStage, enabled = canAct)
            HermexPillButton("Unstage", onUnstage, enabled = canAct)
            HermexPillButton("Discard", onDiscard, enabled = canAct)
        }
        Spacer(Modifier.height(8.dp))
        HermexGitDiffContent(diff)
    }
}

@Composable
private fun CommitSection(
    message: String,
    selectedCount: Int,
    hasStagedChanges: Boolean,
    messageWasTruncated: Boolean,
    pushAfterCommit: Boolean,
    onMessageChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCommit: () -> Unit,
    onCommitSelected: () -> Unit,
    onPushAfterCommitChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    SectionSurface("Commit") {
        GitTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = "Commit message",
            minLines = 3,
            enabled = enabled,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Push after commit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Send the new commit to the upstream remote.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(
                checked = pushAfterCommit,
                onCheckedChange = onPushAfterCommitChange,
                enabled = enabled,
            )
        }
        if (messageWasTruncated) {
            Text("Diff was large; message may be partial.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexPillButton("Generate", onGenerate, enabled = enabled)
            if (selectedCount > 0) {
                HermexPillButton("Commit selected", onCommitSelected, enabled = enabled && message.isNotBlank(), filled = false)
            }
            HermexPillButton(
                if (pushAfterCommit) "Commit & Push" else "Commit",
                onCommit,
                enabled = enabled && message.isNotBlank() && hasStagedChanges,
                filled = true,
            )
        }
    }
}

@Composable
private fun SectionSurface(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hermexGlass(
                    shape = RoundedCornerShape(20.dp),
                    castsShadow = false,
                    surfaceLevel = HermexSurfaceLevel.Base,
                    tintEnabled = false,
                )
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun GitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.secondary) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container.copy(alpha = 0.42f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun GitFileRow(
    file: GitFileChange,
    selected: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hermexHairline(HermexCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 8.dp),
        ) {
            Text(
                text = file.gitPath() ?: "Unknown file",
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallTag(file.status ?: "changed")
                SmallTag(if (file.staged == true) "staged" else "unstaged")
                if (file.additions != null || file.deletions != null) {
                    SmallTag("+${file.additions ?: 0} -${file.deletions ?: 0}")
                }
            }
        }
    }
}

@Composable
private fun SmallTag(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .hermexGlass(shape = androidx.compose.foundation.shape.CircleShape, castsShadow = false)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun GitDialogs(state: GitUiState, viewModel: GitViewModel) {
    state.pendingCheckout?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckoutConfirm,
            title = { Text("Switch branch?") },
            text = { Text("Switch to ${target.displayName}? Uncommitted changes will be checked before the branch changes.") },
            confirmButton = { TextButton(onClick = viewModel::confirmCheckout, enabled = !state.isMutating) { Text("Switch") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCheckoutConfirm) { Text("Cancel") } },
        )
    }
    state.pendingDirtyCheckout?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckoutConfirm,
            title = { Text("Save changes and switch?") },
            text = { Text("This workspace has uncommitted changes. Save them temporarily, switch to ${target.displayName}, then restore them on the destination branch.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDirtyCheckout, enabled = !state.isMutating) { Text("Save and switch") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCheckoutConfirm) { Text("Cancel") } },
        )
    }
    if (state.showPushConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPushConfirm,
            title = { Text("Push branch?") },
            text = { Text("Push the current branch to its configured upstream remote?") },
            confirmButton = { TextButton(onClick = viewModel::confirmPush, enabled = !state.isMutating) { Text("Push") } },
            dismissButton = { TextButton(onClick = viewModel::dismissPushConfirm) { Text("Cancel") } },
        )
    }
    if (state.showDiscardConfirm) {
        val count = state.pendingDiscardPaths.size
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardConfirm,
            title = { Text("Discard changes?") },
            text = {
                Text(
                    if (state.pendingDiscardDeletesFiles) {
                        "Discard $count target(s), including new or untracked files? This cannot be undone."
                    } else {
                        "Discard local changes for $count target(s)? This cannot be undone."
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDiscardSelected, enabled = !state.isMutating) { Text("Discard") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDiscardConfirm) { Text("Cancel") } },
        )
    }
}

private fun GitFileChange.gitPath(): String? =
    (path ?: workspacePath)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private const val MAX_RENDERED_GIT_FILES = 250
private const val MAX_RENDERED_BRANCHES = 200
