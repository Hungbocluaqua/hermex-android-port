package com.uzairansar.hermex.ui.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
    var showsFilters by rememberSaveable { mutableStateOf(false) }
    var cardNavigationStack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSessionId by rememberSaveable { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

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
            allowsMutation = state.canMutateCards,
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
            parentAllowsWrites = state.canMutateCards,
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
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            KanbanTopBar(
                state = state,
                onBack = onBack,
                onRefresh = viewModel::load,
                onSelectBoard = viewModel::selectBoard,
                onShowFilters = { showsFilters = true },
                canCreateCard = state.canMutateCards,
                onCreateCard = {
                    editorSessionId += 1
                    editorCardId = null
                    editorOpen = true
                },
            )
            when (state.availability) {
                KanbanAvailability.Loading -> KanbanLoading()
                KanbanAvailability.Content -> KanbanBoardContent(
                    state = state,
                    onRefresh = viewModel::load,
                    onSearch = viewModel::setSearchQuery,
                    onSelectStatus = viewModel::selectStatus,
                    onClearFilters = viewModel::clearFilters,
                    onOpenCard = { cardNavigationStack = listOf(it) },
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
}

@Composable
private fun KanbanTopBar(
    state: KanbanLabUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectBoard: (String) -> Unit,
    onShowFilters: () -> Unit,
    canCreateCard: Boolean,
    onCreateCard: () -> Unit,
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
                enabled = state.boards.isNotEmpty() && !state.isRefreshing,
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
            }
        }
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
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectStatus: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpenCard: (String) -> Unit = {},
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
                KanbanCardList(state, cards, onOpenCard)
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
private fun KanbanCardList(
    state: KanbanLabUiState,
    cards: List<KanbanCardSummary>,
    onOpenCard: (String) -> Unit,
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
                KanbanCardRow(card, onOpenCard)
            }
        }
    }
}

@Composable
private fun KanbanCardRow(card: KanbanCardSummary, onOpenCard: (String) -> Unit) {
    val title = card.title?.trim().takeUnless { it.isNullOrEmpty() } ?: localizedString("Card")
    val id = card.cardId ?: localizedString("Unknown")
    val profile = card.assignee?.trim().takeUnless { it.isNullOrEmpty() } ?: localizedString("Unassigned")
    val comments = card.commentCount ?: 0
    val links = (card.linkCounts?.parents ?: 0) + (card.linkCounts?.children ?: 0)
    val age = card.ageSeconds?.let(::kanbanAgeAbbreviation)
    val statusTitle = localizedString(kanbanStatusTitleKey(card.status.orEmpty()))
    val staleness = kanbanStaleness(card)
    val stalenessColor = when (staleness) {
        KanbanStaleness.Warning -> Color(0xFFFF9800)
        KanbanStaleness.Critical -> MaterialTheme.colorScheme.error
        KanbanStaleness.None -> MaterialTheme.colorScheme.secondary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(shape = HermexCardShape, castsShadow = false, surfaceLevel = HermexSurfaceLevel.Raised)
            .clickable(enabled = card.cardId != null) { card.cardId?.let(onOpenCard) }
            .semantics {
                contentDescription = listOfNotNull(
                    id,
                    title,
                    statusTitle,
                    profile,
                    card.tenant,
                ).joinToString()
            }
            .padding(14.dp)
            .testTag("kanban_card_${card.cardId}"),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
