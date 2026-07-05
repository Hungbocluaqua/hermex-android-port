package com.uzairansar.hermex.ui.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.GitFileChange
import com.uzairansar.hermex.data.repository.GitRepository

@Composable
fun GitRoute(
    sessionId: String,
    repository: GitRepository,
    onBack: () -> Unit,
) {
    val viewModel: GitViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GitViewModel(sessionId, repository) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = viewModel::refresh) { Text("Refresh") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Git", style = MaterialTheme.typography.headlineMedium)
        Text(state.branch ?: "Branch unavailable", style = MaterialTheme.typography.bodySmall)
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::fetch, enabled = !state.isMutating) { Text("Fetch") }
            Button(onClick = viewModel::pull, enabled = !state.isMutating) { Text("Pull") }
            Button(onClick = viewModel::requestPush, enabled = !state.isMutating) { Text("Push") }
        }
        Spacer(Modifier.height(12.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
            return@Column
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            if (state.files.isEmpty()) {
                Text("Working tree is clean.")
            } else {
                state.files.forEach { file ->
                    GitFileRow(file = file, selected = file.path == state.selectedPath, onClick = { viewModel.selectFile(file) })
                }
            }
            BranchesCard(
                branches = state.branches,
                newBranchName = state.newBranchName,
                onNewBranchNameChange = viewModel::updateNewBranchName,
                onSelectBranch = viewModel::requestCheckout,
                onCreateBranch = viewModel::requestCreateBranch,
                enabled = !state.isMutating,
            )
            state.diff?.let { diff ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(state.selectedPath ?: "Diff", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::stageSelected, enabled = !state.isMutating && state.selectedPath != null) { Text("Stage") }
                            Button(onClick = viewModel::unstageSelected, enabled = !state.isMutating && state.selectedPath != null) { Text("Unstage") }
                            Button(onClick = viewModel::requestDiscardSelected, enabled = !state.isMutating && state.selectedPath != null) { Text("Discard") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(diff, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Commit", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.commitMessage,
                        onValueChange = viewModel::updateCommitMessage,
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::generateCommitMessage, enabled = !state.isMutating) { Text("Generate") }
                        Button(onClick = viewModel::commit, enabled = !state.isMutating && state.commitMessage.isNotBlank()) { Text("Commit") }
                    }
                }
            }
        }
    }

    state.pendingCheckout?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckoutConfirm,
            title = { Text("Switch branch?") },
            text = { Text("Switch to ${target.displayName}? Uncommitted changes will be checked before the branch changes.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCheckout, enabled = !state.isMutating) { Text("Switch") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCheckoutConfirm) { Text("Cancel") }
            },
        )
    }

    state.pendingDirtyCheckout?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckoutConfirm,
            title = { Text("Save changes and switch?") },
            text = { Text("This workspace has uncommitted changes. Save them temporarily, switch to ${target.displayName}, then restore them on the destination branch.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDirtyCheckout, enabled = !state.isMutating) { Text("Save and switch") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCheckoutConfirm) { Text("Cancel") }
            },
        )
    }

    if (state.showPushConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPushConfirm,
            title = { Text("Push branch?") },
            text = { Text("Push the current branch to its configured upstream remote?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPush, enabled = !state.isMutating) { Text("Push") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPushConfirm) { Text("Cancel") }
            },
        )
    }

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardConfirm,
            title = { Text("Discard changes?") },
            text = { Text("Discard local changes for ${state.selectedPath ?: "the selected file"}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDiscardSelected, enabled = !state.isMutating) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscardConfirm) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BranchesCard(
    branches: List<GitBranchOption>,
    newBranchName: String,
    onNewBranchNameChange: (String) -> Unit,
    onSelectBranch: (GitBranchOption) -> Unit,
    onCreateBranch: () -> Unit,
    enabled: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Branches", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (branches.isEmpty()) {
                Text("Branch list unavailable.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    branches.forEach { branch ->
                        AssistChip(
                            onClick = { onSelectBranch(branch) },
                            enabled = enabled && !branch.isCurrent,
                            label = {
                                Text(
                                    text = buildString {
                                        if (branch.isCurrent) append("Current: ")
                                        append(branch.displayName)
                                        if (branch.mode == "remote") append(" remote")
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newBranchName,
                onValueChange = onNewBranchNameChange,
                label = { Text("New branch") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreateBranch, enabled = enabled && newBranchName.isNotBlank()) {
                Text("Create and switch")
            }
        }
    }
}

@Composable
private fun GitFileRow(file: GitFileChange, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = file.path ?: "Unknown file",
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClick, label = { Text(file.status ?: "changed") })
                AssistChip(onClick = onClick, label = { Text(if (file.staged == true) "staged" else "unstaged") })
            }
        }
    }
}
