package com.uzairansar.hermex.ui.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.hermexGlass

@Composable
internal fun KanbanCardEditorRoute(
    repository: KanbanBrowseDataSource,
    board: String,
    mode: KanbanCardEditorMode,
    sessionId: Int,
    profileOptions: List<String>,
    tenantOptions: List<String>,
    prerequisiteOptions: List<KanbanCardSummary>,
    baselineCards: List<KanbanCardSummary>,
    allowsMutation: Boolean,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    onRefreshAndClose: () -> Unit,
) {
    val factory = remember(repository, board, mode, sessionId) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KanbanCardEditorViewModel(
                    repository = repository,
                    board = board,
                    mode = mode,
                    profileOptions = profileOptions,
                    tenantOptions = tenantOptions,
                    prerequisiteOptions = prerequisiteOptions,
                    baselineCards = baselineCards,
                    allowsMutation = allowsMutation,
                ) as T
            }
        }
    }
    val model: KanbanCardEditorViewModel = viewModel(
        key = "kanban-editor:$sessionId:${(mode as? KanbanCardEditorMode.Edit)?.cardId ?: "create"}",
        factory = factory,
    )
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(allowsMutation) { model.updateAllowsMutation(allowsMutation) }
    LaunchedEffect(state.submission) {
        (state.submission as? KanbanCardEditorSubmission.Succeeded)?.let { onSaved(it.cardId) }
    }
    BackHandler(enabled = !state.isInFlight, onBack = onCancel)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexIconButton(localizedString("Cancel"), "‹", onCancel, enabled = !state.isInFlight)
            Text(
                localizedString(if (mode is KanbanCardEditorMode.Edit) "Edit Card" else "New Card"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            HermexPillButton(
                localizedString(if (mode is KanbanCardEditorMode.Edit) "Save" else "Create"),
                model::requestSave,
                filled = true,
                enabled = state.canSubmit,
                modifier = Modifier.testTag("kanban_editor_save"),
            )
        }
        HorizontalDivider()
        when (state.availability) {
            KanbanCardEditorAvailability.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            KanbanCardEditorAvailability.Failed -> EditorUnavailable(model::loadBaseline)
            KanbanCardEditorAvailability.Ready -> KanbanCardEditorContent(
                state = state,
                mode = mode,
                profileOptions = model.profileOptions,
                tenantOptions = model.tenantOptions,
                prerequisiteOptions = model.prerequisiteOptions,
                onDraft = model::updateDraft,
                onRetry = model::retry,
                onReload = model::reloadServerVersion,
                onOverwrite = model::reviewAndOverwrite,
                onRefreshAndClose = onRefreshAndClose,
            )
        }
    }

    if (state.readyUnassignedConfirmation) {
        AlertDialog(
            onDismissRequest = model::dismissReadyConfirmation,
            title = { Text(localizedString("Create Ready, Unassigned Card?")) },
            text = { Text(localizedString("The Dispatcher skips Ready Cards without an Assigned Profile.")) },
            confirmButton = { TextButton(onClick = model::confirmReadyAndSave) { Text(localizedString("Create")) } },
            dismissButton = { TextButton(onClick = model::dismissReadyConfirmation) { Text(localizedString("Cancel")) } },
        )
    }
}

@Composable
private fun EditorUnavailable(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localizedString("Unavailable"), style = MaterialTheme.typography.titleLarge)
            HermexPillButton(localizedString("Try Again"), onRetry, filled = true)
        }
    }
}

@Composable
internal fun KanbanCardEditorContent(
    state: KanbanCardEditorUiState,
    mode: KanbanCardEditorMode,
    profileOptions: List<String>,
    tenantOptions: List<String>,
    prerequisiteOptions: List<KanbanCardSummary>,
    onDraft: ((KanbanCardEditorDraft) -> KanbanCardEditorDraft) -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOverwrite: () -> Unit,
    onRefreshAndClose: () -> Unit,
) {
    val draft = state.draft
    val enabled = !state.isInFlight
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("kanban_card_editor"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("card") {
            EditorSection(localizedString("Card")) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onDraft { it.copy(title = value) } },
                    label = { Text(localizedString("Title")) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_title"),
                )
                OutlinedTextField(
                    value = draft.body,
                    onValueChange = { value -> onDraft { it.copy(body = value) } },
                    label = { Text(localizedString("Description")) },
                    minLines = 4,
                    maxLines = 12,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_body"),
                )
                Text(localizedString("Status"), color = MaterialTheme.colorScheme.secondary)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val statuses = buildList {
                        state.originalStatus?.takeIf { it !in KanbanCardEditorViewModel.CREATE_STATUSES }?.let(::add)
                        addAll(KanbanCardEditorViewModel.CREATE_STATUSES)
                    }.distinct()
                    statuses.forEach { status ->
                        FilterChip(
                            selected = draft.status == status,
                            onClick = { onDraft { it.copy(status = status) } },
                            enabled = enabled && status in KanbanCardEditorViewModel.CREATE_STATUSES,
                            label = { Text(localizedString(kanbanStatusTitleKey(status))) },
                            modifier = Modifier.testTag("kanban_editor_status_$status"),
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.priorityText,
                    onValueChange = { value -> onDraft { it.copy(priorityText = value) } },
                    label = { Text(localizedString("Priority")) },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_priority"),
                )
            }
        }
        item("assignment") {
            EditorSection(localizedString("Assignment")) {
                EditorChoice(
                    label = localizedString("Profile"),
                    value = draft.assignee ?: localizedString("Unassigned"),
                    options = listOf(null) + profileOptions.distinct().map { it },
                    optionTitle = { it ?: localizedString("Unassigned") },
                    enabled = enabled,
                    testTag = "kanban_editor_profile",
                    onSelect = { selected -> onDraft { it.copy(assignee = selected) } },
                )
                OutlinedTextField(
                    value = draft.tenant,
                    onValueChange = { value -> onDraft { it.copy(tenant = value) } },
                    label = { Text(localizedString("Tenant")) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_tenant"),
                )
                if (tenantOptions.isNotEmpty()) {
                    EditorChoice(
                        label = localizedString("Choose Tenant"),
                        value = draft.tenant.ifBlank { localizedString("None") },
                        options = listOf("") + tenantOptions.distinct(),
                        optionTitle = { it.ifBlank { localizedString("None") } },
                        enabled = enabled,
                        testTag = "kanban_editor_tenant_picker",
                        onSelect = { selected -> onDraft { it.copy(tenant = selected) } },
                    )
                }
            }
        }
        item("execution") {
            EditorSection(localizedString("Execution")) {
                val createEnabled = enabled && mode == KanbanCardEditorMode.Create
                EditorChoice(
                    label = localizedString("Workspace"),
                    value = localizedString(workspaceTitle(draft.workspaceKind)),
                    options = KanbanCardEditorViewModel.WORKSPACE_KINDS,
                    optionTitle = { localizedString(workspaceTitle(it)) },
                    enabled = createEnabled,
                    testTag = "kanban_editor_workspace",
                    onSelect = { selected -> onDraft { it.copy(workspaceKind = selected) } },
                )
                if (draft.workspaceKind != "scratch" || mode is KanbanCardEditorMode.Edit) {
                    OutlinedTextField(
                        value = draft.workspacePath,
                        onValueChange = { value -> onDraft { it.copy(workspacePath = value) } },
                        label = { Text(localizedString("Workspace path")) },
                        singleLine = true,
                        enabled = createEnabled,
                        modifier = Modifier.fillMaxWidth().testTag("kanban_editor_workspace_path"),
                    )
                }
                OutlinedTextField(
                    value = draft.skillsText,
                    onValueChange = { value -> onDraft { it.copy(skillsText = value) } },
                    label = { Text(localizedString("Skills")) },
                    singleLine = true,
                    enabled = createEnabled,
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_skills"),
                )
                OutlinedTextField(
                    value = draft.maximumRuntimeText,
                    onValueChange = { value -> onDraft { it.copy(maximumRuntimeText = value) } },
                    label = { Text(localizedString("Maximum Runtime")) },
                    singleLine = true,
                    enabled = createEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_runtime"),
                )
                OutlinedTextField(
                    value = draft.prerequisiteId,
                    onValueChange = { value -> onDraft { it.copy(prerequisiteId = value) } },
                    label = { Text(localizedString("Prerequisite")) },
                    singleLine = true,
                    enabled = createEnabled,
                    modifier = Modifier.fillMaxWidth().testTag("kanban_editor_prerequisite"),
                )
                if (prerequisiteOptions.isNotEmpty()) {
                    EditorChoice(
                        label = localizedString("Choose Prerequisite"),
                        value = draft.prerequisiteId.ifBlank { localizedString("None") },
                        options = listOf("") + prerequisiteOptions.mapNotNull { it.cardId }.distinct(),
                        optionTitle = { id ->
                            prerequisiteOptions.firstOrNull { it.cardId == id }?.title
                                ?.let { "$id — $it" }
                                ?: id.ifBlank { localizedString("None") }
                        },
                        enabled = createEnabled,
                        testTag = "kanban_editor_prerequisite_picker",
                        onSelect = { selected -> onDraft { it.copy(prerequisiteId = selected) } },
                    )
                }
                if (mode is KanbanCardEditorMode.Edit) {
                    Text(
                        localizedString("Workspace, Skills, Maximum Runtime, and Prerequisite are set when the Card is created and cannot be edited here."),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        item("submission") {
            EditorSubmissionSection(
                state = state,
                onRetry = onRetry,
                onReload = onReload,
                onOverwrite = onOverwrite,
                onRefreshAndClose = onRefreshAndClose,
            )
        }
    }
}

@Composable
private fun EditorSubmissionSection(
    state: KanbanCardEditorUiState,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOverwrite: () -> Unit,
    onRefreshAndClose: () -> Unit,
) {
    when (val submission = state.submission) {
        is KanbanCardEditorSubmission.ValidationFailed -> EditorNotice(
            localizedString(editorValidationMessage(submission.field)),
            error = true,
        )
        KanbanCardEditorSubmission.Saving -> EditorNotice(localizedString("Saving"))
        KanbanCardEditorSubmission.CheckingResult -> EditorNotice(localizedString("Checking Result"))
        KanbanCardEditorSubmission.Failed -> EditorSection(localizedString("Failed")) {
            Text(localizedString("The server rejected the request."), color = MaterialTheme.colorScheme.error)
            HermexPillButton(localizedString("Try Again"), onRetry)
        }
        KanbanCardEditorSubmission.OutcomeUncertain -> EditorSection(localizedString("Outcome Uncertain")) {
            Text(localizedString("Refresh the Board before trying again."), color = MaterialTheme.colorScheme.error)
            HermexPillButton(localizedString("Refresh"), onRefreshAndClose)
        }
        KanbanCardEditorSubmission.Conflict -> EditorSection(localizedString("Conflict")) {
            Text(localizedString("This Card changed on the server after the editor opened. Your draft has been preserved."))
            state.remoteCard?.let { remote ->
                EditorValue(localizedString("Server Title"), remote.title ?: localizedString("Untitled Task"))
                EditorValue(localizedString("Server Status"), localizedString(kanbanStatusTitleKey(remote.status.orEmpty())))
            }
            HermexPillButton(localizedString("Reload Server Version"), onReload)
            HermexPillButton(localizedString("Review and Overwrite"), onOverwrite, filled = true)
        }
        else -> Unit
    }
    if (state.capabilityUnavailable) {
        EditorNotice(localizedString("Unavailable"), error = true)
    }
}

@Composable
private fun <T> EditorChoice(
    label: String,
    value: String,
    options: List<T>,
    optionTitle: @Composable (T) -> String,
    enabled: Boolean,
    testTag: String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        HermexPillButton(
            "$label: $value",
            { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionTitle(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false, surfaceLevel = HermexSurfaceLevel.Raised)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun EditorNotice(text: String, error: Boolean = false) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(
            if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            RoundedCornerShape(12.dp),
        ).padding(12.dp),
        color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun EditorValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
        Text(value, modifier = Modifier.weight(1.2f))
    }
}

private fun workspaceTitle(kind: String): String = when (kind) {
    "worktree" -> "Worktree"
    "dir" -> "Directory"
    else -> "Scratch"
}

private fun editorValidationMessage(field: KanbanCardEditorField): String = when (field) {
    KanbanCardEditorField.Title -> "Title is required."
    KanbanCardEditorField.Priority -> "Priority must be a whole number from -100 through 100."
    KanbanCardEditorField.Status -> "Choose a supported Status."
    KanbanCardEditorField.WorkspacePath -> "Workspace path is required for this workspace kind."
    KanbanCardEditorField.MaximumRuntime -> "Maximum Runtime must be a whole number of seconds greater than 0."
    KanbanCardEditorField.Prerequisite -> "Choose a valid Prerequisite."
}
