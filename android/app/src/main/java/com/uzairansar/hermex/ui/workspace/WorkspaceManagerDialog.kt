package com.uzairansar.hermex.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uzairansar.hermex.core.model.WorkspaceRoot
import com.uzairansar.hermex.data.repository.WorkspaceRepository
import com.uzairansar.hermex.ui.localization.localizedString

@Composable
fun WorkspaceManagerDialog(
    viewModelKey: String,
    repository: WorkspaceRepository,
    onDismiss: () -> Unit,
    onRegistryChanged: () -> Unit,
) {
    val factory: ViewModelProvider.Factory = remember(repository) {
        viewModelFactory {
            initializer { WorkspaceManagerViewModel(repository) }
        }
    }
    val viewModel: WorkspaceManagerViewModel = viewModel(key = viewModelKey, factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showsAdd by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WorkspaceRoot?>(null) }
    var removeTarget by remember { mutableStateOf<WorkspaceRoot?>(null) }

    LaunchedEffect(state.mutationVersion) {
        if (state.mutationVersion > 0) onRegistryChanged()
    }

    Dialog(onDismissRequest = { if (!state.isMutating) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .testTag("workspace_manager"),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.isMutating) {
                        Text(localizedString("Done"))
                    }
                    Text(
                        localizedString("Manage Workspaces"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { showsAdd = true }, enabled = !state.isMutating) {
                        Text(localizedString("Add Workspace"))
                    }
                }
                HorizontalDivider()

                state.error?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearError) { Text(localizedString("Dismiss")) }
                    }
                }

                when {
                    state.isLoading && state.workspaces.isEmpty() -> {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.weight(1f))
                    }
                    state.workspaces.isEmpty() -> {
                        Spacer(Modifier.weight(1f))
                        Text(
                            localizedString("Add a workspace to make it available when starting sessions."),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    else -> LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(
                            items = state.workspaces,
                            key = { index, workspace -> workspace.path ?: "workspace-$index" },
                        ) { index, workspace ->
                            WorkspaceManagerRow(
                                workspace = workspace,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.workspaces.lastIndex,
                                enabled = !state.isMutating,
                                onMoveUp = { viewModel.move(index, index - 1) },
                                onMoveDown = { viewModel.move(index, index + 1) },
                                onRename = { renameTarget = workspace },
                                onRemove = { removeTarget = workspace },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }

                Text(
                    localizedString("Removing a workspace only unregisters its path from the server's list. No files are deleted."),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }

    if (showsAdd) {
        AddWorkspaceDialog(
            enabled = !state.isMutating,
            onDismiss = { showsAdd = false },
            onAdd = { path, name, create ->
                showsAdd = false
                viewModel.add(path, name, create)
            },
        )
    }

    renameTarget?.let { workspace ->
        RenameWorkspaceDialog(
            workspace = workspace,
            enabled = !state.isMutating,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                renameTarget = null
                viewModel.rename(workspace, name)
            },
        )
    }

    removeTarget?.let { workspace ->
        AlertDialog(
            onDismissRequest = { if (!state.isMutating) removeTarget = null },
            title = { Text(localizedString("Remove Workspace?")) },
            text = { Text(localizedString("Removing a workspace only unregisters its path from the server's list. No files are deleted.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeTarget = null
                        viewModel.remove(workspace)
                    },
                    enabled = !state.isMutating,
                ) { Text(localizedString("Remove")) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }, enabled = !state.isMutating) {
                    Text(localizedString("Cancel"))
                }
            },
        )
    }
}

@Composable
private fun WorkspaceManagerRow(
    workspace: WorkspaceRoot,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(workspace.name?.takeIf { it.isNotBlank() } ?: workspace.path.orEmpty(), style = MaterialTheme.typography.titleSmall)
        workspace.path?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = onMoveUp, enabled = enabled && canMoveUp) { Text(localizedString("Move Up")) }
            TextButton(onClick = onMoveDown, enabled = enabled && canMoveDown) { Text(localizedString("Move Down")) }
            TextButton(onClick = onRename, enabled = enabled) { Text(localizedString("Rename")) }
            TextButton(onClick = onRemove, enabled = enabled) { Text(localizedString("Remove")) }
        }
    }
}

@Composable
private fun AddWorkspaceDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String?, Boolean) -> Unit,
) {
    var path by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString("Add Workspace")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(localizedString("Workspace path")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(localizedString("Workspace name")) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = create, onCheckedChange = { create = it }, enabled = enabled)
                    Text(localizedString("Create the folder if it doesn't exist"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(path, name, create) }, enabled = enabled && path.isNotBlank()) {
                Text(localizedString("Add"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedString("Cancel")) } },
    )
}

@Composable
private fun RenameWorkspaceDialog(
    workspace: WorkspaceRoot,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(workspace.path) { mutableStateOf(workspace.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedString("Rename Workspace")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(localizedString("Workspace name")) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = enabled && name.isNotBlank()) {
                Text(localizedString("Save"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedString("Cancel")) } },
    )
}
