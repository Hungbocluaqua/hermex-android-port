package com.uzairansar.hermex.ui.sessions

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.AppContainer
import com.uzairansar.hermex.R
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionExportFile
import com.uzairansar.hermex.core.model.SessionExportFormat
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.ui.ShortcutDestination
import com.uzairansar.hermex.ui.ProfileShortcutPublisher
import com.uzairansar.hermex.ui.SavedStatePolicy
import com.uzairansar.hermex.ui.theme.HermesHeaderLogo
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexColors
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexColorFromHex
import com.uzairansar.hermex.ui.theme.hermexBackgroundColor
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContainerColor
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContentColor
import com.uzairansar.hermex.ui.theme.primaryActionTintApplies
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.localization.localizedStringFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListRoute(
    authState: AuthState,
    container: AppContainer,
    shortcutAction: String? = null,
    shortcutNonce: String? = null,
    shortcutProfile: String? = null,
    initialArchived: Boolean = false,
    selectedSessionId: String? = null,
    onOpenChat: (String) -> Unit,
    onOpenVoiceChat: (String) -> Unit,
    onOpenSharedDraft: (String) -> Unit,
    onOpenPanels: () -> Unit,
    onOpenPanel: (String) -> Unit = { onOpenPanels() },
    onOpenKanban: () -> Unit,
    onOpenSettings: () -> Unit,
    onNeedsOnboarding: () -> Unit,
) {
    val loggedIn = authState as? AuthState.LoggedIn
    if (loggedIn == null) {
        LaunchedEffect(Unit) { onNeedsOnboarding() }
        return
    }
    val viewModel: SessionListViewModel = viewModel(
        key = "sessions:${loggedIn.server}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SessionListViewModel(
                    repository = container.sessionRepository(loggedIn.server),
                    panelsRepository = container.panelsRepository(loggedIn.server),
                    localSettingsRepository = container.localSettingsRepository,
                    serverId = loggedIn.server.toString(),
                ) as T
            }

            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SessionListViewModel(
                    repository = container.sessionRepository(loggedIn.server),
                    panelsRepository = container.panelsRepository(loggedIn.server),
                    localSettingsRepository = container.localSettingsRepository,
                    serverId = loggedIn.server.toString(),
                    savedStateHandle = extras.createSavedStateHandle(),
                ) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val headerLogoColorHex = loggedIn.account.headerLogoColorHex
    val shortcutKey = listOfNotNull(shortcutAction, shortcutNonce).joinToString(":")
    var shortcutConsumed by rememberSaveable(shortcutKey) { mutableStateOf(false) }
    var pendingShortcutAction by rememberSaveable(shortcutKey) { mutableStateOf<String?>(null) }
    var expandedActionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    var projectsExpanded by rememberSaveable { mutableStateOf(false) }
    var scheduledSessionsExpanded by rememberSaveable { mutableStateOf(false) }
    var viewingAllScheduledSessions by rememberSaveable { mutableStateOf(false) }
    var scheduledSessionsQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var isCreatingProject by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val latestContext by rememberUpdatedState(context)
    val latestOnOpenChat by rememberUpdatedState(onOpenChat)
    val latestOnOpenVoiceChat by rememberUpdatedState(onOpenVoiceChat)
    val latestOnOpenSharedDraft by rememberUpdatedState(onOpenSharedDraft)
    val profileShortcutPublisher = remember(context) { ProfileShortcutPublisher(context) }
    val configuration = LocalConfiguration.current
    val usesCompactFloatingAction = configuration.fontScale >= 1.3f ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val primaryActionTintColor = remember(state.tintPrimaryActionsWithThemeColor, headerLogoColorHex, state.isMutating) {
        if (primaryActionTintApplies(state.tintPrimaryActionsWithThemeColor, !state.isMutating)) {
            hermexColorFromHex(headerLogoColorHex)
        } else {
            null
        }
    }

    BackHandler(enabled = searchExpanded) {
        viewModel.clearSearch()
        searchExpanded = false
    }
    BackHandler(enabled = viewingAllScheduledSessions) {
        viewingAllScheduledSessions = false
        scheduledSessionsQuery = ""
    }

    // Navigation keeps this ViewModel alive while chat is on top. Refresh each time
    // the sessions destination re-enters composition so newly changed chats appear.
    LaunchedEffect(viewModel) {
        viewModel.refreshAllOnVisible()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionListEvent.OpenSession -> when (event.destination) {
                    SessionOpenDestination.Chat -> latestOnOpenChat(event.sessionId)
                    SessionOpenDestination.VoiceChat -> latestOnOpenVoiceChat(event.sessionId)
                    SessionOpenDestination.SharedDraft -> latestOnOpenSharedDraft(event.sessionId)
                }
                is SessionListEvent.ExportReady -> {
                    shareSessionExport(latestContext, event.file)
                        .onFailure { error ->
                            viewModel.reportActionError(error.message ?: "Could not share session export.")
                        }
                }
            }
        }
    }

    LaunchedEffect(initialArchived) {
        if (initialArchived && !state.showArchived) {
            viewModel.toggleArchived()
        }
    }
    LaunchedEffect(state.profileOptions, state.isLoadingProfiles) {
        if (!state.isLoadingProfiles) {
            profileShortcutPublisher.publish(state.profileOptions)
        }
    }
    val copySessionTitle: (SessionSummary) -> Unit = { session ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hermex session title", session.displayTitle))
    }
    val copySessionDeepLink: (SessionSummary) -> Unit = { session ->
        ShortcutDestination.sessionUri(session.sessionId)?.let { deepLink ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Hermex session deeplink", deepLink))
        }
    }
    val sessionRowContent: @Composable (SessionSummary, String) -> Unit = { session, rowKey ->
        SessionRow(
            session = session,
            projects = state.projects,
            isMutating = state.isMutating,
            isViewingCachedData = state.isViewingCachedData,
            showsMessageCount = state.sessionRowDisplaySettings.showMessageCount,
            showsWorkspace = state.sessionRowDisplaySettings.showWorkspace,
            selected = selectedSessionId != null && selectedSessionId == session.sessionId,
            actionsExpanded = expandedActionsFor == rowKey,
            onOpen = { session.sessionId?.let(onOpenChat) },
            onToggleActions = {
                expandedActionsFor = if (expandedActionsFor == rowKey) null else rowKey
            },
            onPin = { viewModel.togglePin(session) },
            onArchive = { viewModel.toggleArchive(session) },
            onCopyTitle = { copySessionTitle(session) },
            onCopyDeepLink = { copySessionDeepLink(session) },
            onRename = { viewModel.requestRename(session) },
            onDelete = { viewModel.requestDelete(session) },
            onBranch = { viewModel.requestBranch(session) },
            onDuplicate = { viewModel.duplicate(session) },
            onMove = { projectId -> viewModel.move(session, projectId) },
            onExport = { format -> viewModel.exportSession(session, format) },
        )
    }

    LaunchedEffect(shortcutKey, shortcutAction, shortcutProfile, shortcutConsumed) {
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.ShareAction) {
            shortcutConsumed = true
            if (container.sharedDraftStore.hasPendingDraft()) {
                viewModel.createSession(destination = SessionOpenDestination.SharedDraft)
            }
        } else if (!shortcutConsumed && shortcutAction != null && pendingShortcutAction == null) {
            pendingShortcutAction = shortcutAction
        }
    }

    if (viewingAllScheduledSessions) {
        ScheduledSessionsScreen(
            state = state,
            query = scheduledSessionsQuery,
            onQueryChange = {
                scheduledSessionsQuery = SavedStatePolicy.boundedInput(
                    it,
                    SavedStatePolicy.MaximumSearchCharacters,
                )
            },
            onBack = {
                viewingAllScheduledSessions = false
                scheduledSessionsQuery = ""
            },
            onRefresh = viewModel::refreshAll,
            sessionRowContent = sessionRowContent,
        )
    } else Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refreshAll,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .testTag("session_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 18.dp,
                    bottom = 108.dp,
                ),
            ) {
                item {
                    SessionsHeader(
                        avatarInitials = loggedIn.account.initials.ifBlank { "MU" },
                        avatarColorHex = headerLogoColorHex,
                        query = state.searchQuery,
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = { searchExpanded = it },
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = viewModel::searchNow,
                        onClear = viewModel::clearSearch,
                        onSettings = onOpenSettings,
                    )
                }
                if (state.showArchived) {
                    item {
                        ArchivedModeRow(
                            count = state.visibleSessions.size,
                            onClose = viewModel::toggleArchived,
                        )
                    }
                } else {
                    if (!searchExpanded) {
                        item {
                            UtilityRows(
                                settings = state.mainPageDisplaySettings,
                                onOpenPanel = onOpenPanel,
                                onOpenKanban = onOpenKanban,
                            )
                        }
                        if (state.mainPageDisplaySettings.showActiveProfile && !state.isSingleProfileMode) {
                            item {
                                ActiveProfileSection(
                                    state = state,
                                    expanded = profilesExpanded,
                                    onToggleExpanded = { profilesExpanded = !profilesExpanded },
                                    onSelectProfile = viewModel::switchProfile,
                                )
                            }
                        }
                        if (state.mainPageDisplaySettings.showProjects) {
                            item {
                                ProjectSection(
                                projects = state.projects,
                                sessions = state.sessions,
                                selectedProjectId = state.selectedProjectId,
                                expanded = projectsExpanded,
                                isMutating = state.isMutating,
                                isViewingCachedData = state.isViewingCachedData,
                                onToggleExpanded = { projectsExpanded = !projectsExpanded },
                                onSelectProject = viewModel::selectProject,
                                onAddProject = {
                                    viewModel.beginCreateProject()
                                    isCreatingProject = true
                                },
                                onRenameProject = viewModel::requestRenameProject,
                                onDeleteProject = viewModel::requestDeleteProject,
                                )
                            }
                        }
                    }
                }
                item {
                    StatusStack(state = state)
                }
                if (state.isLoading && state.sessions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                } else {
                    val scheduledGroups = state.scheduledSessionGroups
                    val searchIsActive = state.searchQuery.trim().isNotEmpty()
                    val showsScheduledDisclosure = !state.showArchived &&
                        scheduledGroups.showsDisclosure(searchIsActive)
                    val scheduledIsExpanded = searchIsActive || scheduledSessionsExpanded
                    val scheduledRows = if (searchIsActive) {
                        scheduledGroups.scheduled
                    } else {
                        scheduledGroups.scheduledPreview
                    }
                    if (showsScheduledDisclosure) {
                        item(key = "scheduled_sessions_disclosure") {
                            ScheduledSessionsDisclosureHeader(
                                count = scheduledGroups.totalScheduledCount,
                                expanded = scheduledIsExpanded,
                                searchIsActive = searchIsActive,
                                onToggle = { scheduledSessionsExpanded = !scheduledSessionsExpanded },
                            )
                        }
                        if (scheduledIsExpanded) {
                            itemsIndexed(
                                items = scheduledRows,
                                key = { index, session -> "scheduled:${session.stableId}:$index" },
                            ) { index, session ->
                                sessionRowContent(session, "scheduled:${session.stableId}:$index")
                            }
                            if (!searchIsActive && scheduledGroups.hasAdditionalScheduledSessions) {
                                item(key = "scheduled_sessions_view_all") {
                                    ScheduledSessionsViewAllRow(
                                        onClick = {
                                            scheduledSessionsQuery = ""
                                            viewingAllScheduledSessions = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (!state.showArchived && !searchExpanded) {
                        item(key = "sessions_heading") {
                            Text(
                                stringResource(R.string.sessions_title),
                                modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 10.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    val ordinarySessions = if (state.showArchived) state.visibleSessions else scheduledGroups.ordinary
                    if (ordinarySessions.isEmpty()) {
                        if (!showsScheduledDisclosure || scheduledGroups.scheduled.isEmpty()) {
                            item { EmptySessionsRow() }
                        }
                    } else {
                        itemsIndexed(
                            items = ordinarySessions,
                            key = { index, session -> "ordinary:${session.stableId}:$index" },
                        ) { index, session ->
                            sessionRowContent(session, "ordinary:${session.stableId}:$index")
                        }
                    }
                }
            }
        }

        if (!searchExpanded) {
            NewChatFloatingButton(
                onClick = { viewModel.createSession() },
                enabled = !state.isMutating,
                tintColor = primaryActionTintColor,
                compact = usesCompactFloatingAction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 22.dp),
            )
        }
    }

    SessionDialogs(state, viewModel, onOpenChat)
    pendingShortcutAction?.let { action ->
        val isVoice = action == ShortcutDestination.NewVoiceSessionAction
        val isProfile = action == ShortcutDestination.NewProfileSessionAction
        AlertDialog(
            onDismissRequest = {
                pendingShortcutAction = null
                shortcutConsumed = true
            },
            title = { Text(localizedString(if (isVoice) "Voice input" else "New Chat")) },
            text = {
                Text(localizedString(if (isVoice) "Voice input" else "New Chat"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingShortcutAction = null
                        shortcutConsumed = true
                        when {
                            isVoice -> viewModel.createSession(destination = SessionOpenDestination.VoiceChat)
                            isProfile -> viewModel.createSession(profile = shortcutProfile)
                            else -> viewModel.createSession()
                        }
                    },
                ) { Text(localizedString("Continue")) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingShortcutAction = null
                        shortcutConsumed = true
                    },
                ) { Text(localizedString("Cancel")) }
            },
        )
    }
    if (isCreatingProject) {
        AlertDialog(
            onDismissRequest = {
                isCreatingProject = false
                viewModel.dismissCreateProject()
            },
            title = { Text(localizedString("New Project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    OutlinedTextField(
                        value = state.newProjectName,
                        onValueChange = viewModel::updateNewProjectName,
                        label = { Text(localizedString("Project name")) },
                        singleLine = true,
                    )
                    ProjectColorPicker(
                        selectedColorHex = state.newProjectColor,
                        onColorSelected = viewModel::updateNewProjectColor,
                        enabled = !state.isMutating,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProject()
                        isCreatingProject = false
                    },
                    enabled = state.newProjectName.isNotBlank() && !state.isMutating,
                ) {
                    Text(localizedString("Create"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isCreatingProject = false
                        viewModel.dismissCreateProject()
                    },
                ) {
                    Text(localizedString("Cancel"))
                }
            },
        )
    }
}

@Composable
private fun ScheduledSessionsDisclosureHeader(
    count: Int,
    expanded: Boolean,
    searchIsActive: Boolean,
    onToggle: () -> Unit,
) {
    val accessibilityLabel = localizedString(
        when {
            searchIsActive -> "Scheduled sessions"
            expanded -> "Collapse scheduled sessions"
            else -> "Expand scheduled sessions"
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (searchIsActive) 16.dp else 12.dp)
            .clip(HermexCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(enabled = !searchIsActive, onClick = onToggle)
            .semantics { contentDescription = accessibilityLabel }
            .testTag("scheduled_sessions_disclosure")
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lucide_calendar_clock),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(20.dp),
        )
        Text(
            localizedString("Scheduled sessions"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        if (!searchIsActive) {
            Image(
                painter = painterResource(R.drawable.ic_hermex_chevron_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
    }
}

@Composable
private fun ScheduledSessionsViewAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("scheduled_sessions_view_all")
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_hermex_search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(18.dp),
        )
        Text(
            localizedString("View all"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_hermex_chevron_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledSessionsScreen(
    state: SessionListUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    sessionRowContent: @Composable (SessionSummary, String) -> Unit,
) {
    val sessions = state.scheduledSessions(query)
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 18.dp,
                bottom = 36.dp,
            ),
        ) {
            item(key = "scheduled_sessions_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HermexIconButton(
                        label = localizedString("Back"),
                        symbol = "<",
                        onClick = onBack,
                        tonalContainerColor = Color.Transparent,
                    )
                    Text(
                        localizedString("Scheduled sessions"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        sessions.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "scheduled_sessions_search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_sessions)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 10.dp)
                        .testTag("scheduled_sessions_search_field"),
                )
            }
            if (state.isLoading && sessions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (sessions.isEmpty()) {
                item { EmptySessionsRow() }
            } else {
                itemsIndexed(
                    items = sessions,
                    key = { index, session -> "scheduled-all:${session.stableId}:$index" },
                ) { index, session ->
                    sessionRowContent(session, "scheduled-all:${session.stableId}:$index")
                }
            }
        }
    }
}

@Composable
private fun SessionsHeader(
    avatarInitials: String,
    avatarColorHex: String,
    query: String,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (searchExpanded) 0.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!searchExpanded) {
            HermesHeaderLogo(
                modifier = Modifier
                    .width(160.dp)
                    .height(46.dp),
                tint = hermexColorFromHex(avatarColorHex) ?: HermexColors.HeaderGold,
            )
            Spacer(Modifier.weight(1f))
        }
        SessionSearchChrome(
            query = query,
            avatarInitials = avatarInitials,
            avatarColorHex = avatarColorHex,
            expanded = searchExpanded,
            onExpandedChange = onSearchExpandedChange,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onSettings = onSettings,
            modifier = if (searchExpanded) Modifier.weight(1f) else Modifier,
        )
    }
}

@Composable
private fun SessionSearchChrome(
    query: String,
    avatarInitials: String,
    avatarColorHex: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(96.dp))
            .hermexGlass(shape = CircleShape, castsShadow = false)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isBlank()) {
                    Text(
                        stringResource(R.string.search_sessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("session_search_field"),
                )
            }
            if (query.isNotBlank()) {
                HermexIconButton(localizedString("Clear"), "x", onClear, tonalContainerColor = Color.Transparent)
            }
        } else {
            HermexIconButton(
                label = stringResource(R.string.search_sessions),
                symbol = "⌕",
                onClick = { onExpandedChange(true) },
                tonalContainerColor = Color.Transparent,
            )
        }
        HermexIconButton(
            label = if (expanded) stringResource(R.string.close_search) else stringResource(R.string.settings_title),
            symbol = if (expanded) "x" else avatarInitials,
            onClick = {
                if (expanded) {
                    keyboardController?.hide()
                    onClear()
                    onExpandedChange(false)
                } else {
                    onSettings()
                }
            },
            filled = !expanded,
            filledContainerColor = hermexColorFromHex(avatarColorHex) ?: HermexColors.HeaderGold,
            filledContentColor = Color.Black,
            tonalContainerColor = Color.Transparent,
        )
    }
}

@Composable
private fun NewChatFloatingButton(
    onClick: () -> Unit,
    enabled: Boolean,
    tintColor: Color?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(58.dp)
            .then(if (compact) Modifier.width(58.dp) else Modifier)
            .clip(CircleShape)
            .background(hermexPrimaryActionContainerColor(enabled, tintColor)),
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = hermexPrimaryActionContentColor(enabled, tintColor)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (compact) 0.dp else 22.dp,
            vertical = 0.dp,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_hermex_square_and_pencil),
                contentDescription = if (compact) stringResource(R.string.new_chat) else null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(hermexPrimaryActionContentColor(enabled, tintColor)),
            )
            if (!compact) {
                Text(localizedString("Chat"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun UtilityRows(
    settings: com.uzairansar.hermex.data.preferences.MainPageDisplaySettings,
    onOpenPanel: (String) -> Unit,
    onOpenKanban: () -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val rows = buildList {
            if (settings.showTasks) add(UtilityDestination(R.string.nav_tasks, R.drawable.ic_lucide_calendar_clock) { onOpenPanel("tasks") })
            if (settings.showKanban) add(UtilityDestination(R.string.nav_kanban, R.drawable.ic_lucide_columns_3, onOpenKanban))
            if (settings.showSkills) add(UtilityDestination(R.string.nav_skills, R.drawable.ic_lucide_hammer) { onOpenPanel("skills") })
            if (settings.showMemory) add(UtilityDestination(R.string.nav_memory, R.drawable.ic_lucide_brain) { onOpenPanel("memory") })
            if (settings.showInsights) add(UtilityDestination(R.string.nav_insights, R.drawable.ic_lucide_chart_column_increasing) { onOpenPanel("insights") })
        }
        if (isLandscape) {
            rows.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { destination ->
                        UtilityRow(stringResource(destination.label), destination.icon, destination.onOpen, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            rows.forEach { destination ->
                UtilityRow(stringResource(destination.label), destination.icon, destination.onOpen)
            }
        }
    }
}

private data class UtilityDestination(
    @param:androidx.annotation.StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val onOpen: () -> Unit,
)

@Composable
private fun ActiveProfileSection(
    state: SessionListUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectProfile: (ProfileSummary) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HermexCardShape)
                .clickable(onClick = onToggleExpanded)
                .heightIn(min = 44.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidebarUtilityIcon(
                iconRes = R.drawable.ic_lucide_user_round_cog,
                tint = if (state.profileError == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary,
            )
            Text(
                stringResource(R.string.nav_active_profile),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Image(
                painter = painterResource(if (expanded) R.drawable.ic_hermex_chevron_down else R.drawable.ic_hermex_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
            )
        }

        if (expanded) {
            when {
                state.isLoadingProfiles && state.profileOptions.isEmpty() -> ProfileStatusRow("Loading profiles...")
                state.profileError != null && state.profileOptions.isEmpty() -> ProfileStatusRow("Could not load profiles")
                state.profileOptions.isEmpty() -> ProfileStatusRow("No profiles")
                else -> state.profileOptions.forEach { profile ->
                    ProfileOptionRow(
                        profile = profile,
                        selected = profile.name == state.activeProfileName,
                        enabled = !state.isSwitchingProfile && !state.isViewingCachedData,
                        onClick = { onSelectProfile(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatusRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 72.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProfileOptionRow(
    profile: ProfileSummary,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, end = 24.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .semantics { this.selected = selected }
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
            .testTag("profile_option_${profile.name.orEmpty()}"),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SidebarUtilityIcon(
            iconRes = R.drawable.ic_lucide_user_round_cog,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                profile.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile.modelProviderText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            SelectedSubrowIndicator()
        }
    }
}

@Composable
private fun SelectedSubrowIndicator(contentDescription: String? = null) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_hermex_check),
            contentDescription = contentDescription,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
        )
    }
}

@Composable
private fun UtilityRow(
    title: String,
    @DrawableRes iconRes: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HermexCardShape)
            .clickable(onClick = onOpen)
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SidebarUtilityIcon(iconRes = iconRes)
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SidebarUtilityIcon(
    @DrawableRes iconRes: Int,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier
            .size(21.dp)
            .width(28.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_sessions)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
            ),
            shape = HermexGlassShape,
        )
        HermexIconButton(localizedString("Go"), "⌕", onSearch, enabled = !isLoading)
        if (query.isNotBlank()) {
            HermexIconButton(localizedString("Clear"), "×", onClear)
        }
    }
}

@Composable
private fun ArchivedModeRow(
    count: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.archived_sessions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                localizedStringFormat(
                    if (count == 1) "%lld archived session" else "%lld archived sessions",
                    count,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        HermexPillButton(localizedString("Done"), onClose)
    }
}

@Composable
private fun ProjectSection(
    projects: List<ProjectSummary>,
    sessions: List<SessionSummary>,
    selectedProjectId: String?,
    expanded: Boolean,
    isMutating: Boolean,
    isViewingCachedData: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectProject: (String?) -> Unit,
    onAddProject: () -> Unit,
    onRenameProject: (ProjectSummary) -> Unit,
    onDeleteProject: (ProjectSummary) -> Unit,
) {
    var actionProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(HermexCardShape)
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SidebarUtilityIcon(iconRes = R.drawable.ic_lucide_folder)
                Text(
                    stringResource(R.string.nav_projects),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painter = painterResource(if (expanded) R.drawable.ic_hermex_chevron_down else R.drawable.ic_hermex_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                )
            }
            if (expanded) {
                HermexIconButton(
                    label = localizedString("Add project"),
                    symbol = "+",
                    onClick = onAddProject,
                    enabled = !isViewingCachedData && !isMutating,
                    tonalContainerColor = Color.Transparent,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (selectedProjectId != null) {
                HermexPillButton(
                    label = localizedString("All"),
                    onClick = { onSelectProject(null) },
                    enabled = !isMutating,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (expanded) {
            when {
                projects.isEmpty() -> ProjectStatusSubrow("No projects")
                else -> projects.forEach { project ->
                    val projectId = project.projectId
                    ProjectFilterSubrow(
                        project = project,
                        count = sessions.count { it.projectId == projectId && it.archived != true },
                        selected = projectId != null && projectId == selectedProjectId,
                        actionsExpanded = actionProjectId == project.stableId,
                        actionsEnabled = !isViewingCachedData && !isMutating && projectId != null,
                        onSelect = {
                            if (projectId != null) {
                                onSelectProject(projectId)
                            }
                        },
                        onToggleActions = {
                            actionProjectId = if (actionProjectId == project.stableId) null else project.stableId
                        },
                        onRename = { onRenameProject(project) },
                        onDelete = { onDeleteProject(project) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectStatusSubrow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, end = 24.dp)
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SidebarUtilityIcon(iconRes = R.drawable.ic_lucide_folder, tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ProjectFilterSubrow(
    project: ProjectSummary,
    count: Int,
    selected: Boolean,
    actionsExpanded: Boolean,
    actionsEnabled: Boolean,
    onSelect: () -> Unit,
    onToggleActions: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (selected) {
                        Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clickable(enabled = project.projectId != null, onClick = onSelect)
                    .semantics { this.selected = selected }
                    .padding(start = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SidebarUtilityIcon(iconRes = R.drawable.ic_lucide_folder, tint = project.displayColor)
                Text(
                    project.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (count > 0) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (selected) {
                    SelectedSubrowIndicator()
                }
            }
            HermexIconButton(
                label = localizedStringFormat("Project actions for %@", project.displayName),
                symbol = "...",
                onClick = onToggleActions,
                enabled = actionsEnabled,
                tonalContainerColor = Color.Transparent,
                modifier = Modifier.size(44.dp),
            )
        }
        if (actionsExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 64.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HermexPillButton(localizedString("Rename"), onRename, enabled = actionsEnabled)
                HermexPillButton(localizedString("Delete"), onDelete, enabled = actionsEnabled)
            }
        }
    }
}

@Composable
private fun ProjectColorPicker(
    selectedColorHex: String?,
    onColorSelected: (String?) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                localizedString("Color"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(selectedColorHex.hexColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), CircleShape),
            )
            Text(
                selectedColorHex ?: "Default",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectColorSwatch(
                name = "Default",
                hex = null,
                selected = selectedColorHex == null,
                enabled = enabled,
                onClick = { onColorSelected(null) },
            )
            ProjectColorPalette.forEach { option ->
                ProjectColorSwatch(
                    name = option.name,
                    hex = option.hex,
                    selected = selectedColorHex.equals(option.hex, ignoreCase = true),
                    enabled = enabled,
                    onClick = { onColorSelected(option.hex) },
                )
            }
        }
    }
}

@Composable
private fun ProjectColorSwatch(
    name: String,
    hex: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val swatchColor = hex.hexColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hex == null) {
                Text(
                    "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusStack(state: SessionListUiState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.isSearchingRemoteSessions) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                StatusLine("Searching sessions", MaterialTheme.colorScheme.secondary)
            }
        }
        if (state.isViewingCachedData) StatusLine("Offline cache", MaterialTheme.colorScheme.tertiary)
        state.notice?.let { StatusLine(it, MaterialTheme.colorScheme.tertiary) }
        state.searchError?.let { StatusLine(it, MaterialTheme.colorScheme.error) }
        state.error?.let { StatusLine(it, MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun EmptySessionsRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 34.dp)
            .hermexGlass(shape = HermexCardShape, castsShadow = false)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(localizedString("No sessions match this view."), style = MaterialTheme.typography.titleMedium)
        Text(
            localizedString("Create a new chat or clear search filters."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    projects: List<ProjectSummary>,
    isMutating: Boolean,
    isViewingCachedData: Boolean,
    showsMessageCount: Boolean,
    showsWorkspace: Boolean,
    selected: Boolean,
    actionsExpanded: Boolean,
    onOpen: () -> Unit,
    onToggleActions: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onCopyTitle: () -> Unit,
    onCopyDeepLink: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBranch: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (String?) -> Unit,
    onExport: (SessionExportFormat) -> Unit,
) {
    val metadata = session.sessionRowMetadataLabel(showsMessageCount, showsWorkspace)
    val mutationActionsEnabled = !isMutating && !session.isSessionReadOnly
    val rowMinimumHeight = if (metadata != null || session.isActiveStreaming || isViewingCachedData || session.isSessionReadOnly) 54.dp else 46.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SessionSwipeContainer(
            enabled = mutationActionsEnabled,
            archived = session.archived == true,
            onArchive = onArchive,
            onDelete = onDelete,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = rowMinimumHeight)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        else hermexBackgroundColor(),
                    )
                    .semantics { this.selected = selected }
                    .testTag("session_row_${session.stableId}")
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClickLabel = "Session actions",
                        onLongClick = { if (!isMutating) onToggleActions() },
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            if (session.isActiveStreaming) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759)),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (session.pinned == true) {
                        Text(
                            "●",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    session.relativeDate?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    }
                }
                if (metadata != null || session.isActiveStreaming || isViewingCachedData || session.isSessionReadOnly) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (session.isActiveStreaming) SessionStateBadge(localizedString("Live"), Color(0xFF34C759))
                        if (isViewingCachedData) SessionStateBadge(localizedString("Cached"), Color(0xFFFF9500))
                        if (session.isSessionReadOnly) SessionStateBadge(localizedString("Read-only"), MaterialTheme.colorScheme.secondary)
                        metadata?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }
                }
            }
            }
        }
        if (actionsExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (session.archived == true) MiniBadge("Archived")
                if (session.isActiveStreaming) MiniBadge("Streaming", tint = MaterialTheme.colorScheme.primary)
                if (session.isSessionReadOnly) MiniBadge("Read-only", tint = MaterialTheme.colorScheme.secondary)
                HermexPillButton(localizedString("Copy Full Title"), onCopyTitle, enabled = true)
                HermexPillButton(
                    localizedString("Copy Deeplink"),
                    onCopyDeepLink,
                    enabled = !isMutating && !isViewingCachedData && session.sessionId != null,
                )
                HermexPillButton(localizedString(if (session.pinned == true) "Unpin" else "Pin"), onPin, enabled = mutationActionsEnabled)
                HermexPillButton(localizedString(if (session.archived == true) "Restore" else "Archive"), onArchive, enabled = mutationActionsEnabled)
                HermexPillButton(localizedString("Rename"), onRename, enabled = mutationActionsEnabled)
                HermexPillButton(localizedString("Branch"), onBranch, enabled = mutationActionsEnabled)
                HermexPillButton(localizedString("Duplicate"), onDuplicate, enabled = mutationActionsEnabled && !isViewingCachedData && session.sessionId != null)
                HermexPillButton(localizedString("Export HTML"), { onExport(SessionExportFormat.Html) }, enabled = !isMutating && !isViewingCachedData && session.sessionId != null)
                HermexPillButton(localizedString("Export JSON"), { onExport(SessionExportFormat.Json) }, enabled = !isMutating && !isViewingCachedData && session.sessionId != null)
                HermexPillButton(localizedString("Delete"), onDelete, enabled = mutationActionsEnabled)
                HermexPillButton(localizedString("No project"), { onMove(null) }, enabled = mutationActionsEnabled && session.projectId != null)
                projects.forEach { project ->
                    val projectId = project.projectId
                    HermexPillButton(
                        label = if (session.projectId == projectId) "In ${project.displayName}" else "Move ${project.displayName}",
                        onClick = { if (projectId != null) onMove(projectId) },
                        enabled = mutationActionsEnabled && projectId != null && session.projectId != projectId,
                    )
                }
            }
        }
    }

}

@Composable
private fun SessionSwipeContainer(
    enabled: Boolean,
    archived: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealWidthPx = with(LocalDensity.current) { 168.dp.toPx() }
    val viewConfiguration = LocalViewConfiguration.current
    val swipeViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val touchSlop: Float = viewConfiguration.touchSlop * 0.55f
        }
    }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(enabled) {
        if (!enabled) offsetPx = 0f
    }

    CompositionLocalProvider(LocalViewConfiguration provides swipeViewConfiguration) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (offsetPx < -0.5f) {
                Row(
                    modifier = Modifier.matchParentSize(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwipeAction(
                        label = localizedString("Delete"),
                        iconRes = R.drawable.ic_hermex_trash,
                        color = Color(0xFFFF3B30),
                        onClick = {
                            offsetPx = 0f
                            onDelete()
                        },
                    )
                    SwipeAction(
                        label = localizedString(if (archived) "Restore" else "Archive"),
                        iconRes = R.drawable.ic_hermex_archive_box,
                        color = Color(0xFFFF9500),
                        onClick = {
                            offsetPx = 0f
                            onArchive()
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = offsetPx }
                    .draggable(
                        enabled = enabled,
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            offsetPx = (offsetPx + delta).coerceIn(-revealWidthPx, 0f)
                        },
                        onDragStopped = { velocity ->
                            val targetOffset = when {
                                velocity < -250f -> -revealWidthPx
                                velocity > 250f -> 0f
                                offsetPx <= -revealWidthPx * 0.18f -> -revealWidthPx
                                else -> 0f
                            }
                            animate(
                                initialValue = offsetPx,
                                targetValue = targetOffset,
                                initialVelocity = velocity,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            ) { value, _ ->
                                offsetPx = value
                            }
                        },
                    ),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    @DrawableRes iconRes: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxSize()
            .testTag("session_swipe_action_${label.lowercase()}")
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color)
                .testTag("session_swipe_action_${label.lowercase()}_circle"),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("session_swipe_action_${label.lowercase()}_icon"),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        Text(localizedString(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SessionStateBadge(
    label: String,
    tint: Color,
) {
    Text(
        localizedString(label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun MiniBadge(
    label: String,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    Text(
        text = label,
        color = tint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
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
            title = { Text(localizedString("Rename Session")) },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = viewModel::updateRenameDraft,
                    label = { Text(localizedString("Title")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRename, enabled = !state.isMutating && state.renameDraft.isNotBlank()) { Text(localizedString("Rename")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRename) { Text(localizedString("Cancel")) }
            },
        )
    }

    state.deleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { if (!state.isMutating) viewModel.dismissDelete() },
            title = { Text(localizedString("Delete Session?")) },
            text = { Text(session.title ?: localizedString("Delete Session?")) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, enabled = !state.isMutating) { Text(localizedString("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, enabled = !state.isMutating) { Text(localizedString("Cancel")) }
            },
        )
    }

    state.branchSession?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissBranch,
            title = { Text(localizedString("Branch Session")) },
            text = {
                OutlinedTextField(
                    value = state.branchTitleDraft,
                    onValueChange = viewModel::updateBranchTitleDraft,
                    label = { Text(localizedString("Optional title")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmBranch, enabled = !state.isMutating) { Text(localizedString("Branch")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBranch) { Text(localizedString("Cancel")) }
            },
        )
    }

    state.renameProject?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameProject,
            title = { Text(localizedString("Rename Project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    OutlinedTextField(
                        value = state.renameProjectDraft,
                        onValueChange = viewModel::updateRenameProjectDraft,
                        label = { Text(localizedString("Project name")) },
                        singleLine = true,
                    )
                    ProjectColorPicker(
                        selectedColorHex = state.renameProjectColor,
                        onColorSelected = viewModel::updateRenameProjectColor,
                        enabled = !state.isMutating,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRenameProject, enabled = !state.isMutating && state.renameProjectDraft.isNotBlank()) { Text(localizedString("Rename")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameProject) { Text(localizedString("Cancel")) }
            },
        )
    }

    state.deleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteProject,
            title = { Text(localizedString("Delete Project?")) },
            text = { Text(project.displayName) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteProject, enabled = !state.isMutating) { Text(localizedString("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteProject) { Text(localizedString("Cancel")) }
            },
        )
    }
}

private val ProjectSummary.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: "Untitled Project"

private val ProjectSummary.displayColor: Color
    get() = color.hexColorOrNull() ?: when (stableColorSeed % 5) {
        0 -> Color(0xFF34C759)
        1 -> Color(0xFF007AFF)
        2 -> Color(0xFFFF3B30)
        3 -> Color(0xFFFF9500)
        else -> Color(0xFF5856D6)
    }

private val ProjectSummary.stableColorSeed: Int
    get() = (projectId ?: displayName).fold(0) { acc, char -> acc + char.code }

private fun String?.hexColorOrNull(): Color? {
    val raw = this?.trim()?.takeIf { it.matches(Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")) } ?: return null
    return runCatching { Color(AndroidColor.parseColor(raw.withLeadingHash())) }.getOrNull()
}

private fun String.withLeadingHash(): String = if (startsWith('#')) this else "#$this"

private val ProfileSummary.displayTitle: String
    get() = displayName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }?.let { if (it == "default") "Default" else it }
        ?: "Profile"

private val ProfileSummary.modelProviderText: String?
    get() = model?.trim()?.takeIf { it.isNotBlank() }

private val SessionListUiState.activeProfileDisplayText: String
    get() = profileOptions.firstOrNull { it.name == activeProfileName }?.displayTitle
        ?: activeProfileName?.takeIf { it.isNotBlank() }
        ?: "Default"

private fun accountInitials(accountName: String): String {
    val words = accountName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    val initials = when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().take(2)
        else -> "H"
    }
    return initials.uppercase()
}

private val SessionSummary.isActiveStreaming: Boolean
    get() = isStreaming == true || !activeStreamId.isNullOrBlank()

private val SessionSummary.displayTitle: String
    get() = title?.trim()?.takeIf { it.isNotBlank() } ?: "Untitled Session"

private fun SessionSummary.sessionRowMetadataLabel(
    showsMessageCount: Boolean,
    showsWorkspace: Boolean,
): String? = listOfNotNull(
        messageCount?.takeIf { showsMessageCount && it >= 0 }?.let(::messageCountLabel),
        workspace?.takeIf { showsWorkspace && it.isNotBlank() }?.sessionWorkspaceDisplayName(),
    ).joinToString(" \u2022 ").ifBlank { null }

private val SessionSummary.metadataLabel: String?
    get() = listOfNotNull(
        messageCount?.takeIf { it >= 0 }?.let(::messageCountLabel),
        workspace?.takeIf { it.isNotBlank() }?.sessionWorkspaceDisplayName(),
        model?.takeIf { it.isNotBlank() },
    ).joinToString(" \u00b7 ").ifBlank { "No metadata" }

private fun String.sessionWorkspaceDisplayName(): String {
    val normalized = trim()
    return if ('\\' in normalized) normalized else normalized.substringAfterLast('/')
}

private fun messageCountLabel(count: Int): String =
    "$count ${if (count == 1) "message" else "messages"}"

private val SessionSummary.relativeDate: String?
    get() {
        val timestamp = lastMessageAt ?: updatedAt ?: createdAt ?: return null
        val millis = (timestamp * 1000).roundToLong()
        val then = runCatching { Instant.ofEpochMilli(millis) }.getOrNull() ?: return null
        val seconds = Duration.between(then, Instant.now()).seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "now"
            seconds < 3_600 -> "${seconds / 60} min ago"
            seconds < 86_400 -> "${seconds / 3_600} hr ago"
            seconds < 604_800 -> {
                val days = seconds / 86_400
                "$days ${if (days == 1L) "day" else "days"} ago"
            }
            else -> "${seconds / 604_800} wk ago"
        }
    }

private suspend fun shareSessionExport(context: Context, export: SessionExportFile): Result<Unit> =
    runCatching {
        val uri = withContext(Dispatchers.IO) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", export.file)
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType(export.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Export Session"))
    }
