package com.uzairansar.hermex.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.uzairansar.hermex.R
import com.uzairansar.hermex.data.preferences.displayModelTitle
import com.uzairansar.hermex.data.preferences.modelIdentifier
import com.uzairansar.hermex.data.preferences.AppThemeMode
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.data.repository.CacheMaintenanceRepository
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.HermesHeaderLogo
import com.uzairansar.hermex.ui.theme.hermexColorFromHex
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.notifications.AndroidNotificationPermissionPolicy
import com.uzairansar.hermex.ui.chat.StreamStatusNotifier

@Composable
fun SettingsRoute(
    authRepository: AuthRepository,
    localSettingsRepository: LocalSettingsRepository,
    cacheMaintenanceRepository: CacheMaintenanceRepository? = null,
    panelsRepository: PanelsRepository?,
    authState: AuthState,
    onBack: () -> Unit,
    onOpenArchivedSessions: () -> Unit = {},
    onSignedOut: () -> Unit,
) {
    val activeServerKey = (authState as? AuthState.LoggedIn)?.server?.toString() ?: "disconnected"
    val viewModel: SettingsViewModel = viewModel(
        key = "settings:$activeServerKey",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(authRepository, localSettingsRepository, cacheMaintenanceRepository, panelsRepository) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn = authState as? AuthState.LoggedIn
    val context = LocalContext.current
    val appInfo = remember(context) { context.currentAndroidAppInfo() }
    val appIconManager = remember(context) { AppIconManager(context) }
    var selectedAppIcon by remember(appIconManager) {
        mutableStateOf(runCatching(appIconManager::currentChoice).getOrDefault(AppIconChoice.Default))
    }
    var isAppIconPickerExpanded by remember { mutableStateOf(false) }
    var appIconErrorMessage by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.handleResponseCompletionNotificationPermissionResult(granted)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context, viewModel, appIconManager) {
        fun refreshNotificationPermission() {
            viewModel.refreshResponseCompletionNotificationPermission(
                canPostNotifications = canPostAndroidNotifications(context),
            )
        }

        fun refreshAppIcon() {
            appIconManager.ensureValidSelection()
                .onSuccess {
                    selectedAppIcon = it
                    appIconErrorMessage = null
                }
                .onFailure { appIconErrorMessage = it.message ?: "Unable to read the launcher icon." }
        }

        refreshNotificationPermission()
        refreshAppIcon()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNotificationPermission()
                refreshAppIcon()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsHeader(onBack)
            LazyColumn(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .testTag("settings_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsSection(title = "Identity") {
                    IdentitySummary(
                        displayName = state.sessionIdentitySettings.displayName,
                        initials = state.sessionIdentitySettings.initials,
                        headerColorHex = state.headerLogoColorHex,
                        onDisplayNameChange = viewModel::setSessionIdentityDisplayName,
                        onInitialsChange = viewModel::setSessionIdentityInitials,
                    )
                }
            }
            item {
                SettingsSection(title = "Archived Sessions") {
                    SettingsAccessoryRow(
                        label = "Archived Sessions",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_archive_box,
                        onClick = onOpenArchivedSessions,
                    )
                }
            }
            item {
                SettingsSection(title = "Appearance") {
                    SettingsPickerRow(
                        label = "Theme",
                        value = state.themeMode.label,
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_half_filled_theme_circle,
                        onClick = {
                            val nextIndex = (AppThemeMode.entries.indexOf(state.themeMode) + 1) % AppThemeMode.entries.size
                            viewModel.setThemeMode(AppThemeMode.entries[nextIndex])
                        },
                    )
                    SettingsDivider()
                    HeaderLogoColorSettings(
                        selectedHex = state.headerLogoColorHex,
                        onSelected = viewModel::setHeaderLogoColorHex,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Tint New Chat & Send",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_paintbrush,
                        value = state.tintPrimaryActionsWithThemeColor,
                        onValueChange = viewModel::setTintPrimaryActionsWithThemeColor,
                    )
                    SettingsFootnote("Apply your header color to these primary buttons.")
                    SettingsDivider()
                    AppIconSettingsPicker(
                        selected = selectedAppIcon,
                        isExpanded = isAppIconPickerExpanded,
                        errorMessage = appIconErrorMessage,
                        onToggleExpanded = { isAppIconPickerExpanded = !isAppIconPickerExpanded },
                        onSelect = { choice ->
                            appIconErrorMessage = null
                            appIconManager.setChoice(choice)
                                .onSuccess {
                                    selectedAppIcon = it
                                    isAppIconPickerExpanded = false
                                }
                                .onFailure { error ->
                                    selectedAppIcon = runCatching(appIconManager::currentChoice)
                                        .getOrDefault(AppIconChoice.Default)
                                    appIconErrorMessage = error.message ?: "Unable to update the launcher icon."
                                    isAppIconPickerExpanded = true
                                }
                        },
                    )
                }
            }
            item {
                SettingsSection(title = "Interaction") {
                    SettingsToggleRow(
                        label = "Haptic Feedback",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_haptic_phone,
                        value = state.hapticsEnabled,
                        onValueChange = viewModel::setHapticsEnabled,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Response Complete Alerts",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_bell,
                        value = state.responseCompletionNotificationsEnabled,
                        onValueChange = { enabled ->
                            if (!enabled) {
                                viewModel.setResponseCompletionNotificationsEnabled(false, "Android notifications disabled.")
                            } else if (canPostAndroidNotifications(context)) {
                                viewModel.setResponseCompletionNotificationsEnabled(true, "Android notifications allowed.")
                            } else if (hasPostNotificationPermission(context)) {
                                viewModel.setResponseCompletionNotificationsEnabled(false, "Enable Hermex notifications in Android Settings.")
                                openAndroidAppSettings(context)
                            } else {
                                viewModel.markResponseCompletionNotificationPermissionRequested()
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    responseCompletionNotificationStatusText(state, context)?.let { SettingsFootnote(it) }
                    SettingsDivider()
                    SettingsPickerRow(
                        label = "Send While Responding",
                        value = state.streamingSendBehavior.settingsDescription,
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_message_arrow,
                        onClick = {
                            val entries = StreamingSendBehavior.entries
                            val nextIndex = (entries.indexOf(state.streamingSendBehavior) + 1) % entries.size
                            viewModel.setStreamingSendBehavior(entries[nextIndex])
                        },
                    )
                }
            }
            item {
                SettingsSection(title = "Chat") {
                    SettingsToggleRow(
                        label = "Thinking and Tool Cards",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_lucide_brain,
                        value = state.chatDisplaySettings.showThinkingAndToolCards,
                        onValueChange = viewModel::setShowThinkingAndToolCards,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Expand Thinking by Default",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_expand_vertical,
                        value = state.chatDisplaySettings.thinkingCardsStartExpanded,
                        enabled = state.chatDisplaySettings.showThinkingAndToolCards,
                        onValueChange = viewModel::setThinkingCardsStartExpanded,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Expand Tools by Default",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_tools_wrench,
                        value = state.chatDisplaySettings.toolCardsStartExpanded,
                        enabled = state.chatDisplaySettings.showThinkingAndToolCards,
                        onValueChange = viewModel::setToolCardsStartExpanded,
                    )
                    SettingsFootnote("Thinking and Tool cards start expanded instead of collapsed. Tapping a card still toggles it.")
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Streamed Text Animation",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_sparkles,
                        value = state.chatDisplaySettings.streamedTextAnimationEnabled,
                        onValueChange = viewModel::setStreamedTextAnimationEnabled,
                    )
                    SettingsFootnote("Fades words in as a response streams. Turn off to show text instantly.")
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Response Timestamps",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_clock,
                        value = state.chatDisplaySettings.showsAssistantTurnTimestamps,
                        onValueChange = viewModel::setShowsAssistantTurnTimestamps,
                    )
                    SettingsFootnote("Adds a small marker and time above each response.")
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Wrap Code Block Lines",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_wrap_line,
                        value = state.chatDisplaySettings.wrapsCodeBlockLines,
                        onValueChange = viewModel::setWrapsCodeBlockLines,
                    )
                    SettingsFootnote("Wraps long code lines to fit the screen instead of scrolling sideways.")
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Right-to-Left Chat Layout",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_align_right,
                        value = state.chatDisplaySettings.rtlChatLayoutEnabled,
                        onValueChange = viewModel::setRtlChatLayoutEnabled,
                    )
                    SettingsFootnote("Lays out messages and the composer right-to-left while code and tool output stay left-to-right.")
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Hide Attachment Paths",
                        iconRes = com.uzairansar.hermex.R.drawable.ic_hermex_eye_slash,
                        value = state.chatDisplaySettings.hidesAttachmentPaths,
                        onValueChange = viewModel::setHidesAttachmentPaths,
                    )
                    SettingsFootnote("Hides appended file paths while keeping attachment previews and server delivery intact.")
                }
            }
            item {
                SettingsSection(title = "Servers") {
                    if (state.serverSnapshot.servers.isEmpty()) {
                        Text("No servers configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    } else {
                        state.serverSnapshot.servers.forEach { account ->
                            ServerAccountRow(
                                displayName = account.displayName,
                                url = account.urlString,
                                initials = account.initials,
                                headerColorHex = account.headerLogoColorHex,
                                isActive = account.id == state.serverSnapshot.activeServerId,
                                headerCount = state.customHeadersByServer[account.id]?.size ?: 0,
                                onActivate = { viewModel.activateServer(account.id) },
                                onEditIdentity = { viewModel.openIdentityEditor(account) },
                                onEditHeaders = { viewModel.openHeaderEditor(account) },
                                onClearCache = { viewModel.requestClearCache(account) },
                                onForget = { viewModel.requestForgetServer(account) },
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    HermexPillButton(
                        label = if (state.isAddingServer) "Adding..." else "Add Server",
                        onClick = viewModel::openAddServer,
                        enabled = !state.isAddingServer,
                        filled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "Server URLs, custom headers, and cookies are stored in Android encrypted storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val activeServer = state.serverSnapshot.servers.firstOrNull { it.id == state.serverSnapshot.activeServerId }
                        HermexPillButton(
                            label = if (state.isMaintainingCache) "Cleaning..." else "Clean Expired Cache",
                            onClick = viewModel::runCacheMaintenance,
                            enabled = cacheMaintenanceRepository != null && !state.isMaintainingCache,
                        )
                        HermexPillButton(
                            label = if (state.isClearingCache) "Clearing..." else "Clear Active Cache",
                            onClick = { activeServer?.let(viewModel::requestClearCache) },
                            enabled = activeServer != null && cacheMaintenanceRepository != null && !state.isClearingCache,
                        )
                    }
                }
            }
            item {
                SettingsSection(title = "Active Server") {
                    when {
                        panelsRepository == null -> Text("Connect to a server to manage active-server settings.", style = MaterialTheme.typography.bodySmall)
                        state.isLoadingServerSettings -> CircularProgressIndicator(strokeWidth = 2.dp)
                        else -> {
                            DetailLine("Status", if (loggedIn != null) "Signed in" else "Signed out")
                            DetailLine("URL", loggedIn?.server?.toString() ?: "Not configured")
                            DetailLine("Version", state.serverSettings?.webuiVersion ?: "Unknown")
                            state.serverSettings?.botName?.takeIf { it.isNotBlank() }?.let { DetailLine("Bot Name", it) }
                            ServerUpdateControls(state, viewModel)
                            Spacer(Modifier.height(12.dp))
                            SettingsPickerSummaryRow(
                                title = "Default Model",
                                value = defaultModelLabel(state),
                                onClick = viewModel::openDefaultModelPicker,
                                enabled = !state.isLoadingServerSettings,
                            )
                            Spacer(Modifier.height(12.dp))
                            SettingsPickerSummaryRow(
                                title = "Default Profile",
                                value = defaultProfileLabel(state),
                                onClick = viewModel::openDefaultProfilePicker,
                                enabled = !state.isLoadingServerSettings,
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection(title = "Android & Shortcuts") {
                    SettingsActionRow(
                        label = "Open Hermex Settings",
                        value = "Permissions, notifications, and app shortcuts",
                        onClick = { openAndroidAppSettings(context) },
                    )
                }
            }
            item {
                SettingsSection(title = "Sessions") {
                    SettingsToggleRow(
                        label = "Message Count",
                        value = state.sessionRowDisplaySettings.showMessageCount,
                        onValueChange = viewModel::setSessionRowShowMessageCount,
                    )
                    SettingsToggleRow(
                        label = "Workspace",
                        value = state.sessionRowDisplaySettings.showWorkspace,
                        onValueChange = viewModel::setSessionRowShowWorkspace,
                    )
                    SettingsToggleRow(
                        label = "Cron Sessions",
                        value = state.sessionRowDisplaySettings.showCronSessions,
                        onValueChange = viewModel::setShowCronSessions,
                    )
                    SettingsToggleRow(
                        label = "CLI Sessions",
                        value = state.showCliSessions,
                        enabled = !state.isSavingCliSessions,
                        switchTestTag = "cli_sessions_switch",
                        onValueChange = viewModel::setShowCliSessions,
                    )
                    state.cliSessionsError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } ?: run {
                        if (state.cliSessionsServerSynced) {
                            Text(
                                "CLI session visibility is synced with this server, so the WebUI follows it too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection(title = "Offline Data") {
                    Text(
                        "Cached sessions and messages are scoped to the active server for offline viewing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            item {
                SettingsSection(title = "App") {
                    SettingsInfoRow("Version", appInfo.version)
                    SettingsInfoRow("Build", appInfo.build)
                    SettingsActionRow(
                        label = "Privacy Policy",
                        value = "Open in browser",
                        onClick = { openExternalUrl(context, HermexAppLinks.PRIVACY_POLICY_URL) },
                    )
                    SettingsActionRow(
                        label = "Support",
                        value = "Open in browser",
                        onClick = { openExternalUrl(context, HermexAppLinks.SUPPORT_URL) },
                    )
                }
            }
            state.notice?.let {
                item { StatusText(it, isError = false) }
            }
            state.error?.let {
                item { StatusText(it, isError = true) }
            }
            item {
                SettingsSection(title = "Account") {
                    Text(
                        "Sign out clears this server's session cookies. The server registry remains available so you can reconnect quickly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    HermexPillButton(
                        label = if (state.isSigningOut) "Signing out..." else "Sign Out of This Server",
                        onClick = { viewModel.signOut(onSignedOut) },
                        enabled = !state.isSigningOut && loggedIn != null,
                        filled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
        SettingsDialogs(state, viewModel, onSignedOut)
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        HermexIconButton(
            label = "Back",
            symbol = "<",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            "Settings",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(start = 8.dp, bottom = 7.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hermexGlass(
                    shape = RoundedCornerShape(18.dp),
                    castsShadow = false,
                    surfaceLevel = HermexSurfaceLevel.Raised,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun Modifier.settingsDialogChrome(): Modifier = hermexGlass(
    shape = HermexGlassShape,
    surfaceLevel = HermexSurfaceLevel.Floating,
    tintEnabled = false,
)

@Composable
private fun ServerAccountRow(
    displayName: String,
    url: String,
    initials: String,
    headerColorHex: String,
    isActive: Boolean,
    headerCount: Int,
    onActivate: () -> Unit,
    onEditIdentity: () -> Unit,
    onEditHeaders: () -> Unit,
    onClearCache: () -> Unit,
    onForget: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerAvatar(
                initials = displayInitials(displayName, initials, url),
                colorHex = headerColorHex,
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
            HermexPillButton(
                label = if (isActive) "Active" else "Switch",
                onClick = onActivate,
                enabled = !isActive,
                filled = isActive,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HermexPillButton("Edit Identity", onEditIdentity)
            HermexPillButton("Headers ($headerCount)", onEditHeaders)
            HermexPillButton("Clear Cache", onClearCache)
            HermexPillButton("Forget", onForget)
        }
    }
}

@Composable
private fun IdentitySummary(
    displayName: String,
    initials: String,
    headerColorHex: String,
    onDisplayNameChange: (String) -> Unit,
    onInitialsChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerAvatar(
            initials = displayInitials(displayName, initials, displayName),
            colorHex = headerColorHex,
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text("Sessions Avatar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Stored on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    SettingsDivider()
    EditableIdentityRow(
        label = "Display Name",
        value = displayName,
        onValueChange = onDisplayNameChange,
    )
    SettingsDivider()
    EditableIdentityRow(
        label = "Initials",
        value = initials,
        onValueChange = { onInitialsChange(it.take(3)) },
    )
}

@Composable
private fun EditableIdentityRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .widthIn(min = 110.dp, max = 230.dp)
                .testTag("identity_${label.lowercase().replace(' ', '_')}"),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
    )
}

@Composable
private fun SettingsFootnote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 2.dp, bottom = 5.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun SettingsLeadingIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier.size(24.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
    )
}

@Composable
private fun SettingsAccessoryRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(iconRes)
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Image(
            painter = painterResource(R.drawable.ic_hermex_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
        )
    }
}

@Composable
private fun SettingsPickerRow(
    label: String,
    value: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(iconRes)
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            modifier = Modifier.widthIn(max = 150.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            painter = painterResource(R.drawable.ic_hermex_chevron_down),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

private val HeaderLogoColorPresets = listOf(
    "Yellow" to "#FFD700",
    "Blue" to "#5B7CFF",
    "Purple" to "#AF52DE",
    "Red" to "#FF3B30",
    "Green" to "#34C759",
    "White" to "#FFFFFF",
)

@Composable
private fun HeaderLogoColorSettings(
    selectedHex: String,
    onSelected: (String) -> Unit,
) {
    var customDialogVisible by remember { mutableStateOf(false) }
    var customHexDraft by remember(selectedHex) { mutableStateOf(selectedHex) }
    val selectedName = HeaderLogoColorPresets.firstOrNull { it.second.equals(selectedHex, ignoreCase = true) }?.first ?: "Custom"
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Header Logo Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(selectedName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0B0C)),
        contentAlignment = Alignment.Center,
    ) {
        HermesHeaderLogo(
            modifier = Modifier.widthIn(max = 250.dp).fillMaxWidth(0.72f),
            tint = hermexColorFromHex(selectedHex) ?: Color(0xFFFFD700),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderLogoColorPresets.forEach { (name, hex) ->
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
            val color = hermexColorFromHex(hex) ?: Color.White
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .semantics {
                        contentDescription = "$name header logo color"
                        this.selected = isSelected
                    }
                    .clickable { onSelected(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Image(
                        painter = painterResource(R.drawable.ic_hermex_check),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(
                            if (color.luminanceValue() > 0.62f) Color.Black else Color.White,
                        ),
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Custom header logo color" }
            .clickable {
                customHexDraft = selectedHex
                customDialogVisible = true
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Custom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(hermexColorFromHex(selectedHex) ?: Color(0xFFFFD700))
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), CircleShape),
        )
    }
    if (customDialogVisible) {
        val normalized = normalizeCustomHeaderHex(customHexDraft)
        AlertDialog(
            onDismissRequest = { customDialogVisible = false },
            title = { Text("Custom Header Color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = customHexDraft,
                        onValueChange = { customHexDraft = it.take(7) },
                        label = { Text("Hex color") },
                        placeholder = { Text("#FFD700") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("header_custom_color_input"),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                hermexColorFromHex(normalized)
                                    ?: hermexColorFromHex(selectedHex)
                                    ?: Color(0xFFFFD700),
                            ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelected(requireNotNull(normalized))
                        customDialogVisible = false
                    },
                    enabled = normalized != null,
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { customDialogVisible = false }) { Text("Cancel") }
            },
        )
    }
}

private fun normalizeCustomHeaderHex(value: String): String? {
    val raw = value.trim().removePrefix("#").uppercase()
    return raw.takeIf { it.matches(Regex("[0-9A-F]{6}")) }?.let { "#$it" }
}

@Composable
private fun AppIconSettingsPicker(
    selected: AppIconChoice,
    isExpanded: Boolean,
    errorMessage: String?,
    onToggleExpanded: () -> Unit,
    onSelect: (AppIconChoice) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggleExpanded)
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconPreview(selected)
        Column(Modifier.weight(1f)) {
            Text("App Icon", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(selected.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Text(
            text = if (isExpanded) "Hide" else "Choose",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (isExpanded) {
        Spacer(Modifier.height(8.dp))
        AppIconChoice.entries.forEach { choice ->
            AppIconChoiceRow(
                choice = choice,
                isSelected = choice == selected,
                onClick = { onSelect(choice) },
            )
        }
    }

    errorMessage?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AppIconChoiceRow(
    choice: AppIconChoice,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { this.selected = isSelected }
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconPreview(choice)
        Column(Modifier.weight(1f)) {
            Text(choice.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                choice.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = if (isSelected) "Selected" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppIconPreview(choice: AppIconChoice) {
    val shape = RoundedCornerShape(12.dp)
    Image(
        painter = painterResource(choice.previewDrawableRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape),
    )
}

@Composable
private fun ServerAvatar(
    initials: String,
    colorHex: String,
    modifier: Modifier = Modifier,
) {
    val color = colorHex.toComposeColor() ?: MaterialTheme.colorScheme.primary
    val foreground = if (color.luminanceValue() > 0.68f) Color(0xFF111111) else Color.White
    Box(
        modifier = modifier.background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.take(3),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsPickerSummaryRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            DetailLine(title, value)
        }
        Text(
            text = if (enabled) "Choose" else "Loading",
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    iconRes: Int? = null,
    value: Boolean,
    enabled: Boolean = true,
    switchTestTag: String? = null,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(switchTestTag?.let { Modifier.testTag(it) } ?: Modifier)
            .toggleable(
                value = value,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onValueChange,
            )
            .heightIn(min = 54.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconRes?.let { SettingsLeadingIcon(it) }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Switch(
            checked = value,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

@Composable
private fun StatusText(text: String, isError: Boolean) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SettingsDialogs(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onSignedOut: () -> Unit,
) {
    if (state.showDefaultModelPicker) {
        DefaultModelPickerDialog(
            state = state,
            onDismiss = viewModel::dismissDefaultModelPicker,
            onSaveModel = viewModel::saveDefaultModel,
            onSaveCustom = viewModel::saveDefaultModelId,
        )
    }

    if (state.showDefaultProfilePicker) {
        DefaultProfilePickerDialog(
            state = state,
            onDismiss = viewModel::dismissDefaultProfilePicker,
            onSelect = viewModel::saveDefaultProfile,
            onCreateProfile = viewModel::openCreateProfileDialog,
        )
    }

    if (state.showCreateProfileDialog) {
        CreateProfileDialog(
            state = state,
            onDismiss = viewModel::dismissCreateProfileDialog,
            onCreate = viewModel::createProfile,
        )
    }

    if (state.confirmServerUpdate) {
        AlertDialog(
            modifier = Modifier.settingsDialogChrome(),
            shape = HermexGlassShape,
            containerColor = Color.Transparent,
            onDismissRequest = viewModel::cancelServerUpdate,
            title = { Text("Update server?") },
            text = { Text("This pulls the latest Hermes server version and restarts it. Active chats may be interrupted briefly; the app reconnects when the server is back.") },
            confirmButton = { TextButton(onClick = viewModel::applyServerUpdate) { Text("Update") } },
            dismissButton = { TextButton(onClick = viewModel::cancelServerUpdate) { Text("Cancel") } },
        )
    }

    if (state.showForcedUpdateCheckResult) {
        val outcome = state.forcedUpdateCheckOutcome
        AlertDialog(
            modifier = Modifier.settingsDialogChrome(),
            shape = HermexGlassShape,
            containerColor = Color.Transparent,
            onDismissRequest = viewModel::dismissForcedUpdateCheckResult,
            title = { Text(forcedUpdateTitle(outcome)) },
            text = { Text(forcedUpdateMessage(outcome)) },
            confirmButton = {
                if (outcome is ForcedUpdateCheckOutcome.UpdateAvailable) {
                    TextButton(
                        onClick = {
                            viewModel.dismissForcedUpdateCheckResult()
                            viewModel.applyServerUpdate()
                        },
                    ) {
                        Text("Update")
                    }
                } else {
                    TextButton(onClick = viewModel::dismissForcedUpdateCheckResult) { Text("OK") }
                }
            },
            dismissButton = {
                if (outcome is ForcedUpdateCheckOutcome.UpdateAvailable) {
                    TextButton(onClick = viewModel::dismissForcedUpdateCheckResult) { Text("Dismiss") }
                }
            },
        )
    }

    state.identityEditorServer?.let { server ->
        IdentityEditorDialog(
            server = server,
            draft = state.identityDraft,
            onDraftChange = viewModel::updateIdentityDraft,
            onDismiss = viewModel::dismissIdentityEditor,
            onSave = viewModel::saveIdentityDraft,
        )
    }

    state.headerEditorServer?.let { server ->
        HeaderEditorDialog(
            server = server,
            text = state.headerEditorText,
            error = state.headerEditorError,
            onTextChange = viewModel::updateHeaderEditorText,
            onDismiss = viewModel::dismissHeaderEditor,
            onSave = viewModel::saveHeaderEditor,
        )
    }

    if (state.addServerDialogVisible) {
        AddServerDialog(
            state = state,
            onUrlChange = viewModel::updateAddServerUrl,
            onPasswordChange = viewModel::updateAddServerPassword,
            onHeadersChange = viewModel::updateAddServerHeadersText,
            onDisplayNameChange = viewModel::updateAddServerDisplayName,
            onInitialsChange = viewModel::updateAddServerInitials,
            onColorChange = viewModel::updateAddServerHeaderColor,
            onDismiss = viewModel::dismissAddServer,
            onSubmit = viewModel::submitAddServer,
        )
    }

    state.clearCacheServer?.let { server ->
        AlertDialog(
            modifier = Modifier.settingsDialogChrome(),
            shape = HermexGlassShape,
            containerColor = Color.Transparent,
            onDismissRequest = viewModel::cancelClearCache,
            title = { Text("Clear Offline Cache?") },
            text = { Text("Remove cached sessions and messages for ${server.displayName}. Server data is not changed.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearCache, enabled = !state.isClearingCache) {
                    Text(if (state.isClearingCache) "Clearing..." else "Clear")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelClearCache) { Text("Cancel") } },
        )
    }

    state.forgetServer?.let { server ->
        AlertDialog(
            modifier = Modifier.settingsDialogChrome(),
            shape = HermexGlassShape,
            containerColor = Color.Transparent,
            onDismissRequest = viewModel::cancelForgetServer,
            title = { Text("Forget Server?") },
            text = { Text("Remove ${server.displayName}, its encrypted headers, cookies, offline cache, and saved registry entry. Sign in again to add it back.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmForgetServer(onSignedOut) },
                    enabled = !state.isForgettingServer,
                ) {
                    Text(if (state.isForgettingServer) "Removing..." else "Forget")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelForgetServer) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DefaultModelPickerDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSaveModel: (com.uzairansar.hermex.core.model.ModelSummary) -> Unit,
    onSaveCustom: (String) -> Unit,
) {
    var searchText by remember(state.showDefaultModelPicker) { mutableStateOf("") }
    var customModel by remember(state.showDefaultModelPicker) { mutableStateOf("") }
    val groups = remember(state.models, state.modelProviders, searchText) {
        defaultModelPickerGroups(state.models, state.modelProviders, searchText)
    }

    AlertDialog(
        modifier = Modifier.settingsDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text("Default Model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().testTag("default_model_search"),
                    singleLine = true,
                    label = { Text("Search models") },
                    enabled = !state.isSavingDefaultModel,
                )
                state.defaultModelPickerError?.let { StatusText(it, isError = true) }
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    modifier = Modifier.fillMaxWidth().testTag("default_model_custom"),
                    singleLine = true,
                    label = { Text("Custom model ID") },
                    supportingText = { Text("Type the exact model ID the server expects.") },
                    enabled = !state.isSavingDefaultModel,
                )
                HermexPillButton(
                    label = if (state.isSavingDefaultModel) "Saving..." else "Save Custom Model",
                    onClick = { onSaveCustom(customModel) },
                    enabled = customModel.trim().isNotBlank() && !state.isSavingDefaultModel,
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isLoadingLiveModels) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("Refreshing live models...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                when {
                    state.models.isEmpty() -> Text("No models returned by the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    groups.isEmpty() -> Text("No matching models.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    else -> groups.forEach { group ->
                        Text(group.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                        group.models.forEach { model ->
                            DefaultModelOptionRow(
                                model = model,
                                selected = model.modelIdentifier == state.defaultModel,
                                enabled = !state.isSavingDefaultModel,
                                onClick = { onSaveModel(model) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !state.isSavingDefaultModel) { Text("Done") } },
    )
}

@Composable
private fun ServerUpdateControls(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val inFlight = state.updateApplyPhase == ServerUpdateApplyPhase.Applying ||
        state.updateApplyPhase == ServerUpdateApplyPhase.Recovering
    Spacer(Modifier.height(10.dp))
    when (val updateState = state.serverUpdateState) {
        WebUiUpdateState.UpToDate -> StatusText("Up to date", isError = false)
        is WebUiUpdateState.UpdateAvailable -> StatusText("Update available - ${updateState.behind} behind", isError = false)
        WebUiUpdateState.Unavailable,
        null,
        -> Unit
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermexPillButton(
            label = if (state.isCheckingForUpdates) "Checking..." else "Check for updates",
            onClick = viewModel::checkForUpdatesManually,
            enabled = !state.isCheckingForUpdates && !inFlight,
        )
        if (state.updateApplyPhase == ServerUpdateApplyPhase.Idle && state.serverUpdateState is WebUiUpdateState.UpdateAvailable) {
            HermexPillButton(
                label = "Update",
                onClick = viewModel::requestServerUpdate,
                enabled = !state.isCheckingForUpdates,
                filled = true,
            )
        }
        when (state.updateApplyPhase) {
            ServerUpdateApplyPhase.Applying -> UpdateProgressPill("Starting update...")
            ServerUpdateApplyPhase.Recovering -> UpdateProgressPill("Updating & restarting...")
            ServerUpdateApplyPhase.Blocked,
            ServerUpdateApplyPhase.Failed,
            -> HermexPillButton(
                label = "Retry update",
                onClick = viewModel::requestServerUpdate,
                enabled = !state.isCheckingForUpdates,
            )
            ServerUpdateApplyPhase.Idle -> Unit
        }
    }
    state.updateApplyMessage?.let {
        Spacer(Modifier.height(8.dp))
        StatusText(it, isError = state.updateApplyPhase == ServerUpdateApplyPhase.Failed)
    }
}

@Composable
private fun UpdateProgressPill(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun DefaultModelOptionRow(
    model: com.uzairansar.hermex.core.model.ModelSummary,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓" else "", modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(model.displayModelTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            val subtitle = listOfNotNull(model.modelIdentifier, model.provider).distinct().joinToString(" - ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DefaultProfilePickerDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSelect: (com.uzairansar.hermex.core.model.ProfileSummary) -> Unit,
    onCreateProfile: () -> Unit,
) {
    var searchText by remember(state.showDefaultProfilePicker) { mutableStateOf("") }
    val query = searchText.trim()
    val profiles = remember(state.profiles, query) {
        if (query.isBlank()) {
            state.profiles
        } else {
            state.profiles.filter { profile ->
                listOfNotNull(
                    profile.settingsDisplayName(),
                    profile.normalizedProfileName(),
                    profile.model,
                    profile.provider,
                ).any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    AlertDialog(
        modifier = Modifier.settingsDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text("Default Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().testTag("default_profile_search"),
                    singleLine = true,
                    label = { Text("Search profiles") },
                    enabled = !state.isSavingDefaultProfile,
                )
                state.defaultProfilePickerError?.let { StatusText(it, isError = true) }
                if (!state.isSingleProfileMode) {
                    HermexPillButton(
                        label = "New Profile",
                        onClick = onCreateProfile,
                        enabled = !state.isSavingDefaultProfile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when {
                    state.profiles.isEmpty() -> Text("No profiles returned by the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    profiles.isEmpty() -> Text("No matching profiles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    else -> profiles.forEach { profile ->
                        DefaultProfileOptionRow(
                            profile = profile,
                            selected = profile.normalizedProfileName() == state.activeProfile,
                            enabled = !state.isSavingDefaultProfile && profile.normalizedProfileName() != null,
                            onClick = { onSelect(profile) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !state.isSavingDefaultProfile) { Text("Done") } },
    )
}

@Composable
private fun DefaultProfileOptionRow(
    profile: com.uzairansar.hermex.core.model.ProfileSummary,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓" else "", modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(profile.settingsDisplayName(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (!selected && profile.isDefault == true) {
                    Text("Server Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            profile.settingsDetails()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean, String, String, String, String) -> Unit,
) {
    var name by remember(state.showCreateProfileDialog) { mutableStateOf("") }
    var cloneConfig by remember(state.showCreateProfileDialog) { mutableStateOf(false) }
    var defaultModel by remember(state.showCreateProfileDialog) { mutableStateOf("") }
    var modelProvider by remember(state.showCreateProfileDialog) { mutableStateOf("") }
    var baseUrl by remember(state.showCreateProfileDialog) { mutableStateOf("") }
    var apiKey by remember(state.showCreateProfileDialog) { mutableStateOf("") }
    val normalizedName = name.trim().lowercase()
    val invalidBaseUrl = baseUrl.trim().isNotEmpty() && !ProfileNameRules.isValidBaseUrl(baseUrl.trim())
    val canCreate = ProfileNameRules.isValid(normalizedName) && !invalidBaseUrl && !state.isCreatingProfile

    AlertDialog(
        modifier = Modifier.settingsDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text("New Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("new_profile_name"),
                    singleLine = true,
                    label = { Text("Profile name") },
                    supportingText = { Text("Lowercase letters, numbers, hyphens, and underscores.") },
                    enabled = !state.isCreatingProfile,
                    isError = name.isNotBlank() && !ProfileNameRules.isValid(normalizedName),
                )
                SettingsToggleRow(
                    label = "Clone config from active profile",
                    value = cloneConfig,
                    enabled = !state.isCreatingProfile,
                    onValueChange = { cloneConfig = it },
                )
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Default model") },
                    placeholder = { Text("Use active profile default") },
                    enabled = !state.isCreatingProfile,
                )
                OutlinedTextField(
                    value = modelProvider,
                    onValueChange = { modelProvider = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model provider") },
                    placeholder = { Text("Optional") },
                    enabled = !state.isCreatingProfile,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text(if (invalidBaseUrl) "Base URL must start with http:// or https://." else "Optional. Example: http://localhost:11434") },
                    isError = invalidBaseUrl,
                    enabled = !state.isCreatingProfile,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !state.isCreatingProfile,
                )
                state.profileCreateError?.let { StatusText(it, isError = true) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, cloneConfig, defaultModel, modelProvider, baseUrl, apiKey) },
                enabled = canCreate,
            ) {
                Text(if (state.isCreatingProfile) "Creating..." else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isCreatingProfile) { Text("Cancel") } },
    )
}

@Composable
private fun IdentityEditorDialog(
    server: ServerAccount,
    draft: ServerIdentityDraft,
    onDraftChange: (ServerIdentityDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.settingsDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text("Server Identity") },
        text = {
            ServerIdentityFields(
                displayName = draft.displayName,
                initials = draft.initials,
                colorHex = draft.headerLogoColorHex,
                fallbackName = server.displayName.ifBlank { server.urlString },
                onDisplayNameChange = { onDraftChange(draft.copy(displayName = it)) },
                onInitialsChange = { onDraftChange(draft.copy(initials = it)) },
                onColorChange = { onDraftChange(draft.copy(headerLogoColorHex = it)) },
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HeaderEditorDialog(
    server: ServerAccount,
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.settingsDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text("Custom Headers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    server.urlString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("Name: Value") },
                    supportingText = {
                        Text(error ?: "One header per line. Origin, Referer, Host, and Content-Length are blocked.")
                    },
                    isError = error != null,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddServerDialog(
    state: SettingsUiState,
    onUrlChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onHeadersChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onInitialsChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.settingsDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text("Add Server") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.addServerUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Server URL") },
                    placeholder = { Text("100.64.0.1:8787") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !state.isAddingServer,
                )
                if (state.addServerNeedsPassword) {
                    OutlinedTextField(
                        value = state.addServerPassword,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !state.isAddingServer,
                    )
                    Text(
                        "This server requires a password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                OutlinedTextField(
                    value = state.addServerHeadersText,
                    onValueChange = onHeadersChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("Custom Headers") },
                    placeholder = { Text("CF-Access-Client-Id: ...") },
                    enabled = !state.isAddingServer,
                )
                Text("Identity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ServerIdentityFields(
                    displayName = state.addServerDisplayName,
                    initials = state.addServerInitials,
                    colorHex = state.addServerHeaderColorHex,
                    fallbackName = state.addServerUrl,
                    onDisplayNameChange = onDisplayNameChange,
                    onInitialsChange = onInitialsChange,
                    onColorChange = onColorChange,
                    enabled = !state.isAddingServer,
                )
                state.addServerError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !state.isAddingServer && state.addServerUrl.isNotBlank(),
            ) {
                Text(if (state.isAddingServer) "Adding..." else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isAddingServer) { Text("Cancel") }
        },
    )
}

@Composable
private fun ServerIdentityFields(
    displayName: String,
    initials: String,
    colorHex: String,
    fallbackName: String,
    onDisplayNameChange: (String) -> Unit,
    onInitialsChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    val previewName = displayName.ifBlank { fallbackName }
    val previewInitials = displayInitials(previewName, initials, fallbackName)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ServerAvatar(
                initials = previewInitials,
                colorHex = colorHex,
                modifier = Modifier.size(42.dp),
            )
            Column(Modifier.weight(1f)) {
                Text("Server Avatar", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    previewInitials,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth().testTag("server_identity_display_name"),
            singleLine = true,
            label = { Text("Display Name") },
            placeholder = { Text(fallbackName.ifBlank { "Hermex" }) },
            enabled = enabled,
        )
        OutlinedTextField(
            value = initials,
            onValueChange = onInitialsChange,
            modifier = Modifier.fillMaxWidth().testTag("server_identity_initials"),
            singleLine = true,
            label = { Text("Initials") },
            placeholder = { Text(previewInitials) },
            enabled = enabled,
        )
        Text("Header Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServerHeaderColorPresets.forEach { preset ->
                ColorPresetButton(
                    hex = preset,
                    selected = normalizedHeaderColorHex(colorHex) == normalizedHeaderColorHex(preset),
                    enabled = enabled,
                    onClick = { onColorChange(preset) },
                )
            }
        }
    }
}

@Composable
private fun ColorPresetButton(
    hex: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        ServerAvatar(
            initials = if (selected) "X" else "",
            colorHex = hex,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun String.toComposeColor(): Color? {
    val raw = trim().removePrefix("#")
    if (!raw.matches(Regex("[0-9a-fA-F]{6}"))) return null
    val rgb = raw.toLong(16)
    return Color(0xFF000000L or rgb)
}

private fun Color.luminanceValue(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

private fun defaultModelLabel(state: SettingsUiState): String {
    val modelId = state.defaultModel?.trim()?.takeIf { it.isNotBlank() } ?: return "Server default"
    return state.models.firstOrNull { it.modelIdentifier == modelId }?.displayModelTitle ?: modelId
}

private fun defaultProfileLabel(state: SettingsUiState): String {
    val profileName = state.activeProfile?.trim()?.takeIf { it.isNotBlank() } ?: return "Server default"
    return state.profiles.firstOrNull { it.normalizedProfileName() == profileName }?.settingsDisplayName()
        ?: if (profileName == "default") "Default" else profileName
}

private fun forcedUpdateTitle(outcome: ForcedUpdateCheckOutcome?): String =
    when (outcome) {
        is ForcedUpdateCheckOutcome.UpdateAvailable -> "Update available - ${outcome.behind} behind"
        ForcedUpdateCheckOutcome.UpToDate -> "You're up to date"
        ForcedUpdateCheckOutcome.Disabled -> "Update checks are off"
        ForcedUpdateCheckOutcome.Error,
        null,
        -> "Couldn't check for updates"
    }

private fun forcedUpdateMessage(outcome: ForcedUpdateCheckOutcome?): String =
    when (outcome) {
        is ForcedUpdateCheckOutcome.UpdateAvailable -> "This pulls the latest Hermes server version and restarts it. Active chats may be interrupted briefly; the app reconnects when the server is back."
        ForcedUpdateCheckOutcome.UpToDate -> "The Hermes server is running the latest version."
        ForcedUpdateCheckOutcome.Disabled -> "Update checks are turned off on this server."
        ForcedUpdateCheckOutcome.Error,
        null,
        -> "Something went wrong reaching the server. Try again in a moment."
    }

private fun canPostAndroidNotifications(context: Context): Boolean =
    AndroidNotificationPermissionPolicy.canPostNotifications(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = hasPostNotificationPermission(context),
    ) && StreamStatusNotifier(context.applicationContext).canPostCompletionNotifications()

private fun hasPostNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun responseCompletionNotificationStatusText(
    state: SettingsUiState,
    context: Context,
): String? =
    state.responseCompletionNotificationStatusMessage
        ?: when {
            state.responseCompletionNotificationsEnabled && canPostAndroidNotifications(context) -> "Android notifications allowed."
            state.hasRequestedResponseCompletionNotificationPermission && !canPostAndroidNotifications(context) -> "Android notifications disabled."
            else -> null
        }

private fun Context.currentAndroidAppInfo(): AndroidAppInfo {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return AndroidAppInfo(
        version = displayAppVersion(packageInfo.versionName),
        build = displayAppBuild(versionCode),
    )
}

private fun openAndroidAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
