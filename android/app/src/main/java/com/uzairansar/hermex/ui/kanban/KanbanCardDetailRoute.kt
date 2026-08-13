package com.uzairansar.hermex.ui.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanComment
import com.uzairansar.hermex.core.model.KanbanDetailEvent
import com.uzairansar.hermex.core.model.KanbanDispatchRun
import com.uzairansar.hermex.core.model.KanbanDependencyLinks
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.ui.chat.MarkdownText
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.hermexGlass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun KanbanCardDetailRoute(
    repository: KanbanBrowseDataSource,
    board: String,
    cardId: String,
    parentOffline: Boolean,
    parentAllowsWrites: Boolean,
    refreshRevision: Int,
    onBack: () -> Unit,
    onOpenRelatedCard: (String) -> Unit,
    onEdit: (String) -> Unit,
    workflowState: KanbanWorkflowUiState,
    allCards: List<KanbanCardSummary>,
    canUseWorkflow: Boolean,
    moveDestinations: (KanbanCardSummary) -> List<String>,
    displayedCard: (KanbanCardSummary) -> KanbanCardSummary,
    displayedPrerequisites: (String, List<String>) -> List<String>,
    onLoadedCardDetail: (String) -> Unit,
    onWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit,
    onRetryMutation: (KanbanCardSummary) -> Unit,
    onCheckMutation: (KanbanCardSummary) -> Unit,
    onAddPrerequisite: (String, KanbanCardSummary) -> Unit,
    onRemovePrerequisite: (String, KanbanCardSummary) -> Unit,
    onUndoArchive: () -> Unit,
) {
    val factory = remember(repository, board, cardId, parentAllowsWrites) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KanbanCardDetailViewModel(repository, board, cardId, parentAllowsWrites) as T
            }
        }
    }
    val model: KanbanCardDetailViewModel = viewModel(
        key = "kanban-card:$board:$cardId",
        factory = factory,
    )
    val state by model.state.collectAsStateWithLifecycle()
    val canonicalDetail = state.detail
    val canonicalCard = canonicalDetail?.card
    val renderedCard = canonicalCard?.let(displayedCard)
    val canonicalPrerequisites = canonicalDetail?.links?.prerequisites.orEmpty()
    val renderedPrerequisites = canonicalCard?.cardId?.let { displayedPrerequisites(it, canonicalPrerequisites) }
        ?: canonicalPrerequisites
    val renderedDetail = canonicalDetail?.copy(
        card = renderedCard,
        links = (canonicalDetail.links ?: KanbanDependencyLinks()).copy(prerequisites = renderedPrerequisites),
    )
    val renderedState = state.copy(detail = renderedDetail)

    LaunchedEffect(parentOffline, parentAllowsWrites, refreshRevision) {
        model.updateParentState(parentOffline, parentAllowsWrites, refreshRevision)
    }
    LaunchedEffect(state.detail) {
        if (state.availability == KanbanCardDetailAvailability.Content) {
            state.detail?.card?.cardId?.let(onLoadedCardDetail)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexIconButton(localizedString("Back"), "‹", onBack)
            Text(
                renderedCard?.title?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: localizedString("Card"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            HermexPillButton(
                localizedString("Edit"),
                { state.detail?.card?.cardId?.let(onEdit) },
                enabled = state.availability == KanbanCardDetailAvailability.Content &&
                    state.parentAllowsWrites &&
                    state.detail?.readOnly == false &&
                    !state.isStale,
                modifier = Modifier.testTag("kanban_edit_card"),
            )
            renderedCard?.let { card ->
                KanbanCardActionsMenu(
                    card = card,
                    destinations = moveDestinations(card),
                    enabled = canUseWorkflow && card.cardId !in workflowState.activeCardIds && card.hasSupportedStatus,
                    onAction = onWorkflowAction,
                )
            }
            HermexIconButton(
                localizedString("Refresh"),
                "↻",
                model::load,
                enabled = !state.isRefreshing,
            )
        }
        HorizontalDivider()
        when (state.availability) {
            KanbanCardDetailAvailability.Loading -> DetailLoading()
            KanbanCardDetailAvailability.Content -> KanbanCardDetailContent(
                state = renderedState,
                onCommentDraft = model::updateCommentDraft,
                onSubmitComment = model::submitComment,
                onRetryComment = model::retryComment,
                onLoadWorkerLog = model::loadWorkerLog,
                onOpenRelatedCard = onOpenRelatedCard,
                workflowState = workflowState,
                allCards = allCards,
                canUseWorkflow = canUseWorkflow,
                moveDestinations = moveDestinations,
                onWorkflowAction = onWorkflowAction,
                onRetryMutation = onRetryMutation,
                onCheckMutation = onCheckMutation,
                onAddPrerequisite = onAddPrerequisite,
                onRemovePrerequisite = onRemovePrerequisite,
                onUndoArchive = onUndoArchive,
            )
            else -> DetailUnavailable(state.availability, model::load)
        }
    }
}

@Composable
private fun DetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun DetailUnavailable(availability: KanbanCardDetailAvailability, onRetry: () -> Unit) {
    val message = when (availability) {
        KanbanCardDetailAvailability.Missing -> localizedString("No Content")
        KanbanCardDetailAvailability.NetworkUnavailable -> localizedString("Could not connect to the server. Check that hermes-webui is running and the tunnel is connected.")
        KanbanCardDetailAvailability.IncompatibleContract -> localizedString("Incompatible Kanban response")
        else -> localizedString("Unavailable")
    }
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.titleLarge)
            HermexPillButton(localizedString("Try Again"), onRetry, filled = true)
        }
    }
}

@Composable
internal fun KanbanCardDetailContent(
    state: KanbanCardDetailUiState,
    onCommentDraft: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onRetryComment: () -> Unit,
    onLoadWorkerLog: () -> Unit,
    onOpenRelatedCard: (String) -> Unit,
    workflowState: KanbanWorkflowUiState = KanbanWorkflowUiState(),
    allCards: List<KanbanCardSummary> = emptyList(),
    canUseWorkflow: Boolean = false,
    moveDestinations: (KanbanCardSummary) -> List<String> = { emptyList() },
    onWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit = { _, _ -> },
    onRetryMutation: (KanbanCardSummary) -> Unit = {},
    onCheckMutation: (KanbanCardSummary) -> Unit = {},
    onAddPrerequisite: (String, KanbanCardSummary) -> Unit = { _, _ -> },
    onRemovePrerequisite: (String, KanbanCardSummary) -> Unit = { _, _ -> },
    onUndoArchive: () -> Unit = {},
) {
    val detail = state.detail ?: return
    val card = detail.card ?: return
    var showsOperationalHistory by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("kanban_card_detail"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        workflowState.archiveUndo?.takeIf { it.cardId == card.cardId }?.let { undo ->
            item("archive-undo") {
                KanbanArchiveUndoBanner(
                    undo = undo,
                    mutation = workflowState.mutations[undo.cardId],
                    onUndo = onUndoArchive,
                    onCheck = { onCheckMutation(undo.card) },
                )
            }
        }
        if (state.isStale) {
            item("stale") {
                DetailNotice(
                    localizedString("Offline—showing previously loaded data"),
                    Modifier.testTag("kanban_detail_stale"),
                )
            }
        }
        item("description") { DescriptionSection(card) }
        item("metadata") { MetadataSection(card) }
        workflowState.mutations[card.cardId]?.let { mutation ->
            item("mutation") { KanbanMutationStatus(card, mutation, onRetryMutation, onCheckMutation) }
        }
        item("comments") {
            CommentsSection(
                comments = detail.comments.orEmpty(),
                state = state,
                onCommentDraft = onCommentDraft,
                onSubmitComment = onSubmitComment,
                onRetryComment = onRetryComment,
            )
        }
        item("dependencies") {
            DependenciesSection(
                card = card,
                prerequisites = detail.links?.prerequisites.orEmpty(),
                dependents = detail.links?.dependents.orEmpty(),
                allCards = allCards,
                canMutate = canUseWorkflow && card.cardId !in workflowState.activeCardIds && !state.isStale,
                onOpenRelatedCard = onOpenRelatedCard,
                onAddPrerequisite = onAddPrerequisite,
                onRemovePrerequisite = onRemovePrerequisite,
            )
        }
        item("operational-toggle") {
            HermexPillButton(
                localizedString("Operational History"),
                { showsOperationalHistory = !showsOperationalHistory },
                filled = showsOperationalHistory,
                modifier = Modifier.fillMaxWidth().testTag("kanban_operational_history"),
            )
        }
        if (showsOperationalHistory) {
            item("events") { EventsSection(detail.events.orEmpty()) }
            item("runs") { RunsSection(detail.runs.orEmpty()) }
            item("operational-metadata") { OperationalMetadataSection(card) }
            item("worker-log") { WorkerLogSection(state.workerLog, state.isStale, onLoadWorkerLog) }
        }
    }
}

@Composable
private fun DescriptionSection(card: KanbanCardSummary) {
    DetailSection(localizedString("Description")) {
        val body = card.body?.trim()
        if (body.isNullOrEmpty()) {
            Text(localizedString("No Content"), color = MaterialTheme.colorScheme.secondary)
        } else {
            SelectionContainer { MarkdownText(body, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun MetadataSection(card: KanbanCardSummary) {
    DetailSection(localizedString("Metadata")) {
        DetailValue(localizedString("Card ID"), card.cardId ?: localizedString("Unknown"), selectable = true)
        DetailValue(localizedString("Status"), localizedString(kanbanStatusTitleKey(card.status.orEmpty())))
        DetailValue(localizedString("Profile"), card.assignee ?: localizedString("Unassigned"))
        card.tenant?.takeIf(String::isNotBlank)?.let { DetailValue(localizedString("Tenant"), it, selectable = true) }
        card.priority?.let { DetailValue(localizedString("Priority"), "P$it") }
        formatKanbanDetailDate(card.createdAt)?.let { DetailValue(localizedString("Created"), it) }
        formatKanbanDetailDate(card.updatedAt)?.let { DetailValue(localizedString("Updated"), it) }
        card.skills?.takeIf { it.isNotEmpty() }?.let { DetailValue(localizedString("Skills"), it.joinToString(), selectable = true) }
        card.maxRuntimeSeconds?.let { DetailValue(localizedString("Maximum Runtime"), formatKanbanDuration(it)) }
    }
}

@Composable
private fun CommentsSection(
    comments: List<KanbanComment>,
    state: KanbanCardDetailUiState,
    onCommentDraft: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onRetryComment: () -> Unit,
) {
    DetailSection(localizedString("Comment")) {
        if (comments.isEmpty()) Text(localizedString("No Content"), color = MaterialTheme.colorScheme.secondary)
        comments.forEach { comment -> CommentRow(comment) }
        if (state.parentAllowsWrites && state.detail?.readOnly == false) {
            OutlinedTextField(
                value = state.commentDraft,
                onValueChange = onCommentDraft,
                modifier = Modifier.fillMaxWidth().testTag("kanban_comment_draft"),
                label = { Text(localizedString("Comment")) },
                minLines = 2,
                enabled = state.canComment,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HermexPillButton(
                    localizedString("Send"),
                    onSubmitComment,
                    filled = true,
                    enabled = state.canComment,
                    modifier = Modifier.testTag("kanban_comment_send"),
                )
            }
        } else {
            Text(
                localizedString(if (state.isStale) "Offline Data" else "Read-only"),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        when (state.commentSubmission) {
            KanbanCommentSubmissionState.ValidationFailed -> Text(localizedString("Comment cannot be blank."), color = MaterialTheme.colorScheme.error)
            KanbanCommentSubmissionState.Submitting -> Text(localizedString("Loading"), color = MaterialTheme.colorScheme.secondary)
            KanbanCommentSubmissionState.CheckingResult -> Text(localizedString("Checking Result"), color = MaterialTheme.colorScheme.secondary)
            KanbanCommentSubmissionState.Succeeded -> Text(localizedString("Added"), color = MaterialTheme.colorScheme.primary)
            KanbanCommentSubmissionState.RetryAllowed -> {
                Text(localizedString("Failed"), color = MaterialTheme.colorScheme.error)
                HermexPillButton(localizedString("Try Again"), onRetryComment)
            }
            KanbanCommentSubmissionState.OutcomeUncertain -> Text(localizedString("Outcome Uncertain"), color = MaterialTheme.colorScheme.error)
            KanbanCommentSubmissionState.Failed -> Text(localizedString("Unavailable"), color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}

@Composable
private fun CommentRow(comment: KanbanComment) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SelectionContainer { MarkdownText(comment.body.orEmpty()) }
        val metadata = listOfNotNull(comment.author?.trim(), formatKanbanDetailDate(comment.createdAt)).joinToString(" · ")
        if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun DependenciesSection(
    card: KanbanCardSummary,
    prerequisites: List<String>,
    dependents: List<String>,
    allCards: List<KanbanCardSummary>,
    canMutate: Boolean,
    onOpenRelatedCard: (String) -> Unit,
    onAddPrerequisite: (String, KanbanCardSummary) -> Unit,
    onRemovePrerequisite: (String, KanbanCardSummary) -> Unit,
) {
    DetailSection(localizedString("Dependencies")) {
        PrerequisiteGroup(
            card = card,
            ids = prerequisites,
            options = allCards.filter { option ->
                val optionId = option.cardId
                optionId != null && optionId != card.cardId && optionId !in prerequisites
            },
            canMutate = canMutate,
            onOpenRelatedCard = onOpenRelatedCard,
            onAdd = onAddPrerequisite,
            onRemove = onRemovePrerequisite,
        )
        DependencyGroup(localizedString("Dependencies"), dependents, onOpenRelatedCard)
    }
}

@Composable
private fun PrerequisiteGroup(
    card: KanbanCardSummary,
    ids: List<String>,
    options: List<KanbanCardSummary>,
    canMutate: Boolean,
    onOpenRelatedCard: (String) -> Unit,
    onAdd: (String, KanbanCardSummary) -> Unit,
    onRemove: (String, KanbanCardSummary) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Text("${localizedString("Prerequisite")} · ${ids.size}", style = MaterialTheme.typography.titleSmall)
    if (ids.isEmpty()) Text(localizedString("None"), color = MaterialTheme.colorScheme.secondary)
    ids.forEach { id ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                id,
                modifier = Modifier.weight(1f).clickable { onOpenRelatedCard(id) }.padding(vertical = 8.dp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            HermexPillButton(
                localizedString("Remove"),
                { onRemove(id, card) },
                enabled = canMutate,
                modifier = Modifier.testTag("kanban_remove_prerequisite_$id"),
            )
        }
    }
    if (options.isNotEmpty()) {
        Box {
            HermexPillButton(
                localizedString("Add Prerequisite"),
                { menuExpanded = true },
                enabled = canMutate,
                modifier = Modifier.fillMaxWidth().testTag("kanban_add_prerequisite"),
            )
            androidx.compose.material3.DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                options.forEach { option ->
                    val optionId = option.cardId ?: return@forEach
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(option.title?.takeIf(String::isNotBlank) ?: optionId) },
                        onClick = {
                            menuExpanded = false
                            onAdd(optionId, card)
                        },
                        modifier = Modifier.testTag("kanban_prerequisite_option_$optionId"),
                    )
                }
            }
        }
    }
}

@Composable
private fun DependencyGroup(title: String, ids: List<String>, onOpenRelatedCard: (String) -> Unit) {
    Text("$title · ${ids.size}", style = MaterialTheme.typography.titleSmall)
    if (ids.isEmpty()) Text(localizedString("None"), color = MaterialTheme.colorScheme.secondary)
    ids.forEach { id ->
        Text(
            id,
            modifier = Modifier.fillMaxWidth().clickable { onOpenRelatedCard(id) }.padding(vertical = 8.dp),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EventsSection(events: List<KanbanDetailEvent>) {
    DetailSection(localizedString("Events"), Modifier.testTag("kanban_detail_events")) {
        if (events.isEmpty()) Text(localizedString("No Content"), color = MaterialTheme.colorScheme.secondary)
        events.forEach { event ->
            val summary = listOfNotNull(
                event.kind,
                event.payload?.status,
                event.payload?.reason,
                event.payload?.summary,
                event.payload?.fields?.joinToString(),
            ).filter(String::isNotBlank).joinToString(": ")
            DetailHistoryRow(summary.ifBlank { localizedString("Events") }, formatKanbanDetailDate(event.createdAt))
        }
    }
}

@Composable
private fun RunsSection(runs: List<KanbanDispatchRun>) {
    DetailSection(localizedString("Dispatch Runs"), Modifier.testTag("kanban_detail_runs")) {
        if (runs.isEmpty()) Text(localizedString("No Content"), color = MaterialTheme.colorScheme.secondary)
        runs.forEach { run ->
            val title = run.summary?.takeIf(String::isNotBlank)
                ?: run.outcome?.takeIf(String::isNotBlank)
                ?: run.status?.takeIf(String::isNotBlank)
                ?: localizedString("Run")
            val dates = listOfNotNull(formatKanbanDetailDate(run.startedAt), formatKanbanDetailDate(run.completedAt)).joinToString(" → ")
            DetailHistoryRow(title, dates.takeIf(String::isNotBlank))
            run.error?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun OperationalMetadataSection(card: KanbanCardSummary) {
    DetailSection(localizedString("Operational Metadata")) {
        card.workspaceKind?.takeIf(String::isNotBlank)?.let { DetailValue(localizedString("Workspace"), it) }
        card.workspacePath?.takeIf(String::isNotBlank)?.let { DetailValue(localizedString("Workspace Path"), it, selectable = true) }
        card.currentRunId?.takeIf(String::isNotBlank)?.let { DetailValue(localizedString("Run ID"), it, selectable = true) }
        card.claimLock?.takeIf(String::isNotBlank)?.let { DetailValue(localizedString("Claim ID"), it, selectable = true) }
        card.claimExpires?.takeIf(String::isNotBlank)?.let {
            DetailValue(localizedString("Claim Expires"), formatKanbanDetailDate(it) ?: it)
        }
        card.workerId?.takeIf(String::isNotBlank)?.let { DetailValue(localizedString("Worker ID"), it, selectable = true) }
        if (
            listOf(card.workspaceKind, card.workspacePath, card.currentRunId, card.claimLock, card.claimExpires, card.workerId)
                .all { it.isNullOrBlank() }
        ) {
            Text(localizedString("No Content"), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun WorkerLogSection(state: KanbanWorkerLogState, isStale: Boolean, onLoad: () -> Unit) {
    DetailSection(localizedString("Worker Log")) {
        when (state) {
            KanbanWorkerLogState.Idle -> HermexPillButton(
                localizedString("Load Worker Log"),
                onLoad,
                enabled = !isStale,
                modifier = Modifier.testTag("kanban_load_worker_log"),
            )
            KanbanWorkerLogState.Loading -> CircularProgressIndicator(strokeWidth = 2.dp)
            KanbanWorkerLogState.Absent -> Text(localizedString("No Content"), color = MaterialTheme.colorScheme.secondary)
            is KanbanWorkerLogState.Loaded -> {
                if (state.log.truncated == true) Text(localizedString("Only the last part of this log is shown."), color = MaterialTheme.colorScheme.secondary)
                SelectionContainer {
                    Text(state.log.content.orEmpty(), fontFamily = FontFamily.Monospace)
                }
            }
            KanbanWorkerLogState.Failed -> {
                Text(localizedString("Unavailable"), color = MaterialTheme.colorScheme.error)
                HermexPillButton(localizedString("Try Again"), onLoad, enabled = !isStale)
            }
        }
    }
}

@Composable
private fun DetailHistoryRow(title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title)
        subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun DetailNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics { contentDescription = text },
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false, surfaceLevel = HermexSurfaceLevel.Raised)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun DetailValue(label: String, value: String, selectable: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
        if (selectable) {
            SelectionContainer(Modifier.weight(1.2f)) { Text(value) }
        } else {
            Text(value, modifier = Modifier.weight(1.2f))
        }
    }
}

private fun formatKanbanDetailDate(value: String?): String? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(normalized))
    }.getOrDefault(normalized)
}

private fun formatKanbanDuration(seconds: Int): String {
    val bounded = seconds.coerceAtLeast(0)
    val hours = bounded / 3_600
    val minutes = (bounded % 3_600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
