package com.uzairansar.hermex.ui.sessions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.AppContainer
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.ui.ShortcutDestination

@Composable
fun SessionListRoute(
    authState: AuthState,
    container: AppContainer,
    shortcutAction: String? = null,
    shortcutNonce: String? = null,
    onOpenChat: (String) -> Unit,
    onOpenSharedDraft: (String) -> Unit,
    onOpenPanels: () -> Unit,
    onOpenSettings: () -> Unit,
    onNeedsOnboarding: () -> Unit,
) {
    val loggedIn = authState as? AuthState.LoggedIn
    if (loggedIn == null) {
        onNeedsOnboarding()
        return
    }
    val viewModel: SessionListViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SessionListViewModel(container.sessionRepository(loggedIn.server)) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shortcutKey = listOfNotNull(shortcutAction, shortcutNonce).joinToString(":")
    var shortcutConsumed by rememberSaveable(shortcutKey) { mutableStateOf(false) }

    LaunchedEffect(shortcutKey, shortcutAction, shortcutConsumed) {
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.NewSessionAction) {
            shortcutConsumed = true
            viewModel.createSession(onOpenChat)
        }
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.ShareAction) {
            shortcutConsumed = true
            viewModel.createSession(onOpenSharedDraft)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Sessions", style = MaterialTheme.typography.headlineMedium)
                Text(loggedIn.account.displayName, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenPanels) { Text("Panels") }
                Button(onClick = onOpenSettings) { Text("Settings") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.refresh() }, enabled = !state.isMutating) { Text("Refresh") }
            Button(onClick = { viewModel.createSession(onOpenChat) }, enabled = !state.isMutating) { Text("New") }
            Button(onClick = viewModel::toggleArchived, enabled = !state.isMutating) {
                Text(if (state.showArchived) "Hide archived" else "Archived")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Search") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Go") }
            Button(onClick = viewModel::clearSearch, enabled = state.searchQuery.isNotBlank()) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))
        ProjectStrip(
            projects = state.projects,
            selectedProjectId = state.selectedProjectId,
            newProjectName = state.newProjectName,
            isMutating = state.isMutating,
            onSelectProject = viewModel::selectProject,
            onNewProjectName = viewModel::updateNewProjectName,
            onCreateProject = viewModel::createProject,
            onRenameProject = viewModel::requestRenameProject,
            onDeleteProject = viewModel::requestDeleteProject,
        )
        if (state.isViewingCachedData) {
            Spacer(Modifier.height(8.dp))
            Text("Offline cache", color = MaterialTheme.colorScheme.tertiary)
        }
        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.tertiary)
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.archivedCount?.takeIf { it > 0 && !state.showArchived }?.let {
            Spacer(Modifier.height(8.dp))
            Text("$it archived session(s)", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.visibleSessions.isEmpty()) {
            Text("No sessions match this view.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.visibleSessions, key = { it.stableId }) { session ->
                    SessionRow(
                        session = session,
                        projects = state.projects,
                        isMutating = state.isMutating,
                        onOpen = { session.sessionId?.let(onOpenChat) },
                        onPin = { viewModel.togglePin(session) },
                        onArchive = { viewModel.toggleArchive(session) },
                        onRename = { viewModel.requestRename(session) },
                        onDelete = { viewModel.requestDelete(session) },
                        onBranch = { viewModel.requestBranch(session) },
                        onMove = { projectId -> viewModel.move(session, projectId) },
                    )
                }
            }
        }
    }

    SessionDialogs(state, viewModel, onOpenChat)
}

@Composable
private fun ProjectStrip(
    projects: List<ProjectSummary>,
    selectedProjectId: String?,
    newProjectName: String,
    isMutating: Boolean,
    onSelectProject: (String?) -> Unit,
    onNewProjectName: (String) -> Unit,
    onCreateProject: () -> Unit,
    onRenameProject: (ProjectSummary) -> Unit,
    onDeleteProject: (ProjectSummary) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onSelectProject(null) },
                    label = { Text(if (selectedProjectId == null) "All selected" else "All") },
                )
                projects.forEach { project ->
                    AssistChip(
                        onClick = { project.projectId?.let(onSelectProject) },
                        enabled = project.projectId != null,
                        label = { Text(if (project.projectId == selectedProjectId) "Current: ${project.displayName}" else project.displayName) },
                    )
                    AssistChip(onClick = { onRenameProject(project) }, enabled = !isMutating && project.projectId != null, label = { Text("Rename") })
                    AssistChip(onClick = { onDeleteProject(project) }, enabled = !isMutating && project.projectId != null, label = { Text("Delete") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = onNewProjectName,
                    label = { Text("New project") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isMutating,
                )
                Button(onClick = onCreateProject, enabled = !isMutating && newProjectName.isNotBlank()) { Text("Add") }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    projects: List<ProjectSummary>,
    isMutating: Boolean,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBranch: () -> Unit,
    onMove: (String?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (session.archived == true) Text("Archived", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = listOfNotNull(session.model, session.workspace).joinToString(" - ").ifBlank { "No metadata" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = onPin, enabled = !isMutating, label = { Text(if (session.pinned == true) "Unpin" else "Pin") })
                AssistChip(onClick = onArchive, enabled = !isMutating, label = { Text(if (session.archived == true) "Restore" else "Archive") })
                AssistChip(onClick = onRename, enabled = !isMutating, label = { Text("Rename") })
                AssistChip(onClick = onBranch, enabled = !isMutating, label = { Text("Branch") })
                AssistChip(onClick = onDelete, enabled = !isMutating, label = { Text("Delete") })
                session.messageCount?.let { AssistChip(onClick = {}, label = { Text("$it msgs") }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = { onMove(null) }, enabled = !isMutating && session.projectId != null, label = { Text("No project") })
                projects.forEach { project ->
                    val projectId = project.projectId
                    AssistChip(
                        onClick = { if (projectId != null) onMove(projectId) },
                        enabled = !isMutating && projectId != null && session.projectId != projectId,
                        label = { Text(if (session.projectId == projectId) "In ${project.displayName}" else "Move: ${project.displayName}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDialogs(
    state: SessionListUiState,
    viewModel: SessionListViewModel,
    onOpenChat: (String) -> Unit,
) {
    state.renameSession?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = viewModel::updateRenameDraft,
                    label = { Text("Title") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRename, enabled = !state.isMutating && state.renameDraft.isNotBlank()) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRename) { Text("Cancel") }
            },
        )
    }

    state.deleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete Session?") },
            text = { Text("Delete ${session.title ?: "this session"}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, enabled = !state.isMutating) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") }
            },
        )
    }

    state.branchSession?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissBranch,
            title = { Text("Branch Session") },
            text = {
                OutlinedTextField(
                    value = state.branchTitleDraft,
                    onValueChange = viewModel::updateBranchTitleDraft,
                    label = { Text("Optional title") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmBranch(onOpenChat) }, enabled = !state.isMutating) { Text("Branch") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBranch) { Text("Cancel") }
            },
        )
    }

    state.renameProject?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameProject,
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = state.renameProjectDraft,
                    onValueChange = viewModel::updateRenameProjectDraft,
                    label = { Text("Project name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRenameProject, enabled = !state.isMutating && state.renameProjectDraft.isNotBlank()) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameProject) { Text("Cancel") }
            },
        )
    }

    state.deleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteProject,
            title = { Text("Delete Project?") },
            text = { Text("Sessions in ${project.displayName} will move to No project. The sessions themselves will not be deleted.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteProject, enabled = !state.isMutating) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteProject) { Text("Cancel") }
            },
        )
    }
}

private val ProjectSummary.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: "Untitled Project"
