package com.uzairansar.hermex.ui

import android.content.Intent
import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uzairansar.hermex.AppContainer
import com.uzairansar.hermex.BuildConfig
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.ui.chat.ChatRoute
import com.uzairansar.hermex.ui.git.GitRoute
import com.uzairansar.hermex.ui.kanban.KanbanLabRoute
import com.uzairansar.hermex.ui.kanban.KanbanLabFixtureDataSource
import com.uzairansar.hermex.ui.kanban.KanbanLiveTiming
import com.uzairansar.hermex.ui.kanban.supportedKanbanLabScenarios
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.onboarding.OnboardingRoute
import com.uzairansar.hermex.ui.panels.PanelsRoute
import com.uzairansar.hermex.ui.sessions.SessionListRoute
import com.uzairansar.hermex.ui.settings.SettingsRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
import com.uzairansar.hermex.ui.workspace.WorkspaceRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun HermexApp(
    container: AppContainer,
    shortcutIntents: Flow<Intent> = emptyFlow(),
    onResetSecureStorage: suspend () -> Result<Unit> = { Result.failure(IllegalStateException("Reset is unavailable.")) },
    onShortcutIntentConsumed: (Intent) -> Unit = {},
) {
    val context = LocalContext.current
    val usesRegularWidthShell = usesRegularWidthSessionLayout(currentWindowWidthDp())
    val navController = rememberNavController()
    val authState by container.authRepository.state.collectAsStateWithLifecycle()
    val themeMode by container.localSettingsRepository.themeMode.collectAsStateWithLifecycle(
        initialValue = com.uzairansar.hermex.data.preferences.AppThemeMode.System,
    )
    val hapticsEnabled by container.localSettingsRepository.hapticsEnabled.collectAsStateWithLifecycle(
        initialValue = true,
    )
    val localHeaderLogoColorHex by container.localSettingsRepository.headerLogoColorHex.collectAsStateWithLifecycle(
        initialValue = "#FFD700",
    )
    val activeAccount = (authState as? AuthState.LoggedIn)?.account
    val activeServerKey = (authState as? AuthState.LoggedIn)?.server?.toString()
    val headerLogoColorHex = activeAccount?.headerLogoColorHex ?: localHeaderLogoColorHex
    var observedServerKey by rememberSaveable { mutableStateOf(activeServerKey) }
    var wasLoggedIn by rememberSaveable { mutableStateOf(activeServerKey != null) }
    var pendingAuthenticatedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAuthenticatedServerId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredSessionValidationComplete by remember(container.authRepository) {
        mutableStateOf(container.authRepository.restoredSessionValidationComplete)
    }
    val latestAuthState by rememberUpdatedState(authState)
    val latestOnShortcutIntentConsumed by rememberUpdatedState(onShortcutIntentConsumed)
    val profileShortcutPublisher = remember(context) { ProfileShortcutPublisher(context) }

    container.secureStorageFailure?.let {
        HermexTheme(themeMode = themeMode) {
            SecureStorageRecoveryScreen(onResetSecureStorage)
        }
        return
    }

    LaunchedEffect(authState) {
        if (authState !is AuthState.LoggedIn) profileShortcutPublisher.publish(emptyList())
    }

    LaunchedEffect(container.authRepository) {
        if (authState is AuthState.LoggedIn) {
            container.authRepository.validateRestoredSessionOnce()
        }
        restoredSessionValidationComplete = true
    }

    LaunchedEffect(
        activeAccount?.id,
        activeAccount?.displayName,
        activeAccount?.initials,
        activeAccount?.headerLogoColorHex,
    ) {
        activeAccount ?: return@LaunchedEffect
        container.localSettingsRepository.setSessionIdentityDisplayName(activeAccount.displayName)
        container.localSettingsRepository.setSessionIdentityInitials(activeAccount.initials)
        container.localSettingsRepository.setHeaderLogoColorHex(activeAccount.headerLogoColorHex)
    }

    if (!restoredSessionValidationComplete) {
        HermexTheme(themeMode = themeMode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
        return
    }

    LaunchedEffect(navController, shortcutIntents) {
        shortcutIntents.collect { intent ->
            try {
                val route = intent.hermexRoute()?.let { rawRoute ->
                    regularWidthDestinationRoute(rawRoute, usesRegularWidthShell)
                }
                if (route != null) {
                    val requestedServerId = intent.hermexServerId()
                    val loggedIn = latestAuthState as? AuthState.LoggedIn
                    val isDebugFixture = BuildConfig.DEBUG && route.startsWith("kanban-lab?scenario=")
                    if (isDebugFixture) {
                        navController.navigateSingleTop(route)
                    } else if (loggedIn != null) {
                        if (requestedServerId != null && requestedServerId != loggedIn.account.id) {
                            val account = container.authRepository.servers.value.servers.firstOrNull { it.id == requestedServerId }
                            if (account != null) {
                                pendingAuthenticatedRoute = route
                                pendingAuthenticatedServerId = requestedServerId
                                container.authRepository.activate(requestedServerId)
                            } else {
                                navController.navigateSingleTop("sessions")
                            }
                        } else {
                            navController.navigateSingleTop(route)
                        }
                    } else {
                        pendingAuthenticatedRoute = route
                        pendingAuthenticatedServerId = requestedServerId
                        navController.navigate("onboarding") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else if (latestAuthState is AuthState.LoggedIn) {
                    navController.handleDeepLink(intent)
                }
            } finally {
                latestOnShortcutIntentConsumed(intent)
            }
        }
    }

    LaunchedEffect(activeServerKey) {
        if (activeServerKey == null) {
            if (wasLoggedIn) {
                wasLoggedIn = false
                val isWaitingForTargetServerLogin =
                    pendingAuthenticatedRoute != null && pendingAuthenticatedServerId != null
                if (!isWaitingForTargetServerLogin) {
                    pendingAuthenticatedRoute = null
                    pendingAuthenticatedServerId = null
                }
                navController.navigate("onboarding") {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        val previousServerKey = observedServerKey
        val stayedLoggedIn = wasLoggedIn
        observedServerKey = activeServerKey
        val changedServer = stayedLoggedIn && previousServerKey != null && previousServerKey != activeServerKey
        wasLoggedIn = true
        if (changedServer) profileShortcutPublisher.publish(emptyList())
        val pendingRouteForServer = pendingAuthenticatedRoute?.takeIf {
            pendingAuthenticatedServerId == activeServerKey
        }
        if (changedServer || pendingRouteForServer != null) {
            navController.navigate("sessions") {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            if (pendingRouteForServer != null) {
                pendingAuthenticatedRoute = null
                pendingAuthenticatedServerId = null
                navController.navigateSingleTop(pendingRouteForServer)
            }
        }
    }

    HermexTheme(themeMode = themeMode) {
        CompositionLocalProvider(LocalHermexHapticsEnabled provides hapticsEnabled) {
            NavHost(
                navController = navController,
                startDestination = if (authState is AuthState.LoggedIn) "sessions" else "onboarding",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("onboarding") {
                    OnboardingRoute(
                        authRepository = container.authRepository,
                        onConnected = {
                            val requestedServerId = pendingAuthenticatedServerId
                            val activeId = (container.authRepository.state.value as? AuthState.LoggedIn)?.account?.id
                            if (requestedServerId != null && requestedServerId != activeId) {
                                val accountExists = container.authRepository.servers.value.servers.any { it.id == requestedServerId }
                                if (accountExists) {
                                    container.authRepository.activate(requestedServerId)
                                    return@OnboardingRoute
                                }
                                pendingAuthenticatedServerId = null
                            }
                            val route = pendingAuthenticatedRoute?.also {
                                pendingAuthenticatedRoute = null
                                pendingAuthenticatedServerId = null
                            }
                                ?: if (container.sharedDraftStore.hasPendingDraft()) {
                                ShortcutDestination.shareRoute()
                            } else {
                                "sessions"
                            }
                            navController.navigate(route) {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = "sessions?shortcutAction={shortcutAction}&shortcutNonce={shortcutNonce}&shortcutProfile={shortcutProfile}&showArchived={showArchived}&openSessionId={openSessionId}&openSessionConsumeShare={openSessionConsumeShare}&openSessionAutoVoice={openSessionAutoVoice}",
                    arguments = listOf(
                        navArgument("shortcutAction") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("shortcutNonce") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("shortcutProfile") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("showArchived") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("openSessionId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("openSessionConsumeShare") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("openSessionAutoVoice") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = ShortcutDestination.SessionsUri },
                        navDeepLink { uriPattern = ShortcutDestination.NewSessionUriPattern },
                    ),
                ) { entry ->
                    val shortcutAction = ShortcutDestination.supportedAction(entry.arguments?.getString("shortcutAction"))
                    val usesRegularWidthLayout = usesRegularWidthSessionLayout(currentWindowWidthDp())
                    val initiallyOpenSessionId = entry.arguments?.getString("openSessionId")
                    val initiallyConsumesShare = entry.arguments?.getBoolean("openSessionConsumeShare") == true
                    val initiallyAutoStartsVoice = entry.arguments?.getBoolean("openSessionAutoVoice") == true
                    var selectedSessionId by rememberSaveable(activeServerKey, initiallyOpenSessionId) {
                        mutableStateOf(initiallyOpenSessionId)
                    }
                    var selectedConsumesShare by rememberSaveable(activeServerKey, initiallyOpenSessionId, initiallyConsumesShare) {
                        mutableStateOf(initiallyConsumesShare)
                    }
                    var selectedAutoStartsVoice by rememberSaveable(activeServerKey, initiallyOpenSessionId, initiallyAutoStartsVoice) {
                        mutableStateOf(initiallyAutoStartsVoice)
                    }
                    LaunchedEffect(usesRegularWidthLayout, initiallyOpenSessionId) {
                        if (!usesRegularWidthLayout && initiallyOpenSessionId != null) {
                            val suffix = when {
                                selectedConsumesShare -> "?consumeShare=true"
                                selectedAutoStartsVoice -> "?autoStartVoice=true"
                                else -> ""
                            }
                            navController.navigateSingleTop("chat/${Uri.encode(initiallyOpenSessionId)}$suffix")
                        }
                    }
                    val selectSession: (String, Boolean, Boolean) -> Unit = { sessionId, consumeShare, autoStartVoice ->
                        selectedSessionId = sessionId
                        selectedConsumesShare = consumeShare
                        selectedAutoStartsVoice = autoStartVoice
                    }
                    val sessionList: @Composable () -> Unit = {
                        SessionListRoute(
                            authState = authState,
                            container = container,
                            shortcutAction = shortcutAction,
                            shortcutNonce = entry.arguments?.getString("shortcutNonce"),
                            shortcutProfile = entry.arguments?.getString("shortcutProfile"),
                            initialArchived = entry.arguments?.getBoolean("showArchived") == true,
                            selectedSessionId = selectedSessionId.takeIf { usesRegularWidthLayout },
                            onOpenChat = { sessionId ->
                                if (usesRegularWidthLayout) selectSession(sessionId, false, false)
                                else navController.navigateSingleTop("chat/$sessionId")
                            },
                            onOpenVoiceChat = { sessionId ->
                                if (usesRegularWidthLayout) selectSession(sessionId, false, true)
                                else navController.navigateSingleTop("chat/$sessionId?autoStartVoice=true")
                            },
                            onOpenSharedDraft = { sessionId ->
                                if (usesRegularWidthLayout) selectSession(sessionId, true, false)
                                else navController.navigateSingleTop("chat/$sessionId?consumeShare=true")
                            },
                            onOpenPanels = { navController.navigateSingleTop("panels") },
                            onOpenPanel = { section -> navController.navigateSingleTop("panels?section=$section") },
                            onOpenKanban = { navController.navigateSingleTop("kanban") },
                            onOpenSettings = { navController.navigateSingleTop("settings") },
                            onNeedsOnboarding = {
                                navController.navigate("onboarding") {
                                    popUpTo("sessions") { inclusive = true }
                                }
                            },
                        )
                    }
                    if (!usesRegularWidthLayout) {
                        sessionList()
                    } else {
                        val server = (authState as? AuthState.LoggedIn)?.server
                        val detailSessionId = selectedSessionId
                        RegularWidthSessionContainer(
                            sidebar = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                ) {
                                    sessionList()
                                }
                            },
                            detail = {
                                if (server != null && detailSessionId != null) {
                                    ChatRoute(
                                        sessionId = detailSessionId,
                                        serverId = activeServerKey ?: server.toString(),
                                        viewModelKey = "chat:$activeServerKey:$detailSessionId",
                                        repository = container.chatRepository(server),
                                        gitRepository = container.gitRepository(server),
                                        workspaceRepository = container.workspaceRepository(server),
                                        localSettingsRepository = container.localSettingsRepository,
                                        activeHeaderColorHex = headerLogoColorHex,
                                        sharedDraftStore = container.sharedDraftStore,
                                        consumeSharedDraft = selectedConsumesShare,
                                        autoStartVoice = selectedAutoStartsVoice,
                                        onOpenChat = { sessionId -> selectSession(sessionId, false, false) },
                                        onBack = { selectedSessionId = null },
                                        onOpenWorkspace = { navController.navigate("workspace/$detailSessionId") },
                                        onOpenGit = { navController.navigate("git/$detailSessionId") },
                                    )
                                } else {
                                    RegularWidthEmptyDetail(
                                        onNewChat = {
                                            navController.navigateSingleTop(
                                                ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction),
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
                composable(
                    route = "chat/{sessionId}?consumeShare={consumeShare}&autoStartVoice={autoStartVoice}",
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("consumeShare") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("autoStartVoice") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server == null) {
                        OnboardingRoute(container.authRepository) {}
                    } else {
                        ChatRoute(
                            sessionId = requireNotNull(entry.arguments?.getString("sessionId")),
                            serverId = activeServerKey ?: server.toString(),
                            viewModelKey = "chat:$activeServerKey:${entry.arguments?.getString("sessionId")}",
                            repository = container.chatRepository(server),
                            gitRepository = container.gitRepository(server),
                            workspaceRepository = container.workspaceRepository(server),
                            localSettingsRepository = container.localSettingsRepository,
                            activeHeaderColorHex = headerLogoColorHex,
                            sharedDraftStore = container.sharedDraftStore,
                            consumeSharedDraft = entry.arguments?.getBoolean("consumeShare") == true,
                            autoStartVoice = entry.arguments?.getBoolean("autoStartVoice") == true,
                            onOpenChat = { sessionId -> navController.navigateSingleTop("chat/$sessionId") },
                            onBack = { navController.popBackStack() },
                            onOpenWorkspace = {
                                navController.navigate("workspace/${requireNotNull(entry.arguments?.getString("sessionId"))}")
                            },
                            onOpenGit = {
                                navController.navigate("git/${requireNotNull(entry.arguments?.getString("sessionId"))}")
                            },
                        )
                    }
                }
                composable(
                    route = "workspace/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        WorkspaceRoute(
                            sessionId = requireNotNull(entry.arguments?.getString("sessionId")),
                            viewModelKey = "workspace:$activeServerKey:${entry.arguments?.getString("sessionId")}",
                            repository = container.workspaceRepository(server),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "git/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        GitRoute(
                            sessionId = requireNotNull(entry.arguments?.getString("sessionId")),
                            viewModelKey = "git:$activeServerKey:${entry.arguments?.getString("sessionId")}",
                            repository = container.gitRepository(server),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "panels?section={section}",
                    arguments = listOf(
                        navArgument("section") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = ShortcutDestination.PanelsUri }),
                ) { entry ->
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        PanelsRoute(
                            panelsRepository = container.panelsRepository(server),
                            initialSection = entry.arguments?.getString("section"),
                            onBack = { navController.popBackStack() },
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            if (!navController.popBackStack()) {
                                navController.navigate("onboarding") {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        }
                    }
                }
                composable(route = "kanban") {
                    val server = (authState as? AuthState.LoggedIn)?.server
                    if (server != null) {
                        KanbanLabRoute(
                            repository = container.kanbanRepository(server),
                            viewModelKey = "kanban:$activeServerKey",
                            onBack = { navController.popBackStack() },
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate("onboarding") {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    }
                }
                composable(
                    route = "settings",
                    deepLinks = listOf(navDeepLink { uriPattern = ShortcutDestination.SettingsUri }),
                ) {
                    val server = (authState as? AuthState.LoggedIn)?.server
                    SettingsRoute(
                        authRepository = container.authRepository,
                        localSettingsRepository = container.localSettingsRepository,
                        cacheMaintenanceRepository = container.cacheMaintenanceRepository,
                        panelsRepository = server?.let { container.panelsRepository(it) },
                        authState = authState,
                        onBack = { navController.popBackStack() },
                        onOpenArchivedSessions = { navController.navigateSingleTop("sessions?showArchived=true") },
                        onSignedOut = {
                            navController.navigate("onboarding") {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                if (BuildConfig.DEBUG) {
                    composable(
                        route = "kanban-lab?scenario={scenario}",
                        arguments = listOf(
                            navArgument("scenario") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val scenario = entry.arguments?.getString("scenario")
                        val server = (authState as? AuthState.LoggedIn)?.server
                        val fixture = scenario?.takeIf(supportedKanbanLabScenarios::contains)
                        if (fixture != null) {
                            KanbanLabRoute(
                                repository = remember(fixture) { KanbanLabFixtureDataSource(fixture) },
                                viewModelKey = "kanban-lab-fixture:$fixture",
                                liveTiming = if (fixture == "offline" || fixture == "delayed") {
                                    KanbanLiveTiming(
                                        reconnectDelaysMillis = listOf(50),
                                        failuresBeforePolling = 1,
                                        coalescingDelayMillis = 20,
                                        pollingIntervalMillis = 30_000,
                                        initialPollingDelayMillis = if (fixture == "offline") 50 else null,
                                    )
                                } else {
                                    KanbanLiveTiming()
                                },
                                onBack = { navController.popBackStack() },
                            )
                        } else if (server != null) {
                            KanbanLabRoute(
                                repository = container.kanbanRepository(server),
                                viewModelKey = "kanban-lab:$activeServerKey",
                                onBack = { navController.popBackStack() },
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                navController.navigate("onboarding") {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun usesRegularWidthSessionLayout(screenWidthDp: Int): Boolean = screenWidthDp >= 840

@Composable
private fun currentWindowWidthDp(): Int {
    val widthPixels = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPixels.toDp().value.roundToInt() }
}

internal fun regularWidthDestinationRoute(route: String, usesRegularWidthLayout: Boolean): String {
    if (!usesRegularWidthLayout || !route.startsWith("chat/")) return route
    val destination = route.removePrefix("chat/")
    val encodedSessionId = destination.substringBefore('?').takeIf { it.isNotBlank() } ?: return route
    val query = destination.substringAfter('?', missingDelimiterValue = "")
    val consumeShare = query.split('&').any { it == "consumeShare=true" }
    val autoStartVoice = query.split('&').any { it == "autoStartVoice=true" }
    return "sessions?openSessionId=$encodedSessionId&openSessionConsumeShare=$consumeShare&openSessionAutoVoice=$autoStartVoice"
}

@Composable
internal fun RegularWidthSessionContainer(
    sidebar: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(340.dp)
                .widthIn(min = 280.dp, max = 420.dp)
                .fillMaxHeight(),
        ) {
            sidebar()
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            detail()
        }
    }
}

@Composable
private fun RegularWidthEmptyDetail(
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                localizedString("Select a Chat"),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                localizedString("Choose a session from the sidebar or start a new chat."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Button(onClick = onNewChat) {
                Text(localizedString("New Chat"))
            }
        }
    }
}

@Composable
private fun SecureStorageRecoveryScreen(
    onResetSecureStorage: suspend () -> Result<Unit>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resetFailureMessage = localizedString("Could not reset secure storage.")
    var isResetting by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(localizedString("Secure storage needs recovery"), style = MaterialTheme.typography.headlineSmall)
            Text(
                localizedString("Hermex could not open its encrypted account data. Resetting removes saved servers, sign-in cookies, and custom headers from this device."),
                style = MaterialTheme.typography.bodyMedium,
            )
            resetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !isResetting,
                onClick = {
                    isResetting = true
                    resetError = null
                    scope.launch {
                        onResetSecureStorage()
                            .onSuccess { (context as? Activity)?.recreate() }
                            .onFailure { error ->
                                resetError = error.message ?: resetFailureMessage
                                isResetting = false
                            }
                    }
                },
            ) {
                if (isResetting) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(localizedString("Reset secure data"))
                }
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

internal fun Intent.hermexRoute(): String? {
    val uri = data ?: return null
    if (uri.scheme != "hermes-agent") return null
    return when (uri.host?.lowercase()) {
        "share" -> ShortcutDestination.shareRoute()
        "sessions" -> ShortcutDestination.sessionsRoute(uri.getQueryParameter("shortcutAction"))
        "new-chat" -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction)
        "new-chat-voice" -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewVoiceSessionAction)
        "new-chat-profile" -> uri.getQueryParameter("profile")
            ?.takeIf { it.isNotBlank() }
            ?.let { profile -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewProfileSessionAction, profile) }
            ?: ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction)
        "chat", "session" -> uri.hermexSessionId()
            ?.let { sessionId -> "chat/${Uri.encode(sessionId)}" }
        "settings" -> "settings"
        "panels" -> uri.getQueryParameter("section")?.takeIf { it.isNotBlank() }?.let { "panels?section=${Uri.encode(it)}" } ?: "panels"
        "kanban-lab" -> if (BuildConfig.DEBUG) {
            uri.getQueryParameter("scenario")
                ?.lowercase()
                ?.takeIf(supportedKanbanLabScenarios::contains)
                ?.let { "kanban-lab?scenario=${Uri.encode(it)}" }
                ?: "kanban-lab"
        } else {
            null
        }
        else -> null
    }
}

internal fun Intent.hermexServerId(): String? = data
    ?.takeIf {
        it.scheme == "hermes-agent" &&
            (it.host.equals("chat", ignoreCase = true) || it.host.equals("session", ignoreCase = true))
    }
    ?.let { uri ->
        sequenceOf("serverId", "server_id")
            .mapNotNull(uri::getQueryParameter)
            .firstOrNull { it.isNotBlank() }
    }

private fun Uri.hermexSessionId(): String? =
    sequenceOf("sessionId", "id", "session_id")
        .mapNotNull(::getQueryParameter)
        .firstOrNull { it.isNotBlank() }

private fun ShortcutDestination.sessionsRoute(action: String? = null, profile: String? = null): String {
    val supportedAction = supportedAction(action) ?: return "sessions"
    val profileQuery = profile?.takeIf { it.isNotBlank() }?.let { "&shortcutProfile=${Uri.encode(it)}" }.orEmpty()
    return "sessions?shortcutAction=$supportedAction&shortcutNonce=${System.currentTimeMillis()}$profileQuery"
}

private fun ShortcutDestination.shareRoute(): String =
    sessionsRoute(ShortcutDestination.ShareAction)
