package com.uzairansar.hermex.ui.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.localization.localizedPluralString
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.hermexGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanLabRoute(
    repository: KanbanBrowseDataSource,
    onBack: () -> Unit,
    viewModelKey: String = "kanban-lab",
    liveTiming: KanbanLiveTiming = KanbanLiveTiming(),
) {
    val factory = remember(repository, liveTiming) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KanbanLabViewModel(repository, liveTiming) as T
            }
        }
    }
    val viewModel: KanbanLabViewModel = viewModel(key = viewModelKey, factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workflowState by viewModel.workflowState.collectAsStateWithLifecycle()
    val bulkState by viewModel.bulkState.collectAsStateWithLifecycle()
    val boardState by viewModel.boardState.collectAsStateWithLifecycle()
    var showsFilters by rememberSaveable { mutableStateOf(false) }
    var showsBulkActions by rememberSaveable { mutableStateOf(false) }
    var showsBoardManagement by rememberSaveable { mutableStateOf(false) }
    var cardNavigationStack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSessionId by rememberSaveable { mutableStateOf(0) }
    var pendingRunningAction by remember { mutableStateOf<KanbanPendingRunningAction?>(null) }
    var pendingRunningBulkAction by remember { mutableStateOf<KanbanBulkAction?>(null) }
    var confirmsBulkArchive by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.selectedBoardSlug) {
        if (state.selectedBoardSlug == null) {
            cardNavigationStack = emptyList()
            editorOpen = false
            editorCardId = null
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setLifecycleActive(true)
                Lifecycle.Event.ON_STOP -> viewModel.setLifecycleActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setLifecycleActive(false)
            viewModel.setVisible(false)
        }
    }

    val selectedBoardSlug = state.selectedBoardSlug
    val selectedCardId = cardNavigationStack.lastOrNull()
    val showsEditor = editorOpen && selectedBoardSlug != null && state.availability == KanbanAvailability.Content
    val showsCardDetail = selectedCardId != null &&
        selectedBoardSlug != null &&
        state.availability == KanbanAvailability.Content
    val cardWritesAllowed = state.canMutateCards && bulkState.phase == null && !boardState.blocksWrites
    val performWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction, Boolean) -> Unit =
        { card, action, confirmingRunningExit ->
            when (action) {
                is KanbanCardWorkflowAction.Move -> viewModel.moveCard(card, action.status, confirmingRunningExit)
                KanbanCardWorkflowAction.Block -> viewModel.blockCard(card, confirmingRunningExit)
                KanbanCardWorkflowAction.Unblock -> viewModel.unblockCard(card)
                KanbanCardWorkflowAction.Complete -> viewModel.completeCard(card, confirmingRunningExit)
                KanbanCardWorkflowAction.Archive -> viewModel.archiveCard(card, confirmingRunningExit)
            }
        }
    val requestWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit = { card, action ->
        if (card.status == "running") {
            pendingRunningAction = KanbanPendingRunningAction(card, action)
        } else {
            performWorkflowAction(card, action, false)
        }
    }
    val retryMutation: (KanbanCardSummary) -> Unit = { card ->
        val retryAction = workflowState.mutations[card.cardId]?.kind?.runningRetryAction()
        if (card.status == "running" && retryAction != null) {
            pendingRunningAction = KanbanPendingRunningAction(card, retryAction)
        } else {
            viewModel.retryMutation(card)
        }
    }

    if (!showsEditor && !showsCardDetail) {
        BackHandler(enabled = bulkState.isSelectingCards && bulkState.phase == null) {
            viewModel.clearCardSelection()
        }
    }

    if (showsEditor) {
        KanbanCardEditorRoute(
            repository = repository,
            board = selectedBoardSlug,
            mode = editorCardId?.let(KanbanCardEditorMode::Edit) ?: KanbanCardEditorMode.Create,
            sessionId = editorSessionId,
            profileOptions = state.profileOptions,
            tenantOptions = state.tenantOptions,
            prerequisiteOptions = state.snapshot?.allCards().orEmpty().filter { it.cardId != editorCardId },
            baselineCards = state.snapshot?.allCards().orEmpty(),
            allowsMutation = cardWritesAllowed,
            onCancel = { editorOpen = false },
            onSaved = {
                editorOpen = false
                viewModel.load()
            },
            onRefreshAndClose = {
                editorOpen = false
                viewModel.load()
            },
        )
    } else if (showsCardDetail) {
        BackHandler { cardNavigationStack = cardNavigationStack.dropLast(1) }
        KanbanCardDetailRoute(
            repository = repository,
            board = selectedBoardSlug,
            cardId = checkNotNull(selectedCardId),
            parentOffline = state.isOffline,
            parentAllowsWrites = cardWritesAllowed,
            refreshRevision = state.detailRefreshRevision,
            onBack = { cardNavigationStack = cardNavigationStack.dropLast(1) },
            onOpenRelatedCard = { related ->
                if (related != cardNavigationStack.lastOrNull()) cardNavigationStack = cardNavigationStack + related
            },
            onEdit = { cardId ->
                editorSessionId += 1
                editorCardId = cardId
                editorOpen = true
            },
            workflowState = workflowState,
            allCards = state.snapshot?.allCards().orEmpty(),
            canUseWorkflow = state.canUseCardWorkflow && bulkState.phase == null && !boardState.blocksWrites,
            moveDestinations = viewModel::moveDestinations,
            displayedCard = viewModel::displayedCard,
            displayedPrerequisites = viewModel::displayedPrerequisites,
            onLoadedCardDetail = viewModel::acknowledgeLoadedCardDetail,
            onWorkflowAction = requestWorkflowAction,
            onRetryMutation = retryMutation,
            onCheckMutation = viewModel::checkUncertainMutation,
            onAddPrerequisite = viewModel::addPrerequisite,
            onRemovePrerequisite = viewModel::removePrerequisite,
            onUndoArchive = viewModel::undoArchive,
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            KanbanTopBar(
                state = state,
                onBack = onBack,
                onRefresh = viewModel::load,
                onSelectBoard = viewModel::selectBoard,
                onShowFilters = { showsFilters = true },
                onManageBoards = { showsBoardManagement = true },
                canCreateCard = cardWritesAllowed,
                onCreateCard = {
                    editorSessionId += 1
                    editorCardId = null
                    editorOpen = true
                },
                isSelectingCards = bulkState.isSelectingCards,
                canSelectCards = viewModel.canUseBulkActions(),
                boardWriteInProgress = bulkState.phase != null || boardState.blocksWrites,
                onToggleSelectingCards = {
                    if (bulkState.isSelectingCards) viewModel.clearCardSelection() else viewModel.beginSelectingCards()
                },
            )
            when (state.availability) {
                KanbanAvailability.Loading -> KanbanLoading()
                KanbanAvailability.Content -> KanbanBoardContent(
                    state = state,
                    workflowState = workflowState,
                    onRefresh = viewModel::load,
                    onSearch = viewModel::setSearchQuery,
                    onSelectStatus = viewModel::selectStatus,
                    onClearFilters = viewModel::clearFilters,
                    onSelectBoard = viewModel::selectBoard,
                    onOpenCard = { cardNavigationStack = listOf(it) },
                    moveDestinations = viewModel::moveDestinations,
                    canMutateCard = viewModel::canMutateCard,
                    onWorkflowAction = requestWorkflowAction,
                    onRetryMutation = retryMutation,
                    onCheckMutation = viewModel::checkUncertainMutation,
                    onUndoArchive = viewModel::undoArchive,
                    bulkState = bulkState,
                    bulkAvailability = viewModel.bulkActionsAvailability(),
                    canRetryFailedBulk = viewModel.canRetryFailedBulkAction(),
                    canCheckUncertainBulk = viewModel.canCheckUncertainBulkAction(),
                    onToggleCardSelection = viewModel::toggleCardSelection,
                    onShowBulkActions = { showsBulkActions = true },
                    onClearSelection = viewModel::clearCardSelection,
                    onDismissBulkSummary = viewModel::dismissBulkActionSummary,
                    onRetryFailedBulk = viewModel::retryFailedBulkAction,
                    onCheckUncertainBulk = viewModel::checkUncertainBulkAction,
                )
                else -> KanbanUnavailable(state.availability, viewModel::load)
            }
        }
    }

    if (showsFilters && !showsEditor && !showsCardDetail && state.availability == KanbanAvailability.Content) {
        KanbanFiltersSheet(
            state = state,
            onDismiss = { showsFilters = false },
            onApply = { filters ->
                showsFilters = false
                viewModel.applyFilters(filters)
            },
            onClear = {
                showsFilters = false
                viewModel.clearFilters()
            },
        )
    }

    if (showsBulkActions && !showsEditor && !showsCardDetail && state.availability == KanbanAvailability.Content) {
        KanbanBulkActionsSheet(
            selectedCount = bulkState.selectedCardCount,
            statuses = state.configuration?.columns.orEmpty().filter { it != "running" },
            profiles = state.profileOptions,
            canSubmit = viewModel::canSubmitBulkAction,
            onDismiss = { if (bulkState.phase == null) showsBulkActions = false },
            onSubmit = { action ->
                when {
                    action is KanbanBulkAction.ArchiveCards -> {
                        showsBulkActions = false
                        confirmsBulkArchive = true
                    }
                    action is KanbanBulkAction.ChangeStatus && viewModel.bulkSelectionContainsRunning() -> {
                        showsBulkActions = false
                        pendingRunningBulkAction = action
                    }
                    else -> {
                        showsBulkActions = false
                        viewModel.performBulkAction(action)
                    }
                }
            },
        )
    }

    if (showsBoardManagement && !showsEditor && !showsCardDetail && state.availability == KanbanAvailability.Content) {
        KanbanBoardManagementSheet(
            state = state,
            managementState = boardState,
            canManageBoards = viewModel.canManageBoards(),
            onDismiss = { showsBoardManagement = false },
            onBrowse = viewModel::selectBoard,
            onCreate = viewModel::createBoard,
            onEdit = viewModel::editBoard,
            onArchive = viewModel::archiveBoard,
            onMakeActive = viewModel::makeBoardActive,
            onCheckResult = viewModel::checkBoardMutationResult,
            onDismissResult = viewModel::dismissBoardMutationResult,
        )
    }

    pendingRunningAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRunningAction = null },
            title = { Text(localizedString("Leave Running?")) },
            text = { Text(localizedString("Leaving Running may clear the Card's claim and worker state.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRunningAction = null
                        performWorkflowAction(pending.card, pending.action, true)
                    },
                    modifier = Modifier.testTag("kanban_confirm_running_exit"),
                ) { Text(localizedString("Continue")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRunningAction = null }) { Text(localizedString("Cancel")) }
            },
        )
    }


    pendingRunningBulkAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingRunningBulkAction = null },
            title = { Text(localizedString("Leave Running?")) },
            text = { Text(localizedString("Leaving Running may clear the Card's claim and worker state.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRunningBulkAction = null
                        viewModel.performBulkAction(action, confirmedRunningExit = true)
                    },
                    modifier = Modifier.testTag("kanban_bulk_confirm_running_exit"),
                ) { Text(localizedString("Continue")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRunningBulkAction = null }) { Text(localizedString("Cancel")) }
            },
        )
    }

    if (confirmsBulkArchive) {
        val containsRunning = viewModel.bulkSelectionContainsRunning()
        AlertDialog(
            onDismissRequest = { confirmsBulkArchive = false },
            title = { Text(localizedString("Archive Cards")) },
            text = {
                Text(
                    if (containsRunning) {
                        "${localizedString("The selected Cards will be moved to the archive.")} ${localizedString("Leaving Running may clear the Card's claim and worker state.")}"
                    } else {
                        localizedString("The selected Cards will be moved to the archive.")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmsBulkArchive = false
                        viewModel.performBulkAction(
                            KanbanBulkAction.ArchiveCards,
                            confirmedRunningExit = containsRunning,
                            confirmedArchive = true,
                        )
                    },
                    modifier = Modifier.testTag("kanban_bulk_confirm_archive"),
                ) { Text(localizedString("Archive Cards"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmsBulkArchive = false }) { Text(localizedString("Cancel")) }
            },
        )
    }
}

private data class KanbanPendingRunningAction(
    val card: KanbanCardSummary,
    val action: KanbanCardWorkflowAction,
)

private fun KanbanCardMutationKind.runningRetryAction(): KanbanCardWorkflowAction? = when (this) {
    is KanbanCardMutationKind.Status -> if (status == "done") KanbanCardWorkflowAction.Complete else KanbanCardWorkflowAction.Move(status)
    is KanbanCardMutationKind.Block -> KanbanCardWorkflowAction.Block
    is KanbanCardMutationKind.Archive -> KanbanCardWorkflowAction.Archive
    else -> null
}

@Composable
private fun KanbanTopBar(
    state: KanbanLabUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectBoard: (String) -> Unit,
    onShowFilters: () -> Unit,
    onManageBoards: () -> Unit,
    canCreateCard: Boolean,
    onCreateCard: () -> Unit,
    isSelectingCards: Boolean,
    canSelectCards: Boolean,
    boardWriteInProgress: Boolean,
    onToggleSelectingCards: () -> Unit,
) {
    var boardMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HermexIconButton(localizedString("Back"), "‹", onBack)
        Box(Modifier.weight(1f)) {
            HermexPillButton(
                label = state.selectedBoard?.name ?: state.selectedBoardSlug ?: localizedString("Kanban"),
                onClick = { if (state.boards.isNotEmpty()) boardMenuExpanded = true },
                enabled = state.boards.isNotEmpty() && !state.isRefreshing && !boardWriteInProgress,
                modifier = Modifier.fillMaxWidth().testTag("kanban_board_picker"),
            )
            DropdownMenu(
                expanded = boardMenuExpanded,
                onDismissRequest = { boardMenuExpanded = false },
            ) {
                state.boards.forEach { board ->
                    val slug = board.slug?.trim().orEmpty()
                    if (slug.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (slug == state.selectedBoardSlug) "✓ ${board.displayName()}" else board.displayName(),
                                )
                            },
                            onClick = {
                                boardMenuExpanded = false
                                onSelectBoard(slug)
                            },
                        )
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(localizedString("Manage")) },
                    onClick = {
                        boardMenuExpanded = false
                        onManageBoards()
                    },
                    modifier = Modifier.testTag("kanban_manage_boards"),
                )
            }
        }
        HermexIconButton(
            localizedString(if (isSelectingCards) "Cancel" else "Select Cards"),
            if (isSelectingCards) "×" else "✓",
            onToggleSelectingCards,
            enabled = !boardWriteInProgress && (canSelectCards || isSelectingCards),
            modifier = Modifier.testTag("kanban_select_cards"),
        )
        HermexIconButton(
            localizedString("New Card"),
            "+",
            onCreateCard,
            enabled = canCreateCard,
            modifier = Modifier.testTag("kanban_new_card"),
        )
        HermexIconButton(localizedString("Refresh"), "↻", onRefresh, enabled = !state.isRefreshing)
        HermexIconButton(
            localizedString("Filters"),
            if (state.hasActiveFilters) "●" else "≡",
            onShowFilters,
            enabled = state.availability == KanbanAvailability.Content && !state.isRefreshing,
            modifier = Modifier.testTag("kanban_filters"),
        )
    }
}

@Composable
private fun KanbanLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Text(localizedString("Loading"), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun KanbanUnavailable(availability: KanbanAvailability, onRetry: () -> Unit) {
    val symbol = when (availability) {
        KanbanAvailability.AuthenticationRequired -> "🔒"
        KanbanAvailability.NetworkUnavailable -> "⌁"
        KanbanAvailability.IncompatibleContract -> "⚠"
        else -> "!"
    }
    val detail = when (availability) {
        KanbanAvailability.AuthenticationRequired -> localizedString("Your session expired. Sign in again.")
        KanbanAvailability.NetworkUnavailable -> localizedString("Could not connect to the server. Check that hermes-webui is running and the tunnel is connected.")
        KanbanAvailability.IncompatibleContract -> localizedString("Incompatible Kanban response")
        else -> localizedString("The server or Cloudflare tunnel is unavailable. Check that the Mac is awake, hermes-webui is running, and the tunnel is connected.")
    }
    Box(
        Modifier.fillMaxSize().padding(28.dp).testTag("kanban_unavailable_${availability.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(symbol, style = MaterialTheme.typography.headlineMedium)
            Text(localizedString("Kanban"), style = MaterialTheme.typography.headlineSmall)
            Text(detail, color = MaterialTheme.colorScheme.secondary)
            HermexPillButton(localizedString("Retry"), onRetry, filled = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanBoardContent(
    state: KanbanLabUiState,
    workflowState: KanbanWorkflowUiState = KanbanWorkflowUiState(),
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectStatus: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSelectBoard: (String) -> Unit = {},
    onOpenCard: (String) -> Unit = {},
    moveDestinations: (KanbanCardSummary) -> List<String> = { emptyList() },
    canMutateCard: (KanbanCardSummary) -> Boolean = { false },
    onWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit = { _, _ -> },
    onRetryMutation: (KanbanCardSummary) -> Unit = {},
    onCheckMutation: (KanbanCardSummary) -> Unit = {},
    onUndoArchive: () -> Unit = {},
    bulkState: KanbanBulkUiState = KanbanBulkUiState(),
    bulkAvailability: KanbanBulkActionsAvailability = KanbanBulkActionsAvailability.NoSelection,
    canRetryFailedBulk: Boolean = false,
    canCheckUncertainBulk: Boolean = false,
    onToggleCardSelection: (KanbanCardSummary) -> Unit = {},
    onShowBulkActions: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDismissBulkSummary: () -> Unit = {},
    onRetryFailedBulk: () -> Unit = {},
    onCheckUncertainBulk: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        when {
            state.isOffline -> KanbanConnectivityBanner(
                text = localizedString("Offline—showing previously loaded data"),
                offline = true,
                testTag = "kanban_offline_notice",
            )
            state.liveUpdatesDelayed -> KanbanConnectivityBanner(
                text = localizedString("Live updates delayed"),
                offline = false,
                testTag = "kanban_live_delayed_notice",
            )
        }
        if (state.warnings.isNotEmpty()) KanbanCompatibilityBanner(state.warnings)
        if (state.workflowCapabilityUnavailable) {
            KanbanConnectivityBanner(
                text = localizedString("Unavailable"),
                offline = false,
                testTag = "kanban_workflow_unavailable",
            )
        }
        state.boardSelectionNotice?.let { boardName ->
            KanbanBoardSelectionNotice(
                boardName = boardName,
                boards = state.boards,
                onSelectBoard = onSelectBoard,
            )
        }
        workflowState.archiveUndo?.let { undo ->
            KanbanArchiveUndoBanner(
                undo = undo,
                mutation = workflowState.mutations[undo.cardId],
                onUndo = onUndoArchive,
                onCheck = { onCheckMutation(undo.card) },
            )
        }
        if (state.refreshFailed) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(localizedString("Unavailable"), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                HermexPillButton(localizedString("Retry"), onRefresh)
            }
        }
        bulkState.phase?.let { KanbanBulkProgressBanner(it) }
        if (bulkState.phase == null) {
            bulkState.summary?.let { summary ->
                KanbanBulkSummaryBanner(
                    summary = summary,
                    canRetryFailed = canRetryFailedBulk,
                    canCheckUncertain = canCheckUncertainBulk,
                    onDismiss = onDismissBulkSummary,
                    onRetryFailed = onRetryFailedBulk,
                    onCheckUncertain = onCheckUncertainBulk,
                )
            }
        }
        if (bulkState.isSelectingCards) {
            KanbanSelectionControls(
                selectedCount = bulkState.selectedCardCount,
                availability = bulkAvailability,
                onShowBulkActions = onShowBulkActions,
                onDone = onClearSelection,
            )
        }
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).testTag("kanban_search"),
            label = { Text(localizedString("Search")) },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableStatuses.forEach { status ->
                val title = localizedString(kanbanStatusTitleKey(status))
                val count = kanbanStatusCount(state.snapshot, status, state.searchQuery)
                FilterChip(
                    selected = state.selectedStatus == status,
                    onClick = { onSelectStatus(status) },
                    label = { Text("$title  $count") },
                    leadingIcon = {
                        Box(Modifier.size(8.dp).background(kanbanStatusColor(status), CircleShape))
                    },
                    modifier = Modifier.heightIn(min = 44.dp).testTag("kanban_status_$status"),
                )
            }
        }
        HorizontalDivider()
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val cards = state.visibleCards
            if (cards.isEmpty()) {
                KanbanEmptyState(state.hasActiveFilters, onClearFilters)
            } else {
                KanbanCardList(
                    state = state,
                    cards = cards,
                    workflowState = workflowState,
                    onOpenCard = onOpenCard,
                    moveDestinations = moveDestinations,
                    canMutateCard = canMutateCard,
                    onWorkflowAction = onWorkflowAction,
                    onRetryMutation = onRetryMutation,
                    onCheckMutation = onCheckMutation,
                    isSelectingCards = bulkState.isSelectingCards,
                    selectedCardIds = bulkState.selectedCardIds,
                    onToggleCardSelection = onToggleCardSelection,
                )
            }
        }
    }
}

@Composable
private fun KanbanConnectivityBanner(text: String, offline: Boolean, testTag: String) {
    val background = if (offline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val foreground = if (offline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = text }
            .testTag(testTag),
        style = MaterialTheme.typography.bodyMedium,
        color = foreground,
    )
}

@Composable
private fun KanbanCompatibilityBanner(warnings: List<KanbanCompatibilityWarning>) {
    val unknown = warnings.filterIsInstance<KanbanCompatibilityWarning.UnsupportedStatus>().map { it.status }
    val readOnly = warnings.any {
        it == KanbanCompatibilityWarning.ReadOnly || it == KanbanCompatibilityWarning.WriteCapabilityUnavailable
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            buildString {
                if (readOnly) append(localizedString("Read-only"))
                if (unknown.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(localizedString("Unknown Status"))
                    append(": ")
                    append(unknown.joinToString())
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun KanbanBulkProgressBanner(phase: KanbanBulkActionPhase) {
    val message = localizedString(
        if (phase == KanbanBulkActionPhase.Submitting) "Updating task..." else "Checking Result",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = message }
            .testTag("kanban_bulk_progress"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun KanbanBulkSummaryBanner(
    summary: KanbanBulkActionSummary,
    canRetryFailed: Boolean,
    canCheckUncertain: Boolean,
    onDismiss: () -> Unit,
    onRetryFailed: () -> Unit,
    onCheckUncertain: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(summary) { focusRequester.requestFocus() }
    val needsAttention = summary.needsAttention.isNotEmpty()
    val resultDescription = listOf(
        "${summary.succeededCount} ${localizedString("Complete")}",
        "${summary.failedCount} ${localizedString("Failed")}",
        "${summary.uncertainCount} ${localizedString("Outcome Uncertain")}",
    ).joinToString()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (needsAttention) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .focusRequester(focusRequester)
            .focusable()
            .semantics { contentDescription = resultDescription }
            .testTag("kanban_bulk_summary"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                localizedString(if (needsAttention) "Needs Attention" else "Complete"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HermexPillButton(localizedString("Dismiss"), onDismiss)
        }
        Text(resultDescription, style = MaterialTheme.typography.bodySmall)
        summary.needsAttention.forEach { member ->
            Text(
                "${member.cardTitle} · ${localizedString(if (member.outcome == KanbanBulkMemberOutcome.Failed) "Failed" else "Outcome Uncertain")}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("kanban_bulk_member_${member.cardId}"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRetryFailed) {
                HermexPillButton(
                    localizedString("Retry Failed"),
                    onRetryFailed,
                    modifier = Modifier.testTag("kanban_bulk_retry_failed"),
                )
            }
            if (canCheckUncertain) {
                HermexPillButton(
                    localizedString("Refresh"),
                    onCheckUncertain,
                    modifier = Modifier.testTag("kanban_bulk_check_uncertain"),
                )
            }
        }
    }
}

@Composable
private fun KanbanSelectionControls(
    selectedCount: Int,
    availability: KanbanBulkActionsAvailability,
    onShowBulkActions: () -> Unit,
    onDone: () -> Unit,
) {
    val count = localizedPluralString("%lld Cards", selectedCount)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("kanban_selection_controls"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$count · ${localizedString("Selected")}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HermexPillButton(
                localizedString("Bulk Actions"),
                onShowBulkActions,
                enabled = availability == KanbanBulkActionsAvailability.Available,
                filled = true,
                modifier = Modifier.testTag("kanban_bulk_actions"),
            )
            HermexPillButton(
                localizedString("Done"),
                onDone,
                enabled = availability != KanbanBulkActionsAvailability.BoardBusy,
            )
        }
        bulkDisabledExplanation(availability)?.let { explanation ->
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun bulkDisabledExplanation(availability: KanbanBulkActionsAvailability): String? = when (availability) {
    KanbanBulkActionsAvailability.Available,
    KanbanBulkActionsAvailability.NoSelection -> null
    KanbanBulkActionsAvailability.Offline -> localizedString("Offline—showing previously loaded data")
    KanbanBulkActionsAvailability.Incompatible -> localizedString("Unavailable")
    KanbanBulkActionsAvailability.ReadOnly -> localizedString("Read-only")
    KanbanBulkActionsAvailability.Refreshing -> localizedString("The Board is refreshing.")
    KanbanBulkActionsAvailability.BoardBusy -> localizedString("Updating task...")
    KanbanBulkActionsAvailability.InvalidSelection ->
        localizedString("The selection is no longer available. Refresh the Board and select the Cards again.")
    KanbanBulkActionsAvailability.UnknownStatus -> localizedString("Unknown Status")
}

@Composable
private fun KanbanCardList(
    state: KanbanLabUiState,
    cards: List<KanbanCardSummary>,
    workflowState: KanbanWorkflowUiState,
    onOpenCard: (String) -> Unit,
    moveDestinations: (KanbanCardSummary) -> List<String>,
    canMutateCard: (KanbanCardSummary) -> Boolean,
    onWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit,
    onRetryMutation: (KanbanCardSummary) -> Unit,
    onCheckMutation: (KanbanCardSummary) -> Unit,
    isSelectingCards: Boolean,
    selectedCardIds: Set<String>,
    onToggleCardSelection: (KanbanCardSummary) -> Unit,
) {
    val groups = if (state.filters.groupByProfile) groupedKanbanCards(cards) else listOf(KanbanCardGroup(null, cards))
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("kanban_card_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { group ->
            if (state.filters.groupByProfile) {
                item("profile-${group.profile}") {
                    Text(
                        group.profile ?: localizedString("Unassigned"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 2.dp)
                            .testTag("kanban_profile_group_${group.profile ?: "unassigned"}"),
                    )
                }
            }
            items(group.cards, key = { it.cardId.orEmpty() }) { card ->
                KanbanCardRow(
                    card = card,
                    mutation = workflowState.mutations[card.cardId],
                    onOpenCard = onOpenCard,
                    destinations = moveDestinations(card),
                    actionsEnabled = canMutateCard(card) && card.cardId !in workflowState.activeCardIds,
                    onWorkflowAction = onWorkflowAction,
                    onRetryMutation = onRetryMutation,
                    onCheckMutation = onCheckMutation,
                    isSelectingCards = isSelectingCards,
                    isSelected = card.cardId in selectedCardIds,
                    onToggleSelection = onToggleCardSelection,
                )
            }
        }
    }
}

@Composable
private fun KanbanCardRow(
    card: KanbanCardSummary,
    mutation: KanbanCardMutationState?,
    onOpenCard: (String) -> Unit,
    destinations: List<String>,
    actionsEnabled: Boolean,
    onWorkflowAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit,
    onRetryMutation: (KanbanCardSummary) -> Unit,
    onCheckMutation: (KanbanCardSummary) -> Unit,
    isSelectingCards: Boolean,
    isSelected: Boolean,
    onToggleSelection: (KanbanCardSummary) -> Unit,
) {
    val title = card.title?.trim().takeUnless { it.isNullOrEmpty() } ?: localizedString("Card")
    val id = card.cardId ?: localizedString("Unknown")
    val profile = card.assignee?.trim().takeUnless { it.isNullOrEmpty() } ?: localizedString("Unassigned")
    val comments = card.commentCount ?: 0
    val links = (card.linkCounts?.parents ?: 0) + (card.linkCounts?.children ?: 0)
    val age = card.ageSeconds?.let(::kanbanAgeAbbreviation)
    val statusTitle = localizedString(kanbanStatusTitleKey(card.status.orEmpty()))
    val staleness = kanbanStaleness(card)
    val selectedLabel = localizedString("Selected")
    val stalenessColor = when (staleness) {
        KanbanStaleness.Warning -> Color(0xFFFF9800)
        KanbanStaleness.Critical -> MaterialTheme.colorScheme.error
        KanbanStaleness.None -> MaterialTheme.colorScheme.secondary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false, surfaceLevel = HermexSurfaceLevel.Raised)
            .clickable(enabled = card.cardId != null) {
                if (isSelectingCards) onToggleSelection(card) else card.cardId?.let(onOpenCard)
            }
            .semantics {
                contentDescription = listOfNotNull(
                    id,
                    title,
                    statusTitle,
                    profile,
                    card.tenant,
                    selectedLabel.takeIf { isSelected },
                ).joinToString()
                selected = isSelected
            }
            .padding(14.dp)
            .testTag("kanban_card_${card.cardId}"),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isSelectingCards) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.testTag("kanban_selection_${card.cardId}"),
                )
            }
            card.priority?.let { priority ->
                Text(
                    "P$priority",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Text(id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.weight(1f))
            age?.let { Text("◷ $it", style = MaterialTheme.typography.labelSmall, color = stalenessColor) }
            if (!isSelectingCards) KanbanCardActionsMenu(card, destinations, actionsEnabled, onWorkflowAction)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        card.body?.takeIf(String::isNotBlank)?.let { body ->
            Text(
                kanbanMarkdownPreview(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("♙ $profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            card.tenant?.takeIf(String::isNotBlank)?.let { Text("⌂ $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
            if (comments > 0) Text("◇ $comments", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            if (links > 0) Text("↔ $links", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        mutation?.let { KanbanMutationStatus(card, it, onRetryMutation, onCheckMutation) }
    }
}

@Composable
internal fun KanbanCardActionsMenu(
    card: KanbanCardSummary,
    destinations: List<String>,
    enabled: Boolean,
    onAction: (KanbanCardSummary, KanbanCardWorkflowAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HermexIconButton(
            label = localizedString("Card Actions"),
            symbol = "⋯",
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.testTag("kanban_card_actions_${card.cardId}"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            destinations.forEach { status ->
                DropdownMenuItem(
                    text = { Text("${localizedString("Move")}: ${localizedString(kanbanStatusTitleKey(status))}") },
                    onClick = {
                        expanded = false
                        onAction(card, KanbanCardWorkflowAction.Move(status))
                    },
                    modifier = Modifier.testTag("kanban_move_${card.cardId}_$status"),
                )
            }
            if (card.status == "blocked") {
                DropdownMenuItem(
                    text = { Text(localizedString("Unblock")) },
                    onClick = {
                        expanded = false
                        onAction(card, KanbanCardWorkflowAction.Unblock)
                    },
                    modifier = Modifier.testTag("kanban_unblock_${card.cardId}"),
                )
            } else if (card.status != "archived") {
                DropdownMenuItem(
                    text = { Text(localizedString("Block")) },
                    onClick = {
                        expanded = false
                        onAction(card, KanbanCardWorkflowAction.Block)
                    },
                    modifier = Modifier.testTag("kanban_block_${card.cardId}"),
                )
            }
            if (card.status !in setOf("done", "archived")) {
                DropdownMenuItem(
                    text = { Text(localizedString("Complete")) },
                    onClick = {
                        expanded = false
                        onAction(card, KanbanCardWorkflowAction.Complete)
                    },
                    modifier = Modifier.testTag("kanban_complete_${card.cardId}"),
                )
            }
            if (card.status != "archived") {
                DropdownMenuItem(
                    text = { Text(localizedString("Archive"), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expanded = false
                        onAction(card, KanbanCardWorkflowAction.Archive)
                    },
                    modifier = Modifier.testTag("kanban_archive_${card.cardId}"),
                )
            }
        }
    }
}

@Composable
internal fun KanbanMutationStatus(
    card: KanbanCardSummary,
    mutation: KanbanCardMutationState,
    onRetry: (KanbanCardSummary) -> Unit,
    onCheck: (KanbanCardSummary) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("kanban_mutation_${card.cardId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val message = when (mutation.phase) {
            KanbanCardMutationPhase.Updating -> localizedString("Updating task...")
            KanbanCardMutationPhase.CheckingResult -> localizedString("Checking Result")
            KanbanCardMutationPhase.Succeeded -> localizedString("Updated")
            KanbanCardMutationPhase.Failed -> localizedString("Update failed")
            KanbanCardMutationPhase.OutcomeUncertain -> localizedString("Outcome Uncertain")
        }
        val color = when (mutation.phase) {
            KanbanCardMutationPhase.Failed -> MaterialTheme.colorScheme.error
            KanbanCardMutationPhase.OutcomeUncertain -> MaterialTheme.colorScheme.tertiary
            KanbanCardMutationPhase.Succeeded -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        }
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = color)
        when (mutation.phase) {
            KanbanCardMutationPhase.Failed -> HermexPillButton(
                localizedString("Try Again"),
                { onRetry(card) },
                modifier = Modifier.testTag("kanban_retry_${card.cardId}"),
            )
            KanbanCardMutationPhase.OutcomeUncertain -> HermexPillButton(
                localizedString("Refresh"),
                { onCheck(card) },
                modifier = Modifier.testTag("kanban_check_${card.cardId}"),
            )
            else -> Unit
        }
    }
}

@Composable
internal fun KanbanArchiveUndoBanner(
    undo: KanbanArchiveUndo,
    mutation: KanbanCardMutationState?,
    onUndo: () -> Unit,
    onCheck: () -> Unit,
) {
    val recoveryPhase = mutation?.phase
    val status = when (recoveryPhase) {
        KanbanCardMutationPhase.OutcomeUncertain -> localizedString("Outcome Uncertain")
        KanbanCardMutationPhase.Failed -> localizedString("Update failed")
        else -> localizedString("Archived")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = "${undo.cardTitle}, $status" }
            .testTag("kanban_archive_undo"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (recoveryPhase == KanbanCardMutationPhase.OutcomeUncertain) {
            HermexPillButton(localizedString("Refresh"), onCheck, modifier = Modifier.testTag("kanban_archive_check"))
        } else {
            HermexPillButton(
                localizedString(if (recoveryPhase == KanbanCardMutationPhase.Failed) "Try Again" else "Undo"),
                onUndo,
                modifier = Modifier.testTag("kanban_archive_undo_action"),
            )
        }
    }
}

@Composable
private fun KanbanEmptyState(hasFilters: Boolean, onClearFilters: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localizedString("No Results"), style = MaterialTheme.typography.titleLarge)
            Text(
                localizedString(if (hasFilters) "Clear Filters" else "Refresh"),
                color = MaterialTheme.colorScheme.secondary,
            )
            if (hasFilters) HermexPillButton(localizedString("Clear Filters"), onClearFilters)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KanbanBulkActionsSheet(
    selectedCount: Int,
    statuses: List<String>,
    profiles: List<String>,
    canSubmit: (KanbanBulkAction) -> Boolean,
    onDismiss: () -> Unit,
    onSubmit: (KanbanBulkAction) -> Unit,
) {
    var status by rememberSaveable(statuses) {
        mutableStateOf("todo".takeIf(statuses::contains) ?: statuses.firstOrNull().orEmpty())
    }
    var profile by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityText by rememberSaveable { mutableStateOf("0") }
    val priority = priorityText.toIntOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(localizedString("Bulk Actions"), style = MaterialTheme.typography.titleLarge)
            Text(localizedPluralString("%lld Cards", selectedCount), style = MaterialTheme.typography.titleSmall)

            Text(localizedString("Change Status"), style = MaterialTheme.typography.titleSmall)
            KanbanBulkChoiceRow(
                label = localizedString("Status"),
                selected = status,
                options = statuses,
                optionLabel = { localizedString(kanbanStatusTitleKey(it)) },
                testTag = "kanban_bulk_status_picker",
                onSelect = { status = it },
            )
            HermexPillButton(
                localizedString("Change Status"),
                { onSubmit(KanbanBulkAction.ChangeStatus(status)) },
                enabled = canSubmit(KanbanBulkAction.ChangeStatus(status)),
                filled = true,
                modifier = Modifier.fillMaxWidth().testTag("kanban_bulk_change_status"),
            )

            HorizontalDivider()
            Text(localizedString("Assign Profile"), style = MaterialTheme.typography.titleSmall)
            KanbanBulkNullableChoiceRow(
                label = localizedString("Profile"),
                selected = profile,
                options = profiles,
                nullLabel = localizedString("Unassigned"),
                testTag = "kanban_bulk_profile_picker",
                onSelect = { profile = it },
            )
            HermexPillButton(
                localizedString("Assign Profile"),
                { onSubmit(KanbanBulkAction.AssignProfile(profile)) },
                enabled = canSubmit(KanbanBulkAction.AssignProfile(profile)),
                filled = true,
                modifier = Modifier.fillMaxWidth().testTag("kanban_bulk_assign_profile"),
            )

            HorizontalDivider()
            Text(localizedString("Set Priority"), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = priorityText,
                onValueChange = { input ->
                    if (input.isEmpty() || input == "-" || input.toIntOrNull() != null) priorityText = input
                },
                modifier = Modifier.fillMaxWidth().testTag("kanban_bulk_priority_input"),
                label = { Text(localizedString("Priority")) },
                supportingText = { Text(localizedString("A whole number from -100 through 100.")) },
                singleLine = true,
            )
            HermexPillButton(
                localizedString("Set Priority"),
                { priority?.let { onSubmit(KanbanBulkAction.SetPriority(it)) } },
                enabled = priority != null && canSubmit(KanbanBulkAction.SetPriority(priority)),
                filled = true,
                modifier = Modifier.fillMaxWidth().testTag("kanban_bulk_set_priority"),
            )

            HorizontalDivider()
            TextButton(
                onClick = { onSubmit(KanbanBulkAction.ArchiveCards) },
                enabled = canSubmit(KanbanBulkAction.ArchiveCards),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("kanban_bulk_archive"),
            ) {
                Text(localizedString("Archive Cards"), color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) { Text(localizedString("Cancel")) }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun KanbanBulkChoiceRow(
    label: String,
    selected: String,
    options: List<String>,
    optionLabel: @Composable (String) -> String,
    testTag: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            HermexPillButton(optionLabel(selected), { expanded = true }, modifier = Modifier.testTag(testTag))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { expanded = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanBulkNullableChoiceRow(
    label: String,
    selected: String?,
    options: List<String>,
    nullLabel: String,
    testTag: String,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            HermexPillButton(selected ?: nullLabel, { expanded = true }, modifier = Modifier.testTag(testTag))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(nullLabel) },
                    onClick = { expanded = false; onSelect(null) },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { expanded = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KanbanFiltersSheet(
    state: KanbanLabUiState,
    onDismiss: () -> Unit,
    onApply: (KanbanFilterState) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember(state.filters) { mutableStateOf(state.filters) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(localizedString("Filters"), style = MaterialTheme.typography.titleLarge)
            KanbanChoiceRow(
                label = localizedString("Profile"),
                selected = draft.profile,
                options = state.profileOptions,
                enabled = !draft.onlyMine,
                testTag = "kanban_filter_profile",
                onSelect = { draft = draft.copy(profile = it, onlyMine = false) },
            )
            ToggleRow(localizedString("Only Mine"), draft.onlyMine) { enabled ->
                draft = draft.copy(onlyMine = enabled, profile = draft.profile.takeUnless { enabled })
            }
            KanbanChoiceRow(
                label = localizedString("Tenant"),
                selected = draft.tenant,
                options = state.tenantOptions,
                testTag = "kanban_filter_tenant",
                onSelect = { draft = draft.copy(tenant = it) },
            )
            ToggleRow(localizedString("Archived"), draft.includeArchived) { draft = draft.copy(includeArchived = it) }
            ToggleRow(localizedString("Group by Profile"), draft.groupByProfile) { draft = draft.copy(groupByProfile = it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (state.hasActiveFilters) HermexPillButton(localizedString("Clear Filters"), onClear)
                HermexPillButton(localizedString("Cancel"), onDismiss)
                HermexPillButton(localizedString("Apply"), { onApply(draft) }, filled = true)
            }
        }
    }
}

@Composable
private fun KanbanChoiceRow(
    label: String,
    selected: String?,
    options: List<String>,
    enabled: Boolean = true,
    testTag: String,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Box {
            HermexPillButton(
                selected ?: localizedString("All"),
                { expanded = true },
                enabled = enabled,
                modifier = Modifier.testTag(testTag),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(localizedString("All")) },
                    onClick = { expanded = false; onSelect(null) },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { expanded = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KanbanBoardSelectionNotice(
    boardName: String,
    boards: List<KanbanBoardSummary>,
    onSelectBoard: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(14.dp)
            .testTag("kanban_board_selection_notice"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(boardName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            localizedString("This Board no longer exists. Choose another Board."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            boards.forEach { board ->
                board.slug?.trim()?.takeIf(String::isNotEmpty)?.let { slug ->
                    HermexPillButton(
                        board.displayName(),
                        { onSelectBoard(slug) },
                        modifier = Modifier.testTag("kanban_choose_board_$slug"),
                    )
                }
            }
        }
    }
}

private sealed interface KanbanBoardEditorMode {
    data object Create : KanbanBoardEditorMode
    data class Edit(val board: KanbanBoardSummary) : KanbanBoardEditorMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KanbanBoardManagementSheet(
    state: KanbanLabUiState,
    managementState: KanbanBoardManagementUiState,
    canManageBoards: Boolean,
    onDismiss: () -> Unit,
    onBrowse: (String) -> Unit,
    onCreate: (String, String, String, String, String) -> Unit,
    onEdit: (String, String, String, String, String) -> Unit,
    onArchive: (String) -> Unit,
    onMakeActive: (String) -> Unit,
    onCheckResult: () -> Unit,
    onDismissResult: () -> Unit,
) {
    var editorMode by remember { mutableStateOf<KanbanBoardEditorMode?>(null) }
    var pendingArchive by remember { mutableStateOf<KanbanBoardSummary?>(null) }
    var pendingActivation by remember { mutableStateOf<KanbanBoardSummary?>(null) }
    val editorSlug = when (val mode = editorMode) {
        KanbanBoardEditorMode.Create -> managementState.mutation?.kind?.slug
        is KanbanBoardEditorMode.Edit -> mode.board.slug?.trim()
        null -> null
    }
    LaunchedEffect(managementState.mutation) {
        val mutation = managementState.mutation
        if (
            editorMode != null &&
            mutation?.phase == KanbanCardMutationPhase.Succeeded &&
            mutation.kind.slug == editorSlug
        ) {
            editorMode = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!managementState.blocksWrites) onDismiss() },
        modifier = Modifier.testTag("kanban_board_management_sheet"),
    ) {
        if (editorMode != null) {
            KanbanBoardEditorContent(
                mode = checkNotNull(editorMode),
                managementState = managementState,
                canSave = canManageBoards,
                onCancel = { if (!managementState.blocksWrites) editorMode = null },
                onCreate = onCreate,
                onEdit = onEdit,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localizedString("Manage"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    HermexPillButton(
                        localizedString("Create"),
                        {
                            onDismissResult()
                            editorMode = KanbanBoardEditorMode.Create
                        },
                        enabled = canManageBoards,
                        filled = true,
                        modifier = Modifier.testTag("kanban_create_board"),
                    )
                    Spacer(Modifier.size(8.dp))
                    HermexPillButton(
                        localizedString("Done"),
                        onDismiss,
                        enabled = !managementState.blocksWrites,
                        modifier = Modifier.testTag("kanban_done_managing_boards"),
                    )
                }
                managementState.mutation?.let { mutation ->
                    KanbanBoardMutationStatus(mutation, onCheckResult, onDismissResult)
                }
                if (managementState.capabilityUnavailable) {
                    Text(
                        localizedString("Unavailable"),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("kanban_board_management_unavailable"),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.boards, key = { it.slug.orEmpty() }) { board ->
                        KanbanManagedBoardRow(
                            board = board,
                            isBrowsing = board.slug?.trim() == state.selectedBoardSlug,
                            isActive = board.slug?.trim() == state.sharedActiveBoardSlug,
                            mutationsEnabled = canManageBoards,
                            onBrowse = onBrowse,
                            onEdit = {
                                onDismissResult()
                                editorMode = KanbanBoardEditorMode.Edit(board)
                            },
                            onArchive = { pendingArchive = board },
                            onMakeActive = { pendingActivation = board },
                        )
                    }
                }
                Text(
                    localizedString("Browsing a Board stays local to Hermex. Making a Board active changes shared server state."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.size(10.dp))
            }
        }
    }

    pendingArchive?.let { board ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text(localizedString("Archive")) },
            text = { Text(localizedString("Hermex cannot restore an archived Board in-app.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingArchive = null
                        board.slug?.let(onArchive)
                    },
                    modifier = Modifier.testTag("kanban_confirm_archive_board"),
                ) { Text(localizedString("Archive"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingArchive = null }) { Text(localizedString("Cancel")) } },
        )
    }
    pendingActivation?.let { board ->
        AlertDialog(
            onDismissRequest = { pendingActivation = null },
            title = { Text(localizedString("Make Active Board")) },
            text = { Text(localizedString("Making this Board active changes shared server state for other Hermes clients.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingActivation = null
                        board.slug?.let(onMakeActive)
                    },
                    modifier = Modifier.testTag("kanban_confirm_make_active"),
                ) { Text(localizedString("Make Active Board")) }
            },
            dismissButton = { TextButton(onClick = { pendingActivation = null }) { Text(localizedString("Cancel")) } },
        )
    }
}

@Composable
private fun KanbanBoardMutationStatus(
    mutation: KanbanBoardMutationState,
    onCheckResult: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val action = when (mutation.kind) {
        is KanbanBoardMutationKind.Create -> localizedString("Create")
        is KanbanBoardMutationKind.Edit -> localizedString("Edit")
        is KanbanBoardMutationKind.Archive -> localizedString("Archive")
        is KanbanBoardMutationKind.MakeActive -> localizedString("Make Active Board")
    }
    val phase = when (mutation.phase) {
        KanbanCardMutationPhase.Updating -> localizedString("Updating Board...")
        KanbanCardMutationPhase.CheckingResult -> localizedString("Checking Result")
        KanbanCardMutationPhase.Succeeded -> localizedString("Done")
        KanbanCardMutationPhase.Failed -> localizedString("Failed")
        KanbanCardMutationPhase.OutcomeUncertain -> localizedString("Outcome Uncertain")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("kanban_board_mutation_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (mutation.phase in setOf(KanbanCardMutationPhase.Updating, KanbanCardMutationPhase.CheckingResult)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(action, fontWeight = FontWeight.SemiBold)
            Text(phase, style = MaterialTheme.typography.bodySmall)
        }
        if (mutation.phase == KanbanCardMutationPhase.OutcomeUncertain) {
            HermexPillButton(
                localizedString("Check Result"),
                onCheckResult,
                modifier = Modifier.testTag("kanban_check_board_result"),
            )
        } else if (mutation.phase !in setOf(KanbanCardMutationPhase.Updating, KanbanCardMutationPhase.CheckingResult)) {
            HermexPillButton(localizedString("Dismiss"), onDismissResult)
        }
    }
}

@Composable
private fun KanbanManagedBoardRow(
    board: KanbanBoardSummary,
    isBrowsing: Boolean,
    isActive: Boolean,
    mutationsEnabled: Boolean,
    onBrowse: (String) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onMakeActive: () -> Unit,
) {
    val slug = board.slug?.trim().orEmpty()
    var actionsExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false, surfaceLevel = HermexSurfaceLevel.Raised)
            .padding(12.dp)
            .testTag("kanban_managed_board_$slug"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = slug.isNotEmpty() && !isBrowsing) { onBrowse(slug) }
                .testTag("kanban_browse_board_$slug"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(board.icon?.takeIf(String::isNotBlank) ?: "▣")
                Text(board.displayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (slug.isNotEmpty()) Text(slug, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isBrowsing) {
                    Text(
                        "◉ ${localizedString("Browsing")}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("kanban_board_browsing_$slug"),
                    )
                }
                if (isActive) {
                    Text(
                        "✓ ${localizedString("Active")}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("kanban_board_active_$slug"),
                    )
                }
            }
            board.description?.trim()?.takeIf(String::isNotEmpty)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text(localizedPluralString("%lld Cards", board.total ?: 0), style = MaterialTheme.typography.labelSmall)
        }
        Box {
            HermexIconButton(
                localizedString("Board Actions"),
                "⋯",
                { actionsExpanded = true },
                enabled = mutationsEnabled && slug.isNotEmpty(),
                modifier = Modifier.testTag("kanban_board_actions_$slug"),
            )
            DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(localizedString("Edit")) },
                    onClick = { actionsExpanded = false; onEdit() },
                )
                if (!isActive) {
                    DropdownMenuItem(
                        text = { Text(localizedString("Make Active Board")) },
                        onClick = { actionsExpanded = false; onMakeActive() },
                    )
                }
                if (slug != "default") {
                    DropdownMenuItem(
                        text = { Text(localizedString("Archive"), color = MaterialTheme.colorScheme.error) },
                        onClick = { actionsExpanded = false; onArchive() },
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanBoardEditorContent(
    mode: KanbanBoardEditorMode,
    managementState: KanbanBoardManagementUiState,
    canSave: Boolean,
    onCancel: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit,
    onEdit: (String, String, String, String, String) -> Unit,
) {
    val editingBoard = (mode as? KanbanBoardEditorMode.Edit)?.board
    var slug by remember(mode) { mutableStateOf(editingBoard?.slug.orEmpty()) }
    var name by remember(mode) { mutableStateOf(editingBoard?.name.orEmpty()) }
    var description by remember(mode) { mutableStateOf(editingBoard?.description.orEmpty()) }
    var icon by remember(mode) { mutableStateOf(editingBoard?.icon.orEmpty()) }
    var color by remember(mode) { mutableStateOf(editingBoard?.color.orEmpty()) }
    var showSlugError by remember(mode) { mutableStateOf(false) }
    var showNameError by remember(mode) { mutableStateOf(false) }
    val isEditing = editingBoard != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(localizedString(if (isEditing) "Edit" else "Create"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            HermexPillButton(localizedString("Cancel"), onCancel, enabled = !managementState.blocksWrites)
            Spacer(Modifier.size(8.dp))
            HermexPillButton(
                localizedString("Save"),
                {
                    val trimmedSlug = slug.trim()
                    val trimmedName = name.trim()
                    showSlugError = trimmedSlug.isEmpty()
                    showNameError = trimmedName.isEmpty()
                    if (!showSlugError && !showNameError) {
                        if (isEditing) {
                            onEdit(trimmedSlug, trimmedName, description, icon, color)
                        } else {
                            onCreate(trimmedSlug, trimmedName, description, icon, color)
                        }
                    }
                },
                enabled = canSave,
                filled = true,
                modifier = Modifier.testTag("kanban_save_board"),
            )
        }
        OutlinedTextField(
            value = slug,
            onValueChange = { slug = it; showSlugError = false },
            modifier = Modifier.fillMaxWidth().testTag("kanban_board_slug"),
            label = { Text(localizedString("Slug")) },
            enabled = !isEditing && !managementState.blocksWrites,
            isError = showSlugError,
            supportingText = { if (showSlugError) Text(localizedString("Required")) },
            singleLine = true,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; showNameError = false },
            modifier = Modifier.fillMaxWidth().testTag("kanban_board_name"),
            label = { Text(localizedString("Name")) },
            enabled = !managementState.blocksWrites,
            isError = showNameError,
            supportingText = { if (showNameError) Text(localizedString("Required")) },
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth().testTag("kanban_board_description"),
            label = { Text(localizedString("Description")) },
            enabled = !managementState.blocksWrites,
            minLines = 2,
            maxLines = 5,
        )
        OutlinedTextField(
            value = icon,
            onValueChange = { icon = it },
            modifier = Modifier.fillMaxWidth().testTag("kanban_board_icon"),
            label = { Text(localizedString("Icon")) },
            enabled = !managementState.blocksWrites,
            singleLine = true,
        )
        OutlinedTextField(
            value = color,
            onValueChange = { color = it },
            modifier = Modifier.fillMaxWidth().testTag("kanban_board_color"),
            label = { Text(localizedString("Color")) },
            enabled = !managementState.blocksWrites,
            singleLine = true,
        )
        Text(
            localizedString("Creating a Board does not make it active."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (managementState.mutation?.kind?.slug == slug.trim()) {
            managementState.mutation.let { mutation ->
                if (mutation.phase in setOf(KanbanCardMutationPhase.Failed, KanbanCardMutationPhase.OutcomeUncertain)) {
                    Text(
                        localizedString(if (mutation.phase == KanbanCardMutationPhase.Failed) "Failed" else "Outcome Uncertain"),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(localizedString("Refresh the Board before trying again."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun KanbanBoardSummary.displayName(): String =
    name?.trim().takeUnless { it.isNullOrEmpty() } ?: slug?.trim().takeUnless { it.isNullOrEmpty() } ?: localizedString("Board")

private fun kanbanStatusColor(status: String): Color = when (status.lowercase()) {
    "triage" -> Color(0xFF8E8E93)
    "todo" -> Color(0xFF5AC8FA)
    "blocked" -> Color(0xFFFF3B30)
    "ready" -> Color(0xFF34C759)
    "running" -> Color(0xFFFF9500)
    "done" -> Color(0xFF5856D6)
    "archived" -> Color(0xFF636366)
    else -> Color(0xFFAF52DE)
}
