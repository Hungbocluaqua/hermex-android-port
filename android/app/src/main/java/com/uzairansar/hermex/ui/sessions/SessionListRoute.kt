package com.uzairansar.hermex.ui.sessions

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.uzairansar.hermex.ui.theme.HermesHeaderLogo
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexColors
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexColorFromHex
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContainerColor
import com.uzairansar.hermex.ui.theme.hermexPrimaryActionContentColor
import com.uzairansar.hermex.ui.theme.primaryActionTintApplies
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

@Composable
fun SessionListRoute(
    authState: AuthState,
    container: AppContainer,
    shortcutAction: String? = null,
    shortcutNonce: String? = null,
    shortcutProfile: String? = null,
    onOpenChat: (String) -> Unit,
    onOpenVoiceChat: (String) -> Unit,
    onOpenSharedDraft: (String) -> Unit,
    onOpenPanels: () -> Unit,
    onOpenPanel: (String) -> Unit = { onOpenPanels() },
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
            return SessionListViewModel(
                repository = container.sessionRepository(loggedIn.server),
                panelsRepository = container.panelsRepository(loggedIn.server),
                localSettingsRepository = container.localSettingsRepository,
                serverId = loggedIn.server.toString(),
            ) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shortcutKey = listOfNotNull(shortcutAction, shortcutNonce).joinToString(":")
    var shortcutConsumed by rememberSaveable(shortcutKey) { mutableStateOf(false) }
    var expandedActionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    var projectsExpanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var isCreatingProject by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val primaryActionTintColor = remember(state.tintPrimaryActionsWithThemeColor, loggedIn.account.headerLogoColorHex, state.isMutating) {
        if (primaryActionTintApplies(state.tintPrimaryActionsWithThemeColor, !state.isMutating)) {
            hermexColorFromHex(loggedIn.account.headerLogoColorHex)
        } else {
            null
        }
    }
    val copySessionTitle: (SessionSummary) -> Unit = { session ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hermex session title", session.displayTitle))
    }

    LaunchedEffect(shortcutKey, shortcutAction, shortcutProfile, shortcutConsumed) {
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.NewSessionAction) {
            shortcutConsumed = true
            viewModel.createSession(onCreated = onOpenChat)
        }
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.NewVoiceSessionAction) {
            shortcutConsumed = true
            viewModel.createSession(onCreated = onOpenVoiceChat)
        }
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.NewProfileSessionAction) {
            shortcutConsumed = true
            viewModel.createSession(profile = shortcutProfile, onCreated = onOpenChat)
        }
        if (!shortcutConsumed && shortcutAction == ShortcutDestination.ShareAction) {
            shortcutConsumed = true
            viewModel.createSession(onCreated = onOpenSharedDraft)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 18.dp,
                bottom = 108.dp,
            ),
        ) {
            item {
                SessionsHeader(
                    accountName = loggedIn.account.displayName,
                    query = state.searchQuery,
                    searchExpanded = searchExpanded,
                    onSearchExpandedChange = { searchExpanded = it },
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearch = viewModel::refresh,
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
                if (state.searchQuery.isBlank()) {
                    item {
                        UtilityRows(onOpenPanel = onOpenPanel)
                    }
                    if (!state.isSingleProfileMode) {
                        item {
                            ActiveProfileSection(
                                state = state,
                                expanded = profilesExpanded,
                                onToggleExpanded = { profilesExpanded = !profilesExpanded },
                                onSelectProfile = viewModel::switchProfile,
                            )
                        }
                    }
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
                                viewModel.updateNewProjectName("")
                                isCreatingProject = true
                            },
                            onRenameProject = viewModel::requestRenameProject,
                            onDeleteProject = viewModel::requestDeleteProject,
                        )
                    }
                }
            }
            item {
                StatusStack(state = state)
            }
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (state.visibleSessions.isEmpty()) {
                item {
                    EmptySessionsRow()
                }
            } else {
                items(state.visibleSessions, key = { it.stableId }) { session ->
                    SessionRow(
                        session = session,
                        projects = state.projects,
                        isMutating = state.isMutating,
                        isViewingCachedData = state.isViewingCachedData,
                        showsMessageCount = state.sessionRowDisplaySettings.showMessageCount,
                        showsWorkspace = state.sessionRowDisplaySettings.showWorkspace,
                        actionsExpanded = expandedActionsFor == session.stableId,
                        onOpen = { session.sessionId?.let(onOpenChat) },
                        onToggleActions = {
                            expandedActionsFor = if (expandedActionsFor == session.stableId) null else session.stableId
                        },
                        onPin = { viewModel.togglePin(session) },
                        onArchive = { viewModel.toggleArchive(session) },
                        onCopyTitle = { copySessionTitle(session) },
                        onRename = { viewModel.requestRename(session) },
                        onDelete = { viewModel.requestDelete(session) },
                        onBranch = { viewModel.requestBranch(session) },
                        onDuplicate = { viewModel.duplicate(session, onOpenChat) },
                        onMove = { projectId -> viewModel.move(session, projectId) },
                        onExport = { format ->
                            viewModel.exportSession(session, format) { file ->
                                shareSessionExport(context, file)
                            }
                        },
                    )
                }
            }
            if (
                !state.showArchived &&
                state.searchQuery.isBlank() &&
                !state.isViewingCachedData &&
                (state.archivedCount ?: 0) > 0
            ) {
                item {
                    ArchivedEntryRow(
                        count = state.archivedCount,
                        onOpen = viewModel::toggleArchived,
                    )
                }
            }
        }

        NewChatFloatingButton(
            onClick = { viewModel.createSession(onCreated = onOpenChat) },
            enabled = !state.isMutating,
            tintColor = primaryActionTintColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 22.dp),
        )
    }

    SessionDialogs(state, viewModel, onOpenChat)
    if (isCreatingProject) {
        AlertDialog(
            onDismissRequest = {
                isCreatingProject = false
                viewModel.updateNewProjectName("")
            },
            title = { Text("New Project") },
            text = {
                OutlinedTextField(
                    value = state.newProjectName,
                    onValueChange = viewModel::updateNewProjectName,
                    label = { Text("Project name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProject()
                        isCreatingProject = false
                    },
                    enabled = state.newProjectName.isNotBlank() && !state.isMutating,
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isCreatingProject = false
                        viewModel.updateNewProjectName("")
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SessionsHeader(
    accountName: String,
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
            )
            Spacer(Modifier.weight(1f))
        }
        SessionSearchChrome(
            query = query,
            accountName = accountName,
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
    accountName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        "Search sessions",
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotBlank()) {
                HermexIconButton("Clear", "x", onClear, tonalContainerColor = Color.Transparent)
            }
        } else {
            HermexIconButton(
                label = "Search sessions",
                symbol = "⌕",
                onClick = { onExpandedChange(true) },
                tonalContainerColor = Color.Transparent,
            )
        }
        HermexIconButton(
            label = if (expanded) "Close search" else "Settings",
            symbol = if (expanded) "x" else accountInitials(accountName),
            onClick = {
                if (expanded) {
                    onClear()
                    onExpandedChange(false)
                } else {
                    onSettings()
                }
            },
            filled = !expanded,
            filledContainerColor = HermexColors.HeaderGold,
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
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(58.dp)
            .clip(CircleShape)
            .background(hermexPrimaryActionContainerColor(enabled, tintColor)),
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = hermexPrimaryActionContentColor(enabled, tintColor)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 0.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UtilityRows(
    onOpenPanel: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UtilityRow("Tasks", R.drawable.ic_lucide_calendar_clock, "tasks", onOpenPanel)
        UtilityRow("Skills", R.drawable.ic_lucide_hammer, "skills", onOpenPanel)
        UtilityRow("Memory", R.drawable.ic_lucide_brain, "memory", onOpenPanel)
        UtilityRow("Insights", R.drawable.ic_lucide_chart_column_increasing, "insights", onOpenPanel)
    }
}

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
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidebarUtilityIcon(
                iconRes = R.drawable.ic_lucide_user_round_cog,
                tint = if (state.profileError == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Active Profile",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.activeProfileDisplayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(if (expanded) "v" else ">", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HermexCardShape)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(start = 72.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                profile.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile.modelProviderText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Text("check", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UtilityRow(
    title: String,
    @DrawableRes iconRes: Int,
    destination: String,
    onOpenPanel: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HermexCardShape)
            .clickable { onOpenPanel(destination) }
            .heightIn(min = 44.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SidebarUtilityIcon(iconRes = iconRes)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
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
            placeholder = { Text("Search sessions") },
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
        HermexIconButton("Go", "⌕", onSearch, enabled = !isLoading)
        if (query.isNotBlank()) {
            HermexIconButton("Clear", "×", onClear)
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
            Text("Archived Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$count archived session${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        HermexPillButton("Done", onClose)
    }
}

@Composable
private fun ArchivedEntryRow(
    count: Int?,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Archive", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Text(
            "Archived Sessions",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                it.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(">", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
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
                .padding(horizontal = 24.dp),
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
                    "Projects",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "v" else ">", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            if (expanded) {
                HermexIconButton(
                    label = "Add project",
                    symbol = "+",
                    onClick = onAddProject,
                    enabled = !isViewingCachedData && !isMutating,
                    tonalContainerColor = Color.Transparent,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (selectedProjectId != null) {
                HermexPillButton(
                    label = "All",
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
                    .heightIn(min = 44.dp)
                    .clickable(enabled = project.projectId != null, onClick = onSelect)
                    .padding(start = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SidebarUtilityIcon(iconRes = R.drawable.ic_lucide_folder, tint = project.displayColor)
                Text(
                    project.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (count > 0) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (selected) {
                    Text(
                        "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            HermexIconButton(
                label = "Project actions for ${project.displayName}",
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
                HermexPillButton("Rename", onRename, enabled = actionsEnabled)
                HermexPillButton("Delete", onDelete, enabled = actionsEnabled)
            }
        }
    }
}

@Composable
private fun StatusStack(state: SessionListUiState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.isViewingCachedData) StatusLine("Offline cache", MaterialTheme.colorScheme.tertiary)
        state.notice?.let { StatusLine(it, MaterialTheme.colorScheme.tertiary) }
        state.error?.let { StatusLine(it, MaterialTheme.colorScheme.error) }
        state.archivedCount?.takeIf { it > 0 && !state.showArchived }?.let {
            StatusLine("$it archived session(s)", MaterialTheme.colorScheme.secondary)
        }
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
        Text("No sessions match this view.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create a new chat or clear search filters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    projects: List<ProjectSummary>,
    isMutating: Boolean,
    isViewingCachedData: Boolean,
    showsMessageCount: Boolean,
    showsWorkspace: Boolean,
    actionsExpanded: Boolean,
    onOpen: () -> Unit,
    onToggleActions: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onCopyTitle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBranch: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (String?) -> Unit,
    onExport: (SessionExportFormat) -> Unit,
) {
    val metadata = session.sessionRowMetadataLabel(showsMessageCount, showsWorkspace)
    val rowMinimumHeight = if (metadata != null || session.isActiveStreaming || isViewingCachedData) 54.dp else 46.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowMinimumHeight)
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    }
                }
                if (metadata != null || session.isActiveStreaming || isViewingCachedData) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (session.isActiveStreaming) SessionStateBadge("Live", Color(0xFF34C759))
                        if (isViewingCachedData) SessionStateBadge("Cached", Color(0xFFFF9500))
                        metadata?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }
                }
            }
            HermexIconButton(
                label = "Session actions",
                symbol = "...",
                onClick = onToggleActions,
                enabled = !isMutating,
                modifier = Modifier.size(34.dp),
            )
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
                HermexPillButton("Copy Full Title", onCopyTitle, enabled = true)
                HermexPillButton(if (session.pinned == true) "Unpin" else "Pin", onPin, enabled = !isMutating)
                HermexPillButton(if (session.archived == true) "Restore" else "Archive", onArchive, enabled = !isMutating)
                HermexPillButton("Rename", onRename, enabled = !isMutating)
                HermexPillButton("Branch", onBranch, enabled = !isMutating)
                HermexPillButton("Duplicate", onDuplicate, enabled = !isMutating && !isViewingCachedData && session.sessionId != null)
                HermexPillButton("Export HTML", { onExport(SessionExportFormat.Html) }, enabled = !isMutating && !isViewingCachedData && session.sessionId != null)
                HermexPillButton("Export JSON", { onExport(SessionExportFormat.Json) }, enabled = !isMutating && !isViewingCachedData && session.sessionId != null)
                HermexPillButton("Delete", onDelete, enabled = !isMutating)
                HermexPillButton("No project", { onMove(null) }, enabled = !isMutating && session.projectId != null)
                projects.forEach { project ->
                    val projectId = project.projectId
                    HermexPillButton(
                        label = if (session.projectId == projectId) "In ${project.displayName}" else "Move ${project.displayName}",
                        onClick = { if (projectId != null) onMove(projectId) },
                        enabled = !isMutating && projectId != null && session.projectId != projectId,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun SessionStateBadge(
    label: String,
    tint: Color,
) {
    Text(
        label,
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

private val ProjectSummary.displayColor: Color
    get() = color.hexColorOrNull() ?: when (stableColorSeed % 5) {
        0 -> Color(0xFF34C759)
        1 -> Color(0xFF007AFF)
        2 -> Color(0xFFFF3B30)
        3 -> Color(0xFFFF9500)
        else -> Color.Unspecified
    }

private val ProjectSummary.stableColorSeed: Int
    get() = (projectId ?: displayName).fold(0) { acc, char -> acc + char.code }

private fun String?.hexColorOrNull(): Color? {
    val raw = this?.trim()?.removePrefix("#") ?: return null
    val rgb = raw.toLongOrNull(16) ?: return null
    return when (raw.length) {
        6 -> Color((0xFF000000L or rgb).toULong())
        8 -> Color(rgb.toULong())
        else -> null
    }
}

private val ProfileSummary.displayTitle: String
    get() = displayName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Profile"

private val ProfileSummary.modelProviderText: String?
    get() = listOfNotNull(
        provider?.takeIf { it.isNotBlank() },
    ).joinToString(" / ").ifBlank { null }

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
        workspace?.takeIf { showsWorkspace && it.isNotBlank() }?.substringAfterLast('\\')?.substringAfterLast('/'),
    ).joinToString(" • ").ifBlank { null }

private val SessionSummary.metadataLabel: String?
    get() = listOfNotNull(
        messageCount?.takeIf { it >= 0 }?.let(::messageCountLabel),
        workspace?.takeIf { it.isNotBlank() }?.substringAfterLast('\\')?.substringAfterLast('/'),
        model?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { "No metadata" }

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
            seconds < 3_600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3_600}h ago"
            seconds < 604_800 -> "${seconds / 86_400}d ago"
            else -> "${seconds / 604_800}w ago"
        }
    }

private fun shareSessionExport(context: Context, export: SessionExportFile) {
    val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
    val file = File(exportDir, export.filename)
    file.writeBytes(export.data)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(export.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Export Session"))
}
