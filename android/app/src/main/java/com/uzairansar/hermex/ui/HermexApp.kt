package com.uzairansar.hermex.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uzairansar.hermex.AppContainer
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.ui.chat.ChatRoute
import com.uzairansar.hermex.ui.git.GitRoute
import com.uzairansar.hermex.ui.onboarding.OnboardingRoute
import com.uzairansar.hermex.ui.panels.PanelsRoute
import com.uzairansar.hermex.ui.sessions.SessionListRoute
import com.uzairansar.hermex.ui.settings.SettingsRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
import com.uzairansar.hermex.ui.workspace.WorkspaceRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun HermexApp(
    container: AppContainer,
    shortcutIntents: Flow<Intent> = emptyFlow(),
) {
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
    var restoredSessionValidationComplete by remember {
        mutableStateOf(authState !is AuthState.LoggedIn)
    }
    val latestAuthState by rememberUpdatedState(authState)

    LaunchedEffect(container.authRepository) {
        if (authState is AuthState.LoggedIn) {
            container.authRepository.validateRestoredSession()
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
            val route = intent.hermexRoute()
            if (route != null) {
                val requestedServerId = intent.hermexServerId()
                val loggedIn = latestAuthState as? AuthState.LoggedIn
                if (loggedIn != null) {
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
                    route = "sessions?shortcutAction={shortcutAction}&shortcutNonce={shortcutNonce}&shortcutProfile={shortcutProfile}&showArchived={showArchived}",
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
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = ShortcutDestination.SessionsUri },
                        navDeepLink { uriPattern = ShortcutDestination.NewSessionUriPattern },
                    ),
                ) { entry ->
                    val shortcutAction = ShortcutDestination.supportedAction(entry.arguments?.getString("shortcutAction"))
                    SessionListRoute(
                        authState = authState,
                        container = container,
                        shortcutAction = shortcutAction,
                        shortcutNonce = entry.arguments?.getString("shortcutNonce"),
                        shortcutProfile = entry.arguments?.getString("shortcutProfile"),
                        initialArchived = entry.arguments?.getBoolean("showArchived") == true,
                        onOpenChat = { sessionId -> navController.navigateSingleTop("chat/$sessionId") },
                        onOpenVoiceChat = { sessionId -> navController.navigateSingleTop("chat/$sessionId?autoStartVoice=true") },
                        onOpenSharedDraft = { sessionId -> navController.navigateSingleTop("chat/$sessionId?consumeShare=true") },
                        onOpenPanels = { navController.navigateSingleTop("panels") },
                        onOpenPanel = { section -> navController.navigateSingleTop("panels?section=$section") },
                        onOpenSettings = { navController.navigateSingleTop("settings") },
                        onNeedsOnboarding = {
                            navController.navigate("onboarding") {
                                popUpTo("sessions") { inclusive = true }
                            }
                        },
                    )
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

private fun Intent.hermexRoute(): String? {
    val uri = data ?: return null
    if (uri.scheme != "hermes-agent") return null
    return when (uri.host) {
        "share" -> ShortcutDestination.shareRoute()
        "sessions" -> ShortcutDestination.sessionsRoute(uri.getQueryParameter("shortcutAction"))
        "new-chat" -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction)
        "new-chat-voice" -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewVoiceSessionAction)
        "new-chat-profile" -> uri.getQueryParameter("profile")
            ?.takeIf { it.isNotBlank() }
            ?.let { profile -> ShortcutDestination.sessionsRoute(ShortcutDestination.NewProfileSessionAction, profile) }
            ?: ShortcutDestination.sessionsRoute(ShortcutDestination.NewSessionAction)
        "chat" -> uri.getQueryParameter("sessionId")
            ?.takeIf { it.isNotBlank() }
            ?.let { sessionId -> "chat/${Uri.encode(sessionId)}" }
        "settings" -> "settings"
        "panels" -> uri.getQueryParameter("section")?.takeIf { it.isNotBlank() }?.let { "panels?section=${Uri.encode(it)}" } ?: "panels"
        else -> null
    }
}

private fun Intent.hermexServerId(): String? = data
    ?.takeIf { it.scheme == "hermes-agent" && it.host == "chat" }
    ?.getQueryParameter("serverId")
    ?.takeIf { it.isNotBlank() }

private fun ShortcutDestination.sessionsRoute(action: String? = null, profile: String? = null): String {
    val supportedAction = supportedAction(action) ?: return "sessions"
    val profileQuery = profile?.takeIf { it.isNotBlank() }?.let { "&shortcutProfile=${Uri.encode(it)}" }.orEmpty()
    return "sessions?shortcutAction=$supportedAction&shortcutNonce=${System.currentTimeMillis()}$profileQuery"
}

private fun ShortcutDestination.shareRoute(): String =
    sessionsRoute(ShortcutDestination.ShareAction)
